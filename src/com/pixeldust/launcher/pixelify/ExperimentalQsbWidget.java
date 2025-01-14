package com.pixeldust.launcher.pixelify;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.pixeldust.launcher.DeviceProfile;
import com.pixeldust.launcher.LauncherRootView;
import com.pixeldust.launcher.R;
import com.pixeldust.launcher.blur.BlurDrawable;
import com.pixeldust.launcher.blur.BlurWallpaperProvider;
import com.pixeldust.launcher.config.FeatureFlags;

public class ExperimentalQsbWidget extends BaseQsbView {
    private final boolean mBlurEnabled;
    private BlurDrawable mBlurDrawable;
    private int mLeft;
    private int mTop;
    private int mBlurTranslationX;
    private int mBlurTranslationY;
    private Runnable mUpdatePosition = new Runnable() {
        @Override
        public void run() {
            int left = 0, top = 0;
            View view = mQsbView;
            while (!(view instanceof LauncherRootView)) {
                left += view.getLeft();
                top += view.getTop();
                view = (View) view.getParent();
            }
            mLeft = left;
            mTop = top;
            updateBlur();
        }
    };

    public ExperimentalQsbWidget(Context context) {
        this(context, null);
    }

    public ExperimentalQsbWidget(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ExperimentalQsbWidget(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);

        mBlurEnabled = BlurWallpaperProvider.isEnabled();
        if (mBlurEnabled) {
            mBlurDrawable = mLauncher.getBlurWallpaperProvider().createDrawable(100, true);
        }
    }

    @Override
    protected int getQsbView(boolean withMic) {
        return withMic ? R.layout.qsb_wide_with_mic : R.layout.qsb_wide_without_mic;
    }

    @Override
    protected void setupViews() {
        super.onFinishInflate();
        if (mBlurEnabled) {
            mQsbView.setBackground(mBlurDrawable);
            mQsbView.setLayerType(LAYER_TYPE_SOFTWARE, null);
            if (FeatureFlags.useWhiteGoogleIcon(getContext())) {
                ((ImageView) findViewById(R.id.g_icon)).setColorFilter(Color.WHITE);
                if (FeatureFlags.showVoiceSearchButton(getContext())) {
                    ((ImageView) findViewById(R.id.mic_icon)).setColorFilter(Color.WHITE);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (this.mQsbView != null) {
            DeviceProfile deviceProfile = this.mLauncher.getDeviceProfile();
            int size = MeasureSpec.getSize(i);
            ((LayoutParams) this.mQsbView.getLayoutParams()).width = size - (DeviceProfile.calculateCellWidth(size, deviceProfile.inv.numColumns) - deviceProfile.iconSizePx);
        }
        super.onMeasure(i, i2);
    }

    @Override
    protected void aL(Rect rect, Intent intent) {
        if (!this.showMic) {
            intent.putExtra("source_mic_alpha", 0.0f);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!mBlurEnabled) return;
        post(mUpdatePosition);
    }

    private void updateBlur() {
        if (!mBlurEnabled) return;
        mBlurDrawable.setTranslation(mTop - mBlurTranslationY);
        mBlurDrawable.setOverscroll(mLeft - mBlurTranslationX);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBlurEnabled)
            mBlurDrawable.startListening();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBlurEnabled)
            mBlurDrawable.stopListening();
    }

    public void translateBlurX(int translationX) {
        if (!mBlurEnabled) return;
        mBlurTranslationX = translationX;
        updateBlur();
    }

    public void translateBlurY(int translationY) {
        if (!mBlurEnabled) return;
        mBlurTranslationY = translationY;
        updateBlur();
    }
}