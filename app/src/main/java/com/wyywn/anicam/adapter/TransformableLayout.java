package com.wyywn.anicam.adapter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class TransformableLayout extends FrameLayout {
    private Matrix matrix = new Matrix();

    public TransformableLayout(Context context) {
        super(context);
    }

    public TransformableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // 提供一个公共方法来设置变换矩阵
    public void setTransformMatrix(Matrix matrix) {
        this.matrix.set(matrix);
        invalidate(); // 触发重绘
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // 在绘制子View之前，保存当前画布状态并应用矩阵变换
        canvas.save();
        canvas.concat(matrix); // 将Matrix应用到Canvas[7,8](@ref)
        super.dispatchDraw(canvas); // 调用父类方法绘制所有子View（包括WebView）
        canvas.restore(); // 恢复画布状态[7](@ref)
    }
}