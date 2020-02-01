package com.specknet.orientandroid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
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
        return START_STICKY;
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
        startForeground(2, notification);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }
}
