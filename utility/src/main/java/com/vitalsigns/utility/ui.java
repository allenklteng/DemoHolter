package com.vitalsigns.utility;

import android.app.Activity;

import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;

/**
 * Created by allen_teng on 14/09/2017.
 */

public class ui
{
  /**
   * @brief hideStatusBar
   *
   * Hide status bar
   *
   * @param activity
   */
  public static void hideStatusBar(Activity activity)
  {
    activity.getWindow().setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN);
  }
}
