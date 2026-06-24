plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.daintyz.timerwidget"
    // 설계상 targetSdk는 35지만, 이 환경에 설치된 플랫폼이 android-36뿐이라 compileSdk만 36으로 둔다.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daintyz.timerwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // RemoteViews 기반 위젯이므로 Compose는 사용하지 않는다 (설계 문서 4-1 참고)
        viewBinding = true
    }
}

dependencies {
    // 실제 라이브러리 버전/의존성은 1차 구현 단계에서 채운다.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // 창고 캐러셀(커버플로우) + 하단 탭 셸 프래그먼트 전환용.
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

// 빌드된 디버그 APK를 프로젝트 루트 dist/ 폴더에 고정 이름으로 복사 (폰 전송 편의).
// 결과: dist/timerwidget-debug.apk
tasks.register<Copy>("copyDebugApkToDist") {
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("app-debug.apk")
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "timerwidget-debug.apk" }
}

afterEvaluate {
    tasks.named("assembleDebug").configure { finalizedBy("copyDebugApkToDist") }
}

// 빌드된 APK를 매번 깊은 경로(app/build/outputs/...)에서 꺼내지 않도록
// 프로젝트 루트 dist/ 폴더에 고정 이름으로 복사한다. assembleDebug 시 자동 실행.
val copyDebugApk = tasks.register<Copy>("copyDebugApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    from(apkDir) {
        include("app-debug.apk")
        rename { "timerwidget-debug.apk" }
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApk)
}
