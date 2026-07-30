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

package rkr.simplekeyboard.inputmethod.latin.settings

import rkr.simplekeyboard.inputmethod.latin.dictionary.personal.PersonalDictionary
import rkr.simplekeyboard.inputmethod.latin.dictionary.personalstore.PersonalWordFilter

/** One row of the "Personal dictionary" screen: a word of one language, with the key to delete it by. */
internal class PersonalWordRow(
    val subtypeId: String,
    val rawForm: String,
    val normalizedForm: String,
) {
    /** Says nothing on purpose: this type carries the user's word. */
    override fun toString(): String = "PersonalWordRow"
}

/** All rows of one language, in normalized alphabetical order. */
internal class PersonalWordSection(val subtypeId: String, val rows: List<PersonalWordRow>)

/**
 * What the screen materializes: the sections to show, how many rows that is ([shownCount]) and how
 * many matched in total ([totalCount]). The two differ exactly when the cap trims the list, and the
 * screen says so in a "showing N of M" row rather than silently truncating.
 */
internal class PersonalScreenContent(
    val sections: List<PersonalWordSection>,
    val shownCount: Int,
    val totalCount: Int,
) {
    val isTruncated: Boolean
        get() = shownCount < totalCount
}

/**
 * The pure content model of the "Personal dictionary" screen — grouping, search and the row cap,
 * with no Android and no I/O, so all of it is covered by plain JVM tests.
 *
 * The performance limiter is a CAP ON ROWS, not a choice of one language, and that is not cosmetic:
 * `SettingsHostActivity` builds its content imperatively into a `ScrollView` + `LinearLayout`
 * without view reuse, and rebuilds the whole screen in `onStart` — that is, on every return to the
 * foreground and after every dialog. `RecyclerView` is not available (the single androidx dependency
 * is `customview`, and adding `recyclerview` costs on the order of a hundred kilobytes against a
 * phase budget of 25 600 B), so the list simply must not grow without bound. At a cap of 200 it does
 * not matter at all whether those rows come from one language or two.
 *
 * Showing only the active subtype was rejected for a reason that is not convenience: "erase all"
 * deletes the files of EVERY language, so a screen that shows one language would silently erase what
 * is not on it.
 */
internal object PersonalDictionaryScreenModel {

    /** The maximum number of rows materialized at once, across all languages together. */
    const val MAX_MATERIALIZED_ROWS = 200

    /**
     * Builds the content for [dictionaries] (in the order the languages should appear) narrowed by
     * [query].
     *
     * Matching is on the NORMALIZED form of both sides, so a search for "гүзәл" finds a saved
     * "Гүзәл"; the row still shows the saved spelling. Filtering happens HERE, before a single View
     * exists — that is what the contract means by "the search narrows the list before the views are
     * built".
     */
    fun build(
        dictionaries: List<Pair<String, PersonalDictionary>>,
        query: String,
    ): PersonalScreenContent {
        val normalizedQuery = PersonalWordFilter.normalize(query)
        var total = 0
        var remaining = MAX_MATERIALIZED_ROWS
        val sections = ArrayList<PersonalWordSection>(dictionaries.size)

        for ((subtypeId, dictionary) in dictionaries) {
            val matches = ArrayList<PersonalWordRow>()
            // The snapshot is already ordered by normalized form ascending, which is the order the
            // sections want, so no sorting happens here.
            for (index in 0 until dictionary.size) {
                val normalized = dictionary.normalizedFormAt(index)
                if (normalizedQuery.isNotEmpty() && !normalized.contains(normalizedQuery)) continue
                total++
                if (remaining <= 0) continue
                remaining--
                matches.add(
                    PersonalWordRow(subtypeId, dictionary.rawFormAt(index), normalized),
                )
            }
            if (matches.isNotEmpty()) sections.add(PersonalWordSection(subtypeId, matches))
        }

        val shown = sections.sumOf { it.rows.size }
        return PersonalScreenContent(sections, shown, total)
    }
}
