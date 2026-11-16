import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// App parameters: -P gradle property > environment variable > app.properties > neutral default.
val appFile = Properties().apply {
    rootProject.file("app.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun appParam(key: String, default: String): String =
    (findProperty(key) as String?)
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: appFile.getProperty(key)
        ?: default

// Release signing: environment variables > keystore.properties (git-ignored) > unsigned.
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}
fun signing(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(propName)
val signingStoreFile = signing("SIGNING_KEYSTORE", "storeFile")

android {
    namespace = "app.locator"
    compileSdk = 37

    defaultConfig {
        applicationId = appParam("APP_ID", "app.example.locator")
        minSdk = 26
        targetSdk = 37
        versionCode = appParam("APP_VERSION_CODE", "1").toInt()
        versionName = appParam("APP_VERSION_NAME", "0.1.0")
        resValue("string", "app_name", appParam("APP_NAME", "Locator"))
        // The static shortcut needs the package name in an XML resource, where manifest placeholders do not apply.
        resValue("string", "application_id", appParam("APP_ID", "app.example.locator"))
        buildConfigField("String", "APP_URL", "\"${appParam("APP_URL", "https://example.com")}\"")
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile)
                storePassword = signing("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signing("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signing("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // The container user has no passwd entry, so the JVM reports user.home as "?"; Robolectric
        // downloads its Android jars under it. HOME is set by docker-compose.yml.
        unitTests.all { test -> System.getenv("HOME")?.let { test.systemProperty("user.home", it) } }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// A release build without signing details would silently produce an unsigned artifact; refuse instead.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.path == ":app:assembleRelease" || it.path == ":app:bundleRelease" }
    if (releaseRequested && signingStoreFile == null) {
        throw GradleException(
            "Release signing is not configured: set SIGNING_KEYSTORE (+ SIGNING_STORE_PASSWORD, " +
                "SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD) or create keystore.properties.",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.exifinterface)
    implementation(libs.glance.appwidget)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.org.json) // the android.jar copy is only a stub on the JVM
    testImplementation(libs.test.core)
}
