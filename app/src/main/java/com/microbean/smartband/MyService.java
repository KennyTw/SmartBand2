package com.microbean.smartband;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Subscription;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
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

    private float lastBeat = 0;
    private float prevBeat = 0;
    private float saveBeat = 0;
    private String lasttimestr = "";
    private String savetimestr = "";
    private List<Long> HRV = new ArrayList<Long>();
    private List<Long> HRVData = new ArrayList<Long>();
    private String largeTime = "";
    private float largeBeat = 0;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MyService onCreate() executed");


        //Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
        //startActivity(launchIntent);

        tts = new TextToSpeech(this, this);
    }

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
        String command = intent.getStringExtra("command");
        int value = intent.getIntExtra("value",-1);

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
                            buildFitnessClient();

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
                buildFitnessClient();
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
            //tts.setLanguage(Locale.getDefault());
            tts.setLanguage(Locale.CHINESE);
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

    private void buildFitnessClient() {

        Intent toret = new Intent();
        toret.setAction("com.sonymobile.smartwear.action.FORCE_REFRESH");
        getApplicationContext().sendBroadcast(toret);

        final MainActivity currentActivity = (MainActivity) ((MyApp) getApplicationContext()).getCurrentActivity();
        writeMsg(null);
        //myTextView.setText("");
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                //.addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.RECORDING_API)
                        //.addApi(Fitness.BLE_API)
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
                                //findFitnessDataSources(); // for senior
                                new readFitnessData().execute();
                                //buildBle();
                                subscribe();

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

    private class readFitnessData extends AsyncTask<Void, Void, Void>   {
        protected Void doInBackground(Void...params) {
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.HOUR, -1);
            long startTime = cal.getTimeInMillis();

            DataReadRequest readRequest = new DataReadRequest.Builder()
                    // The data request can specify multiple data types to return, effectively
                    // combining multiple data queries into one call.
                    // In this example, it's very unlikely that the request is for several hundred
                    // datapoints each consisting of a few steps and a timestamp.  The more likely
                    // scenario is wanting to see how many steps were walked per day, for 7 days.
                    .aggregate(DataType.TYPE_HEART_RATE_BPM, DataType.AGGREGATE_HEART_RATE_SUMMARY)
                            // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                            // bucketByTime allows for a time span, whereas bucketBySession would allow
                            // bucketing by "sessions", which would need to be defined in code.
                    .bucketByTime(1, TimeUnit.MINUTES)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();

            DataReadResult dataReadResult =
                    Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

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

            String value = "";
            String endTime = dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS));
            for(Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));

                if (field.getName().equals("average")) {
                    value = dp.getValue(field).toString();
                    lastBeat = dp.getValue(field).asFloat();
                    HRVData.add(dp.getEndTime(TimeUnit.MILLISECONDS));

                    if (lastBeat > largeBeat) {
                        largeBeat = lastBeat;
                        largeTime = timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS));
                    }
                }


                // break;
            }

            lasttimestr = endTime;

            writeMsg(endTime + " : " + value);

        }

    }

    public void subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        // [START subscribe_to_datatype]
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

                            dumpSubscriptionsList();
                        } else {
                            Log.i(TAG, "There was a problem subscribing.");
                        }
                    }
                });
        // [END subscribe_to_datatype]
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

}
