package com.poliku.polygoplus.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_threads", indices = {
    @Index("lastMessageTime")
})
public class ThreadEntity {
    @PrimaryKey
    @NonNull
    public String id;
    
    public String listingId;
    public String name;
    public String lastMessage;
    public long lastMessageTime;
    public boolean unread;

    public ThreadEntity(@NonNull String id, String listingId, String name, 
                        String lastMessage, long lastMessageTime, boolean unread) {
        this.id = id;
        this.listingId = listingId;
        this.name = name;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unread = unread;
    }
}
