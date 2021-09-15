package bioneos.vcmap.gui.components;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * <p>Extends the {@link JFileChooser} class so filenames can be validated.</p>
 * <p>Created on: July 12, 2010</p>
 * @author cgoodman
 *
 */
public class SaveJFileChooser
  extends JFileChooser
{
  public static final String[] INVALID_CHARACTERS = { "/", "\n", "\r", "\t", "\0", "\f", "`", "?", "*", "\\", "<", ">", "|", "\"", ":", "%" };

  /**
   * <p>Creates a new Default {@link JFileChooser}</p>
   */
  public SaveJFileChooser()
  {
    super();
  }

  /**
   * <p>Constructor that sets initial selected file</p>
   * @param file
   *    The file that is initially selected
   */
  public SaveJFileChooser(File file)
  {
    super(file);
  }

  /**
   * <p>Determines whether the file is valid before closing the {@link JFileChooser}
   * if it is valid the parents approveSelection method is called.  Otherwise a
   * {@link JOptionPane} with a warning message is displayed.</p>
   */
  @Override
  public void approveSelection()
  {

    if (!isValidDirectory(getSelectedFile()))
      JOptionPane.showMessageDialog(null,
          "<html>The directory <b>\"" + getSelectedFile().getParent() + "\"</b> does not exist.</html>",
          "Save As Error",
          JOptionPane.ERROR_MESSAGE);

    else if (!isValidName(getSelectedFile()))
      JOptionPane.showMessageDialog(null,
          "<html>The filename <b>\"" + getSelectedFile().getName() + "\"</b> is invalid.</html>",
          "Save As Error",
          JOptionPane.ERROR_MESSAGE);

    else
    {
      String fileName = getSelectedFile().getName();

      // Split to check if there is an extension
      String[] fileSplit = fileName.split("\\.");

      // Check to see if the extension matches the extension in the JComboBox
      if ((!fileName.endsWith("." + getFileFilter().getDescription().toUpperCase()) ||
           !fileName.endsWith("." + getFileFilter().getDescription())) &&
          (fileSplit.length > 1 && !fileSplit[fileSplit.length - 1].startsWith(" ")) )
      {
        String ext = fileSplit[fileSplit.length -1];

        // Show option dialog
        Object[] options = {"Use ." + getFileFilter().getDescription(), "Use both", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                    "<html><b>The extension \"." + ext + "\" you have specified does</b></html>\n"
                  + "<html><b>not match the specified extension \"." + getFileFilter().getDescription() + "\".</b></html>\n"
                  + "You can choose to use both, so that your file\n"
                  + "name ends in \"." + ext + "." + getFileFilter().getDescription() + "\"",
                    "Choose Extension",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]);

        // If user choose to use only one extension
        if (choice == 0)
        {
          String newFileName = fileName.substring(0, fileName.length() - ext.length() - 1);
          setSelectedFile(new File(getCurrentDirectory() + System.getProperty("file.separator") +  newFileName));
        }
        // If user choose to use both extensions
        else if (choice == 1)
        {
          setSelectedFile(new File(getSelectedFile().getPath() + "." + getFileFilter().getDescription()));
        }
        // Cancel option
        else
          return;
      }
      else if (!fileName.endsWith("." + getFileFilter().getDescription().toUpperCase()) ||
               !fileName.endsWith("." + getFileFilter().getDescription()))
      {
        setSelectedFile(new File(getSelectedFile().getPath() + "." + getFileFilter().getDescription()));
      }

      super.approveSelection();
    }

  }

  /**
   * <p>Helper method to determine if the user chosen filename is valid.</p>
   * @param file
   *    File whose name is being validated
   * @return
   *    false if the filename contains invalid characters otherwise true
   */
  public boolean isValidName(File file)
  {
    for (String str : INVALID_CHARACTERS)
      if(file.getName().contains(str))
        return false;

    return true;
  }

  /**
   * <p>Helper method to determine if the file's parent directory is valid</p>
   * @param file
   *    File whose directory is being validated
   * @return
   *    true if the directory exists, otherwise false
   */
  public boolean isValidDirectory(File file)
  {
    if (file.getParentFile().exists())
      return true;
    else
      return false;
  }

}