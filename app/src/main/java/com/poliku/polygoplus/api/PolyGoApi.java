package com.poliku.polygoplus.api;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.poliku.polygoplus.api.model.BaseResponse;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface PolyGoApi {

    @POST("otp.php")
    Call<OtpSendResponse> sendOtp(@Body OtpRequest request);

    @POST("otp.php")
    Call<BaseResponse> verifyOtp(@Body OtpRequest request);

    @POST("login.php")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("google_login.php")
    Call<LoginResponse> googleLogin(@Body GoogleLoginRequest request);

    @POST("register.php")
    Call<LoginResponse> register(@Body RegisterRequest request);

    @POST("categories.php")
    Call<CategoryResponse> getCategories(@Body BaseRequest request);

    @POST("categories.php")
    Call<BaseResponse> proposeCategory(@Body ProposeCategoryRequest request);

    @POST("categories.php")
    Call<MajorsResponse> getMajors(@Body BaseRequest request);

    @POST("campus.php")
    Call<CampusResponse> campus(@Body CampusRequest request);

    @POST("listings.php")
    Call<ListingsResponse> getListings(@Body ListingsRequest request);

    @POST("listings.php")
    Call<BaseResponse> markSold(@Body ListingsRequest request);

    @POST("listings.php")
    Call<ListingsResponse> getMyListings(@Body ListingsRequest request);

    @POST("listings.php")
    Call<BaseResponse> relistListing(@Body ListingsRequest request);

    @POST("update_profile.php")
    Call<BaseResponse> updateProfile(@Body UpdateProfileRequest request);

    @POST("add_listing.php")
    Call<AddListingResponse> addListing(@Body AddListingRequest request);

    @POST("add_listing.php")
    Call<AddListingResponse> updateListing(@Body AddListingRequest request);

    @POST("favorites.php")
    Call<BaseResponse> toggleFavorite(@Body FavoriteRequest request);

    @POST("favorites.php")
    Call<ListingsResponse> getFavorites(@Body FavoriteRequest request);

    @POST("follow.php")
    Call<FollowResponse> followUser(@Body FollowRequest request);

    @POST("alerts.php")
    Call<BaseResponse> toggleAlert(@Body AlertRequest request);

    @POST("alerts.php")
    Call<AlertsResponse> getAlerts(@Body AlertRequest request);

    @POST("messages.php")
    Call<ThreadsResponse> getThreads(@Body MessageRequest request);

    @POST("messages.php")
    Call<MessagesResponse> getMessages(@Body MessageRequest request);

    @POST("messages.php")
    Call<SendMessageResponse> sendMessage(@Body MessageRequest request);

    @POST("notifications.php")
    Call<NotificationsResponse> getNotifications(@Body NotificationRequest request);

    @POST("notifications.php")
    Call<BaseResponse> markNotificationsRead(@Body NotificationRequest request);

    @POST("transactions.php")
    Call<BaseResponse> addTransaction(@Body TransactionRequest request);

    @POST("transactions.php")
    Call<TransactionsResponse> getTransactions(@Body TransactionRequest request);

    @POST("transactions.php")
    Call<BaseResponse> updateTransactionStatus(@Body TransactionRequest request);

    @POST("seller.php")
    Call<SellerResponse> getSeller(@Body SellerRequest request);

    @POST("seller.php")
    Call<SellerMetricsResponse> getSellerMetrics(@Body SellerRequest request);

    @POST("green.php")
    Call<GreenMetricsResponse> getGreenMetrics(@Body GreenRequest request);

    @POST("green.php")
    Call<GreenLeaderboardResponse> getGreenLeaderboard(@Body GreenRequest request);

    @POST("report.php")
    Call<BaseResponse> submitReport(@Body ReportRequest request);

    @POST("block.php")
    Call<BlockResponse> listBlockedUsers(@Body BlockRequest request);

    @POST("block.php")
    Call<BaseResponse> blockUser(@Body BlockRequest request);

    @POST("block.php")
    Call<BaseResponse> unblockUser(@Body BlockRequest request);

    @POST("pulse.php")
    Call<PulseResponse> getPulse(@Body PulseRequest request);

    @POST("pulse.php")
    Call<BaseResponse> postPulse(@Body PulseRequest request);

    @POST("verify.php")
    Call<BaseResponse> submitVerification(@Body VerificationRequest request);

    @POST("verify.php")
    Call<VerificationStatusResponse> verificationStatus(@Body VerificationStatusRequest request);

    @POST("delete_account.php")
    Call<BaseResponse> deleteAccount(@Body BaseRequest request);

    @POST("export_data.php")
    Call<ExportDataResponse> exportData(@Body BaseRequest request);

    @POST("security.php")
    Call<BaseResponse> logSecurity(@Body SecurityLogRequest request);

    @POST("review.php")
    Call<BaseResponse> submitReview(@Body ReviewRequest request);

    @POST("forgot_password.php")
    Call<ForgotPasswordResponse> forgotPassword(@Body ForgotPasswordRequest request);

    @POST("update_password.php")
    Call<BaseResponse> updatePassword(@Body UpdatePasswordRequest request);

    @GET("status.php")
    Call<BaseResponse> getStatus();

    @Multipart
    @POST("upload_image.php")
    Call<UploadResponse> uploadImage(@Part MultipartBody.Part image);

    @Multipart
    @POST("ai_suggest.php")
    Call<AiSuggestResponse> aiSuggestImage(@Part MultipartBody.Part image);

    @POST("report_bug.php")
    Call<BaseResponse> reportBug(@Body BugReportRequest request);

    // Request & Response Classes
    class BaseRequest {
        public String action = "list";
    }

    class OtpRequest {
        public String email, otp, action;

        public OtpRequest(String email) {
            this.email = email;
            this.action = "send";
        }

        public OtpRequest(String email, String otp) {
            this.email = email;
            this.otp = otp;
            this.action = "verify";
        }
    }

    class OtpSendResponse extends BaseResponse {
        public String otp;
    }

    class CampusRequest {
        public String action;
        public String id, course;
        public String room, day_of_week, starts_at, ends_at;
        public CampusRequest(String action) {
            this.action = action;
        }
    }

    class CampusResponse extends BaseResponse {
        public List<CampusEvent> events;
        public List<TimetableEntry> timetable;
    }

    class CampusEvent {
        public String id, title, description, venue, starts_at, ends_at;
        public String theme = "default";
        public String accent_color, emoji, label, cover_url;
        @SerializedName("is_featured")
        public int isFeatured = 0;
        public String organizer_name, organizer_contact, registration_url, map_url;
        @SerializedName("capacity")
        public int capacity = 0;
    }

    class TimetableEntry {
        public int id, day_of_week;
        public String course, room, starts_at, ends_at;
    }

    class LoginRequest {
        public String student_id;
        public String password;

        public LoginRequest(String id, String pass) {
            this.student_id = id;
            this.password = pass;
        }
    }

    class GoogleLoginRequest {
        public String id_token;

        public GoogleLoginRequest(String idToken) {
            this.id_token = idToken;
        }
    }

    class RegisterRequest {
        public String full_name, student_id, email, password;
        public boolean consent_agreed;
        public RegisterRequest(String n, String id, String e, String p, boolean consent) {
            this.full_name = n; this.student_id = id; this.email = e; this.password = p;
            this.consent_agreed = consent;
        }
    }

    class ProposeCategoryRequest {
        public String name;
        public String action = "propose";

        public ProposeCategoryRequest(String name) {
            this.name = name;
        }
    }

    class ListingsRequest {
        public String query, sort, id, action;
        public Integer major;
        public Integer owner_id;
        public int offset, limit;

        public ListingsRequest() {
            this.action = "list";
        }

        public ListingsRequest(String id) {
            this.id = id;
            this.action = "detail";
        }

        public ListingsRequest(String id, String action) {
            this.id = id;
            this.action = action;
        }
    }

    class UpdateProfileRequest {
        public String user_id, full_name, email, mobile, profile_pic_url, fcm_token, bio;
        @SerializedName("is_private")
        public Boolean isPrivate;
    }

    class AddListingRequest {
        public String owner_id, title, category, price, description, image_url, tags, location, free_slots;
        public String condition, original_price, action, listing_id;
        public Integer major_id;
        @SerializedName("auto_reply")
        public boolean autoReply;
        @SerializedName("hide_from_friends")
        public boolean hideFromFriends;
    }

    class AddListingResponse extends BaseResponse {
        public String id;
    }

    class FavoriteRequest {
        public String user_id, listing_id, action;
    }

    class MessageRequest {
        public String user_id, thread_id, listing_id, receiver_id, text, action;
    }

    class SendMessageResponse extends BaseResponse {
        @SerializedName("thread_id")
        public String threadId;
    }

    class NotificationRequest {
        public String user_id, action;
    }

    class TransactionRequest {
        public String user_id, listing_id, seller_id, transaction_id, status, amount, action;
    }

    class SellerRequest {
        public String seller_id, seller_name, action;
    }

    class ReportRequest {
        public String user_id, target_type, target_id, reason, details;
    }

    class FollowRequest {
        public String user_id;
        public Integer followed_id;
        public String action;
        public FollowRequest(String userId, int followedId, String action) {
            this.user_id = userId;
            this.followed_id = followedId;
            this.action = action;
        }
    }

    class FollowResponse extends BaseResponse {
        @SerializedName("follower_count")
        public int followerCount;
        @SerializedName("is_following")
        public boolean following;
    }

    class AlertRequest {
        public String user_id;
        public String listing_id;
        public String action;
        public AlertRequest(String userId, String action) {
            this.user_id = userId;
            this.action = action;
        }
        public AlertRequest(String userId, String listingId, String action) {
            this.user_id = userId;
            this.listing_id = listingId;
            this.action = action;
        }
    }

    class AlertsResponse extends BaseResponse {
        @SerializedName("listing_ids")
        public List<Integer> listingIds;
    }

    class BlockRequest {
        public String user_id, blocked_id, action;

        public BlockRequest(String blockedId, String action) {
            this.blocked_id = blockedId;
            this.action = action;
        }
    }

    class BlockResponse extends BaseResponse {
        @SerializedName("blocked_ids")
        public List<String> blockedIds;
    }

    class PulseRequest {
        public String action, tag, title, body;

        public PulseRequest() {
            this.action = "list";
        }

        public PulseRequest(String tag, String title, String body) {
            this.action = "post";
            this.tag = tag;
            this.title = title;
            this.body = body;
        }
    }

    class PulseAlert {
        public String id, title, body, tag;
        @SerializedName("is_global")
        public boolean global;
        @SerializedName("user_name")
        public String userName;
        @SerializedName("created_at")
        public long createdAt;
    }

    class PulseResponse extends BaseResponse {
        public List<PulseAlert> alerts;
        public List<PulseAlert> announcements;
    }

    class VerificationRequest {
        public String action;
        @SerializedName("verification_photo")
        public String verificationPhoto;

        public VerificationRequest(String photoUrl) {
            this.action = "submit";
            this.verificationPhoto = photoUrl;
        }
    }

    class VerificationStatusRequest {
        public String action;

        public VerificationStatusRequest() {
            this.action = "status";
        }
    }

    class VerificationStatusResponse extends BaseResponse {
        @SerializedName("verification_status")
        public String verificationStatus;
        @SerializedName("verification_photo")
        public String verificationPhoto;
    }

    class SecurityLogRequest {
        public String user_id, thread_id, listing_id, landmark, action;
        public Double latitude, longitude;
    }

    class ReviewRequest {
        public String user_id, seller, comment, listing_id;
        public int stars;
    }

    class ForgotPasswordRequest {
        public String identifier;

        public ForgotPasswordRequest(String identifier) {
            this.identifier = identifier;
        }
    }

    class ForgotPasswordResponse extends BaseResponse {
        @SerializedName("temporary_password")
        public String temporaryPassword;
    }

    class UpdatePasswordRequest {
        public String new_password;

        public UpdatePasswordRequest(String newPassword) {
            this.new_password = newPassword;
        }
    }

    class LoginResponse extends BaseResponse {
        public String token;
        public User user;
    }

    class User {
        public String id, name, studentId, email, mobile, role, bio;
        @SerializedName("profile_pic_url")
        public String profile_pic_url;
        @SerializedName("is_verified")
        public boolean verified;
        @SerializedName("is_banned")
        public boolean banned;
        public int active, sold;
        @SerializedName("is_private")
        public boolean isPrivate;
        @SerializedName("joined_at")
        public String joined_at;
        public float rating;
        public int reviews;
        @SerializedName("follower_count")
        public int followerCount;
        @SerializedName("is_following")
        public boolean following;
    }

    class CategoryResponse extends BaseResponse {
        public List<Category> categories;
    }

    class Category {
        public String name, icon_res;
    }

    class MajorsResponse extends BaseResponse {
        public List<Major> majors;
    }

    class Major {
        public int id;
        public String name, faculty;
    }

    class ListingsResponse extends BaseResponse {
        public List<Listing> listings;
        public boolean has_next;
    }

    class Listing {
        public String id, title, seller, price, distance, image_url, category, description, owner_id, free_slots, major_name, location;
        public String condition, original_price, tags;
        public float rating;
        @SerializedName("review_count")
        public int reviewCount;
        @SerializedName("thumb_url")
        public String thumbUrl;
        @SerializedName("is_available")
        public boolean available;
        @SerializedName("archived_at")
        public String archivedAt;
        @SerializedName("major_id")
        public Integer majorId;
        @SerializedName("posted_at_ms")
        public long postedAt;
        @SerializedName("is_verified")
        public boolean verified;
        @SerializedName("auto_reply")
        public boolean autoReply;
        @SerializedName("hide_from_friends")
        public boolean hideFromFriends;
        public int views;
    }

    class ThreadsResponse extends BaseResponse {
        public List<Thread> threads;
    }

    class Thread {
        public String id, listingId, name, last_message;
        public long lastMessageTime;
        public boolean unread;
    }

    class MessagesResponse extends BaseResponse {
        public List<Message> messages;
    }

    class Message {
        public String sender, text;
        public long time;
        public boolean mine;
    }

    class NotificationsResponse extends BaseResponse {
        public List<Notification> notifications;
    }

    class Notification {
        public String id, title, body;
        public long time;
        public boolean read;
    }

    class TransactionsResponse extends BaseResponse {
        public List<Transaction> transactions;
    }

    class Transaction {
        public String id, listingId, title, amount, status, location, seller;
        public long time;
        public boolean reviewed;
    }

    class SellerResponse extends BaseResponse {
        @SerializedName("seller")
        public User user;
        public List<Listing> listings;
        public List<SellerReview> reviews;
    }

    class SellerReview {
        public String reviewer_name, comment;
        public int stars;
    }

    class SellerMetricsResponse extends BaseResponse {
        @SerializedName("earnings")
        public double total_earnings;
        public int items_sold, active_listings;
        @SerializedName("rating")
        public float avg_rating;
        public float trust_score;
    }

    class GreenRequest {
        public String action;
        public int limit;
    }

    class GreenMetrics {
        public double co2, water, paper, energy;
        public int count, rank;
        public int tools_reused;
        public String tier;
    }

    class GreenBreakdown {
        public String category;
        public int items;
        public double co2, water, paper, energy;
    }

    class GreenMetricsResponse extends BaseResponse {
        public GreenMetrics metrics;
        public List<GreenBreakdown> breakdown;
    }

    class GreenEntry {
        public String user_id, name, tier;
        public double co2, water, paper, energy;
        public int count;
    }

    class GreenLeaderboardResponse extends BaseResponse {
        public List<GreenEntry> leaderboard;
        public GreenMetrics me;
    }

    class UploadResponse extends BaseResponse {
        public String url;
        @SerializedName("thumb_url")
        public String thumbUrl;
    }

    class AiSuggestResponse extends BaseResponse {
        public String title, price, description;
    }

    class BugReportRequest {
        public String description, device_model, os_version, app_version, screen, screenshot_url;

        public BugReportRequest(String description, String deviceModel, String osVersion,
                                String appVersion, String screen, String screenshotUrl) {
            this.description = description;
            this.device_model = deviceModel;
            this.os_version = osVersion;
            this.app_version = appVersion;
            this.screen = screen;
            this.screenshot_url = screenshotUrl;
        }
    }

    class ExportDataResponse extends BaseResponse {
        public JsonObject data;
    }
}
