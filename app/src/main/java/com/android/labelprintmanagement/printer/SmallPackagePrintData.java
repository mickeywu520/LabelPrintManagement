package com.android.labelprintmanagement.printer;

import com.android.labelprintmanagement.model.SmallPackageLabelData;
import com.android.labelprintmanagement.utils.BarcodeGenerator;

/**
 * 小包裝標籤列印數據類
 * 專門處理75x50mm小包裝標籤的列印指令生成
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
     * 生成ZPL格式的75x50mm標籤列印指令
     * 標籤尺寸: 75mm x 50mm (約295 x 197 dots at 203 DPI)
     */
    public String generateZPLCommand() {
        if (!isValid()) {
            return "";
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // 開始標籤格式
        cmd.append("^XA\n");
        
        // 設定標籤尺寸 (75mm x 50mm)
        cmd.append("^LL197\n");  // 標籤長度 197 dots (50mm)
        cmd.append("^PW295\n");  // 標籤寬度 295 dots (75mm)
        
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
        cmd.append("! 0 200 200 197 1\n"); // 75x50mm at 203 DPI
        
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
     * 生成TSC格式的列印指令 (75mm x 50mm標籤)
     * 基於TSC SDK範例程式碼整合
     */
    public String generateTSCCommands() {
        if (!isValid()) {
            return "";
        }
        
        StringBuilder cmd = new StringBuilder();
        
        // TSC 基本設定指令
        cmd.append("SIZE 75 mm, 50 mm\r\n");  // 調整為75x50mm
        cmd.append("SPEED 4\r\n");
        cmd.append("DENSITY 12\r\n");
        cmd.append("CODEPAGE UTF-8\r\n");
        cmd.append("SET TEAR ON\r\n");
        cmd.append("SET COUNTER @1 1\r\n");
        cmd.append("@1 = \"0001\"\r\n");
        cmd.append("CLS\r\n");  // 清除緩衝區
        
        // 標籤內容 - 針對75x50mm佈局調整位置
        
        // 標題
        cmd.append("TEXT 50,30,\"ARIAL.TTF\",0,1,1,\"小包裝標籤\"\r\n");
        
        // 料號區域
        cmd.append("TEXT 50,70,\"ARIAL.TTF\",0,1,1,\"P/N: ").append(labelData.getPartNumber()).append("\"\r\n");
        
        // 料號條碼 (Code 128)
        cmd.append("BARCODE 50,100,\"128\",60,1,0,2,2,\"").append(labelData.getPartNumber()).append("\"\r\n");
        
        // 產品名稱 (處理長文字)
        String productName = labelData.getProductName();
        if (productName.length() > 30) {
            // 如果產品名稱太長，截取前30個字符
            productName = productName.substring(0, 30) + "...";
        }
        cmd.append("TEXT 50,190,\"ARIAL.TTF\",0,1,1,\"").append(productName).append("\"\r\n");
        
        // 數量區域 (左下)
        cmd.append("TEXT 50,230,\"ARIAL.TTF\",0,1,1,\"QTY: ").append(labelData.getFormattedQuantity()).append("\"\r\n");
        
        // 數量條碼
        cmd.append("BARCODE 50,260,\"128\",40,1,0,2,2,\"").append(labelData.getQuantity()).append("\"\r\n");
        
        // D/C Code區域 (右下)
        cmd.append("TEXT 400,230,\"ARIAL.TTF\",0,1,1,\"D/C: ").append(labelData.getDcCode()).append("\"\r\n");
        
        // D/C條碼
        cmd.append("BARCODE 400,260,\"128\",40,1,0,2,2,\"").append(labelData.getDcCode()).append("\"\r\n");
        
        // 計數器編號 (右上角)
        cmd.append("TEXT 500,70,\"ARIAL.TTF\",0,1,1,\"No: \"\r\n");
        cmd.append("TEXT 540,70,\"ARIAL.TTF\",0,1,1,").append("@1").append("\r\n");
        
        // 列印指令
        cmd.append("PRINT 1,1\r\n");
        
        return cmd.toString();
    }
    
    /**
     * 執行TSC列印 (需要傳入TSC SDK實例和藍牙地址)
     * 這個方法包含完整的TSC SDK列印流程
     */
    public boolean executeTSCPrint(Object tscDll, String bluetoothAddress) {
        if (!isValid()) {
            return false;
        }
        
        try {
            // 使用反射調用TSC SDK方法，避免直接依賴
            Class<?> tscClass = tscDll.getClass();
            
            // 開啟藍牙連線
            tscClass.getMethod("openport", String.class).invoke(tscDll, bluetoothAddress);
            
            // 發送設定指令
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "SIZE 75 mm, 50 mm\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "SPEED 4\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "DENSITY 12\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "CODEPAGE UTF-8\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "SET TEAR ON\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "SET COUNTER @1 1\r\n");
            tscClass.getMethod("sendcommand", String.class).invoke(tscDll, "@1 = \"0001\"\r\n");
            
            // 清除緩衝區
            tscClass.getMethod("clearbuffer").invoke(tscDll);
            
            // 發送標籤內容指令
            sendTSCLabelContent(tscDll, tscClass);
            
            // 執行列印
            tscClass.getMethod("printlabel", int.class, int.class).invoke(tscDll, 1, 1);
            
            // 關閉連線
            tscClass.getMethod("closeport", int.class).invoke(tscDll, 5000);
            
            return true;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 發送TSC標籤內容指令
     */
    private void sendTSCLabelContent(Object tscDll, Class<?> tscClass) throws Exception {
        // 標題
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 50,30,\"ARIAL.TTF\",0,1,1,\"小包裝標籤\"\r\n");
        
        // 料號
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 50,70,\"ARIAL.TTF\",0,1,1,\"P/N: " + labelData.getPartNumber() + "\"\r\n");
        
        // 料號條碼
        tscClass.getMethod("barcode", int.class, int.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class)
            .invoke(tscDll, 50, 100, "128", 60, 1, 0, 2, 2, labelData.getPartNumber());
        
        // 產品名稱
        String productName = labelData.getProductName();
        if (productName.length() > 30) {
            productName = productName.substring(0, 30) + "...";
        }
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 50,190,\"ARIAL.TTF\",0,1,1,\"" + productName + "\"\r\n");
        
        // 數量
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 50,230,\"ARIAL.TTF\",0,1,1,\"QTY: " + labelData.getFormattedQuantity() + "\"\r\n");
        
        // 數量條碼
        tscClass.getMethod("barcode", int.class, int.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class)
            .invoke(tscDll, 50, 260, "128", 40, 1, 0, 2, 2, labelData.getQuantity());
        
        // D/C Code
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 400,230,\"ARIAL.TTF\",0,1,1,\"D/C: " + labelData.getDcCode() + "\"\r\n");
        
        // D/C條碼
        tscClass.getMethod("barcode", int.class, int.class, String.class, int.class, int.class, int.class, int.class, int.class, String.class)
            .invoke(tscDll, 400, 260, "128", 40, 1, 0, 2, 2, labelData.getDcCode());
        
        // 計數器
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 500,70,\"ARIAL.TTF\",0,1,1,\"No: \"\r\n");
        tscClass.getMethod("sendcommand", String.class).invoke(tscDll, 
            "TEXT 540,70,\"ARIAL.TTF\",0,1,1,@1\r\n");
    }

    /**
     * 根據指定格式生成列印指令
     */
    public String generatePrintCommand(PrintData.PrinterCommandFormat format) {
        switch (format) {
            case ZPL:
                return generateZPLCommand();
            case ESC_POS:
                return generateESCPOSCommand();
            case CPCL:
                return generateCPCLCommand();
            case TSC:
                return generateTSCCommands();
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
