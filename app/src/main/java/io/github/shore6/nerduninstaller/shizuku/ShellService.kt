package io.github.shore6.nerduninstaller.shizuku

import androidx.annotation.Keep
import io.github.shore6.nerduninstaller.IShellService
import kotlin.system.exitProcess

/**
 * Shizuku の UserService。Shizuku によって shell 権限 (adb と同じ) のプロセスで起動される。
 * ここで実行した `pm` コマンドは adb shell から実行したのと同じ扱いになる。
 */
@Keep
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return "-1\nempty command"
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            "$code\n$output"
        } catch (e: Exception) {
            "-1\n${e}"
        }
    }
}
