package com.android.labelprintmanagement.printer;

import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.labelprintmanagement.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 列印機選擇對話框
 * 支援藍芽和WiFi列印機的選擇和連線
 */
public class PrinterSelectionDialog {
    
    public interface PrinterSelectionCallback {
        void onBluetoothPrinterSelected(BluetoothDevice device);
        void onWifiPrinterSelected(String ip, int port);
        void onCancel();
    }
    
    private Context context;
    private PrinterManager printerManager;
    private PrinterSelectionCallback callback;
    
    public PrinterSelectionDialog(Context context, PrinterManager printerManager) {
        this.context = context;
        this.printerManager = printerManager;
    }
    
    public void setCallback(PrinterSelectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 顯示列印機類型選擇對話框
     */
    public void showPrinterTypeSelection() {
        String[] options = {"藍芽列印機", "WiFi列印機"};
        
        new MaterialAlertDialogBuilder(context)
                .setTitle("選擇列印機類型")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showBluetoothPrinterSelection();
                            break;
                        case 1:
                            showWifiPrinterSelection();
                            break;
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .show();
    }
    
    /**
     * 顯示藍芽列印機選擇對話框
     */
    private void showBluetoothPrinterSelection() {
        // 檢查藍芽可用性
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(context, "此設備不支援藍芽功能", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!printerManager.isBluetoothEnabled()) {
            Toast.makeText(context, "請先啟用藍芽功能", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!printerManager.hasBluetoothPermissions()) {
            Toast.makeText(context, "缺少藍芽權限，請在設定中授予權限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 獲取已配對的設備
        Set<BluetoothDevice> pairedDevices = printerManager.getPairedDevices();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(context, "沒有找到已配對的藍芽設備", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 創建設備列表
        List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        BluetoothDeviceAdapter adapter = new BluetoothDeviceAdapter(context, deviceList);
        
        // 創建ListView
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        
        // 創建對話框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle("選擇藍芽列印機")
                .setView(listView)
                .setNegativeButton("取消", (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                });
        
        Dialog dialog = builder.create();
        
        // 設定列表項目點擊事件
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice selectedDevice = deviceList.get(position);
            if (callback != null) {
                callback.onBluetoothPrinterSelected(selectedDevice);
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    /**
     * 顯示WiFi列印機設定對話框
     */
    private void showWifiPrinterSelection() {
        // 檢查WiFi可用性
        if (!printerManager.isWifiAvailable()) {
            Toast.makeText(context, "WiFi未連線，請先連線WiFi", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 創建輸入對話框
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_wifi_printer, null);
        
        TextInputEditText etIpAddress = dialogView.findViewById(R.id.etIpAddress);
        TextInputEditText etPort = dialogView.findViewById(R.id.etPort);
        
        // 設定預設值
        etIpAddress.setText("192.168.1.100"); // 預設IP
        etPort.setText("9100"); // 預設端口
        
        new MaterialAlertDialogBuilder(context)
                .setTitle("WiFi列印機設定")
                .setView(dialogView)
                .setPositiveButton("連線", (dialog, which) -> {
                    String ip = etIpAddress.getText().toString().trim();
                    String portStr = etPort.getText().toString().trim();
                    
                    if (ip.isEmpty()) {
                        Toast.makeText(context, "請輸入IP地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    int port;
                    try {
                        port = Integer.parseInt(portStr);
                        if (port <= 0 || port > 65535) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "請輸入有效的端口號 (1-65535)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (callback != null) {
                        callback.onWifiPrinterSelected(ip, port);
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .show();
    }
    
    /**
     * 藍芽設備列表適配器
     */
    private static class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
        
        public BluetoothDeviceAdapter(@NonNull Context context, @NonNull List<BluetoothDevice> devices) {
            super(context, 0, devices);
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        android.R.layout.simple_list_item_2, parent, false);
            }
            
            BluetoothDevice device = getItem(position);
            if (device != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);
                
                try {
                    String deviceName = device.getName();
                    if (deviceName == null || deviceName.isEmpty()) {
                        deviceName = "未知設備";
                    }
                    text1.setText(deviceName);
                    text2.setText(device.getAddress());
                } catch (SecurityException e) {
                    text1.setText("未知設備");
                    text2.setText("權限不足");
                }
            }
            
            return convertView;
        }
    }
}
