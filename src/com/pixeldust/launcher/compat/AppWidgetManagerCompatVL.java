/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.pixeldust.launcher.compat;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.pixeldust.launcher.LauncherAppWidgetProviderInfo;
import com.pixeldust.launcher.R;
import com.pixeldust.launcher.util.ComponentKey;

class AppWidgetManagerCompatVL extends AppWidgetManagerCompat {

    private final UserManager mUserManager;
    private final PackageManager mPm;

    AppWidgetManagerCompatVL(Context context) {
        super(context);
        mPm = context.getPackageManager();
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    @Override
    public List<AppWidgetProviderInfo> getAllProviders() {
        ArrayList<AppWidgetProviderInfo> providers = new ArrayList<>();
        for (UserHandle user : mUserManager.getUserProfiles()) {
            providers.addAll(mAppWidgetManager.getInstalledProvidersForProfile(user));
        }
        return providers;
    }

    @Override
    public String loadLabel(LauncherAppWidgetProviderInfo info) {
        return info.getLabel(mPm);
    }

    @Override
    public boolean bindAppWidgetIdIfAllowed(int appWidgetId, AppWidgetProviderInfo info,
                                            Bundle options) {
        return mAppWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId, info.getProfile(), info.provider, options);
    }

    @Override
    public UserHandle getUser(LauncherAppWidgetProviderInfo info) {
        return info.getProfile();
    }

    @Override
    public void startConfigActivity(int widgetId, Activity activity,
                                    AppWidgetHost host, int requestCode) {
        try {
            host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode, null);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public Drawable loadPreview(AppWidgetProviderInfo info) {
        return info.loadPreviewImage(mContext, 0);
    }

    @Override
    public Drawable loadIcon(LauncherAppWidgetProviderInfo info) {
        return info.getIcon(mContext);
    }

    @Override
    public Bitmap getBadgeBitmap(LauncherAppWidgetProviderInfo info, Bitmap bitmap,
                                 int imageWidth, int imageHeight) {
        if (info.getProfile().equals(android.os.Process.myUserHandle())) {
            return bitmap;
        }

        // Add a user badge in the bottom right of the image.
        final Resources res = mContext.getResources();
        final int badgeMinTop = res.getDimensionPixelSize(R.dimen.profile_badge_minimum_top);

        // choose min between badge size defined for widget tray versus width, height of the image.
        // Width, height of the image can be smaller than widget tray badge size when being dropped
        // to the workspace.
        final int badgeSize = Math.min(res.getDimensionPixelSize(R.dimen.profile_badge_size),
                Math.min(imageWidth, imageHeight - badgeMinTop));
        final Rect badgeLocation = new Rect(0, 0, badgeSize, badgeSize);

        final int top = Math.max(imageHeight - badgeSize, badgeMinTop);

        if (res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            badgeLocation.offset(0, top);
        } else {
            badgeLocation.offset(bitmap.getWidth() - badgeSize, top);
        }

        Drawable drawable = mPm.getUserBadgedDrawableForDensity(
                new BitmapDrawable(res, bitmap), info.getProfile(), badgeLocation, 0);

        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        bitmap.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(bitmap);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(c);
        c.setBitmap(null);
        return bitmap;
    }

    @Override
    public LauncherAppWidgetProviderInfo findProvider(ComponentName provider, UserHandle user) {
        for (AppWidgetProviderInfo info : mAppWidgetManager
                .getInstalledProvidersForProfile(user)) {
            if (info.provider.equals(provider)) {
                return LauncherAppWidgetProviderInfo.fromProviderInfo(info);
            }
        }
        return null;
    }

    @Override
    public HashMap<ComponentKey, AppWidgetProviderInfo> getAllProvidersMap() {
        HashMap<ComponentKey, AppWidgetProviderInfo> result = new HashMap<>();
        for (UserHandle user : mUserManager.getUserProfiles()) {
            UserHandle userHandle = user;
            for (AppWidgetProviderInfo info :
                    mAppWidgetManager.getInstalledProvidersForProfile(user)) {
                result.put(new ComponentKey(info.provider, userHandle), info);
            }
        }
        return result;
    }
}
