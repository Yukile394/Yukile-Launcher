package net.kdt.pojavlaunch.utils;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A drawable that renders a continuously scrolling diagonal gradient
 * (soft gray -> pink -> white -> pink -> soft gray, repeating) to give
 * a gentle "flowing" animated look. Purely decorative — draws on top
 * of the view's bounds like a normal background drawable.
 */
public class FlowingGradientDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint;
    private final Matrix matrix = new Matrix();
    private final int[] colors = new int[]{
            0xFFC7C7D1, // soft gray
            0xFFF3A8CE, // pink
            0xFFFFFFFF, // white
            0xFFF3A8CE, // pink
            0xFFC7C7D1  // soft gray
    };
    private final float cornerRadiusPx;
    private LinearGradient shader;
    private ValueAnimator animator;
    private float phase = 0f;

    public FlowingGradientDrawable(float cornerRadiusPx, int strokeColor, float strokeWidthPx) {
        this.cornerRadiusPx = cornerRadiusPx;
        if (strokeWidthPx > 0) {
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidthPx);
            strokePaint.setColor(strokeColor);
        } else {
            strokePaint = null;
        }
    }

    /** Creates the drawable, sets it as the view's background, and starts the animation loop. */
    public static FlowingGradientDrawable attach(View view, float cornerRadiusPx, int strokeColor, float strokeWidthPx) {
        FlowingGradientDrawable drawable = new FlowingGradientDrawable(cornerRadiusPx, strokeColor, strokeWidthPx);
        view.setBackground(drawable);
        drawable.start();
        return drawable;
    }

    public void start() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(anim -> {
            phase = (float) anim.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    /** Stops the animation loop. Call when the view holding this drawable is hidden/destroyed. */
    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildShader(bounds);
    }

    private void rebuildShader(Rect b) {
        if (b.width() <= 0 || b.height() <= 0) return;
        // Oversized diagonal gradient with MIRROR tiling so the animated
        // phase shift never reveals a hard seam.
        shader = new LinearGradient(
                -b.width() * 0.5f, 0,
                b.width() * 1.5f, b.height(),
                colors, null, Shader.TileMode.MIRROR);
        fillPaint.setShader(shader);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (shader == null) rebuildShader(bounds);
        if (shader == null) return;

        matrix.setTranslate(phase * bounds.width(), 0);
        shader.setLocalMatrix(matrix);

        RectF rect = new RectF(bounds);
        if (cornerRadiusPx > 0) {
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, fillPaint);
            if (strokePaint != null) canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, strokePaint);
        } else {
            canvas.drawRect(rect, fillPaint);
            if (strokePaint != null) canvas.drawRect(rect, strokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
