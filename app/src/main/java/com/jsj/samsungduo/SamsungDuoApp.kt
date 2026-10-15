package com.jsj.samsungduo

import android.app.Application
import android.util.Log
import com.jsj.samsungduo.ui.SettingsRepository
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Holds the connection to the Xposed framework and keeps the module's remote
 * preferences in step with the settings the user picks here.
 *
 * <p>The service can bind late (the framework process comes up whenever it
 * likes) and can die at any moment, so everything that wants to write has to go
 * through {@link #xposedService} rather than capture a reference once.
 */
class SamsungDuoApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        // Must happen in onCreate: the framework broadcasts its binder to
        // whoever registered by the time the process starts.
        runCatching { XposedServiceHelper.registerListener(this) }
            .onFailure { Log.w(TAG, "cannot register service listener", it) }
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        // The app may have been configured while the framework was absent, so
        // replay everything instead of relying on write-through alone.
        runCatching { SettingsRepository(this).syncAllToFramework() }
            .onFailure { Log.w(TAG, "cannot push settings to framework", it) }
        listeners.forEach { it(service) }
    }

    override fun onServiceDied(service: XposedService) {
        // Only clear if nobody rebound in the meantime.
        if (xposedService === service) {
            xposedService = null
        }
        listeners.forEach { it(null) }
    }

    companion object {
        private const val TAG = "SamsungDuo"

        @Volatile
        var xposedService: XposedService? = null
            private set

        /** Notified with the current service (or null) whenever the binding changes. */
        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

        fun addServiceListener(listener: (XposedService?) -> Unit) {
            listeners.add(listener)
            listener(xposedService)
        }

        fun removeServiceListener(listener: (XposedService?) -> Unit) {
            listeners.remove(listener)
        }
    }
}
