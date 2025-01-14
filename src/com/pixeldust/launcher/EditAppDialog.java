package com.pixeldust.launcher;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.pixeldust.launcher.compat.LauncherAppsCompat;


public class EditAppDialog extends Dialog {
    private static SharedPreferences sharedPrefs;
    private AppInfo info;
    private EditText title;
    private Switch visibility;
    private boolean visibleState;
    private Launcher launcher;

    public EditAppDialog(@NonNull Context context, AppInfo info, Launcher launcher) {
        super(context);
        this.info = info;
        this.launcher = launcher;
        sharedPrefs = Utilities.getPrefs(context.getApplicationContext());
        setCanceledOnTouchOutside(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_edit_dialog);

        final ComponentName component = info.componentName;

        title = ((EditText) findViewById(R.id.title));
        TextView packageName = ((TextView) findViewById(R.id.package_name));
        ImageView icon = ((ImageView) findViewById(R.id.icon));
        visibility = ((Switch) findViewById(R.id.visibility));
        ImageButton reset = ((ImageButton) findViewById(R.id.reset_title));

        icon.setImageBitmap(info.iconBitmap);
        title.setText(info.title);
        packageName.setText(component.getPackageName());
        visibleState = !Utilities.isAppHidden(getContext(), component.flattenToString());
        visibility.setChecked(visibleState);

        View.OnLongClickListener olcl = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    LauncherAppsCompat.getInstance(launcher).showAppDetailsForProfile(component, info.user);
                    return true;
                } catch (SecurityException | ActivityNotFoundException e) {
                    Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                    Log.e("EditAppDialog", "Unable to launch settings", e);
                }
                return false;
            }
        };
        icon.setOnLongClickListener(olcl);

        View.OnClickListener resetTitle = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                title.setText(info.originalTitle);
            }
        };
        reset.setOnClickListener(resetTitle);
    }

    @Override
    public void dismiss() {
        String key = info.componentName.flattenToString();
        if (visibility.isChecked() != visibleState) {
            Utilities.setAppVisibility(getContext(), key, visibility.isChecked());
        }
        String titleS = title.getText().toString();
        if (!titleS.trim().equals(info.title.toString().trim())) {
            info.title = titleS.trim();
            sharedPrefs.edit().putString("alias_" + key, titleS).apply();
        }
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app != null) {
            app.reloadAll(false);
        }
        super.dismiss();
    }
}
