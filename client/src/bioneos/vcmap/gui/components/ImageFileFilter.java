package bioneos.vcmap.gui.components;

import java.io.File;

import javax.swing.JComboBox;
import javax.swing.filechooser.FileFilter;

/**
 * <p>Extends the {@link FileFilter} class to be used with the {@link CustomJFileChooser}
 * to choose between supported file formats.</p>
 *
 * <p>Created on: July 12, 2010</p>
 * @author cgoodman
 *
 */
public class ImageFileFilter extends FileFilter
{
  private final String extension;

  /**
   * <p>Constructor sets the </p>
   * @param ext
   */
  public ImageFileFilter(String ext)
  {
    extension = ext;
  }

  /**
   * <p>Accept method implemented. Accepts directories and files ending with the
   * extension name</p>
   */
  public boolean accept(File f)
  {
    if (f.getName().endsWith("." + extension) || f.getName().endsWith("." + extension.toUpperCase()) || f.isDirectory())
      return true;
    else
      return false;
  }

  /**
   * <p>Returns the description of the {@link FileFilter} or the estension of the
   * files it represnts.  It is used {@link CustomJFileChooser}s {@link JComboBox}
   * of filters.</p>
   */
  public String getDescription()
  {
    return extension;
  }

}
