package com.footballcalendar.app

import android.app.Application
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
import com.footballcalendar.app.ui.theme.TeamColorRegistry
import com.footballcalendar.app.util.ZhEntityDictionary
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
        .readTimeout(38, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "FootballCalendarApp/2.6 (https://github.com/kiki1112/football-calendar-android)")
                .build()
            chain.proceed(req)
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()

    val imageLoader: ImageLoader = ImageLoader.Builder(application)
        .okHttpClient(okHttpClient)
        .memoryCache {
            MemoryCache.Builder(application)
                .maxSizePercent(0.18)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(application.cacheDir.resolve("image_cache"))
                .maxSizeBytes(64L * 1024L * 1024L)
                .build()
        }
        .components {
            add(SvgDecoder.Factory())
        }
        .allowHardware(true)
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
        appContext = application,
        matchDao = database.matchDao(),
        standingDao = database.standingDao(),
        teamDao = database.teamDao(),
        matchDetailDao = database.matchDetailDao(),
        playerDao = database.playerDao(),
        prefsRepo = prefsRepository,
        dataSource = fixtureDataSource,
        apiService = apiService,
        json = json,
    )
}

class FootballCalendarApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TeamColorRegistry.ensureLoaded(this)
        ZhEntityDictionary.ensureLoaded(this)
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
