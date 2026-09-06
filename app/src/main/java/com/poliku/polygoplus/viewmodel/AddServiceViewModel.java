package com.poliku.polygoplus.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

/**
 * Principal Rule 3.3: Contextual Data Restoration
 * This ViewModel ensures no user input is lost during screen rotations
 * or process death for the "Add Service" screen.
 */
public class AddServiceViewModel extends ViewModel {

    private final SavedStateHandle state;
    
    private final MutableLiveData<String> _imageUri = new MutableLiveData<>("");
    public final LiveData<String> imageUri = _imageUri;

    public AddServiceViewModel(SavedStateHandle savedStateHandle) {
        this.state = savedStateHandle;
    }

    public void setImageUri(String uri) {
        _imageUri.setValue(uri);
    }

    public void setTitle(String val) { state.set("title", val); }
    public String getTitle() { return state.get("title"); }

    public void setCategory(String val) { state.set("category", val); }
    public String getCategory() { return state.get("category") != null ? state.get("category") : "Repair"; }

    public void setPrice(String val) { state.set("price", val); }
    public String getPrice() { return state.get("price") != null ? state.get("price") : "0.00"; }

    public void setAvailability(String val) { state.set("availability", val); }
    public String getAvailability() { return state.get("availability"); }

    public void setDeliveryTime(String val) { state.set("time", val); }
    public String getDeliveryTime() { return state.get("time"); }

    public void setDescription(String val) { state.set("desc", val); }
    public String getDescription() { return state.get("desc"); }

    public void setPriceTypeIndex(int index) { state.set("price_type", index); }
    public int getPriceTypeIndex() { return state.get("price_type") != null ? state.get("price_type") : 0; }

    public void setFulfillmentIndex(int index) { state.set("fulfillment", index); }
    public int getFulfillmentIndex() { return state.get("fulfillment") != null ? state.get("fulfillment") : 0; }
}
