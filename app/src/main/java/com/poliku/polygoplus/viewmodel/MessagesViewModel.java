package com.poliku.polygoplus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ThreadEntity;
import com.poliku.polygoplus.util.Resource;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class MessagesViewModel extends AndroidViewModel {

    private final PolyGoRepository repository;
    private final MutableLiveData<Resource<List<ThreadEntity>>> _threadsResource = new MutableLiveData<>();
    public final LiveData<Resource<List<ThreadEntity>>> threadsResource = _threadsResource;

    @Inject
    public MessagesViewModel(@NonNull Application application, PolyGoRepository repository) {
        super(application);
        this.repository = repository;
    }

    public void loadThreads() {
        _threadsResource.setValue(Resource.loading(null));
        String userId = AppDataStore.userId(getApplication());
        
        repository.getThreads(userId, new Callback<PolyGoApi.ThreadsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ThreadsResponse> call, Response<PolyGoApi.ThreadsResponse> response) {
                new Thread(() -> {
                    List<ThreadEntity> list = new ArrayList<>();
                    PolyGoApi.ThreadsResponse body = response.body();
                    if (body != null && body.threads != null) {
                        for (PolyGoApi.Thread t : body.threads) {
                            list.add(new ThreadEntity(t.id, t.listingId, t.name, t.last_message, 
                                    t.lastMessageTime, t.unread));
                        }
                    }
                    _threadsResource.postValue(Resource.success(list));
                }).start();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ThreadsResponse> call, Throwable t) {
                _threadsResource.postValue(Resource.error(t.getMessage(), null));
            }
        });
    }
}
