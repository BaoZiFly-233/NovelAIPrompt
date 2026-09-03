package com.novelstudio.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.novelstudio.core.database.AppDatabase
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.databaseBuilder
import com.novelstudio.core.model.InMemoryPromptDraftStore
import com.novelstudio.core.model.PromptDraftStore
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.network.NovelAIApiServiceImpl
import com.novelstudio.core.network.platformHttpEngine
import com.novelstudio.feature.compare.CompareViewModel
import com.novelstudio.feature.gallery.GalleryViewModel
import com.novelstudio.feature.inpaint.InpaintViewModel
import com.novelstudio.feature.swipe.SwipeViewModel
import com.novelstudio.feature.workbench.WorkbenchViewModel
import com.novelstudio.settingsDirPath
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
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
 * 应用依赖图谱（Task 0.3）：
 * 平台上下文由各端入口传入（Android=applicationContext，Desktop=null）。
 */
fun appModule(platformContext: Any?): Module = module {
    single<PromptDraftStore> { InMemoryPromptDraftStore() }

    single<SettingsStore> { DataStoreSettingsStore(settingsDirPath(platformContext)) }

    single {
        NovelAIApiServiceImpl(
            client = createHttpClient(),
            tokenProvider = { get<SettingsStore>().readToken() },
        )
    }

    single<AppDatabase> { databaseBuilder(platformContext).build() }
    single<ImageDao> { get<AppDatabase>().imageDao() }

    viewModelOf(::WorkbenchViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::CompareViewModel)
    viewModelOf(::SwipeViewModel)
    viewModelOf(::InpaintViewModel)
}
