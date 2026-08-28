package com.poliku.polygoplus;

import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.poliku.polygoplus.ui.ZoomImageView;

import java.util.ArrayList;

public class ImageGalleryActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGES = "images";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_FALLBACK_RES = "fallback_res";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_gallery);
        ArrayList<String> images = getIntent().getStringArrayListExtra(EXTRA_IMAGES);
        if (images == null) images = new ArrayList<>();
        int fallback = getIntent().getIntExtra(EXTRA_FALLBACK_RES, R.drawable.bg_product_home);
        int start = getIntent().getIntExtra(EXTRA_INDEX, 0);
        ViewPager2 pager = findViewById(R.id.galleryPager);
        ArrayList<String> finalImages = images;
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ZoomImageView image = new ZoomImageView(parent.getContext());
                image.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                return new RecyclerView.ViewHolder(image) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ZoomImageView image = (ZoomImageView) holder.itemView;
                if (finalImages.isEmpty()) {
                    image.setImageResource(fallback);
                } else {
                    image.setImageURI(Uri.parse(finalImages.get(position)));
                }
            }

            @Override
            public int getItemCount() {
                return Math.max(1, finalImages.size());
            }
        });
        pager.setCurrentItem(Math.max(0, start), false);
        findViewById(R.id.btnCloseGallery).setOnClickListener(v -> finish());
    }
}
