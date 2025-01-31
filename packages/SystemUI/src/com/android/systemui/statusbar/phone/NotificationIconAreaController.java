package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v4.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.NotificationColorUtil;
import com.android.systemui.R;
import com.android.systemui.statusbar.ExpandableNotificationRow;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationShelf;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.notification.NotificationUtils;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.statusbar.policy.DarkIconDispatcher;
import com.android.systemui.statusbar.policy.DarkIconDispatcher.DarkReceiver;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * A controller for the space in the status bar to the left of the system icons. This area is
 * normally reserved for notifications.
 */
public class NotificationIconAreaController implements DarkReceiver {
    private final NotificationColorUtil mNotificationColorUtil;

    private int mIconSize;
    private int mIconHPadding;
    private int mIconTint = Color.WHITE;

    private StatusBar mStatusBar;
    protected View mNotificationIconArea;
    private NotificationIconContainer mNotificationIcons;
    private NotificationIconContainer mShelfIcons;
    private final Rect mTintArea = new Rect();
    private NotificationStackScrollLayout mNotificationScrollLayout;
    private Context mContext;

    private int mClockAndDateWidth;
    private boolean mCenterClock;

    public NotificationIconAreaController(Context context, StatusBar statusBar) {
        mStatusBar = statusBar;
        mNotificationColorUtil = NotificationColorUtil.getInstance(context);
        mContext = context;

        initializeNotificationAreaViews(context);
    }

    protected View inflateIconArea(LayoutInflater inflater) {
        return inflater.inflate(R.layout.notification_icon_area, null);
    }

    /**
     * Initializes the views that will represent the notification area.
     */
    protected void initializeNotificationAreaViews(Context context) {
        reloadDimens(context);

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        mNotificationIconArea = inflateIconArea(layoutInflater);
        mNotificationIcons = (NotificationIconContainer) mNotificationIconArea.findViewById(
                R.id.notificationIcons);

        mNotificationScrollLayout = mStatusBar.getNotificationScrollLayout();
    }

    public void setupShelf(NotificationShelf shelf) {
        mShelfIcons = shelf.getShelfIcons();
        shelf.setCollapsedIcons(mNotificationIcons);
    }

    public void onDensityOrFontScaleChanged(Context context) {
        reloadDimens(context);
        final FrameLayout.LayoutParams params = generateIconLayoutParams();
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            View child = mNotificationIcons.getChildAt(i);
            child.setLayoutParams(params);
        }
        for (int i = 0; i < mShelfIcons.getChildCount(); i++) {
            View child = mShelfIcons.getChildAt(i);
            child.setLayoutParams(params);
        }
    }

    public void setClockAndDateStatus(int width, int mode, boolean enabled) {
        if (mNotificationIcons != null) {
            mNotificationIcons.setClockAndDateStatus(width, mode, enabled);
        }
        mClockAndDateWidth = width;
        mCenterClock = mode == Clock.STYLE_CLOCK_CENTER && enabled;
    }

    private int getFullIconWidth() {
        return mIconSize + 2 * mIconHPadding;
    }

    @NonNull
    private FrameLayout.LayoutParams generateIconLayoutParams() {
        final int totalWidth = mContext.getResources().getDisplayMetrics().widthPixels;
        int usableWidth = (totalWidth - mClockAndDateWidth - 2 * getFullIconWidth()) / 2;
        if (mCenterClock) {
            return new FrameLayout.LayoutParams(
                    usableWidth, getHeight());
        } else {
            return new FrameLayout.LayoutParams(
                    getFullIconWidth(), getHeight());
        }
    }

    private void reloadDimens(Context context) {
        Resources res = context.getResources();
        mIconSize = res.getDimensionPixelSize(com.android.internal.R.dimen.status_bar_icon_size);
        mIconHPadding = res.getDimensionPixelSize(R.dimen.status_bar_icon_padding);
    }

    /**
     * Returns the view that represents the notification area.
     */
    public View getNotificationInnerAreaView() {
        return mNotificationIconArea;
    }

    /**
     * See {@link com.android.systemui.statusbar.policy.DarkIconDispatcher#setIconsDarkArea}.
     * Sets the color that should be used to tint any icons in the notification area.
     *
     * @param tintArea the area in which to tint the icons, specified in screen coordinates
     * @param darkIntensity
     */
    public void onDarkChanged(Rect tintArea, float darkIntensity, int iconTint) {
        if (tintArea == null) {
            mTintArea.setEmpty();
        } else {
            mTintArea.set(tintArea);
        }
        mIconTint = iconTint;
        applyNotificationIconsTint();
    }

    protected int getHeight() {
        return mStatusBar.getStatusBarHeight();
    }

    protected boolean shouldShowNotificationIcon(NotificationData.Entry entry,
            NotificationData notificationData, boolean showAmbient) {
        if (notificationData.isAmbient(entry.key) && !showAmbient) {
            return false;
        }
        if (!StatusBar.isTopLevelChild(entry)) {
            return false;
        }
        if (entry.row.getVisibility() == View.GONE) {
            return false;
        }

        return true;
    }

    /**
     * Updates the notifications with the given list of notifications to display.
     */
    public void updateNotificationIcons(NotificationData notificationData) {

        updateIconsForLayout(notificationData, entry -> entry.icon, mNotificationIcons,
                false /* showAmbient */);
        updateIconsForLayout(notificationData, entry -> entry.expandedIcon, mShelfIcons,
                NotificationShelf.SHOW_AMBIENT_ICONS);

        applyNotificationIconsTint();
    }

    /**
     * Updates the notification icons for a host layout. This will ensure that the notification
     * host layout will have the same icons like the ones in here.
     *
     * @param notificationData the notification data to look up which notifications are relevant
     * @param function A function to look up an icon view based on an entry
     * @param hostLayout which layout should be updated
     * @param showAmbient should ambient notification icons be shown
     */
    private void updateIconsForLayout(NotificationData notificationData,
            Function<NotificationData.Entry, StatusBarIconView> function,
            NotificationIconContainer hostLayout, boolean showAmbient) {
        ArrayList<StatusBarIconView> toShow = new ArrayList<>(
                mNotificationScrollLayout.getChildCount());

        // Filter out ambient notifications and notification children.
        for (int i = 0; i < mNotificationScrollLayout.getChildCount(); i++) {
            View view = mNotificationScrollLayout.getChildAt(i);
            if (view instanceof ExpandableNotificationRow) {
                NotificationData.Entry ent = ((ExpandableNotificationRow) view).getEntry();
                if (shouldShowNotificationIcon(ent, notificationData, showAmbient)) {
                    toShow.add(function.apply(ent));
                }
            }
        }

        // In case we are changing the suppression of a group, the replacement shouldn't flicker
        // and it should just be replaced instead. We therefore look for notifications that were
        // just replaced by the child or vice-versa to suppress this.

        ArrayMap<String, ArrayList<StatusBarIcon>> replacingIcons = new ArrayMap<>();
        ArrayList<View> toRemove = new ArrayList<>();
        for (int i = 0; i < hostLayout.getChildCount(); i++) {
            View child = hostLayout.getChildAt(i);
            if (!(child instanceof StatusBarIconView)) {
                continue;
            }
            if (!toShow.contains(child)) {
                boolean iconWasReplaced = false;
                StatusBarIconView removedIcon = (StatusBarIconView) child;
                String removedGroupKey = removedIcon.getNotification().getGroupKey();
                for (int j = 0; j < toShow.size(); j++) {
                    StatusBarIconView candidate = toShow.get(j);
                    if (candidate.getSourceIcon().sameAs((removedIcon.getSourceIcon()))
                            && candidate.getNotification().getGroupKey().equals(removedGroupKey)) {
                        if (!iconWasReplaced) {
                            iconWasReplaced = true;
                        } else {
                            iconWasReplaced = false;
                            break;
                        }
                    }
                }
                if (iconWasReplaced) {
                    ArrayList<StatusBarIcon> statusBarIcons = replacingIcons.get(removedGroupKey);
                    if (statusBarIcons == null) {
                        statusBarIcons = new ArrayList<>();
                        replacingIcons.put(removedGroupKey, statusBarIcons);
                    }
                    statusBarIcons.add(removedIcon.getStatusBarIcon());
                }
                toRemove.add(removedIcon);
            }
        }
        // removing all duplicates
        ArrayList<String> duplicates = new ArrayList<>();
        for (String key : replacingIcons.keySet()) {
            ArrayList<StatusBarIcon> statusBarIcons = replacingIcons.get(key);
            if (statusBarIcons.size() != 1) {
                duplicates.add(key);
            }
        }
        replacingIcons.removeAll(duplicates);
        hostLayout.setReplacingIcons(replacingIcons);

        final int toRemoveCount = toRemove.size();
        for (int i = 0; i < toRemoveCount; i++) {
            hostLayout.removeView(toRemove.get(i));
        }

        final FrameLayout.LayoutParams params = generateIconLayoutParams();
        for (int i = 0; i < toShow.size(); i++) {
            View v = toShow.get(i);
            // The view might still be transiently added if it was just removed and added again
            hostLayout.removeTransientView(v);
            if (v.getParent() == null) {
                hostLayout.addView(v, i, params);
            }
        }

        hostLayout.setChangingViewPositions(true);
        // Re-sort notification icons
        final int childCount = hostLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View actual = hostLayout.getChildAt(i);
            StatusBarIconView expected = toShow.get(i);
            if (actual == expected) {
                continue;
            }
            hostLayout.removeView(expected);
            hostLayout.addView(expected, i);
        }
        hostLayout.setChangingViewPositions(false);
        hostLayout.setReplacingIcons(null);
    }

    /**
     * Applies {@link #mIconTint} to the notification icons.
     */
    private void applyNotificationIconsTint() {
        for (int i = 0; i < mNotificationIcons.getChildCount(); i++) {
            final StatusBarIconView iv = (StatusBarIconView) mNotificationIcons.getChildAt(i);
            if (iv.getWidth() != 0) {
                updateTintForIcon(iv);
            } else {
                iv.executeOnLayout(() -> updateTintForIcon(iv));
            }
        }
    }

    private void updateTintForIcon(StatusBarIconView v) {
        boolean isPreL = Boolean.TRUE.equals(v.getTag(R.id.icon_is_pre_L));
        int color = StatusBarIconView.NO_COLOR;
        boolean colorize = !isPreL || NotificationUtils.isGrayscale(v, mNotificationColorUtil);
        if (colorize) {
            color = DarkIconDispatcher.getTint(mTintArea, v, mIconTint);
        }
        v.setStaticDrawableColor(color);
        v.setDecorColor(mIconTint);
    }

    public void setDark(boolean dark) {
        mNotificationIcons.setDark(dark, false, 0);
        mShelfIcons.setDark(dark, false, 0);
    }
}
