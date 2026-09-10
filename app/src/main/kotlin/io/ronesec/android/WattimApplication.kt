package io.ronesec.android

import android.app.Application
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.platform.system.AndroidPlatformPermissionChecker
import io.ronesec.android.platform.audio.AudioDiagnostics
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.PlatformPermissionChecker
import io.ronesec.android.platform.time.AndroidMonotonicClock
import io.ronesec.android.platform.time.AndroidWallClock
import io.ronesec.android.protection.ProtectionStoreWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow

class WattimApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val wallClock by lazy { AndroidWallClock() }
    val monotonicClock by lazy { AndroidMonotonicClock() }
    val audioDiagnostics by lazy { AudioDiagnostics() }

    val database by lazy {
        WattimDatabase.getInstance(this)
    }

    val policyStore by lazy {
        PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = applicationScope,
            ioDispatcher = Dispatchers.IO
        )
    }

    val protectionStoreWriter by lazy {
        ProtectionStoreWriter(
            policyStore = policyStore,
            scope = applicationScope,
            dispatcher = Dispatchers.IO
        )
    }

    val statisticsStore by lazy {
        StatisticsStore(
            database = database,
            wallClock = wallClock,
            ioDispatcher = Dispatchers.IO
        )
    }

    var customPermissionChecker: PlatformPermissionChecker? = null

    val permissionMonitor by lazy {
        val checker = customPermissionChecker ?: AndroidPlatformPermissionChecker(this)
        PermissionMonitor(checker)
    }

    var customPackageCatalog: io.ronesec.android.platform.system.PackageCatalog? = null

    val packageCatalog by lazy {
        customPackageCatalog ?: io.ronesec.android.platform.system.AndroidPackageCatalog(this)
    }

    private val _activeCoordinator = kotlinx.coroutines.flow.MutableStateFlow<io.ronesec.android.protection.InterventionCoordinator?>(null)
    val activeCoordinator: kotlinx.coroutines.flow.StateFlow<io.ronesec.android.protection.InterventionCoordinator?> = _activeCoordinator.asStateFlow()

    fun setCoordinator(coordinator: io.ronesec.android.protection.InterventionCoordinator?) {
        _activeCoordinator.value = coordinator
    }

    private val _activePresenter = kotlinx.coroutines.flow.MutableStateFlow<io.ronesec.android.platform.overlay.OverlayPresenter?>(null)
    val activePresenter: kotlinx.coroutines.flow.StateFlow<io.ronesec.android.platform.overlay.OverlayPresenter?> = _activePresenter.asStateFlow()

    fun setPresenter(presenter: io.ronesec.android.platform.overlay.OverlayPresenter?) {
        _activePresenter.value = presenter
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly trigger initial load
        policyStore
    }

    fun rehydrateOnBootOrUpdate() {
        // Access policyStore to ensure storage init and snapshot load
        policyStore.currentSnapshot
    }
}
