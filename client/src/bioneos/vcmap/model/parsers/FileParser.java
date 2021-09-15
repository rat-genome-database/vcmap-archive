package bioneos.vcmap.model.parsers;

import java.io.File;
import java.sql.SQLException;
import java.util.Vector;

import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.DisplayMap;

/**
 * <p>Interface to be implemented by any class that will parse files
 * for loading custom {@link Annotation} {@link MainGUI}</p>
 *
 * <p>Created on: August 11, 2010</p>
 * @author cgoodman@bioneos.com
 *
 */
public interface FileParser
{
  // Constants Custom constant appended to custom annotation types
  public static final String SUFFIX = " (Custom)";

  // Error codes
  public static final int MATCHING_ID_ERROR = 1;
  public static final int NO_DATA_LOADED_ERROR = 5;
  public static final int FILE_FORMAT_ERROR = 7;
  public static final int FEATURE_MISSING_ERROR = 8;
  public static final int GENERAL_ERROR = 10;
  public static final int INVALID_FORMAT_ERROR = 11;
  public static final int MISSING_INFO_ERROR = 12;

  /**
   * <p>Method called when parsing {@link Annotation} from a {@link File}</p>
   * @param file
   *    the {@link File} that is being parsed
   * @return
   *    A {@link Vector} of {@link Annotation} parsed from the file.
   */
  public Vector<Annotation> parseFile(File file, DisplayMap displayMap, MainGUI mainGUI)
    throws SQLException;

  /**
   * <p>Determines if there was an error of a certain type in the file</p>
   * @param errorCode
   *    The <code>integer</code> representation of the error
   * @return
   *    True if the error occured while parsing the file, otherwise false
   */
  public boolean hasError(int errorCode);

  public String getErrorString();
}
