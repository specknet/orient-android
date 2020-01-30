package com.specknet.orientandroid;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;

import static com.specknet.orientandroid.MainActivity.HOST_NAME;
import static com.specknet.orientandroid.MainActivity.sensorManager;


public class MyService extends IntentService implements SensorEventListener {

    private static final int UDP_PORT2 = 5556;


    private int port2;
    private DatagramSocket s2;
    private InetAddress local2;
    private DatagramPacket p2;
    private Sensor mSensor;
    private int ONGOING_NOTIFICATION_ID = 123789;

    /**
     * A constructor is required, and must call the super <code><a href="/reference/android/app/IntentService.html#IntentService(java.lang.String)">IntentService(String)</a></code>
     * constructor with a name for the worker thread.
     */
    public MyService() {
        super("MyService");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new Notification.Builder(this)
                        .setContentTitle(("Hello"))
                        .setContentText(("Message"))
                        .setSmallIcon(R.drawable.vec_wireless_active)
                        .setContentIntent(pendingIntent)
                        .setTicker(("Ticker"))
                        .build();

        Log.i("MyService", "Starting in foreground");

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        port2 = UDP_PORT2;

        try {
            s2 = new DatagramSocket();
            local2 = Inet4Address.getByName(HOST_NAME);
        } catch (Exception e) {
            Log.i("MainActivity", e.getMessage());
        }
    }

    /**
     * The IntentService calls this method from the default worker thread with
     * the intent that started the service. When this method returns, IntentService
     * stops the service, as appropriate.
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        // Normally we would do some work here, like download a file.
        // For our sample, we just sleep for 5 seconds.

        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL );

        while(true) {
            try {
                Thread.sleep(1000 * 60);
                Log.i("MyService", "Hello");
                sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL );
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
        }
    }

    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        float tablet_mag_x = event.values[0];
        float tablet_mag_y = event.values[1];
        float tablet_mag_z = event.values[2];

        float heading = tablet_mag_x;
        Log.i("MyService", "Heading: " + heading);

        String report2 = String.format("%.2f", heading);
        p2 = new DatagramPacket(report2.getBytes(), report2.length(), local2, port2);
        try {
            s2.send(p2);
        } catch (IOException e) {
            Log.i("MainActivity", "Exception " + e.getMessage());
        }


    }

    public final void onAccuracyChanged(Sensor s, int i) {

    }
}
