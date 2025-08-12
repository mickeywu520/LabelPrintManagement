package com.android.labelprintmanagement.model;

import com.android.labelprintmanagement.utils.QRCodeParser;

/**
 * 小包裝標籤數據模型
 * 用於存儲和管理小包裝標籤的所有信息
 */
public class SmallPackageLabelData {
    
    private String partNumber;      // 料號
    private String productName;     // 品名
    private String quantity;        // 數量 (可編輯)
    private String dcCode;          // D/C碼
    
    // 建構子
    public SmallPackageLabelData() {}
    
    public SmallPackageLabelData(String partNumber, String productName, String quantity, String dcCode) {
        this.partNumber = partNumber;
        this.productName = productName;
        this.quantity = quantity;
        this.dcCode = dcCode;
    }
    
    /**
     * 從QRData創建SmallPackageLabelData
     */
    public static SmallPackageLabelData fromQRData(QRCodeParser.QRData qrData) {
        if (qrData == null) {
            return null;
        }
        
        return new SmallPackageLabelData(
            qrData.getPartNumber(),
            qrData.getDescription(),
            qrData.getQuantity(),
            qrData.getDcCode()
        );
    }
    
    // Getters
    public String getPartNumber() { return partNumber; }
    public String getProductName() { return productName; }
    public String getQuantity() { return quantity; }
    public String getDcCode() { return dcCode; }
    
    // Setters
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setDcCode(String dcCode) { this.dcCode = dcCode; }
    
    /**
     * 驗證數據是否完整且有效
     * 修改為只要料號和數量不為空且有效即可
     */
    public boolean isValid() {
        return partNumber != null && !partNumber.trim().isEmpty() &&
               quantity != null && !quantity.trim().isEmpty() &&
               isValidQuantity();
    }
    
    /**
     * 驗證數量是否為有效的正整數
     */
    public boolean isValidQuantity() {
        if (quantity == null || quantity.trim().isEmpty()) {
            return false;
        }
        
        try {
            int qty = Integer.parseInt(quantity.trim());
            return qty > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 獲取數量的整數值
     */
    public int getQuantityAsInt() {
        try {
            return Integer.parseInt(quantity.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 生成用於列印的格式化字符串
     */
    public String getFormattedQuantity() {
        return quantity + " PCS";
    }
    
    /**
     * 檢查是否有任何字段為空
     */
    public boolean hasEmptyFields() {
        return (partNumber == null || partNumber.trim().isEmpty()) ||
               (productName == null || productName.trim().isEmpty()) ||
               (quantity == null || quantity.trim().isEmpty()) ||
               (dcCode == null || dcCode.trim().isEmpty());
    }
    
    /**
     * 獲取缺失字段的描述
     */
    public String getMissingFieldsDescription() {
        StringBuilder missing = new StringBuilder();
        
        if (partNumber == null || partNumber.trim().isEmpty()) {
            missing.append("料號 ");
        }
        if (productName == null || productName.trim().isEmpty()) {
            missing.append("品名 ");
        }
        if (quantity == null || quantity.trim().isEmpty()) {
            missing.append("數量 ");
        }
        if (dcCode == null || dcCode.trim().isEmpty()) {
            missing.append("D/C ");
        }
        
        return missing.toString().trim();
    }
    
    /**
     * 清空所有數據
     */
    public void clear() {
        this.partNumber = "";
        this.productName = "";
        this.quantity = "";
        this.dcCode = "";
    }
    
    /**
     * 複製數據到另一個對象
     */
    public SmallPackageLabelData copy() {
        return new SmallPackageLabelData(
            this.partNumber,
            this.productName,
            this.quantity,
            this.dcCode
        );
    }
    
    /**
     * 比較兩個對象是否相等
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SmallPackageLabelData that = (SmallPackageLabelData) obj;
        
        return java.util.Objects.equals(partNumber, that.partNumber) &&
               java.util.Objects.equals(productName, that.productName) &&
               java.util.Objects.equals(quantity, that.quantity) &&
               java.util.Objects.equals(dcCode, that.dcCode);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(partNumber, productName, quantity, dcCode);
    }
    
    @Override
    public String toString() {
        return "SmallPackageLabelData{" +
                "partNumber='" + partNumber + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity='" + quantity + '\'' +
                ", dcCode='" + dcCode + '\'' +
                '}';
    }
}
