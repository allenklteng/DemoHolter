package com.vitalsigns.demoholter;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.HandlerThread;
import android.os.Process;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.vitalsigns.sdk.ble.scan.DeviceListFragment;


public class MainActivity extends AppCompatActivity
  implements DeviceListFragment.OnEvent,
             ChartView.OnDrawChartFinishListener
{
  private static final String                   LOG_TAG  = "MainActivity";
  private static final int                      CHART_DATA_RANGE_COUNT = 3;
  private              Activity                 mActivity;
  private              FloatingActionButton     FabStart = null;
  private              ChartManagement          chartManagement;

  /// Chart
  private LinearLayout chartBlock;
  private FrameLayout  chart1Layout;
  private FrameLayout  chart2Layout;
  private FrameLayout  chart3Layout;
  private FrameLayout[] chartLayoutArray;

  /// [CC] : Chart setting ; 10/27/2016
  private int nChart1LeadType;
  private int nChart2LeadType;
  private int nChart3LeadType;
  private final static int LEAD_1 = 0;
  private final static int LEAD_2 = 1;
  private final static int LEAD_3 = 2;

  private int[] chartIdArray;
  private String[] yAxisUnit;
  private String[] chartTitle;
  private int[] gainArray, offsetArray, vrefArray;
  private int currentChannelCount;
  private String unitSettingString = "";
  private String gainSettingString = "";
  private String offsetSettingString = "";
  private String vrefSettingString = " ";

  private              HandlerThread         mBackgroundThread = null;

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
    chartLayoutArray = new FrameLayout[]{
      chart1Layout, chart2Layout, chart3Layout};
    getChartLayoutSize();
    chartTitle = getResources().getStringArray(R.array.chart_title_array);
    chartManagement = new ChartManagement(getApplicationContext(), getSupportFragmentManager(), GlobalData.DEFAULT_CHART_COUNT * CHART_DATA_RANGE_COUNT);
    chartDefaultSetting();

    if(GlobalData.requestPermissionForAndroidM(this))
    {
      initBle();
      Log.d(LOG_TAG, "scanBle @ onCreate()");

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

    /// [AT-PM] : Release background HandlerThread ; 07/20/2017
    Utility.releaseHandlerThread(mBackgroundThread);
    mBackgroundThread = null;
  }

  private void scanBle()
  {
    if(GlobalData.requestPermissionForAndroidM(this))
    {
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
      /// [AT-PM] : Check connected ; 06/16/2017
      if(!GlobalData.BleControl.isConnect())
      {
        Snackbar.make(view, "Blood pressure measurement -> STOPPED", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
        return;
      }

      /// [AT-PM] : Stop the recording ; 06/16/2017
      if(GlobalData.Recording)
      {
        //stop();

        Snackbar.make(view, "Blood pressure measurement -> STOPPED", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
        return;
      }

      /// [AT-PM] : Start recording ; 06/16/2017
      /*
      if(start())
      {
        Snackbar.make(view, "Blood pressure measurement -> STARTED", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
      }
      else
      {
        Snackbar.make(view, "Blood pressure measurement -> FAILED", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .show();
      }*/
    }
  };

  private void settingDefaultConfig() {

    yAxisUnit = new String[GlobalData.DEFAULT_CHART_COUNT];
    gainArray = new int[GlobalData.DEFAULT_CHART_COUNT];
    offsetArray = new int[GlobalData.DEFAULT_CHART_COUNT];
    vrefArray = new int[GlobalData.DEFAULT_CHART_COUNT];

    currentChannelCount = GlobalData.DEFAULT_CHANNEL_COUNT;
    for(int idx = 0; idx < GlobalData.DEFAULT_CHART_COUNT; idx++) {
      unitSettingString += getString(R.string.unit_setting_fragment_default_y_axis_unit);
      gainSettingString += getString(R.string.unit_setting_fragment_default_gain);
      offsetSettingString += getString(R.string.unit_setting_fragment_default_offset);
      vrefSettingString += String.valueOf(GlobalData.DEFAULT_VREF);
      if(idx != GlobalData.DEFAULT_CHART_COUNT - 1) {
        unitSettingString += ",";
        gainSettingString += "," ;
        offsetSettingString += "," ;
        vrefSettingString += ",";
      }
      yAxisUnit[idx] = getString(R.string.unit_setting_fragment_default_y_axis_unit);
      gainArray[idx] = Integer.parseInt(getResources().getString(R.string.unit_setting_fragment_default_gain));
      offsetArray[idx] = Integer.parseInt(getResources().getString(R.string.unit_setting_fragment_default_offset));
      vrefArray[idx] = GlobalData.DEFAULT_VREF;
    }

    nChart1LeadType = LEAD_1;
    nChart2LeadType = LEAD_2;
    nChart3LeadType = LEAD_3;

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

  private VitalSignsBle.BleEvent mBleEvent = new VitalSignsBle.BleEvent()
  {
    @Override
    public void onDisconnect()
    {
      Log.d(LOG_TAG, "onDisconnect()");
      // TODO : stop();
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

  }
}
