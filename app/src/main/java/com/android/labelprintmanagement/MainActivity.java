package com.android.labelprintmanagement;

import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.labelprintmanagement.printer.PrintData;
import com.android.labelprintmanagement.printer.PrinterManager;
import com.android.labelprintmanagement.printer.PrinterSelectionDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements PrinterManager.PrinterConnectionCallback {

    private static final String TAG = "MainActivity";

    // UI 元件
    private TextInputEditText etQuantity, etDC, etHwVer, etFwVer;
    private Button btnScanQRCode, btnPreview, btnBackToStep1;
    private MaterialCardView cardLabelPreview;
    private TextView tvPartName, tvPartNo;
    private ImageView ivBluetoothStatus;
    private LinearLayout layoutStep1;

    // New TextViews for displaying fetched data in Step 2 (these will be removed or repurposed later)
    private TextView tvDocTypeDisplay, tvDocNumberDisplay, tvDocItemDisplay, tvVendorCode, tvSpec, tvMonthDisplay;

    // 儲存從QR Code獲取的資料
    private QRCodeData qrCodeData;

    // 列印管理相關
    private PrinterManager printerManager;
    private PrinterSelectionDialog printerSelectionDialog;
    private boolean isPrinterConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化列印管理器
        initializePrinterManager();

        // 初始化 UI 元件
        initializeUI();

        // 設定按鈕點擊事件
        btnScanQRCode.setOnClickListener(v -> simulateQRCodeScan()); // Changed to simulate scan
        btnPreview.setOnClickListener(v -> showPreviewDialog());
    }

    private void initializePrinterManager() {
        printerManager = new PrinterManager(this);
        printerManager.setCallback(this);

        printerSelectionDialog = new PrinterSelectionDialog(this, printerManager);
        printerSelectionDialog.setCallback(new PrinterSelectionDialog.PrinterSelectionCallback() {
            @Override
            public void onBluetoothPrinterSelected(BluetoothDevice device) {
                printerManager.connectToBluetoothPrinter(device);
            }

            @Override
            public void onWifiPrinterSelected(String ip, int port) {
                printerManager.connectToWifiPrinter(ip, port);
            }

            @Override
            public void onCancel() {
                Toast.makeText(MainActivity.this, "已取消列印機選擇", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeUI() {
        btnScanQRCode = findViewById(R.id.btnFetchData); // Renamed from btnFetchData
        btnScanQRCode.setText("掃描QR Code"); // Update button text
        btnPreview = findViewById(R.id.btnPreview);

        cardLabelPreview = findViewById(R.id.cardLabelPreview);
        tvPartName = findViewById(R.id.tvPartName);
        tvPartNo = findViewById(R.id.tvPartNo); // Corrected ID to match activity_main.xml

        etQuantity = findViewById(R.id.etQuantity);
        etDC = findViewById(R.id.etDC);
        etHwVer = findViewById(R.id.etHwVer);
        etFwVer = findViewById(R.id.etFwVer);

        ivBluetoothStatus = findViewById(R.id.ivBluetoothStatus);
        ivBluetoothStatus.setOnClickListener(v -> checkBluetoothAndPrinterStatus());

        layoutStep1 = findViewById(R.id.layoutStep1);
        btnBackToStep1 = findViewById(R.id.btnBackToStep1);
        btnBackToStep1.setOnClickListener(v -> {
            layoutStep1.setVisibility(View.VISIBLE);
            cardLabelPreview.setVisibility(View.GONE);
            btnPreview.setVisibility(View.GONE);
            btnBackToStep1.setVisibility(View.GONE);
            clearUIFields();
        });

        // Initialize new TextViews for Step 2 display (these will be removed or repurposed later)
//        tvDocTypeDisplay = findViewById(R.id.tvDocTypeDisplay);
//        tvDocNumberDisplay = findViewById(R.id.tvDocNumberDisplay);
//        tvDocItemDisplay = findViewById(R.id.tvDocItemDisplay);
//        tvVendorCode = findViewById(R.id.tvVendorCode);
//        tvSpec = findViewById(R.id.tvSpec);
//        tvMonthDisplay = findViewById(R.id.tvMonthDisplay);

        setupEditTextNavigation();
    }

    /**
     * 設定 EditText 欄位間的 Enter 鍵跳轉功能
     */
    private void setupEditTextNavigation() {
        etQuantity.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                etDC.requestFocus();
                return true;
            }
            return false;
        });

        etDC.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                etHwVer.requestFocus();
                return true;
            }
            return false;
        });

        etHwVer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                etFwVer.requestFocus();
                return true;
            }
            return false;
        });

        etFwVer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void checkBluetoothAndPrinterStatus() {
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(this, "此設備不支援藍芽功能", Toast.LENGTH_SHORT).show();
            updateBluetoothIcon(false);
            return;
        }

        if (!printerManager.isBluetoothEnabled()) {
            showBluetoothSettingsDialog();
            updateBluetoothIcon(false);
            return;
        }

        if (!printerManager.hasBluetoothPermissions()) {
            Toast.makeText(this, "缺少藍芽權限，請在設定中授予權限", Toast.LENGTH_LONG).show();
            updateBluetoothIcon(false);
            return;
        }

        if (isPrinterConnected) {
            Toast.makeText(this, "列印機已連線", Toast.LENGTH_SHORT).show();
        } else {
            printerSelectionDialog.showPrinterTypeSelection();
        }
    }

    private void showBluetoothSettingsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("藍芽未開啟")
                .setMessage("請開啟藍芽以連線至列印機。")
                .setPositiveButton("前往設定", (dialog, which) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "藍芽未開啟，無法連線列印機。", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // Helper method to hide the keyboard
    private void hideKeyboard() {
        View view = MainActivity.this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void updateBluetoothIcon(boolean isConnected) {
        if (isConnected) {
            ivBluetoothStatus.setColorFilter(Color.BLUE);
        } else {
            ivBluetoothStatus.setColorFilter(Color.parseColor("#808080"));
        }
    }

    // Placeholder for QR Code scan initiation
    private void simulateQRCodeScan() {
        Toast.makeText(this, "請使用內建條碼引擎掃描QR Code", Toast.LENGTH_LONG).show();
        // Simulate a QR code scan result for testing purposes
        String simulatedQRCodeContent = "料號:1710002190000P\n品名:FPC-7602 BOTTOM COVER BRACKET\n數量:1000\nD/C:250601";
        parseQRCodeContent(simulatedQRCodeContent);
    }

    private void parseQRCodeContent(String qrCodeContent) {
        Map<String, String> parsedData = new HashMap<>();
        Pattern pattern = Pattern.compile("(料號|品名|數量|D/C):(.+)");
        Matcher matcher = pattern.matcher(qrCodeContent);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            parsedData.put(key, value);
        }

        if (parsedData.containsKey("料號") && parsedData.containsKey("品名") &&
                parsedData.containsKey("數量") && parsedData.containsKey("D/C")) {
            qrCodeData = new QRCodeData(
                    parsedData.get("料號"),
                    parsedData.get("品名"),
                    parsedData.get("數量"),
                    parsedData.get("D/C")
            );
            populateUIWithData();
        } else {
            Toast.makeText(this, "QR Code內容格式不正確", Toast.LENGTH_LONG).show();
            Log.e(TAG, "QR Code content parsing failed. Missing required fields.");
        }
    }

    private void populateUIWithData() {
        if (qrCodeData == null) {
            Toast.makeText(this, "無QR Code資料可顯示。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 填入從QR Code解析的資料
        tvPartName.setText("品名: " + qrCodeData.getPartName());
        tvPartNo.setText("料號: " + qrCodeData.getPartNo());

        // 填入可編輯欄位的預設值
        etQuantity.setText(qrCodeData.getQuantity());
        etDC.setText(qrCodeData.getDc());
        etHwVer.setText("");
        etFwVer.setText("");

        // Hide Step 1 layout and show Step 2 (preview card)
        layoutStep1.setVisibility(View.GONE);
        cardLabelPreview.setVisibility(View.VISIBLE);
        btnPreview.setVisibility(View.VISIBLE);
        btnBackToStep1.setVisibility(View.VISIBLE);
    }

    private void clearUIFields() {
        tvPartName.setText("品名: ");
        tvPartNo.setText("料號: ");
        etQuantity.setText("");
        etDC.setText("");
        etHwVer.setText("");
        etFwVer.setText("");
        qrCodeData = null;
    }

    private boolean validateInputFields() {
        String quantity = etQuantity.getText().toString().trim();
        if (quantity.isEmpty()) {
            Toast.makeText(this, "請輸入數量。", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showPreviewDialog() {
        if (qrCodeData == null) {
            Toast.makeText(this, "請先掃描QR Code獲取資料。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 建立並加載 Dialog 的 View
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_print_preview, null);

        // 找到 Dialog 中的 TextView
        TextView previewPartName = dialogView.findViewById(R.id.preview_tvPartName);
        TextView previewPartNo = dialogView.findViewById(R.id.preview_tvPartNo);
        TextView previewQuantity = dialogView.findViewById(R.id.preview_tvQuantity);
        TextView previewDC = dialogView.findViewById(R.id.preview_tvDC);
        TextView previewHwVer = dialogView.findViewById(R.id.preview_tvHwVer);
        TextView previewFwVer = dialogView.findViewById(R.id.preview_tvFwVer);
        ImageView previewArborLogo = dialogView.findViewById(R.id.preview_ivArborLogo);

        try {
            // 從 QR Code 資料和 UI 上取得最終要列印的資料並填入
            previewPartName.setText("品名: " + qrCodeData.getPartName());
            previewPartNo.setText("料號: " + qrCodeData.getPartNo());

            // 讀取 EditText 中的現有值 (用戶可能已修改)
            String currentQuantity = etQuantity.getText().toString();
            String currentDC = etDC.getText().toString();
            String currentHwVer = etHwVer.getText().toString();
            String currentFwVer = etFwVer.getText().toString();

            previewQuantity.setText("數量: " + currentQuantity);
            previewDC.setText("D/C: " + currentDC);
            previewHwVer.setText("H/W Ver: " + currentHwVer);
            previewFwVer.setText("F/W Ver: " + currentFwVer);

            // For Arbor Logo, it will show the placeholder drawable set in XML.
            previewArborLogo.setImageResource(R.drawable.arbor_logo_placeholder);

        } catch (Exception e) { // Catch generic Exception for simplicity, can be more specific
            Log.e(TAG, "Preview data population failed: " + e.getMessage());
            Toast.makeText(this, "預覽資料準備失敗", Toast.LENGTH_LONG).show();
        }

        // 建立 Dialog
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("確認列印", (dialog, which) -> {
                    performPrintWithChecks();
                })
                .setNegativeButton("關閉", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * 執行列印前的檢查和列印操作
     */
    private void performPrintWithChecks() {
        // 1. 檢查是否有資料
        if (qrCodeData == null) {
            Toast.makeText(this, "請先掃描QR Code獲取資料。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 檢查列印機連線狀態
        if (!isPrinterConnected) {
            Toast.makeText(this, "列印機未連線，請先連線列印機", Toast.LENGTH_LONG).show();
            checkBluetoothAndPrinterStatus();
            return;
        }

        // 3. 驗證輸入資料
        String quantity = etQuantity.getText().toString().trim();
        String dc = etDC.getText().toString().trim();
        String hwVer = etHwVer.getText().toString().trim();
        String fwVer = etFwVer.getText().toString().trim();

        if (quantity.isEmpty()) {
            Toast.makeText(this, "請輸入數量", Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. 創建列印資料
        PrintData printData = new PrintData(
                qrCodeData.getPartNo(),
                qrCodeData.getPartName(),
                quantity,
                dc,
                hwVer,
                fwVer
        );

        // 5. 驗證列印資料
        if (!printData.isValid()) {
            Toast.makeText(this, "列印資料不完整", Toast.LENGTH_SHORT).show();
            return;
        }

        // 6. 執行列印
        Toast.makeText(this, "正在發送列印指令...", Toast.LENGTH_SHORT).show();
        printerManager.printLabel(printData);
    }

    // 實現 PrinterManager.PrinterConnectionCallback 介面
    @Override
    public void onConnectionStatusChanged(PrinterManager.ConnectionStatus status, String message) {
        runOnUiThread(() -> {
            switch (status) {
                case CONNECTING:
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    updateBluetoothIcon(false);
                    isPrinterConnected = false;
                    break;
                case CONNECTED:
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    updateBluetoothIcon(true);
                    isPrinterConnected = true;
                    break;
                case DISCONNECTED:
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    updateBluetoothIcon(false);
                    isPrinterConnected = false;
                    break;
                case ERROR:
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    updateBluetoothIcon(false);
                    isPrinterConnected = false;
                    break;
            }
        });
    }

    @Override
    public void onPrintResult(boolean success, String message) {
        runOnUiThread(() -> {
            if (success) {
                Toast.makeText(this, "列印成功: " + message, Toast.LENGTH_SHORT).show();
                // 列印完成後自動斷線
                disconnectPrinterAfterDelay();
            } else {
                Toast.makeText(this, "列印失敗: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * 列印完成後延遲斷線
     * 給予一些時間讓列印機完成處理，然後自動斷線
     */
    private void disconnectPrinterAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isPrinterConnected) {
                printerManager.disconnect();
                Toast.makeText(this, "列印機已自動斷線", Toast.LENGTH_SHORT).show();
            }
        }, 3000); // 3秒後自動斷線
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        // 可以在這裡處理發現新設備的邏輯
        runOnUiThread(() -> {
            Log.d(TAG, "Found device: " + device.getAddress());
        });
    }

    // New data class to hold parsed QR Code content
    private static class QRCodeData {
        private String partNo;
        private String partName;
        private String quantity;
        private String dc;

        public QRCodeData(String partNo, String partName, String quantity, String dc) {
            this.partNo = partNo;
            this.partName = partName;
            this.quantity = quantity;
            this.dc = dc;
        }

        public String getPartNo() {
            return partNo;
        }

        public String getPartName() {
            return partName;
        }

        public String getQuantity() {
            return quantity;
        }

        public String getDc() {
            return dc;
        }
    }
}
