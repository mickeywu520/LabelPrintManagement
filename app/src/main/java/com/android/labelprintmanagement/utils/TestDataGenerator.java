package com.android.labelprintmanagement.utils;

/**
 * 測試數據生成器
 * 用於生成測試用的QR碼內容和小包裝標籤數據
 */
public class TestDataGenerator {
    
    /**
     * 生成測試用的QR碼內容 (新格式)
     */
    public static String generateTestQRContent() {
        return "PN:1710002190000P\n" +
               "DES:FPC-7602 BOTTOM COVER BRACKET\n" +
               "QTY:1000\n" +
               "DC:250601";
    }
    
    /**
     * 生成測試用的QR碼內容 (舊格式)
     */
    public static String generateTestQRContentLegacy() {
        return "料號:1710002190000P\n" +
               "品名:FPC-7602 BOTTOM COVER BRACKET\n" +
               "數量:1000\n" +
               "D/C:250601";
    }
    
    /**
     * 生成另一組測試數據
     */
    public static String generateTestQRContent2() {
        return "PN:3711760236010P\n" +
               "DES:CONNECTOR HOUSING 2.54MM\n" +
               "QTY:500\n" +
               "DC:240815";
    }
    
    /**
     * 生成錯誤格式的QR碼內容 (用於測試錯誤處理)
     */
    public static String generateInvalidQRContent() {
        return "料號:1710002190000P\n" +
               "品名:FPC-7602 BOTTOM COVER BRACKET\n" +
               "數量:\n" +  // 缺少數量值
               "D/C:250601";
    }
    
    /**
     * 生成不完整的QR碼內容 (用於測試錯誤處理)
     */
    public static String generateIncompleteQRContent() {
        return "PN:1710002190000P\n" +
               "DES:FPC-7602 BOTTOM COVER BRACKET\n";
               // 缺少數量和D/C
    }
    
    /**
     * 生成包含特殊字符的QR碼內容
     */
    public static String generateSpecialCharQRContent() {
        return "PN:ABC-123/DEF_456\n" +
               "DES:SPECIAL CHAR TEST (BRACKET) & SYMBOL\n" +
               "QTY:100\n" +
               "DC:240101";
    }
    
    /**
     * 生成長品名的QR碼內容 (測試UI顯示)
     */
    public static String generateLongNameQRContent() {
        return "PN:VERY_LONG_PART_NUMBER_123456789\n" +
               "DES:THIS IS A VERY LONG PRODUCT NAME THAT MIGHT CAUSE DISPLAY ISSUES IN THE UI LAYOUT\n" +
               "QTY:2500\n" +
               "DC:241225";
    }
    
    /**
     * 獲取所有測試數據的數組
     */
    public static String[] getAllTestQRContents() {
        return new String[]{
            generateTestQRContent(),
            generateTestQRContentLegacy(),
            generateTestQRContent2(),
            generateSpecialCharQRContent(),
            generateLongNameQRContent()
        };
    }
    
    /**
     * 獲取所有錯誤測試數據的數組
     */
    public static String[] getInvalidTestQRContents() {
        return new String[]{
            generateInvalidQRContent(),
            generateIncompleteQRContent(),
            "",
            "INVALID_FORMAT_NO_COLONS",
            "PN:\nDES:\nQTY:\nDC:"  // 空值
        };
    }
    
    /**
     * 模擬PDA廣播的Intent數據
     */
    public static class MockBroadcastData {
        public String barcodedata;
        public int symbology;
        
        public MockBroadcastData(String barcodedata, int symbology) {
            this.barcodedata = barcodedata;
            this.symbology = symbology;
        }
        
        /**
         * 創建QR碼類型的模擬廣播數據
         */
        public static MockBroadcastData createQRCodeData(String content) {
            return new MockBroadcastData(content, 11); // 11 通常代表QR碼
        }
        
        /**
         * 創建Code 128類型的模擬廣播數據
         */
        public static MockBroadcastData createCode128Data(String content) {
            return new MockBroadcastData(content, 6); // 6 通常代表Code 128
        }
        
        /**
         * 創建Code 39類型的模擬廣播數據
         */
        public static MockBroadcastData createCode39Data(String content) {
            return new MockBroadcastData(content, 1); // 1 通常代表Code 39
        }
    }
    
    /**
     * 生成測試用的ZPL指令預期結果
     */
    public static String generateExpectedZPLCommand() {
        return "^XA\n" +
               "^LL197\n" +
               "^PW354\n" +
               "^PR4\n" +
               "^FO10,10^A0N,20,20^FD料號:^FS\n" +
               "^FO60,10^A0N,20,20^FD1710002190000P^FS\n" +
               "^FO10,35^BCo,30,N,N,N^FD1710002190000P^FS\n" +
               "^FO10,75^A0N,16,16^FD品名:^FS\n" +
               "^FO50,75^A0N,16,16^FDFPC-7602 BOTTOM COVER BRACKET^FS\n" +
               "^FO10,130^A0N,16,16^FD數量:1000 PCS^FS\n" +
               "^FO10,150^B3o,N,25,N,N^FD1000^FS\n" +
               "^FO200,130^A0N,16,16^FDD/C: 250601^FS\n" +
               "^FO200,150^B3o,N,25,N,N^FD250601^FS\n" +
               "^XZ\n";
    }
}
