package io.github.shore6.nerduninstaller;

// Shizuku の UserService として shell 権限 (uid 2000) のプロセスで動くサービス
interface IShellService {
    // Shizuku が予約しているトランザクションコード
    void destroy() = 16777114;

    // コマンドを実行し「終了コード\n出力」を返す
    String exec(String command) = 1;
}
