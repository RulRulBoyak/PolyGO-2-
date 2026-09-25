package com.poliku.polygoplus.viewmodel;

import android.util.Patterns;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.PolyGoRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Callback;

/**
 * Principal Rule 3.3: Real-Time Input Validation
 * This ViewModel centralizes the validation logic for Login and Register flows.
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {
    public static final int MIN_PASSWORD_LENGTH = 5;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private final PolyGoRepository repository;

    @Inject
    public AuthViewModel(PolyGoRepository repository) {
        this.repository = repository;
    }

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

    private final MutableLiveData<String> _confirmPasswordError = new MutableLiveData<>();
    public final LiveData<String> confirmPasswordError = _confirmPasswordError;

    private final MutableLiveData<Boolean> _isRegisterFormValid = new MutableLiveData<>(false);
    public final LiveData<Boolean> isRegisterFormValid = _isRegisterFormValid;

    private boolean termsAccepted = false;
    private boolean registrationAttempted = false;

    // Temporary storage for validation values
    private String currentMatrix = "";
    private String currentPassword = "";
    private String currentName = "";
    private String currentEmail = "";
    private String currentConfirmPassword = "";

    public void validateLogin(String matrix, String password) {
        this.currentMatrix = matrix;
        this.currentPassword = password;

        boolean matrixValid = !matrix.trim().isEmpty();
        _matrixError.setValue(matrixValid ? null : "Matrix number is required");

        boolean passwordValid = !password.isEmpty();
        _passwordError.setValue(passwordValid ? null : "Password is required");

        _isLoginFormValid.setValue(matrixValid && passwordValid);
    }

    public void setTermsAccepted(boolean accepted) {
        this.termsAccepted = accepted;
        validateRegister(currentName, currentMatrix, currentEmail, currentPassword,
                currentConfirmPassword);
    }

    public void validateRegister(String name, String matrix, String email, String password,
                                 String confirmPassword) {
        this.currentName = name;
        this.currentMatrix = matrix;
        this.currentEmail = email;
        this.currentPassword = password;
        this.currentConfirmPassword = confirmPassword;

        boolean nameValid = !name.trim().isEmpty();
        boolean matrixValid = !matrix.trim().isEmpty();
        boolean emailValid = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean passwordValid = password.length() >= MIN_PASSWORD_LENGTH
                && password.length() <= MAX_PASSWORD_LENGTH;
        boolean passwordsMatch = password.equals(confirmPassword) && !confirmPassword.isEmpty();

        if (registrationAttempted) {
            _nameError.setValue(nameValid ? null : "Full name is required");
            _matrixError.setValue(matrixValid ? null : "Student ID is required");
            _emailError.setValue(emailValid ? null : "Please enter a valid email address");
            _passwordError.setValue(passwordValid ? null : "Use 5 to 128 characters");
            _confirmPasswordError.setValue(passwordsMatch ? null : "Passwords do not match");
        }

        _isRegisterFormValid.setValue(nameValid && matrixValid && emailValid && passwordValid
                && passwordsMatch && termsAccepted);
    }

    public boolean validateRegisterForSubmit(String name, String matrix, String email,
                                             String password, String confirmPassword) {
        registrationAttempted = true;
        validateRegister(name, matrix, email, password, confirmPassword);
        return Boolean.TRUE.equals(_isRegisterFormValid.getValue());
    }

    public void login(String studentId, String password, Callback<PolyGoApi.LoginResponse> callback) {
        repository.login(studentId, password, callback);
    }

    public void register(String name, String studentId, String email, String password, boolean consentAgreed, Callback<PolyGoApi.LoginResponse> callback) {
        repository.register(name, studentId, email, password, consentAgreed, callback);
    }

    public void sendOtp(String email, Callback<PolyGoApi.OtpSendResponse> callback) {
        repository.sendOtp(email, callback);
    }

    public void googleLogin(String idToken, Callback<PolyGoApi.LoginResponse> callback) {
        repository.googleLogin(idToken, false, callback);
    }

    public void completeGoogleRegistration(String idToken, String name, String studentId,
                                           String password, boolean consentAgreed,
                                           Callback<PolyGoApi.LoginResponse> callback) {
        repository.completeGoogleRegistration(idToken, name, studentId, password,
                consentAgreed, callback);
    }
}
