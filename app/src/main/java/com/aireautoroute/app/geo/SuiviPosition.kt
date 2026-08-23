package com.aireautoroute.app.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

/**
 * Flux des positions du téléphone.
 *
 * La classe ne fait que choisir la meilleure source disponible sur l'appareil et exposer ses
 * mesures ; les règles décidant qu'une mesure est exploitable vivent dans [MesurePosition].
 */
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

    fun positions(): Flow<MesurePosition> = source().flux()

    /**
     * Réévalué à chaque demande : les services Google peuvent être installés, mis à jour ou
     * désactivés pendant la vie de l'application.
     */
    private fun source(): SourcePositions =
        if (FournisseurFusionne.disponible(context)) {
            FournisseurFusionne(context)
        } else {
            FournisseurSysteme(context)
        }
}
