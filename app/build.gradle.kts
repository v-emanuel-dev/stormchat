import com.android.build.api.dsl.Packaging
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
} else {
    println("Warning: local.properties not found. API Key will be missing from BuildConfig.")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.ivip.brainstormia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ivip.brainstormia"
        minSdk = 24
        targetSdk = 35
        versionCode = 60
        versionName = "6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiKeyOpenaiFromProperties = localProperties.getProperty("apiKeyOpenai") ?: ""
        if (apiKeyOpenaiFromProperties.isBlank()) {
            println("Warning: 'apiKeyOpenai' not found in local.properties. BuildConfig field will be empty.")
        }

        val apiKeyGoogleFromProperties = localProperties.getProperty("apiKeyGoogle") ?: ""
        if (apiKeyGoogleFromProperties.isBlank()) {
            println("Warning: 'apiKeyGoogle' not found in local.properties. BuildConfig field will be empty.")
        }

        val apiKeyAnthropicFromProperties = localProperties.getProperty("apiKeyAnthropic") ?: ""
        if (apiKeyAnthropicFromProperties.isBlank()) {
            println("Warning: 'apiKeyAnthropic' not found in local.properties. BuildConfig field will be empty.")
        }

        buildConfigField("String", "OPENAI_API_KEY", "\"${apiKeyOpenaiFromProperties}\"")
        buildConfigField("String", "GOOGLE_API_KEY", "\"${apiKeyGoogleFromProperties}\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${apiKeyAnthropicFromProperties}\"")
}
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {}
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Adicionar bloco de packaging para resolver conflitos META-INF
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")

            pickFirsts.add("mozilla/public-suffix-list.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.generativeai)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences.core.android)
    implementation(libs.firebase.crashlytics.buildtools)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.play.services.auth.v2120)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.core.splashscreen)
    implementation("com.github.jeziellago:compose-markdown:0.5.0")

    // Google Drive API - Ajustado para evitar conflitos
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20230822-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")

    // Forçar uma única versão do HTTP Client
    implementation("org.apache.httpcomponents:httpclient:4.5.14") {
        exclude(group = "org.apache.httpcomponents", module = "httpcore")
    }
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation ("androidx.datastore:datastore-preferences:1.1.4")
    implementation ("com.google.firebase:firebase-bom:32.7.0")
    implementation ("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.1")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // OpenAI API
    implementation("com.aallam.openai:openai-client:3.5.1")
    implementation("io.ktor:ktor-client-android:2.3.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.3")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("org.json:json:20210307")
    implementation ("com.android.billingclient:billing-ktx:7.1.1")
    implementation ("io.noties.markwon:core:4.6.2")
    implementation ("io.noties.markwon:html:4.6.2") // para suporte a HTML
    implementation ("io.noties.markwon:linkify:4.6.2")
    implementation("io.coil-kt:coil-compose:2.2.2")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation ("com.google.code.gson:gson:2.10.1")
    implementation ("androidx.work:work-runtime-ktx:2.10.1")
}