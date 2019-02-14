package com.orientgolf.shaz.orienttouchdesigner;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    // ID of Orient (respeck)
    private final static String ORIENT_UUID = "DA:24:DD:2C:68:D0";
    // Characteristic ID for quaternion data
    private final static String CHARACTERISTIC_QUAT = "000001526-1212-efde-1523-785feabcd125";

    // Constants to verify location and storage permissions
    private static final int REQUEST_CODE_LOCATION = 42;

    // For BLE
    private static RxBleClient rxBleClient;
    private Disposable scanSubscription;

    // For packet handling
    private static float divisor = (1 << 30);
    private ByteBuffer packetData;

    // For networking
    int port;
    DatagramSocket s;
    InetAddress local;
    int msg_length;
    byte[] message;
    DatagramPacket p;
    final String[] ip_name = new String[1];
    final String[] port_name = new String[1];

    // For displaying the quaternions within the app
    TextView infoview;
    String report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        StrictMode.ThreadPolicy policy = new
                StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // This will store each packet from the Orient
        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        infoview = (TextView)findViewById(R.id.infoview);

        final EditText ip_input = new EditText(this);
        final EditText port_input = new EditText(this);
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("IP Address")
                .setMessage("Input IP address of this machine")
                .setView(ip_input)
                .setPositiveButton("Enter", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ip_name[0] = ip_input.getText().toString();
                        getLocationPermission();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Port")
                .setMessage("Input port for data")
                .setView(port_input)
                .setPositiveButton("Enter", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        port_name[0] = port_input.getText().toString();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Next three methods are for getting location permissions

    private void getLocationPermission() {
        port = Integer.parseInt(port_name[0]);
        try {
            s = new DatagramSocket();
            local = Inet4Address.getByName(ip_name[0]);
        } catch (Exception e) {
            Log.i("MainActivity", e.getMessage());
        }

        int hasPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showMessageOKCancel("Location permissions are required to use this app.",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                                        REQUEST_CODE_LOCATION);
                            }
                        });
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_LOCATION);
            }
        } else {
            scanForOrient();
        }
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    scanForOrient();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Location permission denied", Toast.LENGTH_SHORT)
                            .show();
                    System.exit(1);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void scanForOrient() {

        // Scan available devices and try to find the Orient

        rxBleClient = RxBleClient.create(this);

        Log.i("MainActivity", "Scanning..");
        Toast.makeText(this, "Scanning for devices",
                Toast.LENGTH_SHORT).show();
        scanSubscription = rxBleClient.scanBleDevices(new ScanSettings.Builder()
                .build()
        )
                .subscribe(
                        scanResult -> {
                            Log.i("MainActivity", "FOUND :" + scanResult.getBleDevice().getName() + ", " + scanResult.getBleDevice().getMacAddress());
                            // Process scan result here.
                            if (scanResult.getBleDevice().getMacAddress().equalsIgnoreCase(ORIENT_UUID)) {
                                Toast.makeText(this, "Connecting to Orient",
                                        Toast.LENGTH_SHORT).show();
                                connectAndNotifyQuat();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.i("MainActivity", throwable.toString());
                            Toast.makeText(this, throwable.toString(),
                                    Toast.LENGTH_LONG).show();
                        }
                );
    }

    private void connectAndNotifyQuat() {
        // Start getting data from the Orient

        scanSubscription.dispose();

        rxBleClient.getBleDevice(ORIENT_UUID).establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(CHARACTERISTIC_QUAT)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                    Log.i("MainActivity", "Connected to Orient");
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            // Given characteristic has been changes, here is the value.
                            processQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.i("MainActivity", "DISCONNECTED: " + throwable.toString());
                        }
                );
    }

    void processQuatPacket(byte[] bytes) {
        // Process the quaternion data from the Orient

        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float w = packetData.getInt() / divisor;
        float x = packetData.getInt() / divisor;
        float y = packetData.getInt() / divisor;
        float z = packetData.getInt() / divisor;

        report = String.format("Quaternions: %.2f, %.2f, %.2f, %.2f", w, x, y, z);

        msg_length = report.length();
        message = report.getBytes();
        p = new DatagramPacket(message, msg_length, local, port);
        try {
            s.send(p);
        } catch (IOException e){
            Log.i("MainActivity", "Exception " + e.getMessage());
        }

        Log.i("MainActivity", report);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                infoview.setText(report);
            }
        });
    }
}
