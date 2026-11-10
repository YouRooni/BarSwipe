package dev.rooni.barswipe;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public class MainModule extends XposedModule {

    private static final String TAG = "BarSwipe";

    private final WeakHashMap<View, BarController> bars = new WeakHashMap<>();
    private volatile BarController last;
    private boolean hooked;

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (hooked || !"com.android.systemui".equals(param.getPackageName())) return;
        hooked = true;
        try {
            Method dispatch = View.class.getDeclaredMethod("dispatchPointerEvent", MotionEvent.class);
            hook(dispatch).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    BarController bar = barFor((View) chain.getThisObject());
                    if (bar == null) return chain.proceed();
                    return bar.handle((MotionEvent) chain.getArg(0), chain);
                }
            });
            deoptimizeCallers();
            log(Log.INFO, TAG, "Hook initialized");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to initialize hook", t);
        }
    }

    private void deoptimizeCallers() {
        try {
            Class<?> stage = Class.forName("android.view.ViewRootImpl$ViewPostImeInputStage");
            for (Method method : stage.getDeclaredMethods()) {
                if (method.getName().equals("processPointerEvent")) deoptimize(method);
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to deoptimize callers", t);
        }
    }

    private BarController barFor(View view) {
        BarController cached = last;
        if (cached != null && cached.root == view) return cached;
        if (!(view instanceof ViewGroup root)) return null;
        if (!(root.getLayoutParams() instanceof WindowManager.LayoutParams params)) return null;
        if (params.type != WindowManager.LayoutParams.TYPE_STATUS_BAR) return null;
        BarController bar = bars.get(root);
        if (bar == null) {
            bar = new BarController(root);
            bars.put(root, bar);
        }
        last = bar;
        return bar;
    }
}
