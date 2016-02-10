package com.microbean.smartband;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ScrollView;
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
import com.google.android.gms.fitness.request.ClaimBleDeviceRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.fitness.request.StartBleScanRequest;
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

import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

public class MainActivity extends AppCompatActivity implements OnInitListener {

    private static final int REQUEST_OAUTH = 1;

    /**
     *  Track whether an authorization activity is stacking over the current activity, i.e. when
     *  a known auth error is being resolved, such as showing the account chooser or presenting a
     *  consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;
    private static final String TAG = "MyActivity";

    private GoogleApiClient mClient = null;
    private OnDataPointListener mListener;
    private TextView myTextView = null;
    private Handler messageHandler = null;
    private Handler mHandler = new Handler();
    private Handler mHandler2 = new Handler();
    private float lastBeat = 0;
    private float saveBeat = 0;
    private String lasttimestr = "";
    private String savetimestr = "";
    static TextToSpeech tts = null;
    private List<Long> HRV = new ArrayList<Long>();
    private List<Long> HRVData = new ArrayList<Long>();
    private String largeTime = "";
    private float largeBeat = 0;
    static boolean startAct = false;
    private static MainActivity context;

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            //tts.setLanguage(Locale.getDefault());
            tts.setLanguage(Locale.CHINESE);


        } else {
            Log.e("TTS", "Initialization failed");
        }

        buildFitnessClient();

    }

    public static void closeActivity(){
        //startAct = true;
        //context.finish();
       // context.moveTaskToBack(true);
        context.myTextView.setText("");
        context.mClient.disconnect();
        context.HRV.clear();

        context.onInit(0);
        context.goBackLoop();

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        Log.i(TAG, "MainActivity onCreate");
        tts = new TextToSpeech(this, this);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

       // Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
        //Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.hostapp.everest");
        //startService(launchIntent);
        //launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
       // startActivity(launchIntent);
        //launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //startActivity(launchIntent);

        /*Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);*/

        myTextView = (EditText)findViewById(R.id.textEdit1);

        messageHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                myTextView.append(msg.obj.toString() + "\r\n");
            }
        };


        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }




        final Runnable runnable2 = new Runnable() {
            public void run() {
                /*Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
                startActivity(launchIntent);*/

                startAct = true;
                finish();

                Log.i(TAG, "MainActivity startActivity");
                //onBackPressed();



                /*myTextView.setText("");
                mClient.disconnect();
                HRV.clear();
                buildFitnessClient();*/

                //mHandler2.postDelayed(this, 1000 * 30 * 1);
            }
        };

        final Runnable runnable = new Runnable() {
            public void run() {

                beep(2);

                Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
                //launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(launchIntent);


                moveTaskToBack(true);

               // tts.stop();
               // tts.shutdown();

               /* Intent i = new Intent(getApplicationContext(), MainActivity.class);
                i.setAction(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                startActivity(i);*/


                //buildFitnessClient();

                //mHandler.postDelayed(this, 1000 * 60 * 1);
                mHandler2.postDelayed(runnable2,1000 * 30 * 1);
            }
        };



        //mHandler.post(runnable);
        //mHandler.postDelayed(runnable,1000 * 60 * 5);

        goBackLoop();

       // Intent startIntent = new Intent(this, MyService.class);
      //  startService(startIntent);

        //Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.sonymobile.lifelog");
        //startActivity(launchIntent);

       // Intent intent = new Intent();
       // intent.setClassName("com.sonymobile.hostapp.everest", "com.sonymobile.hostapp.everest.googlefit.HeartrateSensorService");
        //stopService(intent);
        //startService(intent);


        //ComponentName componentInfo = taskInfo.get(0).topActivity;
        //componentInfo.getPackageName();




    }

    private void goBackLoop() {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 3);

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("msg", "StartLifeLog");

        PendingIntent pi = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);

    }


    @Override
    protected void onDestroy() {
        //Close the Text to Speech Library
        if(tts != null) {

            tts.stop();
            tts.shutdown();
            Log.i(TAG, "TTS Destroyed");
        }

        if (startAct) {
            myTextView.setText("");
            mClient.disconnect();
            HRV.clear();

            Intent i = new Intent(getApplicationContext(), MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            moveTaskToBack(true);
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     *  Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     *  to connect to Fitness APIs. The scopes included should match the scopes your app needs
     *  (see documentation for details). Authentication will occasionally fail intentionally,
     *  and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     *  can address. Examples of this include the user never having signed in before, or having
     *  multiple accounts on the device and needing to specify which account to use, etc.
     */
    private void buildFitnessClient() {
        myTextView.setText("");
        // Create the Google API Client
        mClient = new GoogleApiClient.Builder(this)
                //.addApi(Fitness.SENSORS_API)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.RECORDING_API)
                //.addApi(Fitness.BLE_API)
                //.addScope(new Scope(Scopes.FITNESS_BODY_READ))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                //.addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                                myTextView.append("Connected\r\n");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                                // findFitnessDataSources(); // for senior
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
                                            MainActivity.this, 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(MainActivity.this,
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
                myTextView.append("Connecting...\r\n");


    }

   /* @Override
    protected void onStart() {
        super.onStart();
        // Connect to the Fitness API
        Log.i(TAG, "Connecting...");
        mClient.connect();
        myTextView.append("Connecting...\r\n");
    }*/

   /* @Override
    protected void onStop() {
        super.onStop();
        myTextView.append("disconnect...\r\n");
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
    }*/

    @Override
    public void onBackPressed() {

        //cancelSubscription();

        if (mClient.isConnected()) {
            mClient.disconnect();
        }


        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                    Log.i(TAG, "onActivityResult connect");
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }


    private void buildBle() {
        BleScanCallback callback = new BleScanCallback() {
            @Override
            public void onDeviceFound(BleDevice device) {
               // ClaimBleDeviceRequest request = new ClaimBleDeviceRequest()
              //          .setDevice(device)
               //         .build();
                PendingResult<Status> pendingResult =
                       Fitness.BleApi.claimBleDevice(mClient, device);
                Log.i(TAG, "onDeviceFound");

                Message msg = Message.obtain(messageHandler);
                msg.obj = "onDeviceFound";
                messageHandler.sendMessage(msg);

            }
            @Override
            public void onScanStopped() {
                Log.i(TAG, "BLE scan stopped");
                Message msg = Message.obtain(messageHandler);
                msg.obj = "BLE scan stopped";
                messageHandler.sendMessage(msg);
            }
        };

        StartBleScanRequest request = new StartBleScanRequest.Builder()
                .setDataTypes(DataType.AGGREGATE_HEART_RATE_SUMMARY)
                .setBleScanCallback(callback)
                .build();

        PendingResult<Status> pendingResult =
                Fitness.BleApi.startBleScan(mClient, request);

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

                String msgtext = "您的心跳" + String.valueOf(Math.round(lastBeat)) + ",最大心跳 " + String.valueOf(Math.round(largeBeat))  + ",於 " +  largeTime  + " ,變異數 " + FinalHRV ;
               // tts.speak(msgtext, TextToSpeech.QUEUE_FLUSH, null);
                tts.speak("心跳 " + String.valueOf(Math.round(lastBeat)), TextToSpeech.QUEUE_FLUSH, null);

                Message msg = Message.obtain(messageHandler);
                msg.obj = msgtext;
                messageHandler.sendMessage(msg);

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

            String value="";
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

            Message msg = Message.obtain(messageHandler);
            msg.obj = endTime + " : " + value;
            messageHandler.sendMessage(msg);
        }

    }

    private void findFitnessDataSources() {
        // [START find_data_sources]
        Fitness.SensorsApi.findDataSources(mClient, new DataSourcesRequest.Builder()
                // At least one datatype must be specified.
                .setDataTypes(DataType.TYPE_HEART_RATE_BPM)
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
                                        DataType.TYPE_HEART_RATE_BPM);
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

                    Message msg = Message.obtain(messageHandler);
                    msg.obj = val + " " + field.getName();
                    messageHandler.sendMessage(msg);
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
                            myTextView.append("Listener registered!\r\n");
                        } else {
                            Log.i(TAG, "Listener not registered.");
                        }
                    }
                });
        // [END register_data_listener]
    }

    /**
     * Unregister the listener with the Sensors API.
     */
    private void unregisterFitnessDataListener() {
        if (mListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return;
        }

        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        Fitness.SensorsApi.remove(
                mClient,
                mListener)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Listener was removed!");
                        } else {
                            Log.i(TAG, "Listener was not removed.");
                        }
                    }
                });
        // [END unregister_data_listener]
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
        // [END unsubscribe_from_datatype]
    }

    private void beep(int beeptime) {
        ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

        for (int i=0 ; i < beeptime ; i++ ) {
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 220);
        }

    }
}
