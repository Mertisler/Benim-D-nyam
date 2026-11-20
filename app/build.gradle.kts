plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.loc.benimdunyam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.loc.benimdunyam"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: kod küçültme + gizleme. Korunması gerekenler proguard-rules.pro'da.
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
    }
    testOptions {
        // JVM birim testlerinde Android stub metotları hata yerine varsayılan değer döndürür.
        unitTests.isReturnDefaultValues = true
        // Robolectric testleri (ör. Room migration) için Android kaynakları.
        unitTests.isIncludeAndroidResources = true
        // Türkçe Windows'ta "windows".lowercase() -> "wındows" olur ve Robolectric'in
        // Conscrypt yerel kütüphanesi bulunamaz. Test JVM'ini İngilizce yerelle başlat.
        unitTests.all { test ->
            test.jvmArgs("-Duser.language=en", "-Duser.country=US")
        }
    }
}

// Room şemalarını sürüm kontrolüne alınabilecek JSON dosyaları olarak dışa aktarır.
// Gelecekteki migration'lar MigrationTestHelper ile bu dosyalara karşı sınanabilir.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Firebase BoM: Firebase kütüphanelerinin
    // birbiriyle uyumlu sürümlerini yönetir.
    implementation(platform(libs.firebase.bom))

    // Firebase Authentication
    implementation(libs.firebase.auth)

    // Cloud Firestore
    implementation(libs.firebase.firestore)

    // Cloud Storage: fotoğraf ve kapak dosyaları.
    // Sürümü BoM belirlediği için ayrıca yazmıyoruz.
    implementation(libs.firebase.storage)

    // Faz 2G.4C: Sunucu üzerinden sürüm koşullu medya temizleme.
    implementation(libs.firebase.functions)
    // Cloud Functions enforceAppCheck=true için Play Integrity sağlayıcısı.
    implementation(libs.firebase.appcheck.playintegrity)
    // Faz 2H.1: yalnızca debug varyantında App Check debug sağlayıcısı.
    // Play Integrity release'de kalır; debug token üretim APK'sine girmez.
    debugImplementation(libs.firebase.appcheck.debug)

    // Firebase Task API ile Kotlin Coroutines uyumu.
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Dependency Injection - Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Faz 2F.1: WorkManager kalıcı iş planlaması (henüz bulut aktarımı yapmaz).
    implementation(libs.androidx.work.runtime.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.compose.material:material-icons-extended")

    // Gson Bağımlılığı
    implementation(libs.gson)

    implementation("androidx.biometric:biometric:1.1.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.airbnb.android:lottie-compose:6.3.0") // En güncel kararlı sürüm




}