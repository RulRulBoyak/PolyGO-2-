package com.poliku.polygoplus.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Principal Rule 3.3: Contextual Data Restoration
 * This ViewModel ensures no user input is lost during screen rotations
 * for the "Add Service" screen.
 */
public class AddServiceViewModel extends ViewModel {

    private final MutableLiveData<String> _imageUri = new MutableLiveData<>("");
    public final LiveData<String> imageUri = _imageUri;

    private String title = "";
    private String category = "Repair";
    private String price = "0.00";
    private String availability = "";
    private String deliveryTime = "";
    private String description = "";
    private int priceTypeIndex = 0;
    private int fulfillmentIndex = 0;

    public void setImageUri(String uri) {
        _imageUri.setValue(uri);
    }

    public void setTitle(String val) { this.title = val; }
    public String getTitle() { return title; }

    public void setCategory(String val) { this.category = val; }
    public String getCategory() { return category; }

    public void setPrice(String val) { this.price = val; }
    public String getPrice() { return price; }

    public void setAvailability(String val) { this.availability = val; }
    public String getAvailability() { return availability; }

    public void setDeliveryTime(String val) { this.deliveryTime = val; }
    public String getDeliveryTime() { return deliveryTime; }

    public void setDescription(String val) { this.description = val; }
    public String getDescription() { return description; }

    public void setPriceTypeIndex(int index) { this.priceTypeIndex = index; }
    public int getPriceTypeIndex() { return priceTypeIndex; }

    public void setFulfillmentIndex(int index) { this.fulfillmentIndex = index; }
    public int getFulfillmentIndex() { return fulfillmentIndex; }
}
