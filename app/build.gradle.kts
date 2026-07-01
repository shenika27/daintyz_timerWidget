import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 업로드 키스토어 설정. keystore.properties(=비밀, 커밋 금지)가 있을 때만 릴리스 서명을 켠다.
// 파일이 없으면(현재/CI/디버그) 서명 없이도 구성이 깨지지 않게 가드한다.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.daintyz.timerwidget"
    // 설계상 targetSdk는 35지만, 이 환경에 설치된 플랫폼이 android-36뿐이라 compileSdk만 36으로 둔다.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daintyz.timerwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.1.8"
    }

    signingConfigs {
        // keystore.properties 가 있을 때만 업로드 서명 구성을 만든다(없으면 릴리스는 미서명).
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 키스토어가 준비됐으면 릴리스 AAB를 업로드 키로 서명한다(Play App Signing 업로드용).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // 위젯은 RemoteViews(Compose 불가)지만, 앱 화면은 Jetpack Compose로 전환됨.
        viewBinding = true
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    composeOptions {
        // Kotlin 1.9.24 호환 Compose 컴파일러.
        kotlinCompilerExtensionVersion = "1.5.14"
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

    // ---- Jetpack Compose (앱 화면) ----
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // 표준 아이콘은 직접 그린 XML 벡터 대신 Material Icons 사용(extended = core 포함).
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- 이미지 로딩(Coil): 원격 썸네일/미리보기 + GIF·애니 WebP 디코딩 ----
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // ---- 결제(Google Play Billing): 개별구매 + 평생이용권. ktx = suspend 확장 포함 ----
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
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
