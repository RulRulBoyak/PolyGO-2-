package com.poliku.polygoplus.api;

import com.poliku.polygoplus.api.model.BaseResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PolyGoApi {

    @POST("otp.php")
    Call<BaseResponse> sendOtp(@Body OtpRequest request);

    @POST("otp.php")
    Call<BaseResponse> verifyOtp(@Body OtpRequest request);

    @POST("login.php")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("register.php")
    Call<LoginResponse> register(@Body RegisterRequest request);

    @POST("categories.php")
    Call<CategoryResponse> getCategories(@Body BaseRequest request);

    @POST("categories.php")
    Call<BaseResponse> proposeCategory(@Body ProposeCategoryRequest request);

    // Request & Response Classes
    class BaseRequest {
        public String action = "list";
    }

    class OtpRequest {
        public String email, otp, action;
        public OtpRequest(String email) { this.email = email; this.action = "send"; }
        public OtpRequest(String email, String otp) { this.email = email; this.otp = otp; this.action = "verify"; }
    }

    class LoginRequest {
        public String student_id;
        public String password;
        public LoginRequest(String id, String pass) { this.student_id = id; this.password = pass; }
    }

    class RegisterRequest {
        public String full_name, student_id, email, password;
        public RegisterRequest(String n, String id, String e, String p) {
            this.full_name = n; this.student_id = id; this.email = e; this.password = p;
        }
    }

    class ProposeCategoryRequest {
        public String name;
        public String action = "propose";
        public ProposeCategoryRequest(String name) { this.name = name; }
    }

    class LoginResponse extends BaseResponse {
        public String token;
        public User user;
    }

    class User {
        public String id, name, studentId, email, mobile, role;
    }

    class CategoryResponse extends BaseResponse {
        public java.util.List<Category> categories;
    }

    class Category {
        public String name, icon_res;
    }
}
