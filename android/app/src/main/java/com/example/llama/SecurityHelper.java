package com.example.llama;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

/**
 * 安全辅助类：封装运行时权限请求、指纹验证（调用 BiometricHelper）。
 */
public class SecurityHelper {
    private static final int REQUEST_WRITE_STORAGE = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 100;

    /**
     * 请求外部存储写入权限（用于导出数据库）。
     * @param activity 当前 Activity
     * @param callback 权限授予/拒绝回调（可为 null）
     */
    public static void requestStoragePermission(Activity activity, PermissionCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_STORAGE);
                // 注意：实际回调需要在 Activity 的 onRequestPermissionsResult 中处理
                return;
            }
        }
        if (callback != null) callback.onGranted();
    }

    /**
     * 处理权限请求结果（应在 Activity 的 onRequestPermissionsResult 中调用）。
     * @param requestCode
     * @param grantResults
     * @param callback
     */
    public static void onRequestPermissionsResult(int requestCode, int[] grantResults, PermissionCallback callback) {
        if (requestCode == REQUEST_WRITE_STORAGE || requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (callback != null) callback.onGranted();
            } else {
                if (callback != null) callback.onDenied();
            }
        }
    }

    /**
     * 显示指纹验证对话框（使用已有的 BiometricHelper）。
     * @param activity
     * @param callback
     */
    public static void verifyFingerprint(Activity activity, BiometricHelper.Callback callback) {
        BiometricHelper.authenticate(activity, callback);
    }

    public interface PermissionCallback {
        void onGranted();
        void onDenied();
    }
}