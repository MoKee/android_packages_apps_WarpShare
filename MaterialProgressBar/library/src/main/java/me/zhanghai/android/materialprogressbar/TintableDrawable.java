/*
 * Copyright (c) 2015 Zhang Hai <Dreaming.in.Code.ZH@Gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.materialprogressbar;

import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.TintAwareDrawable;

/**
 * A {@code Drawable} that is tintable.
 */
public interface TintableDrawable extends TintAwareDrawable {

    /**
     * Specifies tint color for this drawable.
     * <p>
     * A Drawable's drawing content will be blended together with its tint
     * before it is drawn to the screen. This functions similarly to
     * {@link Drawable#setColorFilter(int, PorterDuff.Mode)}.
     * </p>
     * <p>
     * To clear the tint, pass {@code null} to
     * {@link #setTintList(ColorStateList)}.
     * </p>
     * <p class="note"><strong>Note:</strong> Setting a color filter via
     * {@link Drawable#setColorFilter(ColorFilter)} or
     * {@link Drawable#setColorFilter(int, PorterDuff.Mode)} overrides tint.
     * </p>
     *
     * @param tintColor Color to use for tinting this drawable
     * @see #setTintList(ColorStateList)
     * @see #setTintMode(PorterDuff.Mode)
     */
    void setTint(@ColorInt int tintColor);

    /**
     * Specifies tint color for this drawable as a color state list.
     * <p>
     * A Drawable's drawing content will be blended together with its tint
     * before it is drawn to the screen. This functions similarly to
     * {@link Drawable#setColorFilter(int, PorterDuff.Mode)}.
     * </p>
     * <p class="note"><strong>Note:</strong> Setting a color filter via
     * {@link Drawable#setColorFilter(ColorFilter)} or
     * {@link Drawable#setColorFilter(int, PorterDuff.Mode)} overrides tint.
     * </p>
     *
     * @param tint Color state list to use for tinting this drawable, or
     *            {@code null} to clear the tint
     * @see #setTint(int)
     * @see #setTintMode(PorterDuff.Mode)
     */
    void setTintList(@Nullable ColorStateList tint);

    /**
     * Specifies a tint blending mode for this drawable.
     * <p>
     * Defines how this drawable's tint color should be blended into the drawable
     * before it is drawn to screen. Default tint mode is {@link PorterDuff.Mode#SRC_IN}.
     * </p>
     * <p class="note"><strong>Note:</strong> Setting a color filter via
     * {@link Drawable#setColorFilter(ColorFilter)} or
     * {@link Drawable#setColorFilter(int, PorterDuff.Mode)} overrides tint.
     * </p>
     *
     * @param tintMode A Porter-Duff blending mode
     * @see #setTint(int)
     * @see #setTintList(ColorStateList)
     */
    void setTintMode(@NonNull PorterDuff.Mode tintMode);
}
