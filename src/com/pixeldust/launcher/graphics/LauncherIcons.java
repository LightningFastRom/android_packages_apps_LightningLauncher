package com.pixeldust.launcher.graphics;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.os.Process;
import android.os.UserHandle;

import com.pixeldust.launcher.AppInfo;
import com.pixeldust.launcher.IconCache;
import com.pixeldust.launcher.LauncherAppState;
import com.pixeldust.launcher.R;
import com.pixeldust.launcher.Utilities;
import com.pixeldust.launcher.compat.LauncherActivityInfoCompat;
import com.pixeldust.launcher.model.PackageItemInfo;
import com.pixeldust.launcher.shortcuts.DeepShortcutManager;
import com.pixeldust.launcher.shortcuts.ShortcutInfoCompat;
import com.pixeldust.launcher.util.IconNormalizer;

public class LauncherIcons {
    private static final Canvas sCanvas = new Canvas();
    private static final Rect sOldBounds = new Rect();

    static class FixedSizeBitmapDrawable extends BitmapDrawable {
        public FixedSizeBitmapDrawable(Bitmap bitmap) {
            super(null, bitmap);
        }

        @Override
        public int getIntrinsicHeight() {
            return getBitmap().getWidth();
        }

        @Override
        public int getIntrinsicWidth() {
            return getBitmap().getWidth();
        }
    }

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(4, 2));
    }

    public static Bitmap createIconBitmap(ShortcutIconResource shortcutIconResource, Context context) {
        try {
            Resources resourcesForApplication = context.getPackageManager().getResourcesForApplication(shortcutIconResource.packageName);
            if (resourcesForApplication != null) {
                return createIconBitmap(resourcesForApplication.getDrawableForDensity(resourcesForApplication.getIdentifier(shortcutIconResource.resourceName, null, null), LauncherAppState.getInstance().getInvariantDeviceProfile().fillResIconDpi), context);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static Bitmap createIconBitmap(Bitmap bitmap, Context context) {
        int i = LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
        if (i == bitmap.getWidth() && i == bitmap.getHeight()) {
            return bitmap;
        }
        return createIconBitmap(new BitmapDrawable(context.getResources(), bitmap), context);
    }

    public static Bitmap createBadgedIconBitmap(Drawable drawable, UserHandle userHandle, Context context, int i) {
        float f = 1.0f;
        IconNormalizer instance = IconNormalizer.getInstance();
        if (!Utilities.isAtLeastO() || i < 26) {
            f = instance.getScale(drawable, null);
        } /*else {
            boolean[] zArr = new boolean[1];
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
            adaptiveIconDrawable.setBounds(0, 0, 1, 1);
            f = instance.getScale(drawable, null, adaptiveIconDrawable.getIconMask(), zArr);
            if ((zArr[0] ^ 1) != 0) {
                Drawable wrapToAdaptiveIconDrawable = wrapToAdaptiveIconDrawable(context, drawable, f);
                if (wrapToAdaptiveIconDrawable != drawable) {
                    f = instance.getScale(wrapToAdaptiveIconDrawable, null, null, null);
                    drawable = wrapToAdaptiveIconDrawable;
                }
            }
        }*/
        Bitmap createIconBitmap = createIconBitmap(drawable, context, f);
        if (Utilities.isAtLeastO() /*&& (drawable instanceof AdaptiveIconDrawable)*/) {
            createIconBitmap = ShadowGenerator.getInstance().recreateIcon(createIconBitmap);
        }
        return badgeIconForUser(createIconBitmap, userHandle, context);
    }

    public static Bitmap badgeIconForUser(Bitmap bitmap, UserHandle userHandle, Context context) {
        if (userHandle == null || Process.myUserHandle().equals(userHandle)) {
            return bitmap;
        }
        Drawable userBadgedIcon = context.getPackageManager().getUserBadgedIcon(new FixedSizeBitmapDrawable(bitmap), userHandle);
        if (userBadgedIcon instanceof BitmapDrawable) {
            return ((BitmapDrawable) userBadgedIcon).getBitmap();
        }
        return createIconBitmap(userBadgedIcon, context);
    }

    public static Bitmap createScaledBitmapWithoutShadow(Drawable drawable, Context context, int i) {
        RectF rectF = new RectF();
        float f = 1.0f;
        IconNormalizer instance = IconNormalizer.getInstance();
        if (!Utilities.isAtLeastO() || i < 26) {
            f = instance.getScale(drawable, rectF);
        } /*else {
                boolean[] zArr = new boolean[1];
                AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
                adaptiveIconDrawable.setBounds(0, 0, 1, 1);
                f = instance.getScale(drawable, rectF, adaptiveIconDrawable.getIconMask(), zArr);
                if (Utilities.isAtLeastO() && (zArr[0] ^ 1) != 0) {
                    Drawable wrapToAdaptiveIconDrawable = wrapToAdaptiveIconDrawable(context, drawable, f);
                    if (wrapToAdaptiveIconDrawable != drawable) {
                        f = instance.getScale(wrapToAdaptiveIconDrawable, rectF, null, null);
                        drawable = wrapToAdaptiveIconDrawable;
                    }
                }
            }*/
        return createIconBitmap(drawable, context, Math.min(f, ShadowGenerator.getScaleForBounds(rectF)));
    }

    public static Bitmap addShadowToIcon(Bitmap bitmap, Context context) {
        return ShadowGenerator.getInstance().recreateIcon(bitmap);
    }

    public static Bitmap badgeWithBitmap(Bitmap bitmap, Bitmap bitmap2, Context context) {
        int dimensionPixelSize = context.getResources().getDimensionPixelSize(R.dimen.profile_badge_size);
        synchronized (sCanvas) {
            sCanvas.setBitmap(bitmap);
            sCanvas.drawBitmap(bitmap2, new Rect(0, 0, bitmap2.getWidth(), bitmap2.getHeight()), new Rect(bitmap.getWidth() - dimensionPixelSize, bitmap.getHeight() - dimensionPixelSize, bitmap.getWidth(), bitmap.getHeight()), new Paint(2));
            sCanvas.setBitmap(null);
        }
        return bitmap;
    }

    public static Bitmap createIconBitmap(Drawable drawable, Context context) {
        float f = 1.0f;
        /*if (Utilities.isAtLeastO() && (drawable instanceof AdaptiveIconDrawable)) {
            f = ShadowGenerator.getScaleForBounds(new RectF(0.0f, 0.0f, 0.0f, 0.0f));
        }*/
        Bitmap createIconBitmap = createIconBitmap(drawable, context, f);
        /*if (Utilities.isAtLeastO() && (drawable instanceof AdaptiveIconDrawable)) {
            return ShadowGenerator.getInstance(context).recreateIcon(createIconBitmap);
        }*/
        return createIconBitmap;
    }

    public static Bitmap createIconBitmap(Drawable drawable, Context context, float f) {
        Bitmap createBitmap;
        synchronized (sCanvas) {
            int i = LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
            if (drawable instanceof PaintDrawable) {
                PaintDrawable paintDrawable = (PaintDrawable) drawable;
                paintDrawable.setIntrinsicWidth(i);
                paintDrawable.setIntrinsicHeight(i);
            } else if (drawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap != null && bitmap.getDensity() == 0) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                intrinsicWidth = i;
                intrinsicHeight = i;
            } else {
                float f2 = ((float) intrinsicWidth) / ((float) intrinsicHeight);
                if (intrinsicWidth > intrinsicHeight) {
                    intrinsicWidth = (int) (((float) i) / f2);
                    intrinsicHeight = i;
                } else if (intrinsicHeight > intrinsicWidth) {
                    intrinsicHeight = (int) (((float) i) * f2);
                    intrinsicWidth = i;
                } else {
                    intrinsicWidth = i;
                    intrinsicHeight = i;
                }
            }
            createBitmap = Bitmap.createBitmap(i, i, Config.ARGB_8888);
            Canvas canvas = sCanvas;
            canvas.setBitmap(createBitmap);
            int i2 = (i - intrinsicHeight) / 2;
            int i3 = (i - intrinsicWidth) / 2;
            sOldBounds.set(drawable.getBounds());
            if (Utilities.isAtLeastO() /*&& (drawable instanceof AdaptiveIconDrawable)*/) {
                i2 = Math.min(i2, i3);
                intrinsicWidth = Math.max(intrinsicHeight, intrinsicWidth);
                drawable.setBounds(i2, i2, i2 + intrinsicWidth, intrinsicWidth + i2);
            } else {
                drawable.setBounds(i2, i3, intrinsicHeight + i2, intrinsicWidth + i3);
            }
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.scale(f, f, (float) (i / 2), (float) (i / 2));
            drawable.draw(canvas);
            canvas.restore();
            drawable.setBounds(sOldBounds);
            canvas.setBitmap(null);
        }
        return createBitmap;
    }

    static Drawable wrapToAdaptiveIconDrawable(Context context, Drawable drawable, float f) {
        //if (!Utilities.isAtLeastO()) {
        return drawable;
        //}
        /*try {
            if (drawable instanceof AdaptiveIconDrawable) {
                return drawable;
            }
            AdaptiveIconDrawable adaptiveIconDrawable = (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper).mutate();
            FixedScaleDrawable fixedScaleDrawable = (FixedScaleDrawable) adaptiveIconDrawable.getForeground();
            fixedScaleDrawable.setDrawable(drawable);
            fixedScaleDrawable.setScale(f);
            return adaptiveIconDrawable;
        } catch (Exception e) {
            return drawable;
        }*/
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfoCompat, Context context) {
        return createShortcutIcon(shortcutInfoCompat, context, true);
    }

    public static Bitmap createShortcutIcon(ShortcutInfoCompat shortcutInfoCompat, Context context, boolean z) {
        Bitmap defaultIcon;
        LauncherAppState instance = LauncherAppState.getInstance();
        Drawable shortcutIconDrawable = DeepShortcutManager.getInstance(context).getShortcutIconDrawable(shortcutInfoCompat, instance.getInvariantDeviceProfile().fillResIconDpi);
        IconCache iconCache = instance.getIconCache();
        if (shortcutIconDrawable == null) {
            defaultIcon = iconCache.getDefaultIcon(Process.myUserHandle());
        } else {
            defaultIcon = createScaledBitmapWithoutShadow(shortcutIconDrawable, context, 26);
        }
        if (!z) {
            return defaultIcon;
        }
        Bitmap addShadowToIcon = addShadowToIcon(defaultIcon, context);
        ComponentName activity = shortcutInfoCompat.getActivity();
        if (activity != null) {
            AppInfo appInfo = new AppInfo();
            appInfo.user = shortcutInfoCompat.getUserHandle();
            appInfo.componentName = activity;
            appInfo.intent = new Intent("android.intent.action.MAIN").addCategory("android.intent.category.LAUNCHER").setComponent(activity);
            LauncherActivityInfoCompat lac = LauncherActivityInfoCompat.create(context, appInfo.user, appInfo.intent);
            iconCache.getTitleAndIcon(appInfo, lac, false);
            defaultIcon = appInfo.iconBitmap;
        } else {
            PackageItemInfo packageItemInfo = new PackageItemInfo(shortcutInfoCompat.getPackage());
            iconCache.getTitleAndIconForApp(packageItemInfo, false);
            defaultIcon = packageItemInfo.iconBitmap;
        }
        return badgeWithBitmap(addShadowToIcon, defaultIcon, context);
    }
}