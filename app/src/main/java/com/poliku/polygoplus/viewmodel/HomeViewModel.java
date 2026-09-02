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

/**
 * Principal Rule 1.1: Separation of Concerns
 * This ViewModel handles all data logic for the Home Screen.
 * The HomeFragment only observes these changes and renders them.
 */
public class HomeViewModel extends AndroidViewModel {

    private final MutableLiveData<List<AppDataStore.ProductRecord>> _products = new MutableLiveData<>();
    public final LiveData<List<AppDataStore.ProductRecord>> products = _products;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public final LiveData<String> error = _error;

    public HomeViewModel(@NonNull Application application) {
        super(application);
    }

    public void loadProducts() {
        _isLoading.setValue(true);
        
        NetworkApi.getListings(new NetworkApi.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Rule 2.2: Process parsing on background thread
                new Thread(() -> {
                    List<AppDataStore.ProductRecord> list = new ArrayList<>();
                    JSONArray arr = response.optJSONArray("listings");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o != null) {
                                AppDataStore.ProductRecord p = AppDataStore.ProductRecord.fromJson(o);
                                if (p != null) list.add(p);
                            }
                        }
                    }
                    // Update UI using postValue (thread-safe)
                    _products.postValue(list);
                    _isLoading.postValue(false);
                }).start();
            }

            @Override
            public void onError(String message) {
                // Rule 2.2: Fallback parsing also on background
                new Thread(() -> {
                    _products.postValue(AppDataStore.getListings(getApplication()));
                    _isLoading.postValue(false);
                    _error.postValue(message);
                }).start();
            }
        });
    }
}
