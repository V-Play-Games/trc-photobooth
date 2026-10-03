package com.trc.photobooth.data

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object CloudinaryUploader {
    private const val TAG = "CloudinaryUploader"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Uploads a bitmap (such as the 2x2 collage) directly to Cloudinary.
     * Uses [CloudinaryConfig.fromBuildConfig()] credentials.
     *
     * @return Result containing the HTTPS secure_url of the uploaded image.
     */
    suspend fun uploadBitmap(
        bitmap: Bitmap,
        fileName: String,
        folder: String = "trc-photobooth/sessions",
        config: CloudinaryConfig = CloudinaryConfig.fromBuildConfig(),
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.cloudName.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Cloudinary Cloud Name is not configured. Set CLOUDINARY_CLOUD_NAME.")
            )
        }

        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            val jpegBytes = stream.toByteArray()

            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    fileName,
                    jpegBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("folder", folder)
                .addFormDataPart("public_id", fileName.substringBeforeLast("."))

            if (config.uploadPreset.isNotBlank()) {
                multipartBuilder.addFormDataPart("upload_preset", config.uploadPreset)
            }
            if (config.apiKey.isNotBlank()) {
                multipartBuilder.addFormDataPart("api_key", config.apiKey)
            }

            val requestBody = multipartBuilder.build()
            val endpoint = "https://api.cloudinary.com/v1_1/${config.cloudName}/image/upload"

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val obj = json.parseToJsonElement(bodyStr).jsonObject
                        obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    } catch (e: Exception) {
                        null
                    } ?: "Cloudinary upload HTTP error: ${response.code} $bodyStr"
                    Log.e(TAG, "Upload failed: $errMsg")
                    return@withContext Result.failure(Exception(errMsg))
                }

                val obj = json.parseToJsonElement(bodyStr).jsonObject
                val secureUrl = obj["secure_url"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("Missing secure_url in Cloudinary response"))

                Log.i(TAG, "Uploaded image successfully to Cloudinary: $secureUrl")
                Result.success(secureUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
