package com.microbean.smartband;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

/**
 * Created by kenny.lee on 2016/2/9.
 */
public class AlarmReceiver  extends BroadcastReceiver {
    static String savePkgName = null;
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bData = intent.getExtras();
        if(bData.get("msg").equals("StartLifeLog"))
        {

            ActivityManager ama = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> taskInfo = ama.getRunningTasks(1);

            savePkgName = taskInfo.get(0).topActivity.getPackageName();
            Log.i("topActivity", "CURRENT Activity ::" + taskInfo.get(0).topActivity.getClassName());

            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
            //launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            //com.sonymobile.lifelog.ui.TimelineActivity
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MILLISECOND, 1);

            Intent i = new Intent(context,AlarmReceiver.class);
            i.putExtra("msg", "BackActivity");

            PendingIntent pi = PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_ONE_SHOT);

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);

        } else if (bData.get("msg").equals("BackActivity")) {
            if (savePkgName != null) {

                if (!savePkgName.equals("com.microbean.smartband")) {

                    Intent launchIntent2 = context.getPackageManager().getLaunchIntentForPackage(savePkgName);
                    //launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (launchIntent2 != null) {
                        context.startActivity(launchIntent2);
                    } else {
                        Intent intenthome = new Intent(Intent.ACTION_MAIN);
                        intenthome.addCategory(Intent.CATEGORY_HOME);
                        intenthome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intenthome);
                    }
               }

                MainActivity.closeActivity();
            }
            Log.i("AlarmReceiver", "AlarmReceiver BackActivity , savePkgName : " + savePkgName);
        }

    }
}
