package com.android.labelprintmanagement.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * 藍芽設備偏好設置管理器
 * 用於保存和管理已成功連線過的藍芽設備MAC地址
 */
public class BluetoothPreferences {
    
    private static final String PREF_NAME = "bluetooth_preferences";
    private static final String KEY_CONNECTED_DEVICES = "connected_devices";
    private static final String KEY_LAST_CONNECTED_DEVICE = "last_connected_device";
    
    private SharedPreferences preferences;
    
    public BluetoothPreferences(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 保存成功連線的設備MAC地址
     */
    public void saveConnectedDevice(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            return;
        }
        
        // 獲取已保存的設備列表
        Set<String> connectedDevices = getConnectedDevices();
        connectedDevices.add(macAddress.toUpperCase());
        
        // 保存更新後的列表
        SharedPreferences.Editor editor = preferences.edit();
        editor.putStringSet(KEY_CONNECTED_DEVICES, connectedDevices);
        editor.putString(KEY_LAST_CONNECTED_DEVICE, macAddress.toUpperCase());
        editor.apply();
    }
    
    /**
     * 獲取所有已成功連線過的設備MAC地址
     */
    public Set<String> getConnectedDevices() {
        Set<String> defaultSet = new HashSet<>();
        Set<String> savedDevices = preferences.getStringSet(KEY_CONNECTED_DEVICES, defaultSet);
        // 返回一個新的HashSet，避免修改原始數據
        return new HashSet<>(savedDevices);
    }
    
    /**
     * 獲取最後一次成功連線的設備MAC地址
     */
    public String getLastConnectedDevice() {
        return preferences.getString(KEY_LAST_CONNECTED_DEVICE, null);
    }
    
    /**
     * 移除指定的設備MAC地址
     */
    public void removeConnectedDevice(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            return;
        }
        
        Set<String> connectedDevices = getConnectedDevices();
        connectedDevices.remove(macAddress.toUpperCase());
        
        SharedPreferences.Editor editor = preferences.edit();
        editor.putStringSet(KEY_CONNECTED_DEVICES, connectedDevices);
        
        // 如果移除的是最後連線的設備，清除最後連線記錄
        String lastConnected = getLastConnectedDevice();
        if (macAddress.toUpperCase().equals(lastConnected)) {
            editor.remove(KEY_LAST_CONNECTED_DEVICE);
        }
        
        editor.apply();
    }
    
    /**
     * 清除所有已保存的設備
     */
    public void clearAllDevices() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(KEY_CONNECTED_DEVICES);
        editor.remove(KEY_LAST_CONNECTED_DEVICE);
        editor.apply();
    }
    
    /**
     * 檢查指定MAC地址是否已保存
     */
    public boolean isDeviceSaved(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            return false;
        }
        
        Set<String> connectedDevices = getConnectedDevices();
        return connectedDevices.contains(macAddress.toUpperCase());
    }
}