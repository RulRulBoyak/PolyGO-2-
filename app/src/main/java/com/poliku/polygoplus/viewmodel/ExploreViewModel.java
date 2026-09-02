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
import java.util.Locale;

/**
 * Principal Rule 1.1: Separation of Concerns
 * This ViewModel handles all data filtering and fetching for the Explore Screen.
 */
public class ExploreViewModel extends AndroidViewModel {

    private final List<AppDataStore.ProductRecord> allItems = new ArrayList<>();
    
    private final MutableLiveData<List<AppDataStore.ProductRecord>> _filteredItems = new MutableLiveData<>();
    public final LiveData<List<AppDataStore.ProductRecord>> filteredItems = _filteredItems;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private int currentTab = 0; // 0 for Products, 1 for Services

    public ExploreViewModel(@NonNull Application application) {
        super(application);
    }

    public void setTab(int tabIndex) {
        this.currentTab = tabIndex;
        applyFilters();
    }

    public void loadListings() {
        _isLoading.setValue(true);
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Rule 2.2: Large array parsing on background thread
                new Thread(() -> {
                    synchronized (allItems) {
                        allItems.clear();
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
                    }
                    applyFilters();
                    _isLoading.postValue(false);
                }).start();
            }

            @Override
            public void onError(String message) {
                new Thread(() -> {
                    synchronized (allItems) {
                        allItems.clear();
                        allItems.addAll(AppDataStore.getListings(getApplication()));
                    }
                    applyFilters();
                    _isLoading.postValue(false);
                }).start();
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
