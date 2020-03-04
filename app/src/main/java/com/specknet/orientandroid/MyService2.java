package com.specknet.orientandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;

import static com.specknet.orientandroid.MainActivity.HOST_NAME;
import static com.specknet.orientandroid.MainActivity.UDP_PORT;
import static com.specknet.orientandroid.MainActivity.sensorManager;

/**
 * Service which sends android orientation sensor data to Unity via UDP
 */

public class MyService2 extends Service implements SensorEventListener {
    private static final int UDP_PORT2 = 5556;

    private int port2;
    private DatagramSocket s2;
    private InetAddress local2;
    private DatagramPacket p2;
    private Sensor mSensor;

    private int msg_length;
    private byte[] message;
    private DatagramPacket p;

    private int port;
    private InetAddress local;
    private DatagramSocket s;

    // Just in case there could be a conflict with another notification, we give it a high "random" integer
    private final int SERVICE_NOTIFICATION_ID = 9148519;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startMyOwnForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread() {
            @Override
            public void run() {
                Log.i("MyService2", "Starting Sensor service...");
                setupSensors();

                /*
                //String channel = createChannel();

                Intent notificationIntent = new Intent(MyService2.this, MainActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(MyService2.this, 0, notificationIntent, 0);

                Notification notification = new Notification.Builder(MyService2.this)
                        .setContentTitle("Hello")
                        .setContentText("Hello2")
                        .setSmallIcon(R.drawable.vec_wireless_active)
                        .setContentIntent(pendingIntent)
                        .build();

                startForeground(SERVICE_NOTIFICATION_ID, notification);
            */
            }
        }.start();
        return START_NOT_STICKY;
    }

    private void setupSensors(){
        mSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        port2 = UDP_PORT2;

        try {
            s2 = new DatagramSocket();
            local2 = Inet4Address.getByName(HOST_NAME);
        } catch (Exception e) {
            Log.i("MyService2", e.getMessage());
        }

        port = UDP_PORT;

        try {
            s = new DatagramSocket();
            local = Inet4Address.getByName(HOST_NAME);
        } catch (Exception e) {
            Log.i("MainActivity", e.getMessage());
        }

        sensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME );

        while(true) {
            try {
                Log.i("MyService2", "qn: " + MainActivity.queue.size());
                if (MainActivity.queue.size() > 25) {
                    String report = (String)MainActivity.queue.poll();
                    msg_length = report.length();
                    message = report.getBytes();
                    p = new DatagramPacket(message, msg_length, local, port);
                    try {
                        s.send(p);
                    } catch (IOException e) {
                        Log.i("MainActivity", "Exception " + e.getMessage());
                    }
                }

                if (MainActivity.queue.size() > 1)
                    Thread.sleep(1000/(MainActivity.queue.size()/2));
                else
                    Thread.sleep(1000/25);

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
        Log.i("MyService2", "Heading: " + heading);

        float pitch = tablet_mag_z;
        if (tablet_mag_y > 90.f || tablet_mag_y < -90.f) {
            pitch = pitch - 90.f;
        }
        else {
            pitch = -pitch + 90.f;
        }
        Log.i("MyService2", "pitch: " + pitch);
        Log.i("MyService2", "y: " + tablet_mag_y);


        String report2 = String.format("%.2f,%.2f", heading, pitch);
        p2 = new DatagramPacket(report2.getBytes(), report2.length(), local2, port2);
        try {
            s2.send(p2);
        } catch (IOException e) {
            Log.i("MyService2", "Exception " + e.getMessage());
        }


    }

    public final void onAccuracyChanged(Sensor s, int i) {

    }


    private void startMyOwnForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.specknet.orientandroid";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.vec_wireless_active)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
