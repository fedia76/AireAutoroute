package com.aireautoroute.app.geo

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Positions obtenues du fournisseur fusionné des services Google — celui dont se servent Maps et
 * Waze.
 *
 * À la différence du `LocationManager`, il combine GNSS, Wi-Fi et cellulaire, et en priorité haute
 * il allume réellement la puce pour produire un point neuf au lieu de relire un cache.
 */
internal class FournisseurFusionne(private val context: Context) : SourcePositions {

    @SuppressLint("MissingPermission")
    override fun flux(): Flow<MesurePosition> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // 1. Aperçu immédiat : le dernier point connu, avec son âge.
        runCatching {
            client.lastLocation.addOnSuccessListener { location ->
                location?.versMesure()
                    ?.takeIf { it.ageMs <= MesurePosition.AGE_CACHE_MAX_MS }
                    ?.let { trySend(it) }
            }
        }

        // 2. Une mesure fraîche : `maxUpdateAge = 0` interdit au fournisseur de répondre avec un
        // point déjà en mémoire, il doit en produire un.
        val annulation = CancellationTokenSource()
        runCatching {
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(0L)
                    .setDurationMillis(DUREE_MESURE_MS)
                    .build(),
                annulation.token,
            ).addOnSuccessListener { location ->
                location?.let { trySend(it.versMesure()) }
            }
        }

        // 3. Suivi continu à la seconde : c'est ce rythme qui finit par livrer un cap exploitable.
        val rappel = object : LocationCallback() {
            override fun onLocationResult(resultat: LocationResult) {
                resultat.locations.forEach { trySend(it.versMesure()) }
            }
        }
        val demande = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALLE_MS)
            .setMinUpdateIntervalMillis(INTERVALLE_MINIMAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { client.requestLocationUpdates(demande, rappel, Looper.getMainLooper()) }

        awaitClose {
            annulation.cancel()
            runCatching { client.removeLocationUpdates(rappel) }
        }
    }

    companion object {
        private const val INTERVALLE_MS = 1_000L
        private const val INTERVALLE_MINIMAL_MS = 500L
        private const val DUREE_MESURE_MS = 30_000L

        /**
         * Les services Google manquent sur une partie du parc (téléphones dégooglisés, Huawei
         * récents) : la dépendance est compilée dans l'application, mais son usage reste
         * conditionné à leur présence effective sur l'appareil.
         */
        fun disponible(context: Context): Boolean = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
}
