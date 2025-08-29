package com.wyywn.anicam.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.location.Location;

import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Functions{
    public static final String LibFilePath = "lib.json";
    public static final String EnvFilePath = "env.json";

    public static String readFile(String empty,String fileName){
        try {
            File file = new File(fileName);
            String jsonString = empty;
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                BufferedReader br = new BufferedReader(new InputStreamReader(fis));

                StringBuilder text = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    text.append(line).append("\n");
                }
                jsonString = text.toString();
            }

            return jsonString;

        } catch (IOException e) {
            // Handle file read errors
            e.printStackTrace();
        }
        return null;
    }

    public static JSONArray getLib(String exDataPath){
        try {
            return new JSONArray(readFile("[]",exDataPath.concat("/").concat(LibFilePath)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public static void saveLib(String exDataPath,JSONArray LibArr){
        try {
            FileWriter writer = new FileWriter(exDataPath.concat("/").concat(LibFilePath));
            writer.write(LibArr.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JSONObject getEnv(String exDataPath){
        try {
            return new JSONObject(readFile("{}",exDataPath.concat("/").concat(EnvFilePath)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public static void saveEnv(String exDataPath,JSONObject LibArr){
        try {
            FileWriter writer = new FileWriter(exDataPath.concat("/").concat(EnvFilePath));
            writer.write(LibArr.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> convertJsonArrayToList(JSONArray jsonArray) {
        try {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                // 获取每个元素并转换为字符串
                list.add(jsonArray.getString(i));
            }
            return list;
        } catch (JSONException e){
            throw new RuntimeException(e);
        }
    }

    public static JSONArray insertJsonObjToJsonArray(int position, JSONArray jarr, JSONObject jobj){
        try {
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < position; i++) {
                newArr.put(jarr.getJSONObject(i));
            }
            newArr.put(jobj);
            for (int i = position; i < jarr.length(); i++) {
                newArr.put(jarr.getJSONObject(i));
            }
            return newArr;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileName(Context context, Uri uri) {
        String displayName = null;
        try (Cursor cursor = context.getContentResolver().query(
                uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                displayName = cursor.getString(nameIndex);
            }
        }
        return displayName != null ? displayName : "未知文件";
    }

    public static List<Uri> getFilesInDirectory(Context context, Uri treeUri) {
        List<Uri> fileUris = new ArrayList<>();

        // 构建子文档查询URI
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null, null, null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String mimeType = cursor.getString(1);

                    // 只添加文件，不添加文件夹
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                        fileUris.add(fileUri);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FileTraversal", "遍历文件夹出错", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return fileUris;
    }
    public static List<Uri> getFilesByType(Context context, Uri treeUri, String[] mimeTypes) {
        List<Uri> filteredUris = new ArrayList<>();
        List<Uri> allUris = getFilesInDirectory(context, treeUri);

        for (Uri uri : allUris) {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                for (String filterType : mimeTypes) {
                    if (mimeType.startsWith(filterType.replace("/*", ""))) {
                        filteredUris.add(uri);
                        break;
                    }
                }
            }
        }

        return filteredUris;
    }

    public static JSONArray getSubdirectoryTreeUris(Context context, Uri treeUri) {
        //List<Uri> directoryTreeUris = new ArrayList<>();
        JSONArray directoryTreeUrisAndInfo = new JSONArray();

        // 构建子文档查询URI
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null, null, null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);

                    // 只添加文件夹，不添加文件
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        // 构建子文件夹的Tree URI
                        Uri subdirectoryTreeUri = DocumentsContract.buildTreeDocumentUri(
                                treeUri.getAuthority(),
                                documentId
                        );
                        JSONObject localJsonObj = new JSONObject();
                        localJsonObj.put("name", displayName);
                        localJsonObj.put("treeUri", subdirectoryTreeUri.toString());
                        directoryTreeUrisAndInfo.put(localJsonObj);
                        /*directoryTreeUris.add(subdirectoryTreeUri);*/
                        Log.d("Directory", "找到子文件夹: " + displayName + ", Tree URI: " + subdirectoryTreeUri);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DirectoryTraversal", "遍历文件夹出错", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return directoryTreeUrisAndInfo;
    }

    public static List<Uri> getFilesByTypeInSubfolder(Context context, Uri treeUri, String subFolderDocumentId, String[] mimeTypes) {
        List<Uri> fileUris = new ArrayList<>();

        // 构建子目录的查询URI
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, subFolderDocumentId);

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    childrenUri,
                    new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                    },
                    null, null, null
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String displayName = cursor.getString(1);
                    String mimeType = cursor.getString(2);

                    // 只处理文件，不处理文件夹
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        // 检查 MIME 类型是否匹配
                        if (isMimeTypeMatch(mimeType, mimeTypes)) {
                            // 构建文件的完整 URI
                            Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                            fileUris.add(fileUri);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FileSearch", "搜索子目录文件出错", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return fileUris;
    }

    private static boolean isMimeTypeMatch(String fileMimeType, String[] targetMimeTypes) {
        for (String targetMimeType : targetMimeTypes) {
            if (targetMimeType.endsWith("/*")) {
                // 通配符匹配
                String baseType = targetMimeType.substring(0, targetMimeType.length() - 2);
                if (fileMimeType.startsWith(baseType)) {
                    return true;
                }
            } else if (fileMimeType.equals(targetMimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查URI对应的文件是否存在
     * @param uri 要检查的URI
     * @return 文件是否存在
     */
    public static boolean isUriFileExists(Context context, Uri uri) {
        if (uri == null) {
            return false;
        }

        Cursor cursor = null;
        try {
            // 查询文件信息
            cursor = context.getContentResolver().query(
                    uri,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null
            );

            // 如果cursor不为null且能移动到第一行，说明文件存在
            return cursor != null && cursor.moveToFirst();

        } catch (SecurityException e) {
            Log.e("UriCheck", "权限不足: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e("UriCheck", "检查URI存在时出错: " + e.getMessage());
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // 将 Matrix 转换为字符串
    public static String matrixToString(Matrix matrix) {
        if (matrix == null) {
            return "";
        }

        float[] values = new float[9];
        matrix.getValues(values);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(values[i]);
            if (i < values.length - 1) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    // 从字符串转换回 Matrix
    public static Matrix stringToMatrix(String matrixString) {
        if (matrixString == null || matrixString.isEmpty()) {
            return new Matrix(); // 返回单位矩阵
        }

        try {
            String[] valueStrings = matrixString.split(",");
            if (valueStrings.length != 9) {
                Log.w("matrix", "Invalid matrix string format");
                return new Matrix();
            }

            float[] values = new float[9];
            for (int i = 0; i < 9; i++) {
                values[i] = Float.parseFloat(valueStrings[i]);
            }

            Matrix matrix = new Matrix();
            matrix.setValues(values);
            return matrix;
        } catch (NumberFormatException e) {
            Log.e("matrix", "Failed to parse matrix string", e);
            return new Matrix();
        }
    }

    public static Matrix getPhysicsMatrix(JSONObject picObj){
        try {
            Matrix matrix = new Matrix();

            if (picObj.has("matrix")) {
                return stringToMatrix(picObj.getString("matrix"));
            }

            return matrix;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    public static ColorMatrixColorFilter getColorFilterByOptions(float hueProgress, float saturationProgress, float luminanceProgress){
        float hueValue = (hueProgress - 128f) / 128f * 180;
        float saturationValue = saturationProgress / 128f;
        float luminanceValue = luminanceProgress / 128f;

        ColorMatrix colorMatrix = new ColorMatrix();
        ColorMatrix mHueMatrix = new ColorMatrix();
        ColorMatrix mSaturationMatrix = new ColorMatrix();
        ColorMatrix mLuminanceMatrix = new ColorMatrix();

        //设置色相
        mHueMatrix.reset();
        mHueMatrix.setRotate(0, hueValue);
        mHueMatrix.setRotate(1, hueValue);
        mHueMatrix.setRotate(2, hueValue);

        //设置饱和度
        mSaturationMatrix.reset();
        mSaturationMatrix.setSaturation(saturationValue);

        //亮度
        mLuminanceMatrix.reset();
        mLuminanceMatrix.setScale(luminanceValue, luminanceValue, luminanceValue, 1);

        colorMatrix.reset();// 效果叠加
        colorMatrix.postConcat(mLuminanceMatrix);
        colorMatrix.postConcat(mSaturationMatrix);
        colorMatrix.postConcat(mHueMatrix);

        return new ColorMatrixColorFilter(colorMatrix);
    }
    public static ColorMatrixColorFilter getColorFilter(JSONObject picObj){
        try {
            if (!picObj.has("colors")) {
                return new ColorMatrixColorFilter(new ColorMatrix());
            }

            JSONObject colorObj = picObj.getJSONObject("colors");
            float hueProgress = (float) colorObj.getDouble("hue");
            float saturationProgress = (float) colorObj.getDouble("saturation");
            float luminanceProgress = (float) colorObj.getDouble("luminance");

            return getColorFilterByOptions(hueProgress, saturationProgress, luminanceProgress);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setDefaultMatrixValues(JSONObject picObj, JSONObject prototypeObj, ContentResolver resolver){
        try {
            if (prototypeObj == null){
                picObj.put("matrix", matrixToString(new Matrix()));

                JSONObject colors = new JSONObject();
                colors.put("hue", 128);
                colors.put("saturation", 128);
                colors.put("luminance", 128);
                picObj.put("colors", colors);
            } else {
                if (prototypeObj.has("matrix")) {
                    picObj.put("matrix", prototypeObj.getString("matrix"));
                }

                if (prototypeObj.has("colors")) {
                    picObj.put("colors", prototypeObj.getJSONObject("colors"));
                }

            }
            InputStream inputStream = resolver.openInputStream(Uri.parse(picObj.getString("uri")));
            if (inputStream != null) {
                // This part is fine, keep it
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(inputStream, null, options);
                picObj.put("width", options.outWidth);
                picObj.put("height", options.outHeight);
                inputStream.close(); // Important: close the stream
            }
        } catch (JSONException | IOException  e) {
            throw new RuntimeException(e);
        }
    }
    public static float[] getTransformedCenter(Matrix matrix, float originalWidth, float originalHeight) {
        // 原始图像的中心点
        float originalCenterX = originalWidth / 2f;
        float originalCenterY = originalHeight / 2f;

        // 创建包含中心点的数组
        float[] points = {originalCenterX, originalCenterY};

        // 应用矩阵变换
        matrix.mapPoints(points);

        return points; // points[0] = 变换后的X坐标, points[1] = 变换后的Y坐标
    }

    public static int matchedPositionOfId(JSONArray jArr, Long targetId){
        try {
            for (int i = 0; i < jArr.length(); i++) {
                if (targetId == jArr.getJSONObject(i).getLong("id")){
                    return i;
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    private static final Pattern TIME_PATTERN = Pattern.compile("\\{([^}]*)\\}");

    /**
     * 格式化字符串中的时间部分
     * @param input 输入字符串，如 "anicam_{yyyyMMdd_HHmmss}"
     * @return 格式化后的字符串，如 "anicam_20250825_160101"
     */
    public static String formatTimeInString(String input) {
        if (TextUtils.isEmpty(input)) {
            return input;
        }
        if (input.isEmpty()){
            input = "anicam_{yyyyMMdd_HHmmss}";
        }

        // 使用Matcher查找所有大括号内的内容
        Matcher matcher = TIME_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String timeFormat = matcher.group(1); // 获取大括号内的内容
            String formattedTime;

            try {
                // 尝试将大括号内的内容作为时间格式进行格式化
                SimpleDateFormat sdf = new SimpleDateFormat(timeFormat, Locale.getDefault());
                formattedTime = sdf.format(new Date());
            } catch (IllegalArgumentException e) {
                // 如果格式无效，保持原样
                formattedTime = "{" + timeFormat + "}";
            }

            // 替换匹配到的内容
            matcher.appendReplacement(result, formattedTime);
        }

        // 添加剩余部分
        matcher.appendTail(result);
        return result.toString();
    }

    public static final String[] allExifKeys = {
            ExifInterface.TAG_APERTURE,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_ISO,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_WHITE_BALANCE
    };

    public static void addExifData(Context context, File file, String[] keys, String[] data, Location location) {
        try {
            ExifInterface exifInterface = new ExifInterface(file.getAbsolutePath());

            // 设置 EXIF 属性
            for (int i = 0; i < keys.length; i++) {
                if (data[i] != null) {
                    exifInterface.setAttribute(keys[i], data[i]);
                }
            }

            if (location != null){
                double exifLatitude = Math.abs(location.getLatitude());
                String exifLatitudeRef = location.getLatitude() >= 0 ? "N" : "S";

                double exifLongitude = Math.abs(location.getLongitude());
                String exifLongitudeRef = location.getLongitude() >= 0 ? "E" : "W";

                exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDms(exifLatitude));
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exifLatitudeRef);
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDms(exifLongitude));
                exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exifLongitudeRef);
            }

            // 保存 EXIF 到临时文件
            exifInterface.saveAttributes();
        } catch (IOException e) {
            Log.e("Exif", "保存 EXIF 数据失败", e);
        }
    }

    private static String convertToDms(double coord) {
        int degrees = (int) coord;
        coord = (coord - degrees) * 60;
        int minutes = (int) coord;
        coord = (coord - minutes) * 60;
        int seconds = (int) (coord * 1000);

        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }

    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        // 如果是文件路径URI
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // 对于Android 10及以上，我们不应该使用文件路径
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return null;
        }

        // 对于Android 9及以下，尝试从MediaStore获取路径
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    return cursor.getString(columnIndex);
                }
            } finally {
                cursor.close();
            }
        }

        return null;
    }

    public static String[] getExifData(Context context, Uri fileUri, String[] keys) {
        ExifInterface exifInterface = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 对于Android 10及以上，使用ContentResolver打开InputStream
                InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
                if (inputStream != null) {
                    exifInterface = new ExifInterface(inputStream);
                    inputStream.close();
                }
            } else {
                // 对于Android 9及以下，使用文件路径
                String filePath = getPathFromUri(context, fileUri);
                if (filePath != null) {
                    exifInterface = new ExifInterface(filePath);
                }
            }

            if (exifInterface == null) {
                Log.e("Exif", "无法创建ExifInterface");
                return new String[keys.length];
            }

            String[] values = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = exifInterface.getAttribute(keys[i]);
            }
            return values;
        } catch (IOException e) {
            Log.e("Exif", "读取EXIF数据失败", e);
            return new String[keys.length];
        }
    }

    public static File saveBitmapToInteriorPathWithFile(Context context, Bitmap bitmap, File interiorPath, String fileName) {
        File imageFile = new File(interiorPath, fileName);

        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            if (success) {
                return imageFile;
            }
        } catch (IOException e) {
            Log.e("FileSave", "Failed to save image: " + e.getMessage());
        }

        return null;
    }

    /**
     * 将 assets 文件复制到内部存储并返回 File 对象
     *
     * @param context     上下文对象
     * @param assetPath   assets 中的文件路径（如 "config/settings.json"）
     * @param destDirName 目标目录名称（如 "copied_assets"）
     * @return 复制后的 File 对象，失败返回 null
     */
    public static File getFileFromAssets(Context context, String assetPath, String destDirName) {
        // 1. 创建目标目录
        File destDir = new File(context.getFilesDir(), destDirName);
        if (!destDir.exists() && !destDir.mkdirs()) {
            return null; // 创建目录失败
        }

        // 2. 提取文件名
        String fileName = assetPath.contains("/")
                ? assetPath.substring(assetPath.lastIndexOf("/") + 1)
                : assetPath;

        // 3. 创建目标文件
        File outputFile = new File(destDir, fileName);

        // 4. 复制文件
        try (InputStream is = context.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }

            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Uri copyInteriorToStorage(Context context, File orgFile, String folderName, String fileName, String folderType, String mimeType) {
        try {
            // 对于 Android 9 及以下，直接使用传统文件操作
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                return copyForLegacyAndroid(context, orgFile, folderName, fileName, folderType, mimeType);
            } else {
                // Android 10 及以上使用 MediaStore
                return copyForModernAndroid(context, orgFile, folderName, fileName, folderType, mimeType);
            }
        } catch (Exception e) {
            Log.e("FileSave", "保存过程中发生错误", e);
            return null;
        }
    }
    public static Uri copyInteriorToStorage(Context context, File orgFile, String folderName, String fileName, String folderType) {
        return copyInteriorToStorage(context, orgFile, folderName, fileName, folderType, "image/jpeg");
    }

    private static Uri copyForLegacyAndroid(Context context, File orgFile, String folderName, String fileName, String folderType, String mimeType) {
        // 1. 获取目标目录（DCIM/子目录）
        File dcimDir = Environment.getExternalStoragePublicDirectory(folderType);
        File targetDir = new File(dcimDir, folderName);

        // 2. 确保目录存在
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.e("FileSave", "无法创建目录: " + targetDir.getAbsolutePath());
            return null;
        }

        // 3. 创建目标文件
        File targetFile = new File(targetDir, fileName);

        // 4. 复制文件
        try (InputStream in = new FileInputStream(orgFile);
             OutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            // 5. 通知媒体库更新
            MediaScannerConnection.scanFile(
                    context,
                    new String[]{targetFile.getAbsolutePath()},
                    new String[]{mimeType},
                    null
            );

            return Uri.fromFile(targetFile);

        } catch (IOException e) {
            Log.e("FileSave", "文件复制失败", e);
            return null;
        }
    }

    private static Uri copyForModernAndroid(Context context, File orgFile, String folderName, String fileName, String folderType, String mimeType) {
        // Android 10 及以上使用 MediaStore 的代码（保持不变）
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folderType + "/" + folderName);

        Uri targetUri = null;
        if (Objects.equals(mimeType, "image/jpeg")){
            targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
        }

        if (targetUri == null) return null;

        try (InputStream in = new FileInputStream(orgFile);
             OutputStream out = resolver.openOutputStream(targetUri)) {

            if (out == null) return null;

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            return targetUri;

        } catch (IOException e) {
            Log.e("FileSave", "文件复制失败", e);
            resolver.delete(targetUri, null, null); // 删除无效条目
            return null;
        }
    }

    /**
     * 删除指定目录下的所有文件（保留目录本身）
     *
     * @param directory 要清空的目录
     * @return 是否成功删除所有文件
     */
    public static boolean deleteAllFilesInDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return false;
        }

        boolean success = true;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 递归删除子目录
                    success &= deleteDirectoryRecursively(file);
                } else {
                    // 删除文件
                    success &= file.delete();
                }
            }
        }

        return success;
    }

    /**
     * 递归删除目录及其所有内容
     *
     * @param directory 要删除的目录
     * @return 是否成功删除
     */
    public static boolean deleteDirectoryRecursively(File directory) {
        if (directory == null || !directory.exists()) {
            return false;
        }

        if (directory.isDirectory()) {
            File[] children = directory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectoryRecursively(child);
                }
            }
        }

        // 删除目录本身
        return directory.delete();
    }


    // 显示视图
    private static final Map<View, Boolean> animationStateMap = new WeakHashMap<>();

    public static void showContentWithAnimation(View contentLayout, Runnable onComplete) {
        showContentWithAnimation(contentLayout, null, onComplete);
    }

    public static void showContentWithAnimation(View contentLayout, Runnable contentUpdater, Runnable onComplete) {
        // 检查是否已有动画运行
        Boolean isAnimating = animationStateMap.get(contentLayout);
        if (isAnimating != null && isAnimating) {
            // 只更新内容不重启动画
            if (contentUpdater != null) contentUpdater.run();
            return;
        }

        // 标记动画开始
        animationStateMap.put(contentLayout, true);

        // 初始状态设置
        contentLayout.setVisibility(View.VISIBLE);
        contentLayout.setAlpha(0f);
        contentLayout.setScaleX(0.85f);
        contentLayout.setScaleY(0.85f);

        // 优先执行内容更新（如果有）
        if (contentUpdater != null) contentUpdater.run();

        // 启动动画
        contentLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animationStateMap.put(contentLayout, false);
                        contentLayout.animate().setListener(null);
                        if (onComplete != null) onComplete.run();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        animationStateMap.put(contentLayout, false);
                    }
                })
                .start();
    }

    /**
     * 隐藏内容时的动画，带有完成回调
     * @param contentLayout 要隐藏的内容视图
     * @param onComplete 动画完成后的回调（可为null）
     */
    public static void hideContentWithAnimation(View contentLayout, Runnable onComplete) {
        contentLayout.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(250)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        contentLayout.setVisibility(View.GONE);

                        // 清除监听器，避免内存泄漏
                        contentLayout.animate().setListener(null);

                        // 执行回调（如果提供了）
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                })
                .start();
    }

    /**
     * 重载方法，保持向后兼容性
     */
    public static void showContentWithAnimation(View contentLayout) {
        showContentWithAnimation(contentLayout, null);
    }

    /**
     * 重载方法，保持向后兼容性
     */
    public static void hideContentWithAnimation(View contentLayout) {
        hideContentWithAnimation(contentLayout, null);
    }

    private static boolean isAnimating = false;

    public static void slideLibToScreen(View lib, View screen) {
        if (isAnimating) return;
        isAnimating = true;

        // 取消所有动画
        lib.animate().cancel();
        screen.animate().cancel();

        // 启用硬件层加速
        lib.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        screen.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 滑出动画
        ObjectAnimator libSlideOut = ObjectAnimator.ofFloat(lib, "translationX", 0, -lib.getWidth());
        ObjectAnimator libFadeOut = ObjectAnimator.ofFloat(lib, "alpha", 1, 0);

        // 滑入动画（带缩放）
        screen.setTranslationX(screen.getWidth());
        screen.setVisibility(View.VISIBLE);
        screen.setAlpha(0);
        ObjectAnimator screenSlideIn = ObjectAnimator.ofFloat(screen, "translationX", screen.getWidth(), 0);
        ObjectAnimator screenFadeIn = ObjectAnimator.ofFloat(screen, "alpha", 0, 1);
        ObjectAnimator screenScaleIn = ObjectAnimator.ofFloat(screen, "scaleX", 0.95f, 1f);
        ObjectAnimator screenScaleYIn = ObjectAnimator.ofFloat(screen, "scaleY", 0.95f, 1f);

        // 设置动画属性（300ms + M3 插值器）
        FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
        libSlideOut.setInterpolator(interpolator);
        libFadeOut.setInterpolator(interpolator);
        screenSlideIn.setInterpolator(interpolator);
        screenFadeIn.setInterpolator(interpolator);
        screenScaleIn.setInterpolator(interpolator);
        screenScaleYIn.setInterpolator(interpolator);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(libSlideOut, libFadeOut, screenSlideIn, screenFadeIn, screenScaleIn, screenScaleYIn);
        animatorSet.setDuration(300);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 清理状态
                lib.setVisibility(View.GONE);
                lib.setTranslationX(0);
                lib.setAlpha(1);
                lib.setLayerType(View.LAYER_TYPE_NONE, null); // 释放硬件层

                screen.setScaleX(1f);
                screen.setScaleY(1f);
                screen.setLayerType(View.LAYER_TYPE_NONE, null); // 释放硬件层

                isAnimating = false;
            }
        });
        animatorSet.start();
    }

    public static void slideScreenToLib(View lib, View screen) {
        if (isAnimating) return;
        isAnimating = true;

        // 取消所有动画并启用硬件加速
        lib.animate().cancel();
        screen.animate().cancel();
        lib.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        screen.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 屏幕退出动画：加速曲线（符合 M3 退出规范）
        ObjectAnimator screenSlideOut = ObjectAnimator.ofFloat(screen, "translationX", 0, screen.getWidth());
        ObjectAnimator screenFadeOut = ObjectAnimator.ofFloat(screen, "alpha", 1, 0);

        // Lib 进入动画：减速曲线 + 轻微缩放（增强空间感）
        lib.setTranslationX(-lib.getWidth());
        lib.setVisibility(View.VISIBLE);
        lib.setAlpha(0);
        lib.setScaleX(0.95f); // 初始缩放 95%
        lib.setScaleY(0.95f);
        ObjectAnimator libSlideIn = ObjectAnimator.ofFloat(lib, "translationX", -lib.getWidth(), 0);
        ObjectAnimator libFadeIn = ObjectAnimator.ofFloat(lib, "alpha", 0, 1);
        ObjectAnimator libScaleInX = ObjectAnimator.ofFloat(lib, "scaleX", 0.95f, 1f);
        ObjectAnimator libScaleInY = ObjectAnimator.ofFloat(lib, "scaleY", 0.95f, 1f);

        // 统一动画属性：300ms + M3 标准插值器
        FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
        int duration = 300;
        screenSlideOut.setInterpolator(interpolator);
        screenFadeOut.setInterpolator(interpolator);
        libSlideIn.setInterpolator(interpolator);
        libFadeIn.setInterpolator(interpolator);
        libScaleInX.setInterpolator(interpolator);
        libScaleInY.setInterpolator(interpolator);
        screenSlideOut.setDuration(duration);
        screenFadeOut.setDuration(duration);
        libSlideIn.setDuration(duration);
        libFadeIn.setDuration(duration);
        libScaleInX.setDuration(duration);
        libScaleInY.setDuration(duration);

        // 组合动画集
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                screenSlideOut, screenFadeOut,
                libSlideIn, libFadeIn, libScaleInX, libScaleInY
        );

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // 清理状态：退出视图隐藏 + 重置属性
                screen.setVisibility(View.GONE);
                screen.setTranslationX(0);
                screen.setAlpha(1);
                lib.setScaleX(1f);
                lib.setScaleY(1f);
                // 释放硬件层资源
                lib.setLayerType(View.LAYER_TYPE_NONE, null);
                screen.setLayerType(View.LAYER_TYPE_NONE, null);
                isAnimating = false;
            }
        });
        animatorSet.start();
    }
}
