# Implementation Plan - Advanced Teamwork Features (Hilt & Checkstyle)

This plan details the integration of **Hilt** for Dependency Injection and **Checkstyle** for code quality enforcement, making the project truly professional and scalable for a team.

## User Review Required

> [!CAUTION]
> Integrating Hilt requires significant changes to the Gradle build system and the `PolyGoApplication` class. It will also involve refactoring `NetworkApi` and `AppDataStore` from static-heavy classes to Hilt-managed singletons.

## Proposed Changes

### 1. Build System Enhancements
Update Gradle configuration to support Hilt and Checkstyle.

#### [MODIFY] [libs.versions.toml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/gradle/libs.versions.toml)
- Add Hilt versions and libraries.
- Add Checkstyle version.
#### [MODIFY] [build.gradle.kts](file:///C:/Users/User/AndroidStudioProjects/PolyGo/build.gradle.kts)
- Add Hilt and Checkstyle plugins to the root project.
#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/build.gradle.kts)
- Apply Hilt plugin.
- Add Hilt dependencies.
- Configure Checkstyle task.

---

### 2. Dependency Injection (Hilt)
Transition the project to use Hilt for cleaner dependency management.

#### [MODIFY] [PolyGoApplication.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/PolyGoApplication.java)
- Annotate with `@HiltAndroidApp`.
#### [NEW] [AppModule.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/di/AppModule.java)
- Provide `Context`, `SharedPreferences`, and other global singletons.
#### [NEW] [NetworkModule.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/di/NetworkModule.java)
- Provide a Hilt-managed instance of the networking client.
#### [MODIFY] [BaseActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/ui/BaseActivity.java)
- Annotate with `@AndroidEntryPoint`.

---

### 3. Code Quality (Checkstyle)
Ensure all teammates follow the same coding standards.

#### [NEW] [checkstyle.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/config/checkstyle/checkstyle.xml)
- Define rules for indentation, bracket placement, and naming conventions.

## Verification Plan

### Automated Tests
- Run `./gradlew checkstyle` to verify linting rules.
- Run a full build to ensure Hilt code generation is successful.

### Manual Verification
- Deploy the app and ensure all injected dependencies (like `NetworkApi`) are initialized correctly.
- Verify that the app still starts and navigates correctly after the Hilt refactor.
