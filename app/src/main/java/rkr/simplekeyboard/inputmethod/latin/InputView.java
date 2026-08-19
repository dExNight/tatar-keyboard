/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2018 Raimondas Rimkus
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

package rkr.simplekeyboard.inputmethod.latin;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.emoji.EmojiPanelView;
import rkr.simplekeyboard.inputmethod.latin.emoji.EmojiSetSnapshot;
import rkr.simplekeyboard.inputmethod.latin.suggestions.SuggestionStripView;

public final class InputView extends FrameLayout {
    private final Rect mTemporaryBounds = new Rect();
    private SuggestionStripView mSuggestionStripView;
    private EmojiPanelView mEmojiPanelView;
    private Runnable mInsetsChangedListener;

    /**
     * Set while the emoji panel hid a visible suggestion strip, so {@link #hideEmojiPanel()} puts
     * back exactly what was there. The strip has nothing to show while the panel is open and its
     * reserved band was simply empty; the height it frees is handed to the panel instead, so the
     * total keyboard height — and the content top inset — does not change when the panel opens.
     */
    private boolean mStripHiddenByEmojiPanel;

    public InputView(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
    }

    /**
     * Creates the strip on first use. Merely inflating the keyboard never creates it.
     */
    public SuggestionStripView getOrCreateSuggestionStripView() {
        if (mSuggestionStripView != null) {
            return mSuggestionStripView;
        }
        final View stub = findViewById(R.id.suggestion_strip_stub);
        if (!(stub instanceof ViewStub)) {
            return null;
        }
        final View inflated = ((ViewStub) stub).inflate();
        if (!(inflated instanceof SuggestionStripView)) {
            return null;
        }
        final SuggestionStripView strip = (SuggestionStripView) inflated;
        mSuggestionStripView = strip;
        return strip;
    }

    public SuggestionStripView getSuggestionStripView() {
        return mSuggestionStripView;
    }

    /** Creates the emoji panel on first use. Merely inflating the keyboard never creates it. */
    public EmojiPanelView getOrCreateEmojiPanelView() {
        if (mEmojiPanelView != null) {
            return mEmojiPanelView;
        }
        final View stub = findViewById(R.id.emoji_panel_stub);
        if (!(stub instanceof ViewStub)) {
            return null;
        }
        final View inflated = ((ViewStub) stub).inflate();
        if (!(inflated instanceof EmojiPanelView)) {
            return null;
        }
        mEmojiPanelView = (EmojiPanelView) inflated;
        return mEmojiPanelView;
    }

    public EmojiPanelView getEmojiPanelView() {
        return mEmojiPanelView;
    }

    /**
     * Shows the emoji panel sized to the current keyboard height, so that the content top inset is
     * identical whether the keyboard or the panel is visible. The caller hides the
     * {@link rkr.simplekeyboard.inputmethod.keyboard.MainKeyboardView}; the two are never visible
     * at once.
     */
    public EmojiPanelView showEmojiPanel(final int keyboardHeightPx,
            final EmojiSetSnapshot snapshot) {
        final EmojiPanelView panel = getOrCreateEmojiPanelView();
        if (panel == null) {
            return null;
        }
        int panelHeightPx = keyboardHeightPx;
        if (mSuggestionStripView != null && mSuggestionStripView.getVisibility() == VISIBLE) {
            // Take the strip's measured height before hiding it, so the panel grows by exactly
            // what the strip gave up and the keyboard keeps its height.
            final int stripHeight = mSuggestionStripView.getHeight();
            mSuggestionStripView.setVisibility(GONE);
            mStripHiddenByEmojiPanel = true;
            if (stripHeight > 0) {
                panelHeightPx += stripHeight;
            }
        }
        panel.setPanelHeightPx(panelHeightPx);
        panel.setSnapshot(snapshot);
        if (panel.getVisibility() != VISIBLE) {
            panel.setVisibility(VISIBLE);
            notifyInsetsChanged();
        }
        return panel;
    }

    /** Hides the emoji panel without creating it, restoring any strip the panel hid. */
    public void hideEmojiPanel() {
        boolean changed = false;
        if (mStripHiddenByEmojiPanel) {
            mStripHiddenByEmojiPanel = false;
            if (mSuggestionStripView != null) {
                mSuggestionStripView.setVisibility(VISIBLE);
                changed = true;
            }
        }
        if (mEmojiPanelView != null && mEmojiPanelView.getVisibility() != GONE) {
            mEmojiPanelView.setVisibility(GONE);
            changed = true;
        }
        if (changed) {
            notifyInsetsChanged();
        }
    }

    private boolean isEmojiPanelShowing() {
        return mEmojiPanelView != null && mEmojiPanelView.getVisibility() == VISIBLE;
    }

    /** Shows the fixed-height strip, including the valid visible zero-results state. */
    public SuggestionStripView showSuggestionStrip(final String first, final String second,
            final String third) {
        final SuggestionStripView strip = getOrCreateSuggestionStripView();
        if (strip == null) {
            return null;
        }
        strip.setSuggestions(first, second, third);
        if (isEmojiPanelShowing()) {
            // The panel owns the surface: keep the strip down and remember that it wanted to be
            // up, so hideEmojiPanel() restores it. Without this a new input session started while
            // the panel is open (a different field, a selection change) puts the empty band back
            // over the panel.
            mStripHiddenByEmojiPanel = true;
            return strip;
        }
        if (strip.getVisibility() != VISIBLE) {
            strip.setVisibility(VISIBLE);
            notifyInsetsChanged();
        }
        return strip;
    }

    /**
     * Reserves the fixed-height strip: makes it VISIBLE with no words (empty band) so the keyboard
     * keeps a stable height across an eligible input session. Mirrors {@link #showSuggestionStrip}
     * but with no suggestions.
     */
    public SuggestionStripView reserveSuggestionStrip() {
        final SuggestionStripView strip = getOrCreateSuggestionStripView();
        if (strip == null) {
            return null;
        }
        strip.clearSuggestions();
        if (isEmojiPanelShowing()) {
            mStripHiddenByEmojiPanel = true;
            return strip;
        }
        if (strip.getVisibility() != VISIBLE) {
            strip.setVisibility(VISIBLE);
            notifyInsetsChanged();
        }
        return strip;
    }

    /** Clears stale contents and removes all layout and touch impact without creating the strip. */
    public void clearAndHideSuggestionStrip() {
        if (mSuggestionStripView == null) {
            return;
        }
        mSuggestionStripView.clearSuggestions();
        if (mSuggestionStripView.getVisibility() != GONE) {
            mSuggestionStripView.setVisibility(GONE);
            notifyInsetsChanged();
        }
    }

    public void setInsetsChangedListener(final Runnable listener) {
        mInsetsChangedListener = listener;
    }

    /** Clears callbacks and stale strip state before this input view is detached or replaced. */
    public void release() {
        if (mSuggestionStripView != null) {
            mSuggestionStripView.release();
        }
        if (mEmojiPanelView != null) {
            mEmojiPanelView.release();
        }
        mInsetsChangedListener = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    /**
     * Returns bounds relative to this InputView only after the visible keyboard and optional strip
     * have completed layout. The caller-supplied Rect is cleared when no truthful bounds exist.
     */
    public boolean getVisibleInputBounds(final View visibleKeyboardView, final Rect outBounds) {
        outBounds.setEmpty();
        if (!isLaidOut() || visibleKeyboardView == null || !visibleKeyboardView.isShown()
                || !visibleKeyboardView.isLaidOut()
                || visibleKeyboardView.getWidth() <= 0 || visibleKeyboardView.getHeight() <= 0) {
            return false;
        }
        outBounds.set(0, 0, visibleKeyboardView.getWidth(), visibleKeyboardView.getHeight());
        offsetDescendantRectToMyCoords(visibleKeyboardView, outBounds);

        final SuggestionStripView strip = mSuggestionStripView;
        if (strip != null && strip.isShown() && strip.isLaidOut()
                && strip.getWidth() > 0 && strip.getHeight() > 0) {
            mTemporaryBounds.set(0, 0, strip.getWidth(), strip.getHeight());
            offsetDescendantRectToMyCoords(strip, mTemporaryBounds);
            outBounds.union(mTemporaryBounds);
        }

        // The emoji panel is a second surface inside the same stack; when it is shown its bounds
        // join the touchable/visible region the same way the strip's do, so touches on it never
        // fall through to the application behind the keyboard.
        final EmojiPanelView panel = mEmojiPanelView;
        if (panel != null && panel.isShown() && panel.isLaidOut()
                && panel.getWidth() > 0 && panel.getHeight() > 0) {
            mTemporaryBounds.set(0, 0, panel.getWidth(), panel.getHeight());
            offsetDescendantRectToMyCoords(panel, mTemporaryBounds);
            outBounds.union(mTemporaryBounds);
        }
        return true;
    }

    private void notifyInsetsChanged() {
        requestLayout();
        requestApplyInsets();
        if (mInsetsChangedListener != null) {
            mInsetsChangedListener.run();
        }
    }
}
