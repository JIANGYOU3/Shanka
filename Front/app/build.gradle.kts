import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release 签名凭据从本地 keystore.properties 读取（git 忽略、600 权限），
// 文件缺失时不影响 debug 构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

// Debug-only base URL 覆盖（非敏感）：USB 真机经 `adb reverse tcp:<port> tcp:<port>` 访问
// 宿主侧隔离后端时传入 `http://127.0.0.1:<port>`；不传保持模拟器 loopback 默认值。
// 仅接受纯 http(s) URL——引号、空白或换行会破坏 BuildConfig 字符串字面量，配置期拒绝。
// Release 永不读取该 property（正式地址编译期固定）。
val shankaDebugApiBaseUrl: String? = providers.gradleProperty("shankaDebugApiBaseUrl").orNull
if (shankaDebugApiBaseUrl != null) {
    require(shankaDebugApiBaseUrl.matches(Regex("""^https?://[A-Za-z0-9.\-]+(:[0-9]{1,5})?(/[A-Za-z0-9._\-/~]*)?$"""))) {
        "shankaDebugApiBaseUrl 必须是不含引号/空白/换行的合法 http(s) URL，实际收到：$shankaDebugApiBaseUrl"
    }
}

android {
    namespace = "com.qiuzhao.flashcards"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qiuzhao.flashcards"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Keep development installs isolated from the existing release app and its data.
            // Android signs this variant with the local debug keystore automatically.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Debug-only 显式本地环境覆盖：默认模拟器 loopback；USB 真机用
            // -PshankaDebugApiBaseUrl=http://127.0.0.1:<port> 配合 adb reverse。
            // Release 编译期不可达（正式地址在 release 块固定，不读该 property）。
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${shankaDebugApiBaseUrl ?: "http://10.0.2.2:8000"}\"",
            )
        }
        release {
            // Release 编译期固定正式 base URL；无服务器编辑、测试模式或演示数据开关。
            buildConfigField("String", "API_BASE_URL", "\"https://shanka.kbzz1.top\"")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
    testOptions {
        // Robolectric runs the Room/outbox persistence suite on the JVM against a real
        // file-backed SQLite; those unit tests stay in the JVM source set.
        unitTests { isIncludeAndroidResources = true }
    }
}

kapt {
    arguments {
        // shanka-v25.db schema export: every version bump must land a new exported schema
        // and an explicit migration (destructive fallback is banned in the database builder).
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation3:navigation3-runtime:1.0.0")
    implementation("androidx.navigation3:navigation3-ui:1.0.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.4")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // MockWebServer is a JVM artifact: full Retrofit/OkHttp contract fixtures run on the JVM.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Room/outbox persistence suites run on Robolectric's real file-backed SQLite on the JVM.
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.work:work-testing:2.10.4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
