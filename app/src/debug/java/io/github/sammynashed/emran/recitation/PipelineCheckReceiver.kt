package io.github.sammynashed.emran.recitation

import ai.ratl.core.WordStatus
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
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
 *   1. (with --ez fresh true) the unpacked model is wiped, to replay a first launch
 *   2. the bundled model is unpacked from the APK and its SHA-256 verified, timed
 *   3. Emran's own quran.json Arabic -> Normalizer -> tokenizer -> model
 *      -> WordStatus results the UI can render
 */
class PipelineCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                run(context.applicationContext, fresh = intent.getBooleanExtra("fresh", false))
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

    private fun run(ctx: Context, fresh: Boolean) = runBlocking {
        say("=== RATL FULL PIPELINE CHECK ===")

        val assessor = RecitationAssessor(ctx)
        if (fresh) {
            File(ctx.filesDir, "ratl-model").deleteRecursively()
            say("1. unpacked model wiped, isInstalled=${assessor.isInstalled}")
        } else {
            say("1. keeping the unpacked model, isInstalled=${assessor.isInstalled}")
        }

        // ---- unpack from the APK + verify, through the same call Hifz mode makes
        val t0 = System.nanoTime()
        val unpacked = runCatching { assessor.installBundled() }
        val ms = (System.nanoTime() - t0) / 1_000_000
        say("2. installBundled ok=${unpacked.isSuccess} in ${ms} ms, isInstalled=${assessor.isInstalled}" +
            (unpacked.exceptionOrNull()?.let { " error=${it.message}" } ?: ""))
        val installed = assessor.isInstalled
        if (!installed) {
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
            "RESULT: ${if (ok && installed) "PASS" else "SEE ABOVE"} (bundled=$installed assess=$ok)",
        )
        assessor.release()
    }

    private companion object {
        const val TAG = "RATL-PIPE"
    }
}
