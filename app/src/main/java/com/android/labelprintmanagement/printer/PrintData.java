package com.android.labelprintmanagement.printer;

/**
 * 列印數據封裝類
 * 包含所有需要列印的標籤資訊
 */
public class PrintData {
    
    // 從QR Code獲取的固定資料
    private String partNo;           // 料號
    private String partName;         // 品名
    
    // 用戶可編輯的資料
    private String quantity;         // 數量
    private String dc;               // D/C
    private String hwVer;            // HW Ver
    private String fwVer;            // FW Ver
    
    // 建構子
    public PrintData(String partNo, String partName, String quantity, String dc, String hwVer, String fwVer) {
        this.partNo = partNo;
        this.partName = partName;
        this.quantity = quantity;
        this.dc = dc;
        this.hwVer = hwVer;
        this.fwVer = fwVer;
    }
    
    // Getter 方法
    public String getPartName() { return partName; }
    public String getPartNo() { return partNo; }
    public String getQuantity() { return quantity; }
    public String getDc() { return dc; }
    public String getHwVer() { return hwVer; }
    public String getFwVer() { return fwVer; }
    
    /**
     * 驗證列印數據是否完整
     */
    public boolean isValid() {
        return partName != null && !partName.isEmpty() &&
               partNo != null && !partNo.isEmpty() &&
               quantity != null && !quantity.isEmpty() &&
               dc != null && !dc.isEmpty(); // D/C is now mandatory from QR code
    }
    
    /**
     * 生成列印指令字符串
     * 這裡可以根據不同的列印機品牌生成相應的指令格式
     */
    public String generatePrintCommand(PrinterCommandFormat format) {
        switch (format) {
            case ESC_POS:
                return generateEscPosCommand();
            case ZPL:
                return generateZplCommand();
            case CPCL:
                return generateCpclCommand();
            default:
                return generateGenericCommand();
        }
    }
    
    /**
     * 生成ESC/POS格式指令 (適用於大多數熱敏列印機)
     */
    private String generateEscPosCommand() {
        StringBuilder cmd = new StringBuilder();
        
        // ESC/POS 初始化指令
        cmd.append("\u001B@"); // 初始化列印機
        cmd.append("\u001Ba\u0001"); // 居中對齊
        
        // 列印標題
        cmd.append("標籤列印\n");
        cmd.append("------------------------\n");
        
        // 列印內容
        cmd.append("品名: ").append(partName).append("\n");
        cmd.append("料號: ").append(partNo).append("\n");
        cmd.append("數量: ").append(quantity).append(" PCS\n");
        cmd.append("D/C: ").append(dc).append("\n");
        cmd.append("HW Ver: ").append(hwVer).append("\n");
        cmd.append("FW Ver: ").append(fwVer).append("\n");
        
        cmd.append("------------------------\n");
        cmd.append("\n\n\n"); // 走紙
        cmd.append("\u001Dm"); // 切紙
        
        return cmd.toString();
    }
    
    /**
     * 生成ZPL格式指令 (適用於Zebra列印機)
     */
    private String generateZplCommand() {
        StringBuilder cmd = new StringBuilder();
        
        cmd.append("^XA\n"); // 開始標籤格式
        cmd.append("^CF0,30\n"); // 設定字體
        
        // 列印各個欄位
        cmd.append("^FO50,50^FD品名: ").append(partName).append("^FS\n");
        cmd.append("^FO50,100^FD料號: ").append(partNo).append("^FS\n");
        cmd.append("^FO50,150^FD數量: ").append(quantity).append(" PCS^FS\n");
        cmd.append("^FO50,200^FDD/C: ").append(dc).append("^FS\n");
        cmd.append("^FO50,250^FDHW Ver: ").append(hwVer).append("^FS\n");
        cmd.append("^FO50,300^FDFW Ver: ").append(fwVer).append("^FS\n");
        
        cmd.append("^XZ\n"); // 結束標籤格式
        
        return cmd.toString();
    }
    
    /**
     * 生成CPCL格式指令 (適用於某些移動列印機)
     */
    private String generateCpclCommand() {
        StringBuilder cmd = new StringBuilder();
        
        cmd.append("! 0 200 200 400 1\n"); // 設定標籤尺寸
        
        // 列印各個欄位
        cmd.append("TEXT 4 0 30 40 品名: ").append(partName).append("\n");
        cmd.append("TEXT 4 0 30 80 料號: ").append(partNo).append("\n");
        cmd.append("TEXT 4 0 30 120 數量: ").append(quantity).append(" PCS\n");
        cmd.append("TEXT 4 0 30 160 D/C: ").append(dc).append("\n");
        cmd.append("TEXT 4 0 30 200 HW Ver: ").append(hwVer).append("\n");
        cmd.append("TEXT 4 0 30 240 FW Ver: ").append(fwVer).append("\n");
        
        cmd.append("PRINT\n"); // 執行列印
        
        return cmd.toString();
    }
    
    /**
     * 生成通用格式指令
     */
    private String generateGenericCommand() {
        StringBuilder cmd = new StringBuilder();
        
        cmd.append("品名: ").append(partName).append("\n");
        cmd.append("料號: ").append(partNo).append("\n");
        cmd.append("數量: ").append(quantity).append(" PCS\n");
        cmd.append("D/C: ").append(dc).append("\n");
        cmd.append("HW Ver: ").append(hwVer).append("\n");
        cmd.append("FW Ver: ").append(fwVer).append("\n");
        
        return cmd.toString();
    }
    
    @Override
    public String toString() {
        return "PrintData{" +
                "partName='" + partName + '\'' +
                ", partNo='" + partNo + '\'' +
                ", quantity='" + quantity + '\'' +
                ", dc='" + dc + '\'' +
                ", hwVer='" + hwVer + '\'' +
                ", fwVer='" + fwVer + '\'' +
                '}';
    }
    
    /**
     * 列印機指令格式枚舉
     */
    public enum PrinterCommandFormat {
        ESC_POS,    // ESC/POS 格式 (熱敏列印機常用)
        ZPL,        // ZPL 格式 (Zebra 列印機)
        CPCL,       // CPCL 格式 (移動列印機)
        TSC,        // TSC 格式 (TSC 列印機)
        GENERIC     // 通用格式
    }
}
