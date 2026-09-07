# PolyGo+ 🎓

PolyGo+ is a modern, student-centric marketplace designed exclusively for the **Politeknik Kuching Sarawak (PKS)** campus. It enables students and staff to buy, sell, and offer services (like printing, delivery, or repairs) safely within the campus ecosystem.

## 🚀 Key Features

-   **Campus-Only Marketplace**: Restricted to verified PKS emails.
-   **Service Portfolio**: Students can offer and browse technical services.
-   **AI Discovery**: Integrated Google Gemini AI for smart listing suggestions and categorization.
-   **Real-time Chat**: In-app messaging for safe deal negotiations.
-   **Sustainability Dashboard**: Track your environmental impact by recycling second-hand goods on campus.
-   **Push Notifications**: Stay updated on new messages and campus deals via Firebase.

---

## 🛠️ Project Setup for Developers

### 1. Prerequisites
-   **Android Studio** (Koala or newer recommended).
-   **XAMPP** (Apache & MySQL) for the local backend.
-   **Git** for version control.

### 2. Backend Configuration (XAMPP)
1.  Copy the `backend/polygo-api` folder to your `C:\xampp\htdocs\` directory.
2.  Open **XAMPP Control Panel** and ensure Apache and MySQL are running.
3.  **Database Import**: 
    -   Access `http://localhost/phpmyadmin`.
    -   Create a database named `polygo`.
    -   Import the SQL files found in `backend/polygo-api/sql/`.
4.  **Config**: Edit `C:\xampp\htdocs\polygo-api\config.php` to match your local database credentials (usually `root` with no password).
5.  **Emulator Access**: The app is configured to connect to `http://10.0.2.2/polygo-api/` (the host machine's localhost from the emulator).

### 3. API Keys & Security
-   **Google Gemini AI**: 
    -   Add your API key to `local.properties` at the root of the project:
        ```properties
        GEMINI_API_KEY=your_actual_key_here
        ```
-   **Firebase**: 
    -   Place your `google-services.json` in the `app/` directory to enable Notifications and Analytics.

---

## 🤝 Teamwork & Contribution Rules

### Git Branching Strategy
We follow a strict branching model to keep the code stable:
-   `main`: Production-ready code only.
-   `develop`: The integration branch for features.
-   `feature/feature-name`: Individual work branches (e.g., `feature/login-validation`).
-   `hotfix/issue-name`: Urgent bug fixes.

**Commit Message Format**: 
Always prefix your commit messages with the scope: 
`feat(ui): add new sustainability card` or `fix(network): handle timeout errors`.

### Coding Standards
-   **Architecture**: Follow the MVVM (Model-View-ViewModel) pattern.
-   **Transitions**: Use the fade animation (`R.anim.fade_in`, `R.anim.fade_out`) for activity navigation.
-   **Dependency Injection**: Use **Hilt** (coming soon) for managing service instances.
-   **Linting**: Run `./gradlew checkstyle` before pushing to ensure code consistency.

---

## 📞 Support
For technical issues or backend questions, contact the lead developer or open an issue on the repository.

*Built with ❤️ by the PolyGo+ Team at PKS.*
