/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.vending.licensing;

import android.content.SharedPreferences;
import android.util.Log;

import delit.libs.core.util.Logging;

/**
 * An wrapper for SharedPreferences that transparently performs data obfuscation.
 */
public class PreferenceObfuscator {

    private static final String TAG = "PreferenceObfuscator";

    private final SharedPreferences mPreferences;
    private final Obfuscator mObfuscator;
    private SharedPreferences.Editor mEditor;

    /**
     * Constructor.
     *
     * @param sp A SharedPreferences instance provided by the system.
     * @param o The Obfuscator to use when reading or writing data.
     */
    public PreferenceObfuscator(SharedPreferences sp, Obfuscator o) {
        mPreferences = sp;
        mObfuscator = o;
        mEditor = null;
    }

    public void putString(String key, String value) {
        if (mEditor == null) {
            mEditor = mPreferences.edit();
        }
        String obfuscatedValue = mObfuscator.obfuscateString(value, key);
        mEditor.putString(key, obfuscatedValue);
    }

    public String getString(String key, String defValue) {
        String result;
        String value = mPreferences.getString(key, null);
        if (value != null) {
            try {
                result = mObfuscator.unobfuscateString(value, key);
            } catch (ValidationException e) {
                Logging.recordException(e);
                // Unable to unobfuscateString, data corrupt or tampered
                Logging.log(Log.WARN, TAG, "Validation error while reading preference: " + key);
                result = defValue;
            }
        } else {
            // Preference not found
            result = defValue;
        }
        return result;
    }

    public void commit() {
        if (mEditor != null) {
            mEditor.commit();
            mEditor = null;
        }
    }

}
