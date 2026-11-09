import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release imzalama bilgileri: keystore.properties dosyasından veya ortam değişkenlerinden okunur.
// Bu bilgiler depoya eklenmez (.gitignore).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String): String? = System.getenv(key) ?: keystoreProps.getProperty(key)

android {
    namespace = "com.ayaz.viewer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ayaz.viewer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Sabit debug anahtarı: farklı derlemeler arasında güncelleme yapılabilsin diye depodadır.
        // Gizli değildir; yalnızca kişisel kullanım içindir. Kalıcı kullanım için release imzası önerilir.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releaseStore = signingValue("AYAZ_KEYSTORE_FILE")
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = signingValue("AYAZ_KEYSTORE_PASSWORD")
                keyAlias = signingValue("AYAZ_KEY_ALIAS")
                keyPassword = signingValue("AYAZ_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Yayın sürümüyle aynı kod (R8 ile küçültülmüş), yalnızca debug anahtarıyla imzalı.
        // Yayın öncesi test içindir (:smoketest modülü); mağazaya yüklenmez.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/{DEPENDENCIES,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt,versions/9/OSGI-INF/MANIFEST.MF}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":xlsx"))
    // PDF düzenleme (açık kaynak, Apache 2.0, çevrimdışı). Şifreleme/imza için gereken
    // BouncyCastle hariç tutulur: yalnızca açık anahtarla şifrelenmiş PDF'lerde gerekir.
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
