package com.vitalsigns.demoholter;

import android.os.HandlerThread;
import android.os.Process;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.vitalsigns.sdk.ble.scan.DeviceListFragment;


public class MainActivity extends AppCompatActivity
{

  private static final String LOG_TAG = "MainActivity";

  private HandlerThread mBackgroundThread = null;

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

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
                                                                   DeviceListFragment.STYLE_WHITE);
      getFragmentManager().beginTransaction()
                          .add(fragment, getResources().getString(R.string.device_list_fragment_tag))
                          .commitAllowingStateLoss();
    }
  }

  private void initBle()
  {
    GlobalData.BleControl = new VitalSignsBle(MainActivity.this, mBleEvent);
  }

  private VitalSignsBle.BleEvent mBleEvent = new VitalSignsBle.BleEvent()
  {
    @Override
    public void onDisconnect()
    {
      Log.d(LOG_TAG, "onDisconnect()");
      // TODO : stop();

      GlobalData.BleControl.disconnect();
    }

    @Override
    public void onConnect()
    {
      Log.d(LOG_TAG, "onConnect()");
    }
  };
}
