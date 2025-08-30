package com.wyywn.anicam.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.wyywn.anicam.MainActivity;
import com.wyywn.anicam.R;
import com.wyywn.anicam.utils.Functions;
import com.wyywn.anicam.utils.TextViewHint;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SettingsPreferenceFragment extends PreferenceFragmentCompat {

    private Preference importHTML;

    File clockHTMLDestDir;

    private final ActivityResultLauncher<String> pickFileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            handleSelectedFile(uri);
                        }
                    });
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey);

        //exDataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();

        TextViewHint.init(requireActivity().findViewById(R.id.info_textView));

        importHTML = findPreference("importClockHTML");
        clockHTMLDestDir = new File(getContext().getFilesDir(), "time_data");

        if (findPreference("about") != null) {
            findPreference("about").setOnPreferenceClickListener(preference -> {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext(), R.style.M3AlertDialogTheme);
                View dialogView = getLayoutInflater().inflate(R.layout.layout_webview, null);
                builder.setView(dialogView);

                AlertDialog dialog = builder.create();

                dialog.show(); // 注意：要先 show() 再获取视图

                WebView webView = dialogView.findViewById(R.id.layoutWebview);
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);

                webView.loadUrl("file:///android_asset/about.html");
                return true;
            });
        }

        if (importHTML != null) {
            importHTML.setOnPreferenceClickListener(preference -> {
                pickFileLauncher.launch("application/zip");
                return true;
            });
        }

        if (findPreference("deleteImportedHTML") != null) {
            findPreference("deleteImportedHTML").setOnPreferenceClickListener(preference -> {
                if (clockHTMLDestDir.exists()) {
                    boolean isDeleted = Functions.deleteAllFilesInDirectory(clockHTMLDestDir);
                    if (isDeleted){
                        TextViewHint.showText(getString(R.string.info_importedFileDeleted));
                    }
                }
                return true;
            });
        }

        if (findPreference("exportClockExample") != null) {
            findPreference("exportClockExample").setOnPreferenceClickListener(preference -> {
                File exampleFile = Functions.getFileFromAssets(getContext(), "time/example.zip", "cache");
                Uri outputUri = Functions.copyInteriorToStorage(getContext(), exampleFile, "", "example.zip", Environment.DIRECTORY_DOWNLOADS, "application/zip");
                assert exampleFile != null;
                exampleFile.delete();
                if (outputUri != null){
                    TextViewHint.showText(getString(R.string.info_exampleFileSaved));
                }
                return true;
            });
        }

        if (findPreference("license") != null) {
            findPreference("license").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(getActivity(), OssLicensesMenuActivity.class));
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.title_openSourceLicense));
                return true;
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TextViewHint.init(null);
        TextViewHint.resetState();
        Functions.hideContentWithAnimation(requireActivity().findViewById(R.id.info_textView));
    }

    @SuppressLint("StaticFieldLeak")
    private void handleSelectedFile(Uri uri){
        if (uri == null) return;

        TextViewHint.showText(getString(R.string.info_unzipping));
        // 在后台线程执行解压任务
        new AsyncTask<Void, Integer, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {

                    ZipEntry entry;
                    int fileCount = 0;

                    if (clockHTMLDestDir.exists()) {
                        Functions.deleteAllFilesInDirectory(clockHTMLDestDir);
                    } else {
                        clockHTMLDestDir.mkdirs();
                    }

                    while ((entry = zis.getNextEntry()) != null) {
                        fileCount++;
                        String entryName = entry.getName();
                        File outputFile = new File(clockHTMLDestDir, entryName);

                        // 安全校验：确保文件在目标目录内
                        String canonicalPath = outputFile.getCanonicalPath();
                        if (!canonicalPath.startsWith(clockHTMLDestDir.getCanonicalPath() + File.separator)) {
                            throw new SecurityException("路径遍历攻击尝试: " + entryName);
                        }

                        if (entry.isDirectory()) {
                            // 创建目录
                            if (!outputFile.exists()) {
                                outputFile.mkdirs();
                            }
                        } else {
                            // 确保父目录存在
                            File parent = outputFile.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }

                            // 写入文件
                            try (FileOutputStream fos = new FileOutputStream(outputFile);
                                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = zis.read(buffer)) != -1) {
                                    bos.write(buffer, 0, len);
                                }
                                bos.flush();
                            }
                        }
                        zis.closeEntry();

                        // 每解压5个文件更新一次进度
                        if (fileCount % 5 == 0) {
                            publishProgress(fileCount);
                        }
                    }
                    return true; // 解压成功
                } catch (Exception e) {
                    Log.e("ZIP", "解压错误", e);
                    return false; // 解压失败
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                TextViewHint.showText(getString(R.string.hint_unzipped_files_prefix) + values[0] + getString(R.string.hint_unzipped_files_suffix));
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    //TextViewHint.showText("解压完成！目录: " + clockHTMLDestDir.getAbsolutePath());
                    TextViewHint.showText(R.string.hint_importDone);
                } else {
                    TextViewHint.showText(R.string.hint_importFailed);
                }
            }
        }.execute();
    }
}