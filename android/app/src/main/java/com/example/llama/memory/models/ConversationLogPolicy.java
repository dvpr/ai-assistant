package com.example.llama.memory.models;

public class ConversationLogPolicy {
    private long id;
    private String policyType;
    private String policyCombination;
    private boolean isSetup;
    
    public ConversationLogPolicy(long id, String policyType, String policyCombination, boolean isSetup) {
        this.id = id;
        this.policyType = policyType;
        this.policyCombination = policyCombination;
        this.isSetup = isSetup;
    }
    
    public long getId() { return id; }
    public String getPolicyType() { return policyType; }
    public String getPolicyCombination() { return policyCombination; }
    public boolean isSetup() { return isSetup; }
    
    public String getDisplayString() {
        if (!isSetup) return "未设置（使用默认高安全级别）";
        switch (policyType) {
            case "fingerprint": return "指纹验证";
            case "pin": return "PIN码验证";
            case "pattern": return "图案验证";
            case "device_lock": return "设备锁";
            case "combination": return policyCombination;
            default: return policyType;
        }
    }
}