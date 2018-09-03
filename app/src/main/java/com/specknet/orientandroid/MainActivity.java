package com.specknet.orientandroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import io.reactivex.disposables.Disposable;

public class MainActivity extends Activity {

    private static final String ORIENT_BLE_ADDRESS = "C7:BA:D7:9D:F8:2E";
    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";
    private static final boolean raw = false;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                                connectToOrient(ORIENT_BLE_ADDRESS);
                                scanSubscription.dispose();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                        }
                );

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
                            // Given characteristic has been changes, here is the value.
                            Log.i("OrientAndroid", "Received " + bytes.length + " bytes");
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

        Log.i("OrientAndroid", "Quat: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
    }

    private void handleRawPacket(final byte[] bytes) {
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float accel_x = packetData.getShort() / 1024.f;
        float accel_y = packetData.getShort() / 1024.f;
        float accel_z = packetData.getShort() / 1024.f;

        int gyro_x = packetData.getShort();
        int gyro_y = packetData.getShort();
        int gyro_z = packetData.getShort();

        int mag_x = packetData.getShort();
        int mag_y = packetData.getShort();
        int mag_z = packetData.getShort();

        Log.i("OrientAndroid", "Accel:(" + accel_x + ", " + accel_y + ", " + accel_z + ")");
        //Log.i("OrientAndroid", "Gyro:(" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")");
        //Log.i("OrientAndroid", "Mag:(" + mag_x + ", " + mag_y + ", " + mag_z + ")");
    }
}
