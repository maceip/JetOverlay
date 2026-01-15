import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun Project.propOrEnv(key: String, defaultValue: String = ""): String {
    val prop = (findProperty(key) as? String)?.takeIf { it.isNotBlank() }
    val env = System.getenv(key)?.takeIf { it.isNotBlank() }
    return (prop ?: env ?: defaultValue).toString()
}

fun String.asBuildConfigString(): String = "\"${this.replace("\"", "\\\"")}\""

android {
    namespace = "com.yazan.jetoverlay.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yazan.jetoverlay"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val gmailRedirect = propOrEnv("GMAIL_REDIRECT_URI", "jetoverlay://email-callback")
        val notionRedirect = propOrEnv("NOTION_REDIRECT_URI", "jetoverlay://notion-callback")
        val githubRedirect = propOrEnv("GITHUB_REDIRECT_URI", "jetoverlay://github-callback")

        buildConfigField("String", "GMAIL_CLIENT_ID", propOrEnv("GMAIL_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "GMAIL_CLIENT_SECRET", propOrEnv("GMAIL_CLIENT_SECRET").asBuildConfigString())
        buildConfigField("String", "GMAIL_REDIRECT_URI", gmailRedirect.asBuildConfigString())

        buildConfigField("String", "NOTION_CLIENT_ID", propOrEnv("NOTION_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "NOTION_CLIENT_SECRET", propOrEnv("NOTION_CLIENT_SECRET").asBuildConfigString())
        buildConfigField("String", "NOTION_REDIRECT_URI", notionRedirect.asBuildConfigString())

        buildConfigField("String", "GITHUB_CLIENT_ID", propOrEnv("GITHUB_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "GITHUB_CLIENT_SECRET", propOrEnv("GITHUB_CLIENT_SECRET").asBuildConfigString())
        buildConfigField("String", "GITHUB_REDIRECT_URI", githubRedirect.asBuildConfigString())

        buildConfigField("String", "SLACK_CLIENT_ID", propOrEnv("SLACK_CLIENT_ID").asBuildConfigString())
        buildConfigField("String", "SLACK_CLIENT_SECRET", propOrEnv("SLACK_CLIENT_SECRET").asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_18
            freeCompilerArgs.add("-Xreturn-value-checker=check")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Import the SDK Module
    implementation(project(":jetoverlay"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.github.skydoves:flexible-bottomsheet-material3:0.2.0")

    // LiteRT-LM
    implementation(libs.litertlm.android)

    // Images / SVG loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-svg:2.6.0")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Android Instrumentation Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.uiautomator)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)

    // WorkManager for battery-efficient background tasks
    implementation(libs.androidx.work.runtime.ktx)
}
