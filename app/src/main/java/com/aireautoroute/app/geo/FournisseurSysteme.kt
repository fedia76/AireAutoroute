package com.aireautoroute.app.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Positions obtenues du `LocationManager` d'Android, sans passer par les services Google.
 *
 * C'est le mode de repli : il fonctionne partout, mais ne fusionne pas les sources et met plus
 * longtemps à converger que le fournisseur de [FournisseurFusionne].
 */
internal class FournisseurSysteme(private val context: Context) : SourcePositions {

    @SuppressLint("MissingPermission")
    override fun flux(): Flow<MesurePosition> = callbackFlow {
        val gestionnaire = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (gestionnaire == null) {
            close()
            return@callbackFlow
        }

        val fournisseurs = FOURNISSEURS.filter {
            runCatching { gestionnaire.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (fournisseurs.isEmpty()) {
            close()
            return@callbackFlow
        }

        // 1. Aperçu immédiat : le dernier point connu, et seulement s'il n'est pas hors de
        // propos. Son âge part avec lui, c'est ce qui empêche l'appelant d'en faire une position
        // courante.
        fournisseurs
            .mapNotNull { runCatching { gestionnaire.getLastKnownLocation(it) }.getOrNull() }
            .map { it.versMesure() }
            .filter { it.ageMs <= MesurePosition.AGE_CACHE_MAX_MS }
            .minByOrNull { it.ageMs }
            ?.let { trySend(it) }

        val executeur = ContextCompat.getMainExecutor(context)

        // 2. Une mesure fraîche, tout de suite : `getCurrentLocation` réveille le fournisseur pour
        // produire un point neuf, là où `getLastKnownLocation` se contente de relire le cache.
        val annulation = CancellationSignal()
        fournisseurs.forEach { fournisseur ->
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    gestionnaire,
                    fournisseur,
                    annulation,
                    executeur,
                    Consumer<Location?> { location ->
                        location?.let { trySend(it.versMesure()) }
                    },
                )
            }
        }

        // 3. Puis le suivi continu, sans filtre de temps ni de distance : une localisation
        // ponctuelle attend le prochain point, pas le prochain déplacement de cinquante mètres.
        val ecouteur = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.versMesure())
            }

            @Deprecated("Conservé pour les appareils antérieurs à Android 11.")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        fournisseurs.forEach { fournisseur ->
            runCatching {
                gestionnaire.requestLocationUpdates(
                    fournisseur,
                    0L,
                    0f,
                    ecouteur,
                    context.mainLooper,
                )
            }
        }

        awaitClose {
            annulation.cancel()
            runCatching { gestionnaire.removeUpdates(ecouteur) }
        }
    }

    private companion object {
        val FOURNISSEURS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }
}
