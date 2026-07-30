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

package rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore

import android.content.Context
import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.WordCompletionSink

/**
 * The five factors that decide whether ANYTHING may be learned. One predicate, deliberately, rather
 * than five checks spread over the read and the write paths — that is how the two drift apart.
 *
 * Every factor is supplied by `LatinIME`, which is the only place that sees all of them:
 *  1. Tatar suggestions are eligible for this field and subtype (which already implies the field
 *     allows suggestions and does not carry `IME_FLAG_NO_PERSONALIZED_LEARNING`);
 *  2. the personal dictionary setting is ON;
 *  3. the device has been unlocked at least once since boot;
 *  4. the field is not a postal address;
 *  5. — folded into (1): a field with no editor info at all is never eligible, so the
 *     `editorInfo == null` case is closed by the same factor rather than by a separate check.
 */
fun interface PersonalLearningPredicate {
    fun mayLearn(): Boolean
}

/**
 * Turns clean-completion events into store mutations, under [PersonalLearningPredicate].
 *
 * This is the ONE place where typing can cause a write, and it is deliberately not in `LatinIME` and
 * not in `SuggestionsController`: the class that sees every keystroke announces an event, and the
 * decision to persist anything lives here, inside the package that owns the file.
 */
object PersonalLearning {

    @JvmStatic
    fun sinkFor(
        context: Context,
        subtypeId: String,
        predicate: PersonalLearningPredicate,
    ): WordCompletionSink = object : WordCompletionSink {
        override fun onCleanCompletion(word: String) {
            if (!predicate.mayLearn()) return
            PersonalDictionaries.storeFor(context, subtypeId).noteCompletion(word)
        }

        override fun onInputFinished() {
            // The flush is gated too: without the predicate a session that became ineligible could
            // still put what it accumulated on disk.
            if (!predicate.mayLearn()) return
            PersonalDictionaries.storeFor(context, subtypeId).flush()
        }
    }
}
