package com.android.labelprintmanagement.utils;

/**
 * QR碼數據解析器
 * 用於解析小包裝標籤的QR碼內容
 */
public class QRCodeParser {
    
    /**
     * QR碼數據模型
     */
    public static class QRData {
        private String partNumber;    // 料號 (PN)
        private String description;   // 品名 (DES)
        private String quantity;      // 數量 (QTY)
        private String dcCode;        // D/C碼 (DC)
        
        public QRData() {}
        
        public QRData(String partNumber, String description, String quantity, String dcCode) {
            this.partNumber = partNumber;
            this.description = description;
            this.quantity = quantity;
            this.dcCode = dcCode;
        }
        
        // Getters
        public String getPartNumber() { return partNumber; }
        public String getDescription() { return description; }
        public String getQuantity() { return quantity; }
        public String getDcCode() { return dcCode; }
        
        // Setters
        public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
        public void setDescription(String description) { this.description = description; }
        public void setQuantity(String quantity) { this.quantity = quantity; }
        public void setDcCode(String dcCode) { this.dcCode = dcCode; }
        
        /**
         * 驗證數據是否完整
         */
        public boolean isValid() {
            return partNumber != null && !partNumber.trim().isEmpty() &&
                   description != null && !description.trim().isEmpty() &&
                   quantity != null && !quantity.trim().isEmpty() &&
                   dcCode != null && !dcCode.trim().isEmpty();
        }
        
        @Override
        public String toString() {
            return "QRData{" +
                    "partNumber='" + partNumber + '\'' +
                    ", description='" + description + '\'' +
                    ", quantity='" + quantity + '\'' +
                    ", dcCode='" + dcCode + '\'' +
                    '}';
        }
    }
    
    /**
     * 解析QR碼內容
     * 
     * 預期格式:
     * PN:1710002190000P
     * DES:FPC-7602 BOTTOM COVER BRACKET
     * QTY:1000
     * DC:250601
     * 
     * @param qrContent QR碼掃描的原始內容
     * @return 解析後的QRData對象，如果解析失敗返回null
     */
    public static QRData parseQRContent(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            return null;
        }
        
        QRData qrData = new QRData();
        
        try {
            // 按行分割內容
            String[] lines = qrContent.trim().split("\\r?\\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // 查找冒號分隔符
                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) continue;
                
                String key = line.substring(0, colonIndex).trim().toUpperCase();
                String value = line.substring(colonIndex + 1).trim();
                
                // 根據鍵值設置對應的字段
                switch (key) {
                    case "PN":
                        qrData.setPartNumber(value);
                        break;
                    case "DES":
                        qrData.setDescription(value);
                        break;
                    case "QTY":
                        qrData.setQuantity(value);
                        break;
                    case "DC":
                        qrData.setDcCode(value);
                        break;
                }
            }
            
            // 驗證解析結果
            if (qrData.isValid()) {
                return qrData;
            } else {
                return null;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 解析舊格式的QR碼內容（兼容性方法）
     * 
     * 預期格式:
     * 料號:1710002190000P 
     * 品名:FPC-7602 BOTTOM COVER BRACKET 
     * 數量:1000 
     * D/C:250601
     */
    public static QRData parseQRContentLegacy(String qrContent) {
        if (qrContent == null || qrContent.trim().isEmpty()) {
            return null;
        }
        
        QRData qrData = new QRData();
        
        try {
            // 按行分割內容
            String[] lines = qrContent.trim().split("\\r?\\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                // 查找冒號分隔符
                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) continue;
                
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                
                // 根據中文鍵值設置對應的字段
                switch (key) {
                    case "料號":
                        qrData.setPartNumber(value);
                        break;
                    case "品名":
                        qrData.setDescription(value);
                        break;
                    case "數量":
                        qrData.setQuantity(value);
                        break;
                    case "D/C":
                        qrData.setDcCode(value);
                        break;
                }
            }
            
            // 驗證解析結果
            if (qrData.isValid()) {
                return qrData;
            } else {
                return null;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 智能解析QR碼內容，自動檢測格式
     */
    public static QRData smartParseQRContent(String qrContent) {
        // 先嘗試新格式
        QRData result = parseQRContent(qrContent);
        if (result != null) {
            return result;
        }
        
        // 如果新格式失敗，嘗試舊格式
        return parseQRContentLegacy(qrContent);
    }
}
