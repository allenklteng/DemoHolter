package com.vitalsigns.demoholter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.vitalsigns.sdk.ble.scan.DeviceListFragment;
import com.vitalsigns.sdk.dsp.holter.Constant;
import com.vitalsigns.sdk.utility.RequestPermission;

public class MainActivity extends AppCompatActivity
  implements DeviceListFragment.OnEvent,
             ChartView.OnDrawChartFinishListener
{
  private static final String                   LOG_TAG  = "MainActivity";
  private static final int                      CHART_DATA_RANGE_COUNT = 3;
  private static final int                      DRAW_CHART_DELAY = 300;
  private static final int                      CHECK_HR_STATUS_LIGHT_INTERVAL = 100;
  private static final float                    MILLISECOND_TO_SECOND = 1000;
  private static final double                   NANOSECOND_TO_MILLISECOND = 1000000;
  private long                                  startRecordTime;
  private HandlerThread                         drawChartThread;
  private Handler                               drawChartThreadHandler;
  private Handler                               getHeartRateHandler;
  private              Activity                 mActivity;
  private              FloatingActionButton     FabStart = null;
  private              VitalSignsDsp            VSDsp = null;
  private              ChartManagement          chartManagement;

  /// Chart
  private LinearLayout  chartBlock;
  private FrameLayout   chart1Layout;
  private FrameLayout   chart2Layout;
  private FrameLayout   chart3Layout;
  private FrameLayout[] chartLayoutArray;
  private TextView      heartRateText;

  /// [CC] : Chart setting ; 10/27/2016
  private float              drawChartAccumulateTime;

  private int[] chartIdArray;
  private float[][] chartData;
  private float[] xWindowRange;
  private String[] yAxisUnit;
  private String[] chartTitle;
  private String[] threeChartDataRangeString;
  private int[] gainArray, offsetArray, vrefArray;
  private int currentChannelCount;

  private HandlerThread mBackgroundThread = null;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mActivity = this;
    settingDefaultConfig();
    FabStart = (FloatingActionButton)findViewById(R.id.fab);
    FabStart.setOnClickListener(onClickListenerFab);
    chartBlock = (LinearLayout) findViewById(R.id.chart_block);
    chart1Layout = (FrameLayout) findViewById(R.id.chart1);
    chart2Layout = (FrameLayout) findViewById(R.id.chart2);
    chart3Layout = (FrameLayout) findViewById(R.id.chart3);
    heartRateText = (TextView) findViewById(R.id.heart_rate_value);
    chartLayoutArray = new FrameLayout[]{
      chart1Layout, chart2Layout, chart3Layout};
    chartData = new float[GlobalData.DEFAULT_CHART_COUNT][];
    xWindowRange = new float[GlobalData.DEFAULT_CHART_COUNT];
    getChartLayoutSize();
    chartTitle = getResources().getStringArray(R.array.chart_title_array);
    chartManagement = new ChartManagement(getApplicationContext(),
                                          getSupportFragmentManager(),
                                          GlobalData.DEFAULT_CHART_COUNT * CHART_DATA_RANGE_COUNT);
    chartDefaultSetting();

    if(GlobalData.requestPermissionForAndroidM(this))
    {
      initBle();
      Log.d(LOG_TAG, "scanBle @ onCreate()");

      VSDsp = new VitalSignsDsp(MainActivity.this);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu)
  {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item)
  {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if(id == R.id.action_scan_ble_device)
    {
      if((GlobalData.BleControl != null) &&
         (GlobalData.BleControl.isConnect()))
      {
        GlobalData.showToast(mActivity,
                             getString(R.string.action_disconnect_first_if_is_connection),
                             Toast.LENGTH_SHORT);
        return (true);
      }

      if(GlobalData.requestPermissionForAndroidM(this))
      {
        Log.d(LOG_TAG, "scanBle @ onOptionsItemSelected()");
        scanBle();
      }
      return (true);
    }

    if(id == R.id.action_disconnect)
    {
      GlobalData.BleControl.disconnect();
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onStart()
  {
    super.onStart();

    /// [AT-PM] : Start background HandlerThread ; 07/20/2017
    mBackgroundThread = new HandlerThread("Background Thread", Process.THREAD_PRIORITY_BACKGROUND);
    mBackgroundThread.start();
  }

  @Override
  protected void onStop()
  {
    super.onStop();

    if(mBackgroundThread != null)
    {
      mBackgroundThread.interrupt();
      mBackgroundThread.quit();
    }
    mBackgroundThread = null;
  }

  private void scanBle()
  {
    if(GlobalData.requestPermissionForAndroidM(this))
    {
      if(GlobalData.BleControl == null)
      {
        initBle();
        VSDsp = new VitalSignsDsp(MainActivity.this);
      }

      /// [AT-PM] : Call a dialog to scan device ; 05/05/2017
      DeviceListFragment fragment = DeviceListFragment.newInstance(DeviceListFragment.ACTION_SCAN_BLE_DEVICE,
                                                                   DeviceListFragment.STYLE_DEFAULT_BLACK);
      getFragmentManager().beginTransaction()
                          .add(fragment, getResources().getString(R.string.device_list_fragment_tag))
                          .commitAllowingStateLoss();
    }
  }

  private void initBle()
  {
    GlobalData.BleControl = new VitalSignsBle(MainActivity.this, mBleEvent);
  }

  private View.OnClickListener onClickListenerFab = new View.OnClickListener()
  {
    @Override
    public void onClick(View view)
    {
      if(GlobalData.BleControl == null)
      {
        return;
      }

      /// [AT-PM] : Check connected ; 06/16/2017
      if(!GlobalData.BleControl.isConnect())
      {
        GlobalData.showToast(mActivity, "Blood pressure measurement -> STOPPED", Toast.LENGTH_SHORT);
        return;
      }

      /// [AT-PM] : Stop the recording ; 06/16/2017
      if(GlobalData.Recording)
      {
        stopRecord();

        GlobalData.showToast(mActivity, "Blood pressure measurement -> STOPPED", Toast.LENGTH_SHORT);
        return;
      }

      if(VSDsp == null)
      {
        Log.d(LOG_TAG, "VSDsp == null");

        GlobalData.showToast(mActivity, "Blood pressure measurement -> FAILED", Toast.LENGTH_SHORT);
        return ;
      }

      /// [WC] : High pass filter cut off frequency:3f, Low pass filter cut off frequency:100f

      if(!VSDsp.Start(3f,100f,true,true,gainArray, vrefArray, GlobalData.BleControl.getAddress()))
      {
        Log.d(LOG_TAG, "VSDsp.Start() == false");

        GlobalData.showToast(mActivity, "Blood pressure measurement -> FAILED", Toast.LENGTH_SHORT);
        return ;
      }

      GlobalData.showToast(mActivity, "Blood pressure measurement -> STARTED", Toast.LENGTH_SHORT);
      startRecord();

    }
  };

  private void startRecord() {
    GlobalData.Recording = true;
    startRecordTime = 0;

    FabStart.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), android.R.drawable.ic_media_pause));

    /// Get x window range
    xWindowRange = chartManagement.getXWindowRange();
    /// Start draw chart
    chartManagement.startToDrawChart(VSDsp.GetOutputRate());
    /// Config timer
    startRecordTime = System.nanoTime();
    drawChartThread = new HandlerThread("draw chart thread", Process.THREAD_PRIORITY_BACKGROUND);
    drawChartThread.start();
    drawChartThreadHandler = new Handler(drawChartThread.getLooper());
    drawChartThreadHandler.postDelayed(drawChartRunnable, DRAW_CHART_DELAY);

    getHeartRateHandler = new Handler();
    getHeartRateHandler.postDelayed(getHeartRateRunnable, CHECK_HR_STATUS_LIGHT_INTERVAL);
  }

  private void stopRecord()
  {
    /// [AT-PM] : No need to stop if not in recording ; 07/20/2017
    if(!GlobalData.Recording)
    {
      return;
    }
    /// [AT-PM] : Stop the measurement ; 10/24/2016
    if(VSDsp == null)
    {
      return;
    }
    VSDsp.Stop();

    GlobalData.Recording = false;

    if(drawChartThread != null) {
      drawChartThreadHandler.removeCallbacks(drawChartRunnable);
      drawChartThread.interrupt();
      drawChartThread.quit();
      drawChartThread = null;
      drawChartThreadHandler = null;
    }
    if(getHeartRateHandler != null) {
      getHeartRateHandler.removeCallbacksAndMessages(null);
    }

    chartManagement.stopToDrawChart(drawChartAccumulateTime);

    runOnUiThread(new Runnable()
    {
      @Override
      public void run()
      {
        FabStart.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), android.R.drawable.ic_media_play));
      }
    });
  }

  private void settingDefaultConfig() {

    yAxisUnit = new String[GlobalData.DEFAULT_CHART_COUNT];
    gainArray = new int[GlobalData.DEFAULT_CHART_COUNT];
    offsetArray = new int[GlobalData.DEFAULT_CHART_COUNT];
    vrefArray = new int[GlobalData.DEFAULT_CHART_COUNT];

    currentChannelCount = GlobalData.DEFAULT_CHANNEL_COUNT;
    for(int idx = 0; idx < GlobalData.DEFAULT_CHART_COUNT; idx++) {
      yAxisUnit[idx] = getString(R.string.unit_setting_fragment_default_y_axis_unit);
      gainArray[idx] = Integer.parseInt(getResources().getString(R.string.unit_setting_fragment_default_gain));
      offsetArray[idx] = Integer.parseInt(getResources().getString(R.string.unit_setting_fragment_default_offset));
      vrefArray[idx] = GlobalData.DEFAULT_VREF;
    }


  }

  private void getChartLayoutSize() {
    chartBlock.post(new Runnable() {
      @Override
      public void run() {
        /// Get chart1 size
        int chartLayoutWidth = chartBlock.getMeasuredWidth();
        int chartLayoutHeight = chartBlock.getMeasuredHeight() / 3;
        Log.i(LOG_TAG, "chartLayoutWidth = " + chartLayoutWidth +
                           " chartLayoutHeight = " + chartLayoutHeight);
        /// Set chart1 ,chart2 and chart3 to the same size
        LinearLayout.LayoutParams layoutParams;
        layoutParams = new LinearLayout.LayoutParams(chartLayoutWidth, chartLayoutHeight);
        chartLayoutArray[0].setLayoutParams(layoutParams);
        chartLayoutArray[1].setLayoutParams(layoutParams);
        chartLayoutArray[2].setLayoutParams(layoutParams);
      }
    });
  }

  private void chartDefaultSetting() {
    chartIdArray = new int[] {R.id.chart1, R.id.chart2, R.id.chart3};
    changeLayoutByChannelSetting();
  }

  private void changeLayoutByChannelSetting() {
    chartManagement.removeFragment();
    chartManagement.addFragment(
      chartIdArray, yAxisUnit, gainArray, offsetArray, vrefArray,
      chartTitle);
    chart1Layout.invalidate();
    chart2Layout.invalidate();
    chart3Layout.invalidate();
  }

  public void setRecordButtonActive(final boolean bFlag) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (bFlag) {
          GlobalData.showToast(mActivity, "BLE connect!", Toast.LENGTH_SHORT);
          FabStart.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mActivity,R.color.colorAccent)));
        }else {
          GlobalData.showToast(mActivity, "BLE disconnect!", Toast.LENGTH_SHORT);
          FabStart.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(mActivity,R.color.fabButtonColor)));
        }
      }
    });

  }

  final Runnable drawChartRunnable = new Runnable() {
    @Override
    public void run() {
      float startTimeInMilliSecond;
      drawChartAccumulateTime = (float)((System.nanoTime() - startRecordTime) / NANOSECOND_TO_MILLISECOND);
      if(drawChartAccumulateTime > VSDsp.GetAccumulateTimeInMilliSecond()) {
        drawChartAccumulateTime = VSDsp.GetAccumulateTimeInMilliSecond();
      }
      /// Get data from queue
      if(drawChartAccumulateTime < xWindowRange[0] * MILLISECOND_TO_SECOND) {
        startTimeInMilliSecond = 0;
      }else{
        startTimeInMilliSecond = drawChartAccumulateTime - xWindowRange[0] * MILLISECOND_TO_SECOND;
      }

      VSDsp.PrepareJniData(startTimeInMilliSecond, drawChartAccumulateTime,
                           Constant.LEAD_TYPE.LEAD_1, Constant.LEAD_TYPE.LEAD_2, Constant.LEAD_TYPE.LEAD_3,
                           Constant.LINE_TYPE.FILTER, Constant.LINE_TYPE.FILTER, Constant.LINE_TYPE.FILTER);
      chartData[0] = VSDsp.GetDrawLead1();
      chartData[1] = VSDsp.GetDrawLead2();
      chartData[2] = VSDsp.GetDrawLead3();

      if((chartData[0] != null && chartData[0].length > 0) &&
         (chartData[1] != null && chartData[1].length > 0) &&
         (chartData[2] != null && chartData[2].length > 0)) {

        threeChartDataRangeString = chartManagement.drawChart(chartData, drawChartAccumulateTime);
      }
      else
      {
        if(drawChartThreadHandler != null) {
          drawChartThreadHandler.postDelayed(drawChartRunnable, 100);
        }
      }
    }
  };

  final Runnable getHeartRateRunnable = new Runnable() {
    @Override
    public void run() {
      int nHeartRate;

      nHeartRate      = (int)VSDsp.GetHeartRateAvg();
      if (nHeartRate <= 0) {
        heartRateText.setText("0");
      }
      else {
        heartRateText.setText(String.valueOf(nHeartRate));
      }

      getHeartRateHandler.postDelayed(getHeartRateRunnable, CHECK_HR_STATUS_LIGHT_INTERVAL);
    }
  };

  @Override
  protected void onDestroy()
  {
    super.onDestroy();

    if(GlobalData.BleControl != null)
    {
      GlobalData.BleControl.destroy();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {

    if (keyCode == KeyEvent.KEYCODE_BACK)
    {
      Intent intentHome = new Intent(Intent.ACTION_MAIN);
      intentHome.addCategory(Intent.CATEGORY_HOME);
      intentHome.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intentHome);

      return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  private VitalSignsBle.BleEvent mBleEvent = new VitalSignsBle.BleEvent()
  {
    @Override
    public void onDisconnect()
    {
      Log.d(LOG_TAG, "onDisconnect()");
      stopRecord();
      GlobalData.BleControl.disconnect();
      setRecordButtonActive(false);
    }

    @Override
    public void onConnect()
    {
      Log.d(LOG_TAG, "onConnect()");
      setRecordButtonActive(true);
    }
  };

  @Override
  public void onBleDeviceSelected(String bleDeviceAddress)
  {
    if(bleDeviceAddress == null)
    {
      /// [AT-PM] : Re-scan the BLE device ; 10/24/2016
      Log.d(LOG_TAG, "No device selected");
      return;
    }

    /// [AT-PM] : Connect BLE device ; 10/24/2016
    GlobalData.BleControl.connect(bleDeviceAddress);
  }

  @Override
  public void onDfuDeviceSelected(BluetoothDevice bluetoothDevice)
  {
  }

  @Override
  public void onSendCrashMsg(String s, String s1)
  {
  }

  @Override
  public void drawChartFinish()
  {
    if((GlobalData.Recording) && (drawChartThreadHandler != null)) {
      drawChartThreadHandler.postDelayed(drawChartRunnable, 10);
    }
  }

  @Override
  public void onCancelDialog() {
  }


  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults)
  {
    if((grantResults == null) || (grantResults.length == 0))
    {
      return;
    }
    switch (requestCode)
    {
      case RequestPermission.PERMISSION_REQUEST_COARSE_LOCATION:
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
          Log.d(LOG_TAG, "coarse location permission granted");
          if(GlobalData.BleControl == null)
          {
            initBle();
            VSDsp = new VitalSignsDsp(MainActivity.this);
          }
        }
        else
        {
          AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setTitle("Functionality limited");
          builder.setMessage("Since location access has not been granted, this app will not be able to discover devices.");
          builder.setPositiveButton(android.R.string.ok, null);
          builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
              finish();
            }
          });
          builder.show();
        }
        break;
      case RequestPermission.PERMISSION_REQUEST_EXTERNAL_STORAGE:
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
          Log.d(LOG_TAG, "external storage permission granted");
        }
        else
        {
          AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setTitle("Functionality limited");
          builder.setMessage("Since external storage has not been granted, this app will not be able to discover devices.");
          builder.setPositiveButton(android.R.string.ok, null);
          builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
              finish();
            }
          });
          builder.show();
        }
        break;
      case RequestPermission.PERMISSION_REQUEST_READ_PHONE_STATE:
        if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
          Log.d(LOG_TAG, "read phone state granted");
        }
        else
        {
          AlertDialog.Builder builder = new AlertDialog.Builder(this);
          builder.setTitle("Functionality limited");
          builder.setMessage("Since read phone state has not been granted, this app will not be able to discover devices.");
          builder.setPositiveButton(android.R.string.ok, null);
          builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
              finish();
            }
          });
          builder.show();
        }
        break;
    }
  }
}
