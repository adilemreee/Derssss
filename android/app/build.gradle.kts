import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Play'e yüklenecek paketin imza anahtarı depoda değil, android/keystore.properties
// dosyasında tanımlanır. Dosya yoksa sürüm derlemesi imzasız kalır.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "xyz.adilemree.dersdefteri"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.adilemree.dersdefteri"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"https://dersapi.adilemree.xyz\"")
        // Google ile girişte kullanılan Web istemci kimliği (gizli değildir).
        // gradle.properties içindeki googleWebClientId ile verilir.
        buildConfigField(
            "String", "GOOGLE_WEB_CLIENT_ID",
            "\"${providers.gradleProperty("googleWebClientId").getOrElse("")}\"",
        )
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Geliştirme sürümü mağaza sürümünün yanına ayrı uygulama olarak kurulur.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Ders Defterim Test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Glance widget'ı WorkManager üzerinden çizer. Kendi getirdiği 2.7.1'in R8 kuralları
    // AGP 9'da yetersiz kalıyor ve mağaza sürümünde widget "yükleniyor"da takılıyordu.
    implementation(libs.work.runtime.ktx)

    implementation(libs.billing.ktx)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
}
