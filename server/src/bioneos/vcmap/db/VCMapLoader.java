package bioneos.vcmap.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import bioneos.vcmap.db.loaders.Loader;

/**
 * This class performs a full load or update of the VCMap database local data
 * warehouse. This database warehouses all of the relevant map data needed to
 * allow the VCMap client to display maps comparatively. This script is
 * configured via a set of JSON formatted configuration files that allow for
 * modifications to the types and sources of the data loaded into the database.
 *
 * NOTE:  Perhaps the processJsonConfig() method should convert all JSON to
 *   Hashtables so we don't have to continually check for JSON errors. 
 *   Alternatively - We could test for all required attributes at the start of
 *   the method, and then use "optXXX()"
 * NOTE:  Before starting a load -- it might be useful to query for ontology
 *   data and warn on stdout or even fail to load anything, since ontology
 *   currently cannot be loaded *after* the map data is in place.  If failing
 *   on lack of ontology, you could add a "--force" parameter to allow an
 *   override of this default behavior. 
 */
public class VCMapLoader
{
  // Versioning and naming constants
  /** The name of the program. */
  public static final String NAME = "VCMapLoader";
  /** The complete name for the program with a short description. */
  public static final String FULL_NAME = "The Virtual Comparative Map Tool Loader (VCMapLoader)";
  /** The first of three integers used in the version string of the program. */
  public static final int RELEASE = 3;
  /** The second of three integers used in the version string of the program. */
  public static final int FEATURE = 1;
  /** The third of three integers used in the version string of the program. */
  public static final int BUGFIX = 0;
  /** The subversion revision at the time of this build. */
  public static final String BUILD = "@BUILD@";
  /** The date ant was run to create vcmap.jar. */
  public static final String BUILD_DATE = "@BUILD_DATE@";

  /* Download constants */
  private static final int MAX_DOWNLOAD_ATTEMPTS = 3;
  private static final int DOWNLOAD_RETRY_DELAY = 10000;

  // The following is old code from the "custom" synteny algorithm.
  /* The # of mismatches allowed inside of a SyntenyBlock. Should be in the config eventually */
  // private static final int SYNTENY_MAX_MISMATCH = 3;
  // private static final int SYNTENY_MIN_BLOCK_SIZE = 3;

  // Data
  private File scratchDir;
  private File processingDir;
  private String localDb;
  private String localDbUser;
  private String localDbPass;
  private JSONObject config;
  private Vector<JSONObject> sources = new Vector<JSONObject>();
  private JSONObject homologeneConfig = null;
  private JSONObject syntenyConfig = null;

  // Static logger for entire loader
  private static Logger logger = Logger.getLogger(VCMapLoader.class.getName());

  // Variables affecting app operation
  private static boolean devel = false;
  private static boolean cache = false;

  /**
   * Start up the loader in several steps: 
   *   1.) Instantiate the logger 
   *   2.) Process the command line arguments
   *   3.) Process the config file(s)
   *   4.) Begin the loading process
   * 
   * Alternatively, some parameters force the loader into "utility" mode where
   * it will not perform a map load, but instead do some other operation, such
   * as load ontology or delete a map.  After these utility operations are
   * completed, the loader will exit.
   */
  public static void main(String[] args)
  {
    // Initialize the logger
    setupLogger();

    // Load all Loader "plugins"
    setupLoaders();

    // Get our configuration file
    if (args.length < 1) usage();
    File config = null;
    String forceDb = null;
    File ontologyFile = null;
    int deleteMap = -1, deleteAset = -1;
    for (int arg = 0; arg < args.length; arg++)
    {
      if (args[arg].equals("--help") || args[arg].equals("-h"))
      {
        usage();
      }
      else if (args[arg].equals("-c"))
      {
        if (arg + 1 == args.length) usage();
        arg++;

        config = new File(args[arg]);
      }
      else if (args[arg].indexOf("--config=") == 0)
      {
        config = new File(args[arg].substring(args[arg].indexOf("=") + 1));
      }
      else if (args[arg].equals("--devel") || args[arg].equals("-d"))
      {
        devel = true;
        cache = true;
      }
      else if (args[arg].equals("--cache"))
      {
        cache = true;
      }
      else if (args[arg].indexOf("--db=") == 0 || args[arg].indexOf("--database=") == 0)
      {
        forceDb = args[arg].substring(args[arg].indexOf("=") + 1);
      }
      else if (args[arg].indexOf("--insert-ontology=") == 0)
      {
        ontologyFile = new File(args[arg].substring(args[arg].indexOf("=") + 1));
      }
      else if (args[arg].indexOf("--delete-map=") == 0)
      {
        String id = args[arg];
        try
        {
          id = args[arg].substring(args[arg].indexOf('=') + 1);
          deleteMap = Integer.parseInt(id);
          if (deleteMap <= 0) usage();
        }
        catch (NumberFormatException e)
        {
          System.err.println("Bad Integer value:" + id + "\n");
          usage();
        }
      }
      else if (args[arg].indexOf("--delete-aset=") == 0)
      {
        String id = args[arg];
        try
        {
          id = args[arg].substring(args[arg].indexOf('=') + 1);
          deleteAset = Integer.parseInt(id);
          if (deleteAset <= 0) usage();
        }
        catch (NumberFormatException e)
        {
          System.err.println("Bad Integer value:" + id + "\n");
          usage();
        }
      }
      else
      {
        usage();
      }
    }

    // Check for a missing config file
    if (config == null)
    {
      usage();
    }
    else if (!config.exists() || config.isDirectory())
    {
      logger.error("Bad configuration file: " + config.getAbsolutePath());
      usage();
    }

    // Process the config files directory
    HashMap<String, JSONObject> jsonConfigs = null;
    try
    {
      jsonConfigs = configure(config);

      // Modify the DB connect string when appropriate
      if (forceDb != null)
      {
        String db = jsonConfigs.get("config").getString("db_string");
        if (db.indexOf('/') != -1) db = db.substring(0, db.lastIndexOf('/') + 1) + forceDb;
        jsonConfigs.get("config").put("db_string", db);
      }
      else if (devel)
      {
        jsonConfigs.get("config").put("db_string",
            jsonConfigs.get("config").getString("db_string") + "_devel");
      }
    }
    catch (IOException e)
    {
      logger.error("Error reading config file from disk: " + e);
      System.exit(-2);
    }
    catch (JSONException e)
    {
      logger.error("Improperly formatted config file(s): " + e);
      logger.warn("Remove or repair broken config files in the config directory and try again");
      System.exit(-2);
    }

    //
    // Main Processing Setup- The following block prepares the objects
    // necessary for the loader to operate.
    //
    VCMapLoader loader = null;
    try
    {
      // First setup the log files
      setupLogFiles(new File(jsonConfigs.get("config").getString("logFile")));

      // Initialize the loader
      Class.forName("com.mysql.jdbc.Driver");
      loader = new VCMapLoader(jsonConfigs);
    }
    catch (JSONException e)
    {
      logger.error("Improperly formatted JSON config file: " + e);
      e.printStackTrace();
      System.exit(-2);
    }
    catch (SQLException e)
    {
      logger.error("Cannot connect to the local database: " + e);
      System.exit(-3);
    }
    catch (ClassNotFoundException e)
    {
      logger.error("Cannot find usable MySQL driver: " + e);
      System.exit(-4);
    }

    //
    // Utility modes (Ontology processing, Delete map)
    //
    if (ontologyFile != null)
    {
      loader.insertOntology(ontologyFile);
      System.exit(0);
    }
    else if (deleteMap > 0)
    {
      loader.clearMapFromDatabase(deleteMap);
      System.exit(0);
    }
    else if (deleteAset > 0)
    {
      loader.clearAsetFromDatabase(deleteAset);
      System.exit(0);
    }

    //
    // Main Processing - the actual load occurs here
    //
    try
    {
      // Perform the load
      loader.loadDatabase();
    }
    catch (Exception e)
    {
      logger.error("Unknown problem: " + e);
      e.printStackTrace();
      System.exit(-5);
    }
  }

  /**
   * Display the command line usage screen for this application. This method will cause the virtual
   * machine to exit with a value of -1.
   */
  public static void usage()
  {
    System.err.println("Usage:");
    System.err.println("java " + VCMapLoader.class.getName() + " [options] <config>\n");
    System.err.println("\tconfig:");
    System.err.println("\t\t-c file | --config=file");
    System.err.println("\t\t\tSpecify the main config file that specifies the general");
    System.err.println("\t\t\toptions for the loader script.  This file should also");
    System.err.println("\t\t\trefer to all of the various other config files using the");
    System.err.println("\t\t\t'source' attribute.  This option is required.");
    System.err.println("\n\toptions:");
    System.err.println("\t\t-d | --devel");
    System.err.println("\t\t\tOperate in development mode. This causes the application");
    System.err.println("\t\t\tto load information into the DB_devel database.  Also,");
    System.err.println("\t\t\tthis will force the logger into debug mode.");
    System.err.println("\t\t--cache");
    System.err.println("\t\t\tUse cached files instead of connecting to public sources");
    System.err.println("\t\t\tto gather information when possible.  If no cached files");
    System.err.println("\t\t\tare found, files will be downloaded.  No files will be");
    System.err.println("\t\t\tdeleted upon exit of the application.");
    System.err.println("\t\t--database=<db> OR --db=<db>");
    System.err.println("\t\t\tUse <db> as the main VCMap database instead, overriding");
    System.err.println("\t\t\tthe setting in the config files.");
    System.err.println("\t\t--insert-ontology=<file>");
    System.err.println("\t\t\tUse the OBO formatted file <file> to insert an ontology");
    System.err.println("\t\t\ttree into the specified database.");
    System.err.println("\t\t--delete-map=<map-id>");
    System.err.println("\t\t\tThis will cause the program to remove all data including");
    System.err.println("\t\t\tsynteny, versions, annotation_sets associated with the");
    System.err.println("\t\t\tspecified map with DB identifier 'map-id'.  This option");
    System.err.println("\t\t\twill prompt you to confirm the delete before executing.");
    System.err.println("\t\t--delete-aset=<annotation-set-id>");
    System.err.println("\t\t\tThis will cause the program to remove all data including");
    System.err.println("\t\t\tversions and AVPs associated with the specified");
    System.err.println("\t\t\tannotation set with DB identifier 'annotation-set-id'.");
    System.err.println("\t\t\tThis option will prompt you to confirm before executing.");
    System.err.println("\n\tNOTE:");
    System.err.println("\t\tThis program depends on several third party libraries:");
    System.err.println("\t\t\tApache Commons");
    System.err.println("\t\t\tApache Jakarta ORO");
    System.err.println("\t\t\tCompatible MySQL Connector-J (5.1+)");
    System.err.println("\t\t\tJSON.org java support");
    System.err.println("\t\t\tJavaMail");

    System.exit(-1);
  }

  /**
   * A Helper method to setup the basic logging to the console.
   */
  public static void setupLogger()
  {
    logger.setLevel(Level.INFO);

    // Setup a console appender with level INFO
    // (so the user isn't inundated with DEBUG messages)
    PatternLayout consoleLayout = new PatternLayout("%5p [%12C{1}]: %m%n");
    ConsoleAppender console = new ConsoleAppender();
    console.setThreshold(Level.INFO);
    console.setName("console");
    console.setTarget("System.err");
    console.setLayout(consoleLayout);
    console.activateOptions();
    logger.addAppender(console);
  }

  /**
   * A Helper method to instantiate all of the classes in the bioneos.vcmap.db.loaders package.
   * Loading these classes registers them with the {@link LoaderFactory} so they can handle data
   * sources.
   */
  public static void setupLoaders()
  {
    try
    {
      logger.info("Instantiating the Loaders...");
      URL url = VCMapLoader.class.getResource("/bioneos/vcmap/db/loaders/");
      logger.info("Search path for Loaders: " + url);
      String file = url.getPath();
      file = file.substring(file.indexOf(":") + 1);
      file = file.substring(0, file.indexOf("!"));
      logger.info("Jar file identified as: " + file);
      ZipFile dir = new ZipFile(file);
      for (Enumeration<? extends ZipEntry> e = dir.entries(); e.hasMoreElements();)
      {
        String cls = e.nextElement().getName();
        if (cls.indexOf("bioneos/vcmap/db/loaders/") == 0 && cls.endsWith(".class"))
        {
          try
          {
            cls = cls.replaceAll("/", ".").substring(0, cls.length() - 6);
            logger.info("Loading: " + cls);
            Class.forName(cls);
          }
          catch (ClassNotFoundException exp)
          {
            logger.warn("Cannot load class '" + cls + "' skipping...");
          }
        }
      }
    }
    catch (IOException e)
    {
      logger.error("Cannot open our Jar file to instantiate any Loaders.  Failure is eminent...");
      e.printStackTrace();
    }
  }

  /**
   * Setup two log files for 1) verbose output, and 2) warn level and greater output. The latter of
   * these files will always be erased for every run of the {@link VCMapLoader}
   *
   * @param logFile
   *          The {@link File} object of the file to write the more verbose logging messages out. The
   *          warning log file will be named "logFile" + ".error".
   */
  public static void setupLogFiles(File logFile)
  {
    logger.setLevel(devel ? Level.DEBUG : Level.INFO);
    logger.info("Log level set to: " + logger.getLevel());

    try
    {
      PatternLayout layout = new PatternLayout("%5p [%d] %l\n      %m%n");

      // Setup a file appender for verbose output
      logger.info("Creating appender for verbose log output");
      RollingFileAppender fileOut = new RollingFileAppender(layout, logFile.getAbsolutePath(), true);
      fileOut.setMaxFileSize("1024KB");
      fileOut.setMaxBackupIndex(15);
      fileOut.setThreshold(Level.DEBUG);
      logger.addAppender(fileOut);

      logger.info("Clearing old .error logs");
      for (File file : logFile.getParentFile().listFiles())
      {
        if (file.getName().indexOf(logFile.getName() + ".error") == 0) file.delete();
      }

      fileOut = new RollingFileAppender(layout, logFile.getAbsolutePath() + ".error", false);
      fileOut.setMaxFileSize("1024KB");
      fileOut.setMaxBackupIndex(5);
      logger.addAppender(fileOut);

      // Set the threshold after this message so we can get a timestamp as a
      // reference in the new fresh file
      logger.info("Created appender for .error log");
      fileOut.setThreshold(Level.WARN);
    }
    catch (IOException ioe)
    {
      logger.error("Cannot open the DEBUG log, no verbose logging will occur (" + ioe.getMessage()
          + ")");
    }
  }

  /**
   * This method processes a specified directory containing JSON formatted configuration files. Each
   * file will produce a {@Link JSONObject} that will represent a single data source. This
   * setup should be simpler to maintain (and debug JSON formatting problems).
   *
   * @param config
   *          The primary configuration file for the VCMapLoader. This file should refer to all of
   *          its sources stored in separate config files.
   * @return A {@link HashMap} of the {@link JSONObject}s produced by parsing the main configuration
   *         file and all of the sources to which it refers. The attribute of the main configuration
   *         file will be 'config', the Homologene configuration will be 'homologene' and the
   *         remainder of the sources will be 'sourceX' here X will be 1 to the number of sources.
   */
  public static HashMap<String, JSONObject> configure(File config) throws IOException,
      JSONException
  {
    if (!config.canRead()) throw new IOException(config.getAbsolutePath() + " is not readable");
    if (config.isDirectory()) throw new IOException(config.getAbsolutePath() + " is a directory");

    // Process all files in the config directory
    HashMap<String, JSONObject> jsonConfigs = new HashMap<String, JSONObject>();
    File parent = config.getParentFile();
    String problems = "";
    try
    {
      StringBuilder jsonString = new StringBuilder();

      BufferedReader configIn = new BufferedReader(new FileReader(config));
      while (configIn.ready())
      {
        String line = configIn.readLine().trim();
        if (line.matches("^#.*")) continue;
        jsonString.append(line);
      }
      configIn.close();

      // Parse the JSON
      jsonConfigs.put("config", new JSONObject(jsonString.toString()));

      // Grab all of the other config files
      JSONArray sources = jsonConfigs.get("config").getJSONArray("sources");
      int num = 1;
      for (int source = 0; source < sources.length(); source++)
      {
        try
        {
          String fileSource = "";
          BufferedReader buffer = new BufferedReader(new FileReader(new File(parent,
              sources.getString(source))));
          while (buffer.ready())
          {
            String line = buffer.readLine().trim();
            if (line.matches("^#.*")) continue;
            fileSource += line;
          }
          jsonConfigs.put("source" + num, new JSONObject(fileSource));
          num++;
        }
        catch (IOException e)
        {
          problems += ((problems.length() > 0) ? ", " : "") + sources.getString(source);
        }
        catch (JSONException e)
        {
          problems += ((problems.length() > 0) ? ", " : "") + sources.getString(source);
        }
      }
    }
    catch (JSONException e)
    {
      problems += ((problems.length() > 0) ? ", " : "") + config.getAbsolutePath();
      
      // Main config failed -- cannot continue
      throw new JSONException(problems);
    }

    // Now build the Homologene config (another file specified in main config)
    try
    {
      if (jsonConfigs.get("config").has("homologene"))
      {
        String fileSource = "";
        BufferedReader buffer = new BufferedReader(new FileReader(new File(parent, jsonConfigs.get(
            "config").getString("homologene"))));
        while (buffer.ready())
        {
          String line = buffer.readLine().trim();
          if (line.matches("^#.*")) continue;
          fileSource += line;
        }
        jsonConfigs.put("homologene", new JSONObject(fileSource));
      }
    }
    catch (IOException e)
    {
      problems += ((problems.length() > 0) ? ", " : "") + "Homologene";
    }
    catch (JSONException e)
    {
      problems += ((problems.length() > 0) ? ", " : "") + "Homologene";
    }

    // And the synteny config (another file specified in main config)
    try
    {
      if (jsonConfigs.get("config").has("synteny"))
      {
        String fileSource = "";
        BufferedReader buffer = new BufferedReader(new FileReader(new File(parent, jsonConfigs.get(
            "config").getString("synteny"))));
        while (buffer.ready())
        {
          String line = buffer.readLine().trim();
          if (line.matches("^#.*")) continue;
          fileSource += line;
        }
        jsonConfigs.put("synteny", new JSONObject(fileSource));
      }
    }
    catch (IOException e)
    {
      problems += ((problems.length() > 0) ? ", " : "") + "Synteny";
    }
    catch (JSONException e)
    {
      problems += ((problems.length() > 0) ? ", " : "") + "Synteny";
    }

    // Throw an exception if we had problems with some of the files
    if (problems.length() > 0)
    {
      throw new JSONException(problems);
    }

    return jsonConfigs;
  }

  /**
   * Get the primary DB configuration for this loader script instance.  This
   * config is a tuple of (DB name, DB user, DB pass)
   * @return
   *   The primary config of the local DB for this configuration of the loader.
   */
  public String[] getDbConfig()
  {
    // Pull off just the database name
    String localDbName = localDb.substring(localDb.lastIndexOf('/') + 1);
    return new String[] {localDbName, localDbUser, localDbPass};
  }

  /**
   * Construct a loader object and initialize it with the values specified.
   *
   * @param jsonConfigs
   *          The {@link Vector} of all of the {@link JSONObject}s that represent configuration
   *          sources for this run of the loader. The first element in this {@link Vector} should
   *          always be the primary configuration file for the VCMapLoader. This file should refer to
   *          all of its sources stored in separate config files, and these are translated into the
   *          subsequent {@link JSONObject}s in the {@link Vector}.
   * @param devel
   *          Work in development mode. This will append '_devel' onto the database connection string
   *          as specified in the configuration file in order to operate outside of the production
   *          database. Additionally, this will automatically turn on debug-level logging.
   * @param cache
   *          Work in cached files mode. The loader will attempt to grab files from the scratch
   *          directory before downloading from the data sources. This can result in inconsistent
   *          data, but is useful while debugging. Additionally, no files are deleted from the
   *          scratch directory when the loader finishes processing.
   */
  public VCMapLoader(HashMap<String, JSONObject> jsonConfigs) throws JSONException, SQLException
  {
    logger.info(NAME + " starting up.\n" + "Version String: " + RELEASE + "." + FEATURE + "."
        + BUGFIX + "\n" + "Build: " + BUILD + "\n" + "Build Date: " + BUILD_DATE);
    if (devel) logger.info("Starting up in DEVEL mode (using caching and more verbose logging)");

    // Setup some main variables
    config = jsonConfigs.get("config");
    scratchDir = new File(config.optString("scratch", "/tmp/vcmap"));
    logger.debug("Creating scratch directory: " + scratchDir.getAbsolutePath());
    scratchDir.mkdirs();
    processingDir = new File(config.optString("processingDir", "/tmp/vcmap/processed"));
    logger.debug("Creating processing directory: " + processingDir.getAbsolutePath());
    processingDir.mkdirs();
    localDb = config.getString("db_string");
    localDbUser = config.getString("db_user");
    localDbPass = config.getString("db_pass");
    // Setup Homologene / Synteny
    if (jsonConfigs.get("homologene") != null)
      homologeneConfig = jsonConfigs.get("homologene");
    if (jsonConfigs.get("synteny") != null)
      syntenyConfig = jsonConfigs.get("synteny");
    // Setup the sources
    sources = new Vector<JSONObject>();
    for (String key : jsonConfigs.keySet())
      if (key.indexOf("source") != -1) sources.add(jsonConfigs.get(key));

    // Create our database connection
    Connection conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
    Statement st = conn.createStatement();

    // Now process source id's
    for (JSONObject source : sources)
    {
      // Set the source id
      logger.debug("Determining source for: " + source.getString("name"));
      String query = "SELECT id FROM sources WHERE name='" + source.getString("name") + "'";
      ResultSet rs = st.executeQuery(query);
      if (rs.next())
      {
        source.put("id", rs.getInt(1));
        logger.debug("  id is: " + source.getInt("id"));
      }
      else
      {
        // Create the source in the db
        query = "INSERT INTO sources (name) VALUES ('" + source.getString("name") + "')";
        st.executeUpdate(query);
        source.put("id", getLastInsertId(st));
        logger.debug("  new source inserted with id: " + source.getInt("id"));
      }
    }

    // NOTE: We aren't worried about performance in this app because of its
    // run schedule (once a week), so we don't have to worry about closing our
    // connection to the database. If the run schedule changes in the future,
    // we should probably cache this connection when possible.
    conn.close();
    logger.info("Configuration successful.");
  }

  /**
   * Load data into the database as specified by our configuration.
   */
  public void loadDatabase()
  {
    // Start processing
    if (devel) logger.info("__Starting up in development mode__");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
    logger.info(sdf.format(Calendar.getInstance().getTime()) + " -- Database load started for db '"
        + localDb + "'");

    // Handle all the data sources
    for (JSONObject source : sources)
    {
      // Find the proper Loader first
      Loader l = null;
      try
      {
        l = LoaderFactory.getLoader(source);
      }
      catch (NoSuchLoaderException e)
      {
        logger.error("Could not find a loader for data source '"
            + source.optString("name", "unknown") + "' data type '"
            + source.optString("type", "unknown") + "': " + e);
        logger.warn("Continuing processing...");
      }

      // Now process the data from this source
      // NOTE: This assumes two things:
      // 1) The first source in our sources list is the main config file
      //   (always should be, this is safe to assume)
      // 2) processingDir has been set in the file (if not, safe default
      //   of /tmp/vcmap is chosen)
      try
      {
        l.processData(this, source, processingDir);
      }
      catch (JSONException e)
      {
        logger.error("Invalid configuration file for '" + source.optString("name", "unknown")
            + "': " + e);
        logger.warn("Data for previous load may be invalid or incomplete.  Continuing...");
        e.printStackTrace();
      }
      catch (Exception e)
      {
        logger.error("Uncaught exception for '" + source.optString("name", "unknown") + "': " + e);
        e.printStackTrace();
        logger.warn("Data for previous load may be invalid or incomplete.  Continuing...");
      }
    }


    // Finally load all data from the processed directory into the database
    loadData(processingDir, scratchDir);
    
    // Process the synteny data (if configured)
    try
    {
      if (syntenyConfig != null)
        processSynteny(syntenyConfig);
    }
    catch (Exception e)
    {
      logger.error("Problem while processing synteny for database: " + e);
    }

    // Clean up
    if (!cache)
    {
      logger.info("Deleting scratch directory");
      for (File f : scratchDir.listFiles())
        f.delete();
      scratchDir.delete();
    }
    else
    {
      logger.info("Caching files...");
    }

    logger.info("Database load complete.");
  }

  /**
   * Load all files in the specified directory into the database. The data
   * files will be a mixture of assembly data, and annotation sets.  All
   * annotation data files will be a modified GFF3 format without any backward
   * or forward references and will contain all entries that we want to load
   * into the database.  All assemblies will simply contain a file specifying
   * the total set of chromosomes and their lengths, as well as a version for
   * the assembly.
   */
  public void loadData(File processingDir, File scratchDir)
  {
    logger.info("Inserting data from processed files in '" + scratchDir.getAbsolutePath() + 
        "' into the database...");

    // Connect to the db
    Connection conn = null;
    logger.debug("Getting database connection...");
    try
    {
      conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
    }
    catch (SQLException e)
    {
      logger.error("Errors while connecting to db (no data inserted): " + e);
      return;
    }

    // Load any new Assembly data first
    loadAssemblyData(conn, new File(processingDir, "assembly"));
    // Second, load any new Annotation Sets
    loadAnnotationData(conn, new File(processingDir, "annotation"));

    // Finally, recreate all of the links in the database
    try
    {
      if (homologeneConfig != null)
      {
        // First download and handle the homologene dataset (check current version)
        int sourceId = setupHomologene(conn, homologeneConfig);

        // Now rebuild the links
        processLinks(conn, sourceId, homologeneConfig);
      }
    }
    catch (JSONException e)
    {
      logger.error("Syntax problem with the Homologene config (Links may be corrupted in DB): " + e);
      e.printStackTrace();
    }
    catch (IOException e)
    {
      logger.error("Problem grabbing Homologene data (No links will be built)");
      e.printStackTrace();
    }
    catch (SQLException e)
    {
      logger.error("DB error while rebuilding the links:" + e);
    }
    
    logger.info("Finished loading from '" + scratchDir.getAbsolutePath() + "'...");
  }

  /**
   * Expose the scratch directory we are configured to use.
   *
   * @return The {@link File} pointing to the scratch directory.
   */
  public File getScratchDirectory()
  {
    return scratchDir;
  }

  /**
   * Load synteny block data (currently only from UCSC). 
   */
  public void processSynteny(JSONObject source)
  {
    logger.info("Beginning processing of synteny");

    // Connect to the local db
    Statement localSt = null;
    PreparedStatement blockQuery = null;
    try
    {
      Connection conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
      localSt = conn.createStatement();

      // Setup Prepared statement for annotation id queries
      String sql = "SELECT a1.id FROM links l1, links l2, annotation a1, annotation a2 " +
      "WHERE l1.id = l2.id AND l1.annotation_id = a1.id AND l2.annotation_id = a2.id AND " +
      "a1.annotation_set_id = ? AND a2.annotation_set_id = ? AND " +
      "a1.chromosome_id = ? AND a2.chromosome_id = ? AND " +
      "a1.start >= ? AND a1.stop <= ? ORDER BY a1.start";
      blockQuery = conn.prepareStatement(sql);
    }
    catch (SQLException e)
    {
      logger.error("Error while connecting to the VCMap db: " + e);
      logger.error("*** Cannot perform synteny load! ***");
      return;
    }

    // Get some initial parameters from the config
    JSONArray synteny = null;
    int threshold = 0;
    Vector<Integer> levels = new Vector<Integer>();
    try
    {
      synteny = source.getJSONArray("synteny");
      threshold = source.getInt("threshold");
      JSONArray levelsArray = source.getJSONArray("levels");
      for (int i = 0; i < levelsArray.length(); i++)
        levels.add(levelsArray.getInt(0));
    }
    catch (JSONException e)
    {
      logger.error("Configuration problem with UCSC synteny definitions, cannot continue:" + e);
      return;
    }

    //
    // Outside loop for "left" species of the synteny blocks.
    // This will be referred to as the "target" species
    //
    for (int i = 0; i < synteny.length(); i++)
    {
      // Connect to the UCSC db specified
      Statement ucscSt = null;
      String target = "";
      String identifier = "";
      try
      {
        JSONObject syntenyTarget = synteny.getJSONObject(i);
        target = syntenyTarget.getString("name");
        identifier = syntenyTarget.getString("identifier");
        Connection ucscConn = DriverManager.getConnection("jdbc:mysql://"
            + source.getString("hostname") + "/" + syntenyTarget.getString("database"),
            source.getString("username"), source.optString("password", ""));
        ucscSt = ucscConn.createStatement();
      }
      catch (SQLException e)
      {
        logger.error("SQL Error while connecting to UCSC db: " + e);
      }
      catch (JSONException e)
      {
        logger.error("Configuration problem with UCSC synteny definition (index " + i + "): " + e);
        continue;
      }
      
      // Find the map in question, and build a chromosome map
      int targetMapId = 0, targetAsetId = 0;
      HashMap<String, Integer> targetChrMap = new HashMap<String, Integer>();
      try
      {
        String sql = "SELECT m.id, m.default_annotation FROM maps m, versions v " +
          "WHERE m.version_id = v.id AND v.generic = '" + identifier + "' AND m.species = '" + target + "'";
        ResultSet rs = localSt.executeQuery(sql);
        if (rs.next())
        {
          targetMapId = rs.getInt(1);
          targetAsetId = rs.getInt(2);
        }
        else
        {
          logger.warn("No assembly '" + identifier + "' loaded for species " + target);
          continue;
        }
        
        sql = "SELECT name, id FROM chromosomes WHERE map_id = " + targetMapId;
        rs = localSt.executeQuery(sql);
        while (rs.next())
          targetChrMap.put(rs.getString(1), rs.getInt(2));

        // Skip any species without chromosome (bad load)
        if (targetChrMap.keySet().size() == 0)
          throw new SQLException("No chromosomes found for map " + targetMapId + "!");
      }
      catch (SQLException e)
      {
        logger.error("Cannot read target chromosome ids for target " + target + ": " + e);
        continue;
      }

      //
      // Inner loop for all "right" species of the synteny blocks.
      // This will be referred to as the "query" species.
      //
      for (int j = 0; j < synteny.length(); j++)
      {
        // Don't try to load data for query==target
        if (j == i) continue;

        String querySpecies = "", queryTable = "", queryIdentifier = "";
        try
        {
          JSONObject syntenyQuery = synteny.getJSONObject(j);
          querySpecies = syntenyQuery.getString("name");
          queryTable = syntenyQuery.getString("table");
          queryIdentifier = syntenyQuery.getString("identifier");
        }
        catch (JSONException e)
        {
          logger.error("Configuration problem with UCSC synteny definition (index " + j + "): " + e);
        }
        logger.info("Loading for target " + target + " to " + querySpecies + "...");

        // Loop through all of our target chromosomes (ids)
        int queryMapId = 0, queryAsetId = 0;
        HashMap<String, Integer> queryChrMap = new HashMap<String, Integer>();
        try
        {
          String sql = "SELECT m.id, m.default_annotation FROM maps m, versions v " +
            "WHERE m.version_id = v.id AND v.generic = '" + queryIdentifier + 
            "' AND m.species = '" + querySpecies + "'";
          ResultSet rs = localSt.executeQuery(sql);
          if (rs.next())
          {
            queryMapId = rs.getInt(1);
            queryAsetId = rs.getInt(2);
          }
          else
          {
            logger.warn("No assembly '" + queryIdentifier + "' loaded for species " + querySpecies);
            continue;
          }

          sql = "SELECT name, id FROM chromosomes WHERE map_id = " + queryMapId;
          rs = localSt.executeQuery(sql);
          while (rs.next())
            queryChrMap.put(rs.getString(1), rs.getInt(2));

          // Skip any species without chromosome (bad load)
          if (queryChrMap.keySet().size() == 0)
            throw new SQLException("No chromosomes found!");
        }
        catch (SQLException e)
        {
          logger.error("Cannot read chromosome ids for query " + querySpecies + ": " + e);
          continue;
        }

        // Try to grab data from the target "net" table. We are not guaranteed
        // that this table will exist, so we must handle that appropriately.
        try
        {
          String query = "SHOW TABLES LIKE \"" + queryTable + "\"";
          ResultSet rs = ucscSt.executeQuery(query);

          // If no tables exist
          if (!rs.next())
          {
            logger.warn("No synteny data found for query " + querySpecies + " to target " + target
                + ", table '" + queryTable + "' does not exists.\n");
            continue;
          }
        }
        catch (SQLException e)
        {
          // Shouldn't ever happen
          logger.error("Unknown issue checking for existence of net conservation table: " + e);
        }
        
        int partial = 0, complete = 0, correct = 0;
        // Now grab the start and stop positions one chromosome at a time
        // This block will only load synteny for data that exists in the
        // local VCMap database, therefore, it can potentially be a subset of
        // the UCSC net conservation data (if target doesn't have all of the
        // chromosomes that are in the UCSC database).
        for (String targetChr : targetChrMap.keySet())
        {
          try
          {
            // Check for existing synteny (if found, skip this)
            StringBuilder queryIds = new StringBuilder();
            for (int id : queryChrMap.values())
              queryIds.append(queryIds.length() == 0 ? "" : ", ").append(id);
            String sql = "SELECT COUNT(*) FROM synteny " + 
              "WHERE left_id = " + targetChrMap.get(targetChr) + " AND right_id IN (" + queryIds + ")";
            ResultSet rs = localSt.executeQuery(sql);
            if (rs.next())
            {
              if (rs.getInt(1) > 0)
              {
                logger.debug("Synteny already exists from " + target + "(" + targetChr + ") to " + querySpecies + ".");
                continue;
              }
            }

            // Query for UCSC net conservation blocks
            StringBuilder levelStr = new StringBuilder();
            for (int level : levels)
              levelStr.append(levelStr.length() > 0 ? "," : "").append(level);
            String query = "SELECT tStart, tEnd, qStart, qEnd, qName, strand FROM " + queryTable
                + " ";
            query += "WHERE (tEnd - tStart) > " + threshold + " AND level IN (" + levelStr + ") ";
            query += "AND tName = '" + targetChr + "'";
            ResultSet ucscRs = ucscSt.executeQuery(query);

            // Loop through UCSC results
            while (ucscRs.next())
            {
              // For each start/stop block, we must now determine the nearest
              // pieces of homologous annotation

              // Array Format: [leftStartId, leftStopId, rightStartId, rightStopId]
              int[] localIds = new int[4];
              // Array Format: [leftStart, leftStop, rightStart, rightStop]
              int[] ucscPos = new int[4];
              ucscPos[0] = ucscRs.getInt(1);
              ucscPos[1] = ucscRs.getInt(2);
              ucscPos[2] = ucscRs.getInt(3);
              ucscPos[3] = ucscRs.getInt(4);
              int queryChr = (queryChrMap.get(ucscRs.getString(5)) != null) ? queryChrMap.get(ucscRs.getString(5)) : 0;

              // NOTE: (alternative algorithm)
              // First get all of the link ids from start - end on the target (left)
              //   and order them by position in a vector.
              // Then, get all ids from annotation where link id IN (link ids from left)
              //   and order by position in a vector.
              // Finally pick the first and last from the second map (those are known)
              //   and from the first map start at the start/end and look for matches
              //   in 2nd map.

              // Find the left positions first
              blockQuery.setInt(1, targetAsetId);
              blockQuery.setInt(2, queryAsetId);
              blockQuery.setInt(3, targetChrMap.get(targetChr));
              blockQuery.setInt(4, queryChr);
              blockQuery.setInt(5, ucscPos[0]);
              blockQuery.setInt(6, ucscPos[1]);
              ResultSet localRs = blockQuery.executeQuery();
              int lastId = 0;
              while (localRs.next())
              {
                if (localIds[0] == 0) localIds[0] = localRs.getInt(1);
                lastId = localRs.getInt(1);
              }
              localIds[1] = lastId;
              
              // Now get the right ids (swap target / query & use other ucscPos)
              blockQuery.setInt(1, queryAsetId);
              blockQuery.setInt(2, targetAsetId);
              blockQuery.setInt(3, queryChr);
              blockQuery.setInt(4, targetChrMap.get(targetChr));
              blockQuery.setInt(5, ucscPos[2]);
              blockQuery.setInt(6, ucscPos[3]);
              localRs = blockQuery.executeQuery();
              lastId = 0;
              while (localRs.next())
              {
                if (localIds[2] == 0) localIds[2] = localRs.getInt(1);
                lastId = localRs.getInt(1);
              }
              localIds[3] = lastId;

              //
              // Handle situations where nothing homologous can be found on either species
              // NOTE: Adding this processing will reduce the number of "complete mismatches"
              // to 0 for nearly every case (unless no annotation exists), and will build
              // fallback synteny blocks even though no links exist between the two species.
              // this is acceptable behavior as it should only occur for relatively new
              // species, or those decided by NCBI to not be included in Homologene.
              //
              if (localIds[0] == 0 || localIds[1] == 0 || localIds[2] == 0 || localIds[3] == 0)
              {
                // No homologous annotation was found on the left
                logger.warn("For " + target + "(" + targetChr + ") to " + querySpecies + "(" + queryChr + ")" +
                    " no homologous markers were found.  Attempting to load block with no links...");
              }
              
              if (localIds[0] == 0)
              {
                // Left start
                query = "SELECT a1.id FROM annotation a1 " +
                  "WHERE a1.annotation_set_id = " + targetAsetId + " AND " +
                  "a1.start >= " + ucscPos[0] + " AND a1.stop <= " + ucscPos[1] + " AND " +
                  "a1.chromosome_id = " + targetChrMap.get(targetChr) + " " +
                  "ORDER BY a1.start";
                localRs = localSt.executeQuery(query);
                if (localRs.next()) localIds[0] = localRs.getInt(1);
              }
              
              if (localIds[1] == 0)
              {
                // Left stop
                query = "SELECT a1.id FROM annotation a1 " +
                  "WHERE a1.annotation_set_id = " + targetAsetId + " AND " + 
                  "a1.start >= " + ucscPos[0] + " AND a1.stop <= " + ucscPos[1] + " AND " + 
                  "a1.chromosome_id = " + targetChrMap.get(targetChr) + " " + 
                  "ORDER BY a1.start DESC";
                localRs = localSt.executeQuery(query);
                if (localRs.next()) localIds[1] = localRs.getInt(1);
              }
              if (localIds[2] == 0)
              {
                // Right start
                query = "SELECT a2.id FROM annotation a2 " + 
                  "WHERE a2.annotation_set_id = " + queryAsetId + " AND " +
                  "a2.start >= " + ucscPos[2] + " AND a2.stop <= " + ucscPos[3] + " AND " +
                  "a2.chromosome_id = " + queryChr + " ORDER BY a2.start";
                localRs = localSt.executeQuery(query);
                if (localRs.next()) localIds[2] = localRs.getInt(1);
              }
              if (localIds[3] == 0)
              {
                // Right stop
                query = "SELECT a2.id FROM annotation a2 " +
                  "WHERE a2.annotation_set_id = " + queryAsetId + " AND " +
                  "a2.start >= " + ucscPos[2] + " AND a2.stop <= " + ucscPos[3] + " AND " +
                  "a2.chromosome_id = " + queryChr + " ORDER BY a2.start DESC";
                localRs = localSt.executeQuery(query);
                if (localRs.next()) localIds[3] = localRs.getInt(1);
              }

              // Check strand (and swap if necessary)
              if (ucscRs.getString(6).equals("-"))
              {
                int swap = localIds[2];
                localIds[2] = localIds[3];
                localIds[3] = swap;
              }

              //
              // Handle the results
              //
              if (localIds[0] == 0 && localIds[1] == 0 && localIds[2] == 0 && localIds[3] == 0)
              {
                // Absolutely nothing was found anywhere
                complete++;
                logger.debug("Synteny block complete mismatch for:\n" + "Left: "
                    + targetChrMap.get(targetChr) + " Right: " + queryChr + "\n" + "UCSC values: "
                    + targetChr + "(" + ucscPos[0] + ", " + ucscPos[1] + ") " + ucscRs.getString(5)
                    + "(" + ucscPos[2] + ", " + ucscPos[3] + ")\n");
              }
              else if (localIds[0] != 0 && localIds[1] != 0 && localIds[2] != 0 && localIds[3] != 0
                  && localIds[0] != localIds[1] && localIds[2] != localIds[3])
              {
                // Good block found -- insert it
                query = "INSERT INTO synteny ";
                query += "(left_id, right_id, left_start_id, left_stop_id, right_start_id, right_stop_id) ";
                query += "VALUES (" + targetChrMap.get(targetChr) + ", " + queryChr + ", ";
                query += localIds[0] + "," + localIds[1] + "," + localIds[2] + "," + localIds[3] + ")";
                localSt.executeUpdate(query);
                correct++;
              }
              else
              {
                // Either start or stop was missing from just one of the blocks
                partial++;
                logger.debug("Synteny block partial mismatch for:\n" + "Left: "
                    + targetChrMap.get(targetChr) + " Right: " + queryChr + "\n" + "UCSC values: "
                    + targetChr + "(" + ucscPos[0] + ", " + ucscPos[1] + ") " + ucscRs.getString(5)
                    + "(" + ucscPos[2] + ", " + ucscPos[3] + ")\n" + "Local values: "
                    + targetChrMap.get(targetChr) + "(" + localIds[0] + ", " + localIds[1] + ") "
                    + queryChr + "(" + localIds[2] + ", " + localIds[3] + ")");
              }
            }
          }
          catch (SQLException e)
          {
            logger.error("General SQL problem while loading synteny for " + target + ": " + e);
          }

          // Report the number of synteny mismatches
          logger.info("Created " + correct + " synteny blocks for " + target + " chromosome "
                + targetChr + " to " + querySpecies + ".");
          if (complete > 0)
            logger.info("Found " + complete + " complete mismatches for " + target + " chromosome "
                + targetChr + " to " + querySpecies + ".");

          if (partial > 0)
            logger.info("Found " + partial + " partial mismatches for " + target + " chromosome "
                + targetChr + " to " + querySpecies + ".");

          complete = 0;
          partial = 0;
          correct = 0;
        }
      }
      
      // Clean up for next iteration of the loop
      try
      {
        ucscSt.close();
      }
      catch (SQLException e)
      {
        // Do nothing
      }
    }

    // Finish up
    try
    {
      localSt.close();
      blockQuery.close();
    }
    catch (SQLException e)
    {
      // Do nothing
    }
    
    logger.info("Finished processing UCSC synteny.");
  }

  /**
   * Method to gather and prepare the Homologene data for use. 
   * @return
   *   After downloading the file, the current build of the dataset is used to
   *   create a "source" for the data in the database, if it doesn't already
   *   exist.  This "id" will be the return value of this method
   */
  public int setupHomologene(Connection conn, JSONObject source) 
    throws JSONException, IOException, SQLException 
  {
    logger.info("...Grabbing Homologene data...");

    // Make a scratch directory first
    File scratch = new File(scratchDir, "Homologene");
    logger.debug("Creating temporary directory: " + scratch.getAbsolutePath());
    scratch.mkdirs();

    // First create the FTP connection to our source
    FTPClient ftp = new FTPClient();
    String hostname = source.getString("hostname");
    try
    {
      ftp.connect(hostname);
      ftp.login(source.getString("username"), source.getString("password"));
      ftp.enterLocalPassiveMode();
      logger.info("Connected to FTP site at: " + hostname);
    }
    catch (IOException ioe)
    {
      logger.error("Problem connecting to FTP site at (" + hostname + "): " + ioe);
      throw ioe; 
    }

    //
    // Grab the HomoloGene data
    //
    String remoteFile = source.getString("location") + File.separator + source.getString("filename");
    downloadFile(ftp, remoteFile, source.getString("filename"), scratch);

    // Grab the version of the data
    logger.info("Identifying the current version of Homologene");
    String version = "";
    FTPFile[] files = ftp.listFiles(source.getString("location"));
    if (files.length > 0)
    {
      version = files[0].getLink();
    }
 
    // Close FTP connection
    try
    {
      ftp.disconnect();
    }
    catch (IOException ioe)
    {
      // Do nothing
    }
    
    //
    // Grab (or create) the associated source id for this version
    //
    int id = 0;
    String name = source.getString("name") + " " + version;
    String query = "SELECT id FROM sources WHERE name=?";
    PreparedStatement st = conn.prepareStatement(query);
    st.setString(1, name);
    ResultSet rs = st.executeQuery();
    if (rs.next()) id = rs.getInt(1);
    st.close();
    
    if (id == 0)
    {
      // Create a new source
      query = "INSERT INTO sources (name) VALUES (?)";
      st = conn.prepareStatement(query);
      st.setString(1, name);
      st.execute();
      id = getLastInsertId(st);
      st.close();
    }

    return id;
  }

  /**
   * Insert ontology information from the OBO formatted file specified on the command line. This
   * method is a utility method that shortcuts the main processing of this script to simply fill the
   * ontology tree data in the database. Currently this data is mainly treated as a one-time load
   * with very infrequent to no updates. In the future, this method may need to be redone to support
   * more frequent updates to this data, but at this time, we will mainly treat this data as static
   * data.
   *
   * NOTE: This method is a bit of a hack. For one, it assumes (incorrectly) that the file is
   * arranged so that all children of traits will always come later in the file than their parents.
   * This is fine for our purposes of this method, but keep that caveat in mind when reloading data
   * because you may need to rearrange the order of your file.
   *
   * @param ontologyFile
   *          An OBO (http://www.obofoundry.org/) formatted ontology description.
   */
  public void insertOntology(File ontologyFile)
  {
    if (!ontologyFile.exists() || ontologyFile.isDirectory())
    {
      System.err.println("Bad or missing file: " + ontologyFile.getAbsolutePath());
      usage();
    }
    logger.info("Starting Ontology Tree creation from file: " + ontologyFile.getAbsolutePath());
    logger.info("Using database '" + localDb + "'");

    // Connect to the db
    Statement st = null;
    String query = null;
    ResultSet rs = null;
    try
    {
      Connection conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
      st = conn.createStatement();
    }
    catch (SQLException e)
    {
      logger.error("Errors while connecting to db: " + e);
      return;
    }

    // Begin processing the file
    try
    {
      BufferedReader buffer = new BufferedReader(new FileReader(ontologyFile));

      // If the file was successfully opened, insert a "tree" into the database
      query = "INSERT INTO ontology_tree (name, type, version, loaded) "
          + "VALUES ('VCMap Ontology', 'QTL', '', CURDATE())";
      st.executeUpdate(query);

      // Process the file linearly
      int treeId = getLastInsertId(st);
      int nodes = 0, obsoleteNum = 0;
      String date = null;
      while (buffer.ready())
      {
        String line = buffer.readLine();

        // The only header line we care about is the date (for "version")
        if (date == null && line.matches("date:.*"))
        {
          date = line.substring(6).trim();
          query = "UPDATE ontology_tree SET version = '" + date + "' WHERE id = " + treeId;
          st.executeUpdate(query);
        }

        // Now look for a [Term] line to open a processing block
        if (line.matches("\\[Term\\]"))
        {
          String name = "", parent = "", desc = "";
          boolean obsolete = false;
          while (buffer.ready())
          {
            String innerLine = buffer.readLine();
            if (innerLine.matches("is_obsolete:\\s*true"))
            {
              obsolete = true;
              break;
            }
            else if (innerLine.matches("is_a:.*\\s+!\\s+.*")
                || innerLine.matches("relationship:\\s*part_of\\s+\\S*\\s+!\\s+.*"))
            {
              parent = innerLine.substring(innerLine.lastIndexOf("!") + 1).trim();
              parent = parent.replaceAll("'", "\\\\'");
            }
            else if (innerLine.matches("name:.*"))
            {
              name = innerLine.substring(5).trim();
              name = name.replaceAll("'", "\\\\'");
            }
            else if (innerLine.matches("comment:.*"))
            {
              desc = innerLine.substring(8).trim();
              desc = desc.replaceAll("'", "\\\\'");
            }
            else if (innerLine.trim().equals(""))
            {
              // Blank lines signify the end of this term (far as I can tell)
              break;
            }
          }

          // Determine our parent (or -1 if we are a root)
          if (!obsolete)
          {
            int parentId = -1, childId = -1;
            query = "SELECT id FROM ontology_node WHERE category = '" + parent + "'";
            rs = st.executeQuery(query);
            if (rs.next()) parentId = rs.getInt(1);

            // Now insert the data we just gathered
            query = "INSERT INTO ontology_node (tree_id, parent_node_id, category, description) "
                + "VALUES (" + treeId + ", " + (parentId == -1 ? "null" : parentId) + ", '" + name
                + "', '" + desc + "')";
            st.executeUpdate(query);
            childId = getLastInsertId(st);
            nodes++;

            // And add the child to the parent (if appropriate)
            if (parentId != -1 && childId != -1)
            {
              query = "INSERT INTO ontology_children (node_id, child_id) VALUES (" + parentId
                  + ", " + childId + ")";
              st.executeUpdate(query);
            }
          }
          else
          {
            obsoleteNum++;
          }
        }
      }

      logger.info("Done processing file " + ontologyFile.getAbsolutePath());
      logger.info("Inserted " + nodes + " terms, " + obsoleteNum + " obsolete terms.");
    }
    catch (IOException e)
    {
      logger.error("IO Exception while processing " + ontologyFile.getAbsolutePath() + ": " + e);
      e.printStackTrace();
    }
    catch (SQLException e)
    {
      logger.error("SQL Exception while processing " + ontologyFile.getAbsolutePath() + ": " + e);
      logger.error("Last SQL: " + query);
      e.printStackTrace();
    }
  }

  /*
   * (non-javadoc) Helper method to search our the scratch location for a cached copy of a given
   * file. This method should correctly report whether or not this file is cached, even if it has
   * been uncompressed.
   */
  private static boolean isCached(File dir, String file)
  {
    if (new File(dir, file).exists()) return true;

    // Check for a compression extension on the filename
    if (file.endsWith(".gz")) file = file.substring(0, file.lastIndexOf("."));
    return new File(dir, file).exists();
  }

  /*
   * (non-javadoc) Helper method for getting the last insert id from the current statment.
   */
  private static int getLastInsertId(Statement st) throws SQLException
  {
    String query = "SELECT LAST_INSERT_ID()";
    ResultSet rs = st.executeQuery(query);
    if (rs.next()) return rs.getInt(1);
    return -1;
  }
  
  /**
   * Deletes all database entries relevant to the given map id.  Prompts the
   * user with the human name of the map / species / version info so that
   * they can verify the correct id was supplied.  As a result, this method
   * cannot be run in an automated fashion as it reads input from stdin.
   *
   * @param mapId
   *   The id of the map to clear from the database. 
   *
   * @author sgdavis@bioneos.com
   */
  public void clearMapFromDatabase(int mapId)
  {
    // Connect to the db
    Connection conn = null;
    logger.debug("Getting database connection...");
    try
    {
      conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
    }
    catch (SQLException e)
    {
      logger.error("Errors while connecting to db (no data inserted): " + e);
      return;
    }

    try
    {
      // Read information about this map and prompt the user to continue
      Statement st = conn.createStatement();
      String query = "SELECT species, maps.type, generic, sources.name, loaded, COUNT(map_id) " +
        "FROM sources, versions, maps LEFT JOIN annotation_sets ON map_id=maps.id " + 
        "WHERE maps.id=" + mapId + " AND maps.version_id=versions.id AND " +
        "versions.source_id=sources.id GROUP BY map_id";
      ResultSet rs = st.executeQuery(query);
      if (rs.next())
      {
        System.out.println("\nAre you sure you want to delete the following map?");
        System.out.println("\nSpecies:     " + rs.getString(1));
        System.out.println("Map Type:    " + rs.getString(2));
        System.out.println("Map Version: " + rs.getString(3));
        System.out.println("Source:      " + rs.getString(4));
        System.out.println("Loaded on:   " + rs.getString(5));
        System.out.println("(Associated with " + rs.getInt(6) + " Annotation Sets)\n");

        try
        {
          BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
          while (true)
          {
            System.out.print("Please enter 'yes' or 'no'>  ");
            String input = in.readLine();
            if (input.equals("yes")) break;
            if (input.equals("no")) return;
            System.out.println("\nYou must answer either exactly 'yes' or 'no'\n");
          }
          in.close();
        }
        catch (IOException e)
        {
          System.err.println("Problem reading user input.  (No stdin stream?)");
          e.printStackTrace();
          return;
        }
      }
      else
      {
        System.err.println("Cannot find map with id " + mapId);
        return;
      }

      logger.info("Beginning map clear for id " + mapId);

      // Query for all chromosome ids that match the specified map id
      Vector<Integer> chrIds = new Vector<Integer>();
      query = "SELECT id FROM chromosomes WHERE map_id=" + mapId;
      rs = st.executeQuery(query);
      while (rs.next())
        chrIds.add(rs.getInt(1));

      // Build a string containing all comma-separated list of ids
      StringBuilder chrStr = new StringBuilder();
      for (Integer id : chrIds)
        chrStr.append(chrStr.length() == 0 ? "" : ", ").append(id);

      // Verify we have found chromosomes to delete
      if (chrStr.length() == 0)
      {
        logger.error("Cannot find any chromosomes for map id " + mapId + ", possible data corruption?");
        return;
      }

      // Delete all synteny entries that match our chromosomes
      logger.info("Deleting synteny...");
      query = "DELETE FROM synteny WHERE left_id IN (" + chrStr + ") OR right_id IN (" + chrStr + ")";
      st.executeUpdate(query);
      
      // Query for our annotation_set ids (if any)
      Vector<Integer> asetIds = new Vector<Integer>();
      query = "SELECT id FROM annotation_sets WHERE map_id=" + mapId;
      rs = st.executeQuery(query);
      while (rs.next())
        asetIds.add(rs.getInt(1));

      // Build a string containing all comma-separated list of ids
      StringBuilder asetStr = new StringBuilder();
      for (Integer id : asetIds)
        asetStr.append(asetStr.length() == 0 ? "" : ", ").append(id);

      // Query for our version ids
      Vector<Integer> verIds = new Vector<Integer>();
      query = "SELECT version_id FROM maps WHERE id=" + mapId;
      rs = st.executeQuery(query);
      if (rs.next()) verIds.add(rs.getInt(1));
      if (asetStr.length() > 0)
      {
        query = "SELECT version_id from annotation_sets where id IN (" + asetStr + ")";
        rs = st.executeQuery(query);
        while (rs.next())
          verIds.add(rs.getInt(1));
      }

      // Build a string containing all comma-separated list of ids
      StringBuilder verStr = new StringBuilder();
      for (Integer id : verIds)
        verStr.append(verStr.length() == 0 ? "" : ", ").append(id);

      // Query for all of our annotation ids
      Vector<Integer> annoIds = new Vector<Integer>();
      if (asetStr.length() > 0)
      {
        query = "SELECT id FROM annotation WHERE annotation_set_id IN (" + asetStr + ")";
        rs = st.executeQuery(query);
        while (rs.next())
          annoIds.add(rs.getInt(1));
      }
     
      // Build a string containing all comma-separated list of ids
      StringBuilder annoStr = new StringBuilder();
      for (Integer id : annoIds)
        annoStr.append(annoStr.length() == 0 ? "" : ", ").append(id);

      // Delete all annotation AVPs that match our chromosomes
      if (annoStr.length() > 0)
      {
        logger.info("Deleting annotation AVPs...");
        query = "DELETE FROM annotation_avps WHERE annotation_id IN (" + annoStr + ")";
        st.executeUpdate(query);
        // NOTE: orphaned attributes / vals could be detected with a variation on
        //   the following query, if we wanted to find / remove those:
        //
        //   * SELECT a.id, a.type FROM attributes a LEFT JOIN annotation_avps v 
        //     ON a.id=v.attribute_id WHERE v.attribute_id IS NULL;

        // Delete all annotation rows
        logger.info("Deleting all annotations...");
        query = "DELETE FROM annotation WHERE annotation_set_id IN (" + asetStr + ")";
        st.executeUpdate(query);
      }
      else
      {
        logger.info("No annotation to delete.");
      }

      // Delete all annotation_sets for this map
      if (asetStr.length() > 0)
      {
        logger.info("Deleting all annotation sets...");
        query = "DELETE FROM annotation_sets WHERE map_id = " + mapId;
        st.executeUpdate(query);
      }
      else
      {
        logger.info("No annotation sets to delete.");
      }

      // Delete all chromosomes
      logger.info("Deleting chromosomes...");
      query = "DELETE FROM chromosomes WHERE map_id=" + mapId;
      st.executeUpdate(query);

      // Delete the map row
      logger.info("Deleting map row...");
      query = "DELETE FROM maps WHERE id=" + mapId;
      st.executeUpdate(query);
      
      // Delete the versions
      logger.info("Deleting versions rows...");
      query = "DELETE FROM versions WHERE id IN (" + verStr + ")";
      st.executeUpdate(query);
    }
    catch (SQLException e)
    {
      logger.error("There was an error while clearing outdated map data:" + e);
    }

    logger.info("Map delete completed!");
  }
  
  /**
   * Deletes all database entries relevant to the given annotation set id.  
   * Prompts the user with the human name of the version info so that
   * they can verify the correct id was supplied.  As a result, this method
   * cannot be run in an automated fashion as it reads input from stdin.
   *
   * @param asetId
   *   The id of the annotation set to clear from the database. 
   *
   * @author sgdavis@bioneos.com
   */
  public void clearAsetFromDatabase(int asetId)
  {
    // Connect to the db
    Connection conn = null;
    logger.debug("Getting database connection...");
    try
    {
      conn = DriverManager.getConnection(localDb, localDbUser, localDbPass);
    }
    catch (SQLException e)
    {
      logger.error("Errors while connecting to db (no data inserted): " + e);
      return;
    }

    try
    {
      // Read information about this map and prompt the user to continue
      Statement st = conn.createStatement();
      String query = "SELECT species, maps.type, s.type, sources.name, generic, loaded " +
        "FROM sources, versions, maps RIGHT JOIN annotation_sets s ON maps.id=s.map_id " + 
        "WHERE s.id=" + asetId + " AND s.version_id=versions.id AND " +
        "versions.source_id=sources.id";
      ResultSet rs = st.executeQuery(query);
      if (rs.next())
      {
        System.out.println("\nAre you sure you want to delete the following annotation set?");
        System.out.println("\nSpecies:     " + rs.getString(1));
        System.out.println("Map Type:    " + rs.getString(2));
        System.out.println("Set Type:    " + rs.getString(3));
        System.out.println("Source:      " + rs.getString(4));
        System.out.println("Identifier:  " + rs.getString(5));
        System.out.println("Loaded on:   " + rs.getString(6));

        try
        {
          BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
          while (true)
          {
            System.out.print("Please enter 'yes' or 'no'>  ");
            String input = in.readLine();
            if (input.equals("yes")) break;
            if (input.equals("no")) return;
            System.out.println("\nYou must answer either exactly 'yes' or 'no'\n");
          }
          in.close();
        }
        catch (IOException e)
        {
          System.err.println("Problem reading user input.  (No stdin stream?)");
          e.printStackTrace();
          return;
        }
      }
      else
      {
        System.err.println("Cannot find annotation set with id " + asetId);
        return;
      }

      logger.info("Beginning annotation_set clear for id " + asetId);
      
      // Query for our version id
      int verId = -1;
      query = "SELECT version_id from annotation_sets where id = " + asetId;
      rs = st.executeQuery(query);
      if (rs.next())
        verId = rs.getInt(1);

      // Query for all of our annotation ids
      Vector<Integer> annoIds = new Vector<Integer>();
      query = "SELECT id FROM annotation WHERE annotation_set_id = " + asetId;
      rs = st.executeQuery(query);
      while (rs.next())
        annoIds.add(rs.getInt(1));
     
      // Build a string containing all comma-separated list of ids
      StringBuilder annoStr = new StringBuilder();
      for (Integer id : annoIds)
        annoStr.append(annoStr.length() == 0 ? "" : ", ").append(id);
  
      // Delete all annotation AVPs that match our chromosomes
      if (annoStr.length() > 0)
      {
        logger.info("Deleting annotation AVPs...");
        query = "DELETE FROM annotation_avps WHERE annotation_id IN (" + annoStr + ")";
        st.executeUpdate(query);
        // NOTE: orphaned attributes / vals could be detected with a variation on
        //   the following query, if we wanted to find / remove those:
        //
        //   * SELECT a.id, a.type FROM attributes a LEFT JOIN annotation_avps v 
        //     ON a.id=v.attribute_id WHERE v.attribute_id IS NULL;
  
        // Delete all annotation rows
        logger.info("Deleting all annotations...");
        query = "DELETE FROM annotation WHERE annotation_set_id = " + asetId;
        st.executeUpdate(query);
      }
      else
      {
        logger.info("No annotation to delete.");
      }
  
      // Delete the annotation_set row
      logger.info("Deleting annotation set row...");
      query = "DELETE FROM annotation_sets WHERE id = " + asetId;
      st.executeUpdate(query);
  
      // Delete the version row
      logger.info("Deleting versions rows...");
      query = "DELETE FROM versions WHERE id = " + verId;
      st.executeUpdate(query);
    }
    catch (SQLException e)
    {
      logger.error("There was an error while clearing annotation set data:" + e);
    }
  
    logger.info("Annotation set clear completed!");
  }

  /**
   * <p>
   * Downloads the remote file located at the path described by <code>remoteFileName</code>. If an
   * error occurs while downloading, retry one time before failing.
   * <p>
   * If caching is enabled and the file already exists, this method will automatically skip the
   * specified file..
   *
   * @param ftp
   *          The {@link FTPClient} object to handle FTP communication.
   * @param remoteFileName
   *          A <code>String</code> containing the path of the desired remote file.
   * @param localFileName
   *          A <code>String</code> containing the desired filename of the downloaded file.
   * @param dest
   *          A <code>File</code> object representing the local directory in which downloaded files
   *          are to be placed.
   * @return A <code>boolean</code> value to note success or failure of the method.
   *
   * @author dquacken@bioneos.com, sgdavis@bioneos.com
   */
  public boolean downloadFile(FTPClient ftp, String remoteFileName, String localFileName,
      File dest)
  {
    if (cache && isCached(dest, localFileName))
    {
      logger.debug("Using cached file: " + localFileName);
      return true;
    }
    else
    {
      logger.debug("Downloading file: " + remoteFileName);
    }

    // Download the file
    FileOutputStream localFileDestination = null;
    try
    {
      localFileDestination = new FileOutputStream(new File(dest, localFileName));
    }
    catch (FileNotFoundException e)
    {
      logger.error("Cannot open (" + dest + File.separator + localFileName + ") for writing:" + e);
      return false;
    }

    int attempts = 0;
    boolean success = false;
    while (!success && attempts < 3)
    {
      // Make sure to mark the attempt before any potential errors
      // to avoid an infinite loop
      attempts++;

      try
      {
        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        success = ftp.retrieveFile(remoteFileName, localFileDestination);
        if (success)
        {
          logger.debug("File " + remoteFileName + " successfully retrieved");
        }
        else
        {
          // wait for 10 seconds and then retry
          logger.debug("File retrieval failed, waiting 10 seconds before reattempting...");
          try
          {
            Thread.sleep(DOWNLOAD_RETRY_DELAY);
          }
          catch (InterruptedException e)
          {
            // Do nothing
          }
        }
      }
      catch (IOException e)
      {
        success = false;
        logger.error("Problem retreiving file (" + remoteFileName + "): " + e);
      }
    }

    // Log retry attempt overrun
    if (attempts >= MAX_DOWNLOAD_ATTEMPTS)
      logger.error("Too many attempts to retreive file: " + remoteFileName);

    // Clean up
    try
    {
      localFileDestination.close();
    }
    catch (IOException e)
    {
      // Probably never will happen
      logger.debug("Error closing local file??: " + e);
    }
    return true;
  }

  /**
   * Recursively delete a directory (or delete a file).  USE WITH CAUTION.
   */
  public void recursiveDelete(File del)
  {
    if (del.isDirectory())
    {
      for (File f : del.listFiles())
        recursiveDelete(f);
      del.delete();
    }
    else
    {
      del.delete();
    }
  }

  /**
   * Computes the MD5 hash for a file.
   *
   * @param input
   *   A <code>String</code> containing the pathname of the file for which to
   *   compute the hash.
   * @return 
   *   A <code>String</code> containing the hex value of the hash. Or 'null' if 
   *   provided with an invalid file parameter.
   *
   * @author dquacken@bioneos.com
   */
  public static BigInteger computeHash(File input)
  {
    InputStream is = null;
    byte[] buffer = new byte[8192];
    int read = 0;
    BigInteger output = null;

    try
    {
      MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
      if (input != null && input.exists())
        is = new FileInputStream(input);
      else
        return null;

      while ((read = is.read(buffer)) > 0)
        digest.update(buffer, 0, read);

      byte[] md5sum = digest.digest();
      output = new BigInteger(1, md5sum);
    }
    catch (IOException e)
    {
      logger.error("Error calculating md5 checksum: " + e);
    }
    catch (NoSuchAlgorithmException e)
    {
      logger.error("Error instantiating MessageDigest: " + e);
    }
    finally
    {
      try
      {
        if (is != null) is.close();
      }
      catch (IOException e)
      {
        logger.error("Unable to close input stream for MD5 calculation: " + e);
      }
    }
    return output;
  }
  
  /**
   * Helper method to properly URL escape the special values as defined by the GFF3 spec:
   *   http://www.sequenceontology.org/gff3.shtml
   * Under the DESCRIPTION OF THE FORMAT header. 
   * @param raw
   *   The string to escape.
   * @return
   *   The escaped String.
   */
  public static String escapeGff3(String raw)
  {
    // Must do percent first
    String ret = raw.replaceAll("%", "%25");
    // Special chars (NOTE: skipping control chars)
    ret = raw.replaceAll("\t", "%09").replaceAll("\n", "%0A").replaceAll("\r", "%0D");
    // Reserved chars (except for "%")
    ret = ret.replaceAll(";", "%3B").replaceAll("=", "%3D").replaceAll("&", "%26").replaceAll(",", "%2C");
    return ret;
  }
  /**
   * Helper method to properly unescape the special values from a GFF3 formatted file:
   *   http://www.sequenceontology.org/gff3.shtml
   * Under the DESCRIPTION OF THE FORMAT header. 
   * @param encoded
   *   The encoded String from the GFF file.
   * @return
   *   The raw String.
   */
  public static String unescapeGff3(String encoded)
  {
    // Special chars (NOTE: skipping control chars)
    String ret = encoded.replaceAll("%09", "\t").replaceAll("%0A", "\n").replaceAll("%0D", "\r");
    // Reserved chars
    ret = ret.replaceAll("%3B", ";").replaceAll("%3D", "=").replaceAll("%26", "&").replaceAll("%2C", ",");
    // Finally the remaining percents
    ret = ret.replaceAll("%25", "%");
    return ret;
  }

  /*
   * Helper method to build a JSONObject[] referencing the configuration 
   * objects that describe the types supplied in the function parameters.  If
   * nothing is found, this method returns 'null'.  If an array is returned, it
   * will always have 4 elements, always in the order of (species,  in the  
   */
  private JSONObject[] findConfig(String sourceName, String speciesName, String mapType, String identifier, String assembly)
  {
    JSONObject[] config = null;
    for (int i = 0; i < sources.size() && config == null; i++)
    {
      JSONObject source = sources.get(i);
      try
      {
        logger.debug("Checking against source '" + source.getString("name") + "'");
        if (source.getString("name").equals(sourceName))
        {
          JSONArray species = source.getJSONArray("species");
          for (int j = 0; j < species.length() && config == null; j++)
          {
            JSONObject speciesObj = species.getJSONObject(j);
            logger.debug("Checking against species '" + speciesObj.getString("name") + "'");
            if (speciesObj.getString("name").equals(speciesName))
            {
              JSONArray data = speciesObj.has("maps") ? speciesObj.getJSONArray("maps") : speciesObj.getJSONArray("annotations");
              for (int k = 0; k < data.length() && config == null; k++)
              {
                JSONObject dataObj = data.getJSONObject(k);
                String type = speciesObj.has("maps") ? dataObj.optString("type", "unknown") : dataObj.optString("assembly_type", "unknown"); 
                logger.debug("Checking against map type '" + type + "'");
                if (type.equals(mapType))
                {
                  if (assembly != null && !assembly.equals(dataObj.optString("assembly_version")))
                  {
                    logger.debug("Target assembly doesn't match '" + assembly + "'");
                    continue;
                  }
                  JSONObject version = dataObj.getJSONObject("version");
                  logger.debug("Checking against version '" + version.optString("identifier", "") + "'");
                  if (version.has("identifier") && version.getString("identifier").equals(identifier))
                  {
                    logger.debug("Found matching config file (#" + i + ") for source '" + sourceName + "'" + 
                        ", species '" + speciesName + "', map type '" + mapType + "'" + 
                        ", version '" + version.getString("identifier") + "'");
                    config = new JSONObject[4];
                    config[0] = source;
                    config[1] = speciesObj;
                    config[2] = dataObj;
                    config[3] = version;
                  }
                  else if (!version.has("identifier"))
                  {
                    logger.warn("A version identifier was not associated with a configuration object, " +
                                "please verify that the loaders are working correctly for source: " + source.getString("name"));
                  }
                }
              }
            }
          }
        }
      }
      catch (JSONException e)
      {
        logger.warn("Probable invalid config files (source #" + i + "): " + e);
      }
    }
    
    // Can be returning null
    return config;
  }

  /**
   * <p>Load the 'annotation set' data from the processing directory's 
   * 'Annotation' subdirectory.</p>
   * @param processingDirectory
   *   The directory for all annotation set processed data files in the 
   *   processing temp dir.
   */
  public void loadAnnotationData(Connection conn, File processingDirectory)
  {
    logger.info("Loading Annotations...");

    // Loop through the source directories
    for (File sourceDir : processingDirectory.listFiles())
    {
      int sourceId = -1;
      String query = "SELECT id FROM sources WHERE name = ?";
      PreparedStatement getSourceId = null;
      try
      {
        getSourceId = conn.prepareStatement(query);
        getSourceId.setString(1, sourceDir.getName());
        ResultSet rs = getSourceId.executeQuery();
        if (rs.next())
          sourceId = rs.getInt(1);
        else
          throw new SQLException("Query '" + query + "' returned no results for '" + sourceDir.getName() + "'");
      }
      catch (SQLException e)
      {
        logger.error("Cannot find corresponding source entry for " + sourceDir.getName() + ": " + e);
        logger.error("  Cannot continue...");
        continue;
      }
      finally
      {
        try
        {
          if (getSourceId != null) getSourceId.close();
        }
        catch (SQLException e)
        {
          // Do nothing
        }
      }
      logger.debug("Annotation Source: " + sourceDir.getName() + ", id: " + sourceId + "...");

      // Loop for each species
      for (File speciesDir : sourceDir.listFiles())
      {
        logger.debug("  Species: " + speciesDir.getName() + "...");

        // Loop for each map type
        for (File mapDir : speciesDir.listFiles())
        {
          logger.debug("    Map type: " + mapDir.getName() + "...");

          // Loop through the files in the type directory
          for (File filename : mapDir.listFiles())
          {
            logger.debug("      File Name: " + filename.getName() + "...");
            Pattern p = Pattern.compile("data-(.*):(.*)\\.gff3");
            Matcher m = p.matcher(filename.getName());
            if (!m.matches())
            {
              logger.error("Extraneous file '" + filename.getName() +"' in the processing directory!  Skipping...");
              continue;
            }
            
            // Otherwise, use the identifier to match this with a version in
            // the database, and a config file in memory
            String assembly = m.group(1);
            String identifier = m.group(2);
            logger.debug("Version identifier: " + identifier + " for assembly: " + assembly);
            
            // Now we must check for a corresponding JSON config.  
            JSONObject[] config = findConfig(sourceDir.getName(), speciesDir.getName(), mapDir.getName(), identifier, assembly);

            // If no config was found, we simply continue looping files
            if (config == null)
            {
              logger.warn("Found processed file but no corresponding JSON config, skipping this file...");
              continue;
            }

            //
            // Now, check for the existing version row in the database
            //
            int versionId = -1;
            int mapId = -1;
            String sql = "SELECT v.id, a.id, m.id FROM versions v RIGHT JOIN annotation_sets a ON a.version_id = v.id " + 
            "LEFT JOIN maps m ON a.map_id = m.id " +
            "WHERE ((v.build = ? AND v.version = ?) OR (v.release_date = ? AND v.md5 = ?) OR " +
            "(v.release_date = ? AND v.generic = ?)) " + 
            "AND v.source_id = ? AND m.species = ? AND m.type = ?";
            try
            {
              int build = 0, version = 0;
              SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy");
              if (identifier.matches("^\\d+$"))
              {
                build = Integer.parseInt(identifier);
              }
              else if (identifier.matches("^\\d+\\.\\d+$"))
              {
                build = Integer.parseInt(identifier.substring(0, identifier.indexOf('.')));
                version = Integer.parseInt(identifier.substring(identifier.indexOf('.') + 1));
              }
                
              PreparedStatement st = conn.prepareStatement(sql);
              java.sql.Date release = new java.sql.Date(sdf.parse(config[3].getString("release_date")).getTime());
              st.setInt(1, build);
              st.setInt(2, version);
              st.setDate(3, release);
              st.setString(4, config[3].optString("md5", ""));
              st.setDate(5, release);
              st.setString(6, identifier);
              st.setInt(7, sourceId);
              st.setString(8, config[1].getString("name"));
              st.setString(9, config[2].getString("assembly_type"));
              ResultSet rs = st.executeQuery();

              boolean alreadyLoaded = false;
              int vId = -1, asetId = -1;
              while (rs.next())
              {
                logger.debug("Found a version row (" + rs.getInt(1) + "), now checking assembly target...");
                // Must test to make sure that assembly target is the same
                sql = "SELECT v.id FROM versions v, maps m WHERE m.version_id=v.id AND " +
                  "v.generic = ? AND m.id=?";
                PreparedStatement st2 = conn.prepareStatement(sql);
                st2.setString(1, config[2].getString("assembly_version"));
                st2.setInt(2, rs.getInt(3));
                ResultSet rs2 = st2.executeQuery();
                if (rs2.next())
                {
                  vId = rs.getInt(1);
                  asetId = rs.getInt(2);
                  alreadyLoaded = true;
                }
                st2.close();
              }
              st.close();

              // Finally check for an md5 match
              // NOTE: We don't constrain this by map or map type or even
              //   species due to the fact that it is highly unlikely that any
              //   data files would share an overlapping md5 checksum, and
              //   must have already been loaded for some other dataset.
              // However, one exception to this would be data files that contain
              //   multiple data types.  For example, gene & pseudogene.  That
              //   is the main motivation behind combining those two feature
              //   types in this version of the load script.
              if (!alreadyLoaded && config[3].has("md5"))
              {
                sql = "SELECT v.id, s.id, v.md5 FROM versions v RIGHT JOIN annotation_sets s ON v.id=s.version_id " + 
                  "WHERE v.md5 = ?";
                PreparedStatement st2 = conn.prepareStatement(sql);
                st2.setString(1, config[3].optString("md5", ""));
                ResultSet rs2 = st2.executeQuery();
                if (rs2.next())
                {
                  logger.warn("Found an annotation_set with a matching MD5 checksum (" + rs2.getString(3) +
                      ").  Although this potentially may not be the same data because of differences " +
                      "in the release date or identifier, it is highly unlikely that this data " +
                      "differs.  Please verify manually if this data needs to be loaded...");
                  vId = rs2.getInt(1);
                  asetId = rs2.getInt(2);
                  alreadyLoaded = true;
                }
                st2.close();
              }
              
              if (alreadyLoaded)
              {
                logger.info("Skipping annotation_set for " + config[1].getString("name") + " from " + config[0].getString("name") + 
                    ", already loaded with matching version info (id: " + vId + ") aset id: " + asetId);
                continue;
              }
              else
              {
                logger.debug("Annotation set not found.  Proceeding with load...");
              }
              
              // Now check for existing matching identifier
              sql = "SELECT COUNT(*) FROM versions v RIGHT JOIN annotation_sets a ON v.id = a.version_id " + 
                "LEFT JOIN maps m ON a.map_id = m.id " +
                "WHERE v.generic = ? AND v.source_id = ? AND m.species = ? AND m.type = ?";
              st = conn.prepareStatement(sql);
              st.setString(1, identifier);
              st.setInt(2, sourceId);
              st.setString(3, config[1].getString("name"));
              st.setString(4, config[2].getString("type"));
              rs = st.executeQuery();
              
              if (rs.next() && rs.getInt(1) != 0)
              {
                logger.warn("Annotation set identified with different version information, but matching identifier of '" + identifier + "'.  " +
                    "Loading this dataset, but please verify that the configuration files are up-to-date...");
                // TODO - email the administrator, configs are almost certainly out of date
              }
              st.close();
              
              // Finally check for a matching "assembly_version" (without which
              // we cannot load any data)
              sql = "SELECT m.id FROM versions v RIGHT JOIN maps m ON v.id = m.version_id " +
                "WHERE v.generic = ? AND m.type = ? AND m.species = ?";
              st = conn.prepareStatement(sql);
              st.setString(1, config[2].getString("assembly_version"));
              st.setString(2, config[2].getString("assembly_type"));
              st.setString(3, config[1].getString("name"));
              rs = st.executeQuery();
              
              if (rs.next()) mapId = rs.getInt(1);
              else
              {
                logger.error("Cannot load this annotation set because missing assembly: " +
                    config[2].getString("assembly_version") + " for type " + config[2].getString("assembly_type"));
                st.close();
                continue;
              }
              st.close();
              
              // Load a new version with our information
              String fields = "loaded, source_id, generic, release_date, human_name";
              String values = "CURDATE(), ?, ?, ?, ?";
              if (build != 0 && version != 0)
              {
                fields += ", build, version";
                values += ", ?, ?";
              }
              else if (!config[3].optString("md5", "").equals(""))
              {
                fields += ", md5";
                values += ", ?";
              }
              
              sql = "INSERT INTO versions (" + fields + ") VALUES (" + values + ")";
              st = conn.prepareStatement(sql);
              st.setInt(1, sourceId);
              st.setString(2, identifier);
              st.setDate(3, new java.sql.Date(sdf.parse(config[3].getString("release_date")).getTime()));
              st.setString(4, identifier);
              if (build != 0 && version != 0)
              {
                st.setInt(5, build);
                st.setInt(6, version);
              }
              else if (!config[3].optString("md5", "").equals(""))
              {
                st.setString(5, config[3].getString("md5"));
              }
              st.execute();
              versionId = getLastInsertId(st);
              st.close();

              logger.warn("*** New version information created for annotation_set (" + versionId + " - " + identifier +
              		") - please manually update the human_name field in the database. ***");
              // TODO - email the administrator
            }
            catch (ParseException e)
            {
              logger.error("Bad value for 'release_date' in JSON Config, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            catch (JSONException e)
            {
              logger.error("JSON configuration problem during annotation load, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            catch (SQLException e)
            {
              logger.error("Version identification problem during annotation load, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            
            // 
            // Actually create the annotation set
            //
            int asetId = -1;
            PreparedStatement st = null;
            try
            {
              sql = "INSERT INTO annotation_sets (version_id, map_id, type) VALUES (?, ?, ?)";
              st = conn.prepareStatement(sql);
              st.setInt(1, versionId);
              st.setInt(2, mapId);
              st.setString(3, config[2].optString("type", "UNKNOWN"));
              st.execute();
              
              asetId = getLastInsertId(st);
            }
            catch (SQLException e)
            {
              logger.error("Cannot create annotation_set in database, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            finally
            {
              try
              {
                if (st != null) st.close();
              }
              catch (SQLException e)
              {
                // Do Nothing
              }
            }
            
            // And load all of the data
            processAnnotations(conn, filename, asetId, config);
          }
        }
      }
    }
    logger.info("Finished loading Annotations...");
  }

  /**
   * <p>This method processes the annotations in the specified File.  These
   * features are loaded based on the information in the supplied JSONObject
   * config array.</p>
   * @param conn
   *   The connection to the server mysql database.
   * @param filename
   *   The file containing the annotations to insert for the specified
   *   annotation set.
   * @param asetId
   *   The annotation_set_id for this set of annotations.
   * @param config
   *   An array of {@link JSONObject}s that represent the proper configuration
   *   for this annotation_set.
   */
  private void processAnnotations(Connection conn, File filename, int asetId, JSONObject[] config)
  {
    //
    // This block seems somewhat inefficient, but it is really necessary to
    // enable the quickest processing of the annotation as possible.  The
    // reason is that we need to use PreparedStatements in order to reduce the
    // overhead of opening Statements for every DB operation in this method.
    // 
    String query = "";
    PreparedStatement attIdTest = null;
    PreparedStatement attIdAdd = null;
    PreparedStatement valIdTest = null;
    PreparedStatement valIdAdd = null;
    PreparedStatement annotAvpsTest = null;
    PreparedStatement annotAvpsAdd = null;
    PreparedStatement annotInsert = null;
    PreparedStatement ontologyCheck = null;
    try
    {
      query = "SELECT id FROM attributes WHERE type=?";
      attIdTest = conn.prepareStatement(query);

      query = "INSERT INTO attributes (type) VALUES (?)";
      attIdAdd = conn.prepareStatement(query);

      query = "SELECT id FROM vals WHERE sha1=SHA1(?)";
      valIdTest = conn.prepareStatement(query);

      query = "INSERT INTO vals (value, sha1) VALUES (?, SHA1(?))";
      valIdAdd = conn.prepareStatement(query);

      query = "SELECT COUNT(*) FROM annotation_avps avp, attributes a, vals v " +
        "WHERE avp.attribute_id = a.id AND avp.value_id=v.id AND " +
        "avp.annotation_id=? AND a.type=? AND v.sha1=SHA1(?)";
      annotAvpsTest = conn.prepareStatement(query);

      query = "INSERT INTO annotation_avps (annotation_id, attribute_id, value_id) VALUES (?, ?, ?)";
      annotAvpsAdd = conn.prepareStatement(query);
    }
    catch (SQLException e)
    {
      logger.error("Unable to setup the AVP PreparedStatement objects, no data inserted: " + e);
      return;
    }

    //
    // Now process the specified file
    //
    int mapScale = 1;
    int mapId = -1;
    Hashtable<String, Integer> chrMap = new Hashtable<String, Integer>();
    logger.info("Processing the annotations in the file:" + filename.getName());
    try
    {
      // Build chrMap Hashtable (chop all "chr" prefixes)
      query = "SELECT c.name, c.id FROM chromosomes c LEFT JOIN annotation_sets a " +
        "ON c.map_id = a.map_id WHERE a.id=?";
      PreparedStatement st = conn.prepareStatement(query);
      st.setInt(1, asetId);
      ResultSet rs = st.executeQuery();
      while (rs.next())
        chrMap.put(rs.getString(1).substring(3), rs.getInt(2));
      st.close();

      // Get our scale, map id
      query = "SELECT m.id, m.scale FROM maps m LEFT JOIN annotation_sets a ON m.id = a.map_id WHERE a.id=?";
      st = conn.prepareStatement(query);
      st.setInt(1, asetId);
      rs = st.executeQuery();
      if (rs.next()) 
      {
        mapId = rs.getInt(1);
        mapScale = rs.getInt(2);
      }
      else logger.warn("Assuming a scale of '1' for map via annotation_set id " + asetId);
      st.close();

      //
      // Now process the specified file (main loop)
      //
      query = "INSERT INTO annotation (name, chromosome_id, annotation_set_id, ";
      query += "start, stop, source_ref_id) ";
      query += "VALUES (?, ?, ?, ?, ?, ?)";
      annotInsert = conn.prepareStatement(query);
      query = "SELECT id FROM ontology_node WHERE category LIKE ?";  // Case insensitive
      ontologyCheck = conn.prepareStatement(query);

      BufferedReader reader = new BufferedReader(new FileReader(filename));
      while (reader.ready())
      {
        String line = reader.readLine().trim();

        // Skip comment / Pragma lines
        if (line.startsWith("##") || line.startsWith("#")) continue;

        // Process all other lines
        String[] splits = line.split("\t");

        // Skip unknown/undefined chromosomes
        if (chrMap.get(splits[0]) == null) continue;

        // Build attributes hash
        Hashtable<String, String> attrs = new Hashtable<String, String>();
        String[] pairs = splits[8].split(";");
        for (String pair : pairs)
        {
          if (pair.equals(""))
          {
            // Skip trailing ";" if present
            continue;
          }
          else if (pair.indexOf("=") == -1)
          {
            logger.error("Bad key=value pair (" + pair + ").  Skipping...");
            continue;
          }
          String key = pair.substring(0, pair.indexOf("="));
          key = VCMapLoader.unescapeGff3(key);
          String value = pair.substring(pair.indexOf("=") + 1);
          value = VCMapLoader.unescapeGff3(value);
          attrs.put(key.trim(), value.trim());
        }

        // Modify the attributes hash as needed
        String name = attrs.get("Name");
        attrs.remove("Name");
        if (!splits[5].trim().equals("") && !splits[5].trim().equals("."))
          attrs.put("score", splits[5]);
        if (!splits[6].trim().equals("") && !splits[6].trim().equals("."))
          attrs.put("strand", splits[6]);
        if (!splits[7].trim().equals("") && !splits[7].trim().equals("."))
          attrs.put("phase", splits[7]);

        // Insert annotation row
        int start = (int) (Double.parseDouble(splits[3]) * mapScale);
        int stop = (int) (Double.parseDouble(splits[4]) * mapScale);

        annotInsert.setString(1, name); 
        annotInsert.setInt(2, chrMap.get(splits[0]));
        annotInsert.setInt(3, asetId);
        annotInsert.setInt(4, start);
        annotInsert.setInt(5, stop);
        annotInsert.setString(6, attrs.get("source_id"));
        
        annotInsert.execute();
        int annotId = getLastInsertId(annotInsert);

        // Insert annotation AVPs
        for (String key : attrs.keySet())
        {
          // NOTE: Handle ontology specially
          String value = attrs.get(key);
          if (key.equals("ontology"))
          {
            ontologyCheck.setString(1, value);
            rs = ontologyCheck.executeQuery();
            if (rs.next())
            {
              // Convert text into an id
              value = rs.getString(1);
            }
            else
            {
              // Insert missing ontology values...
              // NOTE: The tree_id is hardcoded here, but should be properly
              //   handled in the future if there is more than one copy of
              //   the ontology tree in the database
              int parentId = -1;
              ontologyCheck.setString(1, "Uncategorized");
              rs = ontologyCheck.executeQuery();
              if (rs.next())
              {
                parentId = rs.getInt(1);
              }
              else
              {
                // Need to find the root to create the "Uncategorized" node
                int rootId = -1;
                String sql = "SELECT id FROM ontology_node WHERE parent_node_id IS NULL";
                rs = ontologyCheck.executeQuery(sql);
                if (rs.next()) rootId = rs.getInt(1);
                
                if (rootId != -1)
                {
                  sql = "INSERT INTO ontology_node (tree_id, parent_node_id, category) " +
                    "VALUES (1, " + rootId + ", 'Uncategorized')";
                  ontologyCheck.execute(sql);

                  // Now attempt to get the node id
                  parentId = getLastInsertId(ontologyCheck);
                }
             }
              
              // Create the missing value (as an uncategorized node)
              String sql = "INSERT INTO ontology_node (tree_id, parent_node_id, category) " +
                "VALUES (1, " + parentId + ", '" + value.replaceAll("'", "\\\\'") + "')";
              if (parentId != -1) ontologyCheck.executeUpdate(sql);
              
              // Attempt to retrieve the newly created value
              ontologyCheck.setString(1, value);
              rs = ontologyCheck.executeQuery();
              if (rs.next()) value = rs.getString(1);
              else value = null;
            }
            
            // NOTE: If the value is still null at this point, it indicates
            //  most likely none of the ontology data has been loaded.  Instead
            //  of skipping this AVP, alternatively you could insert the string
            //  value for now, and in the future, update that field to the
            //  ontology_node id as the ontology tree is being loaded.
          }

          // Add all of our annotation AVPs
          if (value != null)
          {
            addAvp(attIdTest, attIdAdd, valIdTest, valIdAdd, annotAvpsTest, annotAvpsAdd, annotId, key, value);
          }
        }
      }
      
      // Now set the default annotation (if specified)
      if (config[2].has("default"))
      {
        logger.info("Setting default annotation type as " + config[2].optString("type", "null"));
        Statement defaultUpdate = null;
        try
        {
          defaultUpdate = conn.createStatement();
          query = "UPDATE maps SET default_annotation=" + asetId + " WHERE id = " + mapId;
          defaultUpdate.execute(query);
        }
        catch (SQLException e)
        {
          logger.error("DB exception while setting the default annotation, please do this manually");
        }
        finally
        {
          if (defaultUpdate != null) defaultUpdate.close();
        }
      }
    }
    catch (IOException e)
    {
      logger.error("General I/O error occurred: e");
      logger.warn("Please verify data accuracy as some data may not have been" +
        " inserted for annotation_set with id " + asetId);
    }
    catch (SQLException e)
    {
      logger.error("Database error occurred: " + e);
      logger.warn("Please verify data accuracy as some data may not have been" +
          " inserted for annotation_set with id " + asetId);
    }
    finally
    {
      // And clean up
      try
      {
        if (attIdTest != null) attIdTest.close();
        if (attIdAdd != null) attIdAdd.close();
        if (valIdTest != null) valIdTest.close();
        if (valIdAdd != null) valIdAdd.close();
        if (annotAvpsTest != null) annotAvpsTest.close();
        if (annotAvpsAdd != null) annotAvpsAdd.close();
        if (annotInsert != null) annotInsert.close();
        if (ontologyCheck != null) ontologyCheck.close();
        chrMap.clear();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Loads the assemblies in the give directory as long as their file 
   * extension is correct and all of the information needed is found </p>
   * @param conn
   *   The database connection to insert data into.
   * @param processingDirectory
   *   The File object that contains the directory that holds the 'assemblies'
   *   that need to be loaded into the server.
   //   
   */
  public void loadAssemblyData(Connection conn, File processingDirectory)
  {
    logger.info("Loading Assemblies...");

    // Loop through the source directories
    for (File sourceDir : processingDirectory.listFiles())
    {
      int sourceId = -1;
      String query = "SELECT id FROM sources WHERE name = ?";
      PreparedStatement getSourceId = null;
      try
      {
        getSourceId = conn.prepareStatement(query);
        getSourceId.setString(1, sourceDir.getName());
        ResultSet rs = getSourceId.executeQuery();
        if (rs.next())
          sourceId = rs.getInt(1);
        else
          throw new SQLException("Query '" + query + "' returned no results for '" + sourceDir.getName() + "'");
      }
      catch (SQLException e)
      {
        logger.error("Cannot find corresponding source entry for " + sourceDir.getName() + ": " + e);
        logger.error("  Cannot continue...");
        continue;
      }
      finally
      {
        try
        {
          if (getSourceId != null) getSourceId.close();
        }
        catch (SQLException e)
        {
          // Do nothing
        }
      }
      logger.debug("Assembly Source: " + sourceDir.getName() + ", id: " + sourceId + "...");
     
      // Loop for each species
      for (File speciesDir : sourceDir.listFiles())
      {
        logger.debug("  Species: " + speciesDir.getName() + "...");

        // Loop for each map type
        for (File mapDir : speciesDir.listFiles())
        {
          logger.debug("    Map type: " + mapDir.getName() + "...");

          // Loop through the files in the type directory
          for (File filename : mapDir.listFiles())
          {
            logger.debug("      File Name: " + filename.getName() + "...");
            Pattern p = Pattern.compile("chromosomes-(.*)\\.txt");
            Matcher m = p.matcher(filename.getName());
            if (!m.matches())
            {
              logger.error("Extraneous file '" + filename.getName() +"' in the processing directory!  Skipping...");
              continue;
            }
            
            // Otherwise, use the identifier to match this with a version in
            // the database, and a config file in memory
            String identifier = m.group(1);
            logger.debug("Version identifier: " + identifier);
            
            // Now we must check for a corresponding JSON config.  
            JSONObject[] config = findConfig(sourceDir.getName(), speciesDir.getName(), mapDir.getName(), identifier, null);

            // If no config was found, we simply continue looping files
            if (config == null)
            {
              logger.warn("Found processed file but no corresponding JSON config, skipping this file...");
              continue;
            }
            
            //
            // Now, check for the existing version row in the database
            //
            int versionId = -1;
            String sql = "SELECT v.id, m.id FROM versions v RIGHT JOIN maps m ON v.id = m.version_id " + 
            "WHERE ((v.build = ? AND v.version = ?) OR (v.release_date = ? AND v.md5 = ?) OR " + 
            "(v.release_date = ? AND v.generic = ?)) " + 
            "AND v.source_id = ? AND m.species = ? AND m.type = ?";
            try
            {
              int build = 0, version = 0;
              SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy");
              if (identifier.matches("^\\d+$"))
              {
                build = Integer.parseInt(identifier);
              }
              else if (identifier.matches("^\\d+\\.\\d+$"))
              {
                build = Integer.parseInt(identifier.substring(0, identifier.indexOf('.')));
                version = Integer.parseInt(identifier.substring(identifier.indexOf('.') + 1));
              }
                
              PreparedStatement st = conn.prepareStatement(sql);
              java.sql.Date release = new java.sql.Date(sdf.parse(config[3].getString("release_date")).getTime());
              st.setInt(1, build);
              st.setInt(2, version);
              st.setDate(3, release);
              st.setString(4, config[3].optString("md5", ""));
              st.setDate(5, release);
              st.setString(6, identifier);
              st.setInt(7, sourceId);
              st.setString(8, config[1].getString("name"));
              st.setString(9, config[2].getString("type"));
              ResultSet rs = st.executeQuery();
              
              if (rs.next())
              {
                logger.info("Skipping map for " + config[1].getString("name") + " from " + config[0].getString("name") + 
                    ", already loaded with matching version info (id: " + rs.getInt(1) + ") map id: " + rs.getInt(2));
                continue;
              }
              else
              {
                logger.debug("Map not found.  Proceeding with load...");
              }
              st.close();
              
              // Now check for existing matching identifier
              sql = "SELECT COUNT(*) FROM versions v RIGHT JOIN maps m ON m.version_id = v.id " + 
              "WHERE v.generic = ? AND v.source_id = ? AND m.species = ? AND m.type = ?";
              st = conn.prepareStatement(sql);
              st.setString(1, identifier);
              st.setInt(2, sourceId);
              st.setString(3, config[1].getString("name"));
              st.setString(4, config[2].getString("type"));
              rs = st.executeQuery();
              
              if (rs.next() && rs.getInt(1) != 0)
              {
                logger.warn("Map identified with different version information, but matching identifier of '" + identifier + "'.  " +
                    "Loading this map, but please verify that the configuration files are up-to-date...");
                // TODO - email the administrator, configs are almost certainly out of date
              }
              st.close();
              
              // Load a new version with our information
              String fields = "loaded, source_id, generic, release_date, human_name";
              String values = "CURDATE(), ?, ?, ?, ?";
              if (build != 0 && version != 0)
              {
                fields += ", build, version";
                values += ", ?, ?";
              }
              else if (!config[3].optString("md5", "").equals(""))
              {
                fields += ", md5";
                values += ", ?";
              }
              
              sql = "INSERT INTO versions (" + fields + ") VALUES (" + values + ")";
              st = conn.prepareStatement(sql);
              st.setInt(1, sourceId);
              st.setString(2, identifier);
              st.setDate(3, new java.sql.Date(sdf.parse(config[3].getString("release_date")).getTime()));
              st.setString(4, identifier);
              if (build != 0 && version != 0)
              {
                st.setInt(5, build);
                st.setInt(6, version);
              }
              else if (!config[3].optString("md5", "").equals(""))
              {
                st.setString(5, config[3].getString("md5"));
              }
              st.execute();
              versionId = getLastInsertId(st);
              st.close();
              logger.warn("*** New version information created for map (" + identifier +
                ") - please manually update the human_name field in the database. ***");
              // TODO - email the administrator
            }
            catch (ParseException e)
            {
              logger.error("Bad value for 'release_date' in JSON Config, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            catch (JSONException e)
            {
              logger.error("JSON configuration problem during assembly load, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }
            catch (SQLException e)
            {
              logger.error("Version identification problem during assembly load, skipping this file: " + filename);
              logger.debug("Error was: " + e);
              continue;
            }

            // 
            // Actually add the map information
            //
            addMap(conn, filename, versionId, config);
          }
        }
      }
    }
    logger.info("Finished loading Assemblies...");
  }

  /**
   * <p>Adds a chromosome to the database</p>
   * @param chromosomeAdd
   *    PreparedStatement which contains the information required to add a chromosome to the
   *    database
   * @param chrName
   *    The String containing the name of the chromosome (chr1-chrX)
   * @param length
   *    The length of the given chromosome
   * @param mapID
   *    The ID of the assembly (map)
   * @throws SQLException
   */
  private void addChromosome(PreparedStatement chromosomeAdd, String chrName, int length, int mapID)
      throws SQLException
  {
    chromosomeAdd.setInt(1, mapID);
    chromosomeAdd.setString(2, "chr" + chrName);
    chromosomeAdd.setInt(3, length);
    chromosomeAdd.execute();
    logger.debug("Added Chromosome: " + chrName + " id: " + getLastInsertId(chromosomeAdd));
  }

  /**
   * <p>Adds the map to the database.</p>
   * @param conn
   *   The database connection.
   * @param chromosomeFile
   *   The file which contains the chromosome information (names and lengths).
   * @param versionId
   *   The Version identifier for the assembly/map.
   * @param config
   *   An array of the four JSONObjects representing the configuration of this
   *   assembly/map.
   */
  private void addMap(Connection conn, File chromosomeFile, int versionId, JSONObject[] config)
  {
    PreparedStatement mapAdd = null, chromosomeAdd = null;
    try
    {
      int scale = config[2].optInt("scale", 1), taxId = config[1].getInt("taxID");
      String units = config[2].optString("units", null);
      String type = config[2].getString("type");
      String species = config[1].getString("name");
      logger.debug("Adding Map " + species + " version id:" + versionId);

      // Build our query
      String query = "INSERT INTO maps (version_id, type, species, units, scale, disabled, taxID)"
        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
      mapAdd = conn.prepareStatement(query);
      mapAdd.setInt(1, versionId);
      mapAdd.setString(2, type);
      mapAdd.setString(3, species);
      mapAdd.setString(4, units);
      mapAdd.setInt(5, scale);
      mapAdd.setInt(6, 0);
      mapAdd.setInt(7, taxId);
      mapAdd.execute();

      int mapId = getLastInsertId(mapAdd);

      // Build our chromosome insertion query
      query = "INSERT INTO chromosomes (map_id, name, length) VALUES (?, ?, ?)";
      chromosomeAdd = conn.prepareStatement(query);

      BufferedReader reader = new BufferedReader(new FileReader(chromosomeFile));
      logger.debug("Adding chromosome information from file: " + chromosomeFile);
      while (reader.ready())
      {
        String line = reader.readLine();
        String[] lineSplits = line.split("\t");
        int length = Integer.parseInt(lineSplits[1]);
        addChromosome(chromosomeAdd, lineSplits[0], length, mapId);
      }
    }
    catch (JSONException e)
    {
      logger.error("There is a problem with the configuration!  Nothing was added, please " +
          "correct this issue and rerun the load script: " + e);
      e.printStackTrace();
    }
    catch (IOException e)
    {
      logger.error("There is a problem reading the chromosome data file: '" + 
          chromosomeFile.getAbsolutePath() + "'!  Nothing was added, please " +
          "correct this issue and rerun the load script: " + e);
     e.printStackTrace();
    }
    catch (SQLException e)
    {
      logger.error("There was a database problem while adding the map: " + e);
      e.printStackTrace();
    }
    finally
    {
      try
      {
        if (mapAdd != null) mapAdd.close();
        if (chromosomeAdd != null) chromosomeAdd.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /*
   * Helper method to create an AVP based on the supplied information.  This
   * method is passed a set of PreparedStatement's to improve the performance
   * of the data insertion.  This method also checks for existing attibutes /
   * values and creates them as necessary.
   */
  private void addAVP(PreparedStatement attIdTest, PreparedStatement attIdAdd,
      PreparedStatement valIdTest, PreparedStatement valIdAdd, PreparedStatement annotAvpsTest,
      PreparedStatement annotAvpsAdd, int annotId, String attr, int val)
  {
    addAvp(attIdTest, attIdAdd, valIdTest, valIdAdd, annotAvpsTest, annotAvpsAdd, annotId, attr, "" + val);
  }

  /*
   * (non-javadoc) Helper method to add a new AVP in the database to a given 
   * annotation using the supplied annotation_id, chromosome_id, attribute, and
   * value.
   */
  private void addAvp(PreparedStatement attIdTest, PreparedStatement attIdAdd,
      PreparedStatement valIdTest, PreparedStatement valIdAdd, PreparedStatement annotAvpsTest,
      PreparedStatement annotAvpsAdd, int annotId, String attr, String val)
  {
    String query = "";

    try
    {
      // First test for presence of the AVP (shortcutting 2 other queries)
      annotAvpsTest.setInt(1, annotId);
      annotAvpsTest.setString(2, attr);
      annotAvpsTest.setString(3, val);
      ResultSet rs = annotAvpsTest.executeQuery();
      if (rs.next() && rs.getInt(1) > 0) return;

      // Get or create the attribute_id
      int attrId = -1;
      attIdTest.setString(1, attr);
      rs = attIdTest.executeQuery();
      if (rs.next())
      {
        attrId = rs.getInt(1);
      }
      else
      {
        attIdAdd.setString(1, attr);
        attIdAdd.execute();
        attrId = getLastInsertId(attIdAdd);
      }

      // Get or create the value_id
      int valId = -1;
      valIdTest.setString(1, val);
      rs = valIdTest.executeQuery();
      if (rs.next())
      {
        valId = rs.getInt(1);
      }
      else
      {
        valIdAdd.setString(1, val);
        valIdAdd.setString(2, val);
        valIdAdd.execute();
        valId = getLastInsertId(valIdAdd);
      }

      // Insert the AVP
      annotAvpsAdd.setInt(1, annotId);
      annotAvpsAdd.setInt(2, attrId);
      annotAvpsAdd.setInt(3, valId);
      annotAvpsAdd.execute();
    }
    catch (SQLException e)
    {
      logger.error("Problem creating AVP from (" + query + "): " + e);
    }
  }

  /**
   * Method to process the links between annotations. By first going through
   * the sourceID and sourceRefIds and then going through the homologene file
   * and both inserting the link into the links table and inserting the
   * homologeneID into the annotation_avps table
   *
   * @param conn
   *   Connection object that contains the connection to the database
   * @param homoSrcId
   *   The source id in the database for the version of Homologene in the
   *   scratch directory.
   * @param homoConfig
   *   The JSON config for the homologene data source, never null.
   * @throws SQLException
   */
  public void processLinks(Connection conn, int homoSrcId, JSONObject homoConfig) 
    throws SQLException, JSONException
  {
    logger.info("Creating links in the links table");

    String query = "INSERT INTO links (id, annotation_id, source_id) VALUES (?, ?, ?)";
    PreparedStatement insertLink = conn.prepareStatement(query);

    query = "SELECT a.id FROM annotation a, annotation_sets s, versions v "
      + "WHERE a.annotation_set_id = s.id AND s.version_id = v.id AND v.source_id=? AND a.source_ref_id=?";
    PreparedStatement getAnnotIds = conn.prepareStatement(query);

    query = "SELECT avp.annotation_id FROM annotation_avps avp, attributes a, vals v "
      + "WHERE avp.attribute_id=a.id AND a.type='EntrezGeneId' AND avp.value_id=v.id AND v.sha1=sha1(?)";
    PreparedStatement getEntrezMatches = conn.prepareStatement(query);

    query = "SELECT DISTINCT source_id, source_ref_id FROM annotation a, annotation_sets s, versions v " 
      + "WHERE a.annotation_set_id = s.id AND s.version_id = v.id "
      + "GROUP BY source_ref_id, source_id HAVING (COUNT(source_ref_id) > 1)";
    PreparedStatement annotationMatches = conn.prepareStatement(query);

    query = "SELECT id FROM attributes WHERE type=?";
    PreparedStatement attIdTest = conn.prepareStatement(query);

    query = "INSERT INTO attributes (type) VALUES (?)";
    PreparedStatement attIdAdd = conn.prepareStatement(query);

    query = "SELECT id FROM vals WHERE sha1=SHA1(?)";
    PreparedStatement valIdTest = conn.prepareStatement(query);

    query = "INSERT INTO vals (value, sha1) VALUES (?, SHA1(?))";
    PreparedStatement valIdAdd = conn.prepareStatement(query);

    query = "SELECT COUNT(*) FROM annotation_avps avp, attributes a, vals v " +
      "WHERE avp.attribute_id = a.id AND avp.value_id=v.id AND " +
      "avp.annotation_id=? AND a.type=? AND v.sha1=SHA1(?)";
    PreparedStatement annotAvpsTest = conn.prepareStatement(query);

    query = "INSERT INTO annotation_avps (annotation_id, attribute_id, value_id) VALUES (?, ?, ?)";
    PreparedStatement annotAvpsAdd = conn.prepareStatement(query);

    // Precache all tax ids
    Vector<Integer> taxIds = new Vector<Integer>();
    query = "SELECT DISTINCT taxID FROM maps";
    Statement st = conn.createStatement();
    ResultSet taxIdResults = st.executeQuery(query);
    while (taxIdResults.next())
      taxIds.add(taxIdResults.getInt(1));
    st.close();

    // Main steps for link creation
    // 1) Truncate the links table (remove all data and reset auto_increment
    // 2) Check sourceID + sourceRefId for the "same" annotation
    // 3) Go through the homologene File line-by-line and get the connection
    // between the annotations in that light as well. Also need to insert
    // the homologene ID into the avps table for the specified annotation.

    //
    // 1. Truncate 'links' table
    //
    logger.debug("Clearing the links table");
    query = "TRUNCATE TABLE links";
    conn.prepareCall(query).execute();

    //
    // 2. Check for "direct links" (same source and source_ref ids)
    //
    logger.debug("Adding 'direct links'...");
    int count = 0;
    // NOTE: Since we truncate the table first, we know the first available id
    //   is '1', but if this method is every modified to work differently, this
    //   value could be determined by the query "SELECT MAX(id) FROM links".
    int maxId = 1;
    ResultSet matchesRs = annotationMatches.executeQuery();
    while (matchesRs.next())
    {
      int sourceId = matchesRs.getInt(1);
      String sourceRefId = matchesRs.getString(2);

      getAnnotIds.setInt(1, sourceId);
      getAnnotIds.setString(2, sourceRefId);
      ResultSet annotIdRs = getAnnotIds.executeQuery();
      while (annotIdRs.next())
      {
        insertLink.setInt(1, maxId);
        insertLink.setInt(2, annotIdRs.getInt(1));
        insertLink.setInt(3, sourceId);
        insertLink.execute();
        count++;
      }
      maxId++;
    }
    logger.debug("Added " + count + " direct links to the links table");

    //
    // 3. Process Homologene
    //
    logger.debug("Adding homologous links from Homologene data");
    count = 0;
    try
    {
      int ncbiGeneId = 0;
      query = "SELECT id FROM sources WHERE name=?";
      PreparedStatement pst = conn.prepareStatement(query);
      pst.setString(1, "NCBI Gene");
      ResultSet rs = pst.executeQuery();
      if (rs.next()) ncbiGeneId = rs.getInt(1);

      JSONObject columns = homoConfig.getJSONObject("columns");
      int maxCol = 0;
      for (Iterator<?> keys = columns.keys(); keys.hasNext(); )
      {
        String key = keys.next().toString();
        if (columns.getInt(key) > maxCol)
          maxCol = columns.getInt(key); 
      }
      File scratch = new File(scratchDir, "Homologene");
      File homologeneFile = new File(scratch, homoConfig.getString("filename"));
      BufferedReader reader = new BufferedReader(new FileReader(homologeneFile));
      int hId = 0;
      while (reader.ready())
      {
        String line = reader.readLine();
        String[] splits = line.split("\t");
        if (splits.length <= maxCol)
        {
          logger.warn("Incorrectly formatted line while parsing HomoloGene file:\n" + line);
          continue;
        }
        
        // Increment the link id everytime we see a new HID (despite potentially
        // introducing gaps in the links table.
        if (hId != 0 && hId != Integer.parseInt(splits[columns.getInt("HID")])) maxId++;
        
        // Pick off the relevant fields...
        hId = Integer.parseInt(splits[columns.getInt("HID")]);
        String geneId = splits[columns.getInt("geneID")];
        int taxId = Integer.parseInt(splits[columns.getInt("taxID")]);
        if (!taxIds.contains(taxId)) continue;

        // First the NCBI features (using source_ref_id)
        getAnnotIds.setInt(1, ncbiGeneId);
        getAnnotIds.setString(2, "GeneID:" + geneId);
        ResultSet annotInfo = getAnnotIds.executeQuery();
        while (annotInfo.next())
        {
          int annotId = annotInfo.getInt(1);
          insertLink.setInt(1, maxId);
          insertLink.setInt(2, annotId);
          insertLink.setInt(3, homoSrcId);
          insertLink.execute();
          addAVP(attIdTest, attIdAdd, valIdTest, valIdAdd, annotAvpsTest, annotAvpsAdd, annotId,
              "homologene_id", hId);
          count++;
        }
        
        // Now any other features able to link to EntrezGene
        getEntrezMatches.setString(1, geneId);
        annotInfo = getEntrezMatches.executeQuery();
        while (annotInfo.next())
        {
          int annotId = annotInfo.getInt(1);
          insertLink.setInt(1, maxId);
          insertLink.setInt(2, annotId);
          insertLink.setInt(3, homoSrcId);
          insertLink.execute();
          addAVP(attIdTest, attIdAdd, valIdTest, valIdAdd, annotAvpsTest, annotAvpsAdd, annotId,
              "homologene_id", hId);
          count++;
        }
      }
    }
    catch (FileNotFoundException e)
    {
      logger.error("Homologene File was not found, no homologene links were created: " + e);
    }
    catch (IOException e)
    {
      logger.error("Homologene Entry encountered an Exception, some links may have been created: "
          + e);
    }
    logger.debug("Added " + count + " homologous links");

    // Clean up (close all statements)
    insertLink.close();
    getAnnotIds.close();
    getEntrezMatches.close();
    annotationMatches.close();
    attIdTest.close();
    attIdAdd.close();
    valIdTest.close();
    valIdAdd.close();
    annotAvpsTest.close();
    annotAvpsAdd.close();
    logger.info("The links table has been built");
  }
}
