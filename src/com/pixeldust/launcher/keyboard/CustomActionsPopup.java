package com.pixeldust.launcher.keyboard;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pixeldust.launcher.ItemInfo;
import com.pixeldust.launcher.Launcher;
import com.pixeldust.launcher.accessibility.LauncherAccessibilityDelegate;
import com.pixeldust.launcher.popup.PopupContainerWithArrow;

public class CustomActionsPopup implements OnMenuItemClickListener {
    private final LauncherAccessibilityDelegate mDelegate;
    private final View mIcon;
    private final Launcher mLauncher;

    public CustomActionsPopup(Launcher launcher, View view) {
        this.mLauncher = launcher;
        this.mIcon = view;
        PopupContainerWithArrow open = PopupContainerWithArrow.getOpen(launcher);
        if (open != null) {
            this.mDelegate = open.getAccessibilityDelegate();
        } else {
            this.mDelegate = launcher.getAccessibilityDelegate();
        }
    }

    private List getActionList() {
        if (this.mIcon == null || (!(this.mIcon.getTag() instanceof ItemInfo))) {
            return Collections.EMPTY_LIST;
        }
        AccessibilityNodeInfo obtain = AccessibilityNodeInfo.obtain();
        //this.mDelegate.addSupportedActions(this.mIcon, obtain, true);
        List arrayList = new ArrayList(obtain.getActionList());
        obtain.recycle();
        return arrayList;
    }

    public boolean canShow() {
        return !getActionList().isEmpty();
    }

    public boolean show() {
        List<AccessibilityAction> actionList = getActionList();
        if (actionList.isEmpty()) {
            return false;
        }
        PopupMenu popupMenu = new PopupMenu(this.mLauncher, this.mIcon);
        popupMenu.setOnMenuItemClickListener(this);
        Menu menu = popupMenu.getMenu();
        for (AccessibilityAction accessibilityAction : actionList) {
            menu.add(0, accessibilityAction.getId(), 0, accessibilityAction.getLabel());
        }
        popupMenu.show();
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        return this.mDelegate.performAction(this.mIcon, (ItemInfo) this.mIcon.getTag(), menuItem.getItemId());
    }
}