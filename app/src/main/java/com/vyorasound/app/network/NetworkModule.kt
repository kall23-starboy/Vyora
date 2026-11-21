package com.vyorasound.app.network

import com.vyorasound.app.player.PlaybackDiagnostics
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val BASE_URL = "https://music.youtube.com/"

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val apiOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            PlaybackDiagnostics.d("InnerTubeHTTP | $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val streamOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val range = request.header("Range") ?: "-"
                PlaybackDiagnostics.d(
                    "StreamNetIO | code=${response.code} | range=$range | " +
                        "mime=${response.header("Content-Type") ?: "-"} | " +
                        "len=${response.header("Content-Length") ?: "-"} | " +
                        "url=${PlaybackDiagnostics.redactStreamUrl(request.url.toString())}"
                )
                response
            }
            .build()
    }

    val innerTubeApiService: InnerTubeApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(apiOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(InnerTubeApiService::class.java)
    }
}
