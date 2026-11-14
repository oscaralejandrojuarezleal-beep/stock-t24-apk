import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Firma: en GitHub llega por secretos; en la PC, opcional, desde keystore.properties (no se sube nunca).
val firmaLocal = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val firmaArchivo: String? = System.getenv("T24_KEYSTORE_PATH") ?: firmaLocal.getProperty("archivo")
val firmaClave: String? = System.getenv("T24_KEYSTORE_PASSWORD") ?: firmaLocal.getProperty("clave")

// Número de versión: lo pone GitHub en cada compilación (1, 2, 3...). En la PC queda en 1.
val numeroCompilacion = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "com.t24.stock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Identidad de la app en Android. NO cambiarla después de repartir la APK,
        // porque Android la tomaría como otra app distinta.
        applicationId = "ar.t24.stock"
        minSdk = 24
        targetSdk = 37
        // Los cambios de pantallas/código de Apps Script llegan solos, sin reinstalar.
        // Los cambios de esta parte Android los compila GitHub y la app ofrece instalarlos.
        versionCode = numeroCompilacion + 1
        versionName = "1.$numeroCompilacion"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("t24") {
            if (firmaArchivo != null && firmaClave != null) {
                storeFile = file(firmaArchivo)
                storePassword = firmaClave
                keyAlias = "t24"
                keyPassword = firmaClave
            }
        }
    }

    buildTypes {
        release {
            if (firmaArchivo != null && firmaClave != null) {
                signingConfig = signingConfigs.getByName("t24")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
