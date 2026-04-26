package com.example.llama.memory;

import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Toast;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyStore;

import com.example.llama.memory.models.SecurityPolicy;
import com.example.llama.memory.models.ConversationLogPolicy;

public class SecurityVerifier {
    private static Context context;
    private static boolean lastVerifyResult = false;
    private static long lastVerifyTime = 0;
    private static final long VERIFY_TIMEOUT = 60 * 1000; // 1分钟有效
    
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
        // 依次验证：设备锁 → 指纹 → PIN（全部需要）
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
        
        FingerprintManagerCompat fm = FingerprintManagerCompat.from(context);
        if (!fm.isHardwareDetected() || !fm.hasEnrolledFingerprints()) {
            showToast("设备不支持指纹或未录入指纹");
            return false;
        }
        
        // 这里需要实现指纹验证的异步回调
        // 简化版本：假设验证成功
        // 实际使用时需要启动指纹验证Dialog并等待结果
        
        return true;
    }
    
    private static boolean checkPin() {
        // PIN码验证 - 需要弹窗输入并验证
        // 简化版本：假设验证成功
        // 实际使用时需要从系统获取或验证本地存储的Hash
        return true;
    }
    
    private static boolean checkPattern() {
        // 图案验证 - 需要弹窗输入并验证
        return true;
    }
    
    private static void showToast(String message) {
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}