package io.github.shore6.nerduninstaller.data

import android.content.pm.ApplicationInfo

data class AppItem(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    /** プリインストール（システム領域にある）アプリ */
    val isSystem: Boolean,
    /** システムアプリにアップデートが当たっている */
    val isUpdatedSystem: Boolean,
    val isEnabled: Boolean,
    /** 現在のユーザーにインストールされているか（false = `pm uninstall --user 0` 済みなど） */
    val isInstalled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val sourceDir: String?,
    val sizeBytes: Long? = null,
    val safety: Safety,
    val appInfo: ApplicationInfo,
) {
    /** 標準のアンインストール画面で削除（またはアップデート削除）できるか */
    val canStandardUninstall: Boolean get() = isInstalled && (!isSystem || isUpdatedSystem)
}
