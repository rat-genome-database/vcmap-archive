package bioneos.vcmap;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Vector;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.help.DefaultHelpBroker;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

import bioneos.common.errors.ErrorReporter;
import bioneos.common.instance.BNApplication;
import bioneos.vcmap.callback.MapLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.Tutorial;
import bioneos.vcmap.gui.dialogs.MapDialog;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.Factory;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.options.GlobalOptions;

/**
 * <p>{@link VCMap} manages all of the open windows representing VCMap to the
 * users.</p>
 *
 * <p>Created on: May 22nd, 2008</p>
 * @author sgdavis@bioneos.com
 */

public class VCMap
  extends BNApplication
  implements MapLoader
{
  // Versioning and naming constants
  /** The name of the program. */
  public static final String NAME = "VCMap";
  /** The complete name for the program with a short description. */
  public static final String FULL_NAME = "The Virtual Comparative Map Tool (VCMap)";
  /** The first of three integers used in the version string of the program. */
  public static final int RELEASE = 3;
  /** The second of three integers used in the version string of the program. */
  public static final int FEATURE = 1;
  /** The third of three integers used in the version string of the program. */
  public static final int BUGFIX = 1;
  /** The subversion revision at the time of this build. */
  public static final String BUILD = "@BUILD@";
  /** The date ant was run to create vcmap.jar. */
  public static final String BUILD_DATE = "@BUILD_DATE@";

  private static boolean backboneError = false;
  private static boolean offBackboneError = false;
  private static boolean loadError = true;
  private static boolean backboneTry = false;
  private static boolean offBackboneTry = false;
  private static boolean loadTry = false;

  private static MainGUI mg;
  private static Vector<String> offBackbone;
  private static String backbone;
  private static String[] backboneString;
  private static Vector<String[]> offBackboneStrings;
  private static Vector<MapData> offBackbones;
  private static MapData mappedBackbone;

  private static String message;

  private static Vector<MapData> allMaps = null;

  // Other Constants
  /** 
   * The common font used for labels throughout the GUI.
   */
  public static final Font labelFont = new Font("default", Font.PLAIN, 10);
  /** 
   * The font used for unit labels.  This MUST be a monospaced font, for
   * performance reasons.
   */
  public static final Font unitFont = new Font("monospaced", Font.PLAIN, 10);
  /* The port used to check for an already running instance. */
  private static final int SINGLE_INSTANCE_PORT = 15122;
  /* Helpset location as a constant for ease of modification. */
  private static final String HELPSET_FILE = "doc/VCMap.hs";

  private GlobalOptions options;

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());
  private static HashMap<String, Vector<Long>> timings;
  private static Preferences prefs = Preferences.userNodeForPackage(VCMap.class);
  private static HelpBroker helpBroker = null;
  private static boolean debug;
  /**
   * <p>This main method gets or creates saved preferences, enables the debugger,
   * checks the java version and creates the first instance of the main user
   * interface.</p>
   */
  public static void main(String[] args)
  {
    // Setup logging
    ErrorReporter.init();
    timings = new HashMap<String, Vector<Long>>();
    logger.setLevel(Level.DEBUG);
    backbone = null;
    offBackbone = new Vector<String>();
    message = "The following errors occurred:<br><br>";
    // Check our command line arguments
    debug = prefs.getBoolean("DebugEnabled", false);
    for (int i = 0; i < args.length; i++)
    {
      loadError = true;
      // Setup the debug log
      //   This can be specified with the command line arguments (-d, or --debug),
      //   or using the graphical Preferences dialog after launching the program
      if (args[i].equals("-d") || args[i].equals("--debug"))
        debug = true;
      else if (args[i].equals("--devel"))
        Factory.devel = true;
      else if (args[i].equals("--help"))
        usage();
      else if (args[i].equals("-b"))
      {
        if(args.length == i + 1) usage();
        i++;
        if (backbone != null)
        {
          backboneTry=true;
          backboneError=true;
          message += "More than one backbone was given<br>";
        }
        backbone = args[i];
      }
      else if (args[i].indexOf("--backbone=") == 0)
      {
        if (backbone != null)
        {
          backboneTry=true;
          backboneError=true;
          message += "More than one backbone was given<br>";
        }
        backbone = args[i].substring(args[i].indexOf("=") + 1);
      }
      else if (args[i].equals("-o"))
      {
        if(args.length == i + 1) usage();
        i++;
        offBackbone.add(args[i]);

      }
      else if (args[i].indexOf("--off-backbone=") == 0)
      {
        offBackbone.add(args[i].substring(args[i].indexOf("=") + 1));
      }
      else
        usage();
    }

    // Start the debug logger
    setDebugLoggerEnabled(debug);

    // Startup message
    logger.debug(NAME + " v" + RELEASE + "." + FEATURE + "." + BUGFIX + " Starting up...");

    // Check the Java version
    String ver = System.getProperty("java.version");
    String v = "" ;
    if (ver.indexOf('_') == -1) v = ver ;
    else v = ver.substring(0, ver.indexOf('_')) ;

    if (v.compareTo("1.5.0") < 0)
    {
      String text = NAME + " v" + RELEASE + "." + FEATURE + "." + BUGFIX;
      text += " requires Java >= v1.5.0\n";
      text += "You are running Java version (" + ver + "), please upgrade your version\n";
      text += "of Java in order to run this program.\n\n";
      text += "The program will now exit...";
      System.out.println(text);
      JOptionPane.showMessageDialog(null, text, "Please upgrade Java", JOptionPane.ERROR_MESSAGE);
      logger.debug("Launched with an old version of Java: " + ver);
      System.exit(-1);
    }

    // Apple Menubar properties
    System.setProperty("apple.laf.useScreenMenuBar", "true");       // OS X
    System.setProperty("com.apple.macos.useScreenMenuBar", "true"); // OS <=9
    ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE); // Disable
    ToolTipManager.sharedInstance().setInitialDelay(300);
    ToolTipManager.sharedInstance().setReshowDelay(300);

    //
    // Intialize and start the Application
    //
    SwingUtilities.invokeLater(new Thread()
    {
      public void run()
      {
        new VCMap();
      }
    });
  }

  private static void usage()
  {
    System.err.println("Usage:");
    System.err.println("java " + VCMap.class.getName() + " [options] <config>\n");
    System.err.println("\tconfig:");
    System.err.println("\t\t--devel");
    System.err.println("\t\t\tOperate in development mode.  This causes the");
    System.err.println("\t\t\tapplication to load information from the DB_devel");
    System.err.println("\t\t\tdatabase.");
    System.err.println("\t\t-d | --debug");
    System.err.println("\t\t\tOperate in debug mode.  This causes the application to");
    System.err.println("\t\t\tlog every single action into a debug log.");
    System.err.println("\t\t-b <backbone> | --backbone=<backbone>");
    System.err.println("\t\t\tUse the <backbone> as the pre-loaded backbone map when");
    System.err.println("\t\t\tthe program launches, the format for the <backbone> is:");
    System.err.println("\t\t\t  'SPECIES:TYPE:SOURCE:CHR:VERSION'");
    System.err.println("\t\t\twhere CHR is the string:  'chr1', 'chr2', etc.; SPECIES");
    System.err.println("\t\t\tis the latin name for the species; TYPE is the map type:");
    System.err.println("\t\t\tGenomic, Genetic, etc; SOURCE is the source of the map (");
    System.err.println("\t\t\tNCBI Genomes, UMD Genomes, etc); and VERSION is the version");
    System.err.println("\t\t\tof the map that you would like (if omitted the latest version");
    System.err.println("\t\t\tis loaded)");
    System.err.println("");
    System.err.println("\t\t\tIf any errors exist in the <backbone> argument, no maps");
    System.err.println("\t\t\tare loaded and the user is notified of the error.");
    System.err.println("\t\t\tOnly one backbone map may be specified, and a backbone");
    System.err.println("\t\t\tmap must be specified in order to load an off-backbone");
    System.err.println("\t\t\tmap.");
    System.err.println("\t\t-o <map> | --off-backbone=<map>");
    System.err.println("\t\t\tUse the <map> as the pre-loaded off-backbone map when");
    System.err.println("\t\t\tthe program launches, the format for the <map> is:");
    System.err.println("\t\t\t  'SPECIES:TYPE:SOURCE:VERSION'");
    System.err.println("\t\t\twhere SPECIES is the latin name for the species; TYPE");
    System.err.println("\t\t\tis the map type: Genomic, Genetic, etc; SOURCE is the ");
    System.err.println("\t\t\tsource of the map (NCBI Genomes, UMD Genomes, etc); and");
    System.err.println("\t\t\tVERSION is the version of the map that you would like (if");
    System.err.println("\t\t\tomitted the latest version is loaded)");
    System.err.println("");
    System.err.println("\t\t\tIf any errors exist in the <map> argument, no maps are");
    System.err.println("\t\t\tloaded and the user is notified of the error.  ");
    System.err.println("\t\t\tThis parameter can be repeated multiple times.");

    System.exit(-1);
  }

  /**
   * <p>Processes the String containing an off-backbone map</p>
   * @param map
   *    String that has map information delimited by colons (:)
   */
  private void processMap(String map)
  {
    String[] mapInfo = map.split(":");
    if (mapInfo.length == 4)
    {
      if (!offBackboneError) offBackboneError = false;
      if (offBackboneStrings == null) offBackboneStrings = new Vector<String[]>();
      offBackboneStrings.add(new String[]
      { mapInfo[0], mapInfo[1], mapInfo[2], mapInfo[3] });
    }
    else if (mapInfo.length == 3)
    {
      if (!offBackboneError) offBackboneError = false;
      if (offBackboneStrings == null) offBackboneStrings = new Vector<String[]>();
      offBackboneStrings.add(new String[]
      { mapInfo[0], mapInfo[1], mapInfo[2] });
    }
    else
    {
      offBackboneError = true;
      message += "Invalid number of parameters for the off-backbone map: " + map + "<br>";
    }
  }

  /**
   * <p>Processes the String containing a backbone map</p>
   * @param backbone
   *    String that has the backbone map information delimited by colons (:)
   */
  private void processBackbone(String backbone)
  {
    String[] backboneInfo = backbone.split(":");
    if (backboneInfo.length == 5)
    {
      if (!backboneError) backboneError = false;
      backboneString = new String[]
      { backboneInfo[0], backboneInfo[1], backboneInfo[2], backboneInfo[3], backboneInfo[4] };
    }
    else if (backboneInfo.length == 4)
    {
      if (!backboneError) backboneError = false;
      backboneString = new String[]
      { backboneInfo[0], backboneInfo[1], backboneInfo[2], backboneInfo[3] };
    }
    else
    {
      backboneError = true;
      message += "Invalid number of parameters for the backbone map: " + backbone + "<br>";
    }

  }

  /**
   * <p>This constructor sets up the what is needed to manage {@link MainGUI}s,
   * and creates the first instance of a {@link MainGUI}</p>
   */
  public VCMap()
  {
    // Necessary for proper application operation
    super(MainGUI.class, SINGLE_INSTANCE_PORT);
    buildPossibleMaps();
    //
    // Additional processing (options, etc)
    //
    // Load and initialize preferences
    setOptions(new GlobalOptions());

    setDebugLoggerEnabled(options.getBooleanOption("DebugEnabled"));

    mg = new MainGUI(this);
    openWindow(mg);
    MapData backboneMap = null;
    Vector<MapData> offBackboneMaps = null;
    if(backbone != null)
    {
      backboneTry = true;
      processBackbone(backbone);
    }
    else
      backboneTry = false;
    if(offBackbone.size() != 0)
    {
      offBackboneTry = true;
      offBackboneError = false;
      if(backboneTry)
      {
        for(String map : offBackbone)
          processMap(map);
      }
    }
    else
      offBackboneTry = false;
    //Figure out the error status
    if (!backboneTry && !offBackboneTry) // no pre-load
    {
      loadError = true;
      loadTry = false;
    }
    else if (backboneTry && !offBackboneTry) // backbone was attempted but not off-backbone
    {
      loadError = false;
      loadTry = true;
    }
    else if (backboneTry && offBackboneTry) // both backbone and off-backbone were attempted
    {
      loadError = false;
      loadTry = true;
    }
    else
    // no backbone given and off-backbone was given
    {
      loadError = true;
      loadTry = true;
      message += "No Backbone was given for the off-backbone map<br>";
    }
    if (!loadError && loadTry) // we have a backbone attempt at least
    {
      backboneMap = null;
      if (backboneError)
      {
        loadError = true;
      }
      else
      {
        backboneMap = getMap(backboneString, true);
        if (backboneMap == null)
        {
          message += "Backbone Map: " + backboneString[0] + ":" + backboneString[1] + ":";
          message += backboneString[2] + " does not exist on the server<br>";
          message += "Check for typographical errors or spelling mistakes<br>";
          loadError = true;
        }
        else
        {
          loadError = !verifyChromosomeName(backboneMap, backboneString[3].toLowerCase());
          if (loadError) message += "Backbone Chromosome name: " + backboneString[3] + " does not" +
          		" exist on this map<br>";
          mappedBackbone = backboneMap;
        }
      }
      if (offBackboneTry)
      {
        offBackboneMaps = null;
        if (offBackboneError)
        {
          loadError = true;
        }
        else
        {
          if (offBackboneStrings == null)
          {
            loadError = true;
          }
          else
          {
            for (String[] map : offBackboneStrings)
            {
              MapData temp = getMap(map, false);
              if (temp == null)
              {
                loadError = true;
              }
              else
              {
                if (offBackboneMaps == null)
                  offBackboneMaps = new Vector<MapData>();
                offBackboneMaps.add(temp);
              }
            }
          }
        }
        if(offBackboneMaps == null || offBackboneMaps.size() != offBackboneStrings.size())
        {
          loadError = true;
        }
      }
    }
    offBackbones = offBackboneMaps;
    verifyMaps();
    if (!loadError)
      loadBackbone(backboneMap, backboneString[3]); //recursively calls the other maps to be loaded
    if (loadError)
    {
      MapDialog.showMapDialog(mg);
      message += "<br><center>No maps were loaded.";
      if (loadTry)
        JOptionPane.showMessageDialog(null, "<html>" + message + "</html>",
            "Load Error", JOptionPane.ERROR_MESSAGE);
    }
    Tutorial.init(mg);
  }

  /**
   * Implemented from abstract class BNApplication.
   */
  public String getName()
  {
    return NAME;
  }

  /**
   * <p>Get the {@link GlobalOptions} used throughout the application</p>
   *
   * @return
   *   {@link GlobalOptions} used throughout the application
   */
  public GlobalOptions getOptions()
  {
    return options;
  }

  /**
   * <p>Get the debug status of the application</p>
   *
   * @return
   *    boolean value for debugger enabled
   */
  public boolean getDebug()
  {
    return debug;
  }

  /**
   * <p>Verifies that the Chromosome exists on the backbone map</p>
   * @param map MapData that holds the data for the backbone map
   * @param chrNum String that has the potential chromosome name
   * @return true if the chromosome is valid, false if not
   */
  public boolean verifyChromosomeName(MapData map, String chrNum)
  {
    try
    {
      if (map.getChromosome(chrNum) != null)
        return true;
      else
        return false;
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  /**
   * <p>Takes in the information for the map and returns the map for that 
   * information or null if the map does not exist.</p>
   * @param mapInfo 
   *   String[] that contains the Species, Type and Version of the possible map
   * @return 
   *   MapData containing the map based on the String[] or null for no match.
   */
  public MapData getMap(String[] mapInfo, boolean backboneMap)
  {
    Vector<MapData> candidateMaps = new Vector<MapData>();
    if (mapInfo == null) return null;
    if (allMaps == null) buildPossibleMaps();
    for (MapData map : allMaps)
      if (mapInfo[0].equalsIgnoreCase(map.getSpecies()))
        if (mapInfo[1].equalsIgnoreCase(map.getTypeString()))
          if (mapInfo[2].equalsIgnoreCase(map.getSource()))
            candidateMaps.add(map);
    if (candidateMaps.size() == 1)
      return candidateMaps.get(0);
    else if (candidateMaps.size() > 1)
    {
      if (backboneMap && mapInfo.length == 5)
      {
        backboneError = true;
        for (MapData maps : candidateMaps)
          if (maps.getVersion().equals(mapInfo[4]))
          {
            backboneError = false;
            return maps;
          }
        message += mapInfo[0] + ":" + mapInfo[1] + ":" + mapInfo[2] + ":" + mapInfo[3] + ":";
        message += mapInfo[4] + "does not exist on the Bio::Neos Server<br>";
      }
      else if (!backboneMap && mapInfo.length == 4)
      {
        offBackboneError = true;
        for (MapData maps : candidateMaps)
          if (maps.getVersion().equals(mapInfo[3]))
          {
            offBackboneError = false;
            return maps;
          }
        message += mapInfo[0] + ":" + mapInfo[1] + ":" + mapInfo[2] + ":" + mapInfo[3] + ":";
        message += "does not exist on the Bio::Neos Server<br>";
      }
      else
      {
        return latestMap(candidateMaps);
      }
      return null;
    }
    else
      return null;
  }

  /**
   * Returns the most recently released map (if no version argument is given).
   * @param candidateMaps
   *   The list of possible maps to be used for this map load.
   * @return
   *   The newest map according to the release_date in the database.
   */
  private MapData latestMap(Vector<MapData> candidateMaps)
  {
    try
    {
      return Factory.findNewestMap(candidateMaps);
    }
    catch (SQLException e)
    {
      logger.warn("There was a problem determining the newest map, using arbitrary map!");
      return candidateMaps.get(0);
    }
  }

  /**
   * <p>When the options are set in {@link VCMap}, the options changes
   * are pushed to all the open windows to reflect the changes.</p>
   *
   * @param options
   *   {@link GlobalOptions} to be used throughout the application
   */
  public void setOptions(GlobalOptions options)
  {
    logger.debug("Reading new options for application.");
    this.options = options;

    if (options.getBooleanOption("DebugEnabled"))
    {
      try
      {
        if (!prefs.get("DebugFile", "none").equals(options.getStringOption("DebugFile")))
        {
          logger.debug("Debug file changed.");
          setDebugLoggerEnabled(false);
          prefs.put("DebugFile", options.getStringOption("DebugFile"));
          prefs.flush();
        }
      }
      catch (BackingStoreException bse)
      {
        logger.warn("There was a problem loading the preferences for VCMap.");
      }
    }

    setDebugLoggerEnabled(options.getBooleanOption("DebugEnabled"));
  }

  /*
   * Quit overwritten so that the settings are saved upon the closure of
   * the application. And so the instance checker is properly disabled.
   */
  public void quit(boolean force)
  {
    // Save options
    if (options != null) options.save();

    // The following code is a bit "hackish" but sine JavaHelp 2.0 does not
    // provide convenience methods for disposing the JFrame opened by the help
    // system (if any) we must do things this way.
    if (helpBroker != null && ((DefaultHelpBroker) helpBroker).getWindowPresentation().getHelpWindow() != null)
    {
      ((JFrame)((DefaultHelpBroker) helpBroker).getWindowPresentation().getHelpWindow()).dispose();
      helpBroker = null;
    }

    // Close the Tutorial frame
    Tutorial.closeTutorial();

    super.quit(force);
  }

  /**
   * <p>This method allows the debug log to be programmatically enabled or
   * disabled.  This allows for the user to turn on / off the debug log through
   * the preferences dialog.  If the debug log has already been enabled and
   * this method is called to enable it again, nothing happens.</p>
   * <p>NOTE: We use log4j a little differently than most, because we always
   * have our log level set to {@link Level}.DEBUG to force logging to a
   * temporary debug file that can be retrieved during major errors.
   * Therefore, in order to enable / disable the user defined debug log, we
   * simply add / remove {@link Appender}s from the main class {@link Logger}.
   *
   * @param enable True to enable the debug log file, false to disable it
   */
  public static void setDebugLoggerEnabled(boolean enable)
  {
    // Turn off the debug logger
    if (!enable)
    {
      logger.debug("Debug Logger stopping...");
      logger.removeAllAppenders();
      return;
    }
    else if (logger.getAllAppenders().hasMoreElements())
    {
      // Do Nothing
      return;
    }

    try
    {
      String fileloc = prefs.get("DebugFile", "VCMap.debug.log");

      PatternLayout layout = new PatternLayout("%5p [%d] %l\n      %m%n");
      RollingFileAppender file = new RollingFileAppender(layout, fileloc, true);
      file.setMaxFileSize("1024KB");
      file.setMaxBackupIndex(5);
      logger.addAppender(file);
    }
    catch (IOException ioe)
    {
      logger.error("Cannot open the DEBUG log, no logging will occur (" +
          ioe.getMessage() + ")");
    }

    logger.debug(NAME + " v" + RELEASE + "." + FEATURE +
        "." + BUGFIX + "-" + BUILD + " Debug Logger Started.");
  }

  /**
   * <p>Initialize the help system if needed (creating the {@link HelpBroker} and
   * {@link HelpSet} object references.</p>
   */
  private void initHelp()
  {
    // Find the HelpSet file and create the HelpSet object:
    ClassLoader cl = getClass().getClassLoader();
    try
    {
      URL hsURL = HelpSet.findHelpSet(cl, HELPSET_FILE);
      helpBroker = new HelpSet(null, hsURL).createHelpBroker();
    }
    catch (Exception e)
    {
      // Say what the exception really is
      logger.error("Problem initializing helpset: " + e.getMessage());
      logger.error("Creating HelpBroker from empty HelpSet as a fallback.");
      helpBroker = (new HelpSet()).createHelpBroker();
    }
  }

  /**
   * Create a Help window using the JavaHelp 2.0 system.  This method follows
   * the single-instance model and only creates a new {@link HelpBroker} object
   * if one hasn't already been created.  Otherwise, it just shows the help.
   * @param location
   *   The point to center the dialog on (typically center of the active
   *   {@link MainGUI} class that called the method.  If this is null, the
   *   dialog will be moved to the center of the screen.
   */
  public void launchHelp(Point location)
  {
    // Create the single help instance if needed
    if (helpBroker == null) initHelp();

    // Center the dialog
    if(location == null)
    {
      Dimension tempSize = Toolkit.getDefaultToolkit().getScreenSize();
      location = new Point(tempSize.width / 2, tempSize.height / 2);
      try
      {
        if (Double.parseDouble(System.getProperty("java.version").substring(0,3)) >= 1.4)
          location = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
        else
          location = new Point(tempSize.width / 2, tempSize.height / 2);
      }
      catch (NumberFormatException nfe)
      {
        location = new Point(tempSize.width / 2, tempSize.height / 2);
      }
    }
    location.x -= helpBroker.getSize().width / 2;
    location.y -= helpBroker.getSize().height / 2;
    helpBroker.setLocation(location);

    // Show the instance
    helpBroker.setDisplayed(true);
  }

  /**
   * Get the {@link HelpBroker} reference for classes needing access to the
   * help or related content.
   * @return
   *   The reference to the HelpBroker object.
   */
  public HelpBroker getHelpBroker()
  {
    if (helpBroker == null) initHelp();
    return helpBroker;
  }

  /**
   * <p>Prints the stack trace to the logger for the given {@link Exception}.</p>
   *
   * @param e
   *    {@link Exception} to print the stack trace for
   */
  public static void printStackTrace(Exception e)
  {
    String stackTrace = e.toString() + "\n";

    for (StackTraceElement element : e.getStackTrace())
      stackTrace += element.toString() + "\n";

    logger.error(stackTrace);
  }

  /**
   * <p>Gets all of the maps that are possible based on the current version of the sql server</p>
   */
  public static void buildPossibleMaps()
  {
    try
    {
      allMaps = Factory.getAvailableMaps();
    }
    catch (SQLException e)
    {}
  }

  public void mapLoadCompleted(boolean successful, int messageType, String message)
  {
    if(successful)
    {
      mg.getMapNavigator().repaint();
      if(offBackbones != null && offBackbones.size() > 0)
      {
      loadMap(offBackbones.firstElement());
        offBackbones.remove(offBackbones.firstElement());
      }
    }
    else
    {
      //This should really never get to this point unless there is a huge problem
      //since most of the potential errors are already taken care of before this point
      loadError = true;
      VCMap.message = message;
    }
  }

  /**
   * <p>Calls the loading of the backbone map</p>
   * @param map MapData that contains the information for the backbone to be loaded
   * @param chr String that has the chromosome to be loaded
   */
  public void loadBackbone(MapData map, String chr)
  {
    mg.loadMap(true, map, chr, this);
  }

  /**
   * <p>Calls the loading of a non-backbone map</p>
   * @param map MapData that contains the information for the map to be loaded
   */
  public void loadMap(MapData map)
  {
    mg.loadMap(false, map, "", this);
  }


  public void verifyMaps()
  {
    if(!loadError && offBackboneTry && !offBackboneError)
    {
      for(MapData map : offBackbones)
      {
        try
        {
          Chromosome chr = mappedBackbone.getChromosome(backboneString[3]);
          if (!Factory.getSyntenyTest(chr, map))
          {
            loadError = true;
            message += map.getName() + " has no syntenic regions with " + mappedBackbone.getName() +"<br>";
          }
        }
        catch (SQLException e){/*There should be no errors*/}
      }
    }
  }

  /**
   * <p>Returns the HashMap object containing the timings</p>
   * @return
   *    The timings HashMap
   */
  public HashMap<String, Vector<Long>> getTimings()
  {
    return timings;
  }

  /**
   * <p>Adds the timing of the operation to the HashMap of timings</p>
   * @param operation
   *    String containing the operation that took place
   * @param time
   *    Time that it took (in ms) for the operation to execute
   */
  public void addTiming(String operation, long time)
  {
    Vector<Long> newTiming = new Vector<Long>();
    newTiming.add(time);
    addTiming(operation, newTiming);
  }

  public void addTiming(String operation, Vector<Long> time)
  {
    Vector<Long> newTiming = timings.get(operation);
    if (newTiming == null)
    {
      newTiming = new Vector<Long>();
    }
    newTiming.addAll(time);
    timings.put(operation, newTiming);
  }
}
