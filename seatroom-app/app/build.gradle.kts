plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.seatroom.app"
    useLibrary("android.car")
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seatroom.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 18
        versionName = "0.9.3"
    }

    buildTypes {
        release {
            // 제출용 release. Pleos SDK가 내부에서 Dagger Hilt를 써서, 코드 축소(R8)는 에뮬레이터에서
            // 확인하기 전까지 끈다(debug와 같은 코드로 동작). 켤 때는 proguard-rules.pro 규칙과 함께 확인할 것.
            // 서명은 심사용 설치를 위해 디버그 키를 쓴다(스토어 배포 때는 실제 키로 바꿀 것).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
    // 화면은 WebView 하나(0.6.0~)라 Compose는 쓰지 않는다
    implementation(libs.androidx.activity.ktx)

    testImplementation("junit:junit:4.13.2")
}

// 화면은 design/seatroom-screens.html 하나만 고친다. 빌드할 때 앱 assets로 복사.
val syncScreens by tasks.registering(Copy::class) {
    into(layout.projectDirectory.dir("src/main/assets"))
    from(rootProject.file("design/seatroom-screens.html")) { rename { "seatroom.html" } }
    from(rootProject.file("design/fonts")) { into("fonts") }
}
tasks.named("preBuild") { dependsOn(syncScreens) }
