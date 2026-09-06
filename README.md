# 🎓 PolyGo+ | The Exclusive PKS Marketplace

**PolyGo+** is a premium, student-led marketplace application designed specifically for the community at **Politeknik Kuching Sarawak (PKS)**. Built with a "Mobile-First" and "Trust-First" philosophy, it enables students and staff to buy, sell, and offer services within the safety of the campus ecosystem.

---

## ✨ Key Features

### 🚀 Futuristic User Experience
*   **Integrated Search:** A streamlined, compact header that combines navigation, user identity, and campus-wide search.
*   **Floating Navigation:** A modern, elevated pill-style bottom bar with an anchored "Add" Action Button.
*   **Premium Haptics:** Specialized tactile feedback patterns (`Swell`, `Success`, `Error`) provide physical confirmation for every key interaction.
*   **Adaptive Design:** Native support for both Phones and Tablets (sw600dp) with specialized "Navigation Rail" and "Split-View" layouts.

### 🤖 AI-Powered Auto-Lister
*   **Gemini 1.5 Integration:** Sell faster by simply taking a photo. Our AI automatically suggests professional Titles, fair RM Pricing, and attractive Descriptions.
*   **Smart Categorization:** Context-aware suggestions based on campus demand.

### 🛡️ Trust & Safety
*   **Multi-Layered Verification:** Specialized flows for **Current Students** (Matrix Card Photo/ID) and **Alumni** (Campus Challenge Questions).
*   **Biometric Security:** Secure sensitive transaction data and account settings behind Fingerprint or Face ID.
*   **Safe Meetup Landmarks:** Integrated maps pointing to official campus safe zones like Block A, the Cafeteria, and the Student Centre.

---

## 🛠️ Tech Stack & Architecture

*   **Language:** Java (Android SDK 34+)
*   **UI Framework:** XML with Material Design 3 (M3)
*   **Asynchronous Logic:** LiveData & ViewModel with SavedStateHandle for process death resilience.
*   **Networking:** Native HTTP with specialized `AiHelper` for Google Generative AI.
*   **Storage:** **Encrypted SharedPreferences** using `androidx.security` for session and JWT protection.
*   **AI Engine:** Google Gemini 1.5 Flash.

---

## 🏗️ Getting Started (Developer Setup)

To maintain security, sensitive API keys and configuration files are excluded from the repository. Follow these steps to get the project running on your local machine:

### 1. Clone the Repository
```bash
git clone https://github.com/RulRulBoyak/PolyGO-2-.git
```

### 2. Configure Local Secrets
Create a file named `local.properties` in the root directory (if it doesn't exist) and add your AI key:
```properties
# Add your Gemini Key from Google AI Studio
GEMINI_API_KEY=YOUR_API_KEY_HERE
```

### 3. Add Firebase Configuration
Obtain the `google-services.json` file from the project lead and place it in the `/app` folder.

### 4. Build & Sync
Open the project in **Android Studio (Quail 3 or newer recommended)** and click **Sync Project with Gradle Files**.

---

## 📜 Localization
The app is fully localized into two languages:
*   **English (Default)**
*   **Bahasa Melayu (Sarawak/PKS Contextualized)**

---

## ⚖️ Legal & Privacy
PolyGo+ is built with compliance in mind:
*   **PDPA 2010:** Data minimization is practiced to protect PKS student privacy.
*   **Accessibility:** Adheres to Android a11y guidelines with 48dp minimum touch targets and content descriptions.

---

## 👥 Contributors
*   **Amirul** - Lead Developer & UI/UX Architect

---
*Created for PKS. By Students, For Students.*
