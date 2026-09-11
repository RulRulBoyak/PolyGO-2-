package com.poliku.polygoplus.worker;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.network.ImageUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@HiltWorker
public class PublishListingWorker extends Worker {

    private static final long NETWORK_TIMEOUT_SECONDS = 60;

    private final PolyGoRepository repository;

    @AssistedInject
    public PublishListingWorker(@Assisted @NonNull Context context,
                                @Assisted @NonNull WorkerParameters workerParameters,
                                PolyGoRepository repository) {
        super(context, workerParameters);
        this.repository = repository;
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        String ownerId = input.getString("owner_id");
        String title = input.getString("title");
        String category = input.getString("category");
        String price = input.getString("price");
        String description = input.getString("description");
        String tags = input.getString("tags");
        String location = input.getString("location");
        String freeSlots = input.getString("free_slots");
        Integer majorId = input.getInt("major_id", 0);
        String[] imageUris = input.getStringArray("image_uris");

        if (ownerId == null || title == null || category == null || price == null
                || description == null || location == null || imageUris == null || imageUris.length == 0) {
            return Result.failure();
        }

        List<String> serverUrls = new CopyOnWriteArrayList<>();
        Context context = getApplicationContext();
        for (String uriString : imageUris) {
            Uri compressed = ImageUtils.compressImage(context, Uri.parse(uriString));
            File imageFile = new File(compressed.getPath());
            if (!imageFile.exists()) {
                continue;
            }

            CountDownLatch uploadLatch = new CountDownLatch(1);
            repository.uploadImage(imageFile, new Callback<PolyGoApi.UploadResponse>() {
                @Override
                public void onResponse(Call<PolyGoApi.UploadResponse> call, Response<PolyGoApi.UploadResponse> response) {
                    PolyGoApi.UploadResponse body = response.body();
                    if (response.isSuccessful() && body != null && body.isSuccess() && body.url != null) {
                        serverUrls.add(body.url);
                    }
                    uploadLatch.countDown();
                }

                @Override
                public void onFailure(Call<PolyGoApi.UploadResponse> call, Throwable t) {
                    uploadLatch.countDown();
                }
            });

            boolean finished = await(uploadLatch);
            if (!finished) {
                return Result.failure(new Data.Builder()
                        .putString("error", "Upload timed out. Check your connection.")
                        .build());
            }
        }

        if (serverUrls.isEmpty()) {
            return Result.failure(new Data.Builder()
                    .putString("error", "Upload failed. Check your connection and try again.")
                    .build());
        }

        String finalImageString = join(serverUrls, "|");
        final AtomicReference<String> listingId = new AtomicReference<>();
        CountDownLatch listingLatch = new CountDownLatch(1);
        repository.addListing(ownerId, title, category, price, description, finalImageString, tags, location,
                freeSlots, majorId > 0 ? majorId : null, new Callback<PolyGoApi.AddListingResponse>() {
                    @Override
                    public void onResponse(Call<PolyGoApi.AddListingResponse> call, Response<PolyGoApi.AddListingResponse> response) {
                        PolyGoApi.AddListingResponse body = response.body();
                        if (response.isSuccessful() && body != null && body.isSuccess() && body.id != null) {
                            listingId.set(body.id);
                        }
                        listingLatch.countDown();
                    }

                    @Override
                    public void onFailure(Call<PolyGoApi.AddListingResponse> call, Throwable t) {
                        listingLatch.countDown();
                    }
                });

        boolean finished = await(listingLatch);
        if (!finished) {
            return Result.failure(new Data.Builder()
                    .putString("error", "Request timed out. Check your connection.")
                    .build());
        }

        String id = listingId.get();
        if (id == null) {
            return Result.failure(new Data.Builder()
                    .putString("error", "Could not publish your listing. Please try again.")
                    .build());
        }

        return Result.success(new Data.Builder()
                .putString("listing_id", id)
                .putString("image_url", finalImageString)
                .build());
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}