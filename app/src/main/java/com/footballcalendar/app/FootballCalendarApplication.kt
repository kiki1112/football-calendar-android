package com.footballcalendar.app

import android.app.Application
import com.footballcalendar.app.calendar.SystemCalendarSyncManager
import com.footballcalendar.app.data.local.AppDatabase
import com.footballcalendar.app.data.preferences.UserPreferencesRepository
import com.footballcalendar.app.data.remote.DelegatingFixtureDataSource
import com.footballcalendar.app.data.remote.FeedApiService
import com.footballcalendar.app.data.remote.FootballDataApiFixtureDataSource
import com.footballcalendar.app.data.remote.GitHubPagesFixtureDataSource
import com.footballcalendar.app.data.remote.MockFixtureDataSource
import com.footballcalendar.app.data.repository.FixtureRepository
import com.footballcalendar.app.sync.NotificationHelper
import com.footballcalendar.app.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val database: AppDatabase = AppDatabase.build(application)
    val prefsRepository = UserPreferencesRepository(application)
    val notificationHelper = NotificationHelper(application)
    val systemCalendarSyncManager = SystemCalendarSyncManager(application)
    val syncScheduler = SyncScheduler(application)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.football-data.org/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val apiService: FeedApiService = retrofit.create(FeedApiService::class.java)

    private val fixtureDataSource = DelegatingFixtureDataSource(
        mockDataSource = MockFixtureDataSource(),
        githubPagesDataSource = GitHubPagesFixtureDataSource(apiService),
        footballDataApiDataSource = FootballDataApiFixtureDataSource(apiService),
    )

    val fixtureRepository = FixtureRepository(
        matchDao = database.matchDao(),
        prefsRepo = prefsRepository,
        dataSource = fixtureDataSource,
    )
}

class FootballCalendarApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()

        appScope.launch {
            val prefs = container.prefsRepository.currentPreferences()
            container.syncScheduler.configurePeriodicSync(
                enabled = prefs.autoSyncEnabled,
                intervalHours = prefs.syncIntervalHours,
                wifiOnly = prefs.wifiOnlySync,
            )
            container.fixtureRepository.ensureSeededIfEmpty()
        }
    }
}
