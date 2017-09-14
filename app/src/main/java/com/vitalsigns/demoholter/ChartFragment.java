package com.vitalsigns.demoholter;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.text.DecimalFormat;

/**
 * Created by WalterChiu on 2017/7/31.
 */

public class ChartFragment extends Fragment
{
  private final String LOG_TAG = "ChartFragment:";
  private ChartView chartView;
  private final float         ECG_GRID_BOX_VOLTAGE_UNIT = 0.5f; /// mV
  private final int           Y_LABEL_COUNT             = 10;
  private final int           X_LABEL_COUNT             = 3;
  private final float         MILLISECOND_TO_SECOND     = 1000;
  private final double        maxValue                  = Math.pow(2, 23);
  private final DecimalFormat df                        = new DecimalFormat("#.###");
  private final int           MAX_VALUE_POSITION        = 0;
  private final int           MIN_VALUE_POSITION        = 1;
  private final int           AVG_VALUE_POSITION        = 2;

  private String chartTitle;
  private String chartTitleArg;
  private int channelNumber;
  private float xWindowRange;
  private int gain, offset, vref;
  private int chartCount;
  private int chartNumber;
  private String unitString;
  private String[] unitArray;
  private float ecgVoltageRange;
  private String defaultValueString;
  private String[] dataRangeString;
  private int xLabelTextSize;
  private int yLabelTextSize;
  private int chartTitleTextSize;
  private int maxChartPoint;
  private float[] yValueArray;
  private float maxY;
  private float minY;
  private float maxX;
  private boolean bGetDataFail = false;
  private boolean bRecording = false;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState)
  {
    Bundle arg = getArguments();
    if(arg != null) {
      dispatchBindle(arg);
    }else{
      channelNumber = 1;
      gain = 1;
      offset = 0;
      vref = GlobalData.DEFAULT_VREF;
      chartTitle = "chart";
    }
    super.onCreate(savedInstanceState);
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    View view = inflater.inflate(R.layout.fragment_chart, container, false);
    initVariable();
    processViews(view);
    processControls();
    return view;
  }

  private void dispatchBindle(Bundle bundle) {
    chartTitleArg = bundle.getString(getActivity().getResources().getString(R.string.chart_fragment_title));
    channelNumber = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_channel_number));
    chartNumber = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_chart_number));
    chartCount = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_channel_count));
    unitString = bundle.getString(getActivity().getResources().getString(R.string.chart_fragment_y_axis_unit));
    chartTitle = chartTitleArg + " ( " + unitString + " )";
    gain = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_voltage_gain));
    offset = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_voltage_offset));
    vref = bundle.getInt(getActivity().getResources().getString(R.string.chart_fragment_voltage_vref));
    Log.d(LOG_TAG , " title = " + chartTitle );
    Log.d(LOG_TAG , " gain/offset/vref = " + gain + "  / " + offset + " / " + vref);
  }

  private void initVariable()
  {
    ecgVoltageRange = Y_LABEL_COUNT * ECG_GRID_BOX_VOLTAGE_UNIT;
    minY = 0;
    maxY =  minY + ecgVoltageRange;
  }

  private void processViews(View view) {
    chartView = (ChartView)view.findViewById(R.id.chart_view);
    xLabelTextSize = view.getResources().getDimensionPixelSize(R.dimen.chart_x_label_text_size_normal);
    yLabelTextSize = view.getResources().getDimensionPixelSize(R.dimen.chart_y_label_text_size_normal);
    chartTitleTextSize = view.getResources().getDimensionPixelSize(R.dimen.chart_title_text_size_normal);
    defaultValueString = view.getResources().getString(R.string.info_default_value);
  }

  private void processControls() {
    showChart();
    // TODO : setListener();
    dataRangeString = new String[] {defaultValueString, defaultValueString, defaultValueString};
  }

  private void showChart() {
    chartView.setLabelCount(X_LABEL_COUNT, Y_LABEL_COUNT);
    chartView.setupChartView(
      chartTitle, chartNumber, chartCount, unitString, minY, maxY);
    chartView.setTitleTextSize(chartTitleTextSize);
    chartView.setLabelTextSize(xLabelTextSize, yLabelTextSize);
    chartView.invalidate();
  }

  public void startRecord(int iSampleRate) {
    bGetDataFail = false;
    bRecording = true;
    chartView.setSampleRate(iSampleRate);
    maxChartPoint = (int)Math.ceil(iSampleRate * chartView.getXVWindow());
    yValueArray = new float[maxChartPoint];
  }

  public void stopRecord(float accumulateTime) {
    bRecording = false;
    //TODO:PanLimit
    //setPanLimits(accumulateTime / MILLISECOND_TO_SECOND);
  }

  public String[] drawChart(float[] data, float fEndTime) {
    updateChartXAxisStartEndTime(fEndTime);
    setDataToChart(data);
    updateChart();
    return dataRangeString;
  }

  private void updateChartXAxisStartEndTime(float fEndTimeInMilliSecond) {
    float minX;
    if(fEndTimeInMilliSecond < xWindowRange *  MILLISECOND_TO_SECOND) {
      maxX = xWindowRange * MILLISECOND_TO_SECOND;
      minX = 0;
    } else {
      maxX = fEndTimeInMilliSecond;
      minX = maxX - xWindowRange * MILLISECOND_TO_SECOND;
    }
    chartView.setXRange(minX / MILLISECOND_TO_SECOND, maxX / MILLISECOND_TO_SECOND);
  }

  private void setDataToChart(float[] data) {
    float newYValue;
    double fValAvg = 0;
    float fMaxPeakY = -Float.MAX_VALUE;
    float fMinPeakY = Float.MAX_VALUE;
    int dataLength;

    dataLength = Math.min(data.length, maxChartPoint);
    for (int idx = 0; idx < dataLength; idx++) {
      newYValue = data[idx];
      newYValue = convertAdcCodeToVoltage(newYValue);
      yValueArray[idx] = newYValue;
      fValAvg += newYValue;
      if (newYValue > fMaxPeakY) {
        fMaxPeakY = newYValue;
      }
      if (newYValue < fMinPeakY) {
        fMinPeakY = newYValue;
      }
    }
    fValAvg = fValAvg / dataLength;
    calculateYRange(fMaxPeakY, fMinPeakY);
    chartView.setYRange(minY, maxY);
    chartView.setDataSource(yValueArray, dataLength);
    /// Set max, min and avg value to text
    if((fMaxPeakY < maxValue) && (fMaxPeakY > -maxValue)) {
      dataRangeString[MAX_VALUE_POSITION] = (df.format(fMaxPeakY));
    }else{
      dataRangeString[MAX_VALUE_POSITION] = defaultValueString;
    }
    if((fMinPeakY < maxValue) && (fMinPeakY > -maxValue)) {
      dataRangeString[MIN_VALUE_POSITION] = df.format(fMinPeakY);
    }else{
      dataRangeString[MIN_VALUE_POSITION] = defaultValueString;
    }
    if((fValAvg < maxValue) && (fValAvg > -maxValue)) {
      dataRangeString[AVG_VALUE_POSITION] = df.format(fValAvg);
    }else{
      dataRangeString[AVG_VALUE_POSITION] = defaultValueString;
    }
  }

  private float convertAdcCodeToVoltage(float adcCode) {
    if(gain != 0) {
      return (vref * adcCode / (1 << 23) / gain + offset);
    }else{
      return (vref * adcCode / (1 << 23) + offset);
    }
  }

  private void calculateYRange(float fMaxPeakY, float fMinPeakY) {
    float ecgChartYShift;
    float dataRange = fMaxPeakY - fMinPeakY;
    if(dataRange != 0) {
      ecgChartYShift = dataRange / Y_LABEL_COUNT;
    }
    else
    {
      ecgChartYShift = fMaxPeakY / Y_LABEL_COUNT;
    }
    maxY = fMaxPeakY + ecgChartYShift;
    minY = fMinPeakY - ecgChartYShift;
  }

  private void updateChart() {
    if(getActivity() == null) {
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      public void run() {
        chartView.invalidate();
      }
    });

  }

  public float getXWindowRange() {
    xWindowRange = chartView.getXVWindow();
    Log.d(LOG_TAG , "xWindowRange = " + xWindowRange);
    return xWindowRange;
  }
}
