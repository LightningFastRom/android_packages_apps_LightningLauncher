package com.pixeldust.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

public class StringSetAppFilter implements AppFilter {
    @Override
    public boolean shouldShowApp(ComponentName app, Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        return prefs.getBoolean("pref_showHidden", false) || !Utilities.isAppHidden(context, app.flattenToString());
    }
}
