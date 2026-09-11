package com.poliku.polygoplus.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "blocked_users", indices = {
    @Index(value = {"userId", "blockedId"}, unique = true)
})
public class BlockEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String userId;

    @NonNull
    public String blockedId;

    public BlockEntity(@NonNull String userId, @NonNull String blockedId) {
        this.userId = userId;
        this.blockedId = blockedId;
    }
}