plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.nesmat"
    compileSdk = 34 // استخدام compileSdk 34 مع مراعاة الإعدادات المحدثة

    defaultConfig {
        applicationId = "com.example.nesmat"
        minSdk = 21 // الحد الأدنى لإصدارات Android المدعومة
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // تفعيل ضغط الكود
            // ضبط shrinkResources
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // استخدام JVM 11 لضمان التوافق
    }
}

dependencies {
    // مكتبات Android الأساسية
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // مكتبة VLC لتشغيل الوسائط
    implementation("org.videolan.android:libvlc-all:3.6.0")

    // مكتبة Retrofit لجلب البيانات من الإنترنت
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // مكتبات Coroutines لدعم العمليات غير المتزامنة
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // مكتبات Lifecycle لدعم دورة حياة الأنشطة
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // مكتبات الاختبارات
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // مكتبة الوسائط
    implementation("androidx.media:media:1.6.0")

    // مكتبة دعم الأنشطة
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("org.glassfish:javax.annotation:10.0-b28")
    implementation("com.google.errorprone:error_prone_annotations:2.14.0") // إذا كنت بحاجة لمكتبة Error Prone

}