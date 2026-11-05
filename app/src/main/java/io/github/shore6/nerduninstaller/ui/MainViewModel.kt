package io.github.shore6.nerduninstaller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shore6.nerduninstaller.data.AppItem
import io.github.shore6.nerduninstaller.data.AppRepository
import io.github.shore6.nerduninstaller.shizuku.PackageCommands
import io.github.shore6.nerduninstaller.shizuku.ShizukuManager
import io.github.shore6.nerduninstaller.shizuku.ShizukuStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

enum class AppFilter(val label: String) {
    USER("ユーザー"),
    SYSTEM("システム"),
    DISABLED("無効化中"),
    UNINSTALLED("削除済み"),
    ALL("すべて"),
}

enum class SortKey(val label: String) {
    NAME("名前"),
    SIZE("サイズ"),
    INSTALLED("インストール日"),
    UPDATED("更新日"),
}

data class UiState(
    val loading: Boolean = true,
    val apps: List<AppItem> = emptyList(),
    val query: String = "",
    val filter: AppFilter = AppFilter.USER,
    val sortKey: SortKey = SortKey.NAME,
    val sortDescending: Boolean = false,
    val selected: Set<String> = emptySet(),
    val hasUsageAccess: Boolean = false,
    val busy: Boolean = false,
) {
    val visibleApps: List<AppItem> by lazy { computeVisible() }

    val selectionMode: Boolean get() = selected.isNotEmpty()

    fun countFor(filter: AppFilter): Int = apps.count { it.matches(filter) }

    private fun computeVisible(): List<AppItem> {
        val q = query.trim()
        val filtered = apps.filter { app ->
            app.matches(filter) && (
                q.isEmpty() ||
                    app.label.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true)
                )
        }
        val collator = Collator.getInstance(Locale.getDefault())
        val comparator: Comparator<AppItem> = when (sortKey) {
            SortKey.NAME -> Comparator { a, b -> collator.compare(a.label, b.label) }
            SortKey.SIZE -> compareBy<AppItem> { it.sizeBytes ?: -1L }
            SortKey.INSTALLED -> compareBy<AppItem> { it.firstInstallTime }
            SortKey.UPDATED -> compareBy<AppItem> { it.lastUpdateTime }
        }
        return filtered.sortedWith(if (sortDescending) comparator.reversed() else comparator)
    }
}

private fun AppItem.matches(filter: AppFilter): Boolean = when (filter) {
    AppFilter.USER -> isInstalled && !isSystem
    AppFilter.SYSTEM -> isInstalled && isSystem
    AppFilter.DISABLED -> isInstalled && !isEnabled
    AppFilter.UNINSTALLED -> !isInstalled
    AppFilter.ALL -> isInstalled
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val shizukuStatus: StateFlow<ShizukuStatus> = ShizukuManager.status

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 標準アンインストール画面で順番に処理するパッケージのキュー */
    private val _uninstallQueue = MutableStateFlow<List<String>>(emptyList())
    val uninstallQueue: StateFlow<List<String>> = _uninstallQueue.asStateFlow()

    private var loadJob: Job? = null
    private var debounceJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = it.apps.isEmpty(), hasUsageAccess = repo.hasUsageAccess()) }
            val apps = repo.loadApps()
            val previousSizes = _state.value.apps.associate { it.packageName to it.sizeBytes }
            val names = apps.mapTo(HashSet()) { it.packageName }
            _state.update { s ->
                s.copy(
                    loading = false,
                    apps = apps.map { it.copy(sizeBytes = previousSizes[it.packageName]) },
                    selected = s.selected.filterTo(HashSet()) { it in names },
                )
            }
            val sized = repo.withSizes(apps)
            _state.update { it.copy(apps = sized) }
        }
    }

    /** パッケージ変更ブロードキャストが連続で来ても 1 回だけ再読み込みする */
    fun refreshDebounced() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(600)
            refresh()
        }
    }

    fun onResume() {
        ShizukuManager.refresh()
        val access = repo.hasUsageAccess()
        val before = _state.value.hasUsageAccess
        _state.update { it.copy(hasUsageAccess = access) }
        if (access != before) refresh()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setFilter(filter: AppFilter) = _state.update { it.copy(filter = filter, selected = emptySet()) }

    fun setSort(key: SortKey) = _state.update {
        if (it.sortKey == key) {
            it.copy(sortDescending = !it.sortDescending)
        } else {
            // サイズ・日付は大きい/新しい順をデフォルトにする
            it.copy(sortKey = key, sortDescending = key != SortKey.NAME)
        }
    }

    fun toggleSelection(pkg: String) = _state.update {
        it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg)
    }

    fun selectAllVisible() = _state.update { s ->
        s.copy(selected = s.visibleApps.mapTo(HashSet()) { it.packageName })
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun postMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun findApp(pkg: String): AppItem? = _state.value.apps.firstOrNull { it.packageName == pkg }

    // ---- 標準アンインストール（Shizuku 不要） ----

    fun requestStandardUninstall(pkgs: List<String>) {
        if (pkgs.isEmpty()) return
        _uninstallQueue.value = pkgs
        clearSelection()
    }

    fun onStandardUninstallFinished() {
        _uninstallQueue.update { it.drop(1) }
        if (_uninstallQueue.value.isEmpty()) refreshDebounced()
    }

    // ---- Shizuku 経由の操作 ----

    fun forceUninstall(apps: List<AppItem>, keepData: Boolean) = runShell(apps.map { it.packageName }, "アンインストール") { pkg ->
        val app = apps.first { it.packageName == pkg }
        PackageCommands.uninstall(pkg, keepData = keepData, userOnly = app.isSystem)
    }

    fun disable(pkg: String) = runShell(listOf(pkg), "無効化") { PackageCommands.disable(it) }

    fun enable(pkg: String) = runShell(listOf(pkg), "有効化") { PackageCommands.enable(it) }

    fun restore(pkg: String) = runShell(listOf(pkg.trim()), "復元") { PackageCommands.restore(it) }

    fun forceStop(pkg: String) = runShell(listOf(pkg), "強制停止") { PackageCommands.forceStop(it) }

    fun grantUsageAccessViaShizuku() {
        viewModelScope.launch {
            val pkg = getApplication<Application>().packageName
            val result = ShizukuManager.exec(PackageCommands.grantUsageAccess(pkg))
            if (result.isSuccessful) {
                _messages.emit("使用状況へのアクセスを許可しました")
                onResume()
                refresh()
            } else {
                _messages.emit("許可に失敗しました: ${result.summary}")
            }
        }
    }

    fun requestShizukuPermission() {
        if (!ShizukuManager.requestPermission()) {
            _messages.tryEmit("権限をリクエストできません。Shizuku アプリの「承認されたアプリ」から許可してください")
        }
    }

    private fun runShell(pkgs: List<String>, actionLabel: String, command: (String) -> String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val failures = mutableListOf<String>()
            for (pkg in pkgs) {
                if (!PackageCommands.isValidPackageName(pkg)) {
                    failures += "$pkg: 不正なパッケージ名です"
                    continue
                }
                val result = ShizukuManager.exec(command(pkg))
                if (!result.isSuccessful) failures += "$pkg: ${result.summary}"
            }
            _state.update { it.copy(busy = false, selected = emptySet()) }
            val ok = pkgs.size - failures.size
            val message = if (failures.isEmpty()) {
                if (pkgs.size == 1) "$actionLabel しました" else "$actionLabel: $ok 件成功"
            } else {
                "$actionLabel: $ok 件成功 / ${failures.size} 件失敗\n${failures.first()}"
            }
            _messages.emit(message)
            refresh()
        }
    }
}
