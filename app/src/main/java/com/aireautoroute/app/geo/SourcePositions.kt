package com.aireautoroute.app.geo

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.Flow

/** Une origine de positions : le fournisseur fusionné de Google, ou le `LocationManager`. */
internal interface SourcePositions {
    /**
     * Flux ouvert tant qu'il est collecté.
     *
     * Toutes les implémentations suivent la même séquence : le dernier point connu s'il est encore
     * d'actualité — c'est son âge, et lui seul, qui dira à l'appelant s'il vaut mieux qu'un aperçu
     * —, une demande immédiate de mesure fraîche, puis le suivi continu.
     */
    fun flux(): Flow<MesurePosition>
}

/**
 * Traduit un point du système.
 *
 * L'âge se calcule sur `elapsedRealtimeNanos`, l'horloge monotone démarrée au boot, et non sur
 * `Location.getTime` : cette dernière suit l'horloge murale, qu'une resynchronisation réseau ou un
 * changement de fuseau peut décaler de plusieurs heures — de quoi faire passer un point périmé
 * pour une mesure de la seconde, ou l'inverse.
 */
internal fun Location.versMesure(): MesurePosition = MesurePosition(
    latitude = latitude,
    longitude = longitude,
    ageMs = ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L)
        .coerceAtLeast(0L),
    precisionMetres = accuracy.takeIf { hasAccuracy() },
    // Un point du cache transporte le cap et la vitesse qu'il avait au moment de sa mesure : sans
    // ce filtre, un fix vieux d'une heure pris sur l'autoroute paraît parfaitement fiable.
    capDegres = bearing.takeIf {
        hasBearing() && hasSpeed() && speed > MesurePosition.VITESSE_MIN_CAP_MS
    },
)
