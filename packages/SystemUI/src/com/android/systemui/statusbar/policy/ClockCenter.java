/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import com.android.systemui.Dependency;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;

public class ClockCenter extends Clock {

    public ClockCenter(Context context) {
        this(context, null);
    }

    public ClockCenter(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClockCenter(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onDarkChanged(Rect area, float darkIntensity, int tint) {
        setTextColor(DarkIconDispatcher.getTint(area, this, tint));
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        FontSizeUtils.updateFontSize(this, R.dimen.status_bar_clock_size);
        setPaddingRelative(
                mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_clock_starting_padding),
                0,
                mContext.getResources().getDimensionPixelSize(
                        R.dimen.status_bar_clock_end_padding),
                0);
    }

    @Override
    protected void updateClockVisibility() {
        boolean visible = mClockVisibleByPolicy && mClockVisibleByUser;
        Dependency.get(IconLogger.class).onIconVisibility("clock", visible);
        int visibility = visible ? (mShowClock ? View.VISIBLE : View.GONE) : View.GONE;
        if (mClockStyle == STYLE_CLOCK_CENTER) {
            setVisibility(visibility);
        } else {
            setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSizeChanged(int xNew, int yNew, int xOld, int yOld){
        super.onSizeChanged(xNew, yNew, xOld, yOld);
        mClockAndDateWidth = xNew;
        if (mNotificationIconAreaController != null) {
            mNotificationIconAreaController.setClockAndDateStatus(mClockAndDateWidth,
                    mClockStyle, mShowClock);
        }
    }
}
