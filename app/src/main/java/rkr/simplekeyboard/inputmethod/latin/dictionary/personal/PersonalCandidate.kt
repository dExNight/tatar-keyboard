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

package rkr.simplekeyboard.inputmethod.latin.dictionary.personal

/**
 * One personal-dictionary match: the form to SHOW ([rawForm], as the user saved it) together with
 * the form to COMPARE by ([normalizedForm]).
 *
 * Both are needed by the three-class merge (E4b): the display form carries the user's own casing,
 * while duplicate detection against dictionary candidates and the exact-word exclusion are defined
 * on the normalized form ("Контракт текста", правка 3 из E4a-1).
 *
 * NOT a Kotlin `data class`, and [toString] is overridden: this type carries the user's word, and a
 * synthesised `toString` would print it at the first interpolation.
 */
class PersonalCandidate(val rawForm: String, val normalizedForm: String) {
    /** Deliberately says nothing: the user's word must never reach a log or an exception message. */
    override fun toString(): String = "PersonalCandidate"
}

/**
 * The personal side of the merge, as seen by the engine. Kept as a seam so the composite computer
 * is testable without a file, a snapshot or Android.
 */
fun interface PersonalCandidateSource {
    /**
     * Matches for [normalizedPrefix] in the personal order — usage count descending, then
     * normalized form ascending — with the record equal to the prefix already excluded.
     */
    fun candidatesFor(normalizedPrefix: String): List<PersonalCandidate>

    /**
     * True when this source cannot produce anything at all (feature off, locked device, empty
     * dictionary). The merge checks it FIRST so a disabled personal dictionary costs the lookup path
     * nothing — not even decoding the prefix bytes into a String.
     */
    fun isEmpty(): Boolean = false

    companion object {
        /** The source used whenever the personal dictionary is off or unavailable. */
        @JvmField
        val EMPTY: PersonalCandidateSource = object : PersonalCandidateSource {
            override fun candidatesFor(normalizedPrefix: String): List<PersonalCandidate> =
                emptyList()

            override fun isEmpty(): Boolean = true
        }
    }
}

/**
 * The production source: reads whatever immutable snapshot [snapshot] currently returns. The
 * snapshot itself is published by the personal store on its own worker, so this only ever reads a
 * `@Volatile` reference — no I/O, no lock, no checksum on the engine thread.
 */
class SnapshotPersonalCandidateSource(
    private val snapshot: () -> PersonalDictionary,
) : PersonalCandidateSource {
    override fun candidatesFor(normalizedPrefix: String): List<PersonalCandidate> =
        snapshot().lookupCandidates(normalizedPrefix)

    override fun isEmpty(): Boolean = snapshot().isEmpty
}
