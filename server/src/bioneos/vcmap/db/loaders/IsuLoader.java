package bioneos.vcmap.db.loaders;

import java.math.BigInteger;
import java.net.URL;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import bioneos.vcmap.db.VCMapLoader;
import bioneos.vcmap.db.LoaderFactory;

/**
 * The Loader subclass to handle data provided by ISU.
 * @author sgdavis
 */
public class IsuLoader
  extends Loader
{
  /* Register our loader with the Factory */
  static
  {
    IsuLoader l = new IsuLoader();
    LoaderFactory.registerLoader(l);
  }

  /**
   * Specify what types of data sources our loader handles.
   * @param name
   *   The name of the data source in question.
   * @param type
   *   The type of data to load from the data source (ignored).
   * @return
   *   True IFF name == 'isu', otherwise false.
   */
  public boolean handles(String name, String type)
  {
    if (!type.toLowerCase().equals("annotation")) return false;
    return name.toLowerCase().equals("isu");
  }

  /**
   * Process the data files from ISU and convert the tab delimited text files
   * into our specialized GFF3 format so that the main
   * {@link bioneos.vcmap.db.VCMapLoader} can process the data and insert it
   * into the VCMap database.
   * @param source
   *   The ISU data source and configuration.
   * @param processingDir
   *   The directory to store the final GFF files that are created.
   * @throws JSONException
   *   Occurs whenever the configuration of the data source is missing required
   *   attributes (bad configuration file most likely)
   */
  public void processData(VCMapLoader main, JSONObject source, File processingDir)
    throws JSONException
  {
    logger.info("IsuLoader.processData() started...");

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

    //
    // Gather data for each configured species
    //
    JSONArray speciesList = source.getJSONArray("species");
    for (int i = 0; i < speciesList.length(); i++)
    {
      JSONObject species = speciesList.getJSONObject(i);
      setupFiles(main, source, species, scratch, processing);
      logger.info("Finished processing of '" + species.getString("name") + "'.");
    }
    
    logger.info("Finished processing of '" + source.getString("name") + "' data source.");
  }

  /*
   * Helper method to download the specified data files and preprocess them
   * into GFF3 format without any forward or reverse references.  If the
   * configuration contains the special attribute "geneIds", this file is
   * directly placed in the processed directory without any translation.
   */
  private void setupFiles(VCMapLoader main, JSONObject source, JSONObject species, File scratch, File process)
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

    // Now grab the annotation files for this species and translate them
    JSONArray annotationSets = species.getJSONArray("annotations");
    for (int i = 0; i < annotationSets.length(); i++)
    {
      JSONObject annotationSet = annotationSets.getJSONObject(i);
      logger.info("Downloading annotation file for species " + species.getString("name") + ", map type " 
          + annotationSet.getString("assembly_type"));
      
      // Create the annotation set scratch locations
      File asetScratch = new File(speciesScratch, annotationSet.getString("assembly_type"));
      logger.debug("Creating annotation set scratch directory: " + asetScratch.getAbsolutePath());
      asetScratch.mkdirs();
      File asetDestDir = new File(destDir, annotationSet.optString("assembly_type", "unknown"));
      logger.debug("Clearing annotation set processing directory: " + asetDestDir.getAbsolutePath());
      asetDestDir.mkdirs();

      // Prepare the versioning information
      String identifier = "unknown";
      String assembly = "unknown";
      if (annotationSet.getJSONObject("version").has("identifier")) 
        identifier = annotationSet.getJSONObject("version").getString("identifier");
      if (annotationSet.has("assembly_version"))
        assembly = annotationSet.getString("assembly_version");
      SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy");
      JSONObject version = annotationSet.getJSONObject("version");
      version.put("release_date", sdf.format(System.currentTimeMillis()));

      // Attempt to download file (only support a single file)
      try
      {
        // Grab the user agreement page first
        URL website = new URL("http://" + source.getString("hostname") + 
            species.getString("location") + annotationSet.getString("suffix"));
        URL file = null;
        logger.info("Looking for download link at: " + website.toString());
        BufferedReader reader = new BufferedReader(new InputStreamReader(website.openStream()));
        Pattern patt = Pattern.compile(".*href=\"(.*)\".*I have read and agree to the above terms and conditions.*");
        while (reader.ready())
        {
          String line = reader.readLine();
          
          Matcher m = patt.matcher(line);
          if (m.matches())
          {
            file = new URL("http://" + source.getString("hostname") + m.group(1));
            break;
          }
        }
        reader.close();

        // If the actual download link was found -- now download
        if (file != null)
        {
          File localFile = new File(asetScratch, "data-" + assembly + ":" + identifier + ".txt.gz");
          localFile.getParentFile().mkdirs();
          FileOutputStream writer = new FileOutputStream(localFile);
          logger.info("Grabbing file from: " + file.toString());
          InputStream stream = file.openStream();
          int bytes = 0;
          byte[] buffer = new byte[1024];
          while (true)
          {
             bytes = stream.read(buffer, 0, 1024);
             if (bytes != -1) writer.write(buffer, 0, bytes);
             else break;
             writer.flush();
          }
          stream.close();
          writer.close();

          // Gunzip the file
          localFile = gunzip(localFile);

          // and finally translate
          File target = new File(asetDestDir, File.separator + "data-" + assembly + ":" + identifier + ".gff3");
          if (annotationSet.getString("assembly_type").equals("Genomic"))
            translateGFF(main, source, species, annotationSet, localFile, target);
          else if (annotationSet.getString("assembly_type").equals("Genetic"))
            translateFile(main, source, species, annotationSet, localFile, target);
          
          BigInteger md5sum = VCMapLoader.computeHash(localFile);
          if (annotationSet.getJSONObject("version").has("md5"))
            annotationSet.getJSONObject("version").put("md5", md5sum.toString(16));
        }
      }
      catch (IOException ioe)
      {
        logger.error("Problem grabbing file over HTTP: " + ioe);
      }

      // As a fix for the duplicate file download issue, we are going to introduce a
      // minor pause in the loading in order for the ISU web server to issue a
      // unique filename.
      try
      {
        Thread.sleep(2000);
      }
      catch (InterruptedException e)
      {
        logger.warn("Thread was interrupted while sleeping, check for duplicate files from ISU...");
      }
    }

    logger.debug("Done downloading files...");
    logger.info("Data retrieved for: " + species.getString("name"));
  }

  /*
   * Helper method for making the very slight changes to the ISU gff files into
   * the format that we expect (change the chromosomes and feature names)
   */
  private void translateGFF(VCMapLoader main, JSONObject source, JSONObject species, JSONObject annotationSet,
      File file, File destFile)
    throws JSONException
  {
    logger.info("Translating file '" + file + "'...");
    destFile.getParentFile().mkdirs();

    // Get versioning from the JSONObject
    JSONObject versionObj = annotationSet.getJSONObject("version");
    String identifier = versionObj.getString("identifier");
    String versionDate = versionObj.getString("release_date");

    try
    {
      BufferedReader reader = new BufferedReader(new FileReader(file));
      BufferedWriter writer = new BufferedWriter(new FileWriter(destFile));

      // First write the file header
      writer.write("##species " + species.getString("name")); // Not valid tag-value
      writer.newLine();
      writer.write("##version " + identifier);
      writer.newLine();
      writer.write("##version-date " + versionDate);
      writer.newLine();
      writer.flush();

      // Now read in the rest of the file
      while(reader.ready())
      {
        String line = reader.readLine();
        String[] splits = line.split("\t");

        // Check for non-data lines
        if (line.startsWith("#"))
        {
          writer.write(line);
          writer.newLine();
          continue;
        }
        else if (splits.length < 8)
        {
          logger.warn("Encountered bad line in file.  Skipping...");
          continue;
        }

        // Correct some of the bad formatting of the ISU data
        if (splits[0].startsWith("Chr.")) splits[0] = splits[0].substring(4).toUpperCase();
        try
        {
          // NOTE: Parsing these as Doubles and casting them is actual a hack to
          //   solve the problems with the Pig GFF files from ISU (1/15/2011)
          int start = (int) Double.parseDouble(splits[3]);
          int stop = (int) Double.parseDouble(splits[4]);
          if (start > stop)
          {
            logger.warn("This file breaks the GFF specifications (start > stop), swapping values.  Offending line: " + line);
            String swap = splits[3];
            splits[3] = splits[4];
            splits[4] = swap;
          }
        }
        catch (NumberFormatException e)
        {
          // This file has some start and stop positions that are obviously bad
          logger.error("Skipping bad line (start and / or stop are not numeric): " + line);
          continue;
        }

        // Output the first 7 columns
        for (int i = 0; i < 8; i++)
          writer.write(splits[i] + "\t");
        
        // Parse the attributes (column index 8)
        String[] attrs = splits[8].split(";");
        String name = "";
        for (int i = 0; i < attrs.length; i++)
        {
          if (attrs[i].toLowerCase().startsWith("name="))
          {
            attrs[i] = "full name=" + attrs[i].substring(5).replaceAll("\"", "");
          }
          else if (attrs[i].toLowerCase().startsWith("abbrev="))
          {
            name = attrs[i].substring(7) + name;
            continue;
          }
          else if (attrs[i].toLowerCase().startsWith("qtl_id=") || attrs[i].toLowerCase().startsWith("id="))
          {
            name += "-" + attrs[i].substring(attrs[i].indexOf('=') + 1);
            attrs[i] = "source_id=" + attrs[i].substring(attrs[i].indexOf('=') + 1);
          }
          else if (attrs[i].toLowerCase().startsWith("type="))
          {
            attrs[i] = "qtl " + attrs[i];
          }
          else if (attrs[i].toLowerCase().startsWith("trait="))
          {
            attrs[i] = "ontology=" + attrs[i].substring(6);
          }
          writer.write(attrs[i] + ";");
        }
        writer.write("Name=" + name);

        // End this data line
        writer.newLine();
      }
      reader.close();
      writer.close();
    }
    catch (IOException e)
    {
      logger.error("Problem translating file: " + file.getAbsolutePath());
    }
  }

  /*
   * Helper method for translating data files from ISU tab delimited format
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

    // Get versioning from the JSONObject
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
      writer.write("##version " + identifier);
      writer.newLine();
      writer.write("##version-date " + versionDate);
      writer.newLine();
      writer.flush();

      // NOTE: the first line of the ISU files is a header, but contains no "#"
      //   so we just manually skip it.
      if (reader.ready()) reader.readLine();
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

          // Use the range when possible
          if (!splits[columns.getInt("range")].trim().equals(""))
          {
            try
            {
              String[] range = splits[columns.getInt("range")].split("-");
              start = Double.parseDouble(range[0]);
              stop = Double.parseDouble(range[1]);
            }
            catch (NumberFormatException e)
            {
              // Nothing we can do, fall back to position as start/stop
            }
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
            if (key.equals("chromosome") || key.equals("name") || key.equals("type") ||
                key.equals("start") || key.equals("stop") || key.equals("strand") ||
                key.equals("score") || key.equals("phase") || key.equals("range"))
              continue;
            try
            {
              attrs.put(key, splits[columns.getInt(key)]);
            }
            catch (IndexOutOfBoundsException e)
            {
              // Do nothing, this attributes are all optional anyway.
            }
          }

          // Now output our line to the destination file
          DecimalFormat f = new DecimalFormat("#.#####");
          writer.write(splits[columns.getInt("chromosome")] + "\t");
          writer.write("VCMapLoader\tQTL\t" + f.format(start) + "\t" + f.format(stop) + "\t");
          if (columns.has("score")) writer.write(splits[columns.getInt("score")]);
          writer.write("\t");
          if (columns.has("strand")) writer.write(splits[columns.getInt("strand")]);
          writer.write("\t");
          if (columns.has("phase")) writer.write(splits[columns.getInt("phase")]);
          writer.write("\t");
          // Finally the attributes hash
          StringBuilder attrsStr = new StringBuilder();
          attrsStr.append("Name=").append(splits[columns.getInt("symbol")] + "-" + splits[columns.getInt("source_id")]);
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
}
