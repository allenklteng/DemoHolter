package com.vitalsigns.demoholter;

import android.os.HandlerThread;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by AllenTeng on 7/20/2017.
 */

public class Utility
{
  /**
   * Release HandlerThread
   * @param handlerThread HandlerThread to be released
   */
  public static void releaseHandlerThread(HandlerThread handlerThread)
  {
    HandlerThread thread = handlerThread;
    if(thread != null)
    {
      thread.interrupt();
      thread.quit();
    }
  }

  /**
   * Get current date time string
   * @return yyyyMMdd_HHmmss
   */
  public static String getDateTime()
  {
    Calendar calendar = Calendar.getInstance();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
    return (sdf.format(calendar.getTime()));
  }
}
