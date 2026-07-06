package dev.melcodes.kilometre.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import kotlinx.coroutines.launch
import java.util.Locale

// App settings category: theme, app language, default GPX save folder, and the
// keep-screen-on toggle (folded here from the old single-row "Recording"
// section). Reached from the Settings hub.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()

    val appTheme by container.appTheme.collectAsStateWithLifecycle(initialValue = "system")
    val saveTreeUri by container.defaultSaveTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val keepScreenOn by container.keepScreenOn.collectAsStateWithLifecycle(initialValue = false)

    var showLanguageDialog by remember { mutableStateOf(false) }
    // Shown when tapping the save-folder row while a folder is already set,
    // offering "change" or "remove".
    var showSaveFolderDialog by remember { mutableStateOf(false) }

    // Folder picker for the default save folder. OpenDocumentTree returns a
    // tree URI; we make the read/write grant durable with
    // takePersistableUriPermission so quick-save keeps working after a
    // reboot, then store the URI. This is why no storage permission appears
    // in the manifest — the grant is scoped to this one folder.
    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            scope.launch { container.setDefaultSaveTreeUri(uri.toString()) }
        }
    }

    val paletteIcon = ImageVector.vectorResource(R.drawable.ic_palette)
    val languageIcon = ImageVector.vectorResource(R.drawable.ic_language)
    val folderIcon = ImageVector.vectorResource(R.drawable.ic_folder)
    val screenIcon = ImageVector.vectorResource(R.drawable.ic_screen)

    // The language currently in force. Empty app-locales means the user has not
    // chosen, so fall back to the system language; anything other than French
    // resolves to English (the default resources). Recomputed each composition
    // so it reflects the locale after an activity recreate. Mirrors the exact
    // logic OnboardingScreen uses, so the two never disagree.
    val appliedLang = run {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language
        if (lang == "fr") "fr" else "en"
    }
    val currentLangLabel = stringResource(
        if (appliedLang == "fr") R.string.onboarding_language_french
        else R.string.onboarding_language_english,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.settings_section_app)) },
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
                SettingsGroup(stringResource(R.string.settings_section_app)) {
                    ThemeRow(
                        icon = paletteIcon,
                        accent = AccentPurple,
                        current = appTheme,
                        onSet = { scope.launch { container.setAppTheme(it) } },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = languageIcon,
                        accent = AccentBlue,
                        title = stringResource(R.string.settings_language),
                        subtitle = currentLangLabel,
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = { showLanguageDialog = true },
                    )
                    RowDivider()
                    // Resolve the folder's display name from its tree URI. Keyed on
                    // the URI so it only re-queries the provider when it changes;
                    // runCatching guards the rare case where the grant was revoked
                    // outside the app.
                    val folderLabel = remember(saveTreeUri) {
                        saveTreeUri?.let { savePathLabel(context, it) }
                    }
                    SettingsRow(
                        icon = folderIcon,
                        accent = AccentOrange,
                        title = stringResource(R.string.settings_save_folder),
                        subtitle = folderLabel ?: stringResource(R.string.settings_save_folder_none),
                        trailingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        onClick = {
                            if (saveTreeUri == null) pickFolderLauncher.launch(null)
                            else showSaveFolderDialog = true
                        },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = screenIcon,
                        accent = AccentTeal,
                        title = stringResource(R.string.settings_keep_screen_on),
                        subtitle = stringResource(R.string.settings_keep_screen_on_hint),
                        onClick = { scope.launch { container.setKeepScreenOn(!keepScreenOn) } },
                        trailingContent = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { scope.launch { container.setKeepScreenOn(it) } },
                            )
                        },
                    )
                }
            }
        }
    }

    // Change-or-remove dialog for an already-set save folder. "Change" opens
    // the picker again; "Remove" releases the persistable grant and clears
    // the preference (which hides the quick-save menu entry). Tapping the
    // scrim cancels.
    if (showSaveFolderDialog) {
        AlertDialog(
            onDismissRequest = { showSaveFolderDialog = false },
            title = { Text(stringResource(R.string.settings_save_folder_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showSaveFolderDialog = false
                    pickFolderLauncher.launch(null)
                }) {
                    Text(stringResource(R.string.settings_save_folder_change))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveFolderDialog = false
                    saveTreeUri?.let { uriStr ->
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(uriStr),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                    }
                    scope.launch { container.setDefaultSaveTreeUri(null) }
                }) {
                    Text(stringResource(R.string.settings_save_folder_remove))
                }
            },
        )
    }

    // App-language chooser. Picking a language calls setApplicationLocales,
    // which the platform persists per-app and which recreates the activity in
    // the chosen language — so the whole UI, including this screen, re-renders
    // translated. We guard against re-applying the active language so tapping
    // the current one just closes the dialog with no needless recreate (the
    // same guard onboarding uses). No DataStore key: the locale list IS the
    // source of truth, and storing it again could drift.
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.onboarding_language_english),
                        selected = appliedLang == "en",
                        onClick = {
                            if (appliedLang != "en") {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags("en"),
                                )
                            }
                            showLanguageDialog = false
                        },
                    )
                    LanguageOption(
                        label = stringResource(R.string.onboarding_language_french),
                        selected = appliedLang == "fr",
                        onClick = {
                            if (appliedLang != "fr") {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags("fr"),
                                )
                            }
                            showLanguageDialog = false
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

// Turn a SAF tree URI into a human-readable full folder path like
// "Internal storage/Download/Kilometre" rather than just the leaf name. SAF
// never exposes a real filesystem path, but the tree document id encodes one
// as "<volume>:<relative/path>" (e.g. "primary:Download/Kilometre"). We
// resolve the volume token to its human description via StorageManager
// ("Internal storage", "SD card", …) and prefix the relative path with it, so
// the row reads as a complete path and stays unambiguous across multiple
// volumes. Falls back to the raw volume token if the description is
// unavailable, to the DocumentFile display name if the id has no path, and to
// null if the grant was revoked.
private fun savePathLabel(context: Context, treeUriString: String): String? = runCatching {
    val uri = Uri.parse(treeUriString)
    val docId = DocumentsContract.getTreeDocumentId(uri)
    val colon = docId.indexOf(':')
    val volume = if (colon >= 0) docId.substring(0, colon) else docId
    val path = if (colon >= 0) docId.substring(colon + 1) else ""
    if (path.isEmpty()) return@runCatching DocumentFile.fromTreeUri(context, uri)?.name
    val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
    val volumeName = storage?.storageVolumes
        ?.firstOrNull { if (volume == "primary") it.isPrimary else it.uuid == volume }
        ?.getDescription(context)
        ?: volume
    "$volumeName/$path"
}.getOrNull()
