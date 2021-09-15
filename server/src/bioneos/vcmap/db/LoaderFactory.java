package bioneos.vcmap.db;

import java.util.Vector;
import org.json.JSONObject;
import org.json.JSONException;
import bioneos.vcmap.db.loaders.Loader;

/**
 * Factory class to produce {@link Loader} classes that can handle 1) gathering
 * and 2) processing data files for a specific data source.  This pattern will
 * allow us to implement DI / IoC so that we can create future
 * {@link Loader} classes to handle additional data sources without having to
 * modify any of the main {@link VCMapLoader} code.
 * @author sgdavis
 */
public class LoaderFactory
{
  /* The list of registered Loaders. */
  private static Vector<Loader> loaders = new Vector<Loader>();

  /**
   * Create a proper loader for the specified data source.  This method assumes
   * that the 'source' parameter has the following attributes:
   * <ul>
   * <li>'name' : The name of the data source</li>
   * <li>'type' : The type of data to load (not always necessary)</li>
   * </ul>
   * @param source
   *   The properly formatted {@link JSONObject} representing a data source.
   * @return
   *   The concrete subclass of {@link Loader} that is capable of handling
   *   this data source.
   * @throws NoSuchLoaderException
   *   If no appropriate {@link Loader} is found, or if an error occurred
   */
  public static Loader getLoader(JSONObject source)
    throws NoSuchLoaderException
  {
    try
    {
      for (Loader l : loaders)
        if (l.handles(source.getString("name"), source.getString("type")))
          return l;
    }
    catch (JSONException e)
    {
      throw new NoSuchLoaderException("Error while looking for Loader for '" + source.optString("name", "unknown") + "'", e);
    }

    throw new NoSuchLoaderException("Loader for datasource '" + source.optString("name", "unknown") + "' cannot be found");
  }

  /**
   * This method is called by new instances of a {@link Loader} subclass in
   * order for the Factory to know they exist.  Presumably, this {@link Loader}
   * object will be instantiated and this method called in a static block of
   * the new {@link Loader} class.
   * @param loader
   *   An object of new class to register.
   */
  public static void registerLoader(Loader l)
  {
    loaders.add(l);
  }
}
