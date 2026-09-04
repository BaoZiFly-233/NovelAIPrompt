package com.novelstudio.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.novelstudio.core.database.AppDatabase
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.GenerationTaskDao
import com.novelstudio.core.database.buildDatabase
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.data.SwipeImageRepository
import com.novelstudio.core.data.CompareSelectionStore
import com.novelstudio.core.data.InMemoryCompareSelectionStore
import com.novelstudio.core.data.GenerationRepository
import com.novelstudio.core.data.GenerationQueueController
import com.novelstudio.core.data.GenerationQueue
import com.novelstudio.core.data.ImageRepositoryImpl
import com.novelstudio.core.data.GenerationRepositoryImpl
import com.novelstudio.core.data.ArtistStringRepository
import com.novelstudio.core.data.PromptAssetRepository
import com.novelstudio.core.data.TagRepository
import com.novelstudio.core.data.WorkbenchDraftRepository
import com.novelstudio.core.data.TagSuggestionRepository
import com.novelstudio.core.data.RoomArtistStringRepository
import com.novelstudio.core.data.RoomPromptAssetRepository
import com.novelstudio.core.data.RoomTagRepository
import com.novelstudio.core.data.RoomWorkbenchDraftRepository
import com.novelstudio.core.data.CachedTagSuggestionRepository
import com.novelstudio.core.data.ImageToolRepository
import com.novelstudio.core.data.ImageToolRepositoryImpl
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.network.NovelAIApiServiceImpl
import com.novelstudio.core.network.platformHttpEngine
import com.novelstudio.core.storage.ImageFileStorage
import com.novelstudio.core.storage.imageFileStorage
import com.novelstudio.feature.compare.CompareViewModel
import com.novelstudio.feature.gallery.GalleryViewModel
import com.novelstudio.feature.swipe.SwipeViewModel
import com.novelstudio.feature.workbench.WorkbenchViewModel
import com.novelstudio.feature.inpaint.ImageToolsViewModel
import com.novelstudio.AssetLibraryViewModel
import com.novelstudio.settingsFilePath
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Token 与偏好设置存储抽象（DataStore Preferences 多平台实现） */
interface SettingsStore {
    suspend fun readToken(): String?
    suspend fun writeToken(value: String)
}

class DataStoreSettingsStore(dataStoreFilePath: String) : SettingsStore {

    private val tokenKey = stringPreferencesKey("nai_api_token")

    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { dataStoreFilePath.toPath() },
        )

    override suspend fun readToken(): String? =
        dataStore.data.first()[tokenKey]

    override suspend fun writeToken(value: String) {
        dataStore.edit { prefs -> prefs[tokenKey] = value }
    }
}

private fun createHttpClient(): HttpClient = HttpClient(platformHttpEngine()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
}

/**
 * 应用依赖图谱：
 * 平台上下文由各端入口传入（Android=applicationContext，Desktop=null）。
 */
fun appModule(platformContext: Any?): Module = module {
    single<SettingsStore> { DataStoreSettingsStore(settingsFilePath(platformContext)) }

    single<NovelAIApiService> {
        NovelAIApiServiceImpl(
            client = createHttpClient(),
            tokenProvider = { get<SettingsStore>().readToken() },
        )
    }

    single<AppDatabase> { buildDatabase(platformContext) }
    single<ImageDao> { get<AppDatabase>().imageDao() }
    single<GenerationTaskDao> { get<AppDatabase>().generationTaskDao() }
    single { get<AppDatabase>().artistStringDao() }
    single { get<AppDatabase>().promptAssetDao() }
    single { get<AppDatabase>().tagDao() }
    single { get<AppDatabase>().imageTagDao() }
    single { get<AppDatabase>().workbenchDraftDao() }
    single { get<AppDatabase>().tagSuggestionCacheDao() }

    single<ImageFileStorage> { imageFileStorage(platformContext) }
    single { ImageRepositoryImpl(get(), get()) }
    single<ImageRepository> { get<ImageRepositoryImpl>() }
    single<SwipeImageRepository> { get<ImageRepositoryImpl>() }
    single<ArtistStringRepository> { RoomArtistStringRepository(get()) }
    single<PromptAssetRepository> { RoomPromptAssetRepository(get()) }
    single<TagRepository> { RoomTagRepository(get(), get()) }
    single<WorkbenchDraftRepository> { RoomWorkbenchDraftRepository(get()) }
    single<TagSuggestionRepository> { CachedTagSuggestionRepository(get(), get()) }
    single<CompareSelectionStore> { InMemoryCompareSelectionStore() }
    single<GenerationRepository> { GenerationRepositoryImpl(get(), get(), get()) }
    single<ImageToolRepository> { ImageToolRepositoryImpl(get(), get(), get()) }
    single<GenerationQueue> {
        GenerationQueueController(
            repository = get(),
            dao = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    viewModelOf(::WorkbenchViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::CompareViewModel)
    viewModelOf(::SwipeViewModel)
    viewModelOf(::AssetLibraryViewModel)
    viewModelOf(::ImageToolsViewModel)
}
