package ru.darkcat.camera

import android.app.Application
import ru.darkcat.camera.data.MediaDatabase
import ru.darkcat.camera.data.VaultRepository

class DarkCatApplication : Application() {
    val vaultRepository: VaultRepository by lazy {
        VaultRepository(this, MediaDatabase.getInstance(this))
    }
}
