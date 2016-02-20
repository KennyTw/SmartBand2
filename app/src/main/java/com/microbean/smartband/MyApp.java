package com.microbean.smartband;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

/**
 * Created by kennylee on 2016/2/20.
 */
public class MyApp extends Application {
    public void onCreate() {
        super.onCreate();
    }

    private Activity mCurrentActivity = null;
    public Activity getCurrentActivity(){
        return mCurrentActivity;
    }
    public void setCurrentActivity(Activity mCurrentActivity){
        this.mCurrentActivity = mCurrentActivity;
    }
}
