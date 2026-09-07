package com.example.signtalk.data.remote

import com.example.signtalk.data.remote.dto.DictionaryListResponseDto
import com.example.signtalk.data.remote.dto.ModelVersionDto
import com.example.signtalk.data.remote.dto.RecognitionLogRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface for backend/sign-talk-api (see its README.md for the
 * full endpoint table). Only the endpoints the app currently calls are
 * declared -- dictionary reads, the active model version, and best-effort
 * recognition logging. All routes live under `/api` (see the backend's
 * app.js: `app.use("/api", routes)`).
 */
interface SignTalkApiService {

    @GET("api/dictionary")
    suspend fun getDictionary(): DictionaryListResponseDto

    @GET("api/model/version")
    suspend fun getActiveModelVersion(): ModelVersionDto

    // Response<ResponseBody> (not Response<Unit>) deliberately -- the backend
    // returns the created log document, not an empty body, and Retrofit's
    // Gson converter can trip over deserializing a non-empty JSON body into
    // Unit. ResponseBody is handled natively by Retrofit without ever
    // invoking Gson, which is all this fire-and-forget call needs.
    @POST("api/logs/recognition")
    suspend fun logRecognition(@Body body: RecognitionLogRequestDto): Response<ResponseBody>
}
