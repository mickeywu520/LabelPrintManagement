package com.android.labelprintmanagement.printer;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
 * 藍芽連線對話框
 * 支援輸入MAC地址和選擇已保存的設備
 */
public class BluetoothConnectionDialog {
    
    public interface BluetoothConnectionCallback {
        void onMacAddressSelected(String macAddress);
        void onCancel();
    }
    
    // 用於接收掃描數據的接口
    public interface ScanDataReceiver {
        void onScanDataReceived(String data);
    }
    
    private static ScanDataReceiver currentScanDataReceiver = null;
    
    private Context context;
    private PrinterManager printerManager;
    private BluetoothConnectionCallback callback;
    
    public BluetoothConnectionDialog(Context context, PrinterManager printerManager) {
        this.context = context;
        this.printerManager = printerManager;
    }
    
    public void setCallback(BluetoothConnectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 顯示藍芽連線選擇對話框
     */
    public void showBluetoothConnectionDialog() {
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
        
        // 獲取已保存的設備
        Set<String> savedDevices = printerManager.getSavedBluetoothDevices();
        
        if (savedDevices != null && !savedDevices.isEmpty()) {
            // 有已保存的設備，顯示選擇對話框
            showSavedDevicesDialog(savedDevices);
        } else {
            // 沒有已保存的設備，直接顯示輸入對話框
            showMacAddressInputDialog();
        }
    }
    
    /**
     * 顯示已保存設備選擇對話框
     */
    private void showSavedDevicesDialog(Set<String> savedDevices) {
        List<String> deviceList = new ArrayList<>(savedDevices);
        
        // 將最後連線的設備排在第一位
        String lastConnected = printerManager.getLastConnectedDeviceMac();
        if (lastConnected != null && deviceList.contains(lastConnected)) {
            deviceList.remove(lastConnected);
            deviceList.add(0, lastConnected);
        }
        
        SavedDeviceAdapter adapter = new SavedDeviceAdapter(context, deviceList, lastConnected);
        
        ListView listView = new ListView(context);
        listView.setAdapter(adapter);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle("選擇藍芽列印機")
                .setView(listView)
                .setPositiveButton("輸入新地址", (dialog, which) -> {
                    showMacAddressInputDialog();
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (callback != null) {
                        callback.onCancel();
                    }
                });
        
        android.app.Dialog dialog = builder.create();
        
        // 設定列表項目點擊事件
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedMac = deviceList.get(position);
            if (callback != null) {
                callback.onMacAddressSelected(selectedMac);
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    /**
     * 顯示MAC地址輸入對話框
     */
    private void showMacAddressInputDialog() {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bluetooth_input, null);
        
        TextInputEditText etMacAddress = dialogView.findViewById(R.id.etMacAddress);
        
        // 添加自動格式化MAC地址的TextWatcher
        MacAddressTextWatcher textWatcher = new MacAddressTextWatcher(etMacAddress);
        etMacAddress.addTextChangedListener(textWatcher);
        
        // 設置掃描數據接收器
        currentScanDataReceiver = new ScanDataReceiver() {
            @Override
            public void onScanDataReceived(String data) {
                // 在主線程中更新UI
                etMacAddress.post(() -> {
                    try {
                        // 暫時移除TextWatcher避免衝突
                        etMacAddress.removeTextChangedListener(textWatcher);
                        
                        // 獲取當前光標位置，確保不超出範圍
                        int cursorPosition = Math.max(0, etMacAddress.getSelectionStart());
                        String currentText = etMacAddress.getText().toString();
                        
                        // 確保光標位置不超出當前文本長度
                        cursorPosition = Math.min(cursorPosition, currentText.length());
                        
                        // 在光標位置插入掃描數據
                        String newText = currentText.substring(0, cursorPosition) + 
                                       data + 
                                       currentText.substring(cursorPosition);
                        
                        // 設置新文本
                        etMacAddress.setText(newText);
                        
                        // 計算新的光標位置，確保不超出新文本的長度
                        int newCursorPosition = cursorPosition + data.length();
                        newCursorPosition = Math.min(newCursorPosition, newText.length());
                        
                        // 安全地設置光標位置
                        if (newCursorPosition >= 0 && newCursorPosition <= etMacAddress.getText().length()) {
                            etMacAddress.setSelection(newCursorPosition);
                        }
                        
                        // 重新添加TextWatcher
                        etMacAddress.addTextChangedListener(textWatcher);
                        
                    } catch (Exception e) {
                        // 如果出現任何異常，確保重新添加TextWatcher
                        android.util.Log.e("BluetoothDialog", "Error inserting scan data", e);
                        try {
                            etMacAddress.setText(etMacAddress.getText().toString() + data);
                            etMacAddress.setSelection(etMacAddress.getText().length());
                        } catch (Exception ex) {
                            android.util.Log.e("BluetoothDialog", "Failed to append data", ex);
                        }
                        // 確保重新添加TextWatcher
                        etMacAddress.addTextChangedListener(textWatcher);
                    }
                });
            }
        };
        
        android.app.Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("輸入藍芽地址")
                .setMessage("請輸入列印機的藍芽MAC地址")
                .setView(dialogView)
                .setPositiveButton("連線", (dialogInterface, which) -> {
                    String macAddress = etMacAddress.getText().toString().trim();
                    
                    if (macAddress.isEmpty()) {
                        Toast.makeText(context, "請輸入MAC地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 驗證MAC地址格式
                    if (!isValidMacAddress(macAddress)) {
                        Toast.makeText(context, "MAC地址格式錯誤，請使用格式：XX:XX:XX:XX:XX:XX", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // 清除掃描數據接收器
                    currentScanDataReceiver = null;
                    
                    if (callback != null) {
                        callback.onMacAddressSelected(macAddress.toUpperCase());
                    }
                })
                .setNegativeButton("取消", (dialogInterface, which) -> {
                    // 清除掃描數據接收器
                    currentScanDataReceiver = null;
                    
                    if (callback != null) {
                        callback.onCancel();
                    }
                })
                .setOnDismissListener(dialogInterface -> {
                    // Dialog被關閉時清除掃描數據接收器
                    currentScanDataReceiver = null;
                })
                .create();
        
        // 設置對話框的窗口參數，防止軟鍵盤彈出時輸入框被擠壓
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        dialog.show();
    }
    
    /**
     * 驗證MAC地址格式
     */
    private boolean isValidMacAddress(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            return false;
        }
        
        // MAC地址格式：XX:XX:XX:XX:XX:XX (X為十六進制字符)
        String macPattern = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
        return macAddress.matches(macPattern);
    }
    
    /**
     * 已保存設備列表適配器
     */
    private static class SavedDeviceAdapter extends ArrayAdapter<String> {
        
        private String lastConnectedDevice;
        
        public SavedDeviceAdapter(@NonNull Context context, @NonNull List<String> devices, String lastConnectedDevice) {
            super(context, 0, devices);
            this.lastConnectedDevice = lastConnectedDevice;
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        android.R.layout.simple_list_item_2, parent, false);
            }
            
            String macAddress = getItem(position);
            if (macAddress != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);
                
                text1.setText(macAddress);
                
                if (macAddress.equals(lastConnectedDevice)) {
                    text2.setText("最後連線的設備");
                    text2.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    text2.setText("已保存的設備");
                    text2.setTextColor(getContext().getResources().getColor(android.R.color.darker_gray));
                }
            }
            
            return convertView;
        }
    }
    
    /**
     * MAC地址自動格式化TextWatcher
     * 自動將輸入的12位十六進制字符格式化為XX:XX:XX:XX:XX:XX格式
     */
    private static class MacAddressTextWatcher implements TextWatcher {
        private TextInputEditText editText;
        private boolean isFormatting = false;
        
        public MacAddressTextWatcher(TextInputEditText editText) {
            this.editText = editText;
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // 不需要實現
        }
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // 不需要實現
        }
        
        @Override
        public void afterTextChanged(Editable s) {
            if (isFormatting) {
                return; // 避免無限循環
            }
            
            isFormatting = true;
            
            String input = s.toString().replaceAll("[^0-9A-Fa-f]", ""); // 移除所有非十六進制字符
            
            if (input.length() > 12) {
                input = input.substring(0, 12); // 限制最多12個字符
            }
            
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < input.length(); i++) {
                if (i > 0 && i % 2 == 0) {
                    formatted.append(":");
                }
                formatted.append(input.charAt(i));
            }
            
            String formattedText = formatted.toString().toUpperCase();
            
            try {
                // 保存當前光標位置
                int cursorPosition = Math.max(0, editText.getSelectionStart());
                
                // 設置格式化後的文本
                editText.setText(formattedText);
                
                // 調整光標位置，確保不超出文本長度
                int newCursorPosition = Math.min(cursorPosition, formattedText.length());
                
                // 如果光標在冒號位置，移動到下一個位置
                if (newCursorPosition < formattedText.length() && 
                    formattedText.charAt(newCursorPosition) == ':') {
                    newCursorPosition++;
                }
                
                // 確保最終位置在有效範圍內
                newCursorPosition = Math.max(0, Math.min(newCursorPosition, formattedText.length()));
                
                // 安全地設置光標位置
                if (newCursorPosition >= 0 && newCursorPosition <= editText.getText().length()) {
                    editText.setSelection(newCursorPosition);
                }
                
            } catch (Exception e) {
                // 如果設置光標位置失敗，將光標移到末尾
                android.util.Log.e("MacAddressTextWatcher", "Error setting cursor position", e);
                try {
                    editText.setSelection(editText.getText().length());
                } catch (Exception ex) {
                    // 如果連設置到末尾都失敗，就不設置光標位置
                    android.util.Log.e("MacAddressTextWatcher", "Failed to set cursor to end", ex);
                }
            }
            
            isFormatting = false;
        }
    }
    
    /**
     * 靜態方法：接收掃描數據
     * 由MainActivity調用，將掃描數據傳遞給當前活動的Dialog
     */
    public static void receiveScanData(String data) {
        if (currentScanDataReceiver != null) {
            currentScanDataReceiver.onScanDataReceived(data);
        }
    }
    
    /**
     * 檢查是否有活動的掃描數據接收器
     */
    public static boolean hasScanDataReceiver() {
        return currentScanDataReceiver != null;
    }
}
