package io.github.sammynashed.emran.recitation

import ai.ratl.core.SessionConfig
import ai.ratl.android.RatlAndroid
import ai.ratl.core.Assessment
import ai.ratl.core.Engine
import ai.ratl.core.Session
import ai.ratl.core.WordStatus
import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Emran's integration of RATL, the on-device Quran recitation assessment engine.
 *
 * ## Why the model is not bundled, and not downloaded
 *
 * Emran declares **no `INTERNET` permission**. That is a manifest-level
 * guarantee, not a policy promise, and it is the strongest privacy claim the app
 * makes - so this feature must not weaken it. An in-app download is therefore
 * off the table entirely: it would require `INTERNET` and change what the
 * operating system permits Emran to do.
 *
 * Bundling the ~31 MB model in the APK was the alternative, and it was rejected
 * too: it makes every user carry the model whether or not they ever check a
 * recitation, and it is stored twice on disk (once in the APK, once extracted
 * for the runtime to open by path).
 *
 * Instead the model is **optional and user-supplied**. The user downloads it
 * once in a browser and imports it through the system file picker, which needs
 * no permission at all. Emran stays offline by construction; the recitation
 * checker simply stays dormant until someone chooses to enable it.
 *
 * Because Emran cannot reach the network, recitation audio provably cannot leave
 * the device. That is enforced by the OS, not by this class promising it.
 */
class RecitationAssessor(private val context: Context) {

    private var engine: Engine? = null

    private val modelDir: File get() = File(context.filesDir, "ratl-model").apply { mkdirs() }
    private val modelFile: File get() = File(modelDir, MODEL_NAME)
    private val vocabFile: File get() = File(modelDir, "vocab.json")

    /** True once a verified model has been imported. */
    val isInstalled: Boolean
        get() = modelFile.isFile && modelFile.length() > 0 && vocabFile.isFile

    val isLoaded: Boolean get() = engine != null

    /** Size on disk of the imported model, or 0. */
    fun installedBytes(): Long = if (isInstalled) modelFile.length() + vocabFile.length() else 0L

    /**
     * Import the model from a user-chosen file.
     *
     * The hash is verified before the file is accepted: RATL identifies a model
     * by its SHA-256 and reports it in every assessment, so an altered or
     * truncated file must be refused here rather than produce quietly wrong
     * results later.
     */
    fun importModel(uri: Uri): Result<Unit> = runCatching {
        val tmp = File(modelDir, "$MODEL_NAME.part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
        } ?: error("Could not open the selected file.")

        val actual = RatlAndroid.sha256(tmp)
        if (actual != MODEL_SHA256) {
            tmp.delete()
            error(
                "That file does not match the expected RATL model.\n" +
                    "Expected ${MODEL_SHA256.take(12)}…, got ${actual.take(12)}…",
            )
        }
        if (!tmp.renameTo(modelFile)) {
            tmp.delete()
            error("Could not save the model.")
        }
        // The vocabulary is small and fixed, and ships with the app so only one
        // file has to be imported.
        context.assets.open("ratl/vocab.json").use { input ->
            vocabFile.outputStream().use { out -> input.copyTo(out) }
        }
    }

    /**
     * Install a model from a file already on disk, verifying it first.
     *
     * Shared by the download path and the file-picker path so verification
     * cannot be bypassed by whichever one is added later.
     *
     * @return true if the file matched the expected hash and was installed
     */
    fun installFrom(source: File): Boolean {
        if (!source.isFile || source.length() <= 0L) return false
        if (RatlAndroid.sha256(source) != MODEL_SHA256) return false
        modelDir.mkdirs()
        source.copyTo(modelFile, overwrite = true)
        context.assets.open("ratl/vocab.json").use { input ->
            vocabFile.outputStream().use { out -> input.copyTo(out) }
        }
        return isInstalled
    }

    /**
     * Where to keep the audio of Hifz attempts, or null (the normal case).
     *
     * Opt-in by a marker file `hifz-debug` in the app's external files folder,
     * so nothing a reciter says is ever stored unless a developer asked for it.
     * It exists to tune the engine against real phone-mic recitation.
     */
    fun debugAudioDir(): File? {
        val base = context.getExternalFilesDir(null) ?: return null
        if (!File(base, "hifz-debug").exists()) return null
        return File(base, "hifz-audio").apply { mkdirs() }
    }

    /** Remove the imported model and free the space. */
    fun removeModel() {
        release()
        modelFile.delete()
        vocabFile.delete()
    }

    /** Load the engine. Call off the main thread - it reads ~31 MB. */
    fun load(): Engine {
        engine?.let { return it }
        check(isInstalled) { "The recitation model has not been added yet." }
        // RATL defaults to ONE thread because its G5 benchmark is specified
        // single-threaded (SPEC 7.2) - a rule about what figure may be
        // reported, not a limit of the engine. On a phone one thread decodes
        // slower than the reciter speaks, so words lagged ~2 s behind. The
        // answer is the same model on more cores; the big cores are 4 of 8.
        return RatlAndroid.engineFromDir(modelDir, threads = INFERENCE_THREADS)
            .also { engine = it }
    }

    fun assess(pcm16kMono: FloatArray, targetWords: List<String>): Assessment =
        load().assessWords(pcm16kMono, targetWords)

    fun startSession(targetWords: List<String>): Session =
        load().createSession(targetWords, SessionConfig(partialIntervalMs = PARTIAL_INTERVAL_MS))

    fun release() {
        engine?.close()
        engine = null
    }

    /**
     * How Emran presents a word.
     *
     * RATL abstains rather than accuse, and the UI must not undo that:
     * `UNCERTAIN` is "unclear", never a mistake. V1 never emits `SUBSTITUTION`,
     * and if a later version does, it is shown the same neutral way rather than
     * asserting a verdict on someone's recitation.
     */
    fun display(status: WordStatus): Display = when (status) {
        WordStatus.CORRECT -> Display.MATCHED
        WordStatus.OMISSION -> Display.MISSED
        WordStatus.REPETITION -> Display.REPEATED
        WordStatus.INCOMPLETE -> Display.UNFINISHED
        WordStatus.INSERTION -> Display.EXTRA
        WordStatus.SUBSTITUTION, WordStatus.UNCERTAIN -> Display.UNCLEAR
        WordStatus.PENDING -> Display.NOT_REACHED
        else -> Display.UNCLEAR
    }

    enum class Display { MATCHED, MISSED, REPEATED, UNFINISHED, EXTRA, UNCLEAR, NOT_REACHED }

    companion object {
        const val MODEL_NAME = "ratl-align-v1-e2e-fp32.onnx"
        const val MODEL_SHA256 =
            "95a01016aa1383bf60d28edfe5eaf6baecb65b71280cc5085b67ce05545d2798"
        const val MODEL_SIZE_MB = 31

        /** Where a user can obtain the model, shown as text for manual import. */
        const val MODEL_SOURCE = "github.com/SammyNashed/ratl/releases"

        /**
         * The release asset [ModelDownloader] fetches.
         *
         * This is a contract with the RATL repository, not a free-form link.
         * The tag and asset name are the ones RATL's own
         * `docs/MODEL_DISTRIBUTION.md` publishes as the distribution channel;
         * rename either side and every install of Emran loses the in-app
         * download at once. The SHA-256 in [MODEL_SHA256] pins the contents, so
         * republishing different bytes under the same tag fails verification
         * rather than shipping silently.
         *
         * `vocab.json` is attached to the same release for other integrators.
         * Emran does not fetch it - it ships in `assets/ratl/`, because a
         * 35-token file is not worth a second request that can fail.
         *
         * HTTPS and a real host, deliberately: the previous value pointed at
         * `127.0.0.1` over `adb reverse`, which could only ever work with a
         * developer machine on the other end of a USB cable.
         */
        const val MODEL_URL =
            "https://github.com/SammyNashed/ratl/releases/download/v1.0.0/" + MODEL_NAME

        /** RATL requires exactly this rate. */
        const val SAMPLE_RATE = 16_000

        /** Inference threads; see [load]. */
        private const val INFERENCE_THREADS = 4

        /**
         * How much new audio before the display is refreshed. RATL's default
         * is 500 ms; halved so a word appears sooner. It is only a floor - the
         * recorder hands the engine everything queued, so a slow decode never
         * drops audio, it just refreshes less often.
         */
        private const val PARTIAL_INTERVAL_MS = 100
    }
}
