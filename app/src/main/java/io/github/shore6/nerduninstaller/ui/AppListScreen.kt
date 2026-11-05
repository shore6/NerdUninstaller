package io.github.shore6.nerduninstaller.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shore6.nerduninstaller.data.AppItem
import io.github.shore6.nerduninstaller.data.Safety
import io.github.shore6.nerduninstaller.shizuku.ShizukuManager
import io.github.shore6.nerduninstaller.shizuku.ShizukuStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val shizuku by vm.shizukuStatus.collectAsStateWithLifecycle()
    val queue by vm.uninstallQueue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var shizukuBannerDismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // 標準アンインストール画面を 1 件ずつ順番に開く
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onStandardUninstallFinished() }
    val head = queue.firstOrNull()
    LaunchedEffect(head) {
        if (head != null) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$head"))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
            try {
                uninstallLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                vm.onStandardUninstallFinished()
            }
        }
    }

    BackHandler(enabled = state.selectionMode) { vm.clearSelection() }
    BackHandler(enabled = !state.selectionMode && searchActive) {
        searchActive = false
        vm.setQuery("")
    }

    val requestBatchUninstall: () -> Unit = {
        val targets = state.apps.filter { it.packageName in state.selected }
        if (shizuku == ShizukuStatus.READY) {
            confirm = forceUninstallRequest(targets) { keepData -> vm.forceUninstall(targets, keepData) }
        } else {
            val standard = targets.filter { it.canStandardUninstall }
            val skipped = targets.size - standard.size
            if (standard.isEmpty()) {
                vm.clearSelection()
                confirm = ConfirmRequest(
                    title = "Shizuku が必要です",
                    message = "選択したアプリはシステムアプリのため、通常の方法では削除できません。Shizuku を起動して許可してください。",
                    confirmLabel = "OK",
                    onConfirm = {},
                )
            } else {
                vm.requestStandardUninstall(standard.map { it.packageName })
                if (skipped > 0) {
                    snackbarMessage(vm, "$skipped 件のシステムアプリは Shizuku が必要なためスキップしました")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            when {
                state.selectionMode -> SelectionTopBar(
                    count = state.selected.size,
                    onClose = vm::clearSelection,
                    onSelectAll = vm::selectAllVisible,
                    onDelete = requestBatchUninstall,
                )
                searchActive -> SearchTopBar(
                    query = state.query,
                    onQueryChange = vm::setQuery,
                    onClose = {
                        searchActive = false
                        vm.setQuery("")
                    },
                )
                else -> MainTopBar(
                    state = state,
                    shizuku = shizuku,
                    onSearch = { searchActive = true },
                    onSort = vm::setSort,
                    onRefresh = vm::refresh,
                    onRestoreByName = { showRestoreDialog = true },
                    onOpenUsageSettings = { openUsageAccessSettings(context) },
                    onOpenShizuku = { openShizuku(context) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (shizuku != ShizukuStatus.READY && !shizukuBannerDismissed) {
                ShizukuBanner(
                    status = shizuku,
                    onAction = {
                        when (shizuku) {
                            ShizukuStatus.NOT_INSTALLED -> openShizukuDownload(context)
                            ShizukuStatus.NOT_RUNNING -> openShizuku(context)
                            ShizukuStatus.PERMISSION_REQUIRED -> vm.requestShizukuPermission()
                            ShizukuStatus.READY -> Unit
                        }
                    },
                    onDismiss = { shizukuBannerDismissed = true },
                )
            }

            if (!state.hasUsageAccess) {
                UsageAccessBanner(
                    canUseShizuku = shizuku == ShizukuStatus.READY,
                    onOpenSettings = { openUsageAccessSettings(context) },
                    onGrantWithShizuku = vm::grantUsageAccessViaShizuku,
                )
            }

            FilterRow(state = state, onSelect = vm::setFilter)

            val visible = state.visibleApps
            val totalSize = visible.sumOf { it.sizeBytes ?: 0L }
            Text(
                text = buildString {
                    append("${visible.size} 個のアプリ")
                    if (state.hasUsageAccess && totalSize > 0) append(" · 合計 ${formatSize(context, totalSize)}")
                    append(" · ${state.sortKey.label}")
                    append(if (state.sortDescending) " ↓" else " ↑")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.filter == AppFilter.UNINSTALLED) {
                            "削除済み（復元可能）のアプリはありません"
                        } else {
                            "アプリが見つかりません"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            selected = app.packageName in state.selected,
                            onClick = {
                                if (state.selectionMode) {
                                    vm.toggleSelection(app.packageName)
                                } else {
                                    detailPackage = app.packageName
                                }
                            },
                            onLongClick = { vm.toggleSelection(app.packageName) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }

    val detailApp = detailPackage?.let { pkg -> state.apps.firstOrNull { it.packageName == pkg } }
    LaunchedEffect(detailPackage, detailApp == null, state.loading) {
        // 削除などで一覧から消えたらシートを閉じる
        if (detailPackage != null && detailApp == null && !state.loading) detailPackage = null
    }
    if (detailApp != null) {
        AppDetailSheet(
            app = detailApp,
            shizuku = shizuku,
            onDismiss = { detailPackage = null },
            onStandardUninstall = {
                detailPackage = null
                vm.requestStandardUninstall(listOf(detailApp.packageName))
            },
            onForceUninstall = {
                confirm = forceUninstallRequest(listOf(detailApp)) { keepData ->
                    detailPackage = null
                    vm.forceUninstall(listOf(detailApp), keepData)
                }
            },
            onDisable = {
                confirm = ConfirmRequest(
                    title = "「${detailApp.label}」を無効化",
                    message = "アプリを無効化します。データは残り、いつでも有効化で元に戻せます。\n\n${detailApp.safety.description}",
                    confirmLabel = "無効化",
                    danger = detailApp.safety == Safety.DANGER,
                    onConfirm = { vm.disable(detailApp.packageName) },
                )
            },
            onEnable = { vm.enable(detailApp.packageName) },
            onRestore = { vm.restore(detailApp.packageName) },
            onForceStop = { vm.forceStop(detailApp.packageName) },
            onSetupShizuku = {
                when (shizuku) {
                    ShizukuStatus.NOT_INSTALLED -> openShizukuDownload(context)
                    ShizukuStatus.NOT_RUNNING -> openShizuku(context)
                    ShizukuStatus.PERMISSION_REQUIRED -> vm.requestShizukuPermission()
                    ShizukuStatus.READY -> Unit
                }
            },
        )
    }

    confirm?.let { request ->
        ConfirmDialog(request = request, onDismiss = { confirm = null })
    }

    if (showRestoreDialog) {
        RestoreByNameDialog(
            enabled = shizuku == ShizukuStatus.READY,
            onDismiss = { showRestoreDialog = false },
            onRestore = { pkg ->
                showRestoreDialog = false
                vm.restore(pkg)
            },
        )
    }
}

private fun snackbarMessage(vm: MainViewModel, message: String) {
    vm.postMessage(message)
}

private fun forceUninstallRequest(apps: List<AppItem>, onConfirm: (Boolean) -> Unit): ConfirmRequest {
    val worst = apps.maxByOrNull { it.safety.severity() }?.safety ?: Safety.USER
    val hasSystem = apps.any { it.isSystem }
    val title = if (apps.size == 1) "「${apps.first().label}」をアンインストール" else "${apps.size} 個のアプリをアンインストール"
    val message = buildString {
        if (apps.size > 1) {
            apps.take(8).forEach { append("・${it.label}\n") }
            if (apps.size > 8) append("…ほか ${apps.size - 8} 件\n")
            append("\n")
        }
        if (hasSystem) {
            append("システムアプリは現在のユーザーから削除されます（adb の pm uninstall --user 0 と同じ）。")
            append("「削除済み」タブからいつでも復元できます。工場出荷時リセットでも元に戻ります。\n\n")
        }
        if (worst != Safety.USER) append("危険度: ${worst.label}\n${worst.description}")
    }.trim()
    return ConfirmRequest(
        title = title,
        message = message,
        confirmLabel = "アンインストール",
        danger = worst == Safety.DANGER,
        showKeepData = true,
        onConfirm = onConfirm,
    )
}

private fun Safety.severity(): Int = when (this) {
    Safety.USER -> 0
    Safety.RECOMMENDED -> 1
    Safety.UNKNOWN -> 2
    Safety.CAUTION -> 3
    Safety.DANGER -> 4
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    state: UiState,
    shizuku: ShizukuStatus,
    onSearch: () -> Unit,
    onSort: (SortKey) -> Unit,
    onRefresh: () -> Unit,
    onRestoreByName: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text("NerdUninstaller", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (shizuku == ShizukuStatus.READY) "Shizuku 接続済み" else "Shizuku 未接続",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shizuku == ShizukuStatus.READY) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "検索") }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "並べ替え")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortKey.entries.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key.label) },
                            onClick = {
                                onSort(key)
                                sortMenu = false
                            },
                            trailingIcon = {
                                if (state.sortKey == key) {
                                    Icon(
                                        if (state.sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("再読み込み") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            moreMenu = false
                            onRefresh()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("パッケージ名を指定して復元") },
                        onClick = {
                            moreMenu = false
                            onRestoreByName()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("使用状況へのアクセス設定") },
                        onClick = {
                            moreMenu = false
                            onOpenUsageSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Shizuku を開く") },
                        onClick = {
                            moreMenu = false
                            onOpenShizuku()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("アプリ名・パッケージ名で検索") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "クリア")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "選択解除") }
        },
        title = { Text("$count 件選択中") },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, contentDescription = "すべて選択") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "アンインストール") }
        },
    )
}

@Composable
private fun FilterRow(state: UiState, onSelect: (AppFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilter.entries.forEach { filter ->
            FilterChip(
                selected = state.filter == filter,
                onClick = { onSelect(filter) },
                label = { Text("${filter.label} ${state.countFor(filter)}") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppItem, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AppIcon(app, 44.dp)
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val showTags = app.isSystem || !app.isEnabled || !app.isInstalled
            if (showTags) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (app.isSystem) {
                        Tag(
                            "システム",
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        SafetyTag(app.safety)
                    }
                    if (!app.isInstalled) {
                        Tag("削除済み", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    } else if (!app.isEnabled) {
                        Tag("無効", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatSize(context, app.sizeBytes), style = MaterialTheme.typography.labelLarge)
            Text(
                formatDate(app.lastUpdateTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShizukuBanner(status: ShizukuStatus, onAction: () -> Unit, onDismiss: () -> Unit) {
    val (message, action) = when (status) {
        ShizukuStatus.NOT_INSTALLED ->
            "プリインストールアプリを削除するには Shizuku が必要です。" to "入手"
        ShizukuStatus.NOT_RUNNING ->
            "Shizuku が起動していません。Shizuku アプリでワイヤレスデバッグ経由で起動してください。" to "開く"
        ShizukuStatus.PERMISSION_REQUIRED ->
            "Shizuku の使用を許可してください。" to "許可する"
        ShizukuStatus.READY -> return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("閉じる") }
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun UsageAccessBanner(canUseShizuku: Boolean, onOpenSettings: () -> Unit, onGrantWithShizuku: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "アプリのサイズを表示するには「使用状況へのアクセス」を許可してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.align(Alignment.End)) {
                if (canUseShizuku) {
                    TextButton(onClick = onGrantWithShizuku) { Text("Shizuku で許可") }
                }
                TextButton(onClick = onOpenSettings) { Text("設定を開く") }
            }
        }
    }
}

data class ConfirmRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val danger: Boolean = false,
    val showKeepData: Boolean = false,
    val onConfirm: (keepData: Boolean) -> Unit,
)

@Composable
private fun ConfirmDialog(request: ConfirmRequest, onDismiss: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    var keepData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column {
                Text(request.message)
                if (request.showKeepData) {
                    CheckRow(
                        checked = keepData,
                        onCheckedChange = { keepData = it },
                        label = "アプリのデータを残す (-k)",
                    )
                }
                if (request.danger) {
                    CheckRow(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        label = "端末が起動しなくなる危険性を理解しました",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !request.danger || acknowledged,
                onClick = {
                    onDismiss()
                    request.onConfirm(keepData)
                },
            ) {
                Text(
                    request.confirmLabel,
                    color = if (request.danger && acknowledged) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, color: Color = Color.Unspecified) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RestoreByNameDialog(enabled: Boolean, onDismiss: () -> Unit, onRestore: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("パッケージ名を指定して復元") },
        text = {
            Column {
                Text(
                    if (enabled) {
                        "pm uninstall --user 0 で削除したシステムアプリを復元します（cmd package install-existing）。"
                    } else {
                        "この操作には Shizuku が必要です。"
                    }
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text("パッケージ名") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = enabled && text.isNotBlank(), onClick = { onRestore(text) }) { Text("復元") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

// ---- 画面遷移ヘルパー ----

fun openUsageAccessSettings(context: Context) {
    context.safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
}

fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
    if (intent != null) context.safeStart(intent) else openShizukuDownload(context)
}

fun openShizukuDownload(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}"))
    if (!context.safeStart(market)) {
        context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
    }
}

fun Context.safeStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
