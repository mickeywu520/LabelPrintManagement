package com.android.labelprintmanagement;

import androidx.appcompat.app.AppCompatActivity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
import android.widget.LinearLayout; // Added import for LinearLayout
import android.widget.TextView;
import android.widget.Toast;

import com.android.labelprintmanagement.printer.PrintData;
import com.android.labelprintmanagement.printer.PrinterManager;
import com.android.labelprintmanagement.printer.PrinterSelectionDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements PrinterManager.PrinterConnectionCallback {

    private static final String TAG = "MainActivity";
    private static final String SERVER_BASE_URL = "http://192.168.0.13:100/";
    private static final String API_ENDPOINT = SERVER_BASE_URL + "api/ErpToBarcode/getPurchaseReceipt";

    // UI 元件
    private TextInputEditText etDocType, etDocNumber, etDocItem;
    private TextInputEditText etQuantity, etDC, etHwVer, etFwVer;
    private Button btnFetchData, btnPreview, btnBackToStep1;
    private MaterialCardView cardLabelPreview;
    private TextView tvVendor, tvPartName, tvPartNo, tvDate;
    private ImageView ivServerStatus; // Server status icon
    private ImageView ivBluetoothStatus; // Bluetooth status icon
    private LinearLayout layoutStep1; // Step 1 layout

    // New TextViews for displaying fetched data in Step 2
    private TextView tvDocTypeDisplay, tvDocNumberDisplay, tvDocItemDisplay, tvVendorCode, tvSpec, tvMonthDisplay;
    // Removed redundant TextViews: tvTypeDisplay, tvItemNoDisplay, tvOrderNoDisplay
    // Removed ImageView ivArborLogoDisplay, ivQrCodeDisplay;

    // 儲存從伺服器獲取的固定資料
    private JSONObject fetchedData;
    private boolean isServerConnected = false; // New flag for server connection status

    // 列印管理相關
    private PrinterManager printerManager;
    private PrinterSelectionDialog printerSelectionDialog;
    private boolean isPrinterConnected = false;

    private OkHttpClient httpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        httpClient = new OkHttpClient();

        // 初始化列印管理器
        initializePrinterManager();

        // 初始化 UI 元件
        initializeUI();

        // 設定按鈕點擊事件
        btnFetchData.setOnClickListener(v -> {
            if (isServerConnected) {
                if (validateInputFields()) {
                    makeApiCall();
                }
            } else {
                Toast.makeText(MainActivity.this, "沒有連線，請點擊伺服器圖示進行連線。", Toast.LENGTH_LONG).show();
            }
        });
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
        etDocType = findViewById(R.id.etDocType);
        etDocNumber = findViewById(R.id.etDocNumber);
        etDocItem = findViewById(R.id.etDocItem);

        btnFetchData = findViewById(R.id.btnFetchData);
        btnPreview = findViewById(R.id.btnPreview);

        cardLabelPreview = findViewById(R.id.cardLabelPreview);
        tvVendor = findViewById(R.id.tvVendor);
        tvPartName = findViewById(R.id.tvPartName);
        tvPartNo = findViewById(R.id.tvPartNo);
        tvDate = findViewById(R.id.tvDate);

        etQuantity = findViewById(R.id.etQuantity);
        etDC = findViewById(R.id.etDC);
        etHwVer = findViewById(R.id.etHwVer);
        etFwVer = findViewById(R.id.etFwVer);

        ivServerStatus = findViewById(R.id.ivServerStatus); // Initialize server status icon
        ivServerStatus.setOnClickListener(v -> checkWifiAndServerStatus()); // Set click listener for server icon

        ivBluetoothStatus = findViewById(R.id.ivBluetoothStatus); // Initialize bluetooth status icon
        ivBluetoothStatus.setOnClickListener(v -> checkBluetoothAndPrinterStatus()); // Set click listener for bluetooth icon

        layoutStep1 = findViewById(R.id.layoutStep1); // Initialize layoutStep1
        btnBackToStep1 = findViewById(R.id.btnBackToStep1); // Initialize btnBackToStep1
        btnBackToStep1.setOnClickListener(v -> {
            layoutStep1.setVisibility(View.VISIBLE);
            cardLabelPreview.setVisibility(View.GONE);
            btnPreview.setVisibility(View.GONE); // Hide preview button when going back
        });

        // Initialize new TextViews for Step 2 display
        tvDocTypeDisplay = findViewById(R.id.tvDocTypeDisplay);
        tvDocNumberDisplay = findViewById(R.id.tvDocNumberDisplay);
        tvDocItemDisplay = findViewById(R.id.tvDocItemDisplay);
        tvVendorCode = findViewById(R.id.tvVendorCode);
        tvSpec = findViewById(R.id.tvSpec);
        tvMonthDisplay = findViewById(R.id.tvMonthDisplay);
        // Removed initialization for tvTypeDisplay, tvItemNoDisplay, tvOrderNoDisplay
        // Removed initialization for ivArborLogoDisplay and ivQrCodeDisplay

        // 設定 EditText 欄位間的 Enter 鍵跳轉
        setupEditTextNavigation();
    }

    /**
     * 設定 EditText 欄位間的 Enter 鍵跳轉功能
     */
    private void setupEditTextNavigation() {
        // 步驟一的 EditText 跳轉
        etDocType.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                etDocNumber.requestFocus();
                return true;
            }
            return false;
        });

        etDocNumber.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                etDocItem.requestFocus();
                return true;
            }
            return false;
        });

        etDocItem.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // 步驟二的 EditText 跳轉
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

    private void checkWifiAndServerStatus() {
        if (!isWifiConnected()) {
            showWifiSettingsDialog();
            updateServerIcon(false); // Server icon remains grayscale
            return;
        }

        Toast.makeText(this, "正在檢查伺服器連線...", Toast.LENGTH_SHORT).show();
        checkServerStatus();
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

        // 如果已經連線，顯示狀態；否則顯示列印機選擇對話框
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

    private boolean isWifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return false;
    }

    private void showWifiSettingsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("WiFi 未開啟")
                .setMessage("請開啟 WiFi 以連線至伺服器。")
                .setPositiveButton("前往設定", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    Toast.makeText(MainActivity.this, "WiFi 未開啟，無法獲取資料。", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void checkServerStatus() {
        Request request = new Request.Builder()
                .url(SERVER_BASE_URL)
                .head() // Use HEAD request to check connectivity without downloading content
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "Server check failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "伺服器連線失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isServerConnected = false; // Update flag
                    updateServerIcon(false);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Server is alive. Status code: " + response.code());
                        Toast.makeText(MainActivity.this, "伺服器連線成功。", Toast.LENGTH_SHORT).show();
                        isServerConnected = true; // Update flag
                        updateServerIcon(true);
                    } else {
                        Log.e(TAG, "Server check failed with code: " + response.code());
                        Toast.makeText(MainActivity.this, "伺服器連線失敗，狀態碼: " + response.code(), Toast.LENGTH_LONG).show();
                        isServerConnected = false; // Update flag
                        updateServerIcon(false);
                    }
                    response.close(); // Close the response body
                });
            }
        });
    }

    private void makeApiCall() {
        String docType = etDocType.getText().toString();
        String docNumber = etDocNumber.getText().toString();
        String docItem = etDocItem.getText().toString();

        // Construct the URL with query parameters
        String url = API_ENDPOINT + "?DocType=" + docType + "&DocNumber=" + docNumber + "&DocItem=" + docItem;
        Log.d(TAG, "API URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        Toast.makeText(this, "正在獲取資料...", Toast.LENGTH_SHORT).show();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.e(TAG, "API call failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "獲取資料失敗: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "API Response: " + responseBody);
                        try {
                            JSONObject responseJson = new JSONObject(responseBody);
                            if ("200".equals(responseJson.getString("Code"))) {
                                JSONArray dataArray = new JSONArray(responseJson.getString("Data"));
                                if (dataArray.length() > 0) {
                                    fetchedData = dataArray.getJSONObject(0); // 取得第一筆資料並儲存
                                    populateUIWithData();
                                    Toast.makeText(MainActivity.this, "資料獲取成功", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "Fetched Data: " + fetchedData.toString()); // Debugging log
                                    hideKeyboard(); // Hide keyboard after successful data fetch
                                } else {
                                    Toast.makeText(MainActivity.this, "獲取資料成功，但無資料。", Toast.LENGTH_SHORT).show();
                                    Log.d(TAG, "No data returned from API.");
                                }
                            } else {
                                Toast.makeText(MainActivity.this, "獲取資料失敗: " + responseJson.getString("Message"), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "API returned error code: " + responseJson.getString("Code") + ", Message: " + responseJson.getString("Message"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "JSON parsing failed: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "資料解析失敗", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e(TAG, "API call failed with code: " + response.code() + ", Body: " + responseBody);
                        Toast.makeText(MainActivity.this, "獲取資料失敗，狀態碼: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // Helper method to hide the keyboard
    private void hideKeyboard() {
        View view = MainActivity.this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void updateServerIcon(boolean isConnected) {
        if (isConnected) {
            ivServerStatus.setColorFilter(Color.BLUE); // Set to blue for connected
        } else {
            ivServerStatus.setColorFilter(Color.parseColor("#808080")); // Set to grayscale for disconnected
        }
    }

    private void updateBluetoothIcon(boolean isConnected) {
        if (isConnected) {
            ivBluetoothStatus.setColorFilter(Color.BLUE); // Set to blue for connected
        } else {
            ivBluetoothStatus.setColorFilter(Color.parseColor("#808080")); // Set to grayscale for disconnected
        }
    }

    private void populateUIWithData() throws JSONException {
        // 填入固定資料
        tvVendor.setText("廠商: " + fetchedData.getString("供應廠商名稱"));
        tvPartName.setText("品名: " + fetchedData.getString("品名"));
        tvPartNo.setText("料號: " + fetchedData.getString("品號"));

        String dateStr = fetchedData.getString("進貨日期");
        String formattedDate = dateStr.substring(0, 4) + "/" + dateStr.substring(4, 6) + "/" + dateStr.substring(6, 8);
        tvDate.setText("日期: " + formattedDate);

        // Populate new display TextViews from fetchedData or fixed values
        tvDocTypeDisplay.setText("進貨單別: " + fetchedData.getString("進貨單別"));
        tvDocNumberDisplay.setText("進貨單號: " + fetchedData.getString("進貨單號"));
        tvDocItemDisplay.setText("進貨項次: " + fetchedData.getString("進貨項次(序號)"));
        tvVendorCode.setText("供應廠商代號: " + fetchedData.getString("供應廠商代號"));
        tvSpec.setText("規格: " + fetchedData.getString("規格"));
        // Extract month from dateStr (e.g., "20250611" -> "06")
        String month = dateStr.substring(4, 6);
        tvMonthDisplay.setText("月份: " + month);

        // Removed redundant display fields: tvTypeDisplay, tvItemNoDisplay, tvOrderNoDisplay
        // Removed lines for populating ImageViews


        // 填入可編輯欄位的預設值
        etQuantity.setText(String.valueOf(fetchedData.getInt("進貨數量")));
        etDC.setText(""); // Removed hardcoded value
        etHwVer.setText(""); // Removed hardcoded value
        etFwVer.setText(""); // Removed hardcoded value

        // 顯示預覽卡片和按鈕
        layoutStep1.setVisibility(View.GONE); // Hide Step 1
        cardLabelPreview.setVisibility(View.VISIBLE); // Show Step 2
        btnPreview.setVisibility(View.VISIBLE);
        btnBackToStep1.setVisibility(View.VISIBLE); // Show back button
    }

    private boolean validateInputFields() {
        String docType = etDocType.getText().toString().trim();
        String docNumber = etDocNumber.getText().toString().trim();
        String docItem = etDocItem.getText().toString().trim();

        if (docType.isEmpty()) {
            Toast.makeText(this, "請輸入進貨單別。", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (docNumber.isEmpty()) {
            Toast.makeText(this, "請輸入進貨單號。", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (docItem.isEmpty()) {
            Toast.makeText(this, "請輸入進貨項次。", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showPreviewDialog() {
        if (fetchedData == null) {
            Toast.makeText(this, "請先獲取資料。", Toast.LENGTH_SHORT).show();
            return;
        }

        // 建立並加載 Dialog 的 View
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_print_preview, null);

        // 找到 Dialog 中的 TextView
        TextView previewVendor = dialogView.findViewById(R.id.preview_tvVendor);
        TextView previewPartName = dialogView.findViewById(R.id.preview_tvPartName);
        TextView previewPartNo = dialogView.findViewById(R.id.preview_tvPartNo);
        TextView previewQuantity = dialogView.findViewById(R.id.preview_tvQuantity);
        TextView previewDC = dialogView.findViewById(R.id.preview_tvDC);
        TextView previewHwVer = dialogView.findViewById(R.id.preview_tvHwVer);
        TextView previewFwVer = dialogView.findViewById(R.id.preview_tvFwVer);
        TextView previewDate = dialogView.findViewById(R.id.preview_tvDate);
        TextView previewMonth = dialogView.findViewById(R.id.preview_tvMonth); // New field
        ImageView previewArborLogo = dialogView.findViewById(R.id.preview_ivArborLogo); // New field
        //ImageView previewQrCode = dialogView.findViewById(R.id.preview_ivQrCode); // New field
        TextView previewType = dialogView.findViewById(R.id.preview_tvType); // New field
        TextView previewItemNo = dialogView.findViewById(R.id.preview_tvItemNo); // New field
        TextView previewOrderNo = dialogView.findViewById(R.id.preview_tvOrderNo); // New field


        try {
            // 從 UI 上取得最終要列印的資料並填入
            previewVendor.setText(fetchedData.getString("供應廠商名稱"));
            previewPartName.setText(fetchedData.getString("品名"));
            previewPartNo.setText(fetchedData.getString("品號"));

            String dateStr = fetchedData.getString("進貨日期");
            String formattedDate = dateStr.substring(0, 4) + "/" + dateStr.substring(4, 6) + "/" + dateStr.substring(6, 8);
            previewDate.setText(formattedDate);

            // 讀取 EditText 中的現有值
            previewQuantity.setText(etQuantity.getText().toString());
            previewDC.setText(etDC.getText().toString());
            previewHwVer.setText(etHwVer.getText().toString());
            previewFwVer.setText(etFwVer.getText().toString());

            // Populate new fields from fetchedData or default values for preview dialog
            // Extract month from dateStr (e.g., "20250611" -> "06")
            previewMonth.setText(dateStr.substring(4, 6)); // Only display month value
            previewType.setText(fetchedData.getString("進貨單別")); // Only display DocType value
            previewItemNo.setText(fetchedData.getString("進貨項次(序號)")); // Only display DocItem value
            previewOrderNo.setText(fetchedData.getString("進貨單號")); // Only display DocNumber value

            // For Arbor Logo, it will show the placeholder drawable set in XML.
            previewArborLogo.setImageResource(R.drawable.arbor_logo_placeholder);

            // Generate QR Code from Part No (料號)
            /*
            String partNoForQr = fetchedData.getString("品號");
            try {
                com.google.zxing.MultiFormatWriter multiFormatWriter = new com.google.zxing.MultiFormatWriter();
                // Try a slightly smaller size for BitMatrix
                com.google.zxing.common.BitMatrix bitMatrix = multiFormatWriter.encode(partNoForQr, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300);
                com.journeyapps.barcodescanner.BarcodeEncoder barcodeEncoder = new com.journeyapps.barcodescanner.BarcodeEncoder(); // Create instance
                android.graphics.Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix); // Call on instance
                previewQrCode.setImageBitmap(bitmap);
            } catch (com.google.zxing.WriterException e) {
                Log.e(TAG, "QR Code generation failed: " + e.getMessage());
                previewQrCode.setImageResource(R.drawable.qr_code_placeholder); // Fallback to placeholder
            }
            */

        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "預覽資料解析失敗", Toast.LENGTH_LONG).show();
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
        if (fetchedData == null) {
            Toast.makeText(this, "請先獲取資料", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. 檢查列印機連線狀態
        if (!isPrinterConnected) {
            Toast.makeText(this, "列印機未連線，請先連線列印機", Toast.LENGTH_LONG).show();
            checkBluetoothAndPrinterStatus(); // 自動顯示列印機選擇對話框
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
        PrintData printData = new PrintData(fetchedData, quantity, dc, hwVer, fwVer);

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
}
