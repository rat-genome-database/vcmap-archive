package bioneos.vcmap.options;

import java.awt.Color;
import java.util.Hashtable;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * <p>Allows {@link VCMap} to interact with its preferences from session to session.</p>
 *
 * <p>Created on: August 18th, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class GlobalOptions
  implements Cloneable
{
  // TODO - Change getXXXOption to grab from the Defaults as a backup.
  private Hashtable<String, Object> options;

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());
  private static Preferences prefs = Preferences.userNodeForPackage(VCMap.class);

  /**
   * <p>Default Constructor, reads in the {@link Preferences} for {@link VCMap}.
   * Also checks for outdated preferences and updates them from the new values
   * in the defaults.
   * </p>
   */
  public GlobalOptions()
  {
    options = new Hashtable<String, Object>(75);

    try
    {
      String[] keys = prefs.keys();

      for (String key : keys)
      {
        if (key.startsWith("int_"))
          options.put(key.substring(key.indexOf("_") + 1), prefs.getInt(key, 0));
        else if (key.startsWith("double_"))
          options.put(key.substring(key.indexOf("_") + 1), prefs.getDouble(key, 0));
        else if (key.startsWith("bool_"))
          options.put(key.substring(key.indexOf("_") + 1), prefs.getBoolean(key, false));
        else if (key.startsWith("string_"))
          options.put(key.substring(key.indexOf("_") + 1), prefs.get(key, ""));
        else if (key.startsWith("color_"))
        {
          String color = prefs.get(key, "");
          if (color.compareTo("") == 0) continue;

          String[] rgb = color.split(",");
          int red = Integer.parseInt(rgb[0]);
          int green = Integer.parseInt(rgb[1]);
          int blue = Integer.parseInt(rgb[2]);
          options.put(key, new Color(red, green, blue));
        }
      }
    }
    catch (BackingStoreException bse)
    {
      logger.debug("BackingStoreException: " + bse.getMessage());
    }

    // Check if we have invalid preferences
    if (getStringOption("build").equals(""))
    {
      logger.warn("No preferences found, so creating them from the defaults");
      Defaults.restoreDefaults(this);
      setOption("release", VCMap.RELEASE);
      setOption("minor", VCMap.FEATURE);
      setOption("bugfix", VCMap.BUGFIX);
      setOption("build", VCMap.BUILD);
    }

    // Now update any outdated preferences
    if (newerVersion(VCMap.RELEASE, VCMap.FEATURE, VCMap.BUGFIX))
    {
      updateOptions();
    }
  }

  /*
   * Private helper to test the version number specified against the one from
   * the preferences.
   * Returns true if the version specified as a parameter is newer than the
   * version from preferences, false if it is older.
   */
  private boolean newerVersion(int r, int m, int b)
  {
    return ((getIntOption("release") < r) ||
        (getIntOption("release") == r && getIntOption("minor") < m) ||
        (getIntOption("release") == r && getIntOption("minor") == m && getIntOption("bugfix") < b));
  }

  /**
   * This method updates the options when the saved options from the disk are
   * out of date. Any new versions of the software that require updates should
   * add a section in this method to ensure that any new values are added to
   * (or removed from) the {@link GlobalOptions} class.
   * <p>NOTE: This method was only implemented in version 1.2.0 and therefore
   * anything before that may have suffered from problems when preferenes
   * versions on the disk were different than the latest version of the
   * software.  Going forward, any additions or deletions from the
   * {@link Defaults} class *must* have an accompanying section in this method.</p>
   * <em>For example:</em>
   * <pre>
   *   if(newerVersion(1, 2, 0))
   *   {
   *     // Changes introduced in version 1.2.0 must go here
   *     ...
   *     update = true;
   *   }
   *   if(newerVersion(1, 2, 5)) // *IF* not *else if*
   *   {
   *     // Changes introduced in version 1.2.5 must go here
   *     // This block will be entered *in addition* to the above block when
   *     // the version of the preferences on the disk is &lt; 1.2.0
   *     ...
   *     update = true;
   *   }
   * </pre>
   */
  public void updateOptions()
  {
    boolean update = false;

    if (newerVersion(1, 2, 0))
    {
      // New options as of version 1.2.0
      options.remove("int_featureColumns");
      options.put("annotColumnSpacing", 10);
      options.put("labelColumnSpacing", 10);
      options.put("buttonHeight", 13);
      options.put("scrollSize", 5);
      options.put("overlapBoxBorderSpacing", 10);
      options.put("showTutorial", true);
      options.put("annotDrawingWidth", 5);

      update = true;
    }

    if (newerVersion(1, 2, 1))
    {
      // New options as of version 1.2.1
      options.put("overlapBoxBetweenAnnotSpacing", 2);
      update = true;
    }
    
    if (newerVersion(3, 0, 0))
    {
      // New options as of version 3.0.0
      options.put("maxZoomBarValue", 15);
      update = true;
    }

    // Finally update our version control numbers
    if(update)
    {
      setOption("release", VCMap.RELEASE);
      setOption("minor", VCMap.FEATURE);
      setOption("bugfix", VCMap.BUGFIX);
      setOption("build", VCMap.BUILD);
    }
  }

  /**
   * <p>Constructor that creates an instance of {@link GlobalOptions} from
   * existing options saved in another {@link GlobalOptions}.</p>
   *
   * @param options
   *   {@link HashTable} of options
   */
  private GlobalOptions(Hashtable<String, Object> options)
  {
    this.options = options;
  }

  /**
   * <p>Stores a new option in the {@link GlobalOptions}.</p>
   *
   * @param name
   *   Is the key of the option. The name for {@link Color} options
   *   are not case sensitive.
   * @param value
   *   Is the element of the key
   */
  public void setOption(String name, Object value)
  {
    if (name == null || value == null) return;

    if (value.getClass() == Color.class)
      options.put("color_" + name.toLowerCase(), value);
    else
      options.put(name, value);
  }

  /**
   * <p>Helper method that calls <code>savePrefs()</code>. Put in place in case
   *  we want to save preferences to a file or somewhere else later on.</p>
   */
  public void save()
  {
    savePrefs(prefs);
  }

  /**
   * <p>Save the options saved in the {@link GlobalOptions} to the
   * {@link Preferences}.</p>
   *
   * @param p
   *   {@link Preferences} where to save the options
   */
  private void savePrefs(Preferences p)
  {
    try
    {
      String[] keys = (String[])options.keySet().toArray(new String[options.keySet().size()]);

      for (String key : keys)
      {
        Object o = options.get(key);

        if (o.getClass() == Integer.class)
          p.putInt("int_" + key, (Integer)o);
        else if (o.getClass() == Double.class)
          p.putDouble("double_" + key, (Double)o);
        else if (o.getClass() == Boolean.class)
          p.putBoolean("bool_" + key, (Boolean)o);
        else if (o.getClass() == String.class)
          p.put("string_" + key, (String)o);
        else if (o.getClass() == Color.class)
        {
          Color color = (Color)o;
          String rgb = ((Integer)color.getRed()).toString() + ",";
          rgb += ((Integer)color.getGreen()).toString() + ",";
          rgb += ((Integer)color.getBlue()).toString();
          p.put(key, rgb);
        }
      }

      p.flush();
    }
    catch (BackingStoreException bse)
    {
      // What to do now?
      logger.error("Problem while trying to save preferences to backing store: " + bse);
    }
  }

  /**
   * <p>Clear the preferences saved for {@link VCMap} and the options saved
   * in the {@link GlobalOptions}.</p>
   *
   * @return
   *   true - Preferences and options were cleared correctly
   *   false - Unable to clear preferences and options
   */
  public boolean clear()
  {
    Preferences p = prefs;

    try
    {
      p.clear();

      p.flush();
    }
    catch (BackingStoreException bse)
    {
      return false;
    }

    return true;
  }

  /**
   * <p>Get an option from this instance that is a boolean value.  This method
   * does the casting and error checking, but for that reason it will not
   * notify the calling method if <code>key</code> is not found.  If it is not
   * found, or an error occurs, a false is returned.</p>
   *
   * @param key - The key or name of the option requested
   * @return An boolean value associated to <code>key</code> or false.
   */
  public boolean getBooleanOption(String key)
  {
    return getBooleanOption(key, false);
  }

  /**
   * <p>Get a boolean option unless it is not found, return a specified value.</p>
   *
   * @param key
   *   The key or name of the option requested.
   * @param def
   *   The default value for which to use in case the boolean option is not
   *   found, or is not saved as a boolean.
   * @return
   *   An boolean value associated to <code>key</code> or the specified default
   *   value.
   */
  public boolean getBooleanOption(String key, boolean def)
  {
    Object o = options.get(key);
    if (o == null) return def;
    else if (o.getClass() != Boolean.class) return def;

    return ((Boolean) o).booleanValue();
  }

  /**
   * <p>Get an option from this instance that is an int value.  This method does
   * the casting and error checking, but for that reason it will not notify the
   * calling method if <code>key</code> is not found.  If it is not found, or
   * an error occurs, a 0 is returned.</p>
   *
   * @param key - The key or name of the option requested
   * @return An int value associated to <code>key</code> or 0.
   */
  public int getIntOption(String key)
  {
    Object o = options.get(key);
    if (o==null) return 0;
    else if (o.getClass()!=Integer.class) return 0;

    return ((Integer)o).intValue();
  }

  /**
   * <p>Get an option from this instance that is a double value.  This method does
   * the casting and error checking, but for that reason it will not notify the
   * calling method if <code>key</code> is not found.  If it is not found, or
   * an error occurs, a 0.0 is returned.</p>
   *
   * @param key - The key or name of the option requested
   * @return A double value associated to <code>key</code> or 0.0 .
   */
  public double getDoubleOption(String key)
  {
    Object o = options.get(key);
    if (o==null) return 0.0;
    else if (o.getClass()!=Double.class) return 0.0;

    return ((Double)o).doubleValue();
  }

  /**
   * <p>Get an option from this instance that is a String value.  This method does
   * the casting and error checking, but for that reason it will not notify the
   * calling method if <code>key</code> is not found.  If it is not found, or
   * an error occurs, an empty {@link String} is returned.</p>
   *
   * @param key - The key or name of the option requested
   * @return A String value associated to <code>key</code> or "" .
   */
  public String getStringOption(String key)
  {
    Object o = options.get(key);
    if (o==null) return "";
    else if (o.getClass()!=String.class) return "";

    return (String) o;
  }

  /**
   * <p>This is the accessor method that returns the drawing colors to be used by
   * the graphical classes.</p>
   *
   * @param type -
   *          The name of the object for which a color is requested. Type is not
   *          case sensitive
   * @return a {@link Color}to be used for this type of object, or return the
   *          {@link Color} from Defaults if <code>type</code> is not
   *          associated to any Color.  Never returns null.
   */
  public Color getColor(String type)
  {
    Object o = options.get("color_" + type.toLowerCase());

    if (o == null)
      return (Color) Color.BLACK;

    return (Color) o;
  }

  /**
   * <p>Find out if a certain object is visible based on the preferences and
   * options saved for the program.</p>
   *
   * @param featureType
   *   {@link String} name of the item
   * @return
   *   true - If object is not found in the preferences or if the object is
   *          shown
   *   false - If the object is not shown
   */
  public boolean isShown(String featureType)
  {
    // By default show anything we don't have a setting for
    if (options.get("shown_" + featureType.toLowerCase()) == null) return true;

    // Otherwise return the users preference
    return getBooleanOption("shown_" + featureType.toLowerCase());
  }

  /**
   * <p>Determine if an option has a value set or not.</p>
   *
   * @param key - The name of the option to check.
   * @return True if the option has a defined value for this GlobalOptions
   *         instance, false if not.  If this method returns false, then a
   *         call to the getXXXOption() methods will return a default and
   *         sometimes unusable number, therefore, this method should be called
   *         first in any situation where a default value is unacceptable.
   */
  public boolean isDefined(String key)
  {
    return options.containsKey(key);
  }

  /*
   * (non-Javadoc)
   * Method overridden to properly make a copy of {@link GlobalOptions}
   * @see java.lang.Object#clone()
   */
  public Object clone()
  {
    Hashtable<String, Object> newOptions = new Hashtable<String, Object>();

    for(String key : options.keySet())
    {
      Object o = options.get(key);

      if (o.getClass() == Integer.class)
        newOptions.put(key, new Integer(((Integer)o).intValue()));
      else if (o.getClass() == Double.class)
        newOptions.put(key, new Double(((Double)o).doubleValue()));
      else if (o.getClass() == Boolean.class)
        newOptions.put(key, new Boolean(((Boolean)o).booleanValue()));
      else if (o.getClass() == String.class)
        newOptions.put(key, new String(((String)o).toString()));
      else if (o.getClass() == Color.class)
        newOptions.put(key, new Color(((Color)o).getRGB()));
    }

    return new GlobalOptions(newOptions);
  }
}
