package bioneos.vcmap.db.loaders;

import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigInteger;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.DecimalFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import bioneos.vcmap.db.VCMapLoader;
import bioneos.vcmap.db.LoaderFactory;

/**
 * The Loader subclass to handle annotation data provided by NCBI.
 *
 * @author sgdavis
 */
public class NcbiAnnotationLoader
  extends Loader
{
  /* Register our loader with the Factory */
  static
  {
    NcbiAnnotationLoader l = new NcbiAnnotationLoader();
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
    if (!type.toLowerCase().equals("annotation")) return false;
    return (name.toLowerCase().equals("ncbi gene") || name.toLowerCase().equals("ncbi unists"));
  }

  /**
   * Process the data files from NCBI and convert the MapData (.md) files into
   * GFF3 format so the main {@link bioneos.vcmap.db.VCMapLoader} can process
   * the data and insert it into the VCMap database.
   * @param source
   *   The NCBI data source and configuration.
   * @param processingDir
   *   The directory to store the final GFF files that are created.
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
        
        // Gather the Build information file if available (save as BUILD_INFO)
        String identifier = "unknown";
        String assembly = "unknown";
        if (annotationSet.has("assembly_version"))
          assembly = annotationSet.getString("assembly_version");
        if (annotationSet.getJSONObject("version").has("file"))
        {
          logger.info("Downloading build info file...");
          main.downloadFile(ftp, species.getString("location") + annotationSet.getJSONObject("version").getString("file"),
             "BUILD_INFO-" + i, asetScratch);

          // Open the BUILD_INFO file and get the versioning information
          BufferedReader reader = null;
          identifier = "";
          String versionDate = "";
          try
          {
            reader = new BufferedReader(new FileReader(new File(asetScratch, "BUILD_INFO-" + i)));
            while (reader.ready())
            {
              String line = reader.readLine();
              Matcher m1 = Pattern.compile(annotationSet.getJSONObject("version").getString("build")).matcher(line);
              Matcher m2 = Pattern.compile(annotationSet.getJSONObject("version").getString("version")).matcher(line);
              Matcher m3 = Pattern.compile(annotationSet.getJSONObject("version").getString("date")).matcher(line);
              if (m1.find())
                identifier = m1.group(1) + identifier;
              else if (m2.find())
                identifier += "." + m2.group(1);
              else if (m3.find())
                versionDate = m3.group(1);
            }
            reader.close();

            // Update our JSON object (for future processing)
            JSONObject version = annotationSet.getJSONObject("version");
            version.put("identifier", identifier);
            version.put("release_date", versionDate);
          }
          catch (IOException e)
          {
            logger.error("Problems reading the versioning file " + 
                annotationSet.getJSONObject("version").getString("file") + "!  Skipping this set...");
            logger.debug("Error was: " + e);
            continue;
          }
        }
        else if (annotationSet.getJSONObject("version").has("identifier"))
        {
          // Assume any version objects without a "file" have a manually
          // configured identifier
          identifier = annotationSet.getJSONObject("version").getString("identifier");
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
          filename = i + "-" + filename;
          boolean success = main.downloadFile(ftp, species.getString("location") + file, filename, asetScratch);          

          // If the file is grabbed
          if (success)
          {
            File localFile = new File(asetScratch, filename);
            
            // If necessary, gunzip
            localFile = gunzip(localFile);

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
      
      // Final step -- special handling for STS data
      // NOTE: In order to cut down on the number of STS markers loaded into
      //   our database, we limit the features loaded to those that will have
      //   a mapping onto the Genetic / RH maps.  In order to do this, we are
      //   making the assumption that we can find the data files for any STS
      //   markers at the speciesScratch location.  We simply build a list of
      //   all of the UniSTS ids in non-Genomic maps at this location.
      //   Anything that doesn't match these ids will culled from the Genomic.
      logger.info("Culling extraneous UniSTS features");
      TreeSet<String> unistsIds = new TreeSet<String>();
      Pattern unists = Pattern.compile(".*(UniSTS:\\d+).*");
      for (File dir : destDir.listFiles())
      {
        if (!dir.isDirectory()) continue;
        if (dir.getName().equals("Genomic")) continue;
        
        logger.debug("Reading UniSTS ids from: " + dir.getAbsolutePath());
        for (File data : dir.listFiles())
        {
          try
          {
            BufferedReader dataReader = new BufferedReader(new FileReader(data));
            while (dataReader.ready())
            {
              String line = dataReader.readLine();
              Matcher m = unists.matcher(line);
              if (m.matches())
                unistsIds.add(m.group(1));
            }
          }
          catch (IOException e)
          {
            logger.error("General I/O exception while reading UniSTS identifiers...");
            logger.warn("All UniSTS markers will be loaded!");
            unistsIds = null;
          }
        }
      }
      
      logger.debug("Filtering genomic data file by matched UniSTS identifiers...");
      File genomic = new File(destDir, "Genomic");
      if (genomic.isDirectory())
      {
        for (File data : genomic.listFiles())
        {
          try
          {
            File dataFinal = new File(genomic, "final-" + data.getName());
            BufferedReader reader = new BufferedReader(new FileReader(data));
            BufferedWriter writer = new BufferedWriter(new FileWriter(dataFinal));
            while (reader.ready())
            {
              String line = reader.readLine();
              Matcher m = unists.matcher(line);
              if ((m.matches() && unistsIds.contains(m.group(1))) || !m.matches())
              {
                writer.write(line);
                writer.newLine();
              }
            }
            reader.close();
            writer.close();
            
            // Remove all file and replace with final.
            logger.debug("Replacing " + data.getName() + " with " + dataFinal.getName());
            data.delete();
            dataFinal.renameTo(data);
          }
          catch (IOException e)
          {
            logger.error("Problem post-processing the sts data, extra features may be loaded");
            logger.debug("Error was: " + e);
          }
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
  private void checkConnection(FTPClient ftp, JSONObject source)
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

  /*
   * Helper method for translating data files from NCBI .md formats into the
   * standard GFF3 format.  The only quirk to this translation is that we
   * create GFF files that never have reverse referencing objects, therefore
   * the files should be able to be processed linearly without any special
   * consideration for the future data entries.  This allows for a drastic
   * improvement in performance.
   * NOTE: The chromosome map processing for sources with more than one file
   * may not always be correct.  In the future we may want to read in the old
   * map just to ensure the lengths don't shrink.  This is not a major concern
   * though so for now we will leave this extra step out.
   */
  private void translateFile(VCMapLoader main, JSONObject source, JSONObject species, 
      JSONObject annotationSet, File file, File destFile)
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

    // Log our assembly string for debug
    if (annotationSet.has("assembly"))
      logger.info("Using assembly identifier: " + annotationSet.getString("assembly"));

    // 
    // Main processing loop
    //
    BufferedReader reader = null;
    BufferedWriter writer = null;
    try
    {
      reader = new BufferedReader(new FileReader(file));
      writer = new BufferedWriter(new FileWriter(destFile));

      // First GFF3 header
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
      
      // Now loop the input file - building a Hashtable of our features
      // NOTE:  This Hashtable will be keyed by feature name, however, it
      //   is possible to have duplicate names in different locations
      //   (mainly from incompleted assemblies).  Therefore, when
      //   appropriate, the gene name may be appended with .X in order
      //   to accomodate these situations.
      //   The structure of the Object[] will be as follows:
      //     0: String chr name
      //     1: String feature name
      //     2: String feature type
      //     3: Double start
      //     4: Double stop
      //     5: Character strand
      //     6: String attributes
      //   These will roughly correlate to the necessary GFF3 columns. The
      //   attributes column will have homologene ids, source ids, and any
      //   ther data that may be useful to insert into the AVPs tables.
      Hashtable<String, Object[]> features = new Hashtable<String, Object[]>();
      String lastChr = null;
      int processed = 0, skipped = 0;
      while (reader.ready())
      {
        String line = reader.readLine();

        // Skip comment lines
        if (line.trim().indexOf("#") == 0) continue;

        // Cut up our data line
        String[] splits = line.split("\t");

        // Skip unlocalized sequence
        if (splits[columns.getInt("chromosome")].indexOf('|') >= 0) continue;

        // Skip alternative assemblies if present
        // NOTE: This underscore replacement seems like a hack, but is
        //   necessary because NCBI has used two identifiers for the primary
        //   assembly for human (at least), one with an underscore and one with
        //   a space.  So basically we just accept either space or underscore
        //   for the assembly tags (in the same spot of course).
        if (columns.has("assembly") && annotationSet.has("assembly"))
        {
          if (!splits[columns.getInt("assembly")].equals(annotationSet.getString("assembly")) && 
              !splits[columns.getInt("assembly")].replaceAll("_", " ").equals(annotationSet.getString("assembly")))
          {
            skipped++;
            continue;
          }
        }

        // Use the columns map to translate the fields into appropriate pos
        String ncbiId = splits[columns.getInt("source_id")];
        Object[] row = new Object[7];
        row[0] = splits[columns.getInt("chromosome")].intern();
        row[1] = splits[columns.getInt("name")];
        row[2] = splits[columns.getInt("type")].intern();
        if (row[2].toString().trim().equals("")) row[2] = "UNKNOWN";
        row[3] = Double.valueOf(splits[columns.getInt("start")]);
        row[4] = Double.valueOf(splits[columns.getInt("stop")]);
        if (((Double) row[3]) > ((Double) row[4]))
        {
          // GFF specifies that start is always < stop
          Object swap = row[3];
          row[3] = row[4];
          row[4] = swap;
        }
        if (columns.has("strand"))  // Optional
          row[5] = Character.valueOf(splits[columns.getInt("strand")].charAt(0));
        else
          row[5] = ".";
        // Add any additional parameters to our attributes column
        Hashtable<String, String> attrs = new Hashtable<String, String>();

        for (Iterator<?> keys = columns.keys(); keys.hasNext(); )
        {
          String key = keys.next().toString();
          // Skip our primary attributes (in other columns already)
          if (key.equals("chromosome") || key.equals("name") ||
              key.equals("start") || key.equals("stop") || key.equals("strand"))
            continue;
          attrs.put(key, splits[columns.getInt(key)]);
        }
        row[6] = attrs;

        // Compare to existing data and combine or rename as needed
        // NOTE: The purpose of this loop is to determine if this identifier is 
        //   already in use in our hash.  If so, we need to check if the
        //   previous id refers to a feature that overlaps with our current
        //   data.  If so, then we will combine these features into one (for
        //   example PSEUDO, RNAs, UTR, or CDS).  If the feature falls on a
        //   new position of the chromosome, or different chromosome entirely,
        //   then we treat this as a new feature (mapped to two locations)
        int suffix = 0;
        String finalName = ncbiId;
        while (features.containsKey(finalName))
        {
          // NOTE: Important!!  This only works correctly because we are
          // assuming that the longest feature (usually the GENE) appears first
          // in the data file.  Currently that is the case, but it is possible
          // that NCBI may change the format so this isn't the case in the
          // future.  If so, simply checking for overlap will not be sufficient
          // (exons never overlap each other, but they refer to the same GENE
          // concept).
          // TODO - We should add some code to detect if the above situation
          // appears to have happened, so we can send out a warning.
          if (overlap(row, features.get(finalName)))
          {
            // These overlap, so combine them into one, and terminate the loop
            row = combine(row, features.get(finalName));
            break;
          }
          else
          {
            // No overlap, so try a new hash key
            // NOTE: Appending ".v" to the hash key might result in a second
            //   collision, especially if many duplicated ids are in use.
            //   Therefore, we also must test this new key (finalName) for
            //   a collision, and determine if there is an appropriate overlap
            //   (so we can combine features) or if not, further up the ver #.
            suffix++;
            finalName = ncbiId + "." + suffix;
          }
        }

        // NOTE: This code assumes that all features for a chromosome are
        //   grouped together.  This is a safe assumption as of now (7/2010)
        //   but, if NCBI changes their file format, this code might need to
        //   be updated.  That would be a problem though because we cannot
        //   fit the entire file representation in memory.
        if (lastChr != null && !lastChr.equals(row[0]))
        {
          writeHash(features, writer);
          features.clear();
        }

        // Add our row (either new, or combined from previous loop)
        features.put(finalName, row);

        lastChr = row[0].toString();
        processed++;
      }
      logger.info("Processed " + processed + " and skipped " + skipped + " data lines");

      // Now output the remainder of our hash from memory
      writeHash(features, writer);

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
   * Helper method to determine when two features (represented by the Object[]
   * defined in the NOTES of translateFile) overlap physically (same feature,
   * different types)
   *   0: String chr name
   *   1: String feature name
   *   2: String feature type
   *   3: Double start
   *   4: Double stop
   *   5: Character strand
   *   6: String attributes
   */
  private boolean overlap(Object[] left, Object[] right)
  {
    // Different chromosomes
    if (!left[0].equals(right[0])) return false;

    // Get starts / stops
    double lStart, lStop;
    lStart = ((Double) left[3]).doubleValue();
    lStop = ((Double) left[4]).doubleValue();
    double rStart, rStop;
    rStart = ((Double) right[3]).doubleValue();
    rStop = ((Double) right[4]).doubleValue();

    // Ensure start < stop
    if (lStop < lStart)
    {
      double swap = lStart;
      lStart = lStop;
      lStop = swap;
    }
    if (rStop < rStart)
    {
      double swap = rStart;
      rStart = rStop;
      rStop = swap;
    }

    // Calculate overlap
    if ((lStart >= rStart && lStart <= rStop) || (lStop >= rStart && lStop <= rStop))
      return true;
    if ((rStart >= lStart && rStart <= lStop) || (rStop >= lStart && rStop <= lStop))
      return true;

    // Nothing else to check -- no overlap
    return false;
  }

  /*
   * Helper method to combine to features (as defined in translateFile) into a 
   * single feature with a combination of the data.  For example, we only are
   * concerned with genes and pseudogene, so type CDS, UTR, or RNA can get
   * discarded.  Type PSEUDO can convert GENE.  And we always use the outer
   * most start / stop positions for the combined feature.
   */
  private Object[] combine(Object[] left, Object[] right)
  {
    Object[] row = new Object[7];
    Hashtable<String, String> newAttrs = new Hashtable<String, String>();
    // Chromosome
    row[0] = left[0];
    // Names should be the same, but add aliases if they aren't
    if (!left[1].equals(right[1]))
    {
      // Try to use the GENE row, otherwise punt (either row will work)
      if (left[2].equals("GENE"))
      {
        row[1] = left[1];
        newAttrs.put("alias", right[1].toString());
      }
      else
      {
        row[1] = right[1];
        newAttrs.put("alias", left[1].toString());
      }
    }
    else
    {
      row[1] = left[1];
    }
    // Type (PSEUDO > GENE > CDS, UTR)
    if (left[2].equals("PSEUDO") || right[2].equals("PSEUDO"))
      row[2] = "PSEUDO";
    else if (left[2].equals("GENE") || right[2].equals("GENE"))
      row[2] = "GENE";
    else
      row[2] = left[2];
    // Start / Stop
    double l = ((Double) left[3]).doubleValue();
    double r = ((Double) right[3]).doubleValue();
    if (l < r) row[3] = left[3];
    else row[3] = right[3];
    l = ((Double) left[4]).doubleValue();
    r = ((Double) right[4]).doubleValue();
    if (l > r) row[4] = left[4];
    else row[4] = right[4];
    // Strand (assume they are the same... else unresolvable problem anyway)
    row[5] = left[5];
    // Additional attributes
    Hashtable<?, ?> values = (Hashtable<?, ?>) left[6];
    for (int i = 0; i < 2; i++)  // Loop just to save duplicating code for left/right
    {
      for (Object k : values.keySet())
      {
        String key = k.toString();
        if (newAttrs.containsKey(key)) newAttrs.put(key, merge(newAttrs.get(key), values.get(key).toString()));
        else newAttrs.put(key, values.get(key).toString());
      }
      values = (Hashtable<?, ?>) right[6];
    }
    row[6] = newAttrs;

    return row;
  }

  /*
   * Helper method to merge two lists of comma separate value strings.  This is
   * definitely not a very efficient way to do things, but performance is not a
   * major concern right now.
   */
  private String merge(String a, String b)
  {
    String[] splitsA = a.split(",");
    String[] splitsB = b.split(",");
    StringBuilder merged = new StringBuilder();
    for (int i = 0; i < splitsA.length; i++)
    {
      boolean match = false;
      for (int j = 0; j < splitsB.length; j++)
        if (splitsA[i].equals(splitsB[j]))
          match = true;
      if (!match)
        merged.append(merged.length() == 0 ? "" : ",").append(splitsA[i]);
    }

    for (int j = 0; j < splitsB.length; j++)
      merged.append(merged.length() == 0 ? "" : ",").append(splitsB[j]);
    return merged.toString();
  }

  /*
   * Helper method to output our feature hash memory representation to the
   * processed file directory. In proper GFF3 format
   */
  private void writeHash(Hashtable<String, Object[]> features, BufferedWriter writer)
    throws IOException
  {
    for (String ident : features.keySet())
    {
      Object[] feature = features.get(ident);
      StringBuilder line = new StringBuilder();
      // Nine Columns in GFF
      // NOTE: Our array is a little different so be sure to process properly.
      //   Columns indexed 5 and 7 (score and phase) are skipped
      for (int i = 0; i < 9; i++) 
      {
        if (i == 0) line.append(feature[0].toString());
        else if (i == 1) line.append("VCMapLoader");
        else if (i == 2) line.append(feature[i]);
        else if ((i == 3 || i == 4) && feature[i] instanceof Double)
        {
          DecimalFormat f = new DecimalFormat("#.#####");
          line.append(f.format(((Double) feature[i]).doubleValue()));
        }
        else if (i == 6) line.append(feature[5]);
        else if (i == 8 && feature[6] instanceof Hashtable<?, ?>)
        {
          Hashtable<?, ?> t = (Hashtable<?, ?>) feature[6];
          StringBuilder attrs = new StringBuilder();
          attrs.append("Name=").append(feature[1]);
          for (Object attr : t.keySet())
          {
            String key = VCMapLoader.escapeGff3(attr.toString());
            String value = VCMapLoader.escapeGff3(t.get(attr).toString());
            attrs.append("; ").append(key).append("=").append(value);
          }
          line.append(attrs.toString());
        }

        // Avoid adding a tab after the last column
        if (i < 8) line.append("\t");
      }
      writer.write(line.toString());
      writer.newLine();
    }
    writer.flush();
  }
}
