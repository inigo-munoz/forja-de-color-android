package app.forjadecolor.domain

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration shared by advice answers and backups. It reads and writes the same
 * shapes as the PWA: absent optional fields stay absent and `kind` tells recipes from consultations.
 */
val ForjaJson: Json = Json {
    classDiscriminator = "kind"
    explicitNulls = false
    encodeDefaults = true
    ignoreUnknownKeys = true
}
