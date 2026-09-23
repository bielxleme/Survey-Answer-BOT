import JSZip from 'jszip';
import { ANDROID_CODEBASE } from '../data/androidCodebase';

export const ADDITIONAL_PROJECT_FILES: { path: string; content: string }[] = [
  {
    path: 'settings.gradle.kts',
    content: `pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\\\.android.*")
                includeGroupByRegex("com\\\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ResearchAgent"
include(":app")
`,
  },
  {
    path: 'build.gradle.kts',
    content: `// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
`,
  },
  {
    path: 'gradle.properties',
    content: `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
`,
  },
  {
    path: 'gradle/libs.versions.toml',
    content: `[versions]
agp = "8.7.3"
kotlin = "2.0.0"
coreKtx = "1.15.0"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.11.00"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
`,
  },
  {
    path: 'app/src/main/res/values/strings.xml',
    content: `<resources>
    <string name="app_name">Research Agent</string>
    <string name="accessibility_service_description">Serviço de Acessibilidade do Research Agent para leitura de questionários, preenchimento assistido e prevenção de alucinações.</string>
</resources>
`,
  },
  {
    path: 'app/src/main/res/values/themes.xml',
    content: `<resources>
    <style name="Theme.ResearchAgent" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">#0f172a</item>
        <item name="android:navigationBarColor">#020617</item>
    </style>
</resources>
`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/ResearchAgentApp.kt',
    content: `package com.researchagent.autofill

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ResearchAgentApp : Application() {
    companion object {
        const val CHANNEL_INTERVENTIONS = "research_agent_interventions"
        const val CHANNEL_OVERLAY = "research_agent_overlay"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val overlayChannel = NotificationChannel(
                CHANNEL_OVERLAY,
                "Bolha Flutuante (Foreground Service)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o serviço da bolha ativo na tela do Android"
            }

            val interventionChannel = NotificationChannel(
                CHANNEL_INTERVENTIONS,
                "Alertas de Intervenção Humana",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica com som e vibração quando a pesquisa requer decisão do usuário"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(overlayChannel)
            notificationManager.createNotificationChannel(interventionChannel)
        }
    }
}
`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/ui/MainActivity.kt',
    content: `package com.researchagent.autofill.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.researchagent.autofill.overlay.FloatingBubbleOverlayService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF020617)
                ) {
                    MainScreen(
                        onStartOverlay = { checkAndStartOverlay() },
                        onOpenAccessibility = { openAccessibilitySettings() }
                    )
                }
            }
        }
    }

    private fun checkAndStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permita a sobreposição sobre outros apps", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            val serviceIntent = Intent(this, FloatingBubbleOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Bolha Flutuante ativada!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Ative 'Research Agent Accessibility Service'", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun MainScreen(onStartOverlay: () -> Unit, onOpenAccessibility: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RESEARCH AGENT",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Agente Autônomo para Android",
            color = Color(0xFF10B981),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onStartOverlay,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Iniciar Bolha Flutuante (Overlay)", color = Color(0xFF020617), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenAccessibility,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Configurar Serviço de Acessibilidade", color = Color(0xFF38BDF8))
        }
    }
}
`,
  },
  {
    path: '.github/workflows/build-apk.yml',
    content: `name: Gerar APK do Android (Automático)

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Baixar Código
        uses: actions/checkout@v4

      - name: Configurar Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Dar permissão de execução ao Gradle Wrapper
        run: chmod +x gradlew || true

      - name: Compilar APK de Debug
        run: ./gradlew assembleDebug --no-daemon

      - name: Publicar APK para Download
        uses: actions/upload-artifact@v4
        with:
          name: ResearchAgent-Android-APK
          path: app/build/outputs/apk/debug/app-debug.apk
`,
  },
  {
    path: 'COMO_GERAR_O_APK.md',
    content: `# Como Gerar o APK do Research Agent para Android

Existem **2 métodos simples** para transformar este projeto em um arquivo **.APK** instalável no seu celular Android:

---

### Método 1: Gerar APK Grátis na Nuvem via GitHub (Sem instalar nada no PC!)

1. Crie uma conta gratuita no [GitHub.com](https://github.com) se ainda não tiver.
2. Crie um novo repositório (privado ou público) e suba todos os arquivos desta pasta ZIP.
3. O GitHub detectará automaticamente o arquivo na pasta \`.github/workflows/build-apk.yml\`.
4. Vá até a aba **Actions** no seu repositório do GitHub.
5. Clique no workflow **"Gerar APK do Android (Automático)"** e clique em **Run workflow**.
6. Em cerca de 2 minutos, a compilação terminará com sucesso!
7. Clique na execução concluída e baixe o arquivo **ResearchAgent-Android-APK.zip** na seção **Artifacts**.
8. Descompacte no seu celular e instale o **app-debug.apk**!

---

### Método 2: Abrir no Android Studio (Computador)

1. Baixe e instale o [Android Studio](https://developer.android.com/studio).
2. Abra o Android Studio e clique em **Open** (Abrir Projeto).
3. Selecione a pasta descompactada deste projeto.
4. Aguarde o Gradle sincronizar (cerca de 1 minuto).
5. No menu superior, clique em:
   **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**.
6. Quando terminar, uma notificação aparecerá no canto inferior direito: clique em **locate**.
7. O arquivo **app-debug.apk** estará pronto!
8. Transfira o arquivo \`.apk\` para o celular (via WhatsApp, Google Drive, cabo USB ou Telegram) e toque nele para instalar!

---

### Permissões Necessárias após Instalar o APK no Android:

1. **Sobrepor a outros aplicativos (Overlay)**:
   - Permite que a bolha flutuante fique visível por cima do Chrome, Shopee, Toluna, Google Opinion Rewards, etc.
   - Configurações do Android → Aplicativos → Acesso Especial → Sobrepor a outros apps → Ative **Research Agent**.

2. **Serviço de Acessibilidade**:
   - Permite que o agente leia os textos das perguntas e clique nas opções certas sem alucinações.
   - Configurações do Android → Acessibilidade → Apps Instalados → Ative **Research Agent Accessibility Service**.
`,
  },
];

export async function generateAndroidProjectZip(): Promise<Blob> {
  const zip = new JSZip();
  const root = zip.folder('ResearchAgent-Android');

  if (!root) {
    throw new Error('Falha ao criar pasta raiz do ZIP');
  }

  // 1. Add all original codebase files
  for (const file of ANDROID_CODEBASE) {
    root.file(file.path, file.content);
  }

  // 2. Add additional project files (Gradle configs, toml, workflows, READMEs, etc.)
  for (const file of ADDITIONAL_PROJECT_FILES) {
    root.file(file.path, file.content);
  }

  return await zip.generateAsync({
    type: 'blob',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  });
}
