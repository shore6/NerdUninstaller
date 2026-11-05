package io.github.shore6.nerduninstaller.shizuku

/** Shizuku (shell 権限) で実行する pm / cmd コマンド */
object PackageCommands {

    private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

    /** シェルに渡す前の検証（コマンドインジェクション対策） */
    fun isValidPackageName(pkg: String): Boolean = PACKAGE_REGEX.matches(pkg)

    /**
     * @param userOnly true の場合 `--user 0`（現在のユーザーからのみ削除）。
     *                 システムアプリはこの方法でしか消せない。工場出荷時リセットで元に戻る。
     */
    fun uninstall(pkg: String, keepData: Boolean, userOnly: Boolean): String = buildString {
        append("pm uninstall ")
        if (keepData) append("-k ")
        if (userOnly) append("--user 0 ")
        append(pkg)
    }

    fun disable(pkg: String) = "pm disable-user --user 0 $pkg"

    fun enable(pkg: String) = "pm enable --user 0 $pkg"

    /** `pm uninstall --user 0` で消したアプリを復元する */
    fun restore(pkg: String) = "cmd package install-existing --user 0 $pkg"

    fun forceStop(pkg: String) = "am force-stop $pkg"

    fun grantUsageAccess(selfPkg: String) = "appops set $selfPkg GET_USAGE_STATS allow"
}
