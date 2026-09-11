package com.poliku.polygoplus.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages", indices = {
    @Index("threadId")
})
public class MessageEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String threadId;
    public String sender;
    public String text;
    public long time;
    public boolean mine;

    public MessageEntity(String threadId, String sender, String text, long time, boolean mine) {
        this.threadId = threadId;
        this.sender = sender;
        this.text = text;
        this.time = time;
        this.mine = mine;
    }
}
