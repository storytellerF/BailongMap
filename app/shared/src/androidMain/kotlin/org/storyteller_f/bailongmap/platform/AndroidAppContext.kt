package org.storyteller_f.bailongmap.platform

import android.content.Context

object AndroidAppContext {
    lateinit var context: Context
        private set

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}
