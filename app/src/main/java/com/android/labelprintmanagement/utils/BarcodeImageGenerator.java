package com.android.labelprintmanagement.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.util.HashMap;
import java.util.Map;

/**
 * 條碼圖像生成工具類
 * 用於生成 Code 128 條碼的 Bitmap 圖像，供預覽顯示使用
 */
public class BarcodeImageGenerator {
    
    private static final String TAG = "BarcodeImageGenerator";
    
    /**
     * 生成 Code 128 條碼圖像
     * 
     * @param content 條碼內容
     * @param width 圖像寬度
     * @param height 圖像高度
     * @return 條碼 Bitmap 圖像，失敗時返回 null
     */
    public static Bitmap generateCode128Barcode(String content, int width, int height) {
        if (content == null || content.trim().isEmpty()) {
            Log.e(TAG, "Barcode content is empty");
            return null;
        }
        
        try {
            // 設置編碼參數
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 0); // 設置邊距為 0

            // 創建 Code 128 寫入器
            Code128Writer writer = new Code128Writer();

            // 生成條碼矩陣
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.CODE_128, width, height, hints);

            // 轉換為 Bitmap
            return bitMatrixToBitmap(bitMatrix);

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate barcode for content: " + content, e);
            return null;
        }
    }
    
    /**
     * 將 BitMatrix 轉換為 Bitmap
     * 
     * @param matrix 條碼矩陣
     * @return Bitmap 圖像
     */
    private static Bitmap bitMatrixToBitmap(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        
        // 創建像素數組
        int[] pixels = new int[width * height];
        
        // 填充像素數據
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                // 黑色表示條碼線，白色表示空白
                pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        
        // 創建 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        
        return bitmap;
    }
    
    /**
     * 生成料號條碼（較大尺寸）
     * 
     * @param partNumber 料號
     * @param width 寬度
     * @param height 高度
     * @return 條碼圖像
     */
    public static Bitmap generatePartNumberBarcode(String partNumber, int width, int height) {
        Log.d(TAG, "Generating part number barcode: " + partNumber);
        return generateCode128Barcode(partNumber, width, height);
    }
    
    /**
     * 生成數量條碼（中等尺寸）
     * 
     * @param quantity 數量
     * @param width 寬度
     * @param height 高度
     * @return 條碼圖像
     */
    public static Bitmap generateQuantityBarcode(String quantity, int width, int height) {
        Log.d(TAG, "Generating quantity barcode: " + quantity);
        return generateCode128Barcode(quantity, width, height);
    }
    
    /**
     * 生成 D/C 條碼（中等尺寸）
     * 
     * @param dcCode D/C 碼
     * @param width 寬度
     * @param height 高度
     * @return 條碼圖像
     */
    public static Bitmap generateDcBarcode(String dcCode, int width, int height) {
        Log.d(TAG, "Generating DC barcode: " + dcCode);
        return generateCode128Barcode(dcCode, width, height);
    }
    
    /**
     * 驗證內容是否適合生成 Code 128 條碼
     * 
     * @param content 要驗證的內容
     * @return true 如果內容有效
     */
    public static boolean isValidCode128Content(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // Code 128 支援 ASCII 0-127 的字符
        for (char c : content.toCharArray()) {
            if (c > 127) {
                Log.w(TAG, "Invalid character for Code 128: " + c);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 獲取建議的條碼尺寸
     * 根據內容長度計算合適的寬度
     * 
     * @param content 條碼內容
     * @param baseWidth 基礎寬度
     * @param height 高度
     * @return 建議的寬度
     */
    public static int getSuggestedWidth(String content, int baseWidth, int height) {
        if (content == null || content.isEmpty()) {
            return baseWidth;
        }
        
        // 根據內容長度調整寬度
        int contentLength = content.length();
        int suggestedWidth = Math.max(baseWidth, contentLength * 12); // 每個字符約 12 像素
        
        // 確保寬度不會太大
        int maxWidth = baseWidth * 3;
        return Math.min(suggestedWidth, maxWidth);
    }
    
    /**
     * 生成預覽用的條碼圖像
     * 針對預覽顯示優化的尺寸和質量
     * 
     * @param content 條碼內容
     * @param type 條碼類型（用於日誌）
     * @return 預覽條碼圖像
     */
    public static Bitmap generatePreviewBarcode(String content, String type) {
        // 檢查內容是否為空
        if (content == null || content.trim().isEmpty()) {
            Log.w(TAG, "Empty content for " + type + " barcode");
            return null;
        }
        
        if (!isValidCode128Content(content)) {
            Log.w(TAG, "Invalid content for " + type + " barcode: " + content);
            return null;
        }
        
        // 根據類型設置不同的尺寸
        int width, height;
        switch (type.toLowerCase()) {
            case "partnumber":
                width = 300;
                height = 60;
                break;
            case "quantity":
            case "dc":
                width = 180;
                height = 45;
                break;
            default:
                width = 200;
                height = 50;
                break;
        }
        
        // 根據內容調整寬度
        width = getSuggestedWidth(content, width, height);
        
        return generateCode128Barcode(content, width, height);
    }
}
