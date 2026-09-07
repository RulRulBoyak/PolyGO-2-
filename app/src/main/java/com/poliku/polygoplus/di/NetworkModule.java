package com.poliku.polygoplus.di;

import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.network.NetworkApi;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public final class NetworkModule {

    @Provides
    @Singleton
    public PolyGoApi providePolyGoApi() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        return new Retrofit.Builder()
                .baseUrl("http://10.0.2.2/polygo-api/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
                .create(PolyGoApi.class);
    }

    @Provides
    @Singleton
    public NetworkApi provideNetworkApi() {
        return null; 
    }
}
