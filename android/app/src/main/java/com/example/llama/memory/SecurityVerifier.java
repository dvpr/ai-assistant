package com.example.llama.memory;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.widget.Toast;

import com.example.llama.memory.models.SecurityPolicy;
import com.example.llama.memory.models.ConversationLogPolicy;

public class SecurityVerifier {
    private static Context context;
    private static boolean lastVerifyResult = false;
    private static long lastVerifyTime = 0;
    private static final long VERIFY_TIMEOUT = 5 * 60 * 1000; // 5分钟有效

    public static void init(Context appContext) {
        context = appContext.getApplicationContext();
    }

    // 验证分类安全策略
    public static boolean verifyCategory(SecurityPolicy policy) {
        if (policy == null || policy.getPolicyType() == null) {
            return true; // 无策略，直接通过
        }

        if (lastVerifyResult && (System.currentTimeMillis() - lastVerifyTime) < VERIFY_TIMEOUT) {
            return true;
        }

        boolean result = verifyInternal(policy);
        if (result) {
            lastVerifyResult = true;
            lastVerifyTime = System.currentTimeMillis();
        }
        return result;
    }

    // 验证对话日志访问（最高安全级别）
    public static boolean verifyConversationLog(ConversationLogPolicy policy) {
        if (policy != null && policy.isSetup() && policy.getPolicyType() != null) {
            SecurityPolicy secPolicy = new SecurityPolicy(policy.getPolicyType(), policy.getPolicyCombination());
            return verifyInternal(secPolicy);
        }
        // 默认最高安全：指纹 + PIN + 设备锁
        return verifyHighestSecurity();
    }

    // 管理员验证（修改安全策略时使用）
    public static boolean verifyAdmin() {
        // 管理员验证：需要指纹 + PIN
        SecurityPolicy adminPolicy = new SecurityPolicy("combination", "fingerprint AND pin");
        return verifyInternal(adminPolicy);
    }

    private static boolean verifyInternal(SecurityPolicy policy) {
        if (policy.isRequireDeviceLock()) {
            if (!checkDeviceLock()) {
                showToast("请先解锁手机");
                return false;
            }
        }

        if (policy.isRequireFingerprint()) {
            if (!checkFingerprint()) {
                showToast("指纹验证失败");
                return false;
            }
        }

        if (policy.isRequirePin()) {
            if (!checkPin()) {
                showToast("PIN码验证失败");
                return false;
            }
        }

        if (policy.isRequirePattern()) {
            if (!checkPattern()) {
                showToast("图案验证失败");
                return false;
            }
        }

        return true;
    }

    private static boolean verifyHighestSecurity() {
        if (!checkDeviceLock()) {
            showToast("请先解锁手机");
            return false;
        }
        if (!checkFingerprint()) {
            showToast("指纹验证失败");
            return false;
        }
        if (!checkPin()) {
            showToast("PIN码验证失败");
            return false;
        }
        return true;
    }

    private static boolean checkDeviceLock() {
        if (context == null) return false;
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardSecure();
    }

    private static boolean checkFingerprint() {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

        // 使用原生 FingerprintManager
        FingerprintManager fm = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        if (fm == null) return false;
        if (!fm.isHardwareDetected()) {
            showToast("设备不支持指纹识别");
            return false;
        }
        if (!fm.hasEnrolledFingerprints()) {
            showToast("请先在系统设置中录入指纹");
            return false;
        }

        // 实际验证需要启动一个异步对话框，这里简化返回 true
        // TODO: 实现真正的指纹验证对话框，并等待结果
        showToast("指纹验证功能需要实现对话框（当前简化通过）");
        return true;
    }

    private static boolean checkPin() {
        // TODO: 实现 PIN 码验证弹窗并验证
        showToast("PIN码验证需要实现（当前简化通过）");
        return true;
    }

    private static boolean checkPattern() {
        // TODO: 实现图案验证弹窗并验证
        showToast("图案验证需要实现（当前简化通过）");
        return true;
    }

    private static void showToast(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}