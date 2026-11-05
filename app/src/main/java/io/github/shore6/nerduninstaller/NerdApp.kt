package io.github.shore6.nerduninstaller

import android.app.Application
import io.github.shore6.nerduninstaller.shizuku.ShizukuManager

class NerdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
    }
}
