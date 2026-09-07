package com.poliku.polygoplus.api.model;

import com.google.gson.annotations.SerializedName;

public class BaseResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
