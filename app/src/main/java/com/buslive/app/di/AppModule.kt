package com.buslive.app.di

import android.content.Context
import androidx.room.Room
import com.buslive.app.BuildConfig
import com.buslive.app.data.api.NominatimApi
import com.buslive.app.data.api.StrideApi
import com.buslive.app.data.db.AppDatabase
import com.buslive.app.data.db.FavoriteDao
import com.buslive.app.data.db.RecentDao
import com.buslive.app.data.db.RouteDao
import com.buslive.app.data.db.StopDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    const val USER_AGENT = "BusLive-Android/${BuildConfig.VERSION_NAME} (+https://github.com/markovo1234/buslive)"

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    @Provides
    @Singleton
    fun strideApi(client: OkHttpClient, json: Json): StrideApi = Retrofit.Builder()
        .baseUrl(StrideApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(StrideApi::class.java)

    @Provides
    @Singleton
    fun nominatimApi(client: OkHttpClient, json: Json): NominatimApi = Retrofit.Builder()
        .baseUrl(NominatimApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NominatimApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "buslive.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun stopDao(db: AppDatabase): StopDao = db.stopDao()
    @Provides fun routeDao(db: AppDatabase): RouteDao = db.routeDao()
    @Provides fun favoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun recentDao(db: AppDatabase): RecentDao = db.recentDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
