plugins {
    alias(libs.plugins.androidTest)
}

android {
    namespace = "com.novelstudio.app.baselineprofile"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetProjectPath = ":androidApp"
    }

    buildTypes {
        create("benchmark") {
            isMinifyEnabled = true
            testProguardFiles += file("proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
