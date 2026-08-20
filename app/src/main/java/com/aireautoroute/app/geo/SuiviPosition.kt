package com.aireautoroute.app.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Flux des positions du téléphone, alimenté par le `LocationManager` du système. */
class SuiviPosition(private val context: Context) {

    fun permissionAccordee(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun gpsActive(): Boolean {
        val gestionnaire = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return gestionnaire?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            gestionnaire?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    @SuppressLint("MissingPermission")
    fun positions(): Flow<Location> = callbackFlow {
        val gestionnaire = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (gestionnaire == null || !permissionAccordee()) {
            close()
            return@callbackFlow
        }

        val ecouteur = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Conservé pour les appareils antérieurs à Android 11.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        val fournisseurs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { gestionnaire.isProviderEnabled(it) }.getOrDefault(false) }

        fournisseurs
            .mapNotNull { runCatching { gestionnaire.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { trySend(it) }

        fournisseurs.forEach { fournisseur ->
            runCatching {
                gestionnaire.requestLocationUpdates(
                    fournisseur,
                    INTERVALLE_MS,
                    DISTANCE_MINIMALE_M,
                    ecouteur,
                    context.mainLooper,
                )
            }
        }

        if (fournisseurs.isEmpty()) close()

        awaitClose { runCatching { gestionnaire.removeUpdates(ecouteur) } }
    }

    private companion object {
        const val INTERVALLE_MS = 5_000L
        const val DISTANCE_MINIMALE_M = 50f
    }
}
