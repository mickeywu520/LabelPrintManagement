package com.android.labelprintmanagement;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.labelprintmanagement.model.SmallPackageLabelData;
import com.android.labelprintmanagement.printer.PrinterManager;
import com.android.labelprintmanagement.printer.PrinterSelectionDialog;
import com.android.labelprintmanagement.printer.SmallPackagePrintData;
import com.android.labelprintmanagement.printer.BluetoothConnectionDialog;
import com.android.labelprintmanagement.utils.QRCodeParser;
import com.android.labelprintmanagement.utils.BarcodeImageGenerator;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import android.graphics.Bitmap;

/**
 * 小包裝標籤列印主活動
 * 處理PDA掃描QR碼到列印標籤的完整流程
 */
public class MainActivity extends AppCompatActivity implements PrinterManager.PrinterConnectionCallback {

    private static final String TAG = "MainActivity";
    private static final String BARCODE_BROADCAST_ACTION = "com.barcodeservice.broadcast.string";

    // UI 元件
    private MaterialCardView cardScanStatus, cardQRData, cardLabelPreview;
    private TextView tvScanStatus;
    private TextInputEditText etPartNumber, etProductName, etQuantity, etDcCode;
    private TextView tvPreviewPartNumber, tvPreviewProductName, tvPreviewQuantity, tvPreviewDcCode;
    private ImageView ivPreviewPartNumberBarcode, ivPreviewQuantityBarcode, ivPreviewDcBarcode;
    private Button btnClear, btnPreview, btnPrint;
    private ImageView ivBluetoothStatus, ivScanIcon;

    // 數據和管理器
    private SmallPackageLabelData labelData;
    private PrinterManager printerManager;
    private PrinterSelectionDialog printerSelectionDialog;
    private BluetoothConnectionDialog bluetoothConnectionDialog;
    private boolean isPrinterConnected = false;

    // Broadcast Receiver for PDA barcode scanning
    private BroadcastReceiver barcodeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BARCODE_BROADCAST_ACTION.equals(intent.getAction())) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    String barcodedata = bundle.getString("barcodedata");
                    int symbology = bundle.getInt("symbology");

                    Log.d(TAG, "Received barcode: " + barcodedata + ", symbology: " + symbology);
                    
                    // 根據symbology類型處理掃描數據
                    if (symbology == 28) {
                        // QR Code - 處理QR碼解析
                        handleBarcodeScanned(barcodedata);
                    } else {
                        // 其他類型 - 插入到當前光標位置
                        insertTextAtCursor(barcodedata);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_small_package_label);

        // 初始化數據
        labelData = new SmallPackageLabelData();

        // 初始化列印管理器
        initializePrinterManager();

        // 初始化UI
        initializeUI();

        // 註冊廣播接收器
        registerBarcodeReceiver();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 取消註冊廣播接收器
        unregisterReceiver(barcodeReceiver);

        // 斷開列印機連線
        if (printerManager != null) {
            printerManager.disconnect();
        }
    }

    // 移除了舊的 updateQuantityInData 方法

    private void initializePrinterManager() {
        printerManager = new PrinterManager(this);
        printerManager.setCallback(this);
        printerSelectionDialog = new PrinterSelectionDialog(this, printerManager);
        bluetoothConnectionDialog = new BluetoothConnectionDialog(this, printerManager);
        
        // 設置藍芽連線回調
        bluetoothConnectionDialog.setCallback(new BluetoothConnectionDialog.BluetoothConnectionCallback() {
            @Override
            public void onMacAddressSelected(String macAddress) {
                // 使用選擇的MAC地址連線
                printerManager.connectToBluetoothPrinter(macAddress);
            }
            
            @Override
            public void onCancel() {
                // 用戶取消連線
                showToast("已取消藍芽連線");
            }
        });
        
        // 設定預設的TSC RE310印表機MAC地址
        String defaultPrinterMac = "00:80:A3:71:EA:61";
        // 如果沒有已保存的設備，則使用預設MAC地址
        if (printerManager.getSavedBluetoothDevices().isEmpty() && 
            printerManager.getLastConnectedDeviceMac() == null) {
            // 這裡可以選擇自動連線到預設印表機，或者在UI中顯示預設地址
            // 為了用戶體驗，我們選擇在UI中顯示預設地址，但不自動連線
        }
    }

    private void initializeUI() {
        // 初始化卡片
        cardScanStatus = findViewById(R.id.cardScanStatus);
        cardQRData = findViewById(R.id.cardQRData);
        cardLabelPreview = findViewById(R.id.cardLabelPreview);

        // 初始化狀態顯示
        tvScanStatus = findViewById(R.id.tvScanStatus);
        ivScanIcon = findViewById(R.id.ivScanIcon);

        // 初始化輸入欄位
        etPartNumber = findViewById(R.id.etPartNumber);
        etProductName = findViewById(R.id.etProductName);
        etQuantity = findViewById(R.id.etQuantity);
        etDcCode = findViewById(R.id.etDcCode);

        // 初始化預覽欄位
        tvPreviewPartNumber = findViewById(R.id.tvPreviewPartNumber);
        tvPreviewProductName = findViewById(R.id.tvPreviewProductName);
        tvPreviewQuantity = findViewById(R.id.tvPreviewQuantity);
        tvPreviewDcCode = findViewById(R.id.tvPreviewDcCode);

        // 初始化預覽條碼
        ivPreviewPartNumberBarcode = findViewById(R.id.ivPreviewPartNumberBarcode);
        ivPreviewQuantityBarcode = findViewById(R.id.ivPreviewQuantityBarcode);
        ivPreviewDcBarcode = findViewById(R.id.ivPreviewDcBarcode);

        // 初始化按鈕
        btnClear = findViewById(R.id.btnClear);
        btnPreview = findViewById(R.id.btnPreview);
        btnPrint = findViewById(R.id.btnPrint);

        // 初始化藍芽狀態圖示
        ivBluetoothStatus = findViewById(R.id.ivBluetoothStatus);

        // 設置按鈕點擊事件
        setupButtonListeners();

        // 設置數量欄位變化監聽
        setupQuantityListener();

        // 添加測試按鈕 (開發階段使用)
        setupTestButton();
    }

    private void setupButtonListeners() {
        btnClear.setOnClickListener(v -> clearData());
        btnPreview.setOnClickListener(v -> showPreview());
        btnPrint.setOnClickListener(v -> printLabel());
        ivBluetoothStatus.setOnClickListener(v -> checkBluetoothAndPrinterStatus());
    }

    private void setupQuantityListener() {
        // 監聽數量欄位變化，自動更新預覽
        etQuantity.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                updateDataFromUI();
                if (cardLabelPreview.getVisibility() == View.VISIBLE) {
                    updatePreviewDisplay();
                }
            }
        });
    }

    private void registerBarcodeReceiver() {
        IntentFilter filter = new IntentFilter(BARCODE_BROADCAST_ACTION);
        registerReceiver(barcodeReceiver, filter);
        Log.d(TAG, "Barcode receiver registered");
    }

    private void handleBarcodeScanned(String barcodeData) {
        if (barcodeData == null || barcodeData.trim().isEmpty()) {
            showToast("掃描數據為空");
            return;
        }

        Log.d(TAG, "Processing barcode data: " + barcodeData);

        // 解析QR碼內容
        QRCodeParser.QRData qrData = QRCodeParser.smartParseQRContent(barcodeData);

        if (qrData != null && qrData.isValid()) {
            // 更新數據模型
            labelData = SmallPackageLabelData.fromQRData(qrData);

            // 更新UI顯示
            updateUIWithScannedData();

            // 更新掃描狀態
            updateScanStatus("掃描成功！", true);

            showToast("QR碼掃描成功");
        } else {
            updateScanStatus("掃描失敗，請重新掃描", false);
            showToast("QR碼格式錯誤，請重新掃描");
        }
    }

    private void updateUIWithScannedData() {
        // 顯示QR數據卡片
        cardQRData.setVisibility(View.VISIBLE);

        // 填充數據到輸入欄位
        etPartNumber.setText(labelData.getPartNumber());
        etProductName.setText(labelData.getProductName());
        etQuantity.setText(labelData.getQuantity());
        etDcCode.setText(labelData.getDcCode());

        // 不再隱藏預覽卡片，讓用戶可以隨時預覽
    }

    private void updateScanStatus(String message, boolean success) {
        tvScanStatus.setText(message);
        if (success) {
            ivScanIcon.setColorFilter(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            ivScanIcon.setColorFilter(getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    private void checkBluetoothAndPrinterStatus() {
        if (!printerManager.isBluetoothAvailable()) {
            showToast("此設備不支援藍芽功能");
            updateBluetoothIcon(false);
            return;
        }

        if (!printerManager.isBluetoothEnabled()) {
            // 顯示對話框詢問用戶是否啟動藍牙
            new MaterialAlertDialogBuilder(this)
                    .setTitle("啟用藍牙")
                    .setMessage("藍牙目前未啟用，是否要啟用藍牙？")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("啟用", (dialog, which) -> {
                        // 請求啟用藍牙
                        Intent enableBtIntent = new Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivity(enableBtIntent);
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        // 用戶取消，顯示提示訊息
                        showToast("請手動啟用藍牙功能");
                    })
                    .setCancelable(false) // 防止點擊對話框外部關閉
                    .show();
            updateBluetoothIcon(false);
            return;
        }

        if (!printerManager.hasBluetoothPermissions()) {
            showToast("缺少藍芽權限，請在設定中授予權限");
            updateBluetoothIcon(false);
            return;
        }

        // 如果已經連線，顯示狀態；否則顯示藍芽連線對話框
        if (isPrinterConnected) {
            showToast("列印機已連線");
        } else {
            bluetoothConnectionDialog.showBluetoothConnectionDialog();
        }
    }

    private void clearData() {
        // 清空數據
        labelData.clear();

        // 清空輸入欄位
        etPartNumber.setText("");
        etProductName.setText("");
        etQuantity.setText("");
        etDcCode.setText("");

        // 隱藏預覽卡片，但保持QR數據卡片可見
        cardLabelPreview.setVisibility(View.GONE);

        // 重置條碼圖像
        resetBarcodeImages();

        // 重置掃描狀態
        updateScanStatus("請使用PDA掃描QR碼...", false);
        ivScanIcon.setColorFilter(getResources().getColor(android.R.color.darker_gray));

        showToast("數據已清除");
    }

    private void showPreview() {
        // 更新數據模型中的所有欄位
        updateDataFromUI();

        // 驗證數據
        if (!labelData.isValid()) {
            showToast("數據不完整：" + labelData.getMissingFieldsDescription());
            return;
        }

        // 顯示預覽
        cardLabelPreview.setVisibility(View.VISIBLE);
        updatePreviewDisplay();

        showToast("預覽已更新");
    }

    private void updateDataFromUI() {
        String partNumber = etPartNumber.getText().toString().trim();
        String productName = etProductName.getText().toString().trim();
        String quantity = etQuantity.getText().toString().trim();
        String dcCode = etDcCode.getText().toString().trim();
        
        labelData.setPartNumber(partNumber);
        labelData.setProductName(productName);
        labelData.setQuantity(quantity);
        labelData.setDcCode(dcCode);
    }

    private void updatePreviewDisplay() {
        // 更新文字內容
        tvPreviewPartNumber.setText(labelData.getPartNumber());
        tvPreviewProductName.setText(labelData.getProductName());
        tvPreviewQuantity.setText(labelData.getFormattedQuantity());
        tvPreviewDcCode.setText(labelData.getDcCode());

        // 生成並顯示條碼圖像
        generateAndDisplayBarcodes();
    }

    /**
     * 生成並顯示條碼圖像
     */
    private void generateAndDisplayBarcodes() {
        // 在背景線程生成條碼，避免阻塞 UI
        new Thread(() -> {
            try {
                // 生成料號條碼
                Bitmap partNumberBarcode = BarcodeImageGenerator.generatePreviewBarcode(
                    labelData.getPartNumber(), "partnumber");

                // 生成數量條碼
                Bitmap quantityBarcode = BarcodeImageGenerator.generatePreviewBarcode(
                    labelData.getQuantity(), "quantity");

                // 生成 D/C 條碼
                Bitmap dcBarcode = BarcodeImageGenerator.generatePreviewBarcode(
                    labelData.getDcCode(), "dc");

                // 在主線程更新 UI
                runOnUiThread(() -> {
                    // 設置料號條碼
                    if (partNumberBarcode != null) {
                        ivPreviewPartNumberBarcode.setImageBitmap(partNumberBarcode);
                        ivPreviewPartNumberBarcode.setBackgroundResource(0); // 移除佔位符背景
                    } else {
                        Log.w(TAG, "Failed to generate part number barcode");
                    }

                    // 設置數量條碼
                    if (quantityBarcode != null) {
                        ivPreviewQuantityBarcode.setImageBitmap(quantityBarcode);
                        ivPreviewQuantityBarcode.setBackgroundResource(0); // 移除佔位符背景
                    } else {
                        Log.w(TAG, "Failed to generate quantity barcode");
                    }

                    // 設置 D/C 條碼
                    if (dcBarcode != null) {
                        ivPreviewDcBarcode.setImageBitmap(dcBarcode);
                        ivPreviewDcBarcode.setBackgroundResource(0); // 移除佔位符背景
                    } else {
                        Log.w(TAG, "Failed to generate DC barcode");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error generating barcodes", e);
                runOnUiThread(() -> showToast("條碼生成失敗"));
            }
        }).start();
    }

    private void updateBluetoothIcon(boolean connected) {
        if (connected) {
            ivBluetoothStatus.setColorFilter(getResources().getColor(android.R.color.holo_blue_bright));
        } else {
            ivBluetoothStatus.setColorFilter(getResources().getColor(android.R.color.darker_gray));
        }
    }

    /**
     * 重置條碼圖像為佔位符
     */
    private void resetBarcodeImages() {
        if (ivPreviewPartNumberBarcode != null) {
            ivPreviewPartNumberBarcode.setImageBitmap(null);
            ivPreviewPartNumberBarcode.setBackgroundResource(R.drawable.barcode_placeholder);
        }
        if (ivPreviewQuantityBarcode != null) {
            ivPreviewQuantityBarcode.setImageBitmap(null);
            ivPreviewQuantityBarcode.setBackgroundResource(R.drawable.barcode_placeholder);
        }
        if (ivPreviewDcBarcode != null) {
            ivPreviewDcBarcode.setImageBitmap(null);
            ivPreviewDcBarcode.setBackgroundResource(R.drawable.barcode_placeholder);
        }
    }

    private void printLabel() {
        // 檢查列印機連線
        if (!isPrinterConnected) {
            showToast("請先連接列印機");
            checkBluetoothAndPrinterStatus();
            return;
        }

        // 更新並驗證數據
        updateDataFromUI();
        if (!labelData.isValid()) {
            showToast("數據不完整：" + labelData.getMissingFieldsDescription());
            return;
        }

        // 創建列印數據
        SmallPackagePrintData printData = new SmallPackagePrintData(labelData);

        // 執行列印
        showToast("正在列印標籤...");
        printerManager.printSmallPackageLabel(printData);
    }

    private void disconnectPrinterAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isPrinterConnected) {
                printerManager.disconnect();
                showToast("列印機已自動斷線");
            }
        }, 3000);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 將掃描的文本插入到當前有焦點的輸入框的光標位置
     */
    private void insertTextAtCursor(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // 首先檢查是否有Dialog中的掃描數據接收器
        if (BluetoothConnectionDialog.hasScanDataReceiver()) {
            BluetoothConnectionDialog.receiveScanData(text);
            Log.d(TAG, "Sent scan data to dialog: " + text);
            showToast("已插入掃描內容到對話框");
            return;
        }
        
        // 獲取當前有焦點的View
        View currentFocus = getCurrentFocus();
        
        if (currentFocus instanceof EditText) {
            EditText editText = (EditText) currentFocus;
            
            // 獲取當前光標位置
            int cursorPosition = editText.getSelectionStart();
            
            // 獲取當前文本
            String currentText = editText.getText().toString();
            
            // 在光標位置插入新文本
            String newText = currentText.substring(0, cursorPosition) + 
                           text + 
                           currentText.substring(cursorPosition);
            
            // 設置新文本
            editText.setText(newText);
            
            // 將光標移動到插入文本的末尾
            editText.setSelection(cursorPosition + text.length());
            
            Log.d(TAG, "Inserted text '" + text + "' at cursor position " + cursorPosition);
            showToast("已插入掃描內容");
        } else {
            // 如果沒有輸入框有焦點，顯示提示
            showToast("請先點擊要輸入的欄位");
            Log.d(TAG, "No EditText has focus, cannot insert text: " + text);
        }
    }

    /**
     * 設置測試按鈕 (開發階段使用)
     * 用於模擬 PDA 廣播，方便測試
     */
    private void setupTestButton() {
        // 長按掃描狀態卡片來觸發測試
        cardScanStatus.setOnLongClickListener(v -> {
            // 模擬 PDA 廣播
            String testQRContent = "PN1710002190000P;DESFPC-7602 BOTTOM COVER BRACKET;QTY1000;DC250601";

            Log.d(TAG, "Test broadcast triggered");
            handleBarcodeScanned(testQRContent);
            showToast("測試 QR 碼已掃描");
            return true;
        });
    }

    /**
     * 顯示離開程式確認對話框
     * 當用戶按下 Back 鍵時調用
     */
    private void showExitConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("離開程式")
                .setMessage("確定要離開小包裝標籤列印程式嗎？")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("確定", (dialog, which) -> {
                    // 用戶確認離開
                    Log.d(TAG, "User confirmed exit");
                    finishAndRemoveTask(); // 完全退出應用程式
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    // 用戶取消，留在當前畫面
                    Log.d(TAG, "User cancelled exit");
                    dialog.dismiss();
                })
                .setCancelable(false) // 防止點擊對話框外部關閉
                .show();
    }

    // PrinterManager.PrinterConnectionCallback 實現

    @Override
    public void onConnectionStatusChanged(PrinterManager.ConnectionStatus status, String message) {
        runOnUiThread(() -> {
            switch (status) {
                case CONNECTED:
                    isPrinterConnected = true;
                    updateBluetoothIcon(true);
                    showToast("列印機連線成功");
                    break;
                case DISCONNECTED:
                    isPrinterConnected = false;
                    updateBluetoothIcon(false);
                    showToast("列印機已斷線");
                    break;
                case CONNECTING:
                    showToast("正在連線列印機...");
                    break;
                case ERROR:
                    isPrinterConnected = false;
                    updateBluetoothIcon(false);
                    showToast("列印機連線失敗: " + message);
                    break;
            }
        });
    }

    @Override
    public void onPrintResult(boolean success, String message) {
        runOnUiThread(() -> {
            if (success) {
                showToast("列印成功: " + message);
//                disconnectPrinterAfterDelay();
            } else {
                showToast("列印失敗: " + message);
            }
        });
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        runOnUiThread(() -> {
            Log.d(TAG, "Found device: " + device.getAddress());
        });
    }

}
