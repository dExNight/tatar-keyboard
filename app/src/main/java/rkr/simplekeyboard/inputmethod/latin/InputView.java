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
import rkr.simplekeyboard.inputmethod.latin.suggestions.SuggestionStripView;

public final class InputView extends FrameLayout {
    private final Rect mTemporaryBounds = new Rect();
    private SuggestionStripView mSuggestionStripView;
    private Runnable mInsetsChangedListener;

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

    /** Shows the fixed-height strip, including the valid visible zero-results state. */
    public SuggestionStripView showSuggestionStrip(final String first, final String second,
            final String third) {
        final SuggestionStripView strip = getOrCreateSuggestionStripView();
        if (strip == null) {
            return null;
        }
        strip.setSuggestions(first, second, third);
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
