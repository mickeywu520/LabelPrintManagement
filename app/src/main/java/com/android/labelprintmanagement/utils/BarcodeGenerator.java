package com.android.labelprintmanagement.utils;

/**
 * 條碼生成工具類
 * 用於生成Code 39和Code 128條碼的列印指令
 * 符合最小5 mil (0.127 mm)寬度要求
 */
public class BarcodeGenerator {
    
    // 條碼類型枚舉
    public enum BarcodeType {
        CODE39,
        CODE128
    }
    
    // 使用 PrintData 中已定義的列印機指令格式枚舉
    // public enum PrinterFormat 已移除，使用 PrintData.PrinterCommandFormat
    
    /**
     * 條碼配置類
     */
    public static class BarcodeConfig {
        private int width;          // 條碼寬度 (dots)
        private int height;         // 條碼高度 (dots)
        private int x;              // X座標
        private int y;              // Y座標
        private boolean showText;   // 是否顯示文字
        private int textSize;       // 文字大小
        
        public BarcodeConfig(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.showText = true;
            this.textSize = 20;
        }
        
        // Getters and Setters
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        
        public boolean isShowText() { return showText; }
        public void setShowText(boolean showText) { this.showText = showText; }
        
        public int getTextSize() { return textSize; }
        public void setTextSize(int textSize) { this.textSize = textSize; }
    }
    
    /**
     * 生成ZPL格式的條碼指令
     */
    public static String generateZPLBarcode(String data, BarcodeType type, BarcodeConfig config) {
        StringBuilder cmd = new StringBuilder();
        
        // 設置條碼位置
        cmd.append("^FO").append(config.getX()).append(",").append(config.getY());
        
        // 根據條碼類型生成指令
        switch (type) {
            case CODE39:
                // Code 39條碼，確保最小5 mil寬度
                // ^B3 = Code 39, o = 方向(正常), N = 檢查碼, Y = 顯示文字, N = 顯示檢查碼
                cmd.append("^B3o,N,").append(config.getHeight()).append(",")
                   .append(config.isShowText() ? "Y" : "N").append(",N");
                break;
                
            case CODE128:
                // Code 128條碼，確保最小5 mil寬度
                // ^BC = Code 128, o = 方向(正常), Y = 顯示文字
                cmd.append("^BCo,").append(config.getHeight()).append(",")
                   .append(config.isShowText() ? "Y" : "N").append(",N,N");
                break;
        }
        
        // 添加數據
        cmd.append("^FD").append(data).append("^FS\n");
        
        return cmd.toString();
    }
    
    /**
     * 生成ESC/POS格式的條碼指令
     */
    public static String generateESCPOSBarcode(String data, BarcodeType type, BarcodeConfig config) {
        StringBuilder cmd = new StringBuilder();
        
        // ESC/POS條碼指令
        cmd.append("\u001B@"); // 初始化列印機
        
        // 設置條碼高度 (ESC h n)
        cmd.append("\u001Bh").append((char)(config.getHeight() / 8));
        
        // 設置條碼寬度 (ESC w n) - 確保最小5 mil
        cmd.append("\u001Bw").append((char)3); // 寬度倍數
        
        // 設置HRI字符位置
        if (config.isShowText()) {
            cmd.append("\u001BH").append((char)2); // 條碼下方顯示
        } else {
            cmd.append("\u001BH").append((char)0); // 不顯示
        }
        
        // 根據條碼類型設置
        switch (type) {
            case CODE39:
                cmd.append("\u001Bk").append((char)4); // Code 39
                break;
            case CODE128:
                cmd.append("\u001Bk").append((char)73); // Code 128
                break;
        }
        
        // 添加數據長度和數據
        cmd.append((char)data.length()).append(data);
        
        return cmd.toString();
    }
    
    /**
     * 生成CPCL格式的條碼指令
     */
    public static String generateCPCLBarcode(String data, BarcodeType type, BarcodeConfig config) {
        StringBuilder cmd = new StringBuilder();
        
        String barcodeCmd;
        switch (type) {
            case CODE39:
                barcodeCmd = "BARCODE";
                break;
            case CODE128:
                barcodeCmd = "BARCODE-128";
                break;
            default:
                barcodeCmd = "BARCODE";
        }
        
        // CPCL條碼指令格式: BARCODE type x y height data
        cmd.append(barcodeCmd).append(" ")
           .append(config.getX()).append(" ")
           .append(config.getY()).append(" ")
           .append(config.getHeight()).append(" ")
           .append(data).append("\n");
        
        return cmd.toString();
    }
    
    /**
     * 根據列印機格式生成條碼指令
     * TODO: 等待 TSC SDK 整合後實現完整功能
     */
    public static String generateBarcode(String data, BarcodeType type, BarcodeConfig config, String format) {
        // 暫時只支援 ZPL 格式，等待 TSC SDK 整合
        return generateZPLBarcode(data, type, config);
    }
    
    /**
     * 為小包裝標籤生成所有必要的條碼
     * 包括料號、數量、D/C的條碼
     */
    public static class SmallPackageBarcodes {
        private String partNumberBarcode;
        private String quantityBarcode;
        private String dcBarcode;
        
        public SmallPackageBarcodes(String partNumberBarcode, String quantityBarcode, String dcBarcode) {
            this.partNumberBarcode = partNumberBarcode;
            this.quantityBarcode = quantityBarcode;
            this.dcBarcode = dcBarcode;
        }
        
        public String getPartNumberBarcode() { return partNumberBarcode; }
        public String getQuantityBarcode() { return quantityBarcode; }
        public String getDcBarcode() { return dcBarcode; }
        
        public String getAllBarcodes() {
            return partNumberBarcode + quantityBarcode + dcBarcode;
        }
    }
    
    /**
     * 為小包裝標籤生成完整的條碼組合
     * TODO: 等待 TSC SDK 整合後實現完整功能
     */
    public static SmallPackageBarcodes generateSmallPackageBarcodes(
            String partNumber, String quantity, String dcCode, String format) {

        // 料號條碼配置 (位置根據90x50mm標籤調整)
        BarcodeConfig partConfig = new BarcodeConfig(50, 30, 200, 40);
        partConfig.setShowText(false); // 料號條碼不顯示文字，因為上方已有文字

        // 數量條碼配置
        BarcodeConfig qtyConfig = new BarcodeConfig(50, 180, 120, 35);
        qtyConfig.setShowText(false);

        // D/C條碼配置
        BarcodeConfig dcConfig = new BarcodeConfig(200, 180, 120, 35);
        dcConfig.setShowText(false);

        // 生成條碼指令 (暫時使用 ZPL 格式)
        String partBarcode = generateBarcode(partNumber, BarcodeType.CODE128, partConfig, "ZPL");
        String qtyBarcode = generateBarcode(quantity, BarcodeType.CODE39, qtyConfig, "ZPL");
        String dcBarcode = generateBarcode(dcCode, BarcodeType.CODE39, dcConfig, "ZPL");

        return new SmallPackageBarcodes(partBarcode, qtyBarcode, dcBarcode);
    }
}
