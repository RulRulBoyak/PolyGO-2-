package com.poliku.polygoplus.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.util.Resource;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Principal Rule 1.1: Separation of Concerns
 * This ViewModel handles all data logic for the Home Screen.
 * The HomeFragment only observes these changes and renders them.
 */
@HiltViewModel
public class HomeViewModel extends AndroidViewModel {

    private final PolyGoRepository repository;
    private final MutableLiveData<Resource<List<ListingEntity>>> _productsResource = new MutableLiveData<>();
    public final LiveData<Resource<List<ListingEntity>>> productsResource = _productsResource;

    @Inject
    public HomeViewModel(@NonNull Application application, PolyGoRepository repository) {
        super(application);
        this.repository = repository;
    }

    public void loadProducts() {
        _productsResource.setValue(Resource.loading(null));
        
        repository.getListings(0, 50, "newest", new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                new Thread(() -> {
                    List<ListingEntity> list = new ArrayList<>();
                    PolyGoApi.ListingsResponse body = response.body();
                    if (body != null && body.listings != null) {
                        String currentUserId = AppDataStore.userId(getApplication());
                        for (PolyGoApi.Listing l : body.listings) {
                            boolean isOwner = currentUserId != null && currentUserId.equals(l.owner_id);
                            ListingEntity entity = new ListingEntity(l.id, l.title, l.seller, l.price, 
                                    l.rating, l.distance, l.image_url, l.category, l.description, 
                                    l.owner_id, l.available, isOwner);
                            entity.reviewCount = l.review_count;
                            list.add(entity);
                        }
                    }
                    _productsResource.postValue(Resource.success(list));
                }).start();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                _productsResource.postValue(Resource.error(t.getMessage(), null));
            }
        });
    }
}
