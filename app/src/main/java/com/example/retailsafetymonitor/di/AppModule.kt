package com.example.retailsafetymonitor.di

import android.content.Context
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.retailsafetymonitor.BuildConfig
import com.example.retailsafetymonitor.data.api.GeminiReportApi
import com.example.retailsafetymonitor.data.api.GeminiReportApiImpl
import com.example.retailsafetymonitor.data.api.GeminiService
import com.example.retailsafetymonitor.data.local.HazardDao
import com.example.retailsafetymonitor.data.local.ReportDao
import com.example.retailsafetymonitor.data.local.SafetyDatabase
import com.example.retailsafetymonitor.data.repository.HazardRepositoryImpl
import com.example.retailsafetymonitor.data.repository.ReportRepositoryImpl
import com.example.retailsafetymonitor.data.worker.HazardEscalationWorker
import com.example.retailsafetymonitor.data.worker.WeeklyReportWorker
import com.example.retailsafetymonitor.domain.repository.HazardRepository
import com.example.retailsafetymonitor.domain.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that binds domain repository interfaces to their Room-backed implementations.
 *
 * Uses `@Binds` (abstract) rather than `@Provides` (concrete) to avoid allocating an
 * additional lambda wrapping the constructor — Dagger generates the binding at compile time.
 *
 * Installed in [SingletonComponent] so the bindings are available app-wide.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds [HazardRepositoryImpl] as the app-wide singleton for [HazardRepository].
     *
     * @param impl Hilt-constructed instance of [HazardRepositoryImpl].
     * @return The [HazardRepository] abstraction used by ViewModels and use cases.
     */
    @Binds
    @Singleton
    abstract fun bindHazardRepository(impl: HazardRepositoryImpl): HazardRepository

    /**
     * Binds [ReportRepositoryImpl] as the app-wide singleton for [ReportRepository].
     *
     * @param impl Hilt-constructed instance of [ReportRepositoryImpl].
     * @return The [ReportRepository] abstraction used by ViewModels and use cases.
     */
    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    /**
     * Binds [GeminiReportApiImpl] as the singleton for [GeminiReportApi].
     *
     * @param impl Hilt-constructed instance of [GeminiReportApiImpl].
     * @return The [GeminiReportApi] abstraction used by [GenerateSafetyReportUseCase].
     */
    @Binds
    @Singleton
    abstract fun bindGeminiReportApi(impl: GeminiReportApiImpl): GeminiReportApi
}

/**
 * Hilt module that provides concrete singleton infrastructure instances:
 * Room database, DAOs, OkHttp client, Retrofit service, and WorkManager.
 *
 * Installed in [SingletonComponent]. All [Provides] functions here are called at most
 * once per application process lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the Room [SafetyDatabase] singleton.
     *
     * Schema migrations are not yet configured — a destructive fallback would be required
     * if the schema changes in a future release while retaining user data.
     *
     * @param context Application context used to locate the database file.
     * @return Singleton [SafetyDatabase] instance.
     */
    @Provides
    @Singleton
    fun provideSafetyDatabase(@ApplicationContext context: Context): SafetyDatabase =
        Room.databaseBuilder(
            context,
            SafetyDatabase::class.java,
            SafetyDatabase.DATABASE_NAME
        ).build()

    /**
     * Provides the [HazardDao] from the singleton [SafetyDatabase].
     *
     * @param db The singleton [SafetyDatabase].
     * @return [HazardDao] for hazard CRUD operations.
     */
    @Provides
    fun provideHazardDao(db: SafetyDatabase): HazardDao = db.hazardDao()

    /**
     * Provides the [ReportDao] from the singleton [SafetyDatabase].
     *
     * @param db The singleton [SafetyDatabase].
     * @return [ReportDao] for report insert and query operations.
     */
    @Provides
    fun provideReportDao(db: SafetyDatabase): ReportDao = db.reportDao()

    /**
     * Provides a singleton [OkHttpClient] with logging and timeout configuration.
     *
     * Logging level is [HttpLoggingInterceptor.Level.BODY] in debug builds for API inspection,
     * and [HttpLoggingInterceptor.Level.NONE] in release to avoid logging sensitive data.
     *
     * @return Configured [OkHttpClient] shared by all Retrofit instances.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.IS_DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides a Retrofit-backed [GeminiService] pointed at the Gemini API base URL.
     *
     * Uses [GsonConverterFactory] for JSON serialization of [GeminiRequest]/[GeminiResponse].
     * The API key is injected per-call in [GeminiReportApiImpl], not as a header here,
     * so this Retrofit instance can be reused for other endpoints if needed.
     *
     * @param okHttpClient The shared [OkHttpClient] with logging and timeouts.
     * @return Singleton [GeminiService] Retrofit interface.
     */
    @Provides
    @Singleton
    fun provideGeminiService(okHttpClient: OkHttpClient): GeminiService =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiService::class.java)

    /**
     * Provides the [WorkManager] singleton for the application context.
     *
     * WorkManager is auto-initialised disabled in the manifest; this method provides
     * the instance after [RetailSafetyMonitorApp] registers the [HiltWorkerFactory].
     *
     * @param context Application context.
     * @return Singleton [WorkManager] instance.
     */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
