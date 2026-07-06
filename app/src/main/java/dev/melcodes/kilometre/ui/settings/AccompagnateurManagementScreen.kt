package dev.melcodes.kilometre.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.domain.models.Accompagnateur
import kotlinx.coroutines.launch

// Manages the list of accompagnateurs for the driver. Supports add, edit,
// and delete. Delete requires a second confirmation tap in an AlertDialog
// so a misclick does not immediately remove someone.
//
// The screen reads from container.accompagnateurs (Room-backed Flow) so
// the list refreshes automatically after any mutation.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccompagnateurManagementScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()
    val accompagnateurs by container.accompagnateurs.collectAsStateWithLifecycle(initialValue = emptyList())

    // Dialog state. editTarget = null means the add dialog; non-null means edit.
    var showAddEdit by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Accompagnateur?>(null) }
    var deleteTarget by remember { mutableStateOf<Accompagnateur?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.settings_accompagnateurs_title)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editTarget = null
                    showAddEdit = true
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add_accompagnateur),
                )
            }
        },
    ) { innerPadding ->
        if (accompagnateurs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_accompagnateur_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(accompagnateurs, key = { it.id }) { acc ->
                    AccompagnateurRow(
                        accompagnateur = acc,
                        onEdit = {
                            editTarget = acc
                            showAddEdit = true
                        },
                        onDelete = { deleteTarget = acc },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddEdit) {
        AddEditDialog(
            existing = editTarget,
            onSave = { name, relation ->
                scope.launch {
                    val target = editTarget
                    if (target == null) {
                        container.addAccompagnateur(name, relation)
                    } else {
                        container.updateAccompagnateur(target.copy(name = name, relation = relation))
                    }
                }
                showAddEdit = false
            },
            onDismiss = { showAddEdit = false },
        )
    }

    val toDelete = deleteTarget
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(stringResource(R.string.settings_accompagnateur_delete_confirm, toDelete.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { container.deleteAccompagnateur(toDelete) }
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.cd_delete_accompagnateur))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

// One accompagnateur row: name and relation on the left, edit and delete
// icon buttons on the right. Edit opens the add/edit dialog pre-filled;
// delete asks for confirmation first.
@Composable
private fun AccompagnateurRow(
    accompagnateur: Accompagnateur,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = accompagnateur.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = accompagnateur.relation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_accompagnateur),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_accompagnateur),
                )
            }
        }
    }
}

// Add / edit dialog. When `existing` is null the title says "Add"; when
// it's an Accompagnateur the fields are pre-filled and the title says "Edit".
@Composable
private fun AddEditDialog(
    existing: Accompagnateur?,
    onSave: (name: String, relation: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var relation by remember(existing) { mutableStateOf(existing?.relation ?: "") }

    val titleRes = if (existing == null) {
        R.string.settings_accompagnateur_add_title
    } else {
        R.string.settings_accompagnateur_edit_title
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_accompagnateur_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text(stringResource(R.string.settings_accompagnateur_relation_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && relation.isNotBlank()) {
                        onSave(name.trim(), relation.trim())
                    }
                },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
