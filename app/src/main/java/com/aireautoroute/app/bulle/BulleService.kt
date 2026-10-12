package com.aireautoroute.app.bulle

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aireautoroute.app.MainActivity
import com.aireautoroute.app.R
import com.aireautoroute.app.data.DepotDonnees
import com.aireautoroute.app.data.PositionUtilisateur
import com.aireautoroute.app.data.PreferencesUi
import com.aireautoroute.app.geo.LocalisateurPk
import com.aireautoroute.app.geo.SuiviPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Étape qui manque avant de pouvoir démarrer la bulle. */
enum class PrerequisBulle { POSITION, SUPERPOSITION, PRET }

/**
 * Service de premier plan qui suit la position et entretient la bulle flottante.
 *
 * Il doit être démarré depuis l'application au premier plan : c'est cette condition, et non la
 * permission de localisation « en arrière-plan », qui lui garde l'accès à la position quand
 * l'utilisateur passe sur une autre application. Le système l'impose aussi pour qu'une
 * notification reste visible tant que la position est lue.
 *
 * Le service ne lit que le catalogue livré et la copie locale des avis (`chargerLocalement`) :
 * il ne rafraîchit rien par le réseau, c'est l'affaire de l'application.
 */
class BulleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: BulleOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRET) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Le système laisse cinq secondes pour passer au premier plan après chaque démarrage, y
        // compris un second démarrage d'un service déjà lancé : on le fait avant toute autre chose.
        if (!passerAuPremierPlan()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay != null) return START_NOT_STICKY

        val suivi = SuiviPosition(this)
        if (!suivi.permissionAccordee() || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val bulle = BulleOverlay(
            context = this,
            onOuvrirApp = ::ouvrirApplication,
            onArreter = { stopSelf() },
        )
        try {
            bulle.afficher()
        } catch (e: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        overlay = bulle
        actifInterne.value = true

        scope.launch { suivre(bulle, suivi) }
        // Pas de redémarrage automatique : relancer un service de position sans que
        // l'utilisateur soit dans l'application serait refusé par le système, et surprenant.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay?.retirer()
        overlay = null
        actifInterne.value = false
        super.onDestroy()
    }

    private suspend fun suivre(bulle: BulleOverlay, suivi: SuiviPosition) {
        val depot = DepotDonnees(applicationContext)
        depot.definirAuteursMasques(PreferencesUi(applicationContext).auteursMasques.value)
        depot.chargerLocalement()

        var precedente: PositionUtilisateur? = null
        suivi.positions().collect { mesure ->
            // Même exigence que le bouton « Me localiser » : un point périmé ou imprécis se
            // projetterait sur la mauvaise autoroute, et la bulle l'afficherait comme certain.
            if (!mesure.fraiche || !mesure.precise) return@collect
            val catalogue = depot.catalogue.value ?: return@collect

            val resultat = LocalisateurPk.localiser(
                autoroutes = catalogue.autoroutes,
                lat = mesure.latitude,
                lon = mesure.longitude,
                capDegres = mesure.capDegres,
            )
            if (resultat == null) {
                precedente = null
                bulle.mettreAJour(ApercuBulle.AUCUNE_POSITION)
                return@collect
            }
            val position = positionPourBulle(resultat, precedente)
            precedente = position
            bulle.mettreAJour(apercuBulle(catalogue, depot.contributions.value, position))
        }
    }

    // --- Premier plan ---------------------------------------------------------

    private fun passerAuPremierPlan(): Boolean {
        creerCanal()
        val notification = construireNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(ID_NOTIFICATION, notification)
            }
            true
        } catch (e: RuntimeException) {
            // Démarrage refusé hors du premier plan, ou permission de localisation absente : le
            // système lève alors une exception plutôt que de laisser le service tourner.
            Log.w(TAG, "Service de premier plan refusé", e)
            false
        }
    }

    private fun creerCanal() {
        val gestionnaire = getSystemService(NotificationManager::class.java)
        gestionnaire.createNotificationChannel(
            NotificationChannel(CANAL, "Suivi des aires", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notification affichée tant que la bulle des aires est active."
                setShowBadge(false)
            },
        )
    }

    private fun construireNotification(): Notification {
        val ouvrir = PendingIntent.getActivity(
            this,
            0,
            intentApplication(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val arreter = PendingIntent.getService(
            this,
            1,
            Intent(this, BulleService::class.java).setAction(ACTION_ARRET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Aires d'autoroute")
            .setContentText("La bulle suit votre position pour afficher les prochaines aires.")
            .setContentIntent(ouvrir)
            .addAction(0, "Arrêter", arreter)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun intentApplication(): Intent =
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun ouvrirApplication() {
        runCatching { startActivity(intentApplication()) }
            .onFailure { Log.w(TAG, "Impossible d'ouvrir l'application depuis la bulle", it) }
    }

    companion object {
        private const val TAG = "BulleService"
        private const val CANAL = "suivi_aires"
        private const val ID_NOTIFICATION = 42
        private const val ACTION_ARRET = "com.aireautoroute.app.action.ARRET_BULLE"

        private val actifInterne = MutableStateFlow(false)

        /** `true` tant que la bulle est posée. */
        val actif: StateFlow<Boolean> = actifInterne.asStateFlow()

        fun prerequisManquant(context: Context): PrerequisBulle = when {
            !SuiviPosition(context).permissionAccordee() -> PrerequisBulle.POSITION
            !Settings.canDrawOverlays(context) -> PrerequisBulle.SUPERPOSITION
            else -> PrerequisBulle.PRET
        }

        /** Réglage système où l'utilisateur autorise l'affichage par-dessus les autres applis. */
        fun intentAutorisationSuperposition(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

        fun notificationsAutorisees(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /** À appeler depuis l'application au premier plan, sans quoi le système refuse le démarrage. */
        fun demarrer(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BulleService::class.java))
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, BulleService::class.java))
        }
    }
}
