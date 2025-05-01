// build.gradle.kts (raiz)
buildscript {
    repositories {
        google()  // Importante: adicione os repositórios aqui
        mavenCentral()
    }
    dependencies {
        // Adicione o classpath para o plugin do Crashlytics
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
        classpath("com.google.gms:google-services:4.4.0")
    }
}

plugins {
    id("com.android.application") version "8.10.0-rc04" apply false
    id("com.android.library") version "8.10.0-rc04" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}
