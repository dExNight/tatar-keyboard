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

import android.graphics.Paint

/**
 * Answers whether the system font can actually draw a given emoji sequence.
 *
 * The keyboard ships no emoji font of its own, so an entry the running device cannot render must
 * never reach a cell — otherwise the user sees a "tofu" box. The set is filtered through this probe
 * once, while the snapshot is built (see [EmojiSet.build]), never during drawing, so the cost is
 * paid a single time per process.
 *
 * The interface itself is pure so it can be exercised on the JVM with a fake; the production
 * implementation ([PaintGlyphProbe]) is the only place that touches Android.
 */
fun interface GlyphProbe {
    /** True when the system font has a glyph for the whole [sequence]. */
    fun hasGlyph(sequence: String): Boolean
}

/**
 * Production [GlyphProbe] backed by [Paint.hasGlyph], which reports whether a single glyph exists
 * for the complete string. `hasGlyph` is available from API 23 and the app's `minSdk` is 24, so no
 * version guard is needed.
 *
 * A [Paint] carries no text state between calls, so one instance is reused for the whole filtering
 * pass. This class is constructed only on the background snapshot-preparation path, never on the UI
 * or cold-start path.
 */
class PaintGlyphProbe(private val paint: Paint = Paint()) : GlyphProbe {
    override fun hasGlyph(sequence: String): Boolean = paint.hasGlyph(sequence)
}
