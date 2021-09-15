package bioneos.vcmap.db.loaders;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import bioneos.vcmap.db.VCMapLoader;
import bioneos.vcmap.db.LoaderFactory;

/**
 * The Loader subclass to handle data provided by Ensembl by calling the underlying Perl script.
 * @author sgdavis@bioneos.com
 */
public class EnsemblLoader
  extends Loader
{
  /* Register our loader with the Factory */
  static
  {
    EnsemblLoader l = new EnsemblLoader();
    LoaderFactory.registerLoader(l);
  }

  /**
   * Specify what types of data sources our loader handles.
   * @param name
   *   The name of the data source in question.
   * @param type
   *   The type of data to load from the data source (ignored).
   * @return
   *   True IFF name == 'ensembl' and type == 'annotation', otherwise false.
   */
  public boolean handles(String name, String type)
  {
    if (!type.toLowerCase().equals("annotation")) return false;
    return name.toLowerCase().equals("ensembl");
  }

  /**
   * Process the data files from ISU and convert the tab delimited text files
   * into our specialized GFF3 format so that the main
   * {@link bioneos.vcmap.db.VCMapLoader} can process the data and insert it
   * into the VCMap database.
   * @param source
   *   The Ensembl data source and configuration.
   * @param processingDir
   *   The directory to store the final GFF files that are created.
   * @throws JSONException
   *   Occurs whenever the configuration of the data source is missing required
   *   attributes (bad configuration file most likely)
   */
  public void processData(VCMapLoader main, JSONObject source, File processingDir)
    throws JSONException
  {
    logger.info("EnsemblLoader.processData() started...");

    File processing = new File(processingDir, source.getString("type").toLowerCase());
    processing = new File(processing, source.getString("name"));
    logger.debug("Creating processing scratch directory: " + processing.getAbsolutePath());
    if (processing.exists())
      main.recursiveDelete(processing);
    processing.mkdirs();

    //
    // Execute the Perl script
    //
    try
    {
      String[] cmd = new String[] {
          "./bin/ensembl_annotation_loader.pl",
          "--config=" + source.toString(),
          "--output=" + processing.getAbsolutePath()
      };
      Process perl = Runtime.getRuntime().exec(cmd);
      perl.waitFor();

      int exit = perl.exitValue();
      if (exit != 0) throw new IOException("Perl script exited abnormally with " + exit);
    }
    catch (InterruptedException e)
    {
      logger.error("Thread interrupted during Perl Ensembl loader execution.  Deleting incomplete files...");
      main.recursiveDelete(processing);      
    }
    catch (IOException e)
    {
      logger.error("I/O Problem executing Ensembl loader script.  Deleting incomplete files...");
      logger.debug("Error was: " + e);
      main.recursiveDelete(processing);
    }
    
    // Post process the JSON config object to accept version date (today) & version (from filename)
    logger.info("Handling data files from the Perl script");
    JSONArray speciesList = source.getJSONArray("species");
    for (int i = 0; i < speciesList.length(); i++)
    {
      JSONObject species = speciesList.getJSONObject(i);
      logger.debug("Looking at " + species.getString("name") + "...");
      JSONArray annotationSets = species.getJSONArray("annotations");
      for (int j = 0; j < annotationSets.length(); j++)
      {
        JSONObject annotationSet = annotationSets.getJSONObject(j);
        logger.debug("Looking at annotation set for type: " + annotationSet.getString("type") + "...");

        // Create the version values for the main load script
        JSONObject version = new JSONObject();

        // Parse filename from directory (if exists)
        File dir = new File(processing, species.getString("name"));
        dir = new File(dir, annotationSet.getString("assembly_type"));
        logger.debug("Looking in directory: " + dir.getAbsolutePath() + "...");
        String identifier = "";
        Pattern p = Pattern.compile("data-.*:(.*)\\.gff3");
        for (String filename : dir.list())
        {
          Matcher m = p.matcher(filename);
          if (m.matches())
          {
            if (!identifier.equals(""))
              logger.warn("Identifier overlap!! Not all data will be loaded");
            identifier = m.group(1); 
          }
        }
        version.put("identifier", identifier);

        // Put the release date from the config into our version object
        version.put("release_date", annotationSet.getString("release_date"));

        // Save our values (for main load script)
        annotationSet.put("version", version);
      }
    }
  }
}
