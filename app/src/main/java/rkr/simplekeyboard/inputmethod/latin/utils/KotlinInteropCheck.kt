package rkr.simplekeyboard.inputmethod.latin.utils

import android.util.Log
import rkr.simplekeyboard.inputmethod.latin.define.DebugFlags

object KotlinInteropCheck {
    @JvmStatic
    fun log() {
        if (DebugFlags.DEBUG_ENABLED) {
            Log.i("TatarKeyboard", "Kotlin interop OK")
        }
    }
}
