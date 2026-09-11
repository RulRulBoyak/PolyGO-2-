package com.poliku.polygoplus.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.poliku.polygoplus.data.local.entity.BlockEntity;

import java.util.List;

@Dao
public interface BlockDao {
    @Query("SELECT blockedId FROM blocked_users WHERE userId = :userId")
    List<String> getBlockedIds(String userId);

    @Query("SELECT COUNT(*) FROM blocked_users WHERE userId = :userId AND blockedId = :blockedId")
    int isBlocked(String userId, String blockedId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BlockEntity block);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<BlockEntity> blocks);

    @Query("DELETE FROM blocked_users WHERE userId = :userId AND blockedId = :blockedId")
    void delete(String userId, String blockedId);
}