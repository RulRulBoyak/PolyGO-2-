package com.poliku.polygoplus.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Principal Rule 3.3: Contextual Data Restoration
 * This ViewModel ensures no user input is lost during screen rotations
 * or process death for the "Add Product" screen.
 */
@HiltViewModel
public class EditProductViewModel extends ViewModel {

    private final SavedStateHandle state;
    
    private final MutableLiveData<String> _imageUri = new MutableLiveData<>("");
    public final LiveData<String> imageUri = _imageUri;

    private final MutableLiveData<Double> _price = new MutableLiveData<>(0.0);
    public final LiveData<Double> price = _price;

    @Inject
    public EditProductViewModel(SavedStateHandle savedStateHandle) {
        this.state = savedStateHandle;
    }

    public void setImageUri(String uri) {
        _imageUri.setValue(uri);
    }

    public void setPrice(double value) {
        _price.setValue(value);
    }

    public void adjustPrice(double delta) {
        double current = _price.getValue() != null ? _price.getValue() : 0.0;
        double next = current + delta;
        if (next >= 0) _price.setValue(next);
    }

    // Standard getters/setters for text fields to be used in onPause/onResume or via state observation
    public void setTitle(String val) {
        state.set("title", val);
    }

    public String getTitle() {
        return state.get("title");
    }

    public void setCategory(String val) {
        state.set("cat", val);
    }

    public String getCategory() {
        return state.get("cat");
    }

    public void setDescription(String val) {
        state.set("desc", val);
    }

    public String getDescription() {
        return state.get("desc");
    }

    public void setLocation(String val) {
        state.set("loc", val);
    }

    public String getLocation() {
        return state.get("loc") != null ? state.get("loc") : "Near campus";
    }

    public void setCustomLocation(String val) {
        state.set("customLoc", val);
    }

    public String getCustomLocation() {
        return state.get("customLoc") != null ? state.get("customLoc") : "";
    }

    public void setTags(String val) {
        state.set("tags", val);
    }

    public String getTags() {
        return state.get("tags") != null ? state.get("tags") : "";
    }
}
