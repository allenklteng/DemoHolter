package com.vitalsigns.demoholter;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by WalterChiu on 2017/7/31.
 */

public class ChartFragment extends Fragment
{
  private final String LOG_TAG = "ChartFragment:";
  private ChartView chartView;
  private final float ECG_GRID_BOX_VOLTAGE_UNIT = 0.5f; /// mV
  private final int Y_LABEL_COUNT = 10, X_LABEL_COUNT = 3;

  private String chartTitle;
  private String chartTitleArg;
  private int channelNumber;
  private int gain, offset, vref;
  private int chartCount;
  private int chartNumber;
  private String unitString;
  private boolean bYAxisUnitVoltage;
  private String[] unitArray;
  private float ecgVoltageRange;
  private String defaultValueString;
  private String[] dataRangeString;
  private int xLabelTextSize;
  private int yLabelTextSize;
  private int chartTitleTextSize;
  private float maxY;
  private float minY;

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
}
