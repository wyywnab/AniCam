package com.wyywn.anicam.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.lang.ref.WeakReference;

/**
 * TextView提示框工具类（全局静态工具）
 * 支持内容更新和延时自动隐藏功能
 * 外部调用方法：init(), showText(), resetState()
 */
public class TextViewHint {
    // 弱引用目标TextView（避免内存泄漏）
    private static WeakReference<TextView> textViewRef;
    // 主线程Handler
    private static final Handler handler = new Handler(Looper.getMainLooper());
    // 延时隐藏任务
    private static Runnable hideRunnable;
    // 当前显示状态标记
    private static boolean isShowing = false;

    /**
     * 初始化工具类（必须优先调用）
     * @param textView 要控制的TextView组件
     */
    public static void init(TextView textView) {
        // 清理旧任务
        resetState();
        // 更新引用
        textViewRef = new WeakReference<>(textView);
        if (textView != null) {
            textView.setVisibility(View.GONE);
        }
    }

    /**
     * 显示提示信息（自动延时隐藏）
     * @param text 要显示的HTML文本内容
     */
    public static void showText(String text, int mills) {
        TextView textView = getValidTextView();
        if (textView == null) return;

        // 清理旧任务
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
        }

        handler.post(() -> {
            // 设置富文本内容
            textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));

            // 首次显示时播放动画
            if (!isShowing) {
                Functions.showContentWithAnimation(textView);
                isShowing = true;
            }
        });

        // 创建延时隐藏任务
        hideRunnable = () -> {
            Functions.hideContentWithAnimation(textView);
            isShowing = false;
        };
        handler.postDelayed(hideRunnable, mills); // 固定延时3秒
    }

    public static void showText(String text) {
        showText(text, 3000);
    }

    /**
     * 显示提示信息（资源ID版本）
     * @param resId 字符串资源ID
     */
    public static void showText(@StringRes int resId, int mills) {
        TextView textView = getValidTextView();
        if (textView != null) {
            showText(textView.getContext().getString(resId), mills);
        }
    }

    public static void showText(@StringRes int resId) {
        showText(resId, 3000);
    }

    /**
     * 重置工具类状态
     * 取消延时任务并复位显示标记
     */
    public static void resetState() {
        isShowing = false;
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
    }

    // 获取有效TextView实例（内部校验）
    private static TextView getValidTextView() {
        if (textViewRef == null || textViewRef.get() == null) {
            return null;
        }
        return textViewRef.get();
    }
}