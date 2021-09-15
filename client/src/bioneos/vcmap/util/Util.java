package bioneos.vcmap.util;

import java.lang.reflect.Method;
import java.net.URL;
import java.text.DecimalFormat;

/**
 * A generic class that holds some utility functions for the VCMap app.
 *
 * NOTE: This class is likely to be moved or reorganized at a later time to
 * better reflect the purpose of this methods.
 *
 * @author sgdavis
 */
public class Util
{
  /**
   * <p>Create a {@link String} out of a value to ensure that scientific
   * notation of floating point or large values never occurs.</p>
   *
   * @param unitValue
   *   Value to format
   * @param scale
   *   The scale in which this value has been represented (1 means integer)
   * @return
   *   formatted {@link String} of unitValue
   */
  public static String formatUnit(int unitValue, int scale)
  {
    if (scale == 1)
    {
      return new Integer(unitValue).toString();
    }
    else
    {
      String format = "0.";
      for(int i = scale; i > 1; i = i / 10)
        format += "0";
      DecimalFormat formatter = new DecimalFormat(format);
      return formatter.format((double) unitValue / scale);
    }
  }

  /**
   * Helper method to place commas in integers to make them easier to read.
   * @param num
   *   The int value to process.
   * @return
   *   The {@link String} containing the integer value plus its proper commas.
   */
  public static String commify(int num)
  {
    if (num == 0) return "0";

    StringBuilder str = new StringBuilder();
    boolean negative = num < 0;
    if (negative) num = num * -1;

    for (int i = 1; Math.pow(1000, i - 1) <= num; i++)
    {
      // Calculate number block
      int val = ((int) (num % Math.pow(1000, i))) / (int) Math.pow(1000, i - 1);

      // Prepend commas only after the first number block
      if (i > 1) str.insert(0, ",");

      // Prepend number block
      str.insert(0, val);

      // Prepend zeros if appropriate
      if (Math.pow(1000, i) < num)
      {
        if (val < 10) str.insert(0, "00");
        else if (val < 100) str.insert(0, "0");
      }
    }

    if (negative) str.insert(0, "-");

    return str.toString();
  }

  /**
   * <p>Open a specific URL for a webpage in the user's web browser.</p>
   *
   * @param url
   *   {@link String} representing the URL to direct the user's web browser
   * @return
   *   True when successfully able to load the URL in a web browser.
   */
  public static boolean openURL(String url)
  {
    try
    {
      Class<?> serviceManager = Class.forName("javax.jnlp.ServiceManager");
      Method lookup = serviceManager.getDeclaredMethod("lookup", String.class);
      Object bs = lookup.invoke(null, new Object[] { "javax.jnlp.BasicService" });
      if (bs == null) throw new Exception("Cannot get javax.jnlp.BasicService");
      Class<?> bsClass = Class.forName("javax.jnlp.BasicService");
      Method showDocument = bsClass.getDeclaredMethod("showDocument", URL.class);
      Boolean worked = (Boolean) showDocument.invoke(bs, new Object[] { url });
      if (!worked.booleanValue()) throw new Exception("BasicService.showDocument() failed.");
    }
    catch (Exception noWebstart)
    {
      try
      {
        String osName = System.getProperty("os.name");

        if (osName.contains("Linux"))
        {
          String[] browsers = {"firefox", "mozilla", "opera", "netscape"};
          for (String browser : browsers)
          {
            if (Runtime.getRuntime().exec(new String[] {"which", browser}).waitFor() == 0)
            {
              Runtime.getRuntime().exec(new String[] {browser, url});
              return true;
            }
          }
        }
        else if (osName.contains("Mac OS"))
        {
          Class<?> fileManager = Class.forName("com.apple.eio.FileManager");
          Method openURL = fileManager.getDeclaredMethod("openURL", String.class);
          openURL.invoke(null, new Object[] {url});
          return true;
        }
        else if (osName.contains("Windows"))
        {
          Runtime.getRuntime().exec("rundll32.exe url.dll,FileProtocolHandler " + url);
          return true;
        }
      }
      catch (Exception e) {}
    }

    // If we haven't succeeded yet, we have failed
    return false;
  }

}
