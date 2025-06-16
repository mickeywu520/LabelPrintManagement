package com.android.labelprintmanagement.printer;

import org.json.JSONObject;

/**
 * 列印數據封裝類
 * 包含所有需要列印的標籤資訊
 */
public class PrintData {
    
    // 從伺服器獲取的固定資料
    private String vendor;           // 廠商
    private String partName;         // 品名
    private String partNo;           // 料號
    private String date;             // 日期
    private String docType;          // 進貨單別
    private String docNumber;        // 進貨單號
    private String docItem;          // 進貨項次
    private String vendorCode;       // 供應廠商代號
    private String spec;             // 規格
    private String month;            // 月份
    
    // 用戶可編輯的資料
    private String quantity;         // 數量
    private String dc;               // D/C
    private String hwVer;            // HW Ver
    private String fwVer;            // FW Ver
    
    // 建構子
    public PrintData() {}
    
    public PrintData(JSONObject fetchedData, String quantity, String dc, String hwVer, String fwVer) {
        try {
            this.vendor = fetchedData.getString("供應廠商名稱");
            this.partName = fetchedData.getString("品名");
            this.partNo = fetchedData.getString("品號");
            
            String dateStr = fetchedData.getString("進貨日期");
            this.date = dateStr.substring(0, 4) + "/" + dateStr.substring(4, 6) + "/" + dateStr.substring(6, 8);
            this.month = dateStr.substring(4, 6);
            
            this.docType = fetchedData.getString("進貨單別");
            this.docNumber = fetchedData.getString("進貨單號");
            this.docItem = fetchedData.getString("進貨項次(序號)");
            this.vendorCode = fetchedData.getString("供應廠商代號");
            this.spec = fetchedData.getString("規格");
            
            this.quantity = quantity;
            this.dc = dc;
            this.hwVer = hwVer;
            this.fwVer = fwVer;
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Getter 和 Setter 方法
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    
    public String getPartNo() { return partNo; }
    public void setPartNo(String partNo) { this.partNo = partNo; }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    
    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }
    
    public String getDocItem() { return docItem; }
    public void setDocItem(String docItem) { this.docItem = docItem; }
    
    public String getVendorCode() { return vendorCode; }
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    
    public String getDc() { return dc; }
    public void setDc(String dc) { this.dc = dc; }
    
    public String getHwVer() { return hwVer; }
    public void setHwVer(String hwVer) { this.hwVer = hwVer; }
    
    public String getFwVer() { return fwVer; }
    public void setFwVer(String fwVer) { this.fwVer = fwVer; }
    
    /**
     * 驗證列印數據是否完整
     */
    public boolean isValid() {
        return vendor != null && !vendor.isEmpty() &&
               partName != null && !partName.isEmpty() &&
               partNo != null && !partNo.isEmpty() &&
               quantity != null && !quantity.isEmpty();
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
        cmd.append("廠商: ").append(vendor).append("\n");
        cmd.append("品名: ").append(partName).append("\n");
        cmd.append("料號: ").append(partNo).append("\n");
        cmd.append("數量: ").append(quantity).append(" PCS\n");
        cmd.append("D/C: ").append(dc).append("\n");
        cmd.append("HW Ver: ").append(hwVer).append("\n");
        cmd.append("FW Ver: ").append(fwVer).append("\n");
        cmd.append("日期: ").append(date).append("\n");
        
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
        cmd.append("^FO50,50^FD廠商: ").append(vendor).append("^FS\n");
        cmd.append("^FO50,100^FD品名: ").append(partName).append("^FS\n");
        cmd.append("^FO50,150^FD料號: ").append(partNo).append("^FS\n");
        cmd.append("^FO50,200^FD數量: ").append(quantity).append(" PCS^FS\n");
        cmd.append("^FO50,250^FDD/C: ").append(dc).append("^FS\n");
        cmd.append("^FO50,300^FDHW Ver: ").append(hwVer).append("^FS\n");
        cmd.append("^FO50,350^FDFW Ver: ").append(fwVer).append("^FS\n");
        cmd.append("^FO50,400^FD日期: ").append(date).append("^FS\n");
        
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
        cmd.append("TEXT 4 0 30 40 廠商: ").append(vendor).append("\n");
        cmd.append("TEXT 4 0 30 80 品名: ").append(partName).append("\n");
        cmd.append("TEXT 4 0 30 120 料號: ").append(partNo).append("\n");
        cmd.append("TEXT 4 0 30 160 數量: ").append(quantity).append(" PCS\n");
        cmd.append("TEXT 4 0 30 200 D/C: ").append(dc).append("\n");
        cmd.append("TEXT 4 0 30 240 HW Ver: ").append(hwVer).append("\n");
        cmd.append("TEXT 4 0 30 280 FW Ver: ").append(fwVer).append("\n");
        cmd.append("TEXT 4 0 30 320 日期: ").append(date).append("\n");
        
        cmd.append("PRINT\n"); // 執行列印
        
        return cmd.toString();
    }
    
    /**
     * 生成通用格式指令
     */
    private String generateGenericCommand() {
        StringBuilder cmd = new StringBuilder();
        
        cmd.append("廠商: ").append(vendor).append("\n");
        cmd.append("品名: ").append(partName).append("\n");
        cmd.append("料號: ").append(partNo).append("\n");
        cmd.append("數量: ").append(quantity).append(" PCS\n");
        cmd.append("D/C: ").append(dc).append("\n");
        cmd.append("HW Ver: ").append(hwVer).append("\n");
        cmd.append("FW Ver: ").append(fwVer).append("\n");
        cmd.append("日期: ").append(date).append("\n");
        
        return cmd.toString();
    }
    
    @Override
    public String toString() {
        return "PrintData{" +
                "vendor='" + vendor + '\'' +
                ", partName='" + partName + '\'' +
                ", partNo='" + partNo + '\'' +
                ", quantity='" + quantity + '\'' +
                ", dc='" + dc + '\'' +
                ", hwVer='" + hwVer + '\'' +
                ", fwVer='" + fwVer + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
    
    /**
     * 列印機指令格式枚舉
     */
    public enum PrinterCommandFormat {
        ESC_POS,    // ESC/POS 格式 (熱敏列印機常用)
        ZPL,        // ZPL 格式 (Zebra 列印機)
        CPCL,       // CPCL 格式 (移動列印機)
        GENERIC     // 通用格式
    }
}
