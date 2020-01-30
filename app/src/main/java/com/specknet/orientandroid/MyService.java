package com.specknet.orientandroid;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class MyService extends IntentService {

    /**
     * A constructor is required, and must call the super <code><a href="/reference/android/app/IntentService.html#IntentService(java.lang.String)">IntentService(String)</a></code>
     * constructor with a name for the worker thread.
     */
    public MyService() {
        super("MyService");
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
        while(true) {
            try {
                Thread.sleep(100);
                Log.i("MyService", "Hello");
            } catch (InterruptedException e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
        }
    }
}
