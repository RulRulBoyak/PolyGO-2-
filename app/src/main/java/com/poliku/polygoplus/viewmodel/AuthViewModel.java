package com.poliku.polygoplus.viewmodel;

import android.util.Patterns;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Principal Rule 3.3: Real-Time Input Validation
 * This ViewModel centralizes the validation logic for Login and Register flows.
 */
public class AuthViewModel extends ViewModel {

    // Login Validation States
    private final MutableLiveData<String> _matrixError = new MutableLiveData<>();
    public final LiveData<String> matrixError = _matrixError;

    private final MutableLiveData<String> _passwordError = new MutableLiveData<>();
    public final LiveData<String> passwordError = _passwordError;

    private final MutableLiveData<Boolean> _isLoginFormValid = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoginFormValid = _isLoginFormValid;

    // Register Validation States
    private final MutableLiveData<String> _nameError = new MutableLiveData<>();
    public final LiveData<String> nameError = _nameError;

    private final MutableLiveData<String> _emailError = new MutableLiveData<>();
    public final LiveData<String> emailError = _emailError;

    private final MutableLiveData<Boolean> _isRegisterFormValid = new MutableLiveData<>(false);
    public final LiveData<Boolean> isRegisterFormValid = _isRegisterFormValid;

    private boolean termsAccepted = false;

    // Temporary storage for validation values
    private String currentMatrix = "";
    private String currentPassword = "";
    private String currentName = "";
    private String currentEmail = "";

    public void validateLogin(String matrix, String password) {
        this.currentMatrix = matrix;
        this.currentPassword = password;

        boolean matrixValid = !matrix.trim().isEmpty();
        _matrixError.setValue(matrixValid ? null : "Matrix number is required");

        boolean passwordValid = password.length() >= 6;
        _passwordError.setValue(passwordValid ? null : "Password must be at least 6 characters");

        _isLoginFormValid.setValue(matrixValid && passwordValid);
    }

    public void setTermsAccepted(boolean accepted) {
        this.termsAccepted = accepted;
        validateRegister(currentName, currentMatrix, currentEmail, currentPassword);
    }

    public void validateRegister(String name, String matrix, String email, String password) {
        this.currentName = name;
        this.currentMatrix = matrix;
        this.currentEmail = email;
        this.currentPassword = password;

        boolean nameValid = !name.trim().isEmpty();
        _nameError.setValue(nameValid ? null : "Full name is required");

        boolean matrixValid = !matrix.trim().isEmpty();
        _matrixError.setValue(matrixValid ? null : "Student/Staff ID is required");

        boolean emailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        _emailError.setValue(emailValid ? null : "Please enter a valid PKS email");

        boolean passwordValid = password.length() >= 6;
        _passwordError.setValue(passwordValid ? null : "Password must be at least 6 characters");

        _isRegisterFormValid.setValue(nameValid && matrixValid && emailValid && passwordValid && termsAccepted);
    }
}
