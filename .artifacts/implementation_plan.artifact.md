# Fix Android 16 JNI Trampoline Crash

This plan addresses the crash signature `[[anon_shmem:dalvik-jit-code-cache]] art_jni_trampoline` observed on Android 16 (SDK 36) by updating dependencies and adjusting build configurations to comply with new OS requirements (16KB page alignment and JNI stability).

## Proposed Changes

### Build Configuration & Dependencies

#### [MODIFY] [libs.versions.toml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/gradle/libs.versions.toml)
- Upgrade `lottie` version from `6.4.1` to `6.7.1` to include fixes for Android 16 stability.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/build.gradle.kts)
- Add `packaging { jniLibs { useLegacyPackaging = true } }` to ensure native libraries are extracted and aligned correctly for 16KB pages.
- Disable the Firebase Performance Monitoring plugin and its dependency as a workaround for a known conflict with the Android 16 JIT cache.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/AndroidManifest.xml)
- Set `android:extractNativeLibs="true"` in the `<application>` tag to ensure native libraries are handled correctly by the package manager on the new OS.

## Verification Plan

### Automated Tests
- Run `.\gradlew.bat assembleDebug` to ensure the project still builds successfully with the updated Lottie version and packaging settings.

### Manual Verification
- Deploy to the Android 16 emulator and verify that the app no longer crashes during UI transitions or animations (e.g., in `SplashActivity` or anywhere `LottieAnimationView` is used).
- Verify that Firebase Crashlytics still reports other events (ensuring we haven't broken the entire Firebase integration).
