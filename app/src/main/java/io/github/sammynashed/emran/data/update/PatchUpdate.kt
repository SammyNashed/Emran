package io.github.sammynashed.emran.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import io.github.sammynashed.emran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.InflaterInputStream

/**
 * Small updates through Telegram, the way Google Play does them: a patch carries only what
 * changed (a few MB) and Emran rebuilds the new APK from its own installed copy. The hifz model,
 * the speech engine and the hadith books (~61 MB) are never sent again.
 *
 * Emran itself still never downloads anything: the patch arrives as a file (opened from Telegram,
 * or picked in Settings), and the result is installed by Android's own "Update this app?" screen.
 * A rebuilt APK must hash to the patch's own fingerprint, or it is thrown away untouched.
 *
 * Format and generator: ~/emran-release/tools/emran_patch.py (see there).
 */
object PatchUpdate {
    class Header(
        val fromVersionCode: Int,
        val fromVersionName: String,
        val fromSha256: String,
        val toVersionCode: Int,
        val toVersionName: String,
        val toSha256: String,
        val toSize: Long,
    )

    sealed class Check {
        class Ready(val header: Header, val file: File) : Check()
        /** Not an Emran patch at all. */
        object NotAPatch : Check()
        /** This version or a newer one is already installed. */
        object AlreadyUpToDate : Check()
        /** Made for another starting version (or another variant of the APK). */
        class WrongVersion(val header: Header) : Check()
    }

    /** A patch file handed to Emran (Telegram's "Open with", share, or the Settings picker). */
    val incoming = mutableStateOf<Uri?>(null)

    /** Set by [InstallStatusReceiver] when Android reports a failed install. */
    val installError = mutableStateOf<String?>(null)

    private val MAGIC = "EMRANPATCH1\n".toByteArray()

    /** Copies the file out of the sender's hands (its permission is temporary) and reads its header. */
    suspend fun check(context: Context, uri: Uri): Check = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "incoming.emran")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return@withContext Check.NotAPatch
        } catch (e: Exception) {
            return@withContext Check.NotAPatch
        }
        val header = runCatching { readHeader(file).first }.getOrNull() ?: return@withContext Check.NotAPatch
        when {
            header.toVersionCode <= BuildConfig.VERSION_CODE -> Check.AlreadyUpToDate
            header.fromVersionCode != BuildConfig.VERSION_CODE -> Check.WrongVersion(header)
            // Same version but a different APK (another phone type's build): the patch can't fit it.
            sha256(File(context.applicationInfo.sourceDir)) != header.fromSha256 -> Check.WrongVersion(header)
            else -> Check.Ready(header, file)
        }
    }

    private fun readHeader(file: File): Pair<Header, Long> {
        DataInputStream(FileInputStream(file).buffered()).use { s ->
            val magic = ByteArray(MAGIC.size).also { s.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "not an Emran patch" }
            val len = s.readInt()
            require(len in 1..65536)
            val json = JSONObject(String(ByteArray(len).also { s.readFully(it) }, Charsets.UTF_8))
            return Header(
                fromVersionCode = json.getInt("fromVersionCode"),
                fromVersionName = json.optString("fromVersionName"),
                fromSha256 = json.getString("fromSha256"),
                toVersionCode = json.getInt("toVersionCode"),
                toVersionName = json.getString("toVersionName"),
                toSha256 = json.getString("toSha256"),
                toSize = json.getLong("toSize"),
            ) to (MAGIC.size + 4L + len)
        }
    }

    /** Rebuilds the new APK from the installed one. Returns null if anything doesn't match. */
    suspend fun build(context: Context, patch: File, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val (header, bodyAt) = readHeader(patch)
        val out = File(patch.parentFile, "emran-${header.toVersionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        try {
            RandomAccessFile(context.applicationInfo.sourceDir, "r").use { old ->
                FileInputStream(patch).use { raw ->
                    raw.channel.position(bodyAt)
                    DataInputStream(InflaterInputStream(raw.buffered(1 shl 16), java.util.zip.Inflater(), 1 shl 16)).use { ops ->
                        out.outputStream().buffered(1 shl 16).use { o ->
                            val buf = ByteArray(1 shl 16)
                            fun emit(n: Int) { o.write(buf, 0, n); digest.update(buf, 0, n); written += n }
                            loop@ while (true) {
                                when (ops.readUnsignedByte()) {
                                    0 -> break@loop
                                    1 -> {
                                        var off = ops.readLong()
                                        var left = ops.readInt().toLong() and 0xFFFFFFFFL
                                        while (left > 0) {
                                            val n = minOf(left, buf.size.toLong()).toInt()
                                            old.seek(off); old.readFully(buf, 0, n)
                                            emit(n); off += n; left -= n
                                        }
                                    }
                                    2 -> {
                                        var left = ops.readInt().toLong() and 0xFFFFFFFFL
                                        while (left > 0) {
                                            val n = minOf(left, buf.size.toLong()).toInt()
                                            ops.readFully(buf, 0, n)
                                            emit(n); left -= n
                                        }
                                    }
                                    else -> error("bad op")
                                }
                                progress((written.toFloat() / header.toSize).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PatchUpdate", "rebuild failed", e)
            out.delete()
            return@withContext null
        }
        if (digest.digest().toHex() != header.toSha256) {
            out.delete()
            return@withContext null
        }
        out
    }

    /** May Emran ask Android to install? (Android 8+ asks the user once, per app.) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Hands the rebuilt APK to Android, which shows its own "Update this app?" screen. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        installError.value = null
        // Read back by the new version on its first start (see [announceIfUpdated]).
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENDING, true).commit()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out, 1 shl 16)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(context, id, Intent(context, InstallStatusReceiver::class.java), flags)
            session.commit(status.intentSender)
        }
    }

    private const val PREFS = "emran_settings"
    private const val KEY_PENDING = "patch_update_pending"

    /**
     * Android closes the app to replace it and nothing reopens it, so without a word the user is
     * left wondering where Emran went. After an update that Emran started itself, say it's done.
     */
    fun announceIfUpdated(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        prefs.edit().putBoolean(KEY_PENDING, false).apply()
        cleanUp(context)
        val ar = (prefs.getString("current_language", "en") ?: "en") == "ar"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = "emran_updates"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(channel, if (ar) "التحديثات" else "Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 7201,
            Intent(context, io.github.sammynashed.emran.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = androidx.core.app.NotificationCompat.Builder(context, channel)
            .setSmallIcon(io.github.sammynashed.emran.R.drawable.ic_stat_emran)
            .setContentTitle(if (ar) "تم تحديث عِمران إلى ${BuildConfig.VERSION_NAME}" else "Emran is updated to ${BuildConfig.VERSION_NAME}")
            .setContentText(if (ar) "اضغط لفتحه. بياناتك كما هي." else "Tap to open it. Your data is as it was.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(7201, n) }
    }

    /** Leftovers from an earlier update are of no use once it is installed (or abandoned). */
    fun cleanUp(context: Context) {
        File(context.cacheDir, "update").listFiles()?.forEach { it.delete() }
    }

    private fun sha256(file: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = s.read(buf); if (n < 0) break; d.update(buf, 0, n) }
        }
        return d.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/** Android's answers about the install: show its confirm screen, or say what went wrong. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> PatchUpdate.cleanUp(context)
            PackageInstaller.STATUS_FAILURE_ABORTED -> PatchUpdate.installError.value = "aborted"
            else -> PatchUpdate.installError.value = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "failed"
        }
    }
}
