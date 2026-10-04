package app.fieldwatch.ui

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Keep editable models through recreation without committing an unfinished edit. */
inline fun <reified T : Any> jsonDraftSaver(): Saver<T, String> = Saver(
    save = { Json.encodeToString(it) },
    restore = { Json.decodeFromString<T>(it) },
)
