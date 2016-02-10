package com.microbean.smartband;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;


/**
 * Created by kenny.lee on 2016/2/9.
 */
public class MyService extends Service {
    private static final String TAG = "MyService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MyService onCreate() executed");
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
        startActivity(launchIntent);

    }
}
