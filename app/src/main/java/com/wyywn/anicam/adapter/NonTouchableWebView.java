package com.wyywn.anicam.adapter;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class NonTouchableWebView extends WebView {

    public NonTouchableWebView(Context context) {
        super(context);
    }

    public NonTouchableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonTouchableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 直接返回 false，让事件交给父容器处理
        return false;
    }

    public static class LocalWebViewClient extends WebViewClient {
        private final Context context;

        public LocalWebViewClient(Context context) {
            this.context = context;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();

            // 处理本地文件链接
            if ("content".equals(uri.getScheme())) {
                view.loadUrl(uri.toString());
                return true;
            }

            // 处理其他链接
            return super.shouldOverrideUrlLoading(view, request);
        }
    }
}