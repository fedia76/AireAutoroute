plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Secret de signature, lu d'abord dans l'environnement (la CI les injecte depuis les secrets du
 * dépôt), sinon dans les propriétés Gradle (`~/.gradle/gradle.properties` en local).
 *
 * Le keystore n'est jamais versionné : sans lui, `assembleRelease` produit un binaire non signé,
 * ce qui reste utile pour vérifier que la compilation en mode release passe.
 */
fun secretSignature(variableEnv: String, proprieteGradle: String): String? =
    System.getenv(variableEnv)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(proprieteGradle) as String?)?.takeIf { it.isNotBlank() }

val fichierKeystore = secretSignature("ANDROID_KEYSTORE_FILE", "aireautoroute.keystore.file")
val motDePasseKeystore = secretSignature("ANDROID_KEYSTORE_PASSWORD", "aireautoroute.keystore.password")
val aliasCle = secretSignature("ANDROID_KEY_ALIAS", "aireautoroute.key.alias")
val motDePasseCle = secretSignature("ANDROID_KEY_PASSWORD", "aireautoroute.key.password")

val signatureDisponible = listOf(fichierKeystore, motDePasseKeystore, aliasCle, motDePasseCle)
    .all { it != null } && file(fichierKeystore!!).exists()

android {
    namespace = "com.aireautoroute.app"
    compileSdk = 36

    defaultConfig {
        // Identifiant de publication, distinct du `namespace` ci-dessus qui reste celui des
        // sources. Il est figé par la fiche Play Store et ne peut plus changer une fois
        // l'application publiée : toute divergence bloquerait les mises à jour.
        applicationId = "com.airesautoroute.myapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Coordonnées du service de contributions. La clé est publique par construction :
        // ce qu'elle autorise est décidé par les règles d'accès du schéma, pas par son secret.
        // Les variables d'environnement permettent de viser un autre projet sans toucher au code.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${System.getenv("SUPABASE_URL") ?: "https://fdquuazpejwxbtozxppj.supabase.co"}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_CLE_PUBLIQUE",
            "\"${System.getenv("SUPABASE_CLE_PUBLIQUE") ?: "sb_publishable_1_A20GXntUVPfv69xsLUpA_5Ju0uI6a"}\"",
        )
    }

    signingConfigs {
        if (signatureDisponible) {
            create("release") {
                storeFile = file(fichierKeystore!!)
                storePassword = motDePasseKeystore
                keyAlias = aliasCle
                keyPassword = motDePasseCle
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            // MapLibre embarque son moteur de rendu OpenGL sous forme de bibliothèques natives.
            // Sans leur table des symboles, un plantage venu de ce code n'arrive dans la Play
            // Console que sous forme d'adresses mémoire, illisibles. Les symboles voyagent dans
            // le bundle et sont retirés avant livraison : ils n'alourdissent pas l'application
            // installée.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // Les tuiles d'un fichier PMTiles sont déjà compressées une par une, et
        // les glyphes sont des protobufs compacts : les recompresser dans l'APK
        // ne gagne rien et ralentit la copie vers le stockage au premier
        // lancement.
        noCompress += listOf("pmtiles", "pbf")
    }

    dependenciesInfo {
        // Le bloc de métadonnées chiffré empêche de vérifier qu'un APK distribué directement
        // correspond bien aux sources. On le retire de l'APK, mais on le garde dans le bundle :
        // c'est ce qui permet à la Play Console de signaler une dépendance vulnérable.
        includeInApk = false
        includeInBundle = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    // Fournisseur de position fusionné de Google. Son absence est gérée à l'exécution :
    // sur un téléphone sans services Google, l'application retombe sur le LocationManager.
    implementation(libs.play.services.location)
    // Moteur de rendu de la carte. Le fond est lu depuis un fichier local :
    // aucune tuile n'est téléchargée à l'exécution.
    implementation(libs.maplibre.android)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
