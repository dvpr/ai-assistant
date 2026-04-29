// BiometricHelper.java
package com.example.llama;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

public class BiometricHelper {

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * 启动指纹验证（自动适配 Android 6.0 以上版本）
     * @param activity 当前 Activity
     * @param callback 回调
     */
    public static void authenticate(Activity activity, Callback callback) {
        // 先检查权限（假设已经在 Activity 中请求并授予）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.checkSelfPermission(android.Manifest.permission.USE_FINGERPRINT)
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onError("缺少指纹权限");
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            authenticateWithBiometricPrompt(activity, callback);
        } else {
            authenticateWithFingerprintManager(activity, callback);
        }
    }

    public static boolean isHardwareSupported(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FingerprintManager fm = (FingerprintManager) activity.getSystemService(Context.FINGERPRINT_SERVICE);
            return fm != null && fm.isHardwareDetected() && fm.hasEnrolledFingerprints();
        }
        return false;
    }

    // ==================== Android 9+ (API 28+) ====================
    private static void authenticateWithBiometricPrompt(Activity activity, Callback callback) {
        android.hardware.biometrics.BiometricPrompt.Builder builder =
                new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                        .setTitle("身份验证")
                        .setSubtitle("请验证指纹以继续")
                        .setDescription("用于访问敏感信息")
                        .setNegativeButton("取消", activity.getMainExecutor(),
                                (dialog, which) -> callback.onError("用户取消"));

        android.hardware.biometrics.BiometricPrompt.AuthenticationCallback authCallback =
                new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        callback.onError("验证错误: " + errString);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        callback.onError("指纹验证失败，请重试");
                    }
                };

        android.hardware.biometrics.BiometricPrompt prompt = builder.build();
        prompt.authenticate(new CancellationSignal(), activity.getMainExecutor(), authCallback);
    }

    // ==================== Android 6.0~8.1 (API 23-27) ====================
    private static void authenticateWithFingerprintManager(Activity activity, Callback callback) {
        FingerprintManager fm = (FingerprintManager) activity.getSystemService(Context.FINGERPRINT_SERVICE);
        if (fm == null) {
            callback.onError("设备不支持指纹识别");
            return;
        }

        // 再次确认硬件支持（包括是否已录入指纹）
        if (!fm.isHardwareDetected()) {
            callback.onError("设备不支持指纹识别");
            return;
        }
        if (!fm.hasEnrolledFingerprints()) {
            callback.onError("请先在系统设置中录入指纹");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("指纹验证")
                .setMessage("请将手指放在指纹传感器上")
                .setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        CancellationSignal cancelSignal = new CancellationSignal();
        FingerprintManager.AuthenticationCallback authCallback = new FingerprintManager.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                dialog.dismiss();
                callback.onError("验证错误: " + errString);
            }

            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                dialog.dismiss();
                callback.onSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                // 可更新提示，此处简单 Toast
                Toast.makeText(activity, "指纹不匹配，请重试", Toast.LENGTH_SHORT).show();
            }
        };

        fm.authenticate(null, cancelSignal, 0, authCallback, null);
    }
}