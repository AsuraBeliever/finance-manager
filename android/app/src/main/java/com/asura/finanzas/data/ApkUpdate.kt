package com.asura.finanzas.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * The APK's half of the web's "Update" button. The web swaps its service
 * worker and reloads; a sideloaded APK cannot replace itself, so it downloads
 * the release CI attached to GitHub for that version and hands it to the
 * system installer, which asks the user to confirm (and, the first time, to
 * allow this app as a source). The release is signed with the same key, so it
 * installs over the running app and keeps its data.
 */
object ApkUpdate {
    private const val RELEASES = "https://github.com/AsuraBeliever/finance-manager/releases/download"

    /** The asset `.github/workflows/release.yml` uploads for a version tag. */
    fun apkUrl(version: String) = "$RELEASES/v$version/Broke_${version}_android.apk"

    private val client by lazy { OkHttpClient.Builder().followRedirects(true).build() }

    /**
     * Download [version]'s APK into the cache, reporting progress in 0..1 when
     * the size is known. Throws when the asset is missing — CI attaches it a
     * few minutes after the tag, so right after a deploy it may not exist yet.
     */
    suspend fun download(
        context: Context,
        version: String,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() } // only ever the one pending update
        }
        val target = File(dir, "Broke_$version.apk")
        client.newCall(Request.Builder().url(apkUrl(version)).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty body")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(total?.let { read.toFloat() / it })
                    }
                }
            }
        }
        target
    }

    /** Open the system installer on a downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
