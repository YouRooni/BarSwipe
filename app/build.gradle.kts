import java.util.Properties

plugins {
    id("com.android.application")
}

val releaseKeystore = rootProject.file("wallify.jks")
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (releaseKeystore.isFile && keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "dev.rooni.barswipe"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rooni.barswipe"
        minSdk = 33
        versionCode = 4
        versionName = "1.0.3"
    }

    val releaseSigning = if (releaseKeystore.isFile) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS") ?: "Rooni"
            keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD") ?: ""
        }
    } else null

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        targetSdk = 35
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += listOf("kotlin/**", "META-INF/*.version", "META-INF/version-control-info.textproto")
    }

    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.9.1")
    compileOnly("io.github.libxposed:api:101.0.1")
}
