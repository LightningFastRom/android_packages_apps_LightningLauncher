package com.pixeldust.launcher.model;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.pixeldust.launcher.AppFilter;
import com.pixeldust.launcher.IconCache;
import com.pixeldust.launcher.InvariantDeviceProfile;
import com.pixeldust.launcher.LauncherAppState;
import com.pixeldust.launcher.LauncherAppWidgetProviderInfo;
import com.pixeldust.launcher.compat.AppWidgetManagerCompat;
import com.pixeldust.launcher.compat.LauncherAppsCompat;
import com.pixeldust.launcher.compat.ShortcutConfigActivityInfo;
import com.pixeldust.launcher.util.MultiHashMap;
import com.pixeldust.launcher.util.PackageUserKey;

public class WidgetsModel {
    private final AppFilter mAppFilter;
    private final IconCache mIconCache;
    private final MultiHashMap<PackageItemInfo, WidgetItem> mWidgetsList = new MultiHashMap<>();

    public WidgetsModel(IconCache iconCache, AppFilter appFilter) {
        this.mIconCache = iconCache;
        this.mAppFilter = appFilter;
    }

    public MultiHashMap<PackageItemInfo, WidgetItem> getWidgetsMap() {
        return this.mWidgetsList;
    }

    public boolean isEmpty() {
        return this.mWidgetsList.isEmpty();
    }

    public ArrayList<WidgetItem> update(Context context, PackageUserKey packageUserKey) {
        ArrayList<WidgetItem> arrayList = new ArrayList<>();
        try {
            PackageManager packageManager = context.getPackageManager();
            InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();
            for (AppWidgetProviderInfo fromProviderInfo : AppWidgetManagerCompat.getInstance(context).getAllProviders(/*packageUserKey*/)) {
                arrayList.add(new WidgetItem(LauncherAppWidgetProviderInfo.fromProviderInfo(fromProviderInfo), packageManager, idp));

            }
            for (ShortcutConfigActivityInfo widgetItem : LauncherAppsCompat.getInstance(context).getCustomShortcutActivityList(packageUserKey)) {
                arrayList.add(new WidgetItem(widgetItem));
            }
            setWidgetsAndShortcuts(arrayList, context, packageUserKey);
        } catch (Exception e) {
            /*if (!Utilities.isBinderSizeError(e)) {
                throw e;
            }*/
        }
        return arrayList;
    }

    private void setWidgetsAndShortcuts(ArrayList<WidgetItem> arrayList, Context context, PackageUserKey packageUserKey) {
        PackageItemInfo packageItemInfo = null;
        Iterator it;
        WidgetItem widgetItem;
        HashMap<String, PackageItemInfo> hashMap = new HashMap<>();
        if (packageUserKey == null) {
            this.mWidgetsList.clear();
        } else {
            for (PackageItemInfo packageItemInfo2 : this.mWidgetsList.keySet()) {
                if (packageItemInfo2.packageName.equals(packageUserKey.mPackageName)) {
                    packageItemInfo = packageItemInfo2;
                    break;
                }
            }
            if (packageItemInfo != null) {
                hashMap.put(packageItemInfo.packageName, packageItemInfo);
                it = ((ArrayList) this.mWidgetsList.get(packageItemInfo)).iterator();
                while (it.hasNext()) {
                    widgetItem = (WidgetItem) it.next();
                    if (widgetItem.componentName.getPackageName().equals(packageUserKey.mPackageName) && widgetItem.user.equals(packageUserKey.mUser)) {
                        it.remove();
                    }
                }
            }
        }
        InvariantDeviceProfile idp = LauncherAppState.getInstance().getInvariantDeviceProfile();
        UserHandle myUserHandle = Process.myUserHandle();
        for (WidgetItem widgetItem2 : arrayList) {
            if (widgetItem2.widgetInfo != null) {
                int min = Math.min(widgetItem2.widgetInfo.spanX, widgetItem2.widgetInfo.minSpanX);
                int min2 = Math.min(widgetItem2.widgetInfo.spanY, widgetItem2.widgetInfo.minSpanY);
                if (min <= idp.numColumns) {
                    if (min2 > idp.numRows) {
                    }
                }
            }
            if (this.mAppFilter.shouldShowApp(widgetItem2.componentName, context)) {
                String packageName = widgetItem2.componentName.getPackageName();
                PackageItemInfo obj = hashMap.get(packageName);
                if (obj == null) {
                    obj = new PackageItemInfo(packageName);
                    obj.user = widgetItem2.user;
                    hashMap.put(packageName, obj);
                } else if (!myUserHandle.equals(obj.user)) {
                    obj.user = widgetItem2.user;
                }
                this.mWidgetsList.addToList(obj, widgetItem2);
            }
        }
        for (PackageItemInfo packageItemInfo22 : hashMap.values()) {
            this.mIconCache.getTitleAndIconForApp(packageItemInfo22, true);
        }
    }
}