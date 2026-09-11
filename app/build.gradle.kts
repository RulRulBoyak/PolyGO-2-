import com.android.build.api.variant.BuildConfigField
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.google.firebase.appdistribution)
    id("com.google.dagger.hilt.android")
    id("checkstyle")
}

// Load Secrets from local.properties
val properties = Properties()
val propertiesFile = rootProject.file("local.properties")
if (propertiesFile.exists()) {
    propertiesFile.inputStream().use { stream ->
        properties.load(stream)
    }
}
val mapsKey = properties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""
val googleWebClientId = properties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""

android {
    namespace = "com.poliku.polygoplus"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.poliku.polygoplus"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject resValue for Google Maps
        resValue("string", "google_maps_api_key", mapsKey)
    }

    signingConfigs {
        create("release") {
            val keystoreProps = Properties()
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { stream ->
                    keystoreProps.load(stream)
                }
            }
            val storePath = keystoreProps.getProperty("storeFile") ?: "secrets/polygo-release.jks"
            val storeFileRef = File(storePath)
            storeFile = if (storeFileRef.isAbsolute) storeFileRef else rootProject.file(storePath)
            storePassword = keystoreProps.getProperty("storePassword") ?: ""
            keyAlias = keystoreProps.getProperty("keyAlias") ?: ""
            keyPassword = keystoreProps.getProperty("keyPassword") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res/layouts/auth",
                "src/main/res/layouts/home",
                "src/main/res/layouts/explore",
                "src/main/res/layouts/profile",
                "src/main/res/layouts/messaging",
                "src/main/res/layouts/marketplace",
                "src/main/res/layouts/common",
                "src/main/res"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.appcompat)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.inappmessaging.display)
    // implementation(libs.firebase.perf)
    implementation(libs.firebase.storage)
    implementation(libs.googleid)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.glide)
    implementation(libs.shimmer)
    implementation(libs.lottie)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.play.services.maps)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.appcheck.playintegrity)
    
    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.konfetti)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")
    implementation(libs.androidx.viewpager2)
    
    // Room
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
    
    // Hilt
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    annotationProcessor(libs.hilt.work.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    setIgnoreFailures(false)
    setShowViolations(true)
}

tasks.register<Checkstyle>("checkstyle") {
    source("src/main/java")
    include("**/*.java")
    classpath = files()
}

androidComponents {
    onVariants { variant ->
        variant.buildConfigFields?.put(
            "GOOGLE_MAPS_API_KEY",
            BuildConfigField("String", "\"$mapsKey\"", "Google Maps API Key")
        )
        variant.buildConfigFields?.put(
            "GOOGLE_WEB_CLIENT_ID",
            BuildConfigField("String", "\"$googleWebClientId\"", "Google Sign-In Client ID")
        )
    }
}
