package dev.melcodes.kilometre.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.data.network.GithubRelease
import dev.melcodes.kilometre.data.network.GithubUpdateClient
import kotlinx.coroutines.launch
import java.io.File

// Public repository, opened by the "Source code" row and used as the update
// source. Hardcoding the URL is fine — it is a public address, not a secret.
private const val REPO_URL = "https://github.com/melcodesdev/kilometre"

// Outcome of a tap on "Check for updates" / "What's new". Drives a result dialog.
private sealed interface UpdateDialog {
    data class Available(val release: GithubRelease, val installedName: String) : UpdateDialog
    data class UpToDate(val installedName: String, val latest: GithubRelease?) : UpdateDialog
    data class WhatsNew(val release: GithubRelease?) : UpdateDialog
    data object Failed : UpdateDialog
}

// About settings category: app name + version, crash log, and an Updates group
// (source-code link, manual update check, and release notes). The update check
// and "what's new" call the public GitHub Releases API — the app's only network
// use, behind an explicit tap. Reached from the Settings hub.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // App version + versionCode from PackageManager — avoids needing buildConfig.
    val installed = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            InstalledVersion(pInfo.versionName ?: "?", pInfo.longVersionCode.toInt())
        } catch (_: Exception) {
            // getPackageInfo can throw in odd reinstall states; fall back rather
            // than crash the screen.
            InstalledVersion("?", -1)
        }
    }
    val appVersion = stringResource(R.string.settings_version, installed.name, installed.code)

    val crashLogFile = remember(context) { File(context.filesDir, KilometreApp.CRASH_LOG_FILE) }
    val crashLogExists = remember(crashLogFile) { crashLogFile.exists() }

    var checking by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<UpdateDialog?>(null) }

    val codeIcon = ImageVector.vectorResource(R.drawable.ic_code)
    val updateIcon = ImageVector.vectorResource(R.drawable.ic_update)

    // Compare by versionCode (not versionName) per the project's versioning rule.
    fun runCheck() {
        scope.launch {
            checking = true
            val releases = GithubUpdateClient.fetchReleases()
            checking = false
            dialog = when {
                releases == null -> UpdateDialog.Failed
                else -> {
                    val newest = releases.filter { it.versionCode != null }
                        .maxByOrNull { it.versionCode!! }
                    if (newest != null && newest.versionCode!! > installed.code) {
                        UpdateDialog.Available(newest, installed.name)
                    } else {
                        UpdateDialog.UpToDate(installed.name, releases.firstOrNull())
                    }
                }
            }
        }
    }

    fun showWhatsNew() {
        scope.launch {
            checking = true
            val releases = GithubUpdateClient.fetchReleases()
            checking = false
            dialog = if (releases == null) UpdateDialog.Failed
            else UpdateDialog.WhatsNew(releases.firstOrNull())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.settings_section_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                SettingsGroup(stringResource(R.string.settings_section_about)) {
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        accent = AccentSlate,
                        title = stringResource(R.string.app_name),
                        subtitle = appVersion,
                    )
                    RowDivider()
                    if (crashLogExists) {
                        SettingsRow(
                            icon = Icons.Filled.Warning,
                            accent = AccentRed,
                            title = stringResource(R.string.settings_crash_log_share),
                            subtitle = stringResource(R.string.settings_crash_log_share_hint),
                            trailingIcon = Icons.Filled.Share,
                            onClick = { shareCrashLog(context, crashLogFile) },
                        )
                    } else {
                        SettingsRow(
                            icon = Icons.Filled.Info,
                            accent = AccentSlate,
                            title = stringResource(R.string.settings_crash_log_none),
                        )
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.settings_section_updates)) {
                    SettingsRow(
                        icon = codeIcon,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_source_code),
                        subtitle = stringResource(R.string.settings_source_code_sub),
                        onClick = { openUrl(context, REPO_URL) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = updateIcon,
                        accent = AccentGreen,
                        title = stringResource(R.string.settings_check_updates),
                        subtitle = stringResource(R.string.settings_check_updates_sub),
                        onClick = { if (!checking) runCheck() },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        accent = AccentPurple,
                        title = stringResource(R.string.settings_whats_new),
                        subtitle = stringResource(R.string.settings_whats_new_sub),
                        onClick = { if (!checking) showWhatsNew() },
                    )
                }
            }
        }
    }

    if (checking) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            icon = { CircularProgressIndicator() },
            text = { Text(stringResource(R.string.update_checking)) },
        )
    }

    dialog?.let { d ->
        UpdateResultDialog(
            dialog = d,
            onDownload = { url -> openUrl(context, url); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

// One installed-version snapshot read from PackageManager.
private data class InstalledVersion(val name: String, val code: Int)

@Composable
private fun UpdateResultDialog(
    dialog: UpdateDialog,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Title, body, optional release notes, and an optional download action,
    // resolved per outcome.
    val title: String
    val body: String
    var notes: String? = null
    var downloadUrl: String? = null

    when (dialog) {
        is UpdateDialog.Available -> {
            title = stringResource(R.string.update_available_title)
            body = stringResource(R.string.update_available_body, dialog.release.name, dialog.installedName)
            notes = dialog.release.notes.ifBlank { null }
            // Prefer the APK asset; fall back to the release page if none.
            downloadUrl = dialog.release.apkUrl ?: dialog.release.htmlUrl
        }
        is UpdateDialog.UpToDate -> {
            title = stringResource(R.string.update_uptodate_title)
            body = stringResource(R.string.update_uptodate_body, dialog.installedName)
            notes = dialog.latest?.notes?.ifBlank { null }
        }
        is UpdateDialog.WhatsNew -> {
            title = stringResource(R.string.settings_whats_new)
            body = dialog.release?.name ?: stringResource(R.string.update_no_releases)
            notes = dialog.release?.notes?.ifBlank { null }
        }
        UpdateDialog.Failed -> {
            title = stringResource(R.string.update_failed_title)
            body = stringResource(R.string.update_failed_body)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Body line, then the release notes in a height-capped scroll so a
            // long changelog doesn't push the buttons off-screen.
            androidx.compose.foundation.layout.Column {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                if (notes != null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            if (downloadUrl != null) {
                TextButton(onClick = { onDownload(downloadUrl) }) {
                    Text(stringResource(R.string.update_download))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dismiss))
                }
            }
        },
        dismissButton = if (downloadUrl != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_dismiss)) } }
        } else null,
    )
}

// Open a URL in the user's browser. Wrapped because a device without any
// browser would otherwise throw ActivityNotFoundException.
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // No browser/handler installed — nothing actionable, so swallow quietly.
    }
}

// Share the local crash log via a FileProvider URI. clipData carries the read
// grant to the share-sheet preview process, which EXTRA_STREAM alone does not.
private fun shareCrashLog(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
