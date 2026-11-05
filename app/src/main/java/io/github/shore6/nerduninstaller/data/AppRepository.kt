package io.github.shore6.nerduninstaller.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val flags = PackageManager.PackageInfoFlags.of(
            (PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS).toLong()
        )
        pm.getInstalledPackages(flags).mapNotNull { pi ->
            val ai: ApplicationInfo = pi.applicationInfo ?: return@mapNotNull null
            val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            AppItem(
                packageName = pi.packageName,
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName),
                versionName = pi.versionName ?: "",
                versionCode = pi.longVersionCode,
                isSystem = isSystem,
                isUpdatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                isEnabled = ai.enabled,
                isInstalled = ai.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                firstInstallTime = pi.firstInstallTime,
                lastUpdateTime = pi.lastUpdateTime,
                sourceDir = ai.sourceDir,
                safety = SafetyDb.classify(pi.packageName, isSystem),
                appInfo = ai,
            )
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /** アプリ本体 + データ + キャッシュの合計サイズを付与する（使用状況へのアクセスが必要） */
    suspend fun withSizes(apps: List<AppItem>): List<AppItem> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext apps
        val ssm = context.getSystemService(StorageStatsManager::class.java)
            ?: return@withContext apps
        val user = Process.myUserHandle()
        apps.map { app ->
            if (!app.isInstalled) return@map app
            val size = try {
                val stats = ssm.queryStatsForPackage(app.appInfo.storageUuid, app.packageName, user)
                stats.appBytes + stats.dataBytes + stats.cacheBytes
            } catch (e: Exception) {
                null
            }
            app.copy(sizeBytes = size)
        }
    }
}
