package com.shadatrahman.bikemode.media

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.core.content.getSystemService

/**
 * [MediaPauser] built on [AudioManager.dispatchMediaKeyEvent].
 *
 * Not the broadcast route that the old answers on the web still recommend: sending an
 * ACTION_MEDIA_BUTTON broadcast has been a no-op for ordinary apps since Android 5.0, because
 * MediaSession only honours media-button events the system itself dispatched. Going through
 * AudioManager is the supported equivalent, and it needs no permission.
 *
 * What it cannot do is aim. Android routes the key to whichever app holds the active media
 * session, with no way to scope it to one audio output — so this pauses the rider's podcast and
 * anything else playing alike. Hence the preference that guards it.
 */
class MediaPauseController(context: Context) : MediaPauser {

    private val appContext = context.applicationContext

    override fun pause() {
        val audio = appContext.getSystemService<AudioManager>() ?: return
        // Without this, ending a ride in silence would still poke whatever played last.
        if (!audio.isMusicActive) return
        // Both halves: some players read a lone ACTION_DOWN as a key being held rather than tapped.
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            runCatching {
                audio.dispatchMediaKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PAUSE))
            }
        }
    }
}
