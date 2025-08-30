package com.wyywn.anicam.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import com.wyywn.anicam.R;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerUtil {

    // 系统图片查看器包名列表
    private static final String[] SYSTEM_GALLERY_PACKAGES = {
            "com.android.gallery", // 原生Android图库
            "com.android.gallery3d", // 原生Android图库3D
            "com.google.android.gallery3d", // Google相册
            "com.sec.android.gallery3d", // 三星图库
            "com.htc.album", // HTC相册
            "com.sonyericsson.album", // 索尼相册
            "com.miui.gallery", // 小米相册
            "com.huawei.gallery", // 华为相册
            "com.coloros.gallery3d", // OPPO相册
            "com.oppo.gallery3d", // OPPO相册
            "com.vivo.gallery", // VIVO相册
            "com.lenovo.scg", // 联想相册
            "com.flyme.gallery", // 魅族相册
            "com.oneplus.gallery", // 一加相册
            "bin.mt.plus"
    };

    /**
     * 使用系统图片查看器打开图片
     * @param context 上下文
     * @param imageUri 要查看的图片文件Uri
     */
    public static void viewImageWithSystemViewer(Context context, Uri imageUri) {
        if (context == null) {
            //Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取文件的Uri（适配Android 7.0及以上版本）
        //Uri imageUri = FileProviderUtil.getUriForFile(context, imageFile);

        // 查找已安装的系统图片查看器
        String systemGalleryPackage = findSystemGalleryPackage(context);
        //Toast.makeText(context, systemGalleryPackage, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(imageUri, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // 如果找到系统图片查看器，直接指定包名启动
        if (systemGalleryPackage != null) {
            intent.setPackage(systemGalleryPackage);
            try {
                context.startActivity(intent);
                return;
            } catch (Exception e) {
                // 如果指定包名启动失败，移除包名限制
                intent.setPackage(null);
            }
        }

        // 没有找到系统图片查看器或指定包名启动失败，让用户选择
        try {
            Intent chooserIntent = Intent.createChooser(intent, context.getString(R.string.ui_selectPhotoViewer));
            context.startActivity(chooserIntent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.info_noAvailablePhotoViewer, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 查找设备上已安装的系统图片查看器包名
     * @param context 上下文
     * @return 系统图片查看器包名，如果未找到则返回null
     */
    private static String findSystemGalleryPackage(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        testIntent.setType("image/*");

        // 获取所有可以查看图片的应用
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(testIntent, 0);
        List<String> availablePackages = new ArrayList<>();

        for (ResolveInfo info : resolveInfos) {
            if (info.activityInfo != null) {
                availablePackages.add(info.activityInfo.packageName);
            }
        }

        // 检查是否有系统图片查看器
        for (String packageName : SYSTEM_GALLERY_PACKAGES) {
            if (availablePackages.contains(packageName)) {
                return packageName;
            }
        }

        return null;
    }
}