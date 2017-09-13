package com.vitalsigns.demoholter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v4.content.ContextCompat;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Created by WalterChiu on 2017/7/31.
 */

public class ChartView extends View
{
  private final String LOG_TAG = "ChartView:";
  private Context mContext;
  /// Painting tools
  private Paint axisPaint;
  private Paint curvePaint;
  private Paint PeakPaint;
  private Paint backgroundPaint;
  private Paint framePaint;
  private TextPaint unitTextPaint;
  private TextPaint titleTextPaint;
  private TextPaint xLabelTextPaint;
  private TextPaint yLabelTextPaint;
  private static final NavigableMap<Double, String> suffixesDouble = new TreeMap<>();
  private DecimalFormat decimalFormat;
  private static final int DRAW_CIRCLE_RADIUS = 6;
  private final float MILLISECOND_TO_SECOND = 1000;
  private final float verticalGridUnit = 40f; /// 0.04 seconds / 1mm
  private final float horizontalGridUnit = 0.1f; /// 1mV /1mm

  private float xWindow;
  private int xLabelCount = 3;
  private int yLabelCount = 8;
  private float xLabelValueInterval;
  private float yLabelValueInterval;
  private int xLabelCoordinateInterval;
  private int yLabelCoordinateInterval;
  private float xGridCoordinateInterval;
  private float yGridCoordinateInterval;
  private float horizontalGridCount; /// mV/horizintalLine
  private float verticalGridCount; /// 40ms/verticalLine
  private int labelTextShift;
  private float minX = 0;
  private float maxX = 0;
  private float minY = 0;
  private float maxY = 0;
  private String titleText = "chart";
  private String unitText = "millivolt";
  private int chartNumber;
  private int chartCount;
  private boolean bIntialDrawing;
  private OnDrawChartFinishListener mCallback;
  private Bitmap cacheBitmap;
  private Bitmap background;

  /// Represent the borders of the View
  private int mTopSide;
  private int mLeftSide;
  private int mRightSide;
  private int mBottomSide;
  private int mMiddleX;

  /// Size of a DensityIndependentPixel
  private float mDips = 0;
  private int mTotalYInPixel;
  private float validPointDistanceThreahold;
  private final float validPointDistanceThreaholdRadio = 500;
  private final float minValidPointDistanceThreahold = 1;

  /// Hold the position of the axis in regard to the range of values
  private int positionOfX;
  private int positionOfY;

  /// Index for the graph array window, and size of the window
  private float[] xAxisData;
  private float[] yAxisData;
  private float[] ptsData;
  private float[] ptsData2;
  private float[] ptsData3;
  private float[] savePtsData;
  private int dataLength;
  private float[] fPeakXArray;
  private float[] fPeakYArray;
  private int nPeakCnt;

  public interface OnDrawChartFinishListener{
    public void drawChartFinish();
  }

  public ChartView(Context context)
  {
    super(context);
    if (isInEditMode()) {
      return;
    }
    this.mContext = context;
    mCallback = (OnDrawChartFinishListener)mContext;
    init();
  }

  public ChartView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    if (isInEditMode()) {
      return;
    }
    this.mContext = context;
    mCallback = (OnDrawChartFinishListener)mContext;
    init();
  }

  public ChartView(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
    if (isInEditMode()) {
      return;
    }
    this.mContext = context;
    mCallback = (OnDrawChartFinishListener)mContext;
    init();
  }

  public ChartView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
  {
    super(context, attrs, defStyleAttr, defStyleRes);
    if (isInEditMode()) {
      return;
    }
    this.mContext = context;
    mCallback = (OnDrawChartFinishListener)mContext;
    init();
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
    if(!bIntialDrawing) {

      cacheBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_4444);
      Canvas cacheCanvasWithoutGrid = new Canvas(cacheBitmap);
      doInitialDrawing(cacheCanvasWithoutGrid);
      bIntialDrawing = true;
    }

    canvas.drawBitmap(cacheBitmap, 0, 0, backgroundPaint);
    doPartialDrawing(canvas);
    /// Last chart redraw finish
    if(chartNumber == chartCount - 1) {
      mCallback.drawChartFinish();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh)
  {
    super.onSizeChanged(w, h, oldw, oldh);
    if(mContext != null)
    {
      initConstants();
    }
    try{
      initWindowSetting();
    } catch (IllegalArgumentException e){
      Log.e(LOG_TAG,"Could not bleInitialize windows." + e.toString());
      return;
    }
  }

  private void init() {
    initDrawingTools();
    /// Format setting
    suffixesDouble.put(1000.0, "k");
    suffixesDouble.put(1000000.0, "M");
    suffixesDouble.put(1000000000.0, "G");
  }

  private void doInitialDrawing(Canvas canvas) {
    /// draw background
    drawChartBackground(canvas);
    drawXAxis(canvas);
    drawYAxis(canvas);
    drawXLabelLine(canvas);
    drawYLabelLine(canvas);
    drawTitle(canvas);
  }

  private void doPartialDrawing(Canvas canvas) {
    drawYLabel(canvas);
    drawXLabel(canvas);
    if(dataLength > 1) {
      if(ptsData != null) {
          drawCurveByCanvasDrawLine(canvas);
      }

      if(fPeakXArray != null && nPeakCnt > 0) {
        drawPeakByCanvasDrawPoint(canvas);
      }
    }
  }

  private void regenerateBackground(){
    if(background != null){
      background.recycle();
    }
    background = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
    Canvas backgroundCanvas = new Canvas(background);

    drawChartBackground(backgroundCanvas);
    drawXAxis(backgroundCanvas);
    drawYAxis(backgroundCanvas);
    drawTitle(backgroundCanvas);
    drawXLabel(backgroundCanvas);
    drawYLabel(backgroundCanvas);

  }

  private void initDrawingTools() {

    axisPaint = new Paint();
    axisPaint.setColor(ContextCompat.getColor(mContext,R.color.chart_grid_color));
    axisPaint.setStrokeWidth(5f*mDips);
    axisPaint.setAlpha(0xff);
    axisPaint.setAntiAlias(true);

    curvePaint = new Paint();
    curvePaint.setColor(ContextCompat.getColor(mContext,R.color.chart_line));
    curvePaint.setStrokeWidth(3);
    curvePaint.setDither(true);
    curvePaint.setStyle(Paint.Style.STROKE);
    curvePaint.setStrokeJoin(Paint.Join.ROUND);
    curvePaint.setStrokeCap(Paint.Cap.ROUND);
    curvePaint.setPathEffect(new CornerPathEffect(10));
    curvePaint.setAntiAlias(true);

    PeakPaint = new Paint();
    PeakPaint.setColor(ContextCompat.getColor(mContext,R.color.chart_peak));
    PeakPaint.setStyle(Paint.Style.FILL);
    PeakPaint.setStrokeWidth(5);


    backgroundPaint = new Paint();
    backgroundPaint.setFilterBitmap(true);
    backgroundPaint.setColor(ContextCompat.getColor(mContext,R.color.chart_background));

    titleTextPaint = new TextPaint();
    titleTextPaint.setAntiAlias(true);
    titleTextPaint.setColor(ContextCompat.getColor(mContext,R.color.primary_text));
    titleTextPaint.setTextAlign(Paint.Align.CENTER);
    titleTextPaint.setTextSize(18);
    titleTextPaint.setTypeface(Typeface.MONOSPACE);

    unitTextPaint = new TextPaint();
    unitTextPaint.setAntiAlias(false);
    unitTextPaint.setColor(ContextCompat.getColor(mContext,R.color.primary_text));
    unitTextPaint.setTextAlign(Paint.Align.CENTER);
    unitTextPaint.setTextSize(18);
    unitTextPaint.setTypeface(Typeface.MONOSPACE);

    xLabelTextPaint = new TextPaint();
    xLabelTextPaint.setAntiAlias(true);
    xLabelTextPaint.setColor(ContextCompat.getColor(mContext,R.color.secondary_text));
    xLabelTextPaint.setTextAlign(Paint.Align.CENTER);
    xLabelTextPaint.setTextSize(18);
    xLabelTextPaint.setTypeface(Typeface.MONOSPACE);
    yLabelTextPaint = new TextPaint();
    yLabelTextPaint.setAntiAlias(true);
    yLabelTextPaint.setColor(ContextCompat.getColor(mContext,R.color.secondary_text));
    yLabelTextPaint.setTextAlign(Paint.Align.RIGHT);
    yLabelTextPaint.setTextSize(15);
    yLabelTextPaint.setTypeface(Typeface.MONOSPACE);
    labelTextShift = 3 * (int)xLabelTextPaint.getTextSize();
  }

  private void initConstants() {
    int windowWidth, windowHeight;

    windowWidth = getMeasuredWidth();
    windowHeight = getMeasuredHeight();
    mDips = mContext.getResources().getDisplayMetrics().density;
    mTopSide = (int) (getTop() + 2 * titleTextPaint.getTextSize());
    mLeftSide = (int) (getLeft() + 10*mDips + 1.5f * labelTextShift);
    mRightSide = (int) (windowWidth - labelTextShift);
    mBottomSide = (int) (windowHeight - 1.0f * labelTextShift);
    mTotalYInPixel = mBottomSide - mTopSide;
    validPointDistanceThreahold =
      Math.min(mTotalYInPixel / validPointDistanceThreaholdRadio, minValidPointDistanceThreahold);
    mMiddleX = (mRightSide - mLeftSide)/2 + mLeftSide;
    xLabelCoordinateInterval = (mRightSide - mLeftSide) / xLabelCount;
    yLabelCoordinateInterval = (mBottomSide - mTopSide) / yLabelCount;
    Log.d(LOG_TAG,
          "mDips: " + mDips +
          " top: " + mTopSide +
          " bottom: " + mBottomSide +
          " left: " + mLeftSide +
          " right: " + mRightSide +
          " midX: " + mMiddleX +
          " validPointDistanceThreahold = " + validPointDistanceThreahold);
    calculateGridParameter();
  }

  private void calculateGridParameter() {
    horizontalGridCount = (maxY - minY) / horizontalGridUnit;
    yGridCoordinateInterval = (mBottomSide - mTopSide) / horizontalGridCount;
    xGridCoordinateInterval = yGridCoordinateInterval; /// square grid box
    verticalGridCount = (mRightSide - mLeftSide) / xGridCoordinateInterval;
    xWindow = verticalGridCount * verticalGridUnit / MILLISECOND_TO_SECOND;
    this.minX = 0;
    this.maxX = xWindow;
    xLabelValueInterval = xWindow / xLabelCount;
  }

  private void initWindowSetting() throws IllegalArgumentException {
    if(maxX < minX || maxY < minY ||
       maxX == minX || maxY == minY){
      throw new IllegalArgumentException("Max and min values make no sense");
    }
    float[][] maxAndMin = new float[][] {{minX, maxX}, {minY, maxY}};
    int[] positions = new int[] {positionOfY, positionOfX};

    /// Place the X and Y axis in regard to the given max and min
    for(int i = 0; i < 2; i++){
      if(maxAndMin[i][0] < 0f){
        if(maxAndMin[i][1] < 0f){
          positions[i] = (int) maxAndMin[i][0];
        } else{
          positions[i] = 0;
        }
      } else if (maxAndMin[i][0] > 0f){
        positions[i] = (int) maxAndMin[i][0];
      } else {
        positions[i] = 0;
      }
    }

    // Put the values back in their right place
    minX = maxAndMin[0][0];
    maxX = maxAndMin[0][1];
    minY = maxAndMin[1][0];
    maxY = maxAndMin[1][1];

    positionOfY = mLeftSide +  (int) (((positions[0] - minX)/(maxX-minX))*(mRightSide - mLeftSide));
    positionOfX = mBottomSide - (int) (((positions[1] - minY)/(maxY-minY))*(mBottomSide - mTopSide));
    Log.d(LOG_TAG,"positionOfX = " + positionOfX + " positionOfY = " + positionOfY);
  }

  public void setupChartView(String chartTitle,
                             int chartNumber,
                             int chartCount,
                             String unit,
                             float minY,
                             float maxY) {
    titleText = chartTitle;
    this.chartNumber = chartNumber;
    this.chartCount = chartCount;
    unitText = unit;
    this.minY = minY;
    this.maxY = maxY;
    yLabelValueInterval = (maxY - minY) / yLabelCount;
  }

  public void setTitleTextSize(int textSize) {
    titleTextPaint.setTextSize(textSize);
    unitTextPaint.setTextSize(textSize - 1);
  }

  public void setLabelTextSize(int xLabelTextSize, int yLabelTextSize) {
    xLabelTextPaint.setTextSize(xLabelTextSize);
    yLabelTextPaint.setTextSize(yLabelTextSize);
    labelTextShift = 3 * (int)xLabelTextPaint.getTextSize();
  }

  public void setLabelCount(int xLabelCount, int yLabelCount) {
    this.xLabelCount = xLabelCount;
    this.yLabelCount = yLabelCount;
    xLabelCoordinateInterval = (mRightSide - mLeftSide) / xLabelCount;
    yLabelCoordinateInterval = (mBottomSide - mTopSide) / yLabelCount;
  }

  public void setTitleTextTypeface(Typeface typeface) {
    titleTextPaint.setTypeface(typeface);
  }

  private String formatYValue(double value) {
    Map.Entry<Double, String> e = suffixesDouble.floorEntry(value);
    decimalFormat = new DecimalFormat("0.000");

    if ((value < 1000) && (value > -1000)) {
      ///< deal with easy case
      return decimalFormat.format(value);
    }
    if (value < 0) {
      return "-" + formatYValue(-value);
    }
    double divideBy = e.getKey();
    String suffix = e.getValue();

    return decimalFormat.format(value / divideBy) + suffix;
  }

  /// *********** Set during drawing end ***************
  private void drawChartBackground(Canvas canvas) {
    canvas.drawRect(mLeftSide, mTopSide, mRightSide, mBottomSide, backgroundPaint);
  }

  private void drawBackground(Canvas canvas){
    regenerateBackground();
    canvas.drawBitmap(background, 0, 0, backgroundPaint);
  }

  private void drawXAxis(Canvas canvas){
    canvas.drawLine(mLeftSide, positionOfX, mRightSide, positionOfX, axisPaint);
  }

  private void drawYAxis(Canvas canvas){
    canvas.drawLine(positionOfY, mTopSide, positionOfY, mBottomSide, axisPaint);
  }

  private void drawTitle(Canvas canvas){
    canvas.drawText(titleText, mMiddleX, titleTextPaint.getTextSize(), titleTextPaint);
  }

  private void drawXLabel(Canvas canvas) {
    float xPos, yPos;
    String labelText;
    yPos = positionOfX + 1.5f * xLabelTextPaint.getTextSize();
    /// draw x label
    for(int posIdx = 0; posIdx <= xLabelCount; posIdx++) {
      xPos = mLeftSide + posIdx * xLabelCoordinateInterval;
      labelText = String.format("%.1f", (minX + posIdx * xLabelValueInterval));
      canvas.drawText(labelText,
                      xPos,
                      yPos,
                      xLabelTextPaint);
    }
  }

  private void drawXLabelLine(Canvas canvas) {
    float xPos, yPos;
    String labelText;
    yPos = positionOfX + 1.5f * xLabelTextPaint.getTextSize();
    /// draw x label
    for(int posIdx = 1; posIdx <= xLabelCount; posIdx++) {
      xPos = mLeftSide + posIdx * xLabelCoordinateInterval;
      canvas.drawLine(
        xPos,
        yPos - (int) (1.5 * xLabelTextPaint.getTextSize()),
        xPos,
        yPos - (int) (0.9 * xLabelTextPaint.getTextSize()),
        axisPaint);
    }
  }

  private void drawYLabel(Canvas canvas) {
    float xPos, yPos;
    String labelText;
    /// draw y label
    xPos = positionOfY - yLabelTextPaint.getTextSize();
    for(int posIdx = 1; posIdx < yLabelCount; posIdx++) {
      yPos = mBottomSide - posIdx * yLabelCoordinateInterval + yLabelTextPaint.descent();
      labelText = formatYValue(minY + posIdx * yLabelValueInterval);
      canvas.drawText(labelText,
                      xPos,
                      yPos,
                      yLabelTextPaint);
    }
  }

  private void drawYLabelLine(Canvas canvas) {
    float xPos, yPos;
    String labelText;
    /// draw y label
    xPos = positionOfY - yLabelTextPaint.getTextSize();
    for(int posIdx = 1; posIdx < yLabelCount; posIdx++) {
      canvas.drawLine(
        xPos + (int) (0.7 * yLabelTextPaint.getTextSize()),
        mBottomSide - posIdx * yLabelCoordinateInterval,
        xPos + (int) (1.3 * yLabelTextPaint.getTextSize()),
        mBottomSide - posIdx * yLabelCoordinateInterval,
        axisPaint);
    }
  }

  private void drawCurveByCanvasDrawLine(Canvas canvas) {
    canvas.drawLines(ptsData, 0, 4 * (dataLength - 1), curvePaint);
  }

  private void drawPeakByCanvasDrawPoint(Canvas canvas)
  {
    int nPeakIdx = 0;
    while(nPeakIdx < nPeakCnt)
    {
      canvas.drawCircle(fPeakXArray[nPeakIdx], fPeakYArray[nPeakIdx], DRAW_CIRCLE_RADIUS, PeakPaint);
      nPeakIdx++;
    }
  }
}
