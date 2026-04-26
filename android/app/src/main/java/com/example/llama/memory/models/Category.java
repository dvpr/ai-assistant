package com.example.llama.memory.models;

public class Category {
    private long id;
    private String name;
    private String displayName;
    private String policyType;
    private String policyCombination;
    private boolean isSetup;
    
    public Category(long id, String name, String displayName, String policyType, 
                    String policyCombination, boolean isSetup) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.policyType = policyType;
        this.policyCombination = policyCombination;
        this.isSetup = isSetup;
    }
    
    public long getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getPolicyType() { return policyType; }
    public String getPolicyCombination() { return policyCombination; }
    public boolean isSetup() { return isSetup; }
    
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public void setPolicyCombination(String policyCombination) { this.policyCombination = policyCombination; }
    public void setSetup(boolean setup) { isSetup = setup; }
    
    public String getDisplayString() {
        return displayName + (isSetup ? " 🔒" : " ⚠️");
    }
    
    public String getPolicyDisplay() {
        if (policyType == null) return "无验证";
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