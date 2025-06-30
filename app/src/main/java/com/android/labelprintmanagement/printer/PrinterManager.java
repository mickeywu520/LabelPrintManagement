package com.android.labelprintmanagement.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.util.Set;

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
    
    public PrinterManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
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
                
                // TSC SDK的openport是void方法，假設成功執行就是連線成功
                Log.d(TAG, "TSC Bluetooth connection successful");
                notifyConnectionStatus(ConnectionStatus.CONNECTED, "TSC藍芽列印機連線成功");
                
            } catch (Exception e) {
                Log.e(TAG, "Exception during TSC Bluetooth connection", e);
                notifyConnectionStatus(ConnectionStatus.ERROR, "連線異常: " + e.getMessage());
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
     * 生成TSC列印指令 (針對90x50mm小包裝標籤)
     */
    private String generateTSCCommands(SmallPackagePrintData printData) {
        if (printData == null || !printData.isValid()) {
            throw new IllegalArgumentException("Invalid print data");
        }
        
        StringBuilder commands = new StringBuilder();
        
        // 基本設定 - 90x50mm標籤
        commands.append("SIZE 90 mm, 50 mm\r\n");
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
        commands.append("TEXT 20,30,\"3\",0,1,1,\"料號: ").append(partNumber).append("\"\r\n");
        
        // 料號條碼 (Code 128)
        commands.append("BARCODE 20,60,\"128\",60,1,0,2,2,\"").append(partNumber).append("\"\r\n");
        
        // 品名文字 (中間)
        commands.append("TEXT 20,140,\"3\",0,1,1,\"品名: ").append(productName).append("\"\r\n");
        
        // 數量文字 (底部左側)
        commands.append("TEXT 20,180,\"3\",0,1,1,\"數量: ").append(quantity).append(" PCS\"\r\n");
        
        // 數量條碼 (Code 39)
        commands.append("BARCODE 20,210,\"39\",40,1,0,2,2,\"").append(quantity).append("\"\r\n");
        
        // D/C文字 (底部右側)
        commands.append("TEXT 300,180,\"3\",0,1,1,\"D/C: ").append(dcCode).append("\"\r\n");
        
        // D/C條碼 (Code 39)
        commands.append("BARCODE 300,210,\"39\",40,1,0,2,2,\"").append(dcCode).append("\"\r\n");
        
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
        
        if (tscInstance instanceof TSCActivity) {
            TSCActivity tsc = (TSCActivity) tscInstance;
            for (String command : commandLines) {
                if (!command.trim().isEmpty()) {
                    Log.d(TAG, "Sending TSC BT command: " + command);
                    tsc.sendcommand(command + "\r\n");
                }
            }
        } else if (tscInstance instanceof TscWifiActivity) {
            TscWifiActivity tsc = (TscWifiActivity) tscInstance;
            for (String command : commandLines) {
                if (!command.trim().isEmpty()) {
                    Log.d(TAG, "Sending TSC WiFi command: " + command);
                    tsc.sendcommand(command + "\r\n");
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown TSC instance type");
        }
        
        Log.d(TAG, "All TSC commands sent successfully");
    }
}
