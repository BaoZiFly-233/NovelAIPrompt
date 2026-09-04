plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            compileOnly("org.jetbrains.runtime:jbr-api:1.10.1")
            implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
        }
    }
}
