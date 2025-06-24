package com.android.labelprintmanagement.printer;

import com.android.labelprintmanagement.model.SmallPackageLabelData;
import com.android.labelprintmanagement.utils.BarcodeGenerator;

/**
 * 小包裝標籤列印數據類
 * 專門處理90x50mm小包裝標籤的列印指令生成
 */
public class SmallPackagePrintData {
    
    private SmallPackageLabelData labelData;
    
    public SmallPackagePrintData(SmallPackageLabelData labelData) {
        this.labelData = labelData;
    }
    
    /**
     * 驗證列印數據是否完整
     */
    public boolean isValid() {
        return labelData != null && labelData.isValid();
    }
    
    /**
     * 生成ZPL格式的90x50mm標籤列印指令
     * 標籤尺寸: 90mm x 50mm (約354 x 197 dots at 203 DPI)
     */
    public String generateZPLCommand() {
        if (!isValid()) {
            return "";
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // 開始標籤格式
        cmd.append("^XA\n");
        
        // 設定標籤尺寸 (90mm x 50mm)
        cmd.append("^LL197\n");  // 標籤長度 197 dots (50mm)
        cmd.append("^PW354\n");  // 標籤寬度 354 dots (90mm)
        
        // 設定列印方向和密度
        cmd.append("^PR4\n");    // 列印速度
        
        // 料號標題和內容 (頂部)
        cmd.append("^FO10,10^A0N,20,20^FD料號:^FS\n");
        cmd.append("^FO60,10^A0N,20,20^FD").append(labelData.getPartNumber()).append("^FS\n");
        
        // 料號條碼 (Code 128, 位置在料號文字下方)
        BarcodeGenerator.BarcodeConfig partBarcodeConfig = new BarcodeGenerator.BarcodeConfig(10, 35, 300, 30);
        partBarcodeConfig.setShowText(false);
        String partBarcode = BarcodeGenerator.generateZPLBarcode(
            labelData.getPartNumber(), 
            BarcodeGenerator.BarcodeType.CODE128, 
            partBarcodeConfig
        );
        cmd.append(partBarcode);
        
        // 品名 (中間位置)
        cmd.append("^FO10,75^A0N,16,16^FD品名:^FS\n");
        
        // 品名內容 - 處理長文字換行
        String productName = labelData.getProductName();
        if (productName.length() > 25) {
            // 如果品名太長，分兩行顯示
            String line1 = productName.substring(0, Math.min(25, productName.length()));
            String line2 = productName.length() > 25 ? productName.substring(25) : "";
            cmd.append("^FO50,75^A0N,16,16^FD").append(line1).append("^FS\n");
            if (!line2.isEmpty()) {
                cmd.append("^FO50,95^A0N,16,16^FD").append(line2).append("^FS\n");
            }
        } else {
            cmd.append("^FO50,75^A0N,16,16^FD").append(productName).append("^FS\n");
        }
        
        // 底部左側 - 數量
        cmd.append("^FO10,130^A0N,16,16^FD數量:").append(labelData.getFormattedQuantity()).append("^FS\n");
        
        // 數量條碼 (Code 39)
        BarcodeGenerator.BarcodeConfig qtyBarcodeConfig = new BarcodeGenerator.BarcodeConfig(10, 150, 120, 25);
        qtyBarcodeConfig.setShowText(false);
        String qtyBarcode = BarcodeGenerator.generateZPLBarcode(
            labelData.getQuantity(), 
            BarcodeGenerator.BarcodeType.CODE39, 
            qtyBarcodeConfig
        );
        cmd.append(qtyBarcode);
        
        // 底部右側 - D/C
        cmd.append("^FO200,130^A0N,16,16^FDD/C: ").append(labelData.getDcCode()).append("^FS\n");
        
        // D/C條碼 (Code 39)
        BarcodeGenerator.BarcodeConfig dcBarcodeConfig = new BarcodeGenerator.BarcodeConfig(200, 150, 120, 25);
        dcBarcodeConfig.setShowText(false);
        String dcBarcode = BarcodeGenerator.generateZPLBarcode(
            labelData.getDcCode(), 
            BarcodeGenerator.BarcodeType.CODE39, 
            dcBarcodeConfig
        );
        cmd.append(dcBarcode);
        
        // 結束標籤格式
        cmd.append("^XZ\n");
        
        return cmd.toString();
    }
    
    /**
     * 生成ESC/POS格式的列印指令
     */
    public String generateESCPOSCommand() {
        if (!isValid()) {
            return "";
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // 初始化列印機
        cmd.append("\u001B@");
        
        // 設定字體大小
        cmd.append("\u001B!\u0000"); // 標準字體
        
        // 料號
        cmd.append("料號: ").append(labelData.getPartNumber()).append("\n");
        
        // 料號條碼
        cmd.append("\u001Bh\u0040"); // 設定條碼高度
        cmd.append("\u001Bw\u0003"); // 設定條碼寬度
        cmd.append("\u001BH\u0000"); // 不顯示HRI
        cmd.append("\u001Bk\u0049"); // Code 128
        cmd.append((char)labelData.getPartNumber().length());
        cmd.append(labelData.getPartNumber());
        cmd.append("\n\n");
        
        // 品名
        cmd.append("品名: ").append(labelData.getProductName()).append("\n\n");
        
        // 數量和D/C (同一行)
        cmd.append("數量: ").append(labelData.getFormattedQuantity());
        cmd.append("    D/C: ").append(labelData.getDcCode()).append("\n");
        
        // 數量條碼
        cmd.append("\u001Bh\u0030"); // 較小的條碼高度
        cmd.append("\u001Bw\u0002"); // 較小的條碼寬度
        cmd.append("\u001BH\u0000"); // 不顯示HRI
        cmd.append("\u001Bk\u0004"); // Code 39
        cmd.append((char)labelData.getQuantity().length());
        cmd.append(labelData.getQuantity());
        
        // 空格分隔
        cmd.append("    ");
        
        // D/C條碼
        cmd.append("\u001Bk\u0004"); // Code 39
        cmd.append((char)labelData.getDcCode().length());
        cmd.append(labelData.getDcCode());
        
        // 切紙
        cmd.append("\n\n\n");
        cmd.append("\u001Bm"); // 切紙指令
        
        return cmd.toString();
    }
    
    /**
     * 生成CPCL格式的列印指令
     */
    public String generateCPCLCommand() {
        if (!isValid()) {
            return "";
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // CPCL標頭
        cmd.append("! 0 200 200 197 1\n"); // 90x50mm at 203 DPI
        
        // 料號文字
        cmd.append("TEXT 4 0 10 10 料號: ").append(labelData.getPartNumber()).append("\n");
        
        // 料號條碼
        cmd.append("BARCODE-128 10 35 M 2 30 ").append(labelData.getPartNumber()).append("\n");
        
        // 品名
        cmd.append("TEXT 4 0 10 75 品名: ").append(labelData.getProductName()).append("\n");
        
        // 數量
        cmd.append("TEXT 4 0 10 130 數量: ").append(labelData.getFormattedQuantity()).append("\n");
        
        // 數量條碼
        cmd.append("BARCODE 10 150 M 2 25 ").append(labelData.getQuantity()).append("\n");
        
        // D/C
        cmd.append("TEXT 4 0 200 130 D/C: ").append(labelData.getDcCode()).append("\n");
        
        // D/C條碼
        cmd.append("BARCODE 200 150 M 2 25 ").append(labelData.getDcCode()).append("\n");
        
        // 列印指令
        cmd.append("PRINT\n");
        
        return cmd.toString();
    }
    
    /**
     * 根據指定格式生成列印指令
     * TODO: 等待 TSC SDK 整合後實現
     */
    public String generatePrintCommand(PrintData.PrinterCommandFormat format) {
        switch (format) {
            case ZPL:
                return generateZPLCommand();
            case ESC_POS:
                return generateESCPOSCommand();
            case CPCL:
                return generateCPCLCommand();
            default:
                return generateZPLCommand();
        }
    }
    
    @Override
    public String toString() {
        return "SmallPackagePrintData{" +
                "partNumber='" + labelData.getPartNumber() + '\'' +
                ", productName='" + labelData.getProductName() + '\'' +
                ", quantity='" + labelData.getQuantity() + '\'' +
                ", dcCode='" + labelData.getDcCode() + '\'' +
                '}';
    }
}
