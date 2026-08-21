plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.aireautoroute.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aireautoroute.app"
        minSdk = 26
        targetSdk = 35
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
