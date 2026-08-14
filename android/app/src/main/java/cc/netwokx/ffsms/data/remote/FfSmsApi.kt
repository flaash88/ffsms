package cc.netwokx.ffsms.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit

interface FfSmsApi {

    @POST("api/v1/campaigns")
    suspend fun uploadCampaign(@Body body: CampaignUploadDto): CampaignAckDto

    @GET("api/v1/stats")
    suspend fun stats(
        @Query("device_id") deviceId: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): StatsDto

    @GET("api/v1/health")
    suspend fun health(): HealthDto

    @GET("api/v1/update/manifest")
    suspend fun updateManifest(): UpdateManifestDto

    /**
     * Streaming, damit das APK nicht komplett in den Arbeitsspeicher geladen
     * wird - es ist rund 20 MB gross.
     */
    @Streaming
    @GET("api/v1/update/apk")
    suspend fun updateApk(): ResponseBody
}

object ApiClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    /**
     * Baut einen Client fuer die aktuell eingestellte Backend-URL.
     *
     * Es wird bewusst kein Client gecacht: URL und API-Key koennen sich in den
     * Einstellungen jederzeit aendern, und ein veralteter Client wuerde stumm
     * gegen das falsche Ziel senden.
     */
    fun create(baseUrl: String, apiKey: String): FfSmsApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(apiKeyInterceptor(apiKey))
            .build()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FfSmsApi::class.java)
    }

    private fun apiKeyInterceptor(apiKey: String) = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("X-API-Key", apiKey)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }
}
