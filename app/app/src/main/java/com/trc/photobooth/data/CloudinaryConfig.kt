package com.trc.photobooth.data

import com.trc.photobooth.BuildConfig

/**
 * Cloudinary configuration model.
 *
 * Credentials are supplied at build time via environment variables or Gradle project properties:
 * - CLOUDINARY_CLOUD_NAME
 * - CLOUDINARY_API_KEY
 * - CLOUDINARY_API_SECRET
 * - CLOUDINARY_UPLOAD_PRESET
 */
data class CloudinaryConfig(
    val cloudName: String,
    val apiKey: String,
    val apiSecret: String,
    val uploadPreset: String,
) {
    /**
     * True if minimal Cloudinary credentials (either unsigned upload preset or API key+secret) are present.
     */
    val isConfigured: Boolean
        get() = cloudName.isNotBlank() && (uploadPreset.isNotBlank() || (apiKey.isNotBlank() && apiSecret.isNotBlank()))

    companion object {
        fun fromBuildConfig(): CloudinaryConfig {
            return CloudinaryConfig(
                cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                apiKey = BuildConfig.CLOUDINARY_API_KEY,
                apiSecret = BuildConfig.CLOUDINARY_API_SECRET,
                uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
            )
        }

        val EMPTY = CloudinaryConfig(
            cloudName = "",
            apiKey = "",
            apiSecret = "",
            uploadPreset = "",
        )
    }
}
