package com.poliku.polygoplus.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Principal Rule 3.3: Contextual Data Restoration
 * This ViewModel ensures no user input is lost during screen rotations
 * for the "Add Product" screen.
 */
public class EditProductViewModel extends ViewModel {

    private final MutableLiveData<String> _imageUri = new MutableLiveData<>("");
    public final LiveData<String> imageUri = _imageUri;

    private final MutableLiveData<Double> _price = new MutableLiveData<>(0.0);
    public final LiveData<Double> price = _price;

    private String title = "";
    private String category = "";
    private String description = "";
    private String location = "Near campus";

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
    public void setTitle(String val) { this.title = val; }
    public String getTitle() { return title; }

    public void setCategory(String val) { this.category = val; }
    public String getCategory() { return category; }

    public void setDescription(String val) { this.description = val; }
    public String getDescription() { return description; }

    public void setLocation(String val) { this.location = val; }
    public String getLocation() { return location; }
}
