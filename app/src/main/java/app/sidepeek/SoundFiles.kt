package app.sidepeek

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns

/**
 * 「從我的檔案選音檔」: a channel sound must be a file the system itself can open, so a picked
 * audio file is copied into the phone's Notifications folder (MediaStore). It then also shows
 * up in the system's own sound list.
 */
object SoundFiles {
    /** Notification sounds are a few seconds; this only stops a whole album being copied by mistake. */
    private const val MAX_BYTES = 20L * 1024 * 1024

    /** The copied sound, or null when it could not be copied (Android 9 and older: no way without a storage permission). */
    fun importNotificationSound(ctx: Context, src: Uri): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val cr = ctx.contentResolver
        val name = displayName(ctx, src) ?: "Sidepeek 鈴聲.mp3"
        val mime = cr.getType(src)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS + "/")
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val dst = try {
            cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) {
            Diagnostics.note(ctx, "SOUND import insert failed: ${e.javaClass.simpleName}"); null
        } ?: return null
        return try {
            var total = 0L
            cr.openInputStream(src)!!.use { input ->
                cr.openOutputStream(dst)!!.use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) error("too large")
                        out.write(buf, 0, n)
                    }
                }
            }
            cr.update(dst, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            dst
        } catch (e: Exception) {
            Diagnostics.note(ctx, "SOUND import copy failed: ${e.javaClass.simpleName} ${e.message}")
            try { cr.delete(dst, null, null) } catch (_: Exception) {}
            null
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.takeIf { n -> n.isNotBlank() } else null
        }
    } catch (_: Exception) { null }
}
