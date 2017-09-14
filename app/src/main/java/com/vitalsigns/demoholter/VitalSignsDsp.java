package com.vitalsigns.demoholter;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;


import com.vitalsigns.sdk.dsp.holter.Dsp;
import com.vitalsigns.sdk.utility.*;
import com.vitalsigns.sdk.utility.Utility;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.vitalsigns.sdk.dsp.bp.Constant.DEV_TYPE_BLE_HEARTRATETAPE;
import static com.vitalsigns.sdk.dsp.bp.Constant.DEV_TYPE_BLE_WATCH;

/**
 * Created by Walter_Chiu on 2017/8/14.
 */

public class VitalSignsDsp
{
  private static final String LOG_TAG = "VitalSignsDsp";
  private Dsp DSP = null;
  private HandlerThread captureBleDataThread;
  private Handler captureBleDataThreadHandler;
  private Handler waitDspExecuteCompleteHandler;
  private static final int CAPTURE_BLE_INIT_TIME = 10;
  private static final int CAPTURE_BLE_SLEEP_TIME = 5;
  private final static int WAIT_DSP_EXECUTE_COMPLETE_INIT_DELAY = 100;
  private final static int WAIT_DSP_EXECUTE_COMPLETE_DELAY = 10;
  private boolean RunCapture = false;
  private boolean Capturing = false;

  private int iSamplePerSec;
  private short sResolution;
  private int iDevPttCH;
  private BlockingQueue<Integer> bleIntDataQueue;
  private ArrayList<RingBuffer<Integer>> rawDataArrayList;
  private int dspDataOutDataCnt = 0;
  private float dspEndTimeMSec = 0;
  public final int MAX_DATA_SECOND = 600; /// 10 minutes
  public int iDataBufferSize;

  public VitalSignsDsp(Context context)
  {
    DSP = new Dsp((Activity)context, context.getString(R.string.package_identity));
    bleIntDataQueue = new ArrayBlockingQueue<Integer>(512);
    waitDspExecuteCompleteHandler = new Handler();
    rawDataArrayList = new ArrayList<>(GlobalData.MAX_CHANNEL_NUMBER);
    RunCapture = false;
  }

  public boolean Start(float highPassFilterCutOffFreq,
                       float lowPassFilterCutOffFreq,
                       boolean bNotch50Hz,
                       boolean bNotch60Hz,
                       int [] nGains,
                       int [] nVrefs,
                       String strMacAddr)
  {
    /// [AT-PM] : Check DSP is available ; 10/25/2016
    if(!DSP.IsJniAvailable())
    {
      Log.d(LOG_TAG, "DSP.IsJniAvailable() == false");
      return (false);
    }

    dspEndTimeMSec = 0;
    iSamplePerSec = GlobalData.BleControl.sampleRate();
    sResolution = GlobalData.BleControl.resolution();
    iDevPttCH = GlobalData.BleControl.channelCount();
    if(!jniStart(highPassFilterCutOffFreq, lowPassFilterCutOffFreq, bNotch50Hz, bNotch60Hz, nGains, nVrefs, strMacAddr))
    {
      Log.d(LOG_TAG, "jniStart() == false");
      return (false);
    }

    /// Clear array list
    bleIntDataQueue.clear();
    rawDataArrayList.clear();
    /// Alloc buffer size
    iDataBufferSize = MAX_DATA_SECOND * GlobalData.BleControl.sampleRate();

    for(int idx = 0; idx < iDevPttCH; idx++) {
      rawDataArrayList.add(new RingBuffer<Integer>(iDataBufferSize));
    }

    captureBleDataThread = new HandlerThread("Capture BLE Data Thread", Process.THREAD_PRIORITY_BACKGROUND);
    captureBleDataThread.start();
    captureBleDataThreadHandler = new Handler(captureBleDataThread.getLooper());
    captureBleDataThreadHandler.postDelayed(captureBleDataThreadRunnable, CAPTURE_BLE_INIT_TIME);
    RunCapture = true;

    /// [AT-PM] : Start BLE ; 10/25/2016
    GlobalData.BleControl.start();

    Log.d(LOG_TAG, "START");
    return (true);
  }

  public void Stop()
  {

    /// [AT-PM] : Stop the thread ; 10/25/2016
    RunCapture = false;

    if(captureBleDataThread != null) {
      HandlerThread moribund = captureBleDataThread;
      captureBleDataThread = null;
      moribund.interrupt();
      moribund.quit();
    }

    /// [AT-PM] : Stop BLE ; 10/25/2016
    GlobalData.BleControl.stop(new VitalSignsBle.BleStop()
    {
      @Override
      public void onStop()
      {
        /// [AT-PM] : Stop DSP ; 10/25/2016
        DSP.Stop();

        waitDspExecuteCompleteHandler.postDelayed(waitDspExecuteCompleteRunnable, WAIT_DSP_EXECUTE_COMPLETE_INIT_DELAY);
        Log.d(LOG_TAG , "CAPTURE THREAD =====> STOPPED");
      }
    });
  }

  /**
   * @brief jniStart
   *
   * startCapture JNI
   *
   * @return true if success
   */
  private boolean jniStart(float highPassFilterCutOffFreq,
                           float lowPassFilterCutOffFreq,
                           boolean bNotch50Hz,
                           boolean bNotch60Hz,
                           int [] nGains,
                           int [] nVrefs,
                           String strMacAddr)
  {
    if(highPassFilterCutOffFreq != 0) {
      DSP.SetHighPassFilter(true, highPassFilterCutOffFreq);
    } else{
      DSP.SetHighPassFilter(false, highPassFilterCutOffFreq);
    }
    if(lowPassFilterCutOffFreq != 0) {
      DSP.SetLowPassFilter(true, lowPassFilterCutOffFreq);
    } else {
      DSP.SetLowPassFilter(false, lowPassFilterCutOffFreq);
    }
    DSP.SetNotch50HzFilter(bNotch50Hz);
    DSP.SetNotch60HzFilter(bNotch60Hz);
    DSP.Start(iSamplePerSec, sResolution, nGains, nVrefs, strMacAddr);
    return (true);
  }

  final Runnable captureBleDataThreadRunnable = new Runnable() {
    @Override
    public void run() {
      while(RunCapture) {
        getBleIntData();
      }
    }};

  private void getBleIntData() {
    int[] dataArray;

    if(!GlobalData.mBleIntDataQueue.isEmpty()) {
      dataArray = GlobalData.mBleIntDataQueue.poll();
      for (int data : dataArray) {
        while(!bleIntDataQueue.offer(data));
      }
      Capturing = true;
      processBleData();
      Capturing = false;
    } else {
      try {
        Thread.currentThread().sleep(CAPTURE_BLE_SLEEP_TIME);
      } catch (InterruptedException e){
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * @brief processBleData
   *
   * Process data from BLE device
   *
   */
  private void processBleData() {
    int tempData;
    float fRawLead1 = 0;
    float fRawLead2 = 0;
    int iDataIdx = 0;
    int [] nRawData = new int[2];

    int size = bleIntDataQueue.size();
    if (size >= iDevPttCH) {
      size = size - (size % iDevPttCH);
      while (iDataIdx < size) {
        for (int iChannelIdx = 0; iChannelIdx < iDevPttCH; iChannelIdx++) {
          tempData = bleIntDataQueue.poll();
          rawDataArrayList.get(iChannelIdx).add(tempData);
          /// [AT-PM] : Prepare raw code for DSP ; 09/29/2016
          if (iChannelIdx == 0) {
            fRawLead1 = (float) tempData;
          }
          if (iChannelIdx == 1) {
            fRawLead2 = (float) tempData;
          }
        }

        /// [AT-PM] : Execute DSP ; 09/29/2016
        dspEndTimeMSec = DSP.Execute(fRawLead1, fRawLead2) * 1000;
        iDataIdx = iDataIdx + iDevPttCH;
      }
    }
  }

  /**
   * @brief PrepareJniData
   *
   * Prepare JNI data
   *
   * @param fStartTime start time in ms
   * @param fEndTime end time in ms
   * @param nChart1LeadType chart 1 lead type
   * @param nChart2LeadType chart 2 lead type
   * @param nChart3LeadType chart 3 lead type
   * @param nChart1LineType  prepare line type to draw on chart 1
   * @param nChart2LineType  prepare line type to draw on chart 2
   * @param nChart3LineType  prepare line type to draw on chart 3
   * @return data count
   */
  public int PrepareJniData(float fStartTime,
                            float fEndTime,
                            int nChart1LeadType,
                            int nChart2LeadType,
                            int nChart3LeadType,
                            int nChart1LineType,
                            int nChart2LineType,
                            int nChart3LineType)
  {
    dspDataOutDataCnt = DSP.ReadData(fStartTime,
                                     fEndTime,
                                     nChart1LeadType,
                                     nChart2LeadType,
                                     nChart3LeadType,
                                     nChart1LineType,
                                     nChart2LineType,
                                     nChart3LineType);
    return (dspDataOutDataCnt);
  }

  /**
   * @brief GetDrawLead1
   *
   * Get data for drawing lead 1
   *
   * @return lead 1 code
   */
  public float [] GetDrawLead1()
  {
    float [] floats = new float[dspDataOutDataCnt];
    int nIdx;

    if(dspDataOutDataCnt == 0)
    {
      return (null);
    }

    nIdx = 0;
    while(nIdx < floats.length)
    {
      floats[nIdx] = DSP.GetLead1Y(nIdx);
      nIdx ++;
    }
    return (floats);
  }

  /**
   * @brief GetDrawLead2
   *
   * Get data for drawing lead 2
   *
   * @return lead 2 code
   */
  public float [] GetDrawLead2()
  {
    float [] floats = new float[dspDataOutDataCnt];
    int nIdx;

    if(dspDataOutDataCnt == 0)
    {
      return (null);
    }

    nIdx = 0;
    while(nIdx < floats.length)
    {
      floats[nIdx] = DSP.GetLead2Y(nIdx);
      nIdx ++;
    }
    return (floats);
  }

  /**
   * @brief GetDrawLead3
   *
   * Get data for drawing lead 3
   *
   * @return lead 3 code
   */
  public float [] GetDrawLead3()
  {
    float [] floats = new float[dspDataOutDataCnt];
    int nIdx;

    if(dspDataOutDataCnt == 0)
    {
      return (null);
    }

    nIdx = 0;
    while(nIdx < floats.length)
    {
      floats[nIdx] = DSP.GetLead3Y(nIdx);
      nIdx ++;
    }
    return (floats);
  }

  private final Runnable waitDspExecuteCompleteRunnable = new Runnable() {
    @Override
    public void run() {
      if(Capturing) {
        waitDspExecuteCompleteHandler.postDelayed(waitDspExecuteCompleteRunnable, WAIT_DSP_EXECUTE_COMPLETE_DELAY);
        Log.d(LOG_TAG,"Wait for the processBleData() stopped");
      } else {
        DSP.Stop();
      }
    }
  };

  public float GetHeartRateAvg()
  {
    return DSP.GetHeartRateAvg();
  }

  public int GetOutputRate(){
    return DSP.GetOutputRate();
  }

  public float GetAccumulateTimeInMilliSecond() {
    return (dspEndTimeMSec);
  }

}
