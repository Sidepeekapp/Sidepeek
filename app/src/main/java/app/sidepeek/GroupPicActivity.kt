package app.sidepeek

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

/**
 * 設定 → 群組頭貼 → one group: choose a picture for that group (replaces the
 * initial badge / LINE's group picture in the notification), or go back to the default.
 * The picture stays on this phone (app-private storage).
 */
class GroupPicActivity : Activity() {
    companion object {
        const val EXTRA_CHAT_KEY = "app.sidepeek.GROUP_CHAT_KEY"
        const val EXTRA_TITLE = "app.sidepeek.GROUP_TITLE"
        private const val REQ_PICK = 1
        private const val SAVE_PX = 256
    }

    private lateinit var chatKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatKey = intent.getStringExtra(EXTRA_CHAT_KEY) ?: return finish()
        if (savedInstanceState != null) return   // recreated while the picker is open
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.group) }
        val hasCustom = MessageStore.customGroupPicPath(this, chatKey) != null
        val items = if (hasCustom) arrayOf(getString(R.string.pick_image), getString(R.string.restore_default)) else arrayOf(getString(R.string.pick_image))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.group_pic_title, title))
            .setItems(items) { _, which -> if (which == 0) pick() else reset() }
            .setNegativeButton(getString(R.string.cancel), null)
            .setOnCancelListener { finish() }
            .setOnDismissListener { if (!picking) finish() }
            .show()
    }

    private var picking = false

    private fun pick() {
        picking = true
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
            else Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try { startActivityForResult(i, REQ_PICK) } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.toast_no_picker), Toast.LENGTH_SHORT).show(); finish()
        }
    }

    private fun reset() {
        MessageStore.setCustomGroupPic(this, chatKey, null)
        applied(getString(R.string.group_pic_restored))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return finish()
        val bmp = decodeSquare(uri)
        if (bmp == null || !MessageStore.setCustomGroupPic(this, chatKey, bmp)) {
            Toast.makeText(this, getString(R.string.image_unreadable), Toast.LENGTH_SHORT).show(); return finish()
        }
        applied(getString(R.string.group_pic_changed))
    }

    private fun applied(msg: String) {
        // Re-render a notification of this group that is still showing, so the picture changes now.
        try { Notifier.refreshChatSilently(this, chatKey) } catch (e: Exception) { Diagnostics.note(this, "group pic refresh: $e") }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Center square of the picked image, at most [SAVE_PX] wide (decoded with subsampling). */
    private fun decodeSquare(uri: Uri): Bitmap? = try {
        val src = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies the photo's EXIF rotation.
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val s = minOf(info.size.width, info.size.height)
                var sample = 1
                while (s / (sample * 2) >= SAVE_PX) sample *= 2
                d.setTargetSampleSize(sample)
            }
        } else decodeLegacy(uri)
        src?.let {
            val s = minOf(it.width, it.height)
            val sq = Bitmap.createBitmap(it, (it.width - s) / 2, (it.height - s) / 2, s, s)
            if (s > SAVE_PX) Bitmap.createScaledBitmap(sq, SAVE_PX, SAVE_PX, true) else sq
        }
    } catch (_: Exception) { null }

    private fun decodeLegacy(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SAVE_PX) sample *= 2
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
