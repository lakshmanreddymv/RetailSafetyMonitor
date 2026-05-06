plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("jacoco")
}

android {
    namespace = "com.example.retailsafetymonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.retailsafetymonitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geminiApiKey = project.findProperty("gemini.api.key")?.toString() ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "IS_DEBUG", "true")
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            buildConfigField("Boolean", "IS_DEBUG", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(11)
}

// ── JaCoCo ──────────────────────────────────────────────────────────────────

jacoco { toolVersion = "0.8.12" }

val jacocoExcludes = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Hilt*.*", "**/*_Factory*.*", "**/*_MembersInjector*.*",
    "**/Dagger*Component*.*", "**/di/**",
    "**/*Screen*.*", "**/*Activity*.*", "**/*Theme*.*",
    "**/*Color*.*", "**/*Type*.*", "**/*Badge*.*", "**/*Card*.*",
    "**/*Gauge*.*", "**/*Overlay*.*", "**/*Colors*.*",
    "**/model/**", "**/*_Impl*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo unit-test coverage report — debug build."
    dependsOn("testDebugUnitTest")
    reports { xml.required.set(true); html.required.set(true) }
    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    description = "Fail if line coverage < 80%."
    dependsOn("jacocoTestReport")
    violationRules {
        rule {
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
        }
    }
    val classesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
    classDirectories.setFrom(fileTree(classesDir) { exclude(jacocoExcludes) })
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX — alias names match FakeProductDetector
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Object Detection
    implementation(libs.mlkit.detection)

    // WorkManager
    implementation(libs.workmanager.ktx)

    // Retrofit + OkHttp + Gson
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.android)

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Coil image loading
    implementation(libs.coil.compose)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine)
    testImplementation(libs.arch.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.workmanager.testing)

    // Android Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
