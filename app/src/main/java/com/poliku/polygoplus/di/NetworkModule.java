package com.poliku.polygoplus.di;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.poliku.polygoplus.BuildConfig;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.network.AuthSessionHandler;
import com.poliku.polygoplus.network.NetworkErrorHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    private static final long APP_CHECK_TTL_MS = 1000L * 60 * 50;
    private static volatile String cachedAppCheckToken;
    private static volatile long cachedAppCheckTokenAt;
    private static final Object APP_CHECK_LOCK = new Object();

    @Provides
    @Singleton
    public PolyGoApi providePolyGoApi(@ApplicationContext Context context) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
        // Never log the Bearer token, even for debugging.
        logging.redactHeader("Authorization");

        Interceptor authInterceptor = chain -> {
            String token = AppDataStore.userToken(context);
            Request request = chain.request();
            if (token != null && !token.isEmpty()) {
                request = request.newBuilder()
                        .addHeader("Authorization", "Bearer " + token)
                        .build();
            }
            return chain.proceed(request);
        };

        Interceptor networkErrorInterceptor = chain -> {
            try {
                return chain.proceed(chain.request());
            } catch (IOException error) {
                NetworkErrorHandler.onNetworkError();
                throw error;
            }
        };

        // App Check tokens are cached by the SDK (~1h). Waiting on the token
        // task is therefore rare (only when a fresh token is being minted), not
        // a per-request cost. We still keep the last-known token as a fallback
        // so a transient provider hiccup never drops the header (which would
        // fail the request server-side when enforcement is on).
        Interceptor appCheckInterceptor = chain -> {
            Request request = chain.request();
            if (!"GET".equalsIgnoreCase(request.method())) {
                String token = cachedAppCheckToken;
                if (token == null || System.currentTimeMillis() - cachedAppCheckTokenAt > APP_CHECK_TTL_MS) {
                    synchronized (APP_CHECK_LOCK) {
                        token = cachedAppCheckToken;
                        if (token == null || System.currentTimeMillis() - cachedAppCheckTokenAt > APP_CHECK_TTL_MS) {
                            String fetched = null;
                            try {
                                Task<AppCheckToken> tokenTask = FirebaseAppCheck.getInstance().getAppCheckToken(false);
                                AppCheckToken minted = Tasks.await(tokenTask, 3, TimeUnit.SECONDS);
                                fetched = minted != null ? minted.getToken() : null;
                            } catch (Exception ignored) {
                                // Provider not ready / timed out — fall back below.
                            }
                            if (fetched != null && !fetched.isEmpty()) {
                                cachedAppCheckToken = fetched;
                                cachedAppCheckTokenAt = System.currentTimeMillis();
                                token = fetched;
                            } else {
                                token = cachedAppCheckToken;
                            }
                        }
                    }
                }
                if (token != null && !token.isEmpty()) {
                    request = request.newBuilder()
                            .addHeader("X-Firebase-AppCheck", token)
                            .build();
                }
            }
            return chain.proceed(request);
        };

        Interceptor authResponseInterceptor = chain -> {
            Response response = chain.proceed(chain.request());
            if (response.code() == 401) {
                AuthSessionHandler.onSessionExpired();
                return response;
            }
            // Fallback for the pre-401 backend: JWT failures arrive as HTTP 200
            // with a JSON body whose message starts with "Unauthorized". peekBody
            // is non-consuming, so the Retrofit callback can still parse it.
            if (response.code() == 200 && response.body() != null) {
                try {
                    String peek = response.peekBody(512).string();
                    if (peek.contains("Unauthorized")) {
                        AuthSessionHandler.onSessionExpired();
                    }
                } catch (Exception ignored) {
                    // Peek failed – ignore, the callback will surface the real error.
                }
            }
            return response;
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .addInterceptor(networkErrorInterceptor)
                .addInterceptor(appCheckInterceptor)
                .addInterceptor(authResponseInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.DEBUG ? "http://10.0.2.2/polygo-api/" : "https://polygo.pks.edu.my/polygo-api/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(PolyGoApi.class);
    }
}
