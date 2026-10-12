package com.aireautoroute.app.bulle

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aireautoroute.app.data.AireResume
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La bulle elle-même : une pastille déplaçable posée par-dessus les autres applications, qui
 * s'ouvre au toucher sur les trois prochaines aires.
 *
 * Elle est faite de vues classiques et non de Compose. Une fenêtre posée par un service n'a ni
 * activité ni cycle de vie à elle ; Compose exigerait de lui en fabriquer un à la main. Les
 * couleurs sont fixes : la bulle doit rester lisible sur n'importe quelle appli, claire ou
 * sombre, et ne suit donc pas l'habillage choisi dans l'application.
 */
internal class BulleOverlay(
    private val context: Context,
    private val onOuvrirApp: () -> Unit,
    private val onArreter: () -> Unit,
) {

    private val fenetres = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val densite = context.resources.displayMetrics.density
    private val largeurEcran = context.resources.displayMetrics.widthPixels

    private val racine = FrameLayout(context)
    private val pastille = creerPastille()
    private val carte = creerCarte()
    private val entete = TextView(context)
    private val lignes = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val textePastille = TextView(context)
    private val sousTextePastille = TextView(context)

    private val parametres = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Ni focus ni capture du clavier : la bulle ne doit jamais gêner l'appli de navigation.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (largeurEcran - dp(LARGEUR_PASTILLE_DP) - dp(8)).coerceAtLeast(0)
        y = dp(200)
    }

    private var attachee = false

    init {
        construirePastilleEtCarte()
        racine.addView(pastille)
        racine.addView(carte)
        carte.visibility = View.GONE
    }

    fun afficher() {
        if (attachee) return
        try {
            fenetres.addView(racine, parametres)
            attachee = true
        } catch (e: RuntimeException) {
            // Permission retirée entre-temps, ou fenêtre refusée par le système : sans bulle, le
            // service n'a plus de raison d'être, et c'est à lui de le constater.
            Log.w(TAG, "Impossible de poser la bulle", e)
            throw e
        }
    }

    fun retirer() {
        if (!attachee) return
        attachee = false
        runCatching { fenetres.removeView(racine) }
    }

    fun mettreAJour(apercu: ApercuBulle) {
        val premiere = apercu.aires.firstOrNull()
        if (premiere == null) {
            textePastille.text = "–"
            sousTextePastille.text = if (apercu.position == null) "hors A" else "aucune"
            pastille.contentDescription = "Aires d'autoroute : " + if (apercu.position == null) {
                "position hors autoroute"
            } else {
                "aucune aire à proximité"
            }
        } else {
            textePastille.text = distance(premiere.distanceKm)
            sousTextePastille.text = "km"
            pastille.contentDescription =
                "Prochaine aire ${premiere.aire.nom}, à ${distance(premiere.distanceKm)} kilomètres"
        }
        remplirCarte(apercu)
    }

    // --- Construction ---------------------------------------------------------

    private fun creerPastille(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(dp(LARGEUR_PASTILLE_DP), dp(LARGEUR_PASTILLE_DP))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BLEU)
            setStroke(dp(2), Color.WHITE)
        }
        elevation = dp(6).toFloat()
    }

    private fun construirePastilleEtCarte() {
        textePastille.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            text = "–"
        }
        sousTextePastille.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            alpha = 0.9f
        }
        pastille.addView(textePastille)
        pastille.addView(sousTextePastille)
        rendreDeplacable(pastille) { ouvrir(true) }

        entete.apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(BLEU)
            gravity = Gravity.CENTER_VERTICAL
        }
        carte.addView(entete, LinearLayout.LayoutParams(MATCH, WRAP))
        carte.addView(lignes, LinearLayout.LayoutParams(MATCH, WRAP))
        carte.addView(creerPied(), LinearLayout.LayoutParams(MATCH, WRAP))
        // Déplacer ou refermer la carte se fait par son bandeau, pas par ses lignes.
        rendreDeplacable(entete) { ouvrir(false) }
    }

    private fun creerCarte(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(dp(LARGEUR_CARTE_DP), WRAP)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#F7F9FB"))
            cornerRadius = dp(14).toFloat()
        }
        clipToOutline = true
        elevation = dp(8).toFloat()
    }

    private fun creerPied(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setBackgroundColor(Color.parseColor("#E9EEF2"))
        addView(
            bouton("OUVRIR L'APPLI", BLEU) {
                ouvrir(false)
                onOuvrirApp()
            },
            LinearLayout.LayoutParams(0, WRAP, 1f),
        )
        addView(bouton("ARRÊTER", Color.parseColor("#B3261E"), onArreter), LinearLayout.LayoutParams(WRAP, WRAP))
    }

    private fun bouton(texte: String, couleur: Int, action: () -> Unit): TextView = TextView(context).apply {
        text = texte
        setTextColor(couleur)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(Typeface.DEFAULT_BOLD)
        setPadding(dp(6), dp(8), dp(6), dp(8))
        setOnClickListener { action() }
    }

    private fun remplirCarte(apercu: ApercuBulle) {
        val position = apercu.position
        entete.text = if (position == null) {
            "Hors autoroute"
        } else {
            "${position.autoroute.nom} · direction ${position.autoroute.terminus(position.sens)}" +
                if (position.sensFiable) "" else " (sens à confirmer)"
        } + "   ▾"

        lignes.removeAllViews()
        if (apercu.aires.isEmpty()) {
            lignes.addView(
                texteSimple(
                    if (position == null) {
                        "Position non reconnue sur une autoroute."
                    } else {
                        "Aucune aire dans les ${PORTEE_BULLE_KM.roundToInt()} prochains km."
                    },
                ),
            )
            return
        }
        apercu.aires.forEachIndexed { index, resume ->
            if (index > 0) {
                lignes.addView(
                    View(context).apply { setBackgroundColor(Color.parseColor("#DDE3E8")) },
                    LinearLayout.LayoutParams(MATCH, 1),
                )
            }
            lignes.addView(ligne(resume))
        }
    }

    private fun texteSimple(texte: String) = TextView(context).apply {
        text = texte
        setTextColor(Color.parseColor("#14202B"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    private fun ligne(resume: AireResume): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))

            val gauche = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            gauche.addView(
                TextView(context).apply {
                    text = resume.aire.nom
                    setTextColor(Color.parseColor("#14202B"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            gauche.addView(
                TextView(context).apply {
                    text = detail(resume)
                    setTextColor(Color.parseColor("#5B6875"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            addView(gauche, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(
                TextView(context).apply {
                    text = distance(resume.distanceKm)
                    setTextColor(BLEU)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setPadding(dp(8), 0, 0, 0)
                },
            )
        }

    private fun detail(resume: AireResume): String {
        val note = resume.noteGenerale?.let { "★ " + "%.1f".format(Locale.FRANCE, it.moyenne) }
            ?: "pas encore notée"
        val jeux = if (resume.notesJeux.isNotEmpty()) " · jeux" else ""
        return note + jeux
    }

    // --- Interactions ---------------------------------------------------------

    private fun ouvrir(ouvrir: Boolean) {
        pastille.visibility = if (ouvrir) View.GONE else View.VISIBLE
        carte.visibility = if (ouvrir) View.VISIBLE else View.GONE
        if (ouvrir) {
            // La carte est plus large que la pastille : on la rentre dans l'écran plutôt que de
            // la laisser déborder par le bord droit.
            parametres.x = parametres.x.coerceIn(0, (largeurEcran - dp(LARGEUR_CARTE_DP)).coerceAtLeast(0))
        }
        if (attachee) fenetres.updateViewLayout(racine, parametres)
    }

    /**
     * Fait glisser la fenêtre au doigt ; un toucher qui ne bouge pas déclenche [onToucher].
     *
     * Le seuil est celui du système, pas une valeur à nous : c'est lui qui distingue un tap d'un
     * glissement sur cet appareil.
     */
    private fun rendreDeplacable(vue: View, onToucher: () -> Unit) {
        val seuil = ViewConfiguration.get(context).scaledTouchSlop
        var departX = 0
        var departY = 0
        var toucheX = 0f
        var toucheY = 0f
        var deplace = false
        vue.setOnTouchListener { v, evenement ->
            when (evenement.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    departX = parametres.x
                    departY = parametres.y
                    toucheX = evenement.rawX
                    toucheY = evenement.rawY
                    deplace = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = evenement.rawX - toucheX
                    val dy = evenement.rawY - toucheY
                    if (!deplace && (abs(dx) > seuil || abs(dy) > seuil)) deplace = true
                    if (deplace && attachee) {
                        parametres.x = (departX + dx).roundToInt()
                        parametres.y = (departY + dy).roundToInt()
                        fenetres.updateViewLayout(racine, parametres)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!deplace) {
                        v.performClick()
                        onToucher()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // --- Utilitaires ----------------------------------------------------------

    private fun dp(valeur: Int): Int = (valeur * densite).roundToInt()

    private fun distance(km: Double): String =
        if (km < 10) "%.1f".format(Locale.FRANCE, km) else "%.0f".format(Locale.FRANCE, km)

    private companion object {
        const val TAG = "BulleOverlay"
        const val LARGEUR_PASTILLE_DP = 58
        const val LARGEUR_CARTE_DP = 236
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        val BLEU = Color.parseColor("#0B5CAD")
    }
}
