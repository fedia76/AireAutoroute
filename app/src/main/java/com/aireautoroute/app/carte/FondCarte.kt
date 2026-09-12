package com.aireautoroute.app.carte

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fond cartographique embarqué, recopié sur le stockage au premier lancement.
 *
 * MapLibre lit une archive PMTiles par plages d'octets, ce que le gestionnaire
 * d'assets d'Android ne sait pas faire : `pmtiles://asset://` n'est pas
 * supporté. Le fichier doit donc vivre dans `filesDir`, d'où cette recopie.
 * Les glyphes suivent le même chemin — non par nécessité, mais pour que le
 * style ne mélange pas deux schémas d'URL.
 *
 * Le fond est facultatif. S'il n'a pas été généré (voir `tools/generer_fond.py`),
 * [preparer] renvoie un emplacement sans archive et la carte se dessine sans lui :
 * on garde le tracé de l'autoroute et les aires, simplement sans repères.
 */
object FondCarte {

    private const val TAG = "FondCarte"

    /** Chemins dans `assets/`, qui servent aussi de chemins relatifs dans `filesDir`. */
    private const val ARCHIVE = "fond/france-z8.pmtiles"
    private const val GLYPHES = "fond/glyphes"

    /**
     * Ce que le style a besoin de savoir. Les deux URL sont indépendantes : les glyphes
     * servent aux libellés des aires, que le fond soit là ou non.
     */
    data class Emplacement(val urlArchive: String?, val motifGlyphes: String?)

    /**
     * Recopie ce qui est disponible et renvoie ses URL, `null` pour ce qui manque.
     *
     * La recopie est ignorée quand le fichier déjà présent a la taille de
     * l'asset : c'est ce qui rend l'appel bon marché à chaque ouverture de la
     * carte, tout en repérant un fond régénéré entre deux versions.
     */
    suspend fun preparer(context: Context): Emplacement = withContext(Dispatchers.IO) {
        Emplacement(
            urlArchive = preparerArchive(context),
            motifGlyphes = preparerGlyphes(context),
        )
    }

    /** URL de l'archive PMTiles, ou `null` si elle n'est ni embarquée ni recopiable. */
    private fun preparerArchive(context: Context): String? {
        val assets = context.assets
        if (!existe(assets, ARCHIVE)) {
            Log.i(TAG, "Aucun fond embarqué : la carte s'affichera sans repères.")
            return null
        }
        val archive = File(context.filesDir, ARCHIVE)
        return try {
            copierSiBesoin(assets, ARCHIVE, archive)
            "pmtiles://file://${archive.absolutePath}"
        } catch (erreur: Exception) {
            // Un fond illisible ne doit pas emporter la carte avec lui.
            Log.e(TAG, "Fond inutilisable, la carte s'affichera sans repères.", erreur)
            null
        }
    }

    /** Motif d'URL des glyphes, ou `null` s'ils manquent : la carte se passera de libellés. */
    private fun preparerGlyphes(context: Context): String? {
        val racine = context.filesDir
        return try {
            copierArborescence(context.assets, GLYPHES, racine)
            // MapLibre substitue {fontstack} et {range} à la demande.
            "file://${File(racine, GLYPHES).absolutePath}/{fontstack}/{range}.pbf"
        } catch (erreur: Exception) {
            Log.e(TAG, "Glyphes inutilisables, la carte s'affichera sans libellés.", erreur)
            null
        }
    }

    private fun existe(assets: AssetManager, chemin: String): Boolean =
        try {
            assets.open(chemin).close()
            true
        } catch (_: Exception) {
            // Absent ou illisible : dans les deux cas, il n'y a pas de fond à poser.
            false
        }

    /**
     * Copie un asset, sauf si la destination a déjà exactement sa taille.
     *
     * La taille de l'asset n'est connue d'avance que s'il est stocké non
     * compressé — ce que garantit le `noCompress` déclaré dans `build.gradle.kts`.
     * Si jamais elle ne l'est pas, on recopie : c'est correct, seulement inutile.
     */
    private fun copierSiBesoin(assets: AssetManager, chemin: String, destination: File) {
        val tailleAttendue = try {
            assets.openFd(chemin).use { it.length }
        } catch (_: Exception) {
            -1L
        }
        if (destination.isFile && tailleAttendue >= 0 && destination.length() == tailleAttendue) {
            return
        }

        destination.parentFile?.mkdirs()
        // On écrit à côté puis on renomme : une copie interrompue (batterie,
        // arrêt forcé) ne doit pas laisser une archive tronquée passer pour
        // valide au lancement suivant.
        val provisoire = File(destination.parentFile, "${destination.name}.partiel")
        assets.open(chemin).use { entree ->
            provisoire.outputStream().use { sortie -> entree.copyTo(sortie) }
        }
        if (!provisoire.renameTo(destination)) {
            provisoire.delete()
            error("Impossible d'installer ${destination.name}")
        }
    }

    /** Copie récursivement un dossier d'assets sous [racine]. */
    private fun copierArborescence(assets: AssetManager, chemin: String, racine: File) {
        val entrees = assets.list(chemin).orEmpty()
        if (entrees.isEmpty()) {
            copierSiBesoin(assets, chemin, File(racine, chemin))
            return
        }
        for (entree in entrees) {
            copierArborescence(assets, "$chemin/$entree", racine)
        }
    }
}
