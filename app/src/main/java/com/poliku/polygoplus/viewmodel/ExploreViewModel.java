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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltViewModel
public class ExploreViewModel extends AndroidViewModel {

    private final PolyGoRepository repository;
    private final List<ListingEntity> allItems = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Resource<List<ListingEntity>>> _listingsResource = new MutableLiveData<>();
    public final LiveData<Resource<List<ListingEntity>>> listingsResource = _listingsResource;

    private volatile int currentTab = 0; // 0 for Products, 1 for Services
    private volatile Integer selectedMajor = null;
    private volatile int offset = 0;
    private final int limit = 20;
    private volatile boolean hasNextPage = true;

    @Inject
    public ExploreViewModel(@NonNull Application application, PolyGoRepository repository) {
        super(application);
        this.repository = repository;
    }

    public void setTab(int tabIndex) {
        this.currentTab = tabIndex;
        applyFilters();
    }

    public void setMajor(Integer majorId) {
        this.selectedMajor = majorId;
        loadListings();
    }

    public void loadListings() {
        offset = 0;
        hasNextPage = true;
        _listingsResource.setValue(Resource.loading(null));
        fetchData(true);
    }

    public void loadMore() {
        if (!hasNextPage) return;
        fetchData(false);
    }

    private void fetchData(boolean clear) {
        repository.getListings(offset, limit, "newest", selectedMajor, new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                worker.execute(() -> {
                    synchronized (allItems) {
                        if (clear) allItems.clear();

                        PolyGoApi.ListingsResponse body = response.body();
                        if (body != null && body.listings != null) {
                            String currentUserId = AppDataStore.userId(getApplication());
                            for (PolyGoApi.Listing l : body.listings) {
                                boolean isOwner = currentUserId != null && currentUserId.equals(l.owner_id);
                                ListingEntity entity = new ListingEntity(l.id, l.title, l.seller, l.price,
                                        l.rating, l.distance, l.image_url, l.category, l.description,
                                        l.owner_id, l.available, isOwner);
                                entity.reviewCount = l.review_count;
                                allItems.add(entity);
                            }
                            offset += limit;
                            hasNextPage = body.has_next;
                        }
                    }
                    applyFilters();
                });
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                _listingsResource.postValue(Resource.error(t.getMessage(), null));
            }
        });
    }

    private void applyFilters() {
        worker.execute(() -> {
            List<ListingEntity> filtered = new ArrayList<>();
            String[] serviceCats = {"Repair", "Printing", "Delivery", "Cleaning", "Lessons", "Laundry", "Services"};

            synchronized (allItems) {
                for (ListingEntity item : allItems) {
                    boolean isService = false;
                    for (String cat : serviceCats) {
                        if (cat.equalsIgnoreCase(item.category)) {
                            isService = true;
                            break;
                        }
                    }

                    if (currentTab == 1 && isService) filtered.add(item);
                    else if (currentTab == 0 && !isService) filtered.add(item);
                }
            }
            _listingsResource.postValue(Resource.success(filtered));
        });
    }

    @Override
    protected void onCleared() {
        worker.shutdownNow();
        super.onCleared();
    }
}