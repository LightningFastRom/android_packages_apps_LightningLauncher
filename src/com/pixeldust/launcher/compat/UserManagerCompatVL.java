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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.pixeldust.launcher.Utilities;
import com.pixeldust.launcher.util.LongArrayMap;

public class UserManagerCompatVL extends UserManagerCompat {
    private static final String USER_CREATION_TIME_KEY = "user_creation_time_";

    protected LongArrayMap<UserHandle> mUsers;
    protected HashMap<UserHandle, Long> mUserToSerialMap;
    protected UserManager mUserManager;
    private final PackageManager mPm;
    private final Context mContext;

    UserManagerCompatVL(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mPm = context.getPackageManager();
        mContext = context;
    }

    @Override
    public void enableAndResetCache() {
        synchronized (this) {
            mUsers = new LongArrayMap<>();
            mUserToSerialMap = new HashMap<>();
            List<UserHandle> users = mUserManager.getUserProfiles();
            if (users != null) {
                for (UserHandle user : users) {
                    long serial = mUserManager.getSerialNumberForUser(user);
                    UserHandle userCompat = user;
                    mUsers.put(serial, userCompat);
                    mUserToSerialMap.put(userCompat, serial);
                }
            }
        }
    }

    @Override
    public List<UserHandle> getUserProfiles() {
        synchronized (this) {
            if (mUsers != null) {
                List<UserHandle> users = new ArrayList<>();
                users.addAll(mUserToSerialMap.keySet());
                return users;
            }
        }

        List<UserHandle> users = mUserManager.getUserProfiles();
        if (users == null) {
            return Collections.emptyList();
        }
        ArrayList<UserHandle> compatUsers = new ArrayList<>(
                users.size());
        for (UserHandle user : users) {
            compatUsers.add(user);
        }
        return compatUsers;
    }

    @Override
    public CharSequence getBadgedLabelForUser(CharSequence label, UserHandle user) {
        if (user == null) {
            return label;
        }
        return mPm.getUserBadgedLabel(label, user);
    }

    @Override
    public long getUserCreationTime(UserHandle user) {
        SharedPreferences prefs = Utilities.getPrefs(mContext);
        String key = USER_CREATION_TIME_KEY + getSerialNumberForUser(user);
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, System.currentTimeMillis()).apply();
        }
        return prefs.getLong(key, 0);
    }

    @Override
    public boolean isQuietModeEnabled(UserHandle user) {
        return false;
    }

    public UserHandle getUserForSerialNumber(long serialNumber) {
        synchronized (this) {
            if (mUsers != null) {
                return mUsers.get(serialNumber);
            }
        }
        return mUserManager.getUserForSerialNumber(serialNumber);
    }

    @Override
    public boolean isUserUnlocked(UserHandle user) {
        return true;
    }

    @Override
    public boolean isDemoUser() {
        return false;
    }

    public long getSerialNumberForUser(UserHandle user) {
        synchronized (this) {
            if (mUserToSerialMap != null) {
                Long serial = mUserToSerialMap.get(user);
                return serial == null ? 0 : serial;
            }
        }
        return mUserManager.getSerialNumberForUser(user);
    }
}

