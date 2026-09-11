package com.poliku.polygoplus.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.poliku.polygoplus.data.local.dao.BlockDao;
import com.poliku.polygoplus.data.local.dao.ChatDao;
import com.poliku.polygoplus.data.local.dao.ListingDao;
import com.poliku.polygoplus.data.local.entity.BlockEntity;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.data.local.entity.MessageEntity;
import com.poliku.polygoplus.data.local.entity.ThreadEntity;
import com.poliku.polygoplus.data.local.entity.UserEntity;

@Database(entities = {ListingEntity.class, UserEntity.class, ThreadEntity.class, MessageEntity.class, BlockEntity.class},
          version = 3, exportSchema = false)
public abstract class PolyGoDatabase extends RoomDatabase {
    public abstract ListingDao listingDao();
    public abstract ChatDao chatDao();
    public abstract BlockDao blockDao();
}
