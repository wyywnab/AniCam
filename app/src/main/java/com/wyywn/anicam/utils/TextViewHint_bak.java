package com.wyywn.anicam.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.StringRes;

import java.lang.ref.WeakReference;

/**
 * TextView提示框工具类
 * 全局单例，支持内容更新和延时隐藏
 */
public class TextViewHint_bak {
    private static TextViewHint_bak instance;
    private final TextView textView;
    private static WeakReference<TextView> textViewRef;
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable hideRunnable;
    private long delayMillis = 3000; // 默认延时2秒隐藏
    private Activity activity;

    /**
     * 私有构造函数
     * @param textView 要控制的TextView
     */
    private TextViewHint_bak(TextView textView, Activity activity) {
        this.textView = textView;
        this.handler = new Handler(Looper.getMainLooper());
        initTextView();
        this.activity = activity;
    }

    /**
     * 初始化TextView
     */
    private void initTextView() {
        textView.setVisibility(TextView.GONE);
        // 设置一些默认样式
        /*textView.setPadding(32, 16, 32, 16);
        textView.setBackgroundResource(android.R.drawable.toast_frame);
        textView.setTextColor(0xFFFFFFFF);
        textView.setGravity(Gravity.CENTER);*/

        // 设置布局参数（如果未设置）
        if (textView.getLayoutParams() == null) {
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            textView.setLayoutParams(params);
        }
    }

    /**
     * 初始化工具类
     * @param textView 要控制的TextView
     */
    public static void init(TextView textView) {
        // 清除旧任务
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
            hideRunnable = null;
        }

        // 更新引用
        textViewRef = new WeakReference<>(textView);
        if (textView != null) {
            textView.setVisibility(View.GONE);
        }
    }

    /**
     * 获取实例
     * @return TextViewHint实例
     */
    public static TextViewHint_bak getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TextViewHint must be initialized first");
        }
        return instance;
    }

    /**
     * 显示提示信息
     * @param text 要显示的文本
     */
    public void show(String text) {
        show(text, delayMillis);
    }

    /**
     * 显示提示信息
     * @param resId 字符串资源ID
     */
    public void show(@StringRes int resId) {
        show(textView.getContext().getString(resId), delayMillis);
    }

    private static boolean isShowing = false;

    /**
     * 显示提示信息
     * @param text 要显示的文本
     * @param duration 显示时长(毫秒)
     */
    public void show(String text, long duration) {
        // 移除之前的隐藏任务
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
        }

        // 更新UI必须在主线程执行
        handler.post(() -> {
            textView.setText(text);
            //textView.bringToFront();
            /*if (!isShowing){
                Functions.showContentWithAnimation(textView);
                isShowing = true;
            }*/
            textView.setVisibility(TextView.VISIBLE);
            //textView.invalidate();
        });

        // 创建新的隐藏任务
        hideRunnable = () -> {
            hide();
            //isShowing = false;
        };
        handler.postDelayed(hideRunnable, duration);
    }

    /**
     * 隐藏提示框
     */
    public void hide() {
        handler.post(() ->
                textView.setVisibility(TextView.GONE)
                //Functions.hideContentWithAnimation(textView)
        );
    }

    /**
     * 设置默认的延时隐藏时间
     * @param millis 毫秒数
     */
    public void setDefaultDelay(long millis) {
        this.delayMillis = millis;
    }

    /**
     * 立即显示信息并使用默认延时隐藏
     * @param text 要显示的文本
     */
    public static void showText(String text) {
        if (textViewRef == null || textViewRef.get() == null) {
            // 弱引用失效时重新初始化
            //init(this.activity.findViewById(R.id.info_textView)); // 需要传入当前 TextView
            return;
        }

        TextView textView = textViewRef.get();
        // 清除旧任务
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable);
        }

        // 显示新内容
        //textView.setText(text);
        textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
        //Functions.showContentWithAnimation(textView);
        if (!isShowing){
            Functions.showContentWithAnimation(textView);
            isShowing = true;
        }

        // 设置自动隐藏
        hideRunnable = () -> {
            Functions.hideContentWithAnimation(textView);
            isShowing = false;
        };
        handler.postDelayed(hideRunnable, 3000);
    }

    /**
     * 立即显示信息并使用默认延时隐藏
     * @param resId 字符串资源ID
     */
    public static void showText(@StringRes int resId) {
        //getInstance().show(resId);
        showText(textViewRef.get().getContext().getString(resId));
    }

    /**
     * 立即隐藏提示框
     */
    public static void hideText() {
        getInstance().hide();
    }

    public static void resetState() {
        isShowing = false; // 重置显示状态
        if (hideRunnable != null) {
            handler.removeCallbacks(hideRunnable); // 取消未执行的隐藏任务
            hideRunnable = null;
        }
    }
}