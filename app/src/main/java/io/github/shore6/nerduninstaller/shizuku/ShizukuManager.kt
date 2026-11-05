package io.github.shore6.nerduninstaller.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.shore6.nerduninstaller.BuildConfig
import io.github.shore6.nerduninstaller.IShellService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY,
}

data class ShellResult(val exitCode: Int, val output: String) {
    val isSuccessful: Boolean
        get() = exitCode == 0 &&
            !output.contains("Failure") &&
            !output.contains("Exception") &&
            !output.startsWith("Error")

    /** エラー表示用の 1 行 */
    val summary: String
        get() = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: "終了コード $exitCode"
}

object ShizukuManager {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 0x5A1

    private lateinit var appContext: Context
    private var initialized = false

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var service: IShellService? = null
    private var pending: CompletableDeferred<IShellService>? = null

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val s = IShellService.Stub.asInterface(binder)
            service = s
            pending?.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun isShizukuInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun refresh() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        val alive = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
        if (!alive) {
            return if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        }
        return try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.READY
            } else {
                ShizukuStatus.PERMISSION_REQUIRED
            }
        } catch (e: Throwable) {
            ShizukuStatus.NOT_RUNNING
        }
    }

    /**
     * Shizuku に権限をリクエストする。
     * @return リクエストできなかった（「今後表示しない」で拒否済みなど）場合 false
     */
    fun requestPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refresh()
                return true
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) return false
            Shizuku.requestPermission(REQUEST_CODE)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun obtainService(): IShellService = withContext(Dispatchers.Main) {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return@withContext it }
        val deferred = pending ?: CompletableDeferred<IShellService>().also {
            pending = it
            Shizuku.bindUserService(serviceArgs, connection)
        }
        try {
            withTimeout(15_000) { deferred.await() }
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** shell 権限でコマンドを実行する */
    suspend fun exec(command: String): ShellResult {
        refresh()
        if (_status.value != ShizukuStatus.READY) {
            return ShellResult(-1, "Shizuku が利用できません")
        }
        return try {
            val s = obtainService()
            val raw = withContext(Dispatchers.IO) { s.exec(command) } ?: ""
            val nl = raw.indexOf('\n')
            val codeText = if (nl < 0) raw else raw.substring(0, nl)
            val output = if (nl < 0) "" else raw.substring(nl + 1).trim()
            ShellResult(codeText.trim().toIntOrNull() ?: -1, output)
        } catch (e: TimeoutCancellationException) {
            ShellResult(-1, "Shizuku サービスへの接続がタイムアウトしました")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ShellResult(-1, e.message ?: e.toString())
        }
    }
}
