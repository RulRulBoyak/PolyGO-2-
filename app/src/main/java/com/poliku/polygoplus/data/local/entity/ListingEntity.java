package com.poliku.polygoplus.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "listings", indices = {
    @Index("category"),
    @Index("ownerId")
})
public class ListingEntity {
    @PrimaryKey
    @NonNull
    public String id;
    
    public String title;
    public String seller;
    public String price;
    public String rating;
    public String reviewCount;
    public String distance;
    public String imageUrl;
    public String category;
    public String description;
    public String ownerId;
    public String location;
    public boolean available;
    public boolean isOwner;
    public boolean archived;
    public long postedAt;
    public int views;

    public ListingEntity(@NonNull String id, String title, String seller, String price, 
                         String rating, String distance, String imageUrl, String category, 
                         String description, String ownerId, boolean available, boolean isOwner,
                         String location, long postedAt, int views) {
        this.id = id;
        this.title = title;
        this.seller = seller;
        this.price = price;
        this.rating = rating;
        this.distance = distance;
        this.imageUrl = imageUrl;
        this.category = category;
        this.description = description;
        this.ownerId = ownerId;
        this.available = available;
        this.isOwner = isOwner;
        this.location = location;
        this.postedAt = postedAt;
        this.views = views;
    }
}