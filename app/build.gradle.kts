plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.android.gms.oss-licenses-plugin")
}

// Signing inputs are environment-only: never bundle developer credentials or keystores in Git.
val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
// Fork-safe CI compiles real SDM/shared code without distributing Google's downloaded SDK.
val includeGoogleHome = providers.gradleProperty("includeGoogleHome").orElse("true").get().also {
    require(it in setOf("true", "false")) { "includeGoogleHome must be true or false" }
}.toBoolean()
require(includeGoogleHome || releaseKeystorePath == null) {
    "Official signing is forbidden for SDK-free verification builds"
}

android {
    namespace = "ca.humiditylogger"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    // GitHub APKs do not need Google Play's encrypted SDK inventory in the signing block.
    // Keep bundle metadata enabled so the OSS plugin still receives the dependency report.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    defaultConfig {
        applicationId = "ca.humiditylogger"
        minSdk = 29
        targetSdk = 36
        versionCode = 6
        versionName = "0.4.2"
        if (!includeGoogleHome) {
            applicationIdSuffix = ".verification"
            versionNameSuffix = "-no-google-home"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("main").java.srcDir(if (includeGoogleHome) "src/googleHome/java" else "src/noGoogleHome/java")

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        // A separate package prevents instrumentation from replacing a tester's real installation.
        create("deviceTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devicetest"
            versionNameSuffix = "-device-test"
            matchingFallbacks += listOf("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        // Exercise real release notices without replacing a developer-signed installation.
        create("releaseSmoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".releasesmoke"
            versionNameSuffix = "-release-smoke"
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        variantBuilder.androidTest.enable = variantBuilder.buildType == "deviceTest"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    if (includeGoogleHome) {
        implementation("com.google.android.gms:play-services-home:17.1.0")
        implementation("com.google.android.gms:play-services-home-types:17.1.0")
    }
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
