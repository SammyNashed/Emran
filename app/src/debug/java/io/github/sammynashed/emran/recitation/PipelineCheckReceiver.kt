package io.github.sammynashed.emran.recitation

import ai.ratl.core.WordStatus
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * DEBUG ONLY. Runs the complete recitation pipeline and logs the result.
 *
 * This exists because MIUI refuses to install a new package over USB without a
 * Mi-account-gated developer setting, which blocks the instrumentation APK. The
 * host app updates fine, so the same checks run from inside it instead. It is
 * confined to the debug source set and is not present in a release build.
 *
 *   adb shell am broadcast -a io.github.sammynashed.emran.RUN_PIPELINE_CHECK \
 *     -n io.github.sammynashed.emran/.recitation.PipelineCheckReceiver
 *   adb logcat -d -s RATL-PIPE:I
 *
 * Checks, in order:
 *   1. DownloadManager fetches the model without Emran holding INTERNET
 *   2. SHA-256 verification accepts the real file and rejects a corrupt one
 *   3. Emran's own quran.json Arabic -> Normalizer -> tokenizer -> model
 *      -> WordStatus results the UI can render
 */
class PipelineCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                // Downloading is OFF by default: a broadcast receiver gets about
                // ten seconds, and MIUI defers download jobs far beyond that, so
                // attempting it here ANRs the app. The download belongs to the
                // sheet, whose scope lives as long as the screen. Pass
                // --ez download true only when deliberately probing it.
                run(
                    context.applicationContext,
                    keepModel = !intent.getBooleanExtra("download", false),
                )
            } catch (t: Throwable) {
                say("FATAL ${t::class.java.simpleName}: ${t.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun say(s: String) = Log.i(TAG, s)

    private fun ayahFromAssets(ctx: Context, surah: Int, ayah: Int): String {
        val text = ctx.assets.open("quran.json").bufferedReader().use { it.readText() }
        val surahs = JSONArray(text)
        for (i in 0 until surahs.length()) {
            val s = surahs.getJSONObject(i)
            if (s.getInt("id") != surah) continue
            val verses = s.getJSONArray("verses")
            for (j in 0 until verses.length()) {
                val v = verses.getJSONObject(j)
                if (v.getInt("id") == ayah) return v.getString("text")
            }
        }
        error("ayah $surah:$ayah not in quran.json")
    }

    private fun run(ctx: Context, keepModel: Boolean) = runBlocking {
        say("=== RATL FULL PIPELINE CHECK ===")

        val assessor = RecitationAssessor(ctx)
        if (!keepModel) {
            assessor.removeModel()
            say("1. model removed, isInstalled=${assessor.isInstalled}")
        } else {
            say("1. keeping existing model, isInstalled=${assessor.isInstalled}")
        }

        // ---- download + verify, through the same class the sheet uses
        val events = if (keepModel) emptyList() else runCatching {
            ModelDownloader(ctx).download(assessor, cancelOnClose = false).toList() }
            .getOrElse {
                say("   download threw ${it::class.java.simpleName}: ${it.message}")
                emptyList()
            }
        val running = events.count { it is ModelDownloader.Progress.Running }
        val verified = events.any { it is ModelDownloader.Progress.Verifying }
        val installed = events.any { it is ModelDownloader.Progress.Installed }
        val failures = events.filterIsInstance<ModelDownloader.Progress.Failed>()
        say("2. download: progress_events=$running verifying=$verified installed=$installed")
        if (failures.isNotEmpty()) say("   failures=${failures.map { it.reason }}")
        say("   isInstalled=${assessor.isInstalled} bytes=${assessor.installedBytes()}")

        // ---- a corrupt file must be refused
        val bogus = File(ctx.cacheDir, "bogus.onnx").apply { writeText("not a model") }
        val rejected = !assessor.installFrom(bogus)
        bogus.delete()
        say("3. corrupt file rejected=$rejected")

        if (!assessor.isInstalled) {
            say("RESULT: STOPPED - no model installed, cannot assess")
            return@runBlocking
        }

        // ---- Arabic -> Normalizer -> tokenizer -> model -> statuses
        val arabic = ayahFromAssets(ctx, 112, 1)
        say("4. arabic 112:1 = $arabic")
        val target = RecitationRecorder.targetWordsFromArabic(arabic)
        say("   normalised = $target")
        if (target.isEmpty()) {
            say("RESULT: FAILED - Normalizer produced no words")
            return@runBlocking
        }

        // Silence. This proves a real ayah reaches the engine and comes back as
        // typed results the UI can draw - not that assessment is accurate,
        // which needs a real voice.
        val pcm = FloatArray(RecitationAssessor.SAMPLE_RATE * 3)
        val a = assessor.assess(pcm, target)
        say("5. model_id=${a.modelId.take(16)}…")
        say("   words=${a.words.size} (target ${target.size})")
        say("   statuses=${a.words.map { it.status }}")

        val ok = a.words.size == target.size &&
            a.words.none { it.status == WordStatus.PENDING } &&
            a.words.none { it.status == WordStatus.SUBSTITUTION }
        say(
            "RESULT: ${if (ok && installed && rejected) "PASS" else "SEE ABOVE"} " +
                "(download=$installed verify_rejects_corrupt=$rejected assess=$ok)",
        )
        assessor.release()
    }

    private companion object {
        const val TAG = "RATL-PIPE"
    }
}
