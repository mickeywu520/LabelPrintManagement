package com.android.labelprintmanagement.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;

import com.android.labelprintmanagement.utils.BluetoothPreferences;

// TSC SDK imports
import com.example.tscdll.TSCActivity;
import com.example.tscdll.TscWifiActivity;

/**
 * 列印機管理器 - 統一管理藍芽和WiFi列印機連線
 * 提供抽象介面，支援不同類型的列印機
 */
public class PrinterManager {
    
    private static final String TAG = "PrinterManager";
    
    // 列印機類型枚舉
    public enum PrinterType {
        BLUETOOTH,
        WIFI,
        USB
    }
    
    // 連線狀態枚舉
    public enum ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    // 列印機連線回調介面
    public interface PrinterConnectionCallback {
        void onConnectionStatusChanged(ConnectionStatus status, String message);
        void onPrintResult(boolean success, String message);
        void onDeviceFound(BluetoothDevice device);
    }
    
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private PrinterConnectionCallback callback;
    private ConnectionStatus currentStatus = ConnectionStatus.DISCONNECTED;
    private PrinterType currentPrinterType = PrinterType.BLUETOOTH;
    private BluetoothDevice connectedDevice;
    
    // TSC SDK instances
    private TSCActivity tscBluetooth;
    private TscWifiActivity tscWifi;
    private String connectedDeviceAddress;
    
    // 藍芽偏好設置管理器
    private BluetoothPreferences bluetoothPreferences;
    
    public PrinterManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.bluetoothPreferences = new BluetoothPreferences(context);
        
        // 初始化TSC SDK實例
        this.tscBluetooth = new TSCActivity();
        this.tscWifi = new TscWifiActivity();
    }
    
    public void setCallback(PrinterConnectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 檢查藍芽是否可用
     */
    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }
    
    /**
     * 檢查藍芽是否已啟用
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    /**
     * 檢查WiFi是否可用
     */
    public boolean isWifiAvailable() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }
    
    /**
     * 檢查是否有藍芽權限
     */
    public boolean hasBluetoothPermissions() {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
               ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 獲取已配對的藍芽設備
     */
    public Set<BluetoothDevice> getPairedDevices() {
        if (!isBluetoothEnabled() || !hasBluetoothPermissions()) {
            return null;
        }
        
        try {
            return bluetoothAdapter.getBondedDevices();
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception when getting paired devices: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 通過MAC地址連線到藍芽列印機
     */
    public void connectToBluetoothPrinter(String macAddress) {
        if (!isBluetoothEnabled()) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "藍芽未啟用");
            return;
        }
        
        if (!hasBluetoothPermissions()) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "缺少藍芽權限");
            return;
        }
        
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            connectToBluetoothPrinter(device);
        } catch (IllegalArgumentException e) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "無效的MAC地址格式: " + macAddress);
        }
    }
    
    /**
     * 連線到指定的藍芽列印機 (使用TSC SDK)
     */
    public void connectToBluetoothPrinter(BluetoothDevice device) {
        if (!isBluetoothEnabled()) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "藍芽未啟用");
            return;
        }
        
        if (!hasBluetoothPermissions()) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "缺少藍芽權限");
            return;
        }
        
        currentPrinterType = PrinterType.BLUETOOTH;
        connectedDevice = device;
        connectedDeviceAddress = device.getAddress();
        
        notifyConnectionStatus(ConnectionStatus.CONNECTING, "正在連線到TSC藍芽列印機...");
        
        // 使用TSC SDK進行實際連線
        new Thread(() -> {
            try {
                Log.d(TAG, "Connecting to TSC Bluetooth printer: " + connectedDeviceAddress);
                
                // 使用TSC SDK開啟藍芽連接 (void方法，無返回值)
                tscBluetooth.openport(connectedDeviceAddress);
                
                // 等待一段時間讓連線建立
                Thread.sleep(2000);
                
                // 嘗試發送測試指令來驗證連線
                boolean connectionVerified = verifyTSCConnection();
                
                if (connectionVerified) {
                    Log.d(TAG, "TSC Bluetooth connection verified successfully");
                    
                    // 保存成功連線的設備MAC地址
                    bluetoothPreferences.saveConnectedDevice(connectedDeviceAddress);
                    
                    notifyConnectionStatus(ConnectionStatus.CONNECTED, "TSC藍芽列印機連線成功");
                } else {
                    Log.w(TAG, "TSC Bluetooth connection failed verification");
                    
                    // 嘗試關閉連線
                    try {
                        tscBluetooth.closeport(1000);
                    } catch (Exception closeEx) {
                        Log.w(TAG, "Error closing failed connection", closeEx);
                    }
                    
                    connectedDevice = null;
                    connectedDeviceAddress = null;
                    notifyConnectionStatus(ConnectionStatus.ERROR, "無法連線到指定的列印機，請檢查設備是否開啟且在範圍內");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Exception during TSC Bluetooth connection", e);
                
                // 清理連線狀態
                connectedDevice = null;
                connectedDeviceAddress = null;
                
                notifyConnectionStatus(ConnectionStatus.ERROR, "連線失敗: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 連線到WiFi列印機 (使用TSC SDK)
     */
    public void connectToWifiPrinter(String printerIp, int port) {
        if (!isWifiAvailable()) {
            notifyConnectionStatus(ConnectionStatus.ERROR, "WiFi未連線");
            return;
        }
        
        currentPrinterType = PrinterType.WIFI;
        connectedDeviceAddress = printerIp + ":" + port;
        
        notifyConnectionStatus(ConnectionStatus.CONNECTING, "正在連線到TSC WiFi列印機...");
        
        // 使用TSC SDK進行實際WiFi連線
        new Thread(() -> {
            try {
                Log.d(TAG, "Connecting to TSC WiFi printer: " + printerIp + ":" + port);
                
                // 使用TSC SDK開啟WiFi連接 (void方法，無返回值)
                tscWifi.openport(printerIp, port);
                
                // TSC SDK的openport是void方法，假設成功執行就是連線成功
                Log.d(TAG, "TSC WiFi connection successful");
                notifyConnectionStatus(ConnectionStatus.CONNECTED, "TSC WiFi列印機連線成功");
                
            } catch (Exception e) {
                Log.e(TAG, "Exception during TSC WiFi connection", e);
                notifyConnectionStatus(ConnectionStatus.ERROR, "WiFi連線異常: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 斷開列印機連線 (使用TSC SDK)
     */
    public void disconnect() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Disconnecting from TSC printer...");
                
                switch (currentPrinterType) {
                    case BLUETOOTH:
                        if (tscBluetooth != null) {
                            tscBluetooth.closeport(5000); // 5秒超時
                            Log.d(TAG, "TSC Bluetooth disconnected");
                        }
                        break;
                    case WIFI:
                        if (tscWifi != null) {
                            tscWifi.closeport(5000); // 5秒超時
                            Log.d(TAG, "TSC WiFi disconnected");
                        }
                        break;
                }
                
                connectedDevice = null;
                connectedDeviceAddress = null;
                notifyConnectionStatus(ConnectionStatus.DISCONNECTED, "已斷開TSC列印機連線");
                
            } catch (Exception e) {
                Log.e(TAG, "Exception during disconnect", e);
                notifyConnectionStatus(ConnectionStatus.DISCONNECTED, "斷線時發生異常，但已強制斷開");
            }
        }).start();
    }
    
    /**
     * 發送列印指令
     */
    public void printLabel(PrintData printData) {
        if (currentStatus != ConnectionStatus.CONNECTED) {
            notifyPrintResult(false, "列印機未連線");
            return;
        }

        // TODO: 根據列印機類型發送相應的列印指令
        switch (currentPrinterType) {
            case BLUETOOTH:
                printViaBluetooth(printData);
                break;
            case WIFI:
                printViaWifi(printData);
                break;
            case USB:
                printViaUsb(printData);
                break;
        }
    }

    /**
     * 發送小包裝標籤列印指令
     */
    public void printSmallPackageLabel(SmallPackagePrintData printData) {
        if (currentStatus != ConnectionStatus.CONNECTED) {
            notifyPrintResult(false, "列印機未連線");
            return;
        }

        // TODO: 根據列印機類型發送相應的列印指令
        switch (currentPrinterType) {
            case BLUETOOTH:
                printSmallPackageViaBluetooth(printData);
                break;
            case WIFI:
                printSmallPackageViaWifi(printData);
                break;
            case USB:
                printSmallPackageViaUsb(printData);
                break;
        }
    }
    
    private void printViaBluetooth(PrintData printData) {
        // TODO: 實現藍芽列印邏輯
        Log.d(TAG, "Printing via Bluetooth: " + printData.toString());
        notifyPrintResult(true, "藍芽列印完成");
    }
    
    private void printViaWifi(PrintData printData) {
        // TODO: 實現WiFi列印邏輯
        Log.d(TAG, "Printing via WiFi: " + printData.toString());
        notifyPrintResult(true, "WiFi列印完成");
    }
    
    private void printViaUsb(PrintData printData) {
        // TODO: 實現USB列印邏輯
        Log.d(TAG, "Printing via USB: " + printData.toString());
        notifyPrintResult(true, "USB列印完成");
    }

    private void printSmallPackageViaBluetooth(SmallPackagePrintData printData) {
        Log.d(TAG, "Printing small package label via TSC Bluetooth: " + printData.toString());

        new Thread(() -> {
            try {
                // 檢查連線狀態
                if (currentStatus != ConnectionStatus.CONNECTED) {
                    notifyPrintResult(false, "列印機未連線，請先連線列印機");
                    return;
                }
                
                // 再次驗證連線是否有效
                if (!verifyTSCConnection()) {
                    notifyPrintResult(false, "列印機連線已斷開，請重新連線");
                    // 更新連線狀態
                    notifyConnectionStatus(ConnectionStatus.DISCONNECTED, "連線已斷開");
                    return;
                }
                
                // 生成TSC列印指令
                String tscCommands = generateTSCCommands(printData);
                Log.d(TAG, "Generated TSC commands: " + tscCommands);
                
                // 使用TSC SDK發送列印指令
                sendTSCCommands(tscBluetooth, tscCommands);
                
                notifyPrintResult(true, "小包裝標籤TSC藍芽列印完成");
                
            } catch (Exception e) {
                Log.e(TAG, "Error printing via TSC Bluetooth", e);
                notifyPrintResult(false, "TSC藍芽列印失敗: " + e.getMessage());
            }
        }).start();
    }

    private void printSmallPackageViaWifi(SmallPackagePrintData printData) {
        Log.d(TAG, "Printing small package label via TSC WiFi: " + printData.toString());

        new Thread(() -> {
            try {
                // 檢查連線狀態
                if (currentStatus != ConnectionStatus.CONNECTED) {
                    notifyPrintResult(false, "列印機未連線，請先連線列印機");
                    return;
                }
                
                // 再次驗證連線是否有效
                if (!verifyTSCConnection()) {
                    notifyPrintResult(false, "列印機連線已斷開，請重新連線");
                    // 更新連線狀態
                    notifyConnectionStatus(ConnectionStatus.DISCONNECTED, "連線已斷開");
                    return;
                }
                
                // 生成TSC列印指令
                String tscCommands = generateTSCCommands(printData);
                Log.d(TAG, "Generated TSC commands: " + tscCommands);
                
                // 使用TSC SDK發送列印指令
                sendTSCCommands(tscWifi, tscCommands);
                
                notifyPrintResult(true, "小包裝標籤TSC WiFi列印完成");
                
            } catch (Exception e) {
                Log.e(TAG, "Error printing via TSC WiFi", e);
                notifyPrintResult(false, "TSC WiFi列印失敗: " + e.getMessage());
            }
        }).start();
    }

    private void printSmallPackageViaUsb(SmallPackagePrintData printData) {
        // TODO: 等待 TSC SDK 整合後實現小包裝標籤USB列印邏輯
        Log.d(TAG, "Printing small package label via USB: " + printData.toString());

        // 暫時模擬列印成功
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 模擬列印時間
                notifyPrintResult(true, "小包裝標籤USB列印完成 (模擬)");
            } catch (InterruptedException e) {
                notifyPrintResult(false, "列印被中斷");
            }
        }).start();
    }
    
    public ConnectionStatus getCurrentStatus() {
        return currentStatus;
    }
    
    public PrinterType getCurrentPrinterType() {
        return currentPrinterType;
    }
    
    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }
    
    /**
     * 獲取已保存的藍芽設備MAC地址列表
     */
    public Set<String> getSavedBluetoothDevices() {
        return bluetoothPreferences.getConnectedDevices();
    }
    
    /**
     * 獲取最後一次連線的設備MAC地址
     */
    public String getLastConnectedDeviceMac() {
        return bluetoothPreferences.getLastConnectedDevice();
    }
    
    /**
     * 移除已保存的藍芽設備
     */
    public void removeSavedBluetoothDevice(String macAddress) {
        bluetoothPreferences.removeConnectedDevice(macAddress);
    }
    
    private void notifyConnectionStatus(ConnectionStatus status, String message) {
        currentStatus = status;
        if (callback != null) {
            callback.onConnectionStatusChanged(status, message);
        }
    }
    
    private void notifyPrintResult(boolean success, String message) {
        if (callback != null) {
            callback.onPrintResult(success, message);
        }
    }
    
    /**
     * 驗證TSC連線是否真正建立
     * 通過發送簡單的狀態查詢指令來測試連線
     */
    private boolean verifyTSCConnection() {
        try {
            switch (currentPrinterType) {
                case BLUETOOTH:
                    if (tscBluetooth != null) {
                        // 發送簡單的狀態查詢指令
                        tscBluetooth.sendcommand("~!T\r\n"); // TSC狀態查詢指令
                        
                        // 等待一小段時間讓指令處理
                        Thread.sleep(500);
                        
                        // 如果沒有拋出異常，認為連線成功
                        return true;
                    }
                    break;
                case WIFI:
                    if (tscWifi != null) {
                        // 發送簡單的狀態查詢指令
                        tscWifi.sendcommand("~!T\r\n"); // TSC狀態查詢指令
                        
                        // 等待一小段時間讓指令處理
                        Thread.sleep(500);
                        
                        // 如果沒有拋出異常，認為連線成功
                        return true;
                    }
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection verification failed", e);
            return false;
        }
        
        return false;
    }

    /**
     * 從 assets 資料夾中載入圖片並轉換為 Bitmap 物件。
     *
     * @param context 應用程式的 Context。
     * @param assetFileName assets 資料夾中圖片的檔案名稱 (例如: "my_image.png")。
     * @return 轉換成功的 Bitmap 物件，如果發生錯誤則回傳 null。
     */
    public Bitmap getBitmapFromAssets(Context context, String assetFileName) {
        // 儲存結果的 Bitmap 物件
        Bitmap bitmap = null;
        InputStream inputStream = null;

        try {
            // 透過 Context 取得 AssetManager
            AssetManager assetManager = context.getAssets();
            // 開啟 assets 中的指定檔案，並取得 InputStream
            inputStream = assetManager.open(assetFileName);

            Log.w(TAG, "Have bitmap!!!!");
            // 使用 BitmapFactory 直接從 InputStream 解碼成 Bitmap
            bitmap = BitmapFactory.decodeStream(inputStream);

        } catch (IOException e) {
            // 如果找不到檔案或讀取失敗，記錄錯誤
            Log.e(TAG, "Failed to get bitmap from assets: " + assetFileName, e);
            e.printStackTrace();
        } finally {
            // 確保 InputStream 在使用完畢後被關閉，避免資源洩漏
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // 回傳轉換後的 Bitmap
        return bitmap;
    }


    /**
     * 複製 assets 資料夾中的檔案到快取目錄，並回傳其絕對路徑。
     *
     * @param context 應用程式的 Context。
     * @param assetFileName 要複製的 assets 檔案名稱，例如 "logo.bmp"。
     * @return 檔案在快取目錄中的絕對路徑，如果失敗則回傳 null。
     */
    public String getAssetFilePath(Context context, String assetFileName) {
        File targetFile = null;

        try {
            AssetManager assetManager = context.getAssets();
            // 讀取 assets 資料夾中的檔案
            InputStream inputStream = assetManager.open(assetFileName);

            // 在應用程式的快取目錄中創建一個臨時檔案
            File cacheDir = context.getCacheDir();
            targetFile = new File(cacheDir, assetFileName);
            FileOutputStream outputStream = new FileOutputStream(targetFile);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            inputStream.close();
            outputStream.flush();
            outputStream.close();

            // 成功複製後，回傳檔案的絕對路徑
            return targetFile.getAbsolutePath();

        } catch (IOException e) {
            e.printStackTrace();
            // 如果複製檔案失敗，回傳 null
            return null;
        }
    }

    /**
     * 輔助方法：將長字串根據字元數分割成多行
     *
     * @param text      要分割的原始字串
     * @param charLimit 每行的字元數限制
     * @return 包含換行符的處理後字串
     */
    public static String wrapText(String text, int charLimit) {
        StringBuilder sb = new StringBuilder();
        int currentLength = 0;
        for (char c : text.toCharArray()) {
            sb.append(c);
            currentLength++;
            // 檢查是否達到字元限制
            if (currentLength >= charLimit) {
                // 插入換行符
                sb.append("\n");
                currentLength = 0;
            }
        }
        return sb.toString();
    }

    /**
     * 生成TSC列印指令 (針對75x50mm小包裝標籤)
     */
    private String generateTSCCommands(SmallPackagePrintData printData) {
        if (printData == null || !printData.isValid()) {
            throw new IllegalArgumentException("Invalid print data");
        }
        
        StringBuilder commands = new StringBuilder();
        
        // 基本設定 - 75x50mm標籤
        commands.append("SIZE 75 mm, 50 mm\r\n");
        commands.append("SPEED 4\r\n");
        commands.append("DENSITY 12\r\n");
        commands.append("CODEPAGE UTF-8\r\n");
        commands.append("SET TEAR ON\r\n");
        commands.append("CLS\r\n"); // 清除緩衝區
        
        // 從SmallPackagePrintData獲取標籤數據
        // 注意：需要訪問printData內部的labelData
        // 這裡暫時使用toString()解析，後續可能需要添加getter方法
        String dataStr = printData.toString();
        
        // 解析數據 (臨時方案，建議後續在SmallPackagePrintData中添加getter方法)
        String partNumber = extractValue(dataStr, "partNumber");
        String productName = extractValue(dataStr, "productName");
        String quantity = extractValue(dataStr, "quantity");
        String dcCode = extractValue(dataStr, "dcCode");

        // 料號文字 (頂部左側)
        commands.append("TEXT 20,30,\"MEIRYO.TTC\",0,12,12,0,\"料號:").append(partNumber).append("\"\r\n");
        // 料號條碼 (Code 128)
        commands.append("BARCODE 90,60,\"128\",60,0,0,2,2,\"").append(partNumber).append("\"\r\n");

        // 品名文字 (中間)
        String fullProductNameText = "品名: " + productName;
        String wrappedProductNameText = wrapText(fullProductNameText, 25);
        // 調整 BLOCK 字體，並增加區塊高度
        commands.append("BLOCK 20,180,800,250,\"MEIRYO.TTC\",0,12,12,0,0,\"" + wrappedProductNameText + "\"\r\n");

        // 數量文字 (底部左側)
        commands.append("TEXT 20,300,\"MEIRYO.TTC\",0,12,12,0,\"數量:").append(quantity).append(" PCS\"\r\n");
        // 數量條碼 (Code 128)
        commands.append("BARCODE 90,330,\"128\",40,0,0,2,2,\"").append(quantity).append("\"\r\n");

        // D/C文字 (底部右側)
        commands.append("TEXT 300,300,\"MEIRYO.TTC\",0,12,12,0,\"D/C:").append(dcCode).append("\"\r\n");
        // D/C條碼 (Code 128)
        commands.append("BARCODE 360,330,\"128\",40,0,0,2,2,\"").append(dcCode).append("\"\r\n");

        // 列印指令
        commands.append("PRINT 1,1\r\n");
        return commands.toString();
    }

    /**
     * 從toString()字符串中提取值的輔助方法
     * 臨時方案，建議後續優化
     */
    private String extractValue(String dataStr, String key) {
        try {
            String searchKey = key + "='";
            int startIndex = dataStr.indexOf(searchKey);
            if (startIndex == -1) return "";
            
            startIndex += searchKey.length();
            int endIndex = dataStr.indexOf("'", startIndex);
            if (endIndex == -1) return "";
            
            return dataStr.substring(startIndex, endIndex);
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract value for key: " + key, e);
            return "";
        }
    }
    
    /**
     * 發送TSC指令到列印機
     */
    private void sendTSCCommands(Object tscInstance, String commands) throws Exception {
        if (tscInstance == null) {
            throw new IllegalStateException("TSC instance is null");
        }
        
        String[] commandLines = commands.split("\r\n");
        
        try {
            if (tscInstance instanceof TSCActivity) {
                TSCActivity tsc = (TSCActivity) tscInstance;
                for (String command : commandLines) {
                    if (!command.trim().isEmpty()) {
                        Log.d(TAG, "Sending TSC BT command: " + command);
                        tsc.sendcommand(command + "\r\n");
                        
                        // 在每個指令之間添加小延遲，避免指令發送過快
                        Thread.sleep(50);
                    }
                }
            } else if (tscInstance instanceof TscWifiActivity) {
                TscWifiActivity tsc = (TscWifiActivity) tscInstance;
                for (String command : commandLines) {
                    if (!command.trim().isEmpty()) {
                        Log.d(TAG, "Sending TSC WiFi command: " + command);
                        tsc.sendcommand(command + "\r\n");
                        
                        // 在每個指令之間添加小延遲，避免指令發送過快
                        Thread.sleep(50);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown TSC instance type");
            }
            
            Log.d(TAG, "All TSC commands sent successfully");
            
        } catch (NullPointerException e) {
            Log.e(TAG, "NullPointerException in sendTSCCommands - connection may be lost", e);
            throw new Exception("列印機連線已斷開，請重新連線", e);
        } catch (Exception e) {
            Log.e(TAG, "Error sending TSC commands", e);
            throw new Exception("發送列印指令失敗: " + e.getMessage(), e);
        }
    }
}
