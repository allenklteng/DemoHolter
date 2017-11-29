package com.vitalsigns.utility;

import android.os.HandlerThread;
import android.os.Process;

/**
 * Created by allen_teng on 14/09/2017.
 */

public class thread
{
  /**
   * @brief releaseHandlerThread
   *
   * Release a handler thread
   *
   * @param handlerThread HandlerThread to be released
   * @return NULL
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
   * @brief createBackgroundHandlerThread
   *
   * Create a background thread
   *
   * @param name thread name
   * @return HandlerThread
   */
  public static HandlerThread createBackgroundHandlerThread(String name)
  {
    HandlerThread thread = new HandlerThread(name, Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();
    return (thread);
  }
}
