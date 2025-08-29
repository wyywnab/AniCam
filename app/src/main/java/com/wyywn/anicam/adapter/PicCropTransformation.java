package com.wyywn.anicam.adapter;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.security.MessageDigest;

public class PicCropTransformation extends BitmapTransformation {
    private static final String ID = "com.wyywn.anicam.adapter.CustomCropTransformation";
    private static final byte[] ID_BYTES = ID.getBytes(CHARSET);

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int width = toTransform.getWidth();
        int height = toTransform.getHeight();

        // 自定义裁剪逻辑
        if (height > width * 1.4) {
            int cropHeight = (int) (width * 1.2);
            return Bitmap.createBitmap(toTransform, 0, 0, width, Math.min(cropHeight, height));
        }
        return toTransform;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PicCropTransformation;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }
}
