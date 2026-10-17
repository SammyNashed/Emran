package io.github.sammynashed.emran.data.testers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Tester(
    val id: String,
    private val nameEn: String,
    private val nameAr: String?,
    private val roleEn: String?,
    private val roleAr: String?,
    /** "testers/<id>.jpg" in assets, or an absolute path for one added on this phone. */
    val photo: String? = null,
) {
    fun name(lang: String) = if (lang == "ar") nameAr ?: nameEn else nameEn
    fun role(lang: String) = if (lang == "ar") roleAr ?: roleEn else roleEn ?: roleAr

    /** The photo, decoded small; null when there is none or it cannot be read. */
    fun loadPhoto(context: Context): Bitmap? = runCatching {
        val p = photo ?: return null
        if (p.startsWith("/")) BitmapFactory.decodeFile(p)
        else context.assets.open(p).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/** One thing a tester said, in the words and language they said it. */
class Review(
    val tester: Tester,
    private val textAr: String?,
    private val textEn: String?,
    /** 1-5, or null when no rating was given. */
    val stars: Int?,
) {
    fun text(lang: String): String = (if (lang == "ar") textAr ?: textEn else textEn ?: textAr).orEmpty()
}

class TestersWall(val testers: List<Tester>, val reviews: List<Review>)

/**
 * Reads `assets/testers.json` (format: docs/testers-format.md). In debug builds the wall can be
 * changed on the phone: the first change copies the list to `files/testers_pending/testers.json`
 * (photos beside it), every add or edit goes there, and from then on that copy is what is shown.
 * It is read off the phone and becomes the new assets/testers.json.
 */
object TestersRepository {
    @Volatile private var cache: TestersWall? = null

    private fun pendingDir(context: Context) = File(context.filesDir, "testers_pending")
    private fun pendingFile(context: Context) = File(pendingDir(context), "testers.json")

    private fun JSONObject.str(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun parse(root: JSONObject, photoBase: File?): Pair<List<Tester>, List<JSONObject>> {
        val arr = root.optJSONArray("testers") ?: JSONArray()
        val testers = List(arr.length()) {
            val o = arr.getJSONObject(it)
            Tester(
                id = o.getString("id"),
                nameEn = o.getString("nameEn"),
                nameAr = o.str("nameAr"),
                roleEn = o.str("roleEn"),
                roleAr = o.str("roleAr"),
                photo = o.str("photo")?.let { p -> photoBase?.let { File(it, p) }?.takeIf { it.exists() }?.absolutePath ?: p },
            )
        }
        val rev = root.optJSONArray("reviews") ?: JSONArray()
        return testers to List(rev.length()) { rev.getJSONObject(it) }
    }

    suspend fun load(context: Context): TestersWall = withContext(Dispatchers.IO) {
        cache ?: try {
            // The phone's working copy, once anything has been added or changed here, replaces the
            // bundled list entirely (it started as a copy of it), so nothing is merged twice.
            val pf = pendingFile(context)
            if (pf.exists()) workingCopy(context)
            val (testers, reviewObjs) = if (pf.exists()) parse(JSONObject(pf.readText()), pendingDir(context))
            else parse(JSONObject(context.assets.open("testers.json").bufferedReader().use { it.readText() }), null)
            val byId = testers.associateBy { it.id }
            val reviews = reviewObjs.mapNotNull { o ->
                val tester = byId[o.optString("testerId")] ?: return@mapNotNull null
                val ar = o.str("textAr")
                val en = o.str("textEn")
                if (ar == null && en == null) null
                else Review(tester, ar, en, if (o.has("stars") && !o.isNull("stars")) o.getInt("stars").coerceIn(1, 5) else null)
            }
            TestersWall(testers, reviews).also { cache = it }
        } catch (e: Exception) {
            android.util.Log.e("TestersRepository", "cannot read testers", e)
            TestersWall(emptyList(), emptyList())
        }
    }

    /** The working copy, created from the bundled list on the first change. */
    private fun workingCopy(context: Context): JSONObject {
        val file = pendingFile(context)
        pendingDir(context).mkdirs()
        val bundled = JSONObject(context.assets.open("testers.json").bufferedReader().use { it.readText() })
        if (!file.exists()) {
            file.writeText(bundled.put("copy", true).toString(2))
        } else {
            val root = JSONObject(file.readText())
            // An older build saved additions only; fold them under the bundled list once.
            if (!root.optBoolean("copy")) {
                val testers = bundled.getJSONArray("testers")
                val reviews = bundled.getJSONArray("reviews")
                root.optJSONArray("testers")?.let { a -> for (i in 0 until a.length()) testers.put(a.get(i)) }
                root.optJSONArray("reviews")?.let { a -> for (i in 0 until a.length()) reviews.put(a.get(i)) }
                file.writeText(bundled.put("copy", true).toString(2))
            }
        }
        return JSONObject(file.readText())
    }

    /** Removes a person from this phone's working copy: their entry, everything they said, and their photo. */
    suspend fun deletePending(context: Context, testerId: String) = withContext(Dispatchers.IO) {
        val root = workingCopy(context)
        fun JSONArray.without(keep: (JSONObject) -> Boolean) = JSONArray().also { out ->
            for (i in 0 until length()) getJSONObject(i).let { if (keep(it)) out.put(it) }
        }
        val testers = root.getJSONArray("testers")
        for (i in 0 until testers.length()) {
            val o = testers.getJSONObject(i)
            if (o.getString("id") == testerId) o.optString("photo").takeIf { it.isNotBlank() }?.let { File(pendingDir(context), it).delete() }
        }
        root.put("testers", testers.without { it.getString("id") != testerId })
        root.put("reviews", root.getJSONArray("reviews").without { it.optString("testerId") != testerId })
        pendingFile(context).writeText(root.toString(2))
        cache = null
    }

    private fun isArabic(s: String) = s.any { it in '\u0600'..'\u06FF' }

    /**
     * Changes a person already on the wall: name, role, photo, and the [reviewIndex]-th thing
     * they said (counted among their own reviews). Blank words leave that review as it was.
     */
    suspend fun editPending(
        context: Context, testerId: String, name: String, role: String?, words: String?, reviewIndex: Int?, photo: Uri?,
        lang: String = "en",
    ) = withContext(Dispatchers.IO) {
        val root = workingCopy(context)
        val testers = root.getJSONArray("testers")
        for (i in 0 until testers.length()) {
            val o = testers.getJSONObject(i)
            if (o.getString("id") != testerId) continue
            // Only the language being edited changes; the other language's version stays as it was.
            val n = name.trim()
            val L = if (lang == "ar") "Ar" else "En"
            if (n.isNotEmpty()) o.put("name$L", n)
            val r = role?.trim().orEmpty()
            if (r.isNotEmpty()) o.put("role$L", r) else o.remove("role$L")
            if (photo != null) savePhoto(context, photo, File(pendingDir(context), "$testerId.jpg"))?.let { o.put("photo", "$testerId.jpg") }
        }
        val w = words?.trim().orEmpty()
        // Someone who had said nothing yet: the words become their first review.
        if (reviewIndex == null && w.isNotEmpty()) {
            root.getJSONArray("reviews").put(JSONObject().put("testerId", testerId).put(if (lang == "ar") "textAr" else "textEn", w))
        }
        if (reviewIndex != null && w.isNotEmpty()) {
            val reviews = root.getJSONArray("reviews")
            var k = 0
            for (i in 0 until reviews.length()) {
                val o = reviews.getJSONObject(i)
                if (o.optString("testerId") != testerId) continue
                if (k++ == reviewIndex) {
                    o.put(if (lang == "ar") "textAr" else "textEn", w)
                    break
                }
            }
        }
        pendingFile(context).writeText(root.toString(2))
        cache = null
    }

    /**
     * Adds a person (or, when [name] matches someone already on the wall, another thing they
     * said) to this phone's additions. Text in Arabic script is stored as Arabic, the rest as
     * English, exactly as typed. The photo is cut to a centred square of 256 px.
     */
    suspend fun addPending(context: Context, name: String, role: String?, words: String?, photo: Uri?) = withContext(Dispatchers.IO) {
        val dir = pendingDir(context).apply { mkdirs() }
        val file = pendingFile(context)
        val root = workingCopy(context)
        val testers = root.getJSONArray("testers")
        val cleanName = name.trim()

        val wall = load(context)
        val existing = wall.testers.firstOrNull { t ->
            t.name("en").equals(cleanName, ignoreCase = true) || t.name("ar") == cleanName
        }
        val id = existing?.id ?: run {
            val slug = cleanName.lowercase().replace(Regex("[^a-z0-9؀-ۿ]+"), "-").trim('-').ifBlank { "tester" }
            "$slug-${System.currentTimeMillis() % 100000}"
        }
        if (existing == null) {
            val o = JSONObject().put("id", id).put("nameEn", cleanName)
            if (isArabic(cleanName)) o.put("nameAr", cleanName)
            role?.trim()?.takeIf { it.isNotEmpty() }?.let { r -> o.put(if (isArabic(r)) "roleAr" else "roleEn", r) }
            if (photo != null) savePhoto(context, photo, File(dir, "$id.jpg"))?.let { o.put("photo", "$id.jpg") }
            testers.put(o)
        }
        words?.trim()?.takeIf { it.isNotEmpty() }?.let { w ->
            root.getJSONArray("reviews").put(JSONObject().put("testerId", id).put(if (isArabic(w)) "textAr" else "textEn", w))
        }
        file.writeText(root.toString(2))
        cache = null
    }

    private fun savePhoto(context: Context, uri: Uri, out: File): File? = runCatching {
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, 256, 256, true)
        out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        out
    }.getOrNull()
}
