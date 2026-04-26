package com.example.llama.memory.models;

public class SecurityPolicy {
    public static final String TYPE_FINGERPRINT = "fingerprint";
    public static final String TYPE_PIN = "pin";
    public static final String TYPE_PATTERN = "pattern";
    public static final String TYPE_DEVICE_LOCK = "device_lock";
    public static final String TYPE_COMBINATION = "combination";
    
    public static final String COMBO_AND = "AND";
    public static final String COMBO_OR = "OR";
    
    private String policyType;
    private String policyCombination;
    private boolean requireFingerprint;
    private boolean requirePin;
    private boolean requirePattern;
    private boolean requireDeviceLock;
    private String comboLogic;
    
    public SecurityPolicy(String policyType, String policyCombination) {
        this.policyType = policyType;
        this.policyCombination = policyCombination;
        parseCombination();
    }
    
    private void parseCombination() {
        if (policyType.equals(TYPE_COMBINATION) && policyCombination != null) {
            String lower = policyCombination.toLowerCase();
            requireFingerprint = lower.contains("fingerprint");
            requirePin = lower.contains("pin");
            requirePattern = lower.contains("pattern");
            requireDeviceLock = lower.contains("device_lock");
            comboLogic = lower.contains(" and ") ? COMBO_AND : COMBO_OR;
        } else if (policyType != null) {
            requireFingerprint = policyType.equals(TYPE_FINGERPRINT);
            requirePin = policyType.equals(TYPE_PIN);
            requirePattern = policyType.equals(TYPE_PATTERN);
            requireDeviceLock = policyType.equals(TYPE_DEVICE_LOCK);
            comboLogic = COMBO_OR;
        }
    }
    
    public String getPolicyType() { return policyType; }
    public boolean isRequireFingerprint() { return requireFingerprint; }
    public boolean isRequirePin() { return requirePin; }
    public boolean isRequirePattern() { return requirePattern; }
    public boolean isRequireDeviceLock() { return requireDeviceLock; }
    public String getComboLogic() { return comboLogic; }
    public boolean isAndLogic() { return COMBO_AND.equals(comboLogic); }
    
    public String getDisplayString() {
        if (policyType == null) return "无验证";
        switch (policyType) {
            case TYPE_FINGERPRINT: return "指纹验证";
            case TYPE_PIN: return "PIN码验证";
            case TYPE_PATTERN: return "图案验证";
            case TYPE_DEVICE_LOCK: return "设备锁";
            case TYPE_COMBINATION:
                String logic = isAndLogic() ? "且" : "或";
                String methods = "";
                if (requireFingerprint) methods += "指纹";
                if (requirePin) methods += (methods.isEmpty() ? "" : logic) + "PIN";
                if (requirePattern) methods += (methods.isEmpty() ? "" : logic) + "图案";
                if (requireDeviceLock) methods += (methods.isEmpty() ? "" : logic) + "设备锁";
                return methods;
            default: return policyType;
        }
    }
}