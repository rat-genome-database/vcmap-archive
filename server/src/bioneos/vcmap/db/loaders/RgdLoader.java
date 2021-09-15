package bioneos.vcmap.db.loaders;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import bioneos.vcmap.db.LoaderFactory;
import bioneos.vcmap.db.VCMapLoader;

/**
 * The Loader subclass to handle data provided by RGD.
 * @author sgdavis
 */
public class RgdLoader
extends Loader
{
  /* Register our loader with the Factory */
  static
  {
    RgdLoader l = new RgdLoader();
    LoaderFactory.registerLoader(l);
  }

  /**
   * Specify what types of data sources our loader handles.
   * @param name
   *   The name of the data source in question.
   * @param type
   *   The type of data to load from the data source (ignored).
   * @return
   *   True IFF name == 'rgd', otherwise false.
   */
  public boolean handles(String name, String type)
  {
    if (!type.toLowerCase().equals("annotation")) return false;
    return name.toLowerCase().equals("rgd");
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

  /**
   * Process the data files from RGD and convert the tab delimited text files
   * into our specialized GFF3 format so that the main
   * {@link bioneos.vcmap.db.VCMapLoader} can process the data and insert it
   * into the VCMap database.
   * @param source
   *   The RGD data source and configuration.
   * @param processingDir
   *   The directory to store the final GFF files that are created.
   * @throws JSONException
   *   Occurs whenever the configuration of the data source is missing required
   *   attributes (bad configuration file most likely)
   */
  public void processData(VCMapLoader main, JSONObject source, File processingDir)
  throws JSONException
  {
    logger.info("RgdLoader.processData() started...");

    File scratch = new File(main.getScratchDirectory(), source.getString("type").toLowerCase());
    scratch = new File(scratch, source.getString("name"));
    logger.debug("Creating scratch directory: " + scratch.getAbsolutePath());
    scratch.mkdirs();
    File processing = new File(processingDir, source.getString("type").toLowerCase());
    processing = new File(processing, source.getString("name"));
    logger.debug("Creating processing scratch directory: " + processing.getAbsolutePath());
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
   * Helper method to download the specified data files and preprocess them
   * into GFF3 format without any forward or reverse references.  If the
   * configuration contains the special attribute "geneIds", this file is
   * directly placed in the processed directory without any translation.
   */
  private void setupFiles(VCMapLoader main, FTPClient ftp, 
      JSONObject source, JSONObject species, File scratch, File process)
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
      JSONArray annotationSets = species.getJSONArray("annotations");
      for (int i = 0; i < annotationSets.length(); i++)
      {
        JSONObject annotationSet = annotationSets.getJSONObject(i);
        JSONArray files = annotationSet.getJSONArray("files");
 
        // Create the annotation set scratch locations
        File asetScratch = new File(speciesScratch, annotationSet.getString("assembly_type"));
        logger.debug("Creating annotation set scratch directory: " + asetScratch.getAbsolutePath());
        asetScratch.mkdirs();
        File asetDestDir = new File(destDir, annotationSet.optString("assembly_type", "unknown"));
        logger.debug("Clearing annotation set processing directory: " + asetDestDir.getAbsolutePath());
        asetDestDir.mkdirs();
        
        // Gather the Build information file if available
        String identifier = "unknown";
        String assembly = "unknown";
        if (annotationSet.has("assembly_version"))
          assembly = annotationSet.getString("assembly_version");
        if (annotationSet.getJSONObject("version").has("file"))
        {
          logger.info("Downloading build info file...");
          String filename = annotationSet.getJSONObject("version").getString("file");
          main.downloadFile(ftp, species.getString("location") + filename, filename, asetScratch);

          // Open the file and get the versioning information
          BufferedReader reader = null;
          identifier = annotationSet.getJSONObject("version").getString("identifier");
          String versionDate = "";
          Date versionDateObj = Calendar.getInstance().getTime();
          try
          {
            reader = new BufferedReader(new FileReader(new File(asetScratch, filename)));
            while (reader.ready())
            {
              String line = reader.readLine();
              Matcher m1 = Pattern.compile(annotationSet.getJSONObject("version").getString("date")).matcher(line);
              if (m1.find())
              {
                versionDate = m1.group(1);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/M/d");
                versionDateObj = sdf.parse(versionDate);
                break;
              }
            }
            reader.close();
          }
          catch (IOException e)
          {
            logger.error("Problems reading the data file " + 
                annotationSet.getJSONObject("version").getString("file") + "!  Skipping this set...");
            logger.debug("Error was: " + e);
            continue;
          }
          catch (ParseException e)
          {
            logger.error("Problems parsing date from: " + versionDate);
            logger.warn("Using today's date instead.");
            logger.debug("Error was: " + e);
          }

          // Update our JSON object (for future processing)
          SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy");
          JSONObject version = annotationSet.getJSONObject("version");
          version.put("release_date", sdf.format(versionDateObj));
        }

        // Map data files
        logger.info("Downloading map data files for " + annotationSet.optString("assembly_type", "unknown") + " map...");
        BigInteger md5sum = new BigInteger("0");
        for (int j = 0; j < files.length(); j++)
        {
          String file = files.getString(j);

          // Grab the file
          String filename = (file.indexOf('/') == -1) ? file : 
            file.substring(file.lastIndexOf('/') + 1);
          boolean success = main.downloadFile(ftp, species.getString("location") + file, filename, asetScratch);          

          // If the file is grabbed
          if (success)
          {
            File localFile = new File(asetScratch, filename);

            // and finally translate
            File target = new File(asetDestDir, "data-" + assembly + ":" + identifier + ".gff3");
            translateFile(main, source, species, annotationSet, localFile, target);
            BigInteger digest = VCMapLoader.computeHash(localFile);
            if (digest != null) md5sum = md5sum.xor(digest);
         } 
        }
        
        // Update md5 hash in config object
        logger.debug("Computed md5 hash for files as: " + md5sum.toString(16));
        if (annotationSet.getJSONObject("version").has("md5"))
          annotationSet.getJSONObject("version").put("md5", md5sum.toString(16));
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
   * Helper method for translating data files from RGD tab delimited format
   * into GFF3.  The only quirk to this translation is that we
   * create GFF files that never have reverse referencing objects, therefore
   * the files should be able to be processed linearly without any special
   * consideration for the future data entries.  This allows for a drastic
   * improvement in performance.
   */
  private void translateFile(VCMapLoader main, JSONObject source, JSONObject species, JSONObject annotationSet,
      File file, File destFile)
  throws JSONException
  {
    logger.info("Translating file '" + file + "'...");
    destFile.getParentFile().mkdirs();

    // Get our versioning from the JSONobject
    JSONObject versionObj = annotationSet.getJSONObject("version");
    String identifier = versionObj.getString("identifier");
    String versionDate = versionObj.getString("release_date");

    // Grab the columns map
    JSONObject columns = annotationSet.getJSONObject("columns");

    // 
    // Main processing loop
    //
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try
    {
      reader = new BufferedReader(new FileReader(file));
      writer = new BufferedWriter(new FileWriter(destFile));

      // First GFF3 header if we aren't appending
      // NOTE: We have modified some of the Pragma tags to better suit our
      //   needs but this makes the file actually invalid GFF3.  Because of
      //   this, we don't output the "##gff-version 3" pragma line.
      writer.write("##species " + species.getString("name")); // Not valid tag-value
      writer.newLine();
      writer.write("##genome-build " + annotationSet.getString("assembly_version"));
      writer.newLine();
      writer.write("##source " + (versionObj.has("source") ? versionObj.getString("source") : source.getString("name")));
      writer.newLine();
      writer.write("##version " + identifier);
      writer.newLine();
      writer.write("##version-date " + versionDate);
      writer.newLine();
      writer.flush();

      // Now loop the input file - since the structure is simple (one line per
      // feature) translation is as easy as properly building a new row, and
      // outputting it to our destination file.
      while (reader.ready())
      {
        String line = reader.readLine();

        // Skip comment lines
        if (line.trim().indexOf("#") == 0) continue;

        // Cut up our data line
        String[] splits = line.split("\t");

        // Use the columns map to translate the fields into appropriate pos
        try
        {
          double start = -1, stop = -1;
          try
          {
            start = Double.parseDouble(splits[columns.getInt("start")]);
          }
          catch (NumberFormatException e) 
          {
            // Nothing we can do
          }

          try
          {
            stop = Double.parseDouble(splits[columns.getInt("stop")]);
          }
          catch (NumberFormatException e)
          {
            // Nothing we can do
          }

          // Skip any unplaced features
          if (start == -1 && stop == -1) continue;
          else if (stop == -1) stop = start;
          else if (start == -1) start = stop;
          if (start > stop)
          {
            // GFF specifies that start is always < stop
            double swap = start;
            start = stop;
            stop = swap;
          }

          // Add any additional parameters to our attributes column
          Hashtable<String, String> attrs = new Hashtable<String, String>();
          for (Iterator<?> keys = columns.keys(); keys.hasNext(); )
          {
            String key = keys.next().toString();
            // Skip our primary attributes (in other columns already)
            if (key.equals("chromosome") || key.equals("name") ||
                key.equals("start") || key.equals("stop") || key.equals("strand") ||
                key.equals("score") || key.equals("phase"))
              continue;
            attrs.put(key, splits[columns.getInt(key)]);
          }

          // Now output our line to the destination file
          DecimalFormat f = new DecimalFormat("#.#####");
          writer.write(splits[columns.getInt("chromosome")] + "\t");
          writer.write("VCMapLoader\t");
          writer.write(annotationSet.getString("type") + "\t");
          writer.write(f.format(start) + "\t" + f.format(stop) + "\t");
          if (columns.has("score")) writer.write(splits[columns.getInt("score")]);
          writer.write("\t");
          if (columns.has("strand")) writer.write(splits[columns.getInt("strand")]);
          writer.write("\t");
          if (columns.has("phase")) writer.write(splits[columns.getInt("phase")]);
          writer.write("\t");
          // Finally the attributes hash
          StringBuilder attrsStr = new StringBuilder();
          attrsStr.append("Name=").append(splits[columns.getInt("name")]);
          for (Object attr : attrs.keySet())
          {
            String key = VCMapLoader.escapeGff3(attr.toString());
            String value = VCMapLoader.escapeGff3(attrs.get(attr).toString());
            attrsStr.append("; ").append(key).append("=").append(value);
          }
          writer.write(attrsStr.toString() + "\n");
        }
        catch (IndexOutOfBoundsException e)
        {
          logger.debug("Skipped line due to insufficient number of columns: " + splits.length);
        }
      }

      // Cleanup
      writer.close();
      reader.close();
    }
    catch (IOException e)
    {
      // Close open connections
      try
      {
        if (writer != null) writer.close();
        if (reader != null) reader.close();
      }
      catch (IOException ioe)
      {
        // Do nothing
      }

      // Cleanup
      logger.error("Problems translating file '" + file + "' to GFF: " + e);
      logger.warn("Deleting partial file, data will not be loaded...");
      destFile.delete();
    }
  }
}