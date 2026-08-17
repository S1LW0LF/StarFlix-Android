package com.starflix.local.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.starFlixDataStore by preferencesDataStore(name = "starflix_preferences")
