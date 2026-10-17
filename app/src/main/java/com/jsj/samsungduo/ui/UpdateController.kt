package com.jsj.samsungduo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Where releases are published. Kept here rather than in a resource so the URL
 * that the checker talks to and the one the "release page" button opens can
 * never drift apart.
 *
 * <p>★ v85：更新检查改为指向本项目自己的仓库，不再指向上游参考项目。否则更新检查会去
 * 问上游的发布页，把原版 APK 推给用户。
 * 发布规则（updater 的判定逻辑，见 [parseRelease] / [isNewer]）：
 * tag = `v` + versionName；必须是正式 Release（prerelease/draft 不在
 * `releases/latest` 里）；必须挂一个 `.apk` 资源。
 */
private const val REPO = "Sunshinejsj/SamsungDuo"
private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

/** Opened when the user wants the notes in full, or when a download is refused. */
internal const val RELEASES_PAGE = "https://github.com/$REPO/releases"

/**
 * The repository itself — README, source and issues.
 *
 * <p>Beside the other two so it cannot drift either, and distinct from
 * [RELEASES_PAGE]: one shows what the module is, the other what changed.
 */
internal const val REPO_HOME = "https://github.com/$REPO"

/**
 * ★ v85：上游参考项目（本模块移植自的 MIUI 原版方案）。设置页"关于"里
 * 那条链接指向它 —— 与 [REPO_HOME]（本项目自己的仓库）分开，免得用户点"查看开源
 * 项目仓库"结果跳到自己的仓库。
 */
internal const val UPSTREAM_HOME = "https://github.com/yixing233/HyperDuo"

/**
 * How long the connect handshake and each read may hang.
 *
 * <p>Kept apart because they fail for different reasons: the connect is what
 * times out when GitHub's front door is unreachable, while a read is a transfer
 * that stalled mid-file.
 */
private const val CONNECT_TIMEOUT_MS = 15000
private const val READ_TIMEOUT_MS = 20000

/**
 * How many times a network step is attempted before the card reports failure.
 *
 * <p>The device's route to GitHub is genuinely flaky — the same URL alternates
 * between a 1.5 s success and a connect timeout — so a single attempt reads as a
 * broken feature when a second one would have worked.
 */
private const val ATTEMPTS = 3

/** One published release, reduced to what the card renders. */
internal data class UpdateInfo(
    /** Release tag as published, e.g. `v1.2`. */
    val tag: String,
    /** The tag without its `v`, used for comparison and display. */
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    /** The asset's numeric id, which is what the API download endpoint needs. */
    val assetId: Long,
)

/**
 * The URLs to try for the APK, best first.
 *
 * <p>`browser_download_url` points at `github.com`, and on this device that host
 * resolves to an Azure address that intermittently refuses to complete a
 * handshake — the same URL alternates between a 1.5 s success and a 15 s connect
 * timeout. The asset API endpoint serves the identical bytes through
 * `api.github.com`, which answered 10 out of 10 attempts, so it is tried first
 * and the browser URL is kept only as a fallback.
 */
private fun UpdateInfo.downloadUrls(): List<String> = buildList {
    if (assetId > 0L) add("https://api.github.com/repos/$REPO/releases/assets/$assetId")
    if (apkUrl.isNotBlank()) add(apkUrl)
}.distinct()

/** Everything the update card can be showing. */
internal sealed interface UpdateState {
    /** Nothing has been asked yet. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** The published release is not newer than the installed build. */
    data class UpToDate(val current: String) : UpdateState

    /** The repository has no published release at all, which is not an error. */
    data object NoRelease : UpdateState

    data class Available(val info: UpdateInfo, val current: String) : UpdateState

    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState

    /** Downloaded and waiting for the user to confirm in the system installer. */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState

    /**
     * @param detail the underlying reason, shown next to the localised headline.
     *   Kept raw because it is diagnostic text (an HTTP code, a socket error) that
     *   would lose its meaning if it were translated.
     */
    data class Failed(val detail: String?) : UpdateState
}

/** How an install attempt ended, so the card can explain each case differently. */
internal enum class InstallResult {
    /** The system installer is on screen; the user has the last word. */
    Started,

    /** The "install unknown apps" grant is missing and the user can give it. */
    NeedsPermission,

    /** Nothing on the device handles a package-install intent. */
    NoInstaller,
}

/**
 * Owns the update flow: check, download, hand off to the system installer.
 *
 * <p>Network work is deliberately off the main thread and every state transition
 * lands back on it, so the card can read [state] as if it were a plain value.
 *
 * <p>The controller is remembered at screen level rather than inside the About
 * tab: a LazyColumn disposes items that scroll away, and a download must not die
 * because the user flicked to the Colours tab.
 */
internal class UpdateController(private val context: Context) {

    var state: UpdateState by mutableStateOf(UpdateState.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun dispose() = scope.cancel()

    /** The installed version, shown wherever there is nothing newer to show. */
    fun currentVersion(): String = installedVersion(context)

    /**
     * Reads the newest published release and decides whether it is worth
     * offering. Re-entrant calls are ignored so that an impatient double-tap
     * cannot start two downloads of the same file.
     */
    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            val current = installedVersion(context)
            val result = withContext(Dispatchers.IO) { runCatching { retry { fetchLatest() } } }
            state = result.fold(
                onSuccess = { info ->
                    when {
                        // A repository with no release yet answers 404, which is an
                        // empty answer rather than a failure.
                        info == null -> UpdateState.NoRelease
                        isNewer(info.version, current) -> UpdateState.Available(info, current)
                        else -> UpdateState.UpToDate(current)
                    }
                },
                onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /**
     * The startup check: same query as [check], but it only speaks up when there
     * is something to say.
     *
     * <p>★ v104：进 App 就自动跑一次。与 [check] 的唯一区别是它<b>只在确实有新版本
     * 时才改写 [state]</b>，其余三种结果（没更新 / 仓库没 release / 网络失败）一律
     * 停在 [UpdateState.Idle]，用户什么都看不到。
     * 不这么做的话，开屏就先甩一条"检查失败 + HTTP 超时"给用户 —— 而更新这件事
     * 本来就不该在没结果时打扰人。
     */
    fun autoCheck() {
        if (state != UpdateState.Idle) return
        scope.launch {
            val current = installedVersion(context)
            val result = withContext(Dispatchers.IO) { runCatching { retry { fetchLatest() } } }
            // 失败与"没有 release"都当没查过：卡片继续停在 Idle，用户手动点才会出结果。
            val info = result.getOrNull() ?: return@launch
            if (isNewer(info.version, current)) state = UpdateState.Available(info, current)
        }
    }

    /**
     * Downloads the APK next to the app's cache and, on success, moves to
     * [UpdateState.Ready]. The progress fraction is only meaningful while
     * [HttpURLConnection.getContentLengthLong] is known; it reports -1 otherwise.
     */
    fun download(info: UpdateInfo) {
        if (state is UpdateState.Downloading) return
        state = UpdateState.Downloading(info, 0f)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    // A name that changes with the release, so a stale download
                    // from a previous check can never be installed by accident.
                    val file = File(dir, sanitise(info.apkName, info.version))
                    downloadFrom(info, file) { fraction ->
                        state = UpdateState.Downloading(info, fraction)
                    }
                    file
                }
            }
            state = result.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /**
     * Opens the system installer for the downloaded APK.
     *
     * <p>There is no silent path on purpose: installing a module that hooks
     * SystemUI without a visible confirmation is not something a user should be
     * able to trigger by accident.
     *
     * A boolean would flatten two unrelated refusals into one, and the caller has
     * to say something different for each: the grant is the user's to give on a
     * settings page, whereas a missing installer is not something the user can
     * fix by granting anything.
     *
     * @return [InstallResult.Started] once the system installer is on screen;
     *   [InstallResult.NeedsPermission] when the "install unknown apps" grant is
     *   still missing, in which case the caller
     *   [UpdateController.openInstallPermissionScreen]; or
     *   [InstallResult.NoInstaller] when no activity on the device would handle
     *   the intent.
     */
    fun install(file: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return InstallResult.NeedsPermission
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A missing handler is caught rather than pre-checked: on Android 11+ an
        // app's view of installed packages is filtered, so resolveActivity can
        // answer null for an installer that would in fact have opened. Only the
        // attempt itself is authoritative.
        return try {
            context.startActivity(intent)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.NoInstaller
        }
    }

    /** Sends the user to the per-app "install unknown apps" switch. */
    fun openInstallPermissionScreen() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Opens [url] in a browser.
     *
     * <p>Named for what it does rather than for the release page, because the
     * card now sends the user to two different URLs through it.
     */
    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

/**
 * Which of [a] and [b] is the newer version.
 *
 * <p>Compared segment by segment over digit runs, so `1.10` beats `1.9` — a plain
 * string compare would call them equal-length and get it backwards. A missing
 * segment counts as zero, which is what makes `1.2` and `1.2.0` the same version.
 */
internal fun isNewer(a: String, b: String): Boolean {
    val left = numericSegments(a)
    val right = numericSegments(b)
    for (i in 0 until maxOf(left.size, right.size)) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l > r
    }
    return false
}

private fun numericSegments(version: String): List<Int> =
    version.trim().removePrefix("v").removePrefix("V")
        .split(Regex("[^0-9]+"))
        .filter { it.isNotEmpty() }
        .mapNotNull { it.toIntOrNull() }

/**
 * The tag GitHub would carry for this build, and therefore the version the card
 * compares against. Read from the package rather than from `BuildConfig` so the
 * number shown in the About card and the number used for comparison are the same
 * read of the same source.
 */
private fun installedVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() } ?: "0"

/** Queries the GitHub API for the newest release, or null when none is published. */
private fun fetchLatest(): UpdateInfo? {
    val connection = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        // The API rejects requests without one, and advertises the pinned media
        // type so a future API default cannot change the shape of the reply.
        setRequestProperty("User-Agent", "SamsungDuo-Updater")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        // 404 is the normal answer for a repository whose first release has not
        // been published yet; that is an empty answer, not a failure.
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        return parseRelease(JSONObject(body))
    } finally {
        connection.disconnect()
    }
}

/**
 * Maps a release payload onto [UpdateInfo].
 *
 * <p>The first APK asset wins. GitHub lets a release carry several, and the
 * publishing workflow attaches exactly one, so anything beyond ordering by name
 * would be guessing.
 */
private fun parseRelease(json: JSONObject): UpdateInfo {
    val tag = json.optString("tag_name").ifBlank { throw IllegalStateException("tag_name missing") }
    val version = tag.trim().removePrefix("v").removePrefix("V")
    val assets = json.optJSONArray("assets")
    var apkUrl = ""
    var apkName = "SamsungDuo-$version.apk"
    var apkSize = 0L
    var assetId = 0L
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            apkUrl = asset.optString("browser_download_url")
            apkName = name
            apkSize = asset.optLong("size")
            assetId = asset.optLong("id")
            break
        }
    }
    if (apkUrl.isBlank() && assetId <= 0L) throw IllegalStateException("no apk asset")
    return UpdateInfo(
        tag = tag,
        version = version,
        notes = json.optString("body").trim(),
        pageUrl = json.optString("html_url").ifBlank { RELEASES_PAGE },
        apkUrl = apkUrl,
        apkName = apkName,
        apkSize = apkSize,
        assetId = assetId,
    )
}

/**
 * Runs [block], retrying a failure up to [ATTEMPTS] times.
 *
 * <p>Only for steps that are safe to repeat: both the metadata query and the APK
 * download are plain GETs, and a re-download overwrites its own `.part` file.
 */
private inline fun <T> retry(block: () -> T): T {
    var last: Throwable? = null
    repeat(ATTEMPTS) { attempt ->
        try {
            return block()
        } catch (t: Throwable) {
            last = t
            // Backing off keeps a host that is refusing connections from being
            // hammered, which is what turns one flaky attempt into three.
            if (attempt < ATTEMPTS - 1) Thread.sleep(400L * (attempt + 1))
        }
    }
    throw last ?: IllegalStateException("retry failed")
}

/**
 * Downloads [info]'s APK into [target], trying each candidate URL in turn.
 *
 * <p>The API endpoint is preferred over the browser URL; see
 * [UpdateInfo.downloadUrls]. A failure against one endpoint is not a failure of
 * the download as long as another one serves the bytes, which matters here
 * because only one of the two hosts is reliable from this network.
 *
 * @throws IllegalStateException when every endpoint failed, carrying the last
 *   reason so the card can show something diagnostic.
 */
private fun downloadFrom(info: UpdateInfo, target: File, onProgress: (Float) -> Unit) {
    val urls = info.downloadUrls()
    if (urls.isEmpty()) throw IllegalStateException("no download URL")
    var last: Throwable? = null
    for (url in urls) {
        try {
            retry { downloadToFile(url, target, onProgress) }
            return
        } catch (t: Throwable) {
            last = t
        }
    }
    throw last ?: IllegalStateException("download failed")
}

/**
 * Streams [url] into [target], reporting progress in `0f..1f`.
 *
 * <p>Writes to a sibling `.part` file and renames on success, so an interrupted
 * download can never leave a truncated APK that later looks installable.
 */
private fun downloadToFile(url: String, target: File, onProgress: (Float) -> Unit) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("User-Agent", "SamsungDuo-Updater")
        // The browser URL redirects to a signed CDN URL, and the API endpoint
        // does too unless the octet-stream type is asked for; the default follow
        // is what makes either work.
        instanceFollowRedirects = true
        // Without this the API answers with JSON metadata instead of the bytes.
        setRequestProperty("Accept", "application/octet-stream")
    }
    val part = File(target.parentFile, "${target.name}.part")
    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }
        val total = connection.contentLengthLong
        var copied = 0L
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) {
                        onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
                output.flush()
            }
        }
        if (total > 0 && copied != total) {
            throw IllegalStateException("truncated: $copied/$total")
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        onProgress(1f)
    } finally {
        connection.disconnect()
    }
}

/** Strips anything a filesystem would object to, and guarantees an `.apk` end. */
private fun sanitise(name: String, version: String): String {
    val cleaned = name.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }
        .joinToString("")
        .takeIf { it.isNotBlank() }
        ?: "SamsungDuo-$version.apk"
    return if (cleaned.endsWith(".apk", ignoreCase = true)) cleaned else "$cleaned.apk"
}

/** Byte count in the units the card shows it in. */
internal fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
