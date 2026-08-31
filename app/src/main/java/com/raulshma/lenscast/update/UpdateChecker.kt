package com.raulshma.lenscast.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.Keep
import com.raulshma.lenscast.BuildConfig
import com.raulshma.lenscast.update.model.GitHubAsset
import com.raulshma.lenscast.update.model.GitHubRelease
import com.raulshma.lenscast.update.model.UpdateCheckResult
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }
    private val manifestAdapter by lazy { moshi.adapter(TestUpdateManifest::class.java) }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "DarkCat-Camera-LensCast-Test")
                instanceFollowRedirects = true
            }
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return@withContext UpdateCheckResult.Error("Update manifest returned HTTP $responseCode")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val manifest = manifestAdapter.fromJson(body)
                ?: return@withContext UpdateCheckResult.Error("Failed to parse update manifest")
            if (manifest.channel != "lenscast-test" || manifest.apk.isBlank() || manifest.sha256.isBlank()) {
                return@withContext UpdateCheckResult.Error("Invalid DarkCat test update manifest")
            }
            val release = GitHubRelease(
                tagName = manifest.versionName,
                name = "DarkCat Camera ${manifest.versionName}",
                body = "DarkCat Camera LensCast test channel (${manifest.gitSha})",
                htmlUrl = manifest.apk,
                assets = listOf(GitHubAsset(manifest.apk.substringAfterLast('/'), manifest.apk, 0L)),
            )
            val currentVersion = getAppVersionName()
            val remoteVersion = manifest.versionName.trimStart('v')
            if (!isNewerVersion(manifest.versionName, currentVersion)) {
                return@withContext UpdateCheckResult.UpToDate(remoteVersion, currentVersion)
            }
            Log.d(TAG, "Controlled test update available: $remoteVersion")
            UpdateCheckResult.UpdateAvailable(release, release.assets.first())
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateCheckResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getAppVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: PackageManager.NameNotFoundException) { "0.0.0" }

    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remote = remoteTag.trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
        val local = localVersion.trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}

@Keep
private data class TestUpdateManifest(
    @param:Json(name = "versionName") val versionName: String,
    @param:Json(name = "channel") val channel: String,
    @param:Json(name = "gitSha") val gitSha: String,
    @param:Json(name = "apk") val apk: String,
    @param:Json(name = "sha256") val sha256: String,
)
