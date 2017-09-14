package com.vitalsigns.demoholter;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Walter_Chiu on 2017/8/1.
 */

public class ChartManagement
{
  private final String LOG_TAG = "ChartManagement:";
  private Context                  mContext;
  private ArrayList<ChartFragment> chartFragmentArray;
  private FragmentManager          fm;
  private FragmentTransaction      ft;
  private int                      chartCount;
  private String[]                 threeChartDataRangeString;

  public ChartManagement(Context context, FragmentManager fm, int chartDataRangeCount) {
    this.mContext = context;
    this.fm = fm;
    chartFragmentArray = new ArrayList<>(GlobalData.MAX_CHANNEL_NUMBER);
    threeChartDataRangeString = new String[chartDataRangeCount];
  }

  public void addFragment(int[] container,
                          String[] unitArray,
                          int[] gainArray,
                          int[] offsetArray,
                          int[] vrefArray,
                          String[] titleArray) {
    /// add fragment by chart number and resized layout
    ChartFragment fragment;

    chartCount = container.length;
    /// Init fragment array
    chartFragmentArray.clear();
    /// Set argument to fragment
    Bundle args;
    /// Add fragment
    for(int idx = 0; idx < container.length; idx++) {
      args = new Bundle();
      args.putString(mContext.getResources().getString(R.string.chart_fragment_display_type),
                     mContext.getResources().getString(R.string.chart_fragment_display_type_normal));
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_channel_count), container.length);
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_channel_number), idx + 1);
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_chart_number), idx);
      args.putString(mContext.getResources().getString(R.string.chart_fragment_y_axis_unit),
                     unitArray[idx]);
      args.putString(mContext.getResources().getString(R.string.chart_fragment_title),
                     titleArray[idx]);
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_voltage_gain),
                  gainArray[idx]);
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_voltage_offset),
                  offsetArray[idx]);
      args.putInt(mContext.getResources().getString(R.string.chart_fragment_voltage_vref),
                  vrefArray[idx]);
      fragment = new ChartFragment();
      fragment.setArguments(args);
      ft = fm.beginTransaction();
      ft.replace(container[idx],
                 fragment,
                 mContext.getResources().getString(R.string.chart_fragment_tag) + String.valueOf(idx + 1));
      ft.addToBackStack(null);
      ft.commitAllowingStateLoss();
      chartFragmentArray.add(fragment);
    }
  }

  public void removeFragment() {
    if(chartFragmentArray.size() > 0) {
      for (int idx = 1; idx <= chartCount; idx++) {
        ft = fm.beginTransaction();
        ft.remove(chartFragmentArray.get(idx - 1)).commitAllowingStateLoss();
      }
      chartFragmentArray.clear();
    }
  }

  public void startToDrawChart(int sampleRate) {
    for(int idx = 0; idx < chartCount; idx++) {
      chartFragmentArray.get(idx).startRecord(sampleRate);
    }
  }

  public void stopToDrawChart(float fEndTime) {
    for(int idx = 0; idx < chartCount; idx++) {
      chartFragmentArray.get(idx).stopRecord(fEndTime);
    }
  }

  public String[] drawChart(float[][] data, float fEndTime) {
    String[] dataRangeArray;
    int dataRangeArrayIndex = 0;
    for(int idx = 0; idx < chartCount; idx++) {

      dataRangeArray = chartFragmentArray.get(idx).drawChart(data[idx], fEndTime);

      System.arraycopy(dataRangeArray, 0, threeChartDataRangeString, dataRangeArrayIndex, 3);
      dataRangeArrayIndex += 3;
    }
    return threeChartDataRangeString;
  }

  public float[] getXWindowRange() {
    float[] xWindowRange = new float[chartCount];
    for(int idx = 0; idx < chartCount; idx++) {
      xWindowRange[idx] = chartFragmentArray.get(idx).getXWindowRange();
    }
    return xWindowRange;
  }
}
