package com.poliku.polygoplus.data;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.data.local.dao.ChatDao;
import com.poliku.polygoplus.data.local.dao.ListingDao;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.util.Resource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public final class PolyGoRepository {

    private final PolyGoApi api;
    private final ListingDao listingDao;
    private final ChatDao chatDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Inject
    public PolyGoRepository(PolyGoApi api, ListingDao listingDao, ChatDao chatDao) {
        this.api = api;
        this.listingDao = listingDao;
        this.chatDao = chatDao;
    }

    // --- Room Database Access ---

    public LiveData<List<ListingEntity>> getLocalListings() {
        return listingDao.getAllListings();
    }

    public void refreshListings(String currentUserId) {
        PolyGoApi.ListingsRequest req = new PolyGoApi.ListingsRequest();
        req.offset = 0;
        req.limit = 100;
        req.sort = "newest";
        api.getListings(req).enqueue(new Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(Call<PolyGoApi.ListingsResponse> call, Response<PolyGoApi.ListingsResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executor.execute(() -> {
                        List<ListingEntity> entities = new ArrayList<>();
                        for (PolyGoApi.Listing l : response.body().listings) {
                            boolean isOwner = currentUserId != null && currentUserId.equals(l.owner_id);
                            ListingEntity entity = new ListingEntity(l.id, l.title, l.seller, l.price, l.rating,
                                    l.distance, l.image_url, l.category, l.description, l.owner_id,
                                    l.available, isOwner);
                            entity.reviewCount = l.review_count;
                            entities.add(entity);
                        }
                        listingDao.insertListings(entities);
                    });
                }
            }

            @Override
            public void onFailure(Call<PolyGoApi.ListingsResponse> call, Throwable t) {}
        });
    }

    // --- Legacy methods for backward compatibility ---

    public void login(String studentId, String password, Callback<PolyGoApi.LoginResponse> callback) {
        api.login(new PolyGoApi.LoginRequest(studentId, password)).enqueue(callback);
    }

    public void googleLogin(String idToken, Callback<PolyGoApi.LoginResponse> callback) {
        api.googleLogin(new PolyGoApi.GoogleLoginRequest(idToken)).enqueue(callback);
    }

    public void register(String name, String studentId, String email, String password, Callback<PolyGoApi.LoginResponse> callback) {
        api.register(new PolyGoApi.RegisterRequest(name, studentId, email, password, true)).enqueue(callback);
    }

    public void sendOtp(String email, Callback<PolyGoApi.OtpSendResponse> callback) {
        api.sendOtp(new PolyGoApi.OtpRequest(email)).enqueue(callback);
    }

    public void verifyOtp(String email, String otp, Callback<BaseResponse> callback) {
        api.verifyOtp(new PolyGoApi.OtpRequest(email, otp)).enqueue(callback);
    }

    public void getCategories(Callback<PolyGoApi.CategoryResponse> callback) {
        api.getCategories(new PolyGoApi.BaseRequest()).enqueue(callback);
    }

    public void getMajors(Callback<PolyGoApi.MajorsResponse> callback) {
        api.getMajors(new PolyGoApi.BaseRequest()).enqueue(callback);
    }

    public void proposeCategory(String name, Callback<BaseResponse> callback) {
        api.proposeCategory(new PolyGoApi.ProposeCategoryRequest(name)).enqueue(callback);
    }

    public void getListings(int offset, int limit, String sort, Callback<PolyGoApi.ListingsResponse> callback) {
        getListings(offset, limit, sort, null, callback);
    }

    public void getListings(int offset, int limit, String sort, Integer major, Callback<PolyGoApi.ListingsResponse> callback) {
        PolyGoApi.ListingsRequest req = new PolyGoApi.ListingsRequest();
        req.offset = offset;
        req.limit = limit;
        req.sort = sort;
        req.major = major;
        api.getListings(req).enqueue(callback);
    }

    public void searchListings(String query, String sort, Callback<PolyGoApi.ListingsResponse> callback) {
        PolyGoApi.ListingsRequest req = new PolyGoApi.ListingsRequest();
        req.query = query;
        req.sort = sort;
        req.action = "search";
        req.limit = 50;
        api.getListings(req).enqueue(callback);
    }

    public void getMyListings(Callback<PolyGoApi.ListingsResponse> callback) {
        PolyGoApi.ListingsRequest req = new PolyGoApi.ListingsRequest(null, "mylistings");
        api.getMyListings(req).enqueue(callback);
    }

    public void relistListing(String listingId, Callback<BaseResponse> callback) {
        PolyGoApi.ListingsRequest req = new PolyGoApi.ListingsRequest(listingId, "relist");
        api.relistListing(req).enqueue(callback);
    }

    public void getListing(String id, Callback<PolyGoApi.ListingsResponse> callback) {
        api.getListings(new PolyGoApi.ListingsRequest(id)).enqueue(callback);
    }

    public void markSold(String listingId, Callback<BaseResponse> callback) {
        api.markSold(new PolyGoApi.ListingsRequest(listingId, "mark_sold")).enqueue(callback);
    }

    public void updateProfile(String userId, String name, String email, String mobile, String photo, String bio, Callback<BaseResponse> callback) {
        PolyGoApi.UpdateProfileRequest req = new PolyGoApi.UpdateProfileRequest();
        req.user_id = userId;
        req.full_name = name;
        req.email = email;
        req.mobile = mobile;
        req.profile_pic_url = photo;
        req.bio = bio;
        api.updateProfile(req).enqueue(callback);
    }

    public void updateFcmToken(String userId, String token, Callback<BaseResponse> callback) {
        PolyGoApi.UpdateProfileRequest req = new PolyGoApi.UpdateProfileRequest();
        req.user_id = userId;
        req.fcm_token = token;
        api.updateProfile(req).enqueue(callback);
    }

    public void updateAccountPrivacy(String userId, boolean isPrivate, Callback<BaseResponse> callback) {
        PolyGoApi.UpdateProfileRequest req = new PolyGoApi.UpdateProfileRequest();
        req.user_id = userId;
        req.isPrivate = isPrivate;
        api.updateProfile(req).enqueue(callback);
    }

    public void notifyPksSecurity(String userId, String threadId, String listingId, String landmark, Double latitude, Double longitude, Callback<BaseResponse> callback) {
        PolyGoApi.SecurityLogRequest req = new PolyGoApi.SecurityLogRequest();
        req.user_id = userId;
        req.thread_id = threadId;
        req.listing_id = listingId;
        req.landmark = landmark;
        req.latitude = latitude;
        req.longitude = longitude;
        req.action = "log";
        api.logSecurity(req).enqueue(callback);
    }

    public void addListing(String ownerId, String title, String category, String price, String description, String image, String tags, String location, Callback<PolyGoApi.AddListingResponse> callback) {
        addListing(ownerId, title, category, price, description, image, tags, location, null, null, callback);
    }

    public void addListing(String ownerId, String title, String category, String price, String description, String image, String tags, String location, String freeSlots, Integer majorId, Callback<PolyGoApi.AddListingResponse> callback) {
        PolyGoApi.AddListingRequest req = new PolyGoApi.AddListingRequest();
        req.owner_id = ownerId;
        req.title = title;
        req.category = category;
        req.price = price;
        req.description = description;
        req.image_url = image;
        req.tags = tags;
        req.location = location;
        req.free_slots = freeSlots;
        req.major_id = majorId;
        api.addListing(req).enqueue(callback);
    }

    public void toggleFavorite(String userId, String listingId, Callback<BaseResponse> callback) {
        PolyGoApi.FavoriteRequest req = new PolyGoApi.FavoriteRequest();
        req.user_id = userId;
        req.listing_id = listingId;
        req.action = "toggle";
        api.toggleFavorite(req).enqueue(callback);
    }

    public void getFavorites(String userId, Callback<PolyGoApi.ListingsResponse> callback) {
        PolyGoApi.FavoriteRequest req = new PolyGoApi.FavoriteRequest();
        req.user_id = userId;
        req.action = "list";
        api.getFavorites(req).enqueue(callback);
    }

    public void getThreads(String userId, Callback<PolyGoApi.ThreadsResponse> callback) {
        PolyGoApi.MessageRequest req = new PolyGoApi.MessageRequest();
        req.user_id = userId;
        req.action = "list";
        api.getThreads(req).enqueue(callback);
    }

    public void getMessages(String userId, String threadId, Callback<PolyGoApi.MessagesResponse> callback) {
        PolyGoApi.MessageRequest req = new PolyGoApi.MessageRequest();
        req.user_id = userId;
        req.thread_id = threadId;
        req.action = "messages";
        api.getMessages(req).enqueue(callback);
    }

    public void sendMessage(String userId, String threadId, String listingId, String receiverId, String text, Callback<BaseResponse> callback) {
        PolyGoApi.MessageRequest req = new PolyGoApi.MessageRequest();
        req.user_id = userId;
        req.thread_id = threadId;
        req.listing_id = listingId;
        req.receiver_id = receiverId;
        req.text = text;
        req.action = "send";
        api.sendMessage(req).enqueue(callback);
    }

    public void getNotifications(String userId, Callback<PolyGoApi.NotificationsResponse> callback) {
        PolyGoApi.NotificationRequest req = new PolyGoApi.NotificationRequest();
        req.user_id = userId;
        api.getNotifications(req).enqueue(callback);
    }

    public void markNotificationsRead(String userId, Callback<BaseResponse> callback) {
        PolyGoApi.NotificationRequest req = new PolyGoApi.NotificationRequest();
        req.user_id = userId;
        req.action = "read";
        api.markNotificationsRead(req).enqueue(callback);
    }

    public void addTransaction(String userId, String listingId, String sellerId, String amount, Callback<BaseResponse> callback) {
        PolyGoApi.TransactionRequest req = new PolyGoApi.TransactionRequest();
        req.user_id = userId;
        req.listing_id = listingId;
        req.seller_id = sellerId;
        req.amount = amount;
        req.action = "add";
        api.addTransaction(req).enqueue(callback);
    }

    public void getTransactions(String userId, Callback<PolyGoApi.TransactionsResponse> callback) {
        PolyGoApi.TransactionRequest req = new PolyGoApi.TransactionRequest();
        req.user_id = userId;
        req.action = "list";
        api.getTransactions(req).enqueue(callback);
    }

    public void updateTransactionStatus(String userId, String transactionId, String status, Callback<BaseResponse> callback) {
        PolyGoApi.TransactionRequest req = new PolyGoApi.TransactionRequest();
        req.user_id = userId;
        req.transaction_id = transactionId;
        req.status = status;
        req.action = "update";
        api.updateTransactionStatus(req).enqueue(callback);
    }

    public void getSeller(String sellerId, String sellerName, Callback<PolyGoApi.SellerResponse> callback) {
        PolyGoApi.SellerRequest req = new PolyGoApi.SellerRequest();
        req.seller_id = sellerId;
        req.seller_name = sellerName;
        req.action = "profile";
        api.getSeller(req).enqueue(callback);
    }

    public void getSellerMetrics(String userId, Callback<PolyGoApi.SellerMetricsResponse> callback) {
        PolyGoApi.SellerRequest req = new PolyGoApi.SellerRequest();
        req.seller_id = userId;
        req.action = "metrics";
        api.getSellerMetrics(req).enqueue(callback);
    }

    public void getImpactMetrics(Callback<PolyGoApi.GreenMetricsResponse> callback) {
        PolyGoApi.GreenRequest req = new PolyGoApi.GreenRequest();
        req.action = "metrics";
        api.getGreenMetrics(req).enqueue(callback);
    }

    public void getGreenLeaderboard(int limit, Callback<PolyGoApi.GreenLeaderboardResponse> callback) {
        PolyGoApi.GreenRequest req = new PolyGoApi.GreenRequest();
        req.action = "leaderboard";
        req.limit = limit;
        api.getGreenLeaderboard(req).enqueue(callback);
    }

    public void submitReport(String userId, String targetType, String targetId, String reason, String details, Callback<BaseResponse> callback) {
        PolyGoApi.ReportRequest req = new PolyGoApi.ReportRequest();
        req.user_id = userId;
        req.target_type = targetType;
        req.target_id = targetId;
        req.reason = reason;
        req.details = details;
        api.submitReport(req).enqueue(callback);
    }

    public void deleteAccount(Callback<BaseResponse> callback) {
        api.deleteAccount(new PolyGoApi.BaseRequest()).enqueue(callback);
    }

    public void getBlockedUsers(String userId, Callback<PolyGoApi.BlockResponse> callback) {
        api.listBlockedUsers(new PolyGoApi.BlockRequest(null, "list")).enqueue(callback);
    }

    public void blockUser(String userId, String blockedId, Callback<BaseResponse> callback) {
        PolyGoApi.BlockRequest req = new PolyGoApi.BlockRequest(blockedId, "block");
        req.user_id = userId;
        api.blockUser(req).enqueue(callback);
    }

    public void unblockUser(String userId, String blockedId, Callback<BaseResponse> callback) {
        PolyGoApi.BlockRequest req = new PolyGoApi.BlockRequest(blockedId, "unblock");
        req.user_id = userId;
        api.unblockUser(req).enqueue(callback);
    }

    public void exportData(Callback<PolyGoApi.ExportDataResponse> callback) {
        api.exportData(new PolyGoApi.BaseRequest()).enqueue(callback);
    }

    public void submitReview(String userId, String seller, String listingId, int stars, String comment, Callback<BaseResponse> callback) {
        PolyGoApi.ReviewRequest req = new PolyGoApi.ReviewRequest();
        req.user_id = userId;
        req.seller = seller;
        req.listing_id = listingId;
        req.stars = stars;
        req.comment = comment;
        api.submitReview(req).enqueue(callback);
    }

    public void forgotPassword(String identifier, Callback<PolyGoApi.ForgotPasswordResponse> callback) {
        api.forgotPassword(new PolyGoApi.ForgotPasswordRequest(identifier)).enqueue(callback);
    }

    public void updatePassword(String newPassword, Callback<BaseResponse> callback) {
        api.updatePassword(new PolyGoApi.UpdatePasswordRequest(newPassword)).enqueue(callback);
    }

    public void getStatus(Callback<BaseResponse> callback) {
        api.getStatus().enqueue(callback);
    }

    public void getPulse(Callback<PolyGoApi.PulseResponse> callback) {
        api.getPulse(new PolyGoApi.PulseRequest()).enqueue(callback);
    }

    public void postPulse(String tag, String title, String body, Callback<BaseResponse> callback) {
        api.postPulse(new PolyGoApi.PulseRequest(tag, title, body)).enqueue(callback);
    }

    public void submitVerification(String photoUrl, Callback<BaseResponse> callback) {
        api.submitVerification(new PolyGoApi.VerificationRequest(photoUrl)).enqueue(callback);
    }

    public void markThreadRead(String threadId) {
        executor.execute(() -> chatDao.markThreadRead(threadId));
    }

    public void uploadImage(File file, Callback<PolyGoApi.UploadResponse> callback) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);
        api.uploadImage(body).enqueue(callback);
    }

    public void uploadImage(Context context, Uri uri, Callback<PolyGoApi.UploadResponse> callback) {
        executor.execute(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    postFailure(callback, "Could not open input stream");
                    return;
                }
                byte[] bytes = getBytes(inputStream);
                inputStream.close();
                RequestBody requestFile = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
                MultipartBody.Part body = MultipartBody.Part.createFormData("image", "upload.jpg", requestFile);
                api.uploadImage(body).enqueue(callback);
            } catch (Exception e) {
                postFailure(callback, e);
            }
        });
    }

    public void aiSuggest(Context context, Uri uri, Callback<PolyGoApi.AiSuggestResponse> callback) {
        executor.execute(() -> {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                if (inputStream == null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onFailure(null, new Throwable("Could not open input stream")));
                    return;
                }
                byte[] bytes = getBytes(inputStream);
                inputStream.close();
                RequestBody requestFile = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
                MultipartBody.Part body = MultipartBody.Part.createFormData("image", "upload.jpg", requestFile);
                api.aiSuggestImage(body).enqueue(callback);
            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onFailure(null, e));
            }
        });
    }

    public void reportBug(String description, String deviceModel, String osVersion,
                          String appVersion, String screen, String screenshotUrl, Callback<BaseResponse> callback) {
        api.reportBug(new PolyGoApi.BugReportRequest(description, deviceModel, osVersion, appVersion, screen, screenshotUrl))
                .enqueue(callback);
    }

    private void postFailure(final Callback<PolyGoApi.UploadResponse> callback, final String message) {
        postFailure(callback, new Throwable(message));
    }

    private void postFailure(final Callback<PolyGoApi.UploadResponse> callback, final Throwable throwable) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> callback.onFailure(null, throwable));
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 4096;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }
}
