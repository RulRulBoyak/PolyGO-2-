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
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    @Provides
    @Singleton
    public PolyGoApi providePolyGoApi(@ApplicationContext Context context) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

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

        Interceptor appCheckInterceptor = chain -> {
            Request request = chain.request();
            if (!"GET".equalsIgnoreCase(request.method())) {
                try {
                    Task<AppCheckToken> tokenTask = FirebaseAppCheck.getInstance().getAppCheckToken(false);
                    AppCheckToken token = Tasks.await(tokenTask, 5, TimeUnit.SECONDS);
                    if (token != null && token.getToken() != null && !token.getToken().isEmpty()) {
                        request = request.newBuilder()
                                .addHeader("X-Firebase-AppCheck", token.getToken())
                                .build();
                    }
                } catch (Exception ignored) {
                    // Token unavailable (e.g. provider not ready) — request proceeds without it.
                }
            }
            return chain.proceed(request);
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .addInterceptor(networkErrorInterceptor)
                .addInterceptor(appCheckInterceptor)
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
