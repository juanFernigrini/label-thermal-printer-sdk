package com.printer.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.dothantech.lpapi.LPAPI;
import com.dothantech.lpapi.LPAPI.BarcodeType;
import com.dothantech.printer.IDzPrinter;
import com.dothantech.printer.IDzPrinter.PrintParamName;
import com.dothantech.printer.IDzPrinter.PrintProgress;
import com.dothantech.printer.IDzPrinter.PrinterAddress;
import com.dothantech.printer.IDzPrinter.PrinterInfo;
import com.dothantech.printer.IDzPrinter.PrinterState;
import com.dothantech.printer.IDzPrinter.ProgressInfo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


@SuppressLint("InflateParams")
public class MainActivity extends Activity {
/********************************************************************************************************************************************/
// Functions related to the connection and printing of DzPrinter
    /********************************************************************************************************************************************/

// Callback functions related to the operation of the LPAPI printer.
    private final LPAPI.Callback mCallback = new LPAPI.Callback() {

        /****************************************************************************************************************************************/
        // All callback functions are called within the printing thread, so if it's necessary to update the UI,
        // a message should be sent to the main UI thread to avoid complicated operations like mutual exclusion.
        /****************************************************************************************************************************************/

        // Called when the printer connection status changes
        @Override
        public void onStateChange(PrinterAddress arg0, PrinterState arg1) {
            final PrinterAddress printer = arg0;
            switch (arg1) {
                case Connected:
                case Connected2:
                    // Successful printer connection, send notification and update the UI
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onPrinterConnected(printer);
                        }
                    });
                    break;

                case Disconnected:
                    // Printer connection failed or disconnected, send notification and update the UI
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onPrinterDisconnected();
                        }
                    });
                    break;

                default:
                    break;
            }
        }

        // Called when the Bluetooth adapter status changes
        @Override
        public void onProgressInfo(ProgressInfo arg0, Object arg1) {
        }

        @Override
        public void onPrinterDiscovery(PrinterAddress arg0, PrinterInfo arg1) {
        }

        // Called when the label print progress changes
        @Override
        public void onPrintProgress(PrinterAddress address, Object bitmapData, PrintProgress progress, Object addiInfo) {
            switch (progress) {
                case Success:
                    // Successful label printing, send notification and update the UI
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onPrintSuccess();
                        }
                    });
                    break;

                case Failed:
                    // Failed label printing, send notification and update the UI
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onPrintFailed();
                        }
                    });
                    break;

                default:
                    break;
            }
        }
    };

    private LPAPI api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize the interface
        initialView();

        // Call the init method of the LPAPI object to initialize it
        this.api = LPAPI.Factory.createInstance(mCallback);

        // Try to connect to the printer that was previously connected successfully
        if (mPrinterAddress != null) {
            if (api.openPrinterByAddress(mPrinterAddress)) {
                // Printer connection request sent successfully, update the UI
                onPrinterConnecting(mPrinterAddress, false);
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        // When exiting the application, call the quit method of the LPAPI object to disconnect the printer
        api.quit();

        // Necessary operations when exiting the application
        fini();

        super.onDestroy();
    }

    // Click event for each item in the printer list
    private class DeviceListItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            PrinterAddress printer = pairedPrinters.get(which);
            if (printer != null) {
                // Connect the selected printer
                if (api.openPrinterByAddress(printer)) {
                    // Printer connection request sent successfully, update the UI
                    onPrinterConnecting(printer, true);
                    return;
                }
            }

            // Printer connection failed, update the UI
            onPrinterDisconnected();
        }
    }

    // Check if the printer is connected
    private boolean isPrinterConnected() {
        // Call the getPrinterState method of the LPAPI object to get the printer's state
        PrinterState state = api.getPrinterState();

        // Printer not connected
        if (state == null || state.equals(PrinterState.Disconnected)) {
            Toast.makeText(MainActivity.this, this.getResources().getString(R.string.pleaseconnectprinter), Toast.LENGTH_SHORT).show();
            return false;
        }

        // Printer connecting
        if (state.equals(PrinterState.Connecting)) {
            Toast.makeText(MainActivity.this, this.getResources().getString(R.string.waitconnectingprinter), Toast.LENGTH_SHORT).show();
            return false;
        }

        // Printer connected
        return true;
    }

    // Get the necessary parameters for printing
    private Bundle getPrintParam(int copies, int orientation) {
        Bundle param = new Bundle();

        // Print density
        if (printDensity >= 0) {
            param.putInt(PrintParamName.PRINT_DENSITY, printDensity);
        }

        // Print speed
        if (printSpeed >= 0) {
            param.putInt(PrintParamName.PRINT_SPEED, printSpeed);
        }

        // Gap type
        if (gapType >= 0) {
            param.putInt(PrintParamName.GAP_TYPE, gapType);
        }

        // Print page rotation angle
        if (orientation != 0) {
            param.putInt(PrintParamName.PRINT_DIRECTION, orientation);
        }

        // Number of copies
        if (copies > 1) {
            param.putInt(PrintParamName.PRINT_COPIES, copies);
        }

        return param;
    }

/********************************************************************************************************************************************/
// Functions related to graphic printing using LPAPI
    /********************************************************************************************************************************************/

// Print text
    private boolean printText(String text, Bundle param) {
        // Start the graphic printing job, passing the parameters (page width, page height)
        api.startJob(40, 30, 0);

        // Start printing the page, draw the text
        api.drawText(text, 4, 5, 40, 40, 4);

        // Finish the printing task and commit the print job
        return api.commitJob();
    }

    // Print text with 1D barcode
    private boolean printText1DBarcode(String text, String onedBarcde, Bundle param) {
        // Start the graphic printing job, passing the parameters (page width, page height)
        api.startJob(40, 30, 0);

        // Start printing the page, draw the text
        api.drawText(text, 4, 4, 40, 10, 4);

        // Set the content orientation to 180 degrees
        api.setItemOrientation(0);

        // Draw the 1D barcode, this is drawn with a 180-degree rotation
        api.draw1DBarcode(onedBarcde, BarcodeType.CODE128, 4, 15, 40, 15, 3);

        // Finish the printing task and commit the print job
        return api.commitJob();
    }

    // Print 2D barcode
    private boolean print2dBarcode(String twodBarcode, Bundle param) {
        // Start the graphic printing job, passing the parameters (page width, page height)
        api.startJob(48, 50, 0);

        // Start printing the page, draw the QR code
        api.draw2DQRCode(twodBarcode, 9, 10, 30);

        // Finish the printing task and commit the print job
        return api.commitJob();
    }

    // Print image
    private boolean printBitmap(Bitmap bitmap, Bundle param) {
        // Print the image
        return api.printBitmap(bitmap, param);
    }

/********************************************************************************************************************************************/
// User interface related functions

    /********************************************************************************************************************************************/

// Initialize the interface
    private void initialView() {
        printQualityList = getResources().getStringArray(R.array.print_quality);
        printDensityList = getResources().getStringArray(R.array.print_density);
        printSpeedList = getResources().getStringArray(R.array.print_speed);
        gapTypeList = getResources().getStringArray(R.array.gap_type);
        btnConnectDevice = (Button) findViewById(R.id.btn_printer);
        btnPrintQuality = (Button) findViewById(R.id.btn_printquality);
        btnGapType = (Button) findViewById(R.id.btn_gaptype);
        btnPrintDensity = (Button) findViewById(R.id.btn_printdensity);
        btnPrintSpeed = (Button) findViewById(R.id.btn_printspeed);
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.app_name), Context.MODE_PRIVATE);
        String lastPrinterMac = sharedPreferences.getString(KeyLastPrinterMac, null);
        String lastPrinterName = sharedPreferences.getString(KeyLastPrinterName, null);
        String lastPrinterType = sharedPreferences.getString(KeyLastPrinterType, null);
        IDzPrinter.AddressType lastAddressType = TextUtils.isEmpty(lastPrinterType) ? null : Enum.valueOf(IDzPrinter.AddressType.class, lastPrinterType);
        if (lastPrinterMac == null || lastPrinterName == null || lastAddressType == null) {
            mPrinterAddress = null;
        } else {
            mPrinterAddress = new IDzPrinter.PrinterAddress(lastPrinterName, lastPrinterMac, lastAddressType);
        }
        printQuality = sharedPreferences.getInt(KeyPrintQuality, -1);
        printDensity = sharedPreferences.getInt(KeyPrintDensity, -1);
        printSpeed = sharedPreferences.getInt(KeyPrintSpeed, -1);
        gapType = sharedPreferences.getInt(KeyGapType, -1);
        defaultText1 = sharedPreferences.getString(KeyDefaultText1, getResources().getString(R.string.defaulttextone));
        defaultText2 = sharedPreferences.getString(KeyDefaultText2, getResources().getString(R.string.defaulttexttwo));
        default1dBarcode = sharedPreferences.getString(KeyDefault1dBarcode, getResources().getString(R.string.defaultonedbarcode));
        default2dBarcode = sharedPreferences.getString(KeyDefault2dBarcode, getResources().getString(R.string.defaulttwodbarcode));
        btnPrintDensity.setText(getResources().getString(R.string.printdensity) + printDensityList[printDensity + 1]);
        btnPrintQuality.setText(getResources().getString(R.string.printquality) + printQualityList[printQuality + 1]);
        btnPrintSpeed.setText(getResources().getString(R.string.printspeed) + printSpeedList[printSpeed + 1]);
        btnGapType.setText(getResources().getString(R.string.gaptype) + gapTypeList[gapType + 1]);

        String[] testPicName = getResources().getStringArray(R.array.test_pic_name);
        bitmapOrientations = getResources().getIntArray(R.array.test_pic_orientation);

        // Load test images
        if (testPicName != null) {
            for (String str : testPicName) {
                try {
                    InputStream is = getAssets().open(str);
                    if (is != null) {
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        if (bmp != null) {
                            printBitmaps.add(bmp);
                        }
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    // Operations needed when closing the application
    private void fini() {
        // Save relevant information
        SharedPreferences sharedPreferences = getSharedPreferences(getResources().getString(R.string.app_name), Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();

        editor.putInt(KeyPrintQuality, printQuality);
        editor.putInt(KeyPrintDensity, printDensity);
        editor.putInt(KeyPrintSpeed, printSpeed);
        editor.putInt(KeyGapType, gapType);
        if (mPrinterAddress != null) {
            editor.putString(KeyLastPrinterMac, mPrinterAddress.macAddress);
            editor.putString(KeyLastPrinterName, mPrinterAddress.shownName);
            editor.putString(KeyLastPrinterType, mPrinterAddress.addressType.toString());
        }
        if (defaultText1 != null) {
            editor.putString(KeyDefaultText1, defaultText1);
        }
        if (defaultText2 != null) {
            editor.putString(KeyDefaultText2, defaultText2);
        }
        if (default1dBarcode != null) {
            editor.putString(KeyDefault1dBarcode, default1dBarcode);
        }
        if (default2dBarcode != null) {
            editor.putString(KeyDefault2dBarcode, default2dBarcode);
        }
        editor.commit();
    }

    // Printer selection button event
    public void selectPrinterOnClick(View view) {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(MainActivity.this, this.getResources().getString(R.string.unsupportedbluetooth), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!btAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, this.getResources().getString(R.string.unenablebluetooth), Toast.LENGTH_SHORT).show();
            return;
        }

        pairedPrinters = api.getAllPrinterAddresses(null);
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.selectbondeddevice).setAdapter(new DeviceListAdapter(), new DeviceListItemClicker()).show();
    }

    // Set print quality button event
    public void printQualityOnClick(View view) {
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.setprintquality).setAdapter(new ParamAdapter(printQualityList), new PrintQualityItemClicker()).show();
    }

    // Set gap type button event
    public void gapTypeOnClick(View view) {
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.setgaptype).setAdapter(new ParamAdapter(gapTypeList), new GapTypeItemClicker()).show();
    }

    // Set print density button event
    public void printDensityOnClick(View view) {
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.setprintdensity).setAdapter(new ParamAdapter(printDensityList), new PrintDensityItemClicker()).show();
    }

    // Set print speed button event
    public void printSpeedOnClick(View view) {
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.setprintspeed).setAdapter(new ParamAdapter(printSpeedList), new PrintSpeedItemClicker()).show();
    }

    // Print text button event
    public void printTextOnClick(View view) {
        // Show print data settings interface
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.printtext);
        builder.setView(initView(R.string.textvalue, defaultText1));
        builder.setPositiveButton(R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Get print data and print
                defaultText1 = et1.getText().toString();
                if (isPrinterConnected()) {
                    if (printText(defaultText1, getPrintParam(1, 0))) {
                        onPrintStart();
                    } else {
                        onPrintFailed();
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    // Print text 1D barcode button event
    public void printText1DBarcodeOnClick(View view) {
        // Show print data settings interface
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.printtext1dbarcode);
        builder.setView(initView(R.string.textvalue, defaultText2, R.string.onedbarcodevalue, default1dBarcode));
        builder.setPositiveButton(R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Get print data and print
                defaultText2 = et1.getText().toString();
                default1dBarcode = et2.getText().toString();
                if (isPrinterConnected()) {
                    if (printText1DBarcode(defaultText2, default1dBarcode, getPrintParam(1, 90))) {
                        onPrintStart();
                    } else {
                        onPrintFailed();
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    // Print 2D barcode button event
    public void print2DBarcodeOnClick(View view) {
        // Show print data settings interface
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(R.string.print2dbarcode);
        builder.setView(initView(R.string.twodbarcodevalue, default2dBarcode));
        builder.setPositiveButton(R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Get print data and print
                default2dBarcode = et1.getText().toString();
                if (isPrinterConnected()) {
                    if (print2dBarcode(default2dBarcode, getPrintParam(1, 0))) {
                        onPrintStart();
                    } else {
                        onPrintFailed();
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    // Print bitmap button event
    public void printBitmapOnClick(View view) {
        new AlertDialog.Builder(MainActivity.this).setTitle(R.string.printbitmap).setAdapter(new BitmapListAdapter(), new BitmapListItemClicker()).show();
    }

    // Printer connection request successful operation
    private void onPrinterConnecting(PrinterAddress printer, boolean showDialog) {
        // Printer connection request successful, refresh interface prompt
        String txt = printer.shownName;
        if (TextUtils.isEmpty(txt)) txt = printer.macAddress;
        txt = getResources().getString(R.string.nowisconnectingprinter) + '[' + txt + ']';
        txt += getResources().getString(R.string.printer);
        if (showDialog) {
            showStateAlertDialog(txt);
        }
        btnConnectDevice.setText(txt);
    }

    // Printer connection successful operation
    private void onPrinterConnected(PrinterAddress printer) {
        // Printer connection successful, refresh interface prompt, save related information
        clearAlertDialog();
        Toast.makeText(MainActivity.this, this.getResources().getString(R.string.connectprintersuccess), Toast.LENGTH_SHORT).show();
        mPrinterAddress = printer;
        // Call the LPAPI object's getPrinterInfo method to get the current connected printer's information
        String txt = getResources().getString(R.string.printer) + getResources().getString(R.string.chinesecolon);
        txt += api.getPrinterInfo().deviceName + "\n";
        txt += api.getPrinterInfo().deviceAddress;
        btnConnectDevice.setText(txt);
    }

    // Printer connection failed or disconnected operation
    private void onPrinterDisconnected() {
        // Printer connection failed or disconnected, refresh interface prompt
        clearAlertDialog();

        Toast.makeText(MainActivity.this, this.getResources().getString(R.string.connectprinterfailed), Toast.LENGTH_SHORT).show();
        btnConnectDevice.setText("");
    }

    // Start printing label operation
    private void onPrintStart() {
        // Start printing label, refresh interface prompt
        showStateAlertDialog(R.string.nowisprinting);
    }

    // Label printing successful operation
    private void onPrintSuccess() {
        // Label printing successful, refresh interface prompt
        clearAlertDialog();
        Toast.makeText(MainActivity.this, this.getResources().getString(R.string.printsuccess), Toast.LENGTH_SHORT).show();
    }

    // Print request failed or label printing failed operation
    private void onPrintFailed() {
        // Print request failed or label printing failed, refresh interface prompt
        clearAlertDialog();
        Toast.makeText(MainActivity.this, this.getResources().getString(R.string.printfailed), Toast.LENGTH_SHORT).show();
    }

    // Show connection and printing status dialog
    private void showStateAlertDialog(int resId) {
        showStateAlertDialog(getResources().getString(resId));
    }

    // Show connection and printing status dialog
    private void showStateAlertDialog(String str) {
        if (stateAlertDialog != null && stateAlertDialog.isShowing()) {
            stateAlertDialog.setTitle(str);
        } else {
            stateAlertDialog = new AlertDialog.Builder(MainActivity.this).setCancelable(false).setTitle(str).show();
        }
    }

    // Clear connection and printing status dialog
    private void clearAlertDialog() {
        if (stateAlertDialog != null && stateAlertDialog.isShowing()) {
            stateAlertDialog.dismiss();
        }
        stateAlertDialog = null;
    }

    // Adapter for filling the printer list
    private class DeviceListAdapter extends BaseAdapter {
        private TextView tv_name = null;
        private TextView tv_mac = null;

        @Override
        public int getCount() {
            return pairedPrinters.size();
        }

        @Override
        public Object getItem(int position) {
            return pairedPrinters.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.printer_item, null);
            }
            tv_name = (TextView) convertView.findViewById(R.id.tv_device_name);
            tv_mac = (TextView) convertView.findViewById(R.id.tv_macaddress);

            if (pairedPrinters != null && pairedPrinters.size() > position) {
                PrinterAddress printer = pairedPrinters.get(position);
                tv_name.setText(printer.shownName);
                tv_mac.setText(printer.macAddress);
            }

            return convertView;
        }
    }
    // Adapter used to fill the print parameter configuration interface
    private class ParamAdapter extends BaseAdapter {
        private TextView tv_param = null;
        private String[] paramArray = null;

        public ParamAdapter(String[] array) {
            this.paramArray = array;
        }

        @Override
        public int getCount() {
            return paramArray.length;
        }

        @Override
        public Object getItem(int position) {
            return paramArray[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.param_item, null);
            }
            tv_param = (TextView) convertView.findViewById(R.id.tv_param);
            String text = "";
            if (paramArray != null && paramArray.length > position) {
                text = paramArray[position];
            }
            tv_param.setText(text);

            return convertView;
        }
    }

    // Adapter used to fill the list of sample print images
    private class BitmapListAdapter extends BaseAdapter {
        private ImageView iv_bmp = null;

        @Override
        public int getCount() {
            return printBitmaps.size();
        }

        @Override
        public Object getItem(int position) {
            return printBitmaps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.bitmap_item, null);
            }
            iv_bmp = (ImageView) convertView.findViewById(R.id.iv_bmp);
            if (printBitmaps != null && printBitmaps.size() > position) {
                Bitmap bmp = printBitmaps.get(position);
                if (bmp != null) {
                    iv_bmp.setImageBitmap(bmp);
                }
            }

            return convertView;
        }
    }

    // Click event for each print quality option
    private class PrintQualityItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            printQuality = which - 1;
            btnPrintQuality.setText(getResources().getString(R.string.printquality) + printQualityList[which]);
        }
    }

    // Click event for each print density option
    private class PrintDensityItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            printDensity = which - 1;
            btnPrintDensity.setText(getResources().getString(R.string.printdensity) + printDensityList[which]);
        }
    }

    // Click event for each print speed option
    private class PrintSpeedItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            printSpeed = which - 1;
            btnPrintSpeed.setText(getResources().getString(R.string.printspeed) + printSpeedList[which]);
        }
    }

    // Click event for each gap type option
    private class GapTypeItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            gapType = which - 1;
            btnGapType.setText(getResources().getString(R.string.gaptype) + gapTypeList[which]);
        }
    }

    // Click event for each item in the sample print image list
    private class BitmapListItemClicker implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (isPrinterConnected()) {
                int orientation = 0;
                if (bitmapOrientations != null && bitmapOrientations.length > which) {
                    orientation = bitmapOrientations[which];
                }

                // Get the print data and perform the print
                Bitmap bmp = printBitmaps.get(which);
                if (bmp != null) {
                    if (printBitmap(bmp, getPrintParam(1, orientation))) {
                        onPrintStart();
                        return;
                    }
                }

                onPrintFailed();
            }
        }
    }

    // Initializes and gets the view to configure a print data (single data)
    private View initView(int title1, String text1) {
        View view = View.inflate(MainActivity.this, R.layout.setvalue_item, null);
        ((TextView) view.findViewById(R.id.tv_title1)).setText(title1);
        et1 = (EditText) view.findViewById(R.id.et_value1);
        et1.setText(text1 == null ? "" : text1);
        et1.setSelection(et1.getText().toString().length());
        return view;
    }

    // Initializes and gets the view to configure print data (two data)
    private View initView(int title1, String text1, int title2, String text2) {
        View view = View.inflate(MainActivity.this, R.layout.setvalue_item, null);
        ((LinearLayout) view.findViewById(R.id.ll_2)).setVisibility(View.VISIBLE);
        ((TextView) view.findViewById(R.id.tv_title1)).setText(title1);
        et1 = (EditText) view.findViewById(R.id.et_value1);
        et1.setText(text1 == null ? "" : text1);
        et1.setSelection(et1.getText().length());
        ((TextView) view.findViewById(R.id.tv_title2)).setText(title2);
        et2 = (EditText) view.findViewById(R.id.et_value2);
        et2.setText(text2 == null ? "" : text2);
        et2.setSelection(et2.getText().toString().length());
        return view;
    }

    // Handler to manage various notification messages and update the interface
    private final Handler mHandler = new Handler();

    // Key names to save various configurations
    private static final String KeyPrintQuality = "PrintQuality";
    private static final String KeyPrintDensity = "PrintDensity";
    private static final String KeyPrintSpeed = "PrintSpeed";
    private static final String KeyGapType = "GapType";

    private static final String KeyLastPrinterMac = "LastPrinterMac";
    private static final String KeyLastPrinterName = "LastPrinterName";
    private static final String KeyLastPrinterType = "LastPrinterType";

    private static final String KeyDefaultText1 = "DefaultText1";
    private static final String KeyDefaultText2 = "DefaultText2";
    private static final String KeyDefault1dBarcode = "Default1dBarcode";
    private static final String KeyDefault2dBarcode = "Default2dBarcode";

    // Declaration of various interface control objects
    private Button btnConnectDevice = null;
    private Button btnPrintQuality = null;
    private Button btnPrintDensity = null;
    private Button btnPrintSpeed = null;
    private Button btnGapType = null;
    private EditText et1 = null;
    private EditText et2 = null;

    // Print parameters
    private int printQuality = -1;
    private int printDensity = -1;
    private int printSpeed = -1;
    private int gapType = -1;

    // Print data
    private String defaultText1 = "";
    private String defaultText2 = "";
    private String default1dBarcode = "";
    private String default2dBarcode = "";

    // Arrays and lists used to fill the configuration data
    private String[] printQualityList = null;
    private String[] printDensityList = null;
    private String[] printSpeedList = null;
    private String[] gapTypeList = null;

    private List<PrinterAddress> pairedPrinters = new ArrayList<PrinterAddress>();

    private List<Bitmap> printBitmaps = new ArrayList<Bitmap>();
    private int[] bitmapOrientations = null;

    // Last connected printer
    private PrinterAddress mPrinterAddress = null;

    // Dialog to show the state
    private AlertDialog stateAlertDialog = null;

}
