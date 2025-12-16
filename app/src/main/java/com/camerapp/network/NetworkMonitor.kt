package com.camerapp.network

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.camerapp.NetworkUtils
import com.camerapp.NoConnectionSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private var noConnectionSheet: NoConnectionSheet? = null
    private var hasShownOfflineMessage = false
    private var isMonitoring = false

    interface NetworkMonitorCallback {
        fun onConnectionRestored()
        fun onConnectionLost()
    }

    private var callback: NetworkMonitorCallback? = null

    fun setCallback(callback: NetworkMonitorCallback) {
        this.callback = callback
    }

    fun startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Network monitoring already started")
            return
        }

        isMonitoring = true
        hasShownOfflineMessage = false

        // Initialize NetworkUtils if not already done
        NetworkUtils.initialize(context)

        lifecycleScope.launch {
            NetworkUtils.isConnected.collectLatest { isConnected ->
                handleNetworkStateChange(isConnected)
            }
        }

        Log.d(TAG, "Network monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        NetworkUtils.unregister()

        // Dismiss any active sheet
        noConnectionSheet?.dismiss()
        noConnectionSheet = null

        Log.d(TAG, "Network monitoring stopped")
    }

    private fun handleNetworkStateChange(isConnected: Boolean) {
        if (isConnected) {
            handleConnectionRestored()
        } else {
            handleConnectionLost()
        }
    }

    private fun handleConnectionRestored() {
        Log.d(TAG, "Network connection restored")

        // Dismiss offline sheet if showing
        noConnectionSheet?.dismiss()
        noConnectionSheet = null
        hasShownOfflineMessage = false

        callback?.onConnectionRestored()
    }

    private fun handleConnectionLost() {
        Log.d(TAG, "Network connection lost")

        // Show offline message once per disconnect cycle
        if (!hasShownOfflineMessage) {
            hasShownOfflineMessage = true

            // Small delay to avoid immediate show on app start
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isMonitoring && !NetworkUtils.isConnected.value) {
                    showNoConnectionSheet()
                }
            }, 2000)
        }

        callback?.onConnectionLost()
    }

    private fun showNoConnectionSheet() {
        if (noConnectionSheet != null) {
            Log.w(TAG, "No connection sheet already showing")
            return
        }

        noConnectionSheet = NoConnectionSheet.show(fragmentManager)
        noConnectionSheet?.setOnDismissListener(object : NoConnectionSheet.OnDismissListener {
            override fun onDismissClick() {
                noConnectionSheet = null
                // User acknowledged offline mode - don't show again until next connection loss
            }
        })

        Log.d(TAG, "No connection sheet shown")
    }

    // Network status methods
    fun isCurrentlyConnected(): Boolean {
        return NetworkUtils.isConnected.value
    }

    fun getConnectionType(): String {
        return NetworkUtils.getConnectionType(context)
    }

    fun getNetworkInfo(): String {
        return if (isCurrentlyConnected()) {
            "Network: Connected (${getConnectionType()})"
        } else {
            "Network: Disconnected"
        }
    }

    // Manual control methods
    fun forceShowOfflineSheet() {
        if (noConnectionSheet == null) {
            showNoConnectionSheet()
        }
    }

    fun dismissOfflineSheet() {
        noConnectionSheet?.dismiss()
        noConnectionSheet = null
    }

    fun resetOfflineMessageFlag() {
        hasShownOfflineMessage = false
    }

    // Lifecycle
    fun cleanup() {
        stopMonitoring()
        callback = null
        Log.d(TAG, "NetworkMonitor cleaned up")
    }
}