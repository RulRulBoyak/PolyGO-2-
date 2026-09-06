package com.poliku.polygoplus.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.NetworkApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ExploreViewModel extends AndroidViewModel {

    private final List<AppDataStore.ProductRecord> allItems = new ArrayList<>();
    
    private final MutableLiveData<List<AppDataStore.ProductRecord>> _filteredItems = new MutableLiveData<>();
    public final LiveData<List<AppDataStore.ProductRecord>> filteredItems = _filteredItems;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<Boolean> _isMoreLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isMoreLoading = _isMoreLoading;

    private int currentTab = 0; // 0 for Products, 1 for Services
    private int offset = 0;
    private final int limit = 20;
    private boolean hasNextPage = true;

    public ExploreViewModel(@NonNull Application application) {
        super(application);
    }

    public void setTab(int tabIndex) {
        this.currentTab = tabIndex;
        applyFilters();
    }

    public void loadListings() {
        offset = 0;
        hasNextPage = true;
        _isLoading.setValue(true);
        fetchData(true);
    }

    public void loadMore() {
        if (_isMoreLoading.getValue() == Boolean.TRUE || !hasNextPage) return;
        _isMoreLoading.setValue(true);
        fetchData(false);
    }

    private void fetchData(boolean clear) {
        NetworkApi.getListings(offset, limit, "newest", new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                new Thread(() -> {
                    synchronized (allItems) {
                        if (clear) allItems.clear();
                        
                        JSONArray list = response.optJSONArray("listings");
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                JSONObject o = list.optJSONObject(i);
                                if (o != null) {
                                    AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                                    if (p != null) allItems.add(p);
                                }
                            }
                        }
                        offset += limit;
                        hasNextPage = response.optBoolean("has_next", false);
                    }
                    applyFilters();
                    _isLoading.postValue(false);
                    _isMoreLoading.postValue(false);
                }).start();
            }

            @Override
            public void onError(String message) {
                if (clear) {
                    new Thread(() -> {
                        synchronized (allItems) {
                            allItems.clear();
                            allItems.addAll(AppDataStore.getListings(getApplication()));
                        }
                        applyFilters();
                        _isLoading.postValue(false);
                    }).start();
                } else {
                    _isMoreLoading.postValue(false);
                }
            }
        });
    }

    private void applyFilters() {
        new Thread(() -> {
            List<AppDataStore.ProductRecord> filtered = new ArrayList<>();
            String[] serviceCats = {"Repair", "Printing", "Delivery", "Cleaning", "Lessons", "Laundry", "Services"};
            
            synchronized (allItems) {
                for (AppDataStore.ProductRecord item : allItems) {
                    boolean isService = false;
                    for (String cat : serviceCats) {
                        if (cat.equalsIgnoreCase(item.category)) { isService = true; break; }
                    }
                    
                    if (currentTab == 1 && isService) filtered.add(item);
                    else if (currentTab == 0 && !isService) filtered.add(item);
                }
            }
            _filteredItems.postValue(filtered);
        }).start();
    }
}
