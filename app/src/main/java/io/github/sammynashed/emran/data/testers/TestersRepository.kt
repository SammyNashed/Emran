package io.github.sammynashed.emran.data.testers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class Tester(
    val id: String,
    private val nameEn: String,
    private val nameAr: String?,
    private val roleEn: String?,
    private val roleAr: String?
) {
    fun name(lang: String) = if (lang == "ar") nameAr ?: nameEn else nameEn
    fun role(lang: String) = if (lang == "ar") roleAr ?: roleEn else roleEn ?: roleAr
}

/** One thing a tester said, in the words and language they said it. */
class Review(
    val tester: Tester,
    private val textAr: String?,
    private val textEn: String?,
    /** 1-5, or null when no rating was given (then no stars are drawn). */
    val stars: Int?
) {
    fun text(lang: String): String = (if (lang == "ar") textAr ?: textEn else textEn ?: textAr).orEmpty()
}

class TestersWall(val testers: List<Tester>, val reviews: List<Review>)

/** Reads `assets/testers.json` (format: docs/testers-format.md). */
object TestersRepository {
    @Volatile private var cache: TestersWall? = null

    suspend fun load(context: Context): TestersWall = withContext(Dispatchers.IO) {
        cache ?: try {
            val root = JSONObject(context.assets.open("testers.json").bufferedReader().use { it.readText() })
            val testerArr = root.optJSONArray("testers")
            val testers = List(testerArr?.length() ?: 0) {
                val o = testerArr!!.getJSONObject(it)
                Tester(
                    id = o.getString("id"),
                    nameEn = o.getString("nameEn"),
                    nameAr = o.optString("nameAr").takeIf { s -> s.isNotBlank() && s != "null" },
                    roleEn = o.optString("roleEn").takeIf { s -> s.isNotBlank() && s != "null" },
                    roleAr = o.optString("roleAr").takeIf { s -> s.isNotBlank() && s != "null" }
                )
            }
            val byId = testers.associateBy { it.id }
            val reviewArr = root.optJSONArray("reviews")
            val reviews = (0 until (reviewArr?.length() ?: 0)).mapNotNull {
                val o = reviewArr!!.getJSONObject(it)
                val tester = byId[o.optString("testerId")] ?: return@mapNotNull null
                val ar = o.optString("textAr").takeIf { s -> s.isNotBlank() && s != "null" }
                val en = o.optString("textEn").takeIf { s -> s.isNotBlank() && s != "null" }
                if (ar == null && en == null) null
                else Review(tester, ar, en, if (o.has("stars") && !o.isNull("stars")) o.getInt("stars").coerceIn(1, 5) else null)
            }
            TestersWall(testers, reviews).also { cache = it }
        } catch (e: Exception) {
            android.util.Log.e("TestersRepository", "cannot read testers.json", e)
            TestersWall(emptyList(), emptyList())
        }
    }
}
