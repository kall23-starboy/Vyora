package com.vyorasound.app.network

import com.vyorasound.app.data.dto.BrowseRequestDto
import com.vyorasound.app.data.dto.NextRequestDto
import com.vyorasound.app.data.dto.PlayerRequestDto
import com.vyorasound.app.data.dto.SearchRequestDto
import com.vyorasound.app.data.dto.SearchSuggestionsRequestDto
import com.vyorasound.app.data.dto.VisitorIdRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface InnerTubeApiService {

    @POST("youtubei/v1/browse?prettyPrint=false")
    suspend fun browse(
        @HeaderMap headers: Map<String, String>,
        @Body body: BrowseRequestDto,
        @Query("continuation") continuation: String? = null
    ): Response<ResponseBody>

    @POST("youtubei/v1/search?prettyPrint=false")
    suspend fun search(
        @HeaderMap headers: Map<String, String>,
        @Body body: SearchRequestDto,
        @Query("continuation") continuation: String? = null
    ): Response<ResponseBody>

    @POST("youtubei/v1/music/get_search_suggestions?prettyPrint=false")
    suspend fun getSearchSuggestions(
        @HeaderMap headers: Map<String, String>,
        @Body body: SearchSuggestionsRequestDto
    ): Response<ResponseBody>

    @POST("youtubei/v1/next?prettyPrint=false")
    suspend fun next(
        @HeaderMap headers: Map<String, String>,
        @Body body: NextRequestDto
    ): Response<ResponseBody>

    @POST("youtubei/v1/player?prettyPrint=false")
    suspend fun player(
        @HeaderMap headers: Map<String, String>,
        @Body body: PlayerRequestDto
    ): Response<ResponseBody>

    @POST
    suspend fun playerAtUrl(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: PlayerRequestDto
    ): Response<ResponseBody>

    @POST
    suspend fun visitorIdAtUrl(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: VisitorIdRequestDto
    ): Response<ResponseBody>
}
