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

/**
 * The two pieces of [EmojiSearchView] geometry that can be got wrong without anything crashing, so
 * they live here, Android-free, and are exercised on the plain JVM — the same split the panel makes
 * between the view and [EmojiPanelState].
 *
 * Both rules come from defects the operator found on a real phone in 1.6.0: the caret had a key's
 * constant added to its x and read as a trailing space, and the result band was measured even when
 * its only content was an invitation to type.
 */
internal object EmojiSearchLayout {

    /**
     * X of the caret: the right edge of the drawn query and nothing else. The caret follows the
     * text, so no inset, padding or key half-size may enter here — [textLeft] is where the query is
     * drawn from and [textWidth] is what the paint measured for it.
     */
    fun caretX(textLeft: Float, textWidth: Float): Float = textLeft + textWidth

    /**
     * Whether the band under the query row exists at all. An empty query has nothing to report, and
     * a band filled with placeholder words is worse than a band that is not there: the space goes
     * back to the keyboard instead.
     */
    fun showsResultBand(queryText: String): Boolean = queryText.isNotEmpty()

    /**
     * Height the view asks for: the query row always, the result band only while there is a query.
     */
    fun contentHeight(queryRowPx: Int, resultBandPx: Int, queryText: String): Int =
        if (showsResultBand(queryText)) queryRowPx + resultBandPx else queryRowPx
}
