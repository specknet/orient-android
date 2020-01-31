package com.specknet.orientandroid;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Service which sends android orientation sensor data to Unity via UDP
 */

public class MyService2 extends Service  {


    // Just in case there could be a conflict with another notification, we give it a high "random" integer
    private final int SERVICE_NOTIFICATION_ID = 9148519;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread() {
            @Override
            public void run() {
                Log.i("MyService2", "Starting Sensor service...");

                Intent notificationIntent = new Intent(MyService2.this, MainActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(MyService2.this, 0, notificationIntent, 0);

                Notification notification = new Notification.Builder(MyService2.this)
                        .setContentTitle("Hello")
                        .setContentText("Hello2")
                        .setSmallIcon(R.drawable.vec_wireless_active)
                        .setContentIntent(pendingIntent)
                        .build();

                startForeground(SERVICE_NOTIFICATION_ID, notification);
            }
        }.start();
        return START_STICKY;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
