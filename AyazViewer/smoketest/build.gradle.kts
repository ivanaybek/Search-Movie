import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Yayın öncesi uçtan uca test: uygulamanın R8 ile küçültülmüş "staging" sürümünü (yayın sürümüyle
// aynı kod) gerçek bir kullanıcı gibi UiAutomator ile kullanır ve mağaza ekran görüntülerini çeker.
// Uygulamanın iç sınıflarına dokunmaz; bu yüzden küçültme sonrası oluşabilecek hataları yakalar.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ayaz.viewer.smoketest"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("staging") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "staging" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
