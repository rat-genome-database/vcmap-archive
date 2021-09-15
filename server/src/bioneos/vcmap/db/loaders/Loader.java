package bioneos.vcmap.db.loaders;

import bioneos.vcmap.db.VCMapLoader;
import bioneos.vcmap.db.LoaderFactory;

/**
 * This abstract class is the basis for creating new handlers for additional
 * data sources that will load data into the VCMap data warehouse.  Concrete
 * subclasses of this abstract class must do two things:
 * <ol>
 * <li>Provide an implementation of Loader.processData()</li>
 * <li>Register with the {@link LoaderFactory}</li>
 * </ol>
 * In order to ensure subclasses are properly registered, they should all
 * contain the following static block.  Classes are loaded with 
 * {@link Class#forName} in the main {@link VCMapLoader} resulting in the
 * execution of this block.
 * <pre>
 *  static
 *  {
 *    Loader l = new Loader();
 *    LoaderFactory.registerLoader(l);
 *  }
 * </pre>
 * @author sgdavis
 */
public abstract class Loader
{

  /**
   *  Logger reference to allow for standard logging as setup by the 
   *  {@link bioneos.vcmap.db.VCMapLoader} class.
   */
  protected static org.apache.log4j.Logger logger = 
    org.apache.log4j.Logger.getLogger(VCMapLoader.class.getName());

  /**
   * Indicate whether or not this Loader can process the described data source.
   * Because this loader is abstract, it will always return false from this
   * method.
   * @param name
   *   The name of the data source.
   * @param type
   *   The type of data to be loaded from this data source.
   * @return
   *   True if this implementation can process the data source specified as
   *   a parameter, false otherwise.
   */
  public boolean handles(String name, String type)
  {
    return false;
  }

  /**
   * Process the data from the specified data source.  The 'source' param
   * will not only describe the data source to process, but also provide
   * information about what types of data to gather.  This is all specified in
   * the external configuration file.  Concrete subclasses must implement this
   * method including (but possibly not limited to) 1) connecting to the data
   * source using supplied credentials 2) pruning unneeded data, and 
   * 3) transforming the data into the appropriate format (GFF3) and finally
   * saving that data into the specified location.
   * <p>After processing, each Loader should have produced two files for each
   * map that will be loaded by the main script:
   * <ol>
   * <li><strong>data.gff3</strong> The data file in our modified gff3 format
   * located at <code>processingDir/source/species/map type/data.gff3</code></li>
   * <li><strong>chromosomes.txt</strong> A tab-delimited file with 2 columns,
   * the first indicating the names of the chromosomes and the second
   * indicating the length of the chromosomes.  This value should be properly
   * scaled to an integer according to the settings in the configuration for
   * this source.  This file should be located at
   * <code>processingDir/source/species/map type/chromosomes.txt</code></li>
   * @param main
   *   The main object controlling the load process.  This object stores some
   *   data that is commonly used throughout several of the loaders.
   * @param source
   *   The description / configuration of the data source to process.
   * @param processingDir
   *   The file location to store the files after they have been converted to
   *   GFF3 format so that the main {@link bioneos.vcmap.db.VCMapLoader} can
   *   process them the rest of the way.
   * @throws JSONException
   *   This occurs when required attributes are missing from the data source
   *   configuration (i.e. bad configuration file).
   */
  public abstract void processData(VCMapLoader main, org.json.JSONObject source, java.io.File processingDir)
    throws org.json.JSONException;
}
