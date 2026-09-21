package com.poliku.polygoplus.api.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BaseResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("maintenance")
    private boolean maintenance;

    @SerializedName("maintenance_message")
    private String maintenanceMessage;

    @SerializedName("home_messages")
    private List<String> homeMessages;

    @SerializedName("home_message_interval_seconds")
    private int homeMessageIntervalSeconds;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public boolean isMaintenance() {
        return maintenance;
    }

    public String getMaintenanceMessage() {
        return maintenanceMessage;
    }

    public List<String> getHomeMessages() {
        return homeMessages;
    }

    public int getHomeMessageIntervalSeconds() {
        return homeMessageIntervalSeconds;
    }
}
