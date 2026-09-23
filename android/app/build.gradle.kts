plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.researchagent.autofill"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.researchagent.autofill"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "20").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "2.0.0"
    }

    // Assinatura: usa secrets do CI se existirem; senão, a chave fixa do repositório
    // (mesma assinatura em todo build → atualizações instalam por cima sem desinstalar).
    signingConfigs {
        create("shared") {
            val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/research-agent.jks")
                storePassword = "researchagent"
                keyAlias = "researchagent"
                keyPassword = "researchagent"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Interface feita com Views nativas do Android: sem Compose/AppCompat → APK pequeno e sem conflitos.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // OCR on-device com modelo embutido (não depende do Google Play Services para baixar o modelo)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
