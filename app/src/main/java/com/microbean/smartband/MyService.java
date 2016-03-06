package com.microbean.smartband;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.BleDevice;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.BleScanCallback;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.StartBleScanRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.fitness.result.ListSubscriptionsResult;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;


/**
 * Created by kenny.lee on 2016/2/9.
 */
public class MyService extends Service  implements TextToSpeech.OnInitListener {
    private static final String TAG = "MyService";
    public boolean bcreated = false;
    public boolean terminated = false;
    static TextToSpeech tts = null;
    private AlarmManager alarmManager;
    private GoogleApiClient mClient = null;
    private boolean authInProgress = false;
    private static final int REQUEST_OAUTH = 1;
    private OnDataPointListener mListener;
    private BluetoothAdapter mBluetoothAdapter;
    private static final long SCAN_PERIOD = 120000;
    private Handler mHandler;
    private boolean mScanning;

    PowerManager.WakeLock screenLock;
    KeyguardManager.KeyguardLock keylock;
    WindowManager windowManager;
    LayoutInflater inflater;


    private float lastBeat = 0;
    private float prevBeat = 0;
    private float saveBeat = 0;
    private String lasttimestr = "";
    private String savetimestr = "";
    private List<Long> HRV = new ArrayList<Long>();
    private List<Long> HRVData = new ArrayList<Long>();
    private String largeTime = "";
    private float largeBeat = 0;
    private int lastStep = 0;
    private int saveStep = 0;
    private String laststeptimestr = "";
    private String savesteptimestr = "";
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MyService onCreate() executed");

        screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "TAG");



       // screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
       //         PowerManager.FULL_WAKE_LOCK, "TAG");

        KeyguardManager keyguardManager = (KeyguardManager)getSystemService(Activity.KEYGUARD_SERVICE);
        keylock = keyguardManager.newKeyguardLock(KEYGUARD_SERVICE);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        //Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
        //startActivity(launchIntent);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        mHandler = new Handler();
        tts = new TextToSpeech(this, this);
    }

    BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SCREEN_ON)) {
                keylock.reenableKeyguard();
                Log.i(TAG, "ACTION_SCREEN_ON reenableKeyguard");
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {

            }
        }
    };

    @Override
    public void onDestroy() {
        Log.e(TAG, "start onDestroy");
        super.onDestroy();

        if(tts != null) {
            tts.stop();
            tts.shutdown();
            Log.i(TAG, "TTS Destroyed");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        if (intent == null)
            return START_STICKY;

        String command = intent.getStringExtra("command");
        int value = intent.getIntExtra("value", -1);
        String mtype = intent.getStringExtra("type");
        if (mtype == null) {
            mtype = "";
        }
        final String type = mtype;


        if (command.equals("setCurrentActivity") ) {

            if (bcreated == false) {
                bcreated = true;
                new Thread() {
                    public void run() {

                        while (!terminated) {

                            //wait tts initial
                            while (tts == null) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }


                            writeMsg(null);
                            if (mClient != null)
                                mClient.disconnect();

                            HRV.clear();

                            //
                            buildFitnessClient("");

                            try {
                                Thread.sleep(1000 * 60 * 5);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }.start();
            } else  {
                writeMsg(null);
                if (mClient != null)
                    mClient.disconnect();
                HRV.clear();
                //
                buildFitnessClient(type);
            }
        } else if (command.equals("onActivityResult")) {
            if (value == -1) {
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                    Log.i(TAG, "onActivityResult connect");
                }

            }

        }
        return START_STICKY;
    }

    void handleCommand(Intent intent) {

    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());
            //tts.setLanguage(Locale.CHINESE);
        } else {
            Log.e("TTS", "Initialization failed");
        }

      //  buildFitnessClient();

    }

    private void writeMsg (String msgtext) {

        final MainActivity currentActivity = (MainActivity) ((MyApp) getApplicationContext()).getCurrentActivity();
        if (currentActivity == null) {
            Log.i(TAG, "currentActivity null");
        } else {
            Message msg = Message.obtain(currentActivity.messageHandler);
            msg.obj = msgtext;
            currentActivity.messageHandler.sendMessage(msg);
        }
    }

    private void dowork() {
        //Intent i = new Intent(getApplicationContext(), MainActivity.class);
        //i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //startActivity(i);


        //BlueToothDirect();

        final MainActivity currentActivity = (MainActivity) ((MyApp) getApplicationContext()).getCurrentActivity();
        // currentActivity.onBackPressed();
        // currentActivity.onBackPressed();

        writeMsg(null);
        //myTextView.setText("");
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                //.addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.RECORDING_API)
                .addApi(Fitness.BLE_API)
                .addScope(new Scope(Scopes.FITNESS_BODY_READ))
                        //.addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                writeMsg("Connected");
                                //myTextView.append("Connected\r\n");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                                // findFitnessDataSources(); // for senior
                                new readFitnessData().execute();
                                // buildBle();
                                // subscribe();
                                // cancelSubscription();

                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            currentActivity, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;

                                        result.startResolutionForResult(currentActivity,
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(TAG,
                                                "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();

        mClient.connect();
        writeMsg("Connecting...");
        //myTextView.append("Connecting...\r\n");

    }
    private void buildFitnessClient(String type) {

        /*Intent toret = new Intent();
        toret.setAction("com.sonymobile.smartwear.action.FORCE_REFRESH");
        getApplicationContext().sendBroadcast(toret);*/


        /*Intent toretxiaomi = new Intent();
        toretxiaomi.setAction("com.xiaomi.hm.health.ACTION_DEVICE_UNBIND_APPLICATION");
        getApplicationContext().sendBroadcast(toretxiaomi);*/

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
       // if (!powerManager.isScreenOn()) {
       //

        Log.i(TAG, "isScreenOn : " + powerManager.isScreenOn());

       if(!type.equals("backActivity") && !powerManager.isScreenOn()) {
       //if(!type.equals("backActivity") ) {


           View row = inflater.inflate(R.layout.myview, null);
           final TextView winview = (TextView) row.findViewById(R.id.myview);

           final Runnable runnableUI = new Runnable() {
               public void run() {
                   winview.setText("Syncing Fitness Data");
                   winview.setTextColor(Color.BLACK);
                   winview.setBackgroundColor(Color.WHITE);
                   WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                           WindowManager.LayoutParams.MATCH_PARENT,
                           WindowManager.LayoutParams.TYPE_PHONE,
                           WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                           PixelFormat.TRANSLUCENT);
                   windowManager.addView(winview, params);
               }
           };

           mHandler.post(runnableUI);



            //screenLock.acquire();
            keylock.disableKeyguard();

            final ActivityManager ama = (ActivityManager) getApplicationContext().getSystemService(getApplicationContext().ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> taskInfo = ama.getRunningTasks(1);


           /*int mytaskid = 0;
           for (int i = 0; i < taskInfo.size(); i++)
           {
               Log.i(TAG, "Application executed : "
                       +taskInfo.get(i).baseActivity.toShortString()
                       + "\t\t ID: "+taskInfo.get(i).id+"");
               // bring to front
               if (taskInfo.get(i).baseActivity.toShortString().indexOf("com.microbean.smartband") > -1) {
                  // ama.moveTaskToFront(taskInfo.get(i).id, ActivityManager.MOVE_TASK_WITH_HOME);
                   mytaskid = taskInfo.get(i).id;
               }
           }*/

          // ama.moveTaskToFront(21,ActivityManager.MOVE_TASK_NO_USER_ACTION);
           //ama.moveTaskToFront(mytaskid,ActivityManager.MOVE_TASK_NO_USER_ACTION);

            final String savePkgName = taskInfo.get(0).topActivity.getPackageName();
            final int saveTaskId = taskInfo.get(0).id;
            Log.i(TAG, "CURRENT Activity :" + taskInfo.get(0).topActivity.getClassName());

            Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.xiaomi.hm.health");
           // launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            //launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(launchIntent);

            final Runnable runnable = new Runnable() {
                public void run() {

                    //screenLock.release();
                    keylock.reenableKeyguard();

                   // ama.moveTaskToFront(saveTaskId, ActivityManager.MOVE_TASK_NO_USER_ACTION);
                    //dowork();


                   // screenLock.release();

                    //ama.moveTaskToFront(mytaskid,ActivityManager.MOVE_TASK_NO_USER_ACTION);


                    dowork();

                    Intent launchIntent2 = getApplicationContext().getPackageManager().getLaunchIntentForPackage(savePkgName);
                    if (launchIntent2 != null) {
                        launchIntent2.putExtra("type", "backActivity");
                        //launchIntent2.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        //launchIntent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        launchIntent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        getApplicationContext().startActivity(launchIntent2);
                    } else {
                        Intent intenthome = new Intent(Intent.ACTION_MAIN);
                        intenthome.addCategory(Intent.CATEGORY_HOME);
                        intenthome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getApplicationContext().startActivity(intenthome);
                    }

                    windowManager.removeView(winview);
                }
            };


            mHandler.postDelayed(runnable,1000 * 30);
         // mHandler.postDelayed(runnable,1000 * 10);
          // new Thread(runnable).run();

         // mHandler.post(runnable);


        } else {
            dowork();
        }
      //  }




    }

    private class readFitnessData extends AsyncTask<Void, Void, Void>   {
        protected Void doInBackground(Void...params) {
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
          //  cal.add(Calendar.HOUR, 1);
           // cal.add(Calendar.HOUR, 1);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.HOUR, -3);
            long startTime = cal.getTimeInMillis();

          /*  DataSource dataSource = new DataSource.Builder()
                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                    .setName(TAG + " - step count")
                    .setType(DataSource.TYPE_RAW)
                    .build();*/

            DataReadRequest readRequest = new DataReadRequest.Builder()
                    // The data request can specify multiple data types to return, effectively
                    // combining multiple data queries into one call.
                    // In this example, it's very unlikely that the request is for several hundred
                    // datapoints each consisting of a few steps and a timestamp.  The more likely
                    // scenario is wanting to see how many steps were walked per day, for 7 days.
                   // .aggregate(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)
                    // .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)


                    //.aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                    .read(DataType.TYPE_STEP_COUNT_DELTA)
                    .read(DataType.TYPE_ACTIVITY_SEGMENT)
                   // .read(DataType.TYPE_ACTIVITY_SAMPLE)
                    .read(DataType.TYPE_HEART_RATE_BPM)
                   // .read(DataType.TYPE_CALORIES_EXPENDED)
                    //.read(DataType.TYPE_DISTANCE_DELTA)
                    //.read(DataType.TYPE_LOCATION_TRACK)

                   // .read(dataSource)


                                    // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                                    // bucketByTime allows for a time span, whereas bucketBySession would allow
                            // bucketing by "sessions", which would need to be defined in code.
                  //  .bucketByTime(10, TimeUnit.MINUTES)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();


            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

           // DailyTotalResult dataReadResult = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_ACTIVITY_SEGMENT).await(1, TimeUnit.MINUTES);

            //dumpDataSet(dataReadResult.getTotal());

            prevBeat = lastBeat;

            if (dataReadResult.getBuckets().size() > 0) {
                Log.i(TAG, "Number of returned buckets of DataSets is: "
                        + dataReadResult.getBuckets().size());
                for (Bucket bucket : dataReadResult.getBuckets()) {
                    List<DataSet> dataSets = bucket.getDataSets();
                    for (DataSet dataSet : dataSets) {
                        dumpDataSet(dataSet);
                    }
                }


            } else if (dataReadResult.getDataSets().size() > 0) {
                Log.i(TAG, "Number of returned DataSets is: "
                        + dataReadResult.getDataSets().size());
                for (DataSet dataSet : dataReadResult.getDataSets()) {
                    dumpDataSet(dataSet);
                }
            }

            if (!laststeptimestr.equals(savesteptimestr)) {
                int descStep = lastStep - saveStep;
                savesteptimestr = laststeptimestr;
                saveStep = lastStep;
                //tts.speak(savesteptimestr + " , 增加 " + descStep + " 步", TextToSpeech.QUEUE_FLUSH, null);
            }

            if (!lasttimestr.equals(savetimestr)) {

                long FinalHRV = 0;
                //caculate HRV
                if (HRVData.size() > 0) {
                    for (int i = 1; i < HRVData.size(); i++) {
                        HRV.add(HRVData.get(i) - HRVData.get(i-1));
                    }
                }

                if (HRV.size() > 0) {

                    long mean = 0;
                    long sum = 0;
                    for (long a : HRV) {
                        sum += (a);
                    }

                    mean = sum / HRV.size();
                    Log.i(TAG, "mean: " + mean);

                    double variance_sum = 0;
                    for (long b : HRV)
                        variance_sum += (mean - b) * (mean - b);

                    double variance = variance_sum / HRV.size();
                    double std = Math.sqrt(variance);

                    FinalHRV = Math.round(std / (1000*60));
                    Log.i(TAG, "HRV: " + FinalHRV );

                }

                //beep(1);
                savetimestr = lasttimestr;

                String remindtext = "";
                if (lastBeat > prevBeat ) {
                    remindtext = ",上升" + Math.round(lastBeat-prevBeat) ;
                } else if (lastBeat < prevBeat ) {
                    remindtext = ",下降" + Math.round(prevBeat-lastBeat) ;
                }

                String msgtext = "心跳" + String.valueOf(Math.round(lastBeat)) + remindtext + " "  + ",最大心跳 " + String.valueOf(Math.round(largeBeat))  + ",於 " +  largeTime  + " ,變異數 " + FinalHRV ;
                // tts.speak(msgtext, TextToSpeech.QUEUE_FLUSH, null);

                tts.speak("心跳 " + String.valueOf(Math.round(lastBeat) + remindtext), TextToSpeech.QUEUE_FLUSH, null);

                writeMsg(msgtext);


                //tts.speak(String.valueOf(Math.round(lastBeat)),TextToSpeech.QUEUE_FLUSH, null,null);
            }

            return null;
        }
    }

    private void dumpDataSet(DataSet dataSet) {
        //Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = DateFormat.getTimeInstance();
        DateFormat timeFormat =  new SimpleDateFormat("HH:mm");


        for (DataPoint dp : dataSet.getDataPoints()) {

            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "getOriginalDataSource: " + dp.getOriginalDataSource().getAppPackageName());
            //Log.i(TAG, "getOriginalDataSource toString: " + dp.getOriginalDataSource().toString());

            //if (!dp.getOriginalDataSource().getAppPackageName().equals("com.xiaomi.hm.health"))
             //   return;

                String value = "";
                String fieldname = "";
                String deviceinfo = dp.getOriginalDataSource().getAppPackageName();
                String endTime = dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS));
                String startTime = dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS));

                for (Field field : dp.getDataType().getFields()) {
                    Log.i(TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));

                    //if (field.getName().equals("average") || field.getName().equals("steps") || field.getName().equals("activity") || field.getName().equals("bpm")  ) {
                        value = dp.getValue(field).toString();

                        if ( dp.getDataType().getName().equals("com.google.heart_rate.summary") || dp.getDataType().getName().equals("com.google.heart_rate.bpm")) {
                            fieldname = "heart_rate";
                            lastBeat = dp.getValue(field).asFloat();
                            HRVData.add(dp.getEndTime(TimeUnit.MILLISECONDS));

                            if (lastBeat > largeBeat) {
                                largeBeat = lastBeat;
                                largeTime = timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS));
                            }

                            lasttimestr = endTime;
                        } else if (dp.getDataType().getName().equals("com.google.step_count.delta")) {
                            fieldname = "step_count";

                            laststeptimestr = timeFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS));
                            lastStep =  dp.getValue(field).asInt();
                           // beep(1);
                        } else if (dp.getDataType().getName().equals("com.google.activity.summary") ||  dp.getDataType().getName().equals("com.google.activity.segment") ) {
                            fieldname = "activity";
                            if (value.equals("7")) {
                                value = "Walking";
                            } else if (value.equals("109")) {
                                value = "Light sleep";
                            } else if (value.equals("110")) {
                                value = "Deep sleep";
                            } else if (value.equals("3")) {
                                value = "not moving";
                            }
                        } else if (dp.getDataType().getName().equals("com.google.calories.expended") ) {
                            fieldname = "calories";
                        }
                    //}
                }

                if (!value.equals(""))
                    writeMsg(startTime + "-" + endTime + " : " + fieldname + " : " + value + " (" + deviceinfo + ")");

        }

    }

    public void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        // [START subscribe_to_datatype]
        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }

                           // dumpSubscriptionsList();
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });

        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_HEART_RATE_BPM)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }

                           // dumpSubscriptionsList();
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }});

        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_ACTIVITY_SEGMENT)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }

                            //dumpSubscriptionsList();
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });

        Fitness.RecordingApi.subscribe(mClient, DataType.TYPE_CALORIES_EXPENDED)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            if (status.getStatusCode()
                                    == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                                Log.i(TAG, "Existing subscription for activity detected.");
                            } else {
                                Log.i(TAG, "Successfully subscribed!");
                            }

                            // dumpSubscriptionsList();
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });
        // [END subscribe_to_datatype]
    }

    private void cancelSubscription() {
        final String dataTypeStr = DataType.TYPE_HEART_RATE_BPM.toString();
        Log.i(TAG, "Unsubscribing from data type: " + dataTypeStr);

        // Invoke the Recording API to unsubscribe from the data type and specify a callback that
        // will check the result.
        // [START unsubscribe_from_datatype]
        Fitness.RecordingApi.unsubscribe(mClient, DataType.TYPE_HEART_RATE_BPM)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully unsubscribed for data type: " + dataTypeStr);
                        } else {
                            // Subscription not removed
                            Log.i(TAG, "Failed to unsubscribe for data type: " + dataTypeStr);
                        }
                    }
                });

        Fitness.RecordingApi.unsubscribe(mClient, DataType.TYPE_ACTIVITY_SEGMENT)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully unsubscribed for data type: " + dataTypeStr);
                        } else {
                            // Subscription not removed
                            Log.i(TAG, "Failed to unsubscribe for data type: " + dataTypeStr);
                        }
                    }
                });

        Fitness.RecordingApi.unsubscribe(mClient, DataType.TYPE_CALORIES_EXPENDED)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully unsubscribed for data type: " + dataTypeStr);
                        } else {
                            // Subscription not removed
                            Log.i(TAG, "Failed to unsubscribe for data type: " + dataTypeStr);
                        }
                    }
                });
        // [END unsubscribe_from_datatype]
    }

    private void dumpSubscriptionsList() {
        // [START list_current_subscriptions]
        Fitness.RecordingApi.listSubscriptions(mClient, DataType.TYPE_HEART_RATE_BPM)
                // Create the callback to retrieve the list of subscriptions asynchronously.
                .setResultCallback(new ResultCallback<ListSubscriptionsResult>() {
                    @Override
                    public void onResult(ListSubscriptionsResult listSubscriptionsResult) {
                        for (Subscription sc : listSubscriptionsResult.getSubscriptions()) {
                            DataType dt = sc.getDataType();
                            Log.i(TAG, "Active subscription for data type: " + dt.getName());
                        }
                    }
                });
        // [END list_current_subscriptions]
    }

    private void buildBle() {
        BleScanCallback callback = new BleScanCallback() {
            @Override
            public void onDeviceFound(BleDevice device) {
                // ClaimBleDeviceRequest request = new ClaimBleDeviceRequest()
                //          .setDevice(device)
                //         .build();
                Log.i(TAG, "onDeviceFound");
                PendingResult<Status> pendingResult =
                        Fitness.BleApi.claimBleDevice(mClient, device);


               /* Message msg = Message.obtain(messageHandler);
                msg.obj = "onDeviceFound";
                messageHandler.sendMessage(msg);*/

            }
            @Override
            public void onScanStopped() {
                Log.i(TAG, "BLE scan stopped");
                /*Message msg = Message.obtain(messageHandler);
                msg.obj = "BLE scan stopped";
                messageHandler.sendMessage(msg);*/
            }
        };

        StartBleScanRequest request = new StartBleScanRequest.Builder()
                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                .setBleScanCallback(callback)
                .setTimeoutSecs(60)
                .build();

        PendingResult<Status> pendingResult =
                Fitness.BleApi.startBleScan(mClient, request);

        pendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (!status.isSuccess()) {
                    switch (status.getStatusCode()) {
                        case FitnessStatusCodes.DISABLED_BLUETOOTH:
                            //try {
                            // status.startResolutionForResult(mMonitor, REQUEST_BLUETOOTH);
                            Log.i(TAG, "startResolutionForResult ");
                            // } catch (IntentSender.SendIntentException e) {
                            //    Log.i(TAG, "SendIntentException: " + e.getMessage());
                            //}
                            break;
                    }
                    Log.i(TAG, "BLE scan unsuccessful");
                } else {
                    Log.i(TAG, "ble scan status message: " + status.describeContents());
                    Log.i(TAG, "BLE scan successful: " + status.toString());
                }
            }
        });

    }

    private void findFitnessDataSources() {
        // [START find_data_sources]
        Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
                // At least one datatype must be specified.
                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                        // Can specify whether data type is raw or derived.

                        //.setDataSourceTypes(DataSource.TYPE_RAW, DataSource.TYPE_DERIVED)
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .build())
                .setResultCallback(new ResultCallback<DataSourcesResult>() {
                    @Override
                    public void onResult(DataSourcesResult dataSourcesResult) {
                        Log.i(TAG, "Result: " + dataSourcesResult.getStatus().toString());
                        for (DataSource dataSource : dataSourcesResult.getDataSources()) {
                            Log.i(TAG, "Data source found: " + dataSource.toString());
                            Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                            //Let's register a listener to receive Activity data!
                            if (dataSource.getDataType().equals(DataType.TYPE_HEART_RATE_BPM)
                                    && mListener == null) {
                                Log.i(TAG, "Data source for TYPE_HEART_RATE_BPM found!  Registering.");
                                registerFitnessDataListener(dataSource,
                                        DataType.TYPE_STEP_COUNT_DELTA);
                            }
                        }
                    }
                });
        // [END find_data_sources]
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        // [START register_data_listener]
        mListener = new OnDataPointListener() {
            @Override
            public void onDataPoint(DataPoint dataPoint) {
                for (Field field : dataPoint.getDataType().getFields()) {
                    Value val = dataPoint.getValue(field);
                    Log.i(TAG, "Detected DataPoint field: " + field.getName());
                    Log.i(TAG, "Detected DataPoint value: " + val);
                    //myTextView.append(val + " " + field.getName() + "\r\n");

                    /*Message msg = Message.obtain(messageHandler);
                    msg.obj = val + " " + field.getName();
                    messageHandler.sendMessage(msg);*/
                }
            }
        };

        Fitness.SensorsApi.add(
                mClient,
                new SensorRequest.Builder()
                        .setDataSource(dataSource) // Optional but recommended for custom data sets.
                        .setDataType(dataType) // Can't be omitted.
                        .setSamplingRate(1, TimeUnit.SECONDS)
                        .build(),
                mListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener registered!");
                          //  myTextView.append("Listener registered!\r\n");
                        } else {
                            Log.i(TAG, "Listener not registered.");
                        }
                    }
                });
        // [END register_data_listener]
    }

    private void beep(int beeptime) {
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        for (int i=0 ; i < beeptime ; i++ ) {
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 220);
        }

    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.i(TAG, "rssi : " + rssi + " , " + device.toString());
                        /*runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mLeDeviceListAdapter.addDevice(device);
                                mLeDeviceListAdapter.notifyDataSetChanged();
                            }
                        });*/
                }};


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);

                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

    }

    private void BlueToothDirect() {

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

       /* List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        for(BluetoothDevice device : devices) {
            if(device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                Log.i(TAG, "device : " + device.toString());
            }
        }*/

        mBluetoothAdapter.startLeScan(mLeScanCallback);
        scanLeDevice(true);
    }


}
