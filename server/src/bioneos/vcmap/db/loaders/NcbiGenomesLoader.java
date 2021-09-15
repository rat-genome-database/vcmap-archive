package bioneos.vcmap.db.loaders;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigInteger;
import java.util.Vector;
import java.util.Hashtable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import bioneos.vcmap.db.VCMapLoader;
import bioneos.vcmap.db.LoaderFactory;

/**
 * The Loader subclass to handle assembly (map) data provided by NCBI.
 * @author sgdavis
 */
public class NcbiGenomesLoader
  extends Loader
{
  /* Register our loader with the Factory */
  static
  {
    NcbiGenomesLoader l = new NcbiGenomesLoader();
    LoaderFactory.registerLoader(l);
  }

  /**
   * Specify what types of data sources our loader handles.
   * @param name
   *   The name of the data source in question.
   * @param type
   *   The type of data to load from the data source (ignored).
   * @return
   *   Handles assembly data from "NCBI Genomes".
   */
  public boolean handles(String name, String type)
  {
    return (name.toLowerCase().equals("ncbi genomes") && type.toLowerCase().equals("assembly")); 
  }

  /**
   * Process the data files from NCBI and convert the assembly (agp) files into
   * the chromosomes.txt format main {@link bioneos.vcmap.db.VCMapLoader} can
   * process the data and insert it into the VCMap database.
   * @param source
   *   The NCBI data source and configuration.
   * @param processingDir
   *   The directory to store the final chromosomes.txt files that are created.
   * @throws JSONException
   *   Occurs whenever the configuration of the data source is missing required
   *   attributes (bad configuration file most likely)
   */
  public void processData(VCMapLoader main, JSONObject source, File processingDir)
    throws JSONException
  {
    logger.info("NcbiLoader.processData() started...");

    File scratch = new File(main.getScratchDirectory(), source.getString("type").toLowerCase());
    scratch = new File(scratch, source.getString("name"));
    logger.debug("Creating scratch directory: " + scratch.getAbsolutePath());
    scratch.mkdirs();
    File processing = new File(processingDir, source.getString("type").toLowerCase());
    processing = new File(processing, source.getString("name"));
    logger.debug("Clearing processing scratch directory: " + processing.getAbsolutePath());
    if (processing.exists())
      main.recursiveDelete(processing);
    processing.mkdirs();

    // First create the FTP connection to our source
    FTPClient ftp = new FTPClient();
    try
    {
      ftp.connect(source.getString("hostname"));
      ftp.login(source.getString("username"), source.getString("password"));
      ftp.enterLocalPassiveMode();
      logger.info("Connected to FTP site at: " + source.getString("hostname"));
    }
    catch (IOException ioe)
    {
      logger.error("Problem connecting to FTP site at (" + source.getString("hostname") + "): " + ioe);
      logger.error("No further processing can be done for this source...");
      return;
    }

    //
    // Gather data for each configured species
    //
    JSONArray speciesList = source.getJSONArray("species");
    for (int i = 0; i < speciesList.length(); i++)
    {
      JSONObject species = speciesList.getJSONObject(i);
      setupFiles(main, ftp, source, species, scratch, processing);
      logger.info("Finished processing of '" + species.getString("name") + "'.");
    }
    
    try
    {
      ftp.disconnect();
    }
    catch (IOException ioe)
    {
      // Do nothing
    }
    
    logger.info("Finished processing of '" + source.getString("name") + "' data source.");
  }

  /*
   * Helper method to download and preprocess the data files.
   */
  private void setupFiles(VCMapLoader main, FTPClient ftp, JSONObject source, JSONObject species, File scratch, File process)
    throws JSONException
  {
    logger.info("Grabbing data files for: " + species.getString("name"));
    
    // Create the scratch location
    File speciesScratch = new File(scratch, species.getString("name"));
    logger.debug("Creating species scratch directory: " + speciesScratch.getAbsolutePath());
    speciesScratch.mkdirs();
    File destDir = new File(process, species.getString("name"));
    logger.debug("Creating species processing directory: " + destDir.getAbsolutePath());
    destDir.mkdirs();

    try
    {
      // Ensure ftp connection is still alive
      checkConnection(ftp, source);
      
      // Now Grab the map data files for this source
      JSONArray maps = species.getJSONArray("maps");
      for (int i = 0; i < maps.length(); i++)
      {
        JSONObject map = maps.getJSONObject(i);
        JSONArray files = map.getJSONArray("files");
        
        // Create the map scratch locations
        File mapScratch = new File(speciesScratch, map.getString("type"));
        logger.debug("Creating map scratch directory: " + mapScratch.getAbsolutePath());
        mapScratch.mkdirs();
        File mapDestDir = new File(destDir, map.getString("type"));
        logger.debug("Creating map processing directory: " + mapDestDir.getAbsolutePath());
        mapDestDir.mkdirs();

        // Gather the Build information file if available (save as BUILD_INFO)
        if (map.getJSONObject("version").has("file"))
        {
          logger.info("Downloading build info file...");
          main.downloadFile(ftp, species.getString("location") + map.getJSONObject("version").getString("file"),
             "BUILD_INFO-" + i, mapScratch);
        }

        // Data files
        logger.info("Downloading map data files for " + map.optString("type", "unknown") + " map...");
        for (int j = 0; j < files.length(); j++)
        {
          // Separate the parent directory and filename
          String dir = species.getString("location") + files.getString(j);
          String wildcard = (dir.indexOf('/') == -1) ? dir : dir.substring(dir.lastIndexOf('/') + 1);
          dir = dir.indexOf("/") == -1 ? "/" : dir.substring(0, dir.lastIndexOf("/") + 1);
         
          // List all files and download any matching our regexp (or can be a static string)
          String[] allChr = ftp.listNames(dir);
          if (allChr == null || allChr.length == 0)
          {
            logger.warn("Data location '" + dir + "' does not contain any files!  Attempting match with our cache...");
            allChr = mapScratch.list();
          }
          
          logger.info("Downloading assembly definition files from (" + dir + ")...");
          Vector<File> chrs = new Vector<File>();
          for (String remoteFile : allChr)
          {
            // Only grab the files matching our wildcard
            String remoteFilename = remoteFile.indexOf('/') == -1 ? remoteFile : remoteFile.substring(remoteFile.lastIndexOf('/') + 1); 
            if (remoteFilename.matches(wildcard))
            {
              String filename = i + "-" + remoteFilename;
              boolean success = main.downloadFile(ftp, remoteFile, filename, mapScratch);

              if (success)
              {
                logger.debug("Grabbed file: " + remoteFile);

                // If necessary, gunzip
                File localFile = new File(mapScratch, filename);
                localFile = gunzip(localFile);

                chrs.add(localFile);
              }
              else
              {
                logger.error("Could not download file: " + remoteFile);
              }
            }
            else
            {
              logger.debug("File '" + remoteFile + "' does not match '" + wildcard + "'");
            }
          }

          // Now attempt to version our maps
          JSONObject version = map.getJSONObject("version");
          String identifier = version.getString("identifier");
          if (version.has("file"))
          {
            try
            {
              BufferedReader reader = new BufferedReader(new FileReader(new File(mapScratch, "BUILD_INFO-" + i)));
              boolean found = false;
              while (reader.ready())
              {
                String line = reader.readLine();
                found = found || line.matches(".*" + identifier + ".*");
              }
              reader.close();

              // Appropriately notify when assembly is not mentioned in the BUILD_INFO
              if (!found)
              {
                // Files downloaded, but assembly doesn't show up in build
                // info (config probably is outdated)
                logger.error("*** Downloaded assembly data files, but could not find the identifier '" + identifier +
                    "' in the build info.  This indicates that the config file is likely out of date.");
                logger.info("Deleting downloaded files and skipping...");
                // TODO
                //main.sendEmail();
                
                // Delete files - to avoid future confusion (regardless of caching)
                for (File f : chrs)
                  f.delete();
                File temp = new File(mapScratch, "BUILD_INFO-" + i);
                temp.delete();
                
                // Skip processing
                continue;
              }
            }
            catch (IOException e)
            {
              logger.error("Cannot determine if map assembly data is up to date!!  " +
              		"Moving ahead regardless (You should manually verify the newest assembly was loaded)...");
              logger.debug("Error was: " + e);
            }

            // Store for future processing
            version.put("identifier", identifier);
          }
          else if (version.has("md5"))
          {
            // Calculate and save the md5 of this file for future processing
            BigInteger md5sum = new BigInteger("0");
            for (File f : chrs)
              md5sum = md5sum.xor(VCMapLoader.computeHash(f));
            logger.debug("Computed md5 hash for files as: " + md5sum.toString(16));
            version.put("md5", md5sum.toString(16));
          }

          File target = new File(mapDestDir, "chromosomes-" + identifier + ".txt");
          processChromosomes(chrs, map, target);
        }
      }
    }
    catch (IOException ioe)
    {
      logger.error("Dead FTP connection to site (" + source.getString("hostname") + "): " + ioe);
    }

    logger.debug("Done downloading files...");
    logger.info("Data retrieved for: " + species.getString("name"));
  }

  /*
   * Helper method to read in .AGP chromosome files to determine chromosome
   * lengths for a given map.  These values are then stored in the processing
   * directory in a tab-delimited file named chromosomes.  This takes into
   * account the scale configuration from the map settings.
   */
  private void processChromosomes(Vector<File> chrs, JSONObject map, File target)
  {
    logger.info("Processing chromosome lengths...");
    target.getParentFile().mkdirs();

    // Build the chrMap first
    Hashtable<String, Double> chrMap = new Hashtable<String, Double>();
    JSONObject columns = map.optJSONObject("columns");
    if (columns == null)
    {
      columns = new JSONObject();
      // Put the defaults in place based on file name
      try
      {
        if (chrs.get(0).getName().indexOf(".agp") != -1)
        {
          columns.put("chromosome", 0);
          columns.put("start", 1);
          columns.put("stop", 2);
        }
        else if (chrs.get(0).getName().indexOf(".md") != -1)
        {
          columns.put("chromosome", 1);
          columns.put("start", 3);
          columns.put("stop", 4);
        }
      }
      catch (JSONException e)
      {
        // Won't happen
      }
    }

    // Read in the lengths from the data files
    for (File file : chrs)
    {
      try
      {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        while (reader.ready())
        {
          String line = reader.readLine();
          if (line.trim().startsWith("#")) continue;
          String[] splits = line.split("\t");
          String chr = splits[columns.optInt("chromosome", 0)];
          if (chr.indexOf("chr") == 0) chr = chr.substring(3);

          double start = Double.parseDouble(splits[columns.optInt("start", 1)]);
          double stop = Double.parseDouble(splits[columns.optInt("stop", 2)]);
          if (start > stop) stop = start;
          if (chrMap.get(chr) == null || chrMap.get(chr) < stop)
            chrMap.put(chr, stop);
        }
        reader.close();
      }
      catch (IOException e)
      {
        logger.error("Problem with chromosome definition file: " + e);
      }
    }

    // Output tab-delimited lengths - based on the chrMap
    try
    {
      BufferedWriter writer = new BufferedWriter(new FileWriter(target));
      for (String key : chrMap.keySet())
      {
        writer.write(key);
        writer.write("\t");
        writer.write("" + ((int) (chrMap.get(key) * map.optInt("scale", 1))));
        writer.newLine();
      }
      writer.close();
    }
    catch (IOException e)
    {
      logger.error("Problem writing chromosome definition file: " + e);
    }
  }

  /*
   * Helper method to unzip a Gzipped file.  Returns the new file reference.
   */
  private File gunzip(File zipFile)
  {
    if (!zipFile.getName().endsWith(".gz")) return zipFile;
    if (!zipFile.exists() && zipFile.getName().endsWith(".gz"))
    {
      // Test for an already unzipped file (when in caching mode)
      String filename = zipFile.getAbsolutePath();
      filename = filename.substring(0, filename.length() - 3);
      File test = new File(filename);
      if (test.exists()) return test;
    }

    logger.debug("Gunzipping: " + zipFile.getAbsolutePath());

    // Gunzip any compressed files
    try
    {
      Process unzip = Runtime.getRuntime().exec(
          new String[] {"/bin/gunzip", "-f", zipFile.getAbsolutePath()});

      int exitVal = unzip.waitFor();
      if (exitVal != 0)
      {
        logger.error("'gunzip' exited abnormally with value: " + exitVal);
        logger.error("Deleting file: " + zipFile.getAbsolutePath());
        zipFile.delete();
      }
    }
    catch(IOException ioe)
    {
      logger.error("Problem executing gunzip: " + ioe);
    }
    catch(InterruptedException e)
    {
      logger.error("Problem while gunzipping files: " + e);
    }

    String newFile = zipFile.getAbsolutePath();
    return new File(newFile.substring(0, newFile.length() - 3));
  }

  /*
   * Helper method to check a connection to an FTP site, and bring it back
   * online if necessary.
   */
  public void checkConnection(FTPClient ftp, JSONObject source)
    throws IOException
  {
    boolean connected = false;
    try
    {
      connected = ftp.sendNoOp();
    }
    catch (FTPConnectionClosedException e)
    {
      connected = false;
    }
    catch (IOException e)
    {
      connected = false;
    }

    if (!connected)
    {
      // try reconnecting
      try
      {
        logger.warn("FTP connection down... attempting reconnect...");
        ftp.disconnect();
        ftp.connect(source.getString("hostname"));
        ftp.login(source.getString("username"), source.getString("password"));
        ftp.enterLocalPassiveMode();
        logger.info("Successfully reconnected to: " + source.getString("hostname")); 
      }
      catch (IOException e)
      {
        logger.error("Error while trying to reconnect: " + e);
        throw e;
      }
      catch (JSONException e)
      {
        // Should have been caught already
        logger.error("Improperly formed JSON in source config: " + e);
      }
    }
  }
}
