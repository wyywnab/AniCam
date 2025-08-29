package com.wyywn.anicam.utils;
import android.app.Activity;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 全屏控制工具类，确保内容延伸到刘海区域
 */
public class FullScreenUtils {
    private static final String TAG = "FullScreenUtils";

    /**
     * 禁用全屏模式（显示状态栏和导航栏）
     * @param activity 当前Activity
     */
    public static void disableFullScreen(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        // 1. 清除全屏标志
        clearFullScreenFlags(window);

        // 2. 显示系统栏
        showSystemBars(window);

        // 3. 取消刘海屏适配
        unadaptCutoutScreen(window);

        // 4. 恢复默认布局
        unsetLayoutFullscreen(window);

        // 5. 恢复内容区域不延伸到系统栏
        setFitsSystemWindows(window, true);

        WindowManager.LayoutParams lp = window.getAttributes();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(lp);
    }

    /**
     * 清除全屏标志
     * @param window 窗口对象
     */
    private static void clearFullScreenFlags(Window window) {
        // 清除全屏相关标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // 清除透明状态栏和导航栏标志
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    /**
     * 显示系统栏（状态栏和导航栏）
     * @param window 窗口对象
     */
    private static void showSystemBars(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 新API
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            }
        } else {
            // 旧版本API
            View decorView = window.getDecorView();
            int uiOptions = decorView.getSystemUiVisibility();
            uiOptions &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    /**
     * 取消刘海屏/水滴屏适配
     * @param window 窗口对象
     */
    private static void unadaptCutoutScreen(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            window.setAttributes(lp);
        }
    }

    /**
     * 恢复默认布局
     * @param window 窗口对象
     */
    private static void unsetLayoutFullscreen(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
        } else {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags &= ~(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            decorView.setSystemUiVisibility(flags);
        }
    }

    /**
     * 设置内容是否延伸到系统栏区域
     * @param window 窗口对象
     * @param fitsSystemWindows 是否适应系统窗口
     */
    private static void setFitsSystemWindows(Window window, boolean fitsSystemWindows) {
        View decorView = window.getDecorView();
        if (decorView instanceof ViewGroup) {
            ViewGroup contentView = (ViewGroup) decorView.findViewById(android.R.id.content);
            if (contentView != null && contentView.getChildCount() > 0) {
                View content = contentView.getChildAt(0);
                content.setFitsSystemWindows(fitsSystemWindows);
            }
        }
    }

    /**
     * 检查当前是否处于全屏模式
     * @param activity 当前Activity
     * @return 是否全屏
     */
    public static boolean isFullScreen(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return false;
        }

        // 检查窗口标志
        int flags = window.getAttributes().flags;
        boolean isFullscreenByFlags = (flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;

        // 检查系统UI可见性
        View decorView = window.getDecorView();
        int uiOptions = decorView.getSystemUiVisibility();
        boolean isFullscreenByUi = (uiOptions & View.SYSTEM_UI_FLAG_FULLSCREEN) == View.SYSTEM_UI_FLAG_FULLSCREEN;

        return isFullscreenByFlags || isFullscreenByUi;
    }

    /**
     * 启用全屏模式（隐藏状态栏和导航栏）
     * @param activity 当前Activity
     */
    public static void enableFullScreen(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        // 添加额外的空值检查
        View decorView = window.getDecorView();
        if (decorView == null) {
            // 如果 DecorView 为 null，延迟执行全屏设置
            decorView.post(() -> enableFullScreen(activity));
            return;
        }

        // 1. 设置窗口标志
        setWindowFlags(window);

        // 2. 适配刘海屏
        adaptCutoutScreen(window);

        // 3. 设置内容延伸到系统栏区域
        setLayoutFullscreen(window);

        // 4. 隐藏系统栏
        hideSystemBars(window);

        // 5. 特殊设备适配
        adaptSpecialDevices(activity);
    }

    /**
     * 设置窗口标志
     * @param window 窗口对象
     */
    private static void setWindowFlags(Window window) {
        // 清除可能阻碍全屏的标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

        // 添加全屏标志
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // 对于旧版本Android，设置透明状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    /**
     * 设置内容延伸到系统栏区域
     * @param window 窗口对象
     */
    private static void setLayoutFullscreen(Window window) {
        // 关键设置：允许内容延伸到系统栏区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decorView = window.getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            decorView.setSystemUiVisibility(flags);
        }
    }

    /**
     * 隐藏系统栏（状态栏和导航栏）
     * @param window 窗口对象
     */
    private static void hideSystemBars(Window window) {
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 新API
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // 旧版本API
            View decorView = window.getDecorView();
            if (decorView != null) { // 添加空值检查
                int uiOptions = decorView.getSystemUiVisibility();
                uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

                decorView.setSystemUiVisibility(uiOptions);
            }
        }
    }

    /**
     * 适配刘海屏/水滴屏
     * @param window 窗口对象
     */
    private static void adaptCutoutScreen(Window window) {
        // 设置全屏布局延伸到刘海区域
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
    }

    /**
     * 特殊设备适配（针对某些厂商的特殊实现）
     * @param activity 当前Activity
     */
    private static void adaptSpecialDevices(Activity activity) {
        // 华为设备特殊适配
        if (isHuaweiDevice()) {
            adaptHuaweiCutout(activity);
        }

        // 小米设备特殊适配
        if (isXiaomiDevice()) {
            adaptXiaomiCutout(activity);
        }

        // OPPO/VIVO设备特殊适配
        if (isOppoDevice() || isVivoDevice()) {
            adaptOppoVivoCutout(activity);
        }
    }

    /**
     * 华为设备刘海适配
     */
    private static void adaptHuaweiCutout(Activity activity) {
        try {
            Class<?> hwWindowManager = Class.forName("com.huawei.android.view.HwWindowManager");
            Method method = hwWindowManager.getMethod("setWindowLayoutInDisplayCutoutMode",
                    WindowManager.LayoutParams.class, int.class);
            method.invoke(null, activity.getWindow().getAttributes(),
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES);
        } catch (Exception e) {
            Log.d(TAG, "Huawei cutout adaptation failed: " + e.getMessage());
        }
    }

    /**
     * 小米设备刘海适配
     */
    private static void adaptXiaomiCutout(Activity activity) {
        try {
            Class<?> windowClass = Class.forName("android.view.Window");
            Method method = windowClass.getMethod("setExtraFlags", int.class, int.class);
            method.invoke(activity.getWindow(), 0x00000100 | 0x00000200 | 0x00000400,
                    0x00000100 | 0x00000200 | 0x00000400); // 全屏显示，内容延伸到刘海区域
        } catch (Exception e) {
            Log.d(TAG, "Xiaomi cutout adaptation failed: " + e.getMessage());
        }
    }

    /**
     * OPPO/VIVO设备刘海适配
     */
    private static void adaptOppoVivoCutout(Activity activity) {
        try {
            // OPPO和VIVO通常使用系统默认设置，但可以尝试设置特殊标志
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            try {
                Field field = params.getClass().getField("layoutInDisplayCutoutMode");
                field.setInt(params, 1); // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } catch (Exception e) {
                Log.d(TAG, "OPPO/VIVO cutout adaptation failed: " + e.getMessage());
            }
            activity.getWindow().setAttributes(params);
        } catch (Exception e) {
            Log.d(TAG, "OPPO/VIVO cutout adaptation failed: " + e.getMessage());
        }
    }

    /**
     * 检查设备品牌
     */
    private static boolean isHuaweiDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("HUAWEI");
    }

    private static boolean isXiaomiDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("XIAOMI");
    }

    private static boolean isOppoDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("OPPO");
    }

    private static boolean isVivoDevice() {
        return Build.MANUFACTURER.equalsIgnoreCase("VIVO");
    }

    /**
     * 获取刘海区域的安全边距
     * @param activity 当前Activity
     * @return 包含安全边距的Rect对象
     */
    /*public static Rect getCutoutSafeArea(Activity activity) {
        Rect safeArea = new Rect(0, 0, 0, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            View decorView = activity.getWindow().getDecorView();
            WindowInsets windowInsets = decorView.getRootWindowInsets();
            if (windowInsets != null) {
                WindowInsets displayCutout = windowInsets.getDisplayCutout();
                if (displayCutout != null) {
                    safeArea.left = displayCutout.getSafeInsetLeft();
                    safeArea.top = displayCutout.getSafeInsetTop();
                    safeArea.right = displayCutout.getSafeInsetRight();
                    safeArea.bottom = displayCutout.getSafeInsetBottom();
                }
            }
        }

        return safeArea;
    }*/

    /**
     * 检查设备是否有刘海屏
     * @param activity 当前Activity
     * @return 是否有刘海屏
     */
    public static boolean hasCutout(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            View decorView = activity.getWindow().getDecorView();
            WindowInsets windowInsets = decorView.getRootWindowInsets();
            if (windowInsets != null) {
                return windowInsets.getDisplayCutout() != null;
            }
        }
        return false;
    }

    /**
     * 获取实际屏幕高度（包括被系统UI占用的区域）
     */
    public static int getRealScreenHeight(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        } else {
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.heightPixels;
    }

    /**
     * 获取实际屏幕宽度（包括被系统UI占用的区域）
     */
    public static int getRealScreenWidth(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        } else {
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }
        return metrics.widthPixels;
    }
}