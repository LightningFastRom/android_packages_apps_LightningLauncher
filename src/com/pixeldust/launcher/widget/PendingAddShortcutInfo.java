/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.pixeldust.launcher.widget;

import com.pixeldust.launcher.LauncherSettings;
import com.pixeldust.launcher.PendingAddItemInfo;
import com.pixeldust.launcher.compat.ShortcutConfigActivityInfo;

/**
 * Meta data used for late binding of the short cuts.
 *
 * @see {@link PendingAddItemInfo}
 */
public class PendingAddShortcutInfo extends PendingAddItemInfo {

    ShortcutConfigActivityInfo activityInfo;

    public PendingAddShortcutInfo(ShortcutConfigActivityInfo activityInfo) {
        this.activityInfo = activityInfo;
        componentName = activityInfo.getComponent();
        user = activityInfo.getUser();
        itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }
}
