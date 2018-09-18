package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVWriter;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.reactivex.disposables.Disposable;

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    //private static final String ORIENT_BLE_ADDRESS = "C7:BA:D7:9D:F8:2E"; // test device
    private static String ORIENT_BLE_ADDRESS;

    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private int n = 0;
    private Long connected_timestamp = null;
    private Long capture_started_timestamp = null;
    boolean connected = false;

    private int counter = 0;
    private CSVWriter writer;
    private File path;
    private File file;
    private boolean logging = false;

    private Button start_button;
    private Button stop_button;
    private Context ctx;
    private TextView captureTimetextView;
    private TextView accelTextView;
    private TextView gyroTextView;

    private Spinner positionSpinner;
    private Spinner groupSpinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        path = Environment.getExternalStorageDirectory();

        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureTimetextView = findViewById(R.id.captureTimetextView);
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        positionSpinner = findViewById(R.id.positionSpinner);
        groupSpinner = findViewById(R.id.groupSpinner);

        positionSpinner.setOnItemSelectedListener(this);
        groupSpinner.setOnItemSelectedListener(this);

        List<String> position_list = new ArrayList<String>();
        position_list.add("POSITION");


        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, position_list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionSpinner.setAdapter(adapter);

        List<String> group_list = new ArrayList<String>();
        group_list.add("GROUP");
        for (char c = 'A'; c <= 'Z'; c++){
            group_list.add(String.valueOf(c));
        }

        ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, group_list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(adapter2);



        try {
            FileInputStream fis = new FileInputStream(path + "/" + "orient.ble");
            BufferedReader bfr = new BufferedReader(new InputStreamReader(fis));
            String ble_string = bfr.readLine();
            ORIENT_BLE_ADDRESS = ble_string;
        }
        catch (IOException e) {
            Log.e("MainActivity", "Error reading orient.ble file");
            Toast.makeText(this,"Error reading orient.ble file",
                    Toast.LENGTH_SHORT).show();
        }

        start_button.setOnClickListener(v-> {
            start_button.setEnabled(false);
            // make a new filename based on the start timestamp
            String file_ts = new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date());
            file = new File(path, "PdIoT_" + file_ts + ".csv");

            try {
                writer = new CSVWriter(new FileWriter(file), ',');
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }

            String[] entries = "timestamp#seq#accel_x#accel_y#accel_z#gyro_x#gyro_y#gyro_z".split("#");
            writer.writeNext(entries);
            try {
                writer.flush();
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }

            logging = true;
            capture_started_timestamp = System.currentTimeMillis();
            counter = 0;
            Toast.makeText(this,"Start logging",
                    Toast.LENGTH_SHORT).show();
            stop_button.setEnabled(true);
        });

        stop_button.setOnClickListener(v-> {
            logging = false;
            stop_button.setEnabled(false);
            try {
                writer.flush();
                writer.close();
                Toast.makeText(this,"Recording saved",
                        Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e("MainActivity", "Caught IOException: " + e.getMessage());
            }
            start_button.setEnabled(true);
        });

        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);

        scanSubscription = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.i("OrientAndroid", "FOUND: " + scanResult.getBleDevice().getName() + ", " +
                                    scanResult.getBleDevice().getMacAddress());
                            // Process scan result here.
                            if (scanResult.getBleDevice().getMacAddress().equals(ORIENT_BLE_ADDRESS)) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Found " + scanResult.getBleDevice().getName(),
                                            Toast.LENGTH_SHORT).show();
                                });
                                connectToOrient(ORIENT_BLE_ADDRESS);
                                scanSubscription.dispose();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            runOnUiThread(() -> {
                                Toast.makeText(ctx, "BLE scanning error",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                );

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        // TODO Auto-generated method stub
        Toast.makeText(this, "YOUR SELECTION IS : " + parent.getItemAtPosition(position).toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
        Toast.makeText(this, "YOUR SELECTION IS : " , Toast.LENGTH_SHORT).show();
    }

    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        orient_device.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            n += 1;
                            // Given characteristic has been changes, here is the value.

                            if (n % 25 == 0) {
                                Long elapsed_connection_time = System.currentTimeMillis() - connected_timestamp;
                                float connected_secs = elapsed_connection_time / 1000.f;
                                float freq = n / connected_secs;
                                Log.i("OrientAndroid", "Packet count: " + Integer.toString(n) + ", Freq: " + Float.toString(freq));
                            }

                            //Log.i("OrientAndroid", "Received " + bytes.length + " bytes");
                            if (!connected) {
                                connected = true;
                                connected_timestamp = System.currentTimeMillis();
                                runOnUiThread(() -> {
                                                               Toast.makeText(ctx, "Receiving sensor data",
                                                                       Toast.LENGTH_SHORT).show();
                                    start_button.setEnabled(true);
                                                           });
                            }
                            if (raw) handleRawPacket(bytes); else handleQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleQuatPacket(final byte[] bytes) {
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        int w = packetData.getInt();
        int x = packetData.getInt();
        int y = packetData.getInt();
        int z = packetData.getInt();

        double dw = w / 1073741824.0;  // 2^30
        double dx = x / 1073741824.0;
        double dy = y / 1073741824.0;
        double dz = z / 1073741824.0;

        Log.i("OrientAndroid", "QuatInt: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
        Log.i("OrientAndroid", "QuatDbl: (w=" + dw + ", x=" + dx + ", y=" + dy + ", z=" + dz + ")");
    }

    private void handleRawPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float accel_x = packetData.getShort() / 1024.f;  // integer part: 6 bits, fractional part 10 bits, so div by 2^10
        float accel_y = packetData.getShort() / 1024.f;
        float accel_z = packetData.getShort() / 1024.f;

        float gyro_x = packetData.getShort() / 32.f;  // integer part: 11 bits, fractional part 5 bits, so div by 2^5
        float gyro_y = packetData.getShort() / 32.f;
        float gyro_z = packetData.getShort() / 32.f;

        float mag_x = packetData.getShort() / 16.f;  // integer part: 12 bits, fractional part 4 bits, so div by 2^4
        float mag_y = packetData.getShort() / 16.f;
        float mag_z = packetData.getShort() / 16.f;

        //Log.i("OrientAndroid", "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")");
        //Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
        if (mag_x != 0f || mag_y != 0f || mag_z != 0f)
            Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");

        if (logging) {
            //String[] entries = "first#second#third".split("#");
            String[] entries = {Long.toString(ts),
                    Integer.toString(counter),
                    Float.toString(accel_x),
                    Float.toString(accel_y),
                    Float.toString(accel_z),
                    Float.toString(gyro_x),
                    Float.toString(gyro_y),
                    Float.toString(gyro_z),
            };
            writer.writeNext(entries);

            if (counter % 25 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int)elapsed_time / 1000;
                int s = total_secs % 60;
                int m = total_secs / 60;

                String m_str = Integer.toString(m);
                if (m_str.length() < 2) {
                    m_str = "0" + m_str;
                }

                String s_str = Integer.toString(s);
                if (s_str.length() < 2) {
                    s_str = "0" + s_str;
                }

                String time_str = m_str + ":" + s_str;

                String accel_str = "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")";
                String gyro_str = "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    gyroTextView.setText(gyro_str);
                });
            }

            counter += 1;
        }
    }


}
