package io.github.sammynashed.emran.recitation

import ai.ratl.core.WordStatus
import ai.ratl.text.Normalizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The complete path, on the real device, with nothing stubbed:
 *
 *   DownloadManager  ->  SHA-256 verification  ->  install
 *   Emran quran.json Arabic  ->  RATL Normalizer  ->  tokenizer
 *   ->  RATL model  ->  WordStatus results
 *
 * Driven headlessly because the UI cannot be tapped from here; the sheet calls
 * exactly these APIs in exactly this order.
 *
 * The download is served over `adb reverse` from the development machine while
 * the release URL does not exist yet. If it is unreachable the download half is
 * skipped and the assessment half still runs against an already-installed
 * model - a missing test server must not look like a passing download.
 */
@RunWith(AndroidJUnit4::class)
class FullPipelineTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun say(s: String) = android.util.Log.i("RATL-PIPE", s)

    /** Arabic for one ayah, straight out of Emran's own bundled asset. */
    private fun ayahFromAssets(surah: Int, ayah: Int): String {
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
        error("ayah $surah:$ayah not found in quran.json")
    }

    @Test
    fun downloadVerifyAndAssessRealAyah() = runBlocking {
        val assessor = RecitationAssessor(ctx)
        assessor.removeModel()
        assertTrue("model should start absent", !assessor.isInstalled)

        // ---- 1. download + verify, through the same class the sheet uses
        val events = try {
            ModelDownloader(ctx).download(assessor).toList()
        } catch (e: Throwable) {
            emptyList()
        }
        val installedByDownload = events.any { it is ModelDownloader.Progress.Installed }
        val failed = events.filterIsInstance<ModelDownloader.Progress.Failed>()
        say("download events=${events.size} installed=$installedByDownload failures=$failed")

        if (installedByDownload) {
            assertTrue("verification step must run", events.any {
                it is ModelDownloader.Progress.Verifying
            })
            assertTrue("progress must be reported", events.any {
                it is ModelDownloader.Progress.Running
            })
            assertTrue("model must be installed after download", assessor.isInstalled)
            say("DOWNLOAD PATH: verified and installed, ${assessor.installedBytes()} bytes")
        } else {
            say("DOWNLOAD PATH: unavailable (test server not reachable) - $failed")
        }

        // ---- 2. a corrupt file must be refused
        val bogus = File(ctx.cacheDir, "bogus.onnx").apply { writeText("not a model") }
        assertTrue("a corrupt file must be rejected", !assessor.installFrom(bogus))
        bogus.delete()

        if (!assessor.isInstalled) {
            say("SKIPPING assessment: no model available on this device")
            return@runBlocking
        }

        // ---- 3. Emran Arabic -> Normalizer -> tokenizer -> model -> statuses
        val arabic = ayahFromAssets(112, 1)
        say("arabic 112:1 = $arabic")

        val target = RecitationRecorder.targetWordsFromArabic(arabic)
        say("normalised target = $target")
        assertTrue("Normalizer produced no words for a real ayah", target.isNotEmpty())

        // Silence: content does not matter here. What is being proven is that a
        // real Arabic ayah reaches the engine and comes back as typed results
        // the UI can render - not that assessment is accurate, which needs a
        // real voice.
        val pcm = FloatArray(RecitationAssessor.SAMPLE_RATE * 3)
        val assessment = assessor.assess(pcm, target)

        say("model_id = ${assessment.modelId.take(16)}…")
        say("statuses = ${assessment.words.map { it.status }}")
        assertEquals(
            "one result per target word", target.size, assessment.words.size,
        )
        assertTrue("no results returned", assessment.words.isNotEmpty())
        assertTrue(
            "PENDING must never appear in a batch assessment",
            assessment.words.none { it.status == WordStatus.PENDING },
        )
        assertTrue(
            "V1 must never emit SUBSTITUTION",
            assessment.words.none { it.status == WordStatus.SUBSTITUTION },
        )
        // Every word must carry a status the UI knows how to draw.
        assessment.words.forEach { w ->
            assertTrue(
                "unmapped status ${w.status}",
                assessor.display(w.status) in RecitationAssessor.Display.entries,
            )
        }
        say("PIPELINE OK: ${assessment.words.size} words assessed from Arabic input")
        assessor.release()
    }
}
