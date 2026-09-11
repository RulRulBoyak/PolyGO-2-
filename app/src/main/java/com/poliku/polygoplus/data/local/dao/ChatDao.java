package com.poliku.polygoplus.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.poliku.polygoplus.data.local.entity.MessageEntity;
import com.poliku.polygoplus.data.local.entity.ThreadEntity;

import java.util.List;

@Dao
public interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY lastMessageTime DESC")
    LiveData<List<ThreadEntity>> getAllThreads();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertThreads(List<ThreadEntity> threads);

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY time ASC")
    LiveData<List<MessageEntity>> getMessagesForThread(String threadId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<MessageEntity> messages);

    @Query("DELETE FROM chat_threads")
    void deleteAllThreads();

    @Query("UPDATE chat_threads SET unread = 0 WHERE id = :threadId")
    void markThreadRead(String threadId);
}
