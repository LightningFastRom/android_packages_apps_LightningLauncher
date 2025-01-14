/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.pixeldust.launcher;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

import com.pixeldust.launcher.compat.LauncherActivityInfoCompat;
import com.pixeldust.launcher.compat.LauncherAppsCompat;
import com.pixeldust.launcher.compat.UserManagerCompat;
import com.pixeldust.launcher.model.PackageItemInfo;
import com.pixeldust.launcher.pixelify.PixelIconProvider;
import com.pixeldust.launcher.util.ComponentKey;
import com.pixeldust.launcher.util.SQLiteCacheHelper;
import com.pixeldust.launcher.util.Thunk;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache {

    private static final String TAG = "Launcher.IconCache";

    private static final int INITIAL_ICON_CACHE_CAPACITY = 50;

    // Empty class name is used for storing package default entry.
    private static final String EMPTY_CLASS_NAME = ".";

    private static final int LOW_RES_SCALE_FACTOR = 5;

    @Thunk
    static final Object ICON_UPDATE_TOKEN = new Object();

    @Thunk
    static class CacheEntry {
        public Bitmap icon;
        public CharSequence title = "";
        public CharSequence contentDescription = "";
        public boolean isLowResIcon;
    }

    private final HashMap<UserHandle, Bitmap> mDefaultIcons = new HashMap<>();
    @Thunk
    final MainThreadExecutor mMainThreadExecutor = new MainThreadExecutor();

    private final Context mContext;
    private final PackageManager mPackageManager;
    @Thunk
    final UserManagerCompat mUserManager;
    private final LauncherAppsCompat mLauncherApps;
    private final HashMap<ComponentKey, CacheEntry> mCache =
            new HashMap<>(INITIAL_ICON_CACHE_CAPACITY);
    private final int mIconDpi;
    @Thunk
    final IconDB mIconDb;

    @Thunk
    final Handler mWorkerHandler;

    // The background color used for activity icons. Since these icons are displayed in all-apps
    // and folders, this would be same as the light quantum panel background. This color
    // is used to convert icons to RGB_565.
    private final int mActivityBgColor;
    // The background color used for package icons. These are displayed in widget tray, which
    // has a dark quantum panel background.
    private final int mPackageBgColor;
    private final BitmapFactory.Options mLowResOptions;

    public PixelIconProvider pip;

    private Canvas mLowResCanvas;
    private Paint mLowResPaint;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mUserManager = UserManagerCompat.getInstance(mContext);
        mLauncherApps = LauncherAppsCompat.getInstance(mContext);
        mIconDpi = inv.fillResIconDpi;
        mIconDb = new IconDB(context, inv.iconBitmapSize);
        mLowResCanvas = new Canvas();
        mLowResPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        pip = new PixelIconProvider(context);

        mWorkerHandler = new Handler(LauncherModel.getWorkerLooper());

        mActivityBgColor = context.getResources().getColor(R.color.quantum_panel_bg_color);
        TypedArray ta = context.obtainStyledAttributes(new int[]{R.attr.colorSecondary});
        mPackageBgColor = ta.getColor(0, 0);
        ta.recycle();
        mLowResOptions = new BitmapFactory.Options();
        // Always prefer RGB_565 config for low res. If the bitmap has transparency, it will
        // automatically be loaded as ALPHA_8888.
        mLowResOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    private Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(), android.R.mipmap.sym_def_app_icon);
    }

    private Drawable getFullResIcon(Resources resources, int iconId) {
        Drawable d;
        try {
            d = resources.getDrawableForDensity(iconId, mIconDpi, null);
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        return (d != null) ? d : getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ActivityInfo info) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(LauncherActivityInfo info) {
        return info.getIcon(mIconDpi);
    }

    private Bitmap makeDefaultIcon(UserHandle user) {
        Drawable unbadged = getFullResDefaultActivityIcon();
        return Utilities.createBadgedIconBitmap(unbadged, user, mContext);
    }

    /**
     * Remove any records for the supplied ComponentName.
     */
    public synchronized void remove(ComponentName componentName, UserHandle user) {
        mCache.remove(new ComponentKey(componentName, user));
    }

    /**
     * Remove any records for the supplied package name from memory.
     */
    private void removeFromMemCacheLocked(String packageName, UserHandle user) {
        HashSet<ComponentKey> forDeletion = new HashSet<>();
        for (ComponentKey key : mCache.keySet()) {
            if (key.componentName.getPackageName().equals(packageName)
                    && key.user.equals(user)) {
                forDeletion.add(key);
            }
        }
        for (ComponentKey condemned : forDeletion) {
            mCache.remove(condemned);
        }
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandle user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfoCompat app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDBAndMemCache(app, info, userSerial);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
        }
    }

    /**
     * Updates the entries related to all packages in memory and persistent DB.
     */
    public void updateIconsForAll(final UserHandle user) {
        long userSerial = mUserManager.getSerialNumberForUser(user);
        for (LauncherActivityInfoCompat app : mLauncherApps.getActivityList(null, user)) {
            updateIconInDBAndMemCache(app, userSerial);
        }
    }

    /**
     * Removes the entries related to the given package in memory and persistent DB.
     */
    public synchronized void removeIconsForPkg(String packageName, UserHandle user) {
        removeFromMemCacheLocked(packageName, user);
        long userSerial = mUserManager.getSerialNumberForUser(user);
        mIconDb.delete(
                IconDB.COLUMN_COMPONENT + " LIKE ? AND " + IconDB.COLUMN_USER + " = ?",
                new String[]{packageName + "/%", Long.toString(userSerial)});
    }

    public void updateDbIcons(Set<String> ignorePackagesForMainUser) {
        // Remove all active icon update tasks.
        mWorkerHandler.removeCallbacksAndMessages(ICON_UPDATE_TOKEN);

        for (UserHandle user : mUserManager.getUserProfiles()) {
            // Query for the set of apps
            final List<LauncherActivityInfoCompat> apps = mLauncherApps.getActivityList(null, user);
            // Fail if we don't have any apps
            if (apps == null || apps.isEmpty()) {
                if (user.equals(Utilities.myUserHandle())) {
                    return;
                } else {
                    continue;
                }
            }

            // Update icon cache. This happens in segments and {@link #onPackageIconsUpdated}
            // is called by the icon cache when the job is complete.
            updateDBIcons(user, apps, Utilities.myUserHandle().equals(user)
                    ? ignorePackagesForMainUser : Collections.<String>emptySet());
        }
    }

    /**
     * Updates the persistent DB, such that only entries corresponding to {@param apps} remain in
     * the DB and are updated.
     *
     * @return The set of packages for which icons have updated.
     */
    private void updateDBIcons(UserHandle user, List<LauncherActivityInfoCompat> apps,
                               Set<String> ignorePackages) {
        long userSerial = mUserManager.getSerialNumberForUser(user);
        PackageManager pm = mContext.getPackageManager();
        HashMap<String, PackageInfo> pkgInfoMap = new HashMap<>();
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES)) {
            pkgInfoMap.put(info.packageName, info);
        }

        HashMap<ComponentName, LauncherActivityInfoCompat> componentMap = new HashMap<>();
        for (LauncherActivityInfoCompat app : apps) {
            componentMap.put(app.getComponentName(), app);
        }

        HashSet<Integer> itemsToRemove = new HashSet<>();
        Stack<LauncherActivityInfoCompat> appsToUpdate = new Stack<>();

        Cursor c = null;
        try {
            c = mIconDb.query(
                    new String[]{IconDB.COLUMN_ROWID, IconDB.COLUMN_COMPONENT,
                            IconDB.COLUMN_LAST_UPDATED, IconDB.COLUMN_VERSION,
                            IconDB.COLUMN_SYSTEM_STATE},
                    IconDB.COLUMN_USER + " = ? ",
                    new String[]{Long.toString(userSerial)});

            final int indexComponent = c.getColumnIndex(IconDB.COLUMN_COMPONENT);
            final int indexLastUpdate = c.getColumnIndex(IconDB.COLUMN_LAST_UPDATED);
            final int indexVersion = c.getColumnIndex(IconDB.COLUMN_VERSION);
            final int rowIndex = c.getColumnIndex(IconDB.COLUMN_ROWID);
            final int systemStateIndex = c.getColumnIndex(IconDB.COLUMN_SYSTEM_STATE);

            while (c.moveToNext()) {
                String cn = c.getString(indexComponent);
                ComponentName component = ComponentName.unflattenFromString(cn);
                PackageInfo info = pkgInfoMap.get(component.getPackageName());
                if (info == null) {
                    if (!ignorePackages.contains(component.getPackageName())) {
                        remove(component, user);
                        itemsToRemove.add(c.getInt(rowIndex));
                    }
                    continue;
                }
                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_IS_DATA_ONLY) != 0) {
                    // Application is not present
                    continue;
                }

                long updateTime = c.getLong(indexLastUpdate);
                int version = c.getInt(indexVersion);
                LauncherActivityInfoCompat app = componentMap.remove(component);
                if (version == info.versionCode && updateTime == info.lastUpdateTime &&
                        TextUtils.equals(c.getString(systemStateIndex), Locale.getDefault().toString())) {
                    continue;
                }
                if (app == null) {
                    remove(component, user);
                    itemsToRemove.add(c.getInt(rowIndex));
                } else {
                    appsToUpdate.add(app);
                }
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
            // Continue updating whatever we have read so far
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (!itemsToRemove.isEmpty()) {
            mIconDb.delete(
                    Utilities.createDbSelectionQuery(IconDB.COLUMN_ROWID, itemsToRemove), null);
        }

        // Insert remaining apps.
        if (!componentMap.isEmpty() || !appsToUpdate.isEmpty()) {
            Stack<LauncherActivityInfoCompat> appsToAdd = new Stack<>();
            appsToAdd.addAll(componentMap.values());
            new SerializedIconUpdateTask(userSerial, pkgInfoMap,
                    appsToAdd, appsToUpdate).scheduleNext();
        }
    }

    @Thunk
    void addIconToDBAndMemCache(LauncherActivityInfoCompat app, PackageInfo info,
                                long userSerial) {
        // Reuse the existing entry if it already exists in the DB. This ensures that we do not
        // create bitmap if it was already created during loader.
        ContentValues values = updateCacheAndGetContentValues(app, false);
        addIconToDB(values, app.getComponentName(), info, userSerial);
    }

    @Thunk
    void updateIconInDBAndMemCache(LauncherActivityInfoCompat app, long userSerial) {
        // Reuse the existing entry if it already exists in the DB. This ensures that we do not
        // create bitmap if it was already created during loader.
        final ComponentKey key = new ComponentKey(app.getComponentName(), app.getUser());
        CacheEntry entry;
        entry = mCache.get(key);
        if (entry == null) {
            return;
        }
        Bitmap tmp = entry.icon;
        entry.icon = Utilities.createBadgedIconBitmap(
                pip.getIcon(app, mIconDpi), app.getUser(),
                mContext);
        if (entry.icon.equals(tmp)) {
            return;
        }
        mCache.put(key, entry);

        Bitmap lowResIcon = generateLowResIcon(entry.icon, mActivityBgColor);
        ContentValues values = newContentValues(entry.icon, lowResIcon, entry.title.toString());
        mIconDb.update(values, "componentName = ? AND profileID = ?", new String[]{app.getComponentName().flattenToString(), "" + userSerial});
    }

    /**
     * Updates {@param values} to contain versoning information and adds it to the DB.
     *
     * @param values {@link ContentValues} containing icon & title
     */
    private void addIconToDB(ContentValues values, ComponentName key,
                             PackageInfo info, long userSerial) {
        values.put(IconDB.COLUMN_COMPONENT, key.flattenToString());
        values.put(IconDB.COLUMN_USER, userSerial);
        values.put(IconDB.COLUMN_LAST_UPDATED, info.lastUpdateTime);
        values.put(IconDB.COLUMN_VERSION, info.versionCode);
        mIconDb.insertOrReplace(values);
    }

    @Thunk
    ContentValues updateCacheAndGetContentValues(LauncherActivityInfoCompat app,
                                                 boolean replaceExisting) {
        final ComponentKey key = new ComponentKey(app.getComponentName(), app.getUser());
        CacheEntry entry = null;
        if (!replaceExisting) {
            entry = mCache.get(key);
            // We can't reuse the entry if the high-res icon is not present.
            if (entry == null || entry.isLowResIcon || entry.icon == null) {
                entry = null;
            }
        }
        if (entry == null) {
            entry = new CacheEntry();
            entry.icon = Utilities.createBadgedIconBitmap(
                    pip.getIcon(app, mIconDpi), app.getUser(),
                    mContext);
        }
        entry.title = app.getLabel();
        entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, app.getUser());
        mCache.put(new ComponentKey(app.getComponentName(), app.getUser()), entry);

        Bitmap lowResIcon = generateLowResIcon(entry.icon, mActivityBgColor);
        return newContentValues(entry.icon, lowResIcon, entry.title.toString()
        );
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     *
     * @return a request ID that can be used to cancel the request.
     */
    public IconLoadRequest updateIconInBackground(final BubbleTextView caller, final ItemInfo info) {
        Runnable request = new Runnable() {

            @Override
            public void run() {
                if (info instanceof AppInfo) {
                    getTitleAndIcon((AppInfo) info, null, false);
                } else if (info instanceof ShortcutInfo) {
                    ShortcutInfo st = (ShortcutInfo) info;
                    getTitleAndIcon(st,
                            st.promisedIntent != null ? st.promisedIntent : st.intent,
                            st.user, false);
                } else if (info instanceof PackageItemInfo) {
                    PackageItemInfo pti = (PackageItemInfo) info;
                    getTitleAndIconForApp(pti, false);
                }
                mMainThreadExecutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        caller.reapplyItemInfo(info);
                    }
                });
            }
        };
        mWorkerHandler.post(request);
        return new IconLoadRequest(request, mWorkerHandler);
    }

    private Bitmap getNonNullIcon(CacheEntry entry, UserHandle user) {
        Bitmap b = entry.icon == null ? getDefaultIcon(user) : entry.icon;
        return b;
    }

    /**
     * Fill in "application" with the icon and label for "info."
     */
    public synchronized void getTitleAndIcon(AppInfo application,
                                             LauncherActivityInfoCompat info, boolean useLowResIcon) {
        UserHandle user = info == null ? application.user : info.getUser();
        CacheEntry entry = cacheLocked(application.componentName, info, user,
                false, useLowResIcon);
        application.originalTitle = Utilities.trim(entry.title);
        String key = "alias_" + application.componentName.flattenToString();
        application.title = Utilities.getPrefs(mContext).getString(key, application.originalTitle.toString());
        application.contentDescription = entry.contentDescription;
        application.iconBitmap = getNonNullIcon(entry, user);
        application.usingLowResIcon = entry.isLowResIcon;
    }

    /**
     * Updates {@param application} only if a valid entry is found.
     */
    public synchronized void updateTitleAndIcon(AppInfo application) {
        CacheEntry entry = cacheLocked(application.componentName, null, application.user,
                false, application.usingLowResIcon);
        if (entry.icon != null && !isDefaultIcon(entry.icon, application.user)) {
            application.originalTitle = Utilities.trim(entry.title);
            String key = "alias_" + application.componentName.flattenToString();
            application.title = Utilities.getPrefs(mContext).getString(key, application.originalTitle.toString());
            application.contentDescription = entry.contentDescription;
            application.iconBitmap = entry.icon;
            application.usingLowResIcon = entry.isLowResIcon;
        }
    }

    /**
     * Returns a high res icon for the given intent and user
     */
    public synchronized Bitmap getIcon(Intent intent, UserHandle user) {
        ComponentName component = intent.getComponent();
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (component == null) {
            return getDefaultIcon(user);
        }

        LauncherActivityInfoCompat launcherActInfo = mLauncherApps.resolveActivity(intent, user);
        CacheEntry entry = cacheLocked(component, launcherActInfo, user, true, false /* useLowRes */);
        return entry.icon;
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param intent}. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ShortcutInfo shortcutInfo, Intent intent,
                                             UserHandle user, boolean useLowResIcon) {
        ComponentName component = intent.getComponent();
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (component == null) {
            shortcutInfo.setIcon(getDefaultIcon(user));
            shortcutInfo.title = "";
            shortcutInfo.contentDescription = "";
            shortcutInfo.usingFallbackIcon = true;
            shortcutInfo.usingLowResIcon = false;
        } else {
            LauncherActivityInfoCompat info = mLauncherApps.resolveActivity(intent, user);
            getTitleAndIcon(shortcutInfo, component, info, user, true, useLowResIcon);
        }
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param info}
     */
    public synchronized void getTitleAndIcon(
            ShortcutInfo shortcutInfo, ComponentName component, LauncherActivityInfoCompat info,
            UserHandle user, boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(component, info, user, usePkgIcon, useLowResIcon);
        Bitmap iBitmap = getNonNullIcon(entry, user);
        shortcutInfo.setIcon(iBitmap);
        String title = Utilities.trim(entry.title);
        String key = "alias_" + component.flattenToString();
        shortcutInfo.title = Utilities.getPrefs(mContext).getString(key, title);
        shortcutInfo.contentDescription = entry.contentDescription;
        shortcutInfo.usingFallbackIcon = isDefaultIcon(entry.icon, user);
        shortcutInfo.usingLowResIcon = entry.isLowResIcon;
    }

    /**
     * Fill in {@param infoInOut} with the corresponding icon and label.
     */
    public synchronized void getTitleAndIconForApp(
            PackageItemInfo infoInOut, boolean useLowResIcon) {
        CacheEntry entry = getEntryForPackageLocked(
                infoInOut.packageName, infoInOut.user, useLowResIcon);
        infoInOut.title = Utilities.trim(entry.title);
        infoInOut.contentDescription = entry.contentDescription;
        infoInOut.iconBitmap = getNonNullIcon(entry, infoInOut.user);
        infoInOut.usingLowResIcon = entry.isLowResIcon;
    }

    public synchronized Bitmap getDefaultIcon(UserHandle user) {
        if (!mDefaultIcons.containsKey(user)) {
            mDefaultIcons.put(user, makeDefaultIcon(user));
        }
        return mDefaultIcons.get(user);
    }

    public boolean isDefaultIcon(Bitmap icon, UserHandle user) {
        return mDefaultIcons.get(user) == icon;
    }

    /**
     * Retrieves the entry from the cache. If the entry is not present, it creates a new entry.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry cacheLocked(ComponentName componentName, LauncherActivityInfoCompat info,
                                   UserHandle user, boolean usePackageIcon, boolean useLowResIcon) {
        ComponentKey cacheKey = new ComponentKey(componentName, user);
        CacheEntry entry = mCache.get(cacheKey);
        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);

            // Check the DB first.
            if (!getEntryFromDB(cacheKey, entry, useLowResIcon)) {
                if (info != null) {
                    entry.icon = Utilities.createBadgedIconBitmap(
                            pip.getIcon(info, mIconDpi), info.getUser(),
                            mContext);
                } else {
                    if (usePackageIcon) {
                        CacheEntry packageEntry = getEntryForPackageLocked(
                                componentName.getPackageName(), user, false);
                        if (packageEntry != null) {
                            entry.icon = packageEntry.icon;
                            entry.title = packageEntry.title;
                            entry.contentDescription = packageEntry.contentDescription;
                        }
                    }
                    if (entry.icon == null) {
                        entry.icon = getDefaultIcon(user);
                    }
                }
            }

            if (TextUtils.isEmpty(entry.title) && info != null) {
                entry.title = info.getLabel();
                entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
            }
        }
        return entry;
    }

    /**
     * Adds a default package entry in the cache. This entry is not persisted and will be removed
     * when the cache is flushed.
     */
    public synchronized void cachePackageInstallInfo(String packageName, UserHandle user,
                                                     Bitmap icon, CharSequence title) {
        removeFromMemCacheLocked(packageName, user);

        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        // For icon caching, do not go through DB. Just update the in-memory entry.
        if (entry == null) {
            entry = new CacheEntry();
            mCache.put(cacheKey, entry);
        }
        if (!TextUtils.isEmpty(title)) {
            entry.title = title;
        }
        if (icon != null) {
            entry.icon = Utilities.createIconBitmap(icon, mContext);
        }
    }

    private static ComponentKey getPackageKey(String packageName, UserHandle user) {
        ComponentName cn = new ComponentName(packageName, packageName + EMPTY_CLASS_NAME);
        return new ComponentKey(cn, user);
    }

    /**
     * Gets an entry for the package, which can be used as a fallback entry for various components.
     * This method is not thread safe, it must be called from a synchronized method.
     */
    private CacheEntry getEntryForPackageLocked(String packageName, UserHandle user,
                                                boolean useLowResIcon) {
        ComponentKey cacheKey = getPackageKey(packageName, user);
        CacheEntry entry = mCache.get(cacheKey);

        if (entry == null || (entry.isLowResIcon && !useLowResIcon)) {
            entry = new CacheEntry();
            boolean entryUpdated = true;

            // Check the DB first.
            if (!getEntryFromDB(cacheKey, entry, useLowResIcon)) {
                try {
                    int flags = Utilities.myUserHandle().equals(user) ? 0 :
                            PackageManager.GET_UNINSTALLED_PACKAGES;
                    PackageInfo info = mPackageManager.getPackageInfo(packageName, flags);
                    ApplicationInfo appInfo = info.applicationInfo;
                    if (appInfo == null) {
                        throw new NameNotFoundException("ApplicationInfo is null");
                    }

                    // Load the full res icon for the application, but if useLowResIcon is set, then
                    // only keep the low resolution icon instead of the larger full-sized icon
                    Bitmap icon = Utilities.createBadgedIconBitmap(
                            appInfo.loadIcon(mPackageManager), user, mContext);
                    Bitmap lowResIcon = generateLowResIcon(icon, mPackageBgColor);
                    entry.title = appInfo.loadLabel(mPackageManager);
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(entry.title, user);
                    entry.icon = useLowResIcon ? lowResIcon : icon;
                    entry.isLowResIcon = useLowResIcon;

                    // Add the icon in the DB here, since these do not get written during
                    // package updates.
                    ContentValues values =
                            newContentValues(icon, lowResIcon, entry.title.toString());
                    addIconToDB(values, cacheKey.componentName, info,
                            mUserManager.getSerialNumberForUser(user));

                } catch (NameNotFoundException e) {
                    entryUpdated = false;
                }
            }

            // Only add a filled-out entry to the cache
            if (entryUpdated) {
                mCache.put(cacheKey, entry);
            }
        }
        return entry;
    }

    private boolean getEntryFromDB(ComponentKey cacheKey, CacheEntry entry, boolean lowRes) {
        Cursor c = null;
        try {
            c = mIconDb.query(
                    new String[]{lowRes ? IconDB.COLUMN_ICON_LOW_RES : IconDB.COLUMN_ICON,
                            IconDB.COLUMN_LABEL},
                    IconDB.COLUMN_COMPONENT + " = ? AND " + IconDB.COLUMN_USER + " = ?",
                    new String[]{cacheKey.componentName.flattenToString(),
                            Long.toString(mUserManager.getSerialNumberForUser(cacheKey.user))});
            if (c.moveToNext()) {
                entry.icon = loadIconNoResize(c, 0, lowRes ? mLowResOptions : null);
                entry.isLowResIcon = lowRes;
                entry.title = c.getString(1);
                if (entry.title == null) {
                    entry.title = "";
                    entry.contentDescription = "";
                } else {
                    entry.contentDescription = mUserManager.getBadgedLabelForUser(
                            entry.title, cacheKey.user);
                }
                return true;
            }
        } catch (SQLiteException e) {
            Log.d(TAG, "Error reading icon cache", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }

    public static class IconLoadRequest {
        private final Runnable mRunnable;
        private final Handler mHandler;

        IconLoadRequest(Runnable runnable, Handler handler) {
            mRunnable = runnable;
            mHandler = handler;
        }

        public void cancel() {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    /**
     * A runnable that updates invalid icons and adds missing icons in the DB for the provided
     * LauncherActivityInfoCompat list. Items are updated/added one at a time, so that the
     * worker thread doesn't get blocked.
     */
    @Thunk
    class SerializedIconUpdateTask implements Runnable {
        private final long mUserSerial;
        private final HashMap<String, PackageInfo> mPkgInfoMap;
        private final Stack<LauncherActivityInfoCompat> mAppsToAdd;
        private final Stack<LauncherActivityInfoCompat> mAppsToUpdate;
        private final HashSet<String> mUpdatedPackages = new HashSet<>();

        @Thunk
        SerializedIconUpdateTask(long userSerial, HashMap<String, PackageInfo> pkgInfoMap,
                                 Stack<LauncherActivityInfoCompat> appsToAdd,
                                 Stack<LauncherActivityInfoCompat> appsToUpdate) {
            mUserSerial = userSerial;
            mPkgInfoMap = pkgInfoMap;
            mAppsToAdd = appsToAdd;
            mAppsToUpdate = appsToUpdate;
        }

        @Override
        public void run() {
            if (!mAppsToUpdate.isEmpty()) {
                LauncherActivityInfoCompat app = mAppsToUpdate.pop();
                String pkg = app.getComponentName().getPackageName();
                PackageInfo info = mPkgInfoMap.get(pkg);
                if (info != null) {
                    synchronized (IconCache.this) {
                        ContentValues values = updateCacheAndGetContentValues(app, true);
                        addIconToDB(values, app.getComponentName(), info, mUserSerial);
                    }
                    mUpdatedPackages.add(pkg);
                }
                if (mAppsToUpdate.isEmpty() && !mUpdatedPackages.isEmpty()) {
                    // No more app to update. Notify model.
                    LauncherAppState.getInstance().getModel().onPackageIconsUpdated(
                            mUpdatedPackages, mUserManager.getUserForSerialNumber(mUserSerial));
                }

                // Let it run one more time.
                scheduleNext();
            } else if (!mAppsToAdd.isEmpty()) {
                LauncherActivityInfoCompat app = mAppsToAdd.pop();
                PackageInfo info = mPkgInfoMap.get(app.getComponentName().getPackageName());
                if (info != null) {
                    synchronized (IconCache.this) {
                        addIconToDBAndMemCache(app, info, mUserSerial);
                    }
                }

                if (!mAppsToAdd.isEmpty()) {
                    scheduleNext();
                }
            }
        }

        public void scheduleNext() {
            mWorkerHandler.postAtTime(this, ICON_UPDATE_TOKEN, SystemClock.uptimeMillis() + 1);
        }
    }

    private static final class IconDB extends SQLiteCacheHelper {
        private final static int DB_VERSION = 10;

        private final static int RELEASE_VERSION = DB_VERSION + 1;

        private final static String TABLE_NAME = "icons";
        private final static String COLUMN_ROWID = "rowid";
        private final static String COLUMN_COMPONENT = "componentName";
        private final static String COLUMN_USER = "profileId";
        private final static String COLUMN_LAST_UPDATED = "lastUpdated";
        private final static String COLUMN_VERSION = "version";
        private final static String COLUMN_ICON = "icon";
        private final static String COLUMN_ICON_LOW_RES = "icon_low_res";
        private final static String COLUMN_LABEL = "label";
        private final static String COLUMN_SYSTEM_STATE = "system_state";

        public IconDB(Context context, int iconPixelSize) {
            super(context, LauncherFiles.APP_ICONS_DB,
                    (RELEASE_VERSION << 16) + iconPixelSize,
                    TABLE_NAME);
        }

        @Override
        protected void onCreateTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_COMPONENT + " TEXT NOT NULL, " +
                    COLUMN_USER + " INTEGER NOT NULL, " +
                    COLUMN_LAST_UPDATED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_VERSION + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_ICON + " BLOB, " +
                    COLUMN_ICON_LOW_RES + " BLOB, " +
                    COLUMN_LABEL + " TEXT, " +
                    COLUMN_SYSTEM_STATE + " TEXT, " +
                    "PRIMARY KEY (" + COLUMN_COMPONENT + ", " + COLUMN_USER + ") " +
                    ");");
        }
    }

    private ContentValues newContentValues(Bitmap icon, Bitmap lowResIcon, String label) {
        ContentValues values = new ContentValues();
        values.put(IconDB.COLUMN_ICON, Utilities.flattenBitmap(icon));
        values.put(IconDB.COLUMN_ICON_LOW_RES, Utilities.flattenBitmap(lowResIcon));

        values.put(IconDB.COLUMN_LABEL, label);
        values.put(IconDB.COLUMN_SYSTEM_STATE, Locale.getDefault().toString());

        return values;
    }

    /**
     * Generates a new low-res icon given a high-res icon.
     */
    private Bitmap generateLowResIcon(Bitmap icon, int lowResBackgroundColor) {
        int dstWidth = Math.max(1, icon.getWidth() / LOW_RES_SCALE_FACTOR);
        int dstHeight = Math.max(1, icon.getHeight() / LOW_RES_SCALE_FACTOR);
        if (lowResBackgroundColor == Color.TRANSPARENT) {
            return Bitmap.createScaledBitmap(icon, dstWidth, dstHeight, true);
        } else {
            Bitmap lowResIcon = Bitmap.createBitmap(dstWidth,
                    dstHeight, Bitmap.Config.RGB_565);
            synchronized (this) {
                mLowResCanvas.setBitmap(lowResIcon);
                mLowResCanvas.drawColor(lowResBackgroundColor);
                mLowResCanvas.drawBitmap(icon, new Rect(0, 0, icon.getWidth(), icon.getHeight()),
                        new Rect(0, 0, lowResIcon.getWidth(), lowResIcon.getHeight()),
                        mLowResPaint);
                mLowResCanvas.setBitmap(null);
            }
            return lowResIcon;
        }
    }

    private static Bitmap loadIconNoResize(Cursor c, int iconIndex, BitmapFactory.Options options) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception e) {
            return null;
        }
    }
}
