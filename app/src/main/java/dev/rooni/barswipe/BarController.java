package dev.rooni.barswipe;

import android.animation.ValueAnimator;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import io.github.libxposed.api.XposedInterface;

final class BarController {

    private static final PathInterpolator HIDE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    private static final PathInterpolator SHOW = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    final ViewGroup root;
    private final float hideThreshold;
    private final float showThreshold;

    private boolean hidden;
    private float progress;
    private ValueAnimator animator;

    private boolean tracking;
    private boolean swallowing;
    private float downX;
    private float downY;

    BarController(ViewGroup root) {
        this.root = root;
        float dp = root.getResources().getDisplayMetrics().density;
        hideThreshold = 6 * dp;
        showThreshold = 14 * dp;
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (progress > 0f) apply(progress);
        });
    }

    Object handle(MotionEvent e, XposedInterface.Chain chain) throws Throwable {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                downX = e.getRawX();
                downY = e.getRawY();
                tracking = true;
                swallowing = hidden;
                return swallowing ? Boolean.TRUE : chain.proceed();
            }
            case MotionEvent.ACTION_POINTER_DOWN -> {
                tracking = false;
                return swallowing ? Boolean.TRUE : chain.proceed();
            }
            case MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;
                if (swallowing) {
                    if (tracking && dy > showThreshold && dy > Math.abs(dx)) {
                        tracking = false;
                        show();
                    }
                    if (up) swallowing = false;
                    return Boolean.TRUE;
                }
                if (tracking && -dy > hideThreshold && -dy > Math.abs(dx) * 1.2f) {
                    tracking = false;
                    swallowing = !up;
                    cancelDownstream(e, chain);
                    hide();
                    return Boolean.TRUE;
                }
                if (tracking && (dy > hideThreshold || Math.abs(dx) > showThreshold)) tracking = false;
                return chain.proceed();
            }
            case MotionEvent.ACTION_CANCEL -> {
                tracking = false;
                if (swallowing) {
                    swallowing = false;
                    return Boolean.TRUE;
                }
                return chain.proceed();
            }
            default -> {
                return swallowing ? Boolean.TRUE : chain.proceed();
            }
        }
    }

    private static void cancelDownstream(MotionEvent e, XposedInterface.Chain chain) throws Throwable {
        MotionEvent cancel = MotionEvent.obtain(e);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        try {
            chain.proceed(new Object[]{cancel});
        } finally {
            cancel.recycle();
        }
    }

    private void hide() {
        if (hidden) return;
        hidden = true;
        buzz(false);
        animateTo(1f, 260, HIDE);
    }

    private void show() {
        if (!hidden) return;
        hidden = false;
        buzz(true);
        animateTo(0f, 420, SHOW);
    }

    private void animateTo(float target, long duration, PathInterpolator interpolator) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(duration);
        animator.setInterpolator(interpolator);
        animator.addUpdateListener(valueAnimator -> apply((float) valueAnimator.getAnimatedValue()));
        animator.start();
    }

    private void apply(float p) {
        progress = p;
        float y = -root.getHeight() * p;
        for (int i = 0; i < root.getChildCount(); i++) {
            root.getChildAt(i).setTranslationY(y);
        }
    }

    private void buzz(boolean showing) {
        try {
            Vibrator vibrator = root.getContext().getSystemService(Vibrator.class);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            VibrationEffect effect;
            if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK)) {
                VibrationEffect.Composition c = VibrationEffect.startComposition();
                if (showing) {
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f);
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 40);
                } else {
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f);
                }
                effect = c.compose();
            } else {
                effect = VibrationEffect.createPredefined(showing
                        ? VibrationEffect.EFFECT_TICK : VibrationEffect.EFFECT_CLICK);
            }
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        } catch (Throwable ignored) {
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }
}
