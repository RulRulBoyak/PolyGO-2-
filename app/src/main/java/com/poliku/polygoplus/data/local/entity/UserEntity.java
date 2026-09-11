package com.poliku.polygoplus.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "users", indices = {
    @Index("studentId"),
    @Index("email")
})
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String id;
    
    public String name;
    public String studentId;
    public String email;
    public String mobile;
    public String role;
    public String profilePicUrl;

    public UserEntity(@NonNull String id, String name, String studentId, String email, 
                      String mobile, String role, String profilePicUrl) {
        this.id = id;
        this.name = name;
        this.studentId = studentId;
        this.email = email;
        this.mobile = mobile;
        this.role = role;
        this.profilePicUrl = profilePicUrl;
    }
}
