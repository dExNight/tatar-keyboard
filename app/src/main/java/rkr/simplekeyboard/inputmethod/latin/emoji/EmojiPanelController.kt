/*
 * Copyright (C) 2026 Tatar Keyboard contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Handler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Emoji-panel UI seam, modelled on `StripSurface`. All methods run on the UI thread. */
interface EmojiSurface {
    /** Bind [snapshot] to the panel view and make it the active surface. */
    fun showPanel(snapshot: EmojiSetSnapshot)
}

/**
 * Marshals a [Runnable] onto the UI thread. Production wraps a [Handler]; JVM tests inject a
 * synchronous or deferrable poster so no real Handler is needed.
 */
fun interface EmojiUiPoster {
    fun post(runnable: Runnable)
}

/**
 * Builds the filtered emoji snapshot off the UI thread — reading the asset, probing glyphs and
 * composing categories. Production reads `assets/emoji/emoji_set_v1.txt` through [EmojiSet.build];
 * JVM tests inject a fake and count how many times [build] runs, which is what makes "one
 * preparation per process" observable without Android.
 */
fun interface EmojiSnapshotSource {
    fun build(): EmojiSetSnapshot
}

/** Where a process's single snapshot preparation stands. */
enum class EmojiPanelPreparation { NOT_PREPARED, PREPARING, READY, UNAVAILABLE }

/**
 * Serialized owner of the emoji panel's snapshot, modelled on `SuggestionsController`.
 *
 * Threading: every public method must be called on the UI thread. The single-thread background
 * executor runs only the (blocking) snapshot preparation. Its result is re-marshaled onto
 * [uiPoster] before any controller state is touched, so every mutation happens on one serialized
 * owner.
 *
 * Preparation runs exactly once per process: it is started on the FIRST emoji key press, never in
 * `onCreate` and never on the UI thread, and it is never restarted — not after [EmojiPanelPreparation.READY]
 * (the snapshot is cached), and not after [EmojiPanelPreparation.UNAVAILABLE] (the panel then never
 * shows in this process and the emoji key is a no-op). The glyph-probe result lives only in memory;
 * it is never written to shared preferences or a file, because a stale cache would reintroduce the
 * very "tofu" the probe exists to prevent.
 *
 * Until the snapshot is published, a press arms exactly one latest-only deferred show. It is
 * dropped by any other press (which simply re-arms it), by an editor-session change, by
 * `onFinishInputView`, and by input-view recreation, so a panel never pops up for an editor the
 * user has already left.
 */
class EmojiPanelController internal constructor(
    private val surface: EmojiSurface,
    private val uiPoster: EmojiUiPoster,
    private val executorFactory: () -> ExecutorService?,
    private val snapshotSourceFactory: () -> EmojiSnapshotSource?,
) {
    /** Production entry point. */
    constructor(context: Context, surface: EmojiSurface, uiHandler: Handler) : this(
        surface,
        EmojiUiPoster { runnable -> uiHandler.post(runnable) },
        { Executors.newSingleThreadExecutor() },
        { AssetSnapshotSource(context.applicationContext) },
    )

    private var executor: ExecutorService? = null

    // Written on the UI thread after a successful preparation and read on the UI thread by showNow.
    @Volatile
    private var snapshot: EmojiSetSnapshot? = null

    private var preparation = EmojiPanelPreparation.NOT_PREPARED

    // The single latest-only deferred show. Never more than one outstanding.
    private var pendingShow = false

    private var destroyed = false

    fun preparationState(): EmojiPanelPreparation = preparation

    /**
     * The emoji key was pressed.
     *
     * @return true when the caller should show the panel or has already handed off a request that
     *   will show it once ready; false when the panel will not show in this process (the key is a
     *   no-op that only gives haptic feedback).
     */
    fun onEmojiKeyPressed(): Boolean {
        if (destroyed) return false
        when (preparation) {
            EmojiPanelPreparation.READY -> {
                showNow()
                return true
            }
            EmojiPanelPreparation.UNAVAILABLE -> return false
            EmojiPanelPreparation.NOT_PREPARED -> {
                preparation = EmojiPanelPreparation.PREPARING
                pendingShow = true
                startPreparation()
                return preparation != EmojiPanelPreparation.UNAVAILABLE
            }
            EmojiPanelPreparation.PREPARING -> {
                // Latest-only: a second press does not start a second preparation.
                pendingShow = true
                return true
            }
        }
    }

    /** Drops the single deferred show without letting it fire later. */
    private fun cancelPendingShow() {
        pendingShow = false
    }

    /** A new editor session began; a deferred show for the previous one must not fire. */
    fun onEditorSessionChanged() = cancelPendingShow()

    fun onFinishInputView() = cancelPendingShow()

    /** The input view was recreated (rotation, theme or height change). */
    fun onInputViewRecreated() = cancelPendingShow()

    fun onDestroy() {
        destroyed = true
        pendingShow = false
        executor?.shutdownNow()
        executor = null
    }

    private fun startPreparation() {
        val backgroundExecutor = backgroundExecutor()
        if (backgroundExecutor == null) {
            finishUnavailable()
            return
        }
        val source = try {
            snapshotSourceFactory()
        } catch (_: Throwable) {
            null
        }
        if (source == null) {
            finishUnavailable()
            return
        }
        try {
            backgroundExecutor.execute {
                val built = try {
                    source.build()
                } catch (_: Throwable) {
                    EmojiSetSnapshot.EMPTY
                }
                uiPoster.post { onPrepared(built) }
            }
        } catch (_: Throwable) {
            finishUnavailable()
        }
    }

    private fun onPrepared(built: EmojiSetSnapshot) {
        if (destroyed) return
        if (built.isEmpty) {
            finishUnavailable()
            return
        }
        snapshot = built
        preparation = EmojiPanelPreparation.READY
        if (pendingShow) {
            pendingShow = false
            surface.showPanel(built)
        }
    }

    private fun finishUnavailable() {
        preparation = EmojiPanelPreparation.UNAVAILABLE
        pendingShow = false
    }

    private fun showNow() {
        val ready = snapshot ?: return
        surface.showPanel(ready)
    }

    private fun backgroundExecutor(): ExecutorService? {
        if (destroyed) return null
        executor?.let { return it }
        val created = try {
            executorFactory()
        } catch (_: Throwable) {
            null
        } ?: return null
        executor = created
        return created
    }
}

/**
 * Production [EmojiSnapshotSource]: reads the packed asset and filters it through
 * [PaintGlyphProbe]. Constructed cheaply on the UI thread (it only keeps the application context);
 * the AssetManager and `Paint.hasGlyph` are touched only inside [build], which runs on the
 * controller's background executor, never on the UI or cold-start path. The result is returned to
 * the caller and never written to any persistent store.
 */
private class AssetSnapshotSource(private val context: Context) : EmojiSnapshotSource {
    override fun build(): EmojiSetSnapshot =
        try {
            context.assets.open(ASSET_PATH).use { input ->
                EmojiSet.build(input, PaintGlyphProbe(Paint()))
            }
        } catch (_: Throwable) {
            EmojiSetSnapshot.EMPTY
        }

    private companion object {
        const val ASSET_PATH = "emoji/emoji_set_v1.txt"
    }
}
