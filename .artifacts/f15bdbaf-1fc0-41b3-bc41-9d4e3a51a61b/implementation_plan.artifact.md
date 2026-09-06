# Implementation Plan - Feature Expansion and UI Refinement

This plan covers the implementation of several requested features including navigation animations, push notifications, login verification, expansion of the sustainability dashboard, and dynamic category management.

## User Review Required

> [!IMPORTANT]
> The implementation of dynamic categories requires changes to the backend database schema. I will provide the SQL script to be run on your local MySQL server.

> [!NOTE]
> For Push Notifications to work in development, you'll need a valid `google-services.json` file. I will ensure the code is ready to handle FCM tokens and messages.

## Proposed Changes

### Navigation Animations
Modify activity transitions to use fade effects instead of slides for a smoother experience.

#### [NEW] [fade_in.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/res/anim/fade_in.xml)
#### [NEW] [fade_out.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/res/anim/fade_out.xml)
#### [MODIFY] [SearchActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/SearchActivity.java)
- Update `finish()` to use `fade_in` and `fade_out`.
- Add `overridePendingTransition` when starting the activity from other screens (e.g., `HomeActivity`).

---

### Authentication & Verification
Implement an OTP/Email verification flow to enhance security.

#### [NEW] [OtpActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/OtpActivity.java)
- A dedicated screen for entering the verification code.
#### [MODIFY] [RegisterActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/RegisterActivity.java)
- Update registration flow to redirect to `OtpActivity` before final account creation.
#### [MODIFY] [NetworkApi.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/network/NetworkApi.java)
- Add endpoints for `sendOtp` and `verifyOtp`.

---

### Sustainability Dashboard ("My Campus Impact")
Expand the "My Campus Impact" screen with more metrics and a gamified seller tier system.

#### [MODIFY] [SustainabilityDashboardActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/SustainabilityDashboardActivity.java)
- Add calculations for "Paper Prevented" and "Energy Saved".
- Implement logic to determine Seller Tier (Bronze, Silver, Gold) based on total sales.
#### [MODIFY] [activity_sustainability_dashboard.xml](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/res/layout/activity_sustainability_dashboard.xml)
- Redesign the layout to match the provided photo, including the "Seller Tier" card and expanded metrics grid.

---

### Dynamic Categories
Move category management to the backend to allow users to propose new categories.

#### [NEW] [categories.sql](file:///C:/Users/User/AndroidStudioProjects/PolyGo/backend/polygo-api/sql/categories.sql)
- SQL script to create the `categories` table and populate initial data.
#### [NEW] [categories.php](file:///C:/Users/User/AndroidStudioProjects/PolyGo/backend/polygo-api/categories.php)
- Backend endpoint to fetch and propose categories.
#### [MODIFY] [NetworkApi.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/network/NetworkApi.java)
- Add `getCategories` and `proposeCategory` methods.
#### [MODIFY] [SearchActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/SearchActivity.java)
- Fetch categories from `NetworkApi` instead of using a hardcoded array.
#### [MODIFY] [AddServiceActivity.java](file:///C:/Users/User/AndroidStudioProjects/PolyGo/app/src/main/java/com/poliku/polygoplus/AddServiceActivity.java)
- Update category selection to use dynamic data and add a "Propose New Category" option.

## Verification Plan

### Automated Tests
- Run unit tests for `ExploreViewModel` and `NetworkApi` to ensure data fetching still works correctly.

### Manual Verification
- **Navigation:** Verify that navigating back from `SearchActivity` performs a fade transition.
- **Sustainability:** Open the "My Campus Impact" screen and verify that the new metrics and seller tier are displayed correctly.
- **Categories:** Verify that the category list in Search and Add Service matches the backend database.
- **Auth:** Test the registration flow and verify the OTP step.
