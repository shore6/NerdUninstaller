package io.github.shore6.nerduninstaller.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.shore6.nerduninstaller.data.AppItem
import io.github.shore6.nerduninstaller.shizuku.ShizukuStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailSheet(
    app: AppItem,
    shizuku: ShizukuStatus,
    onDismiss: () -> Unit,
    onStandardUninstall: () -> Unit,
    onForceUninstall: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onRestore: () -> Unit,
    onForceStop: () -> Unit,
    onSetupShizuku: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shizukuReady = shizuku == ShizukuStatus.READY

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // ヘッダー
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { copyToClipboard(context, app.packageName) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "パッケージ名をコピー")
                }
            }

            // 危険度
            if (app.isSystem) {
                Spacer(Modifier.height(12.dp))
                val (container, content) = safetyColors(app.safety)
                Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("削除の危険度: ${app.safety.label}", style = MaterialTheme.typography.titleSmall)
                        Text(app.safety.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // 情報
            Spacer(Modifier.height(12.dp))
            InfoLine("バージョン", "${app.versionName.ifEmpty { "—" }} (${app.versionCode})")
            InfoLine("サイズ", formatSize(context, app.sizeBytes))
            InfoLine("インストール", formatDateTime(app.firstInstallTime))
            InfoLine("最終更新", formatDateTime(app.lastUpdateTime))
            InfoLine(
                "種類",
                buildString {
                    append(if (app.isSystem) "システムアプリ" else "ユーザーアプリ")
                    if (app.isUpdatedSystem) append("（アップデート済み）")
                    if (!app.isInstalled) append(" / 削除済み") else if (!app.isEnabled) append(" / 無効")
                },
            )
            app.sourceDir?.let { InfoLine("パス", it, monospace = true) }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (app.isInstalled) {
                // 基本操作
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val launch = remember(app.packageName, context) {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)
                    }
                    if (launch != null && app.isEnabled) {
                        ActionButton("起動", Icons.Default.PlayArrow) { context.safeStart(launch) }
                    }
                    ActionButton("アプリ情報", Icons.Default.Info) {
                        context.safeStart(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                        )
                    }
                    ActionButton("ストア", Icons.Default.Storefront) { openStore(context, app.packageName) }
                }

                Spacer(Modifier.height(16.dp))
                Text("アンインストール", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                if (app.canStandardUninstall) {
                    Button(onClick = onStandardUninstall, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (app.isUpdatedSystem) "アップデートをアンインストール" else "アンインストール")
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (shizukuReady) {
                    Button(
                        onClick = onForceUninstall,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (app.isSystem) "強制アンインストール (Shizuku)" else "確認なしでアンインストール (Shizuku)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.isEnabled) {
                            OutlinedButton(onClick = onDisable, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Block, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("無効化")
                            }
                        } else {
                            OutlinedButton(onClick = onEnable, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("有効化")
                            }
                        }
                        OutlinedButton(onClick = onForceStop, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("強制停止")
                        }
                    }
                } else if (app.isSystem) {
                    ShizukuRequiredCard(shizuku, onSetupShizuku)
                }
            } else {
                // 削除済み（現在のユーザーからのみ削除されている）
                Text(
                    "このアプリは現在のユーザーから削除されています。システムには残っているため復元できます。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (shizukuReady) {
                    Button(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("復元 (Shizuku)")
                    }
                } else {
                    ShizukuRequiredCard(shizuku, onSetupShizuku)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun ShizukuRequiredCard(status: ShizukuStatus, onSetup: () -> Unit) {
    val action = when (status) {
        ShizukuStatus.NOT_INSTALLED -> "Shizuku を入手"
        ShizukuStatus.NOT_RUNNING -> "Shizuku を開く"
        ShizukuStatus.PERMISSION_REQUIRED -> "Shizuku を許可"
        ShizukuStatus.READY -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "システムアプリの削除・無効化・復元には Shizuku が必要です。",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onSetup, modifier = Modifier.align(Alignment.End)) {
                Text(action)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            }
        }
    }
}

private fun openStore(context: Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    if (!context.safeStart(market)) {
        context.safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    // Android 13 以降はシステムがコピー完了を表示するので Toast は出さない
    cm.setPrimaryClip(ClipData.newPlainText("package", text))
}
