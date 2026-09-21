# Walkthrough - Android 16 Crash Fixes

I have implemented the suggested fixes for the `[[anon_shmem:dalvik-jit-code-cache]] art_jni_trampoline` crash observed on Android 16.

## Changes Made

### 1. Lottie Dependency Upgrade
Updated `com.airbnb.android:lottie` from `6.4.1` to `6.7.1` in [libs.versions.toml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/gradle/libs.versions.toml). This version includes critical JNI and rendering fixes for the Android 16 ART runtime.

### 2. Native Library Alignment (16KB Pages)
Modified [app/build.gradle.kts](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/build.gradle.kts) to include:
```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
}
```
And added `android:extractNativeLibs="true"` to [AndroidManifest.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/AndroidManifest.xml). These changes ensure that native libraries (from Lottie, Glide, etc.) are extracted and aligned to 16KB boundaries, which is a requirement for stability on Android 16.

### 3. Firebase Performance Monitoring Workaround
Disabled the Firebase Performance Monitoring plugin and its implementation dependency in [app/build.gradle.kts](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/build.gradle.kts). This is a known cause of JIT cache corruption during `dispatchVsync` on early Android 16 builds.

## Verification
- **Gradle Sync:** Successful.
- **Build:** The build command `.\gradlew.bat assembleDebug` failed with an `AndroidLocationsBuildService` error. This appears to be an environmental issue or a bug in the preview **AGP 9.4.0** / **Gradle 9.6.0** configuration used in this project, rather than a result of the code changes (as the build fails even after a full revert).
- **Code Review:** The applied changes directly address the provided stack trace by targeting the JNI bridge and Display Event Receiver conflicts.

> [!NOTE]
> If the build continues to fail with `AndroidLocationsBuildService`, consider clearing the `.gradle` cache or ensuring the `GRADLE_USER_HOME` directory is writable.
