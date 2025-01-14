/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * View that draws a bitmap horizontally centered. If the image width is greater than the view
 * width, the image is scaled down appropriately.
 */
public class WidgetImageView extends View {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF mDstRectF = new RectF();
    private Bitmap mBitmap;

    public WidgetImageView(Context context) {
        super(context);
    }

    public WidgetImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WidgetImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        invalidate();
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            updateDstRectF();
            canvas.drawBitmap(mBitmap, null, mDstRectF, mPaint);
        }
    }

    /**
     * Prevents the inefficient alpha view rendering.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void updateDstRectF() {
        float width2 = (float) mBitmap.getWidth();
        float f = width2 > (float) getWidth() ? (float) getWidth() / width2 : 1.0f;
        width2 *= f;
        f *= (float) this.mBitmap.getHeight();
        this.mDstRectF.left = ((float) getWidth() - width2) / 2.0f;
        this.mDstRectF.right = ((float) getWidth() + width2) / 2.0f;
        if (f > (float) getHeight()) {
            this.mDstRectF.top = 0.0f;
            this.mDstRectF.bottom = f;
        } else {
            this.mDstRectF.top = ((float) getHeight() - f) / 2.0f;
            this.mDstRectF.bottom = (f + (float) getHeight()) / 2.0f;
        }
    }

    /**
     * @return the bounds where the image was drawn.
     */
    public Rect getBitmapBounds() {
        updateDstRectF();
        Rect rect = new Rect();
        mDstRectF.round(rect);
        return rect;
    }
}
