package com.camerapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Network utilities for checking connectivity status.
 * Note: Speech recognition can work offline, so network checks should only be used
 * for features that actually require internet connectivity (like cloud services).
 */
object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun isCellularConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    /**
     * Checks if the network is suitable for cloud-based services.
     * This should NOT be used for speech recognition which works offline.
     */
    fun isNetworkSuitableForCloudServices(context: Context): Boolean {
        return isNetworkAvailable(context)
    }
}