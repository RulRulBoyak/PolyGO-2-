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

    private final MutableLiveData<Resource<List<ListingEntity>>> _picksResource = new MutableLiveData<>();
    public final LiveData<Resource<List<ListingEntity>>> picksResource = _picksResource;

    private final MutableLiveData<Resource<List<PolyGoApi.Category>>> _categoriesResource = new MutableLiveData<>();
    public final LiveData<Resource<List<PolyGoApi.Category>>> categoriesResource = _categoriesResource;

    @Inject
    public HomeViewModel(@NonNull Application application, PolyGoRepository repository) {
        super(application);
        this.repository = repository;
    }

    public void loadProducts() {
        _productsResource.setValue(Resource.loading(null));

        repository.getListings(0, 50, "newest", null, currentUserId(), new Callback<PolyGoApi.ListingsResponse>() {
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
                                    l.rating, l.distance != null ? l.distance : (l.location == null ? "" : l.location),
                                    l.image_url, l.category, l.description,
                                    l.owner_id, l.available, isOwner, l.location, l.postedAt, l.views);
                            entity.reviewCount = l.review_count;
                            entity.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
                            list.add(entity);
                        }
                        AppDataStore.updateListingsCache(getApplication(), body.listings);
                    }
                    _productsResource.postValue(Resource.success(list));
                }).start();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                new Thread(() -> {
                    List<ListingEntity> cached = AppDataStore.listingsToEntities(AppDataStore.getActiveListings(getApplication()));
                    if (cached != null && !cached.isEmpty()) {
                        _productsResource.postValue(Resource.success(cached));
                    } else {
                        _productsResource.postValue(Resource.error(t.getMessage(), null));
                    }
                }).start();
            }
        });
    }

    public void loadPicks() {
        _picksResource.setValue(Resource.loading(null));
        int majorId = AppDataStore.pickedMajorId(getApplication());
        repository.getListings(0, 12, "top_rated", majorId != 0 ? majorId : null, currentUserId(), new Callback<PolyGoApi.ListingsResponse>() {
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
                                    l.rating, l.distance != null ? l.distance : (l.location == null ? "" : l.location),
                                    l.image_url, l.category, l.description,
                                    l.owner_id, l.available, isOwner, l.location, l.postedAt, l.views);
                            entity.reviewCount = l.review_count;
                            entity.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
                            list.add(entity);
                        }
                    }
                    _picksResource.postValue(Resource.success(list));
                }).start();
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                new Thread(() -> {
                    List<ListingEntity> cached = AppDataStore.listingsToEntities(AppDataStore.getActiveListings(getApplication()));
                    if (cached != null && !cached.isEmpty()) {
                        _picksResource.postValue(Resource.success(cached));
                    } else {
                        _picksResource.postValue(Resource.error(t.getMessage(), null));
                    }
                }).start();
            }
        });
    }

    public void loadCategories() {
        _categoriesResource.setValue(Resource.loading(null));
        repository.getCategories(new Callback<PolyGoApi.CategoryResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.CategoryResponse> call, Response<PolyGoApi.CategoryResponse> response) {
                new Thread(() -> {
                    List<PolyGoApi.Category> list = new ArrayList<>();
                    PolyGoApi.CategoryResponse body = response.body();
                    if (body != null && body.categories != null) {
                        list.addAll(body.categories);
                    }
                    _categoriesResource.postValue(Resource.success(list));
                }).start();
            }

            @Override
            public void onFailure(Call<PolyGoApi.CategoryResponse> call, Throwable t) {
                _categoriesResource.postValue(Resource.error(t.getMessage(), null));
            }
        });
    }

    private Integer currentUserId() {
        String id = AppDataStore.userId(getApplication());
        if (id == null) return null;
        try {
            int parsed = Integer.parseInt(id);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}