package com.poliku.polygoplus.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.poliku.polygoplus.data.local.entity.ListingEntity;

import java.util.List;

@Dao
public interface ListingDao {
    @Query("SELECT * FROM listings ORDER BY id DESC")
    LiveData<List<ListingEntity>> getAllListings();

    @Query("SELECT * FROM listings WHERE category = :category")
    LiveData<List<ListingEntity>> getListingsByCategory(String category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertListings(List<ListingEntity> listings);

    @Query("DELETE FROM listings")
    void deleteAll();
}
