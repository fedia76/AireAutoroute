package com.aireautoroute.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aireautoroute.app.ui.EcranAccueil
import com.aireautoroute.app.ui.EcranDetail
import com.aireautoroute.app.ui.EcranNotation
import com.aireautoroute.app.ui.theme.AireAutorouteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AireAutorouteTheme {
                ApplicationAires()
            }
        }
    }
}

private object Routes {
    const val ACCUEIL = "accueil"
    const val DETAIL = "aire/{aireId}"
    const val NOTATION = "noter/{aireId}"

    fun detail(aireId: String) = "aire/$aireId"
    fun notation(aireId: String) = "noter/$aireId"
}

@Composable
fun ApplicationAires(vm: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val etat by vm.etat.collectAsStateWithLifecycle()
    val contexte = LocalContext.current

    val demandePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultats ->
        if (resultats.values.any { it }) vm.localiser()
    }

    NavHost(navController = navController, startDestination = Routes.ACCUEIL) {
        composable(Routes.ACCUEIL) {
            EcranAccueil(
                etat = etat,
                onPositionManuelle = vm::definirPositionManuelle,
                onInverserSens = vm::inverserSens,
                onAire = { aireId -> navController.navigate(Routes.detail(aireId)) },
                onLocaliser = {
                    val accordee = ContextCompat.checkSelfPermission(
                        contexte,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (accordee) {
                        vm.localiser()
                    } else {
                        demandePermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
            )
        }

        composable(Routes.DETAIL) { entree ->
            val aireId = entree.arguments?.getString("aireId").orEmpty()
            val detail by remember(aireId) { vm.detail(aireId) }.collectAsStateWithLifecycle()
            EcranDetail(
                detail = detail,
                onRetour = { navController.popBackStack() },
                onNoter = { navController.navigate(Routes.notation(aireId)) },
                onAjouterEnseigne = { nom -> vm.ajouterEnseigne(aireId, nom) },
                onRetirerEnseigne = { enseigneId -> vm.retirerEnseigne(aireId, enseigneId) },
            )
        }

        composable(Routes.NOTATION) { entree ->
            val aireId = entree.arguments?.getString("aireId").orEmpty()
            val detail by remember(aireId) { vm.detail(aireId) }.collectAsStateWithLifecycle()
            EcranNotation(
                detail = detail,
                onRetour = { navController.popBackStack() },
                onValider = { saisies, auteur ->
                    vm.enregistrerContribution(aireId, saisies, auteur)
                    navController.popBackStack()
                },
            )
        }
    }
}
