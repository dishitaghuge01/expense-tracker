plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.expensetracker.prototype"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.expensetracker.prototype"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-prototype"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint {
        warningsAsErrors = true
        // PRD/build plan explicitly require API 36; do not silently raise the baseline.
        disable += "OldTargetApi"
        // Pin the compatible stable toolchain; review upgrades deliberately, not on each CI run.
        // These are update suggestions, not correctness or security checks.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
}
