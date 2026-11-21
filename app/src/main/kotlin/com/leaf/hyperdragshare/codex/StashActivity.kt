package com.leaf.hyperdragshare.codex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Transparent share receiver; all content is used through the floating tray. */
class StashActivity : ComponentActivity() {
    private var pendingImport: Intent? = null
    private lateinit var importId: String
    private var shareImported = false
    private var receiveOnly = false
    private var message: String? = null
    private val importLock = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        receiveOnly = isSupportedShare(intent)
        importId = savedInstanceState?.getString("share_id") ?: UUID.randomUUID().toString()
        shareImported = savedInstanceState?.getBoolean("share_imported") == true
        pendingImport = intent.takeIf { !shareImported && receiveOnly }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        importId = UUID.randomUUID().toString()
        shareImported = false
        receiveOnly = isSupportedShare(intent)
        pendingImport = intent.takeIf(::isSupportedShare)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val resumedImport = importId
            importPendingShare()
            if (resumedImport != importId) return@launch
            if (!receiveOnly) StashOverlayPanel.show(this@StashActivity)
            else if (!shareImported) Toast.makeText(this@StashActivity,
                message ?: "保存失败，请重新分享后重试", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("share_id", importId)
        outState.putBoolean("share_imported", shareImported)
        super.onSaveInstanceState(outState)
    }

    private suspend fun importPendingShare() = importLock.withLock {
        val incoming = pendingImport ?: return@withLock
        val incomingId = importId
        message = null
        try {
            val app = applicationContext
            withContext(Dispatchers.IO) {
                val savedCount = StashStore(app).use { store ->
                    when {
                        incoming.type == "text/plain" -> {
                            val text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                                ?: throw IOException("分享内容没有文字")
                            if (text.isBlank()) throw IOException("分享的文字为空")
                            store.importText(text, incomingId)
                        }
                        incoming.type?.startsWith("image/") == true -> {
                            store.addImages(contentResolver, incoming.imageUris(), incomingId)
                        }
                        else -> throw IOException("不支持这种分享内容")
                    }
                }
                if (savedCount > 0) StashOverlayPanel.contentAdded(app, savedCount)
            }
            if (pendingImport === incoming) {
                pendingImport = null
                shareImported = true
            }
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to import shared content", error)
            message = "保存失败：${error.message ?: "无法读取分享内容"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while importing shared content", error)
            message = "没有读取分享内容的权限，请重新分享后重试。"
        }
    }

    private fun isSupportedShare(intent: Intent): Boolean =
        (intent.action == Intent.ACTION_SEND &&
            (intent.type == "text/plain" || intent.type?.startsWith("image/") == true)) ||
            (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true)

    private fun Intent.imageUris(): List<Uri> {
        val streams = if (action == Intent.ACTION_SEND_MULTIPLE) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (streams.isNotEmpty()) return streams.distinct()
        val clip = clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
    }
}
