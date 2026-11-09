import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Saf Kotlin/JVM XLSX okuma motoru. Android bağımlılığı yoktur; bu sayede JVM üzerinde
// hızlıca test edilebilir. XmlPullParser arayüzü Android çatısında hazır bulunduğu için
// yalnızca derleme zamanında (compileOnly) eklenir.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.xmlpull)
    testImplementation(libs.kxml2)
    testImplementation(libs.junit)
}

tasks.test {
    maxHeapSize = "1g"
    // İsteğe bağlı: düzenlenmiş dosyaları dış araçla doğrulamak için -Payaz.editOut=/klasör
    providers.gradleProperty("ayaz.editOut").orNull?.let { systemProperty("ayaz.editOut", it) }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
