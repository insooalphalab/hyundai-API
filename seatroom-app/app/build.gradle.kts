plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.seatroom.app"
    useLibrary("android.car")
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seatroom.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 15
        versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.vehicle.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    testImplementation("junit:junit:4.13.2")
}

// 화면은 design/seatroom-screens.html 하나만 고친다. 빌드할 때 앱 assets로 복사.
val syncScreens by tasks.registering(Copy::class) {
    into(layout.projectDirectory.dir("src/main/assets"))
    from(rootProject.file("design/seatroom-screens.html")) { rename { "seatroom.html" } }
    from(rootProject.file("design/fonts")) { into("fonts") }
}
tasks.named("preBuild") { dependsOn(syncScreens) }
