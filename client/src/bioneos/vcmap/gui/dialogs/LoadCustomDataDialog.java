package bioneos.vcmap.gui.dialogs;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileFilter;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.CustomAnnotationLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.Tutorial;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.parsers.FileParser;
import bioneos.vcmap.util.Util;

/**
 * <p>This class displays a {@link JDialog} that allows a user
 * to load custom {@link Annotation} for a specified {@link DisplayMap}</p>
 * <p>Created on: July 9, 2010</p>
 * @author cgoodman
 *
 */
public class LoadCustomDataDialog
  extends VCMDialog
  implements ActionListener, ItemListener, HyperlinkListener, CustomAnnotationLoader
{
  public static final String[] supportedTypes = {"GFF3", "BED", "SAM/BAM", "VCF"};

  private static HashMap<MainGUI, LoadCustomDataDialog> instances = new HashMap<MainGUI, LoadCustomDataDialog>();

  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private MainGUI mainGUI;

  // Variables
  private DisplayMap mapToAdd;
  private Vector<DisplayMap> displayMaps;

  // GUI Components
  private JFileChooser fileChooser;
  private JEditorPane lMessage;
  private JLabel lPath;
  private JTextField pathField;
  private JButton pathButton;
  private JLabel mapStatus;
  private JLabel lMap;
  private JComboBox mapBox;
  private JLabel lErrors;
  private JButton load;
  private JButton cancel;

  // Icons
  private ImageIcon checkIcon;
  private ImageIcon errorIcon;
  private ImageIcon warningIcon;

  // File to be loaded
  private File file;
  private String fileType;

  // Separate Threads
  private FileValidator fileV;

  // Constants
  public static final int PREFERRED_MESSAGE_WIDTH = 525;
  public static final String TUTORIAL_TEXT = "CustomAnnotation";
  public static final String NO_FILE_ERROR = "No file has been chosen";
  public static final String MATCHING_MAP_FOUND = "The file you have selected will be loaded onto the map indicated in the drop down menu: ";
  public static final String NO_MAPS_MATCHED = "Location of data cannot be automatically determined.  Please select one of the open maps below:";
  public static final String NO_MAP_SELECTED_ERROR = "There is no map selected.  Please select a map";

  /**
   * <p>Sets up the components in the {@link LoadCustomDataDialog}</p>
   * @param parent
   */
  private LoadCustomDataDialog(MainGUI parent)
  {
    super(parent, false);

    this.mainGUI = parent;

    // Get Icons
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/error.png");
      if(imageUrl != null)
      {
        errorIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl));
      }
    }
    catch (Exception e)
    {
      logger.warn("Error retrieving the error icon: " + e);
    }

    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/check.png");
      if(imageUrl != null)
      {
        checkIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl));
      }
    }
    catch (Exception e)
    {
      logger.warn("Error retrieving the check icon: " + e);
    }

    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/warning.png");
      if(imageUrl != null)
      {
        warningIcon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl));
      }
    }
    catch (Exception e)
    {
      logger.warn("Error retrieving the check icon: " + e);
    }

    // Setup file dialog properties
    fileChooser = new JFileChooser();
    fileChooser.setAcceptAllFileFilterUsed(false);

    fileChooser.addChoosableFileFilter(
      new FileFilter()
      {
        public String getDescription()
        {
          return "GFF3 file (*.GFF, *.GFF3, *.TXT)";
        }

        public boolean accept(File f)
        {
          return (f.isDirectory() ||
              f.getName().toUpperCase().endsWith(".GFF") ||
              f.getName().toUpperCase().endsWith(".TXT") ||
              f.getName().toUpperCase().endsWith(".GFF3"));
        }
      });
    fileChooser.addChoosableFileFilter(
      new FileFilter()
      {
        public String getDescription()
        {
          return "BED file (*.BED, *.TXT)";
        }

        public boolean accept(File f)
        {
          return (f.isDirectory() ||
              f.getName().toUpperCase().endsWith(".TXT") ||
              f.getName().toUpperCase().endsWith(".BED"));
        }
      });
    fileChooser.addChoosableFileFilter(
      new FileFilter()
      {
        public String getDescription()
        {
          return "SAM/BAM file (*.SAM, *.BAM)";
        }

        public boolean accept(File f)
        {
          return (f.isDirectory() ||
              f.getName().toUpperCase().endsWith(".SAM") ||
              f.getName().toUpperCase().endsWith(".BAM"));
        }
      });
    fileChooser.addChoosableFileFilter(
      new FileFilter()
      {
        public String getDescription()
        {
          return "VCF file (*.VCF, *.TXT)";
        }

        public boolean accept(File f)
        {
          return (f.isDirectory() ||
              f.getName().toUpperCase().endsWith(".VCF") ||
              f.getName().toUpperCase().endsWith(".TXT"));
        }
      });

    String text = "VCMap supports several different file formats to represent genomic " +
    		  "features.  For a description of the file formats supported you can visit " +
    		  "<a href=\"http://vcmap.bioneos.com/supported-files.php\">this page</a>.  You " +
    		  "can also <a href=\"" + TUTORIAL_TEXT + "\">view the tutorial page</a>.";

    lPath = new JLabel("File Path:");

    // Set up JEditorPane
    lMessage = new JEditorPane() {
          public Dimension getPreferredSize()
          {
            int height = (super.getPreferredSize().width / PREFERRED_MESSAGE_WIDTH) + 1;
            return new Dimension(PREFERRED_MESSAGE_WIDTH, super.getPreferredSize().height * height);
          }
    };
    lMessage.setEditable(false);
    lMessage.setBackground(lPath.getBackground());
    lMessage.setContentType("text/html");
    lMessage.setText(text);
    lMessage.addHyperlinkListener(this);

    pathField = new JTextField();
    pathField.setEditable(false);
    pathField.addMouseListener(
        new MouseAdapter()
        {
          public void mouseReleased(MouseEvent me)
          {
            if (SwingUtilities.isLeftMouseButton(me))
            {
              showFileChooser();
            }
          }
        }
      );


    pathButton = new JButton("Browse...");
    pathButton.setActionCommand("ChooseFile");
    pathButton.addActionListener(this);

    mapStatus = new JLabel(" ");
    mapStatus.setHorizontalTextPosition(JLabel.RIGHT);
    mapStatus.setVerticalAlignment(JLabel.CENTER);
    mapStatus.setFont(new Font(mapStatus.getFont().getName(), mapStatus.getFont().getStyle(), 10));

    lMap = new JLabel("Data Location:");
    mapBox = new JComboBox();
    mapSelectEnabled(false);

    lErrors = new JLabel();
    lErrors.setHorizontalTextPosition(JLabel.RIGHT);
    lErrors.setVerticalAlignment(JLabel.CENTER);
    lErrors.setFont(new Font(lErrors.getFont().getName(), lErrors.getFont().getStyle(), 10));

    load = new JButton("Load");
    load.addActionListener(this);
    load.setEnabled(false);

    cancel = new JButton("Cancel");
    cancel.addActionListener(this);

    SpringLayout s = new SpringLayout();
    JPanel mainPanel = new JPanel(s);

    s.putConstraint(SpringLayout.NORTH, lMessage, 20, SpringLayout.NORTH, mainPanel);
    s.putConstraint(SpringLayout.WEST, lMessage, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.NORTH, lPath, 15, SpringLayout.SOUTH, lMessage);
    s.putConstraint(SpringLayout.WEST, lPath, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.EAST, pathButton, 0, SpringLayout.EAST, lMessage);
    s.putConstraint(SpringLayout.NORTH, pathButton, 5, SpringLayout.SOUTH, lPath);

    s.putConstraint(SpringLayout.EAST, pathField, -5, SpringLayout.WEST, pathButton);
    s.putConstraint(SpringLayout.SOUTH, pathField, 0, SpringLayout.SOUTH, pathButton);
    s.putConstraint(SpringLayout.WEST, pathField, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.EAST, mapStatus, 0, SpringLayout.EAST, pathButton);
    s.putConstraint(SpringLayout.NORTH, mapStatus, 5, SpringLayout.SOUTH, pathField);
    s.putConstraint(SpringLayout.WEST, mapStatus, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.NORTH, lMap, 10, SpringLayout.SOUTH, mapStatus);
    s.putConstraint(SpringLayout.WEST, lMap, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.NORTH, mapBox, 0, SpringLayout.SOUTH, lMap);
    s.putConstraint(SpringLayout.EAST, mapBox, -20, SpringLayout.EAST, mainPanel);
    s.putConstraint(SpringLayout.WEST, mapBox, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.EAST, cancel, 0, SpringLayout.EAST, lMessage);
    s.putConstraint(SpringLayout.NORTH, cancel, 5, SpringLayout.SOUTH, mapBox);

    s.putConstraint(SpringLayout.EAST, load, -5, SpringLayout.WEST, cancel);
    s.putConstraint(SpringLayout.NORTH, load, 0, SpringLayout.NORTH, cancel);

    s.putConstraint(SpringLayout.EAST, lErrors, -5, SpringLayout.WEST, load);
    s.putConstraint(SpringLayout.SOUTH, lErrors, 0, SpringLayout.SOUTH, cancel);
    s.putConstraint(SpringLayout.WEST, lErrors, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.SOUTH, mainPanel, 20, SpringLayout.SOUTH, load);
    s.putConstraint(SpringLayout.EAST, mainPanel, 20, SpringLayout.EAST, lMessage);

    mainPanel.add(lMessage);
    mainPanel.add(lPath);
    mainPanel.add(pathButton);
    mainPanel.add(pathField);
    mainPanel.add(mapStatus);
    mainPanel.add(lMap);
    mainPanel.add(mapBox);
    mainPanel.add(load);
    mainPanel.add(cancel);
    mainPanel.add(lErrors);

    setTitle("Import from File");
    setContentPane(mainPanel);
    pack();
  }

  /**
   * <p>Show the instance of {@link LoadCustomDataDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link LoadCustomDataDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link LoadCustomDataDialog}
   */
  public static void showLoadCustomDataDialog(MainGUI parent)
  {
    if (parent.getMapNavigator().getDisplayMaps().size() > 0)
    {
      if (instances.get(parent) == null)
        instances.put(parent, new LoadCustomDataDialog(parent));

      LoadCustomDataDialog instance = instances.get(parent);

      instance.setupComponents();

      Point center = parent.getLocation();
      center.x += parent.getWidth() / 2;
      center.y += parent.getHeight() / 2;
      center.x -= instance.getWidth() / 2;
      center.y -= instance.getHeight() / 2;

      if (center.x < 0)
        center.x = 0;
      if (center.y < 0)
        center.y = 0;

      instance.setLocation(center);
      instance.setVisible(true);
    }
    else
    {
      JOptionPane.showMessageDialog(null,
          "There are no loaded maps to add annotation to.",
          "No Loaded Maps",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * <p>Create reference to the {@link DisplayMap}s in the {@link MainGUI}s
   * {@link MapNavigator}.  If the {@link DisplayMap}s have changed the {@link File}
   * is re-validated</p>
   *
   */
  public void setupComponents()
  {
    if (displayMaps == null || !displayMaps.equals(mainGUI.getMapNavigator().getDisplayMaps()))
    {
      // Create a new vector so changes in maps can be checked
      displayMaps = new Vector<DisplayMap>();
      for (DisplayMap dm : mainGUI.getMapNavigator().getDisplayMaps())
        displayMaps.addElement(dm);

      if (file != null)
      {
        fileV = new FileValidator();
        fileV.start();
      }
    }

  }

  /**
   * <p>Removes the instance of the {@link LoadCustomDataDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link LoadCustomDataDialog}
   */
  public static void closeLoadCustomDataDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Shows the {@link JFileChooser} and handles file selection.</p>
   */
  public void showFileChooser()
  {
    if (fileChooser.showOpenDialog(mainGUI) == JFileChooser.APPROVE_OPTION)
    {
      file = fileChooser.getSelectedFile(); // Create and store file
      pathField.setText(file.getPath());

      // test labels
      if (file != null)
      {
        //Determine file type
        if (file.getName().toUpperCase().endsWith(".GFF") ||
            file.getName().toUpperCase().endsWith(".GFF3"))
          fileType = "GFF3";
        else if (file.getName().toUpperCase().endsWith(".BED"))
          fileType = "BED";
        else if (file.getName().toUpperCase().endsWith(".SAM") ||
            file.getName().toUpperCase().endsWith(".BAN"))
          fileType = "SAM/BAM";
        else if (file.getName().toUpperCase().endsWith(".VCF"))
          fileType = "VCF";
        else if (file.getName().toUpperCase().endsWith(".TXT"))
        {
          for (String type : supportedTypes)
          {
            if (fileChooser.getFileFilter().getDescription().startsWith(type))
              fileType = type;
          }

        }
      }

      fileV = new FileValidator();
      fileV.start();
    }

    // test labels
    if (file != null)
      clearLabel(lErrors);
  }

  /**
   * <p>Enable or diable the map selection {@link JComboBox} and {@link JLabel}</p>
   * @param b
   *    true to enable the {@link JComboBox} false disales it
   */
  public void mapSelectEnabled(boolean b)
  {
    lMap.setEnabled(b);
    mapBox.setEnabled(b);
  }

  /**
   * <p>Clears the {@link ImageIcon} and text of a {@link JLabel}</p>
   * @param label
   *    the {@link JLabel} that is being cleared
   */
  public void clearLabel(JLabel label)
  {
    setLabel(label, null, " ");
  }

  /**
   * <p>Set the {@link ImageIcon} and text of a {@link JLabel}</p>
   * @param label
   *    the {@link JLabel} that is being changed
   * @param icon
   *    the {@link ImageIcon} that is being set
   * @param text
   *    the text that is set on the {@link JLabel}
   */
  public void setLabel(JLabel label, ImageIcon icon, String text)
  {
    label.setIcon(icon);
    label.setText(text);
  }

  /**
   * <p>Takes a {@link DisplayMap} and returns a {@link String} with it's info</p>
   * @param map
   *    The {@link DisplayMap} that is being converted to a string
   * @return
   *    a {@link String} containing the map species, type, and version
   */
  public String getMapString(DisplayMap map)
  {
    try
    {
      return map.getMap().getSpecies() + " - " +
             map.getMap().getTypeString() + ", " +
             map.getMap().getVersion();
    }
    catch (NullPointerException npe)
    {
      return null;
    }
  }

  /**
   * <p>Populates the {@link JComboBox} with the {@link DisplayMap} information</p>
   * @param maps
   *    a {@link Vector} of {@link DisplayMap}s whose information will be displayed
   *    in the {@link JComboBox}
   */
  public void populateMapBox(Vector<DisplayMap> maps)
  {
    mapBox.removeItemListener(this);

    mapBox.removeAllItems();

    mapBox.addItem(" ");

    for (DisplayMap dm : maps)
      mapBox.addItem(getMapString(dm));

    mapBox.addItemListener(this);
  }

  public void hyperlinkUpdate(HyperlinkEvent e)
  {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
      if (e.getDescription().equals(TUTORIAL_TEXT))
      {
        Tutorial.showTutorial(mainGUI);
        Tutorial.updatePage(e.getDescription());
      }
      else if (e.getDescription() != null && !Util.openURL(e.getDescription()))
        JOptionPane.showMessageDialog(mainGUI, "Unable to launch your default web browser.\n" +
            "Please enter the following URL into your web browser\n" +
            "for more information about gff files:\n\n" +
            e.getDescription(),
            "Unable to Launch Default Browser", JOptionPane.INFORMATION_MESSAGE);
  }

  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand() == "ChooseFile")
      showFileChooser();
    else if (ae.getActionCommand() == "Cancel")
      this.setVisible(false);
    else if (ae.getActionCommand() == "Load")
    {
      this.setVisible(false);
      mainGUI.loadCustomAnnotation(file, fileType, mapToAdd.getMap().getName(), this);
    }

  }

  public void itemStateChanged(ItemEvent ie)
  {
    // Do nothing for events other than selection events
    if (ie.getStateChange() != ItemEvent.SELECTED)
      return;

    // Enable load button when maps are selected
    load.setEnabled(mapBox.getSelectedIndex() != 0);

    if (mapBox.getSelectedIndex() == 0) // No map selected
      mapToAdd = null;
    else
    {

      // Determine which map has been selected
      String[] mapData = ((String)mapBox.getSelectedItem()).split("[\\s,][\\s-\\s]");

      String species = mapData[0];
      String mapType = mapData[1].substring(1);
      String mapVersion = mapData[2];

      for (DisplayMap dm : displayMaps)
      {
        if (dm.getMap().getSpecies().equals(species) &&
            dm.getMap().getTypeString().equals(mapType) &&
            dm.getMap().getVersion().toString().equals(mapVersion))
          mapToAdd = dm;
      }
    }

    if (mapToAdd == null)
      setLabel(lErrors, errorIcon, NO_MAP_SELECTED_ERROR);
    else
      clearLabel(lErrors);
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.CustomAnnotationLoader#customAnnotationLoadCompleted(boolean, int, java.lang.String)
   */
  public void customAnnotationLoadCompleted(boolean successful, int messageType, String message)
  {
    if (successful)
    {
      if (messageType == MainGUI.GFF3_FEATURE_ERROR)
      {
        JOptionPane.showMessageDialog(null,
            message,
            "GFF Feature Error",
            JOptionPane.WARNING_MESSAGE);
      }
      else
        mainGUI.setEnabled(true);
    }
    else
    {
      this.setVisible(true);

      if (messageType == MainGUI.OUT_OF_MEMORY_ERROR)
      {
        String text = "VCMap has run out of memory while trying to load the custom Annotation.\n";
        text += "The amount of Annotation you are trying to load may be causing this problem.\n\n";
        text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
        String log = "LoadCustomDataDialog: Error loading custom annotation: " + message;
        ErrorReporter.handleMajorError(mainGUI, text, log);
      }
      else if (messageType == MainGUI.SQL_ERROR)
      {
        String text = "There was a problem while trying to communicate with the VCMap\n";
        text += "database.  Please try again later.\n\n";
        text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
        String log = "AnnotationDialog: Error loading annotation types: " + message;
        ErrorReporter.handleMajorError(mainGUI, text, log);
      }
      else if (messageType == MainGUI.FILE_READ_ERROR)
      {
        String text = "VCMap has encountered an error while reading the data file.\n\n";
        text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
        ErrorReporter.handleMajorError(mainGUI, text, message);
      }
      else if (messageType == FileParser.NO_DATA_LOADED_ERROR)
      {
        JOptionPane.showMessageDialog(null,
            message,
            "No Loaded Annotation",
            JOptionPane.ERROR_MESSAGE);
      }
      else if (messageType == FileParser.FILE_FORMAT_ERROR)
      {
        JOptionPane.showMessageDialog(null,
            message,
            "Incorrect File Format",
            JOptionPane.ERROR_MESSAGE);
      }
      else if (messageType == FileParser.MISSING_INFO_ERROR)
      {
        JOptionPane.showMessageDialog(null,
            message,
            "File Missing Information",
            JOptionPane.ERROR_MESSAGE);
      }
      else if (messageType == FileParser.INVALID_FORMAT_ERROR)
      {
        JOptionPane.showMessageDialog(null,
            message,
            "Invalid File Format",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /**
   * <p>Creates a seperate {@link Thread} that checks if there are any maps
   * matching the Meta-Data in the gff file</p>
   * @author cgoodman
   *
   */
  private class FileValidator
    extends Thread
  {
    private String species;
    private String genome_build;
    private boolean directiveEnd;
    private Vector<DisplayMap> maps;

    public FileValidator()
    {
      species = null;
      genome_build = null;
      directiveEnd = false;
      maps = new Vector<DisplayMap>();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run()
    {
      if (fileType.equals("GFF"))
        validateGFF();

      // Add to JComboBox
      if (maps.size() == 1)
      {
        mapSelectEnabled(false);
        setLabel(mapStatus, checkIcon, MATCHING_MAP_FOUND);
        LoadCustomDataDialog.this.populateMapBox(displayMaps);
        mapToAdd = maps.get(0);
        LoadCustomDataDialog.this.mapBox.setSelectedIndex(1);
        load.setEnabled(true);
        clearLabel(lErrors);
      }
      else
      {
        mapSelectEnabled(true);
        setLabel(mapStatus, warningIcon, NO_MAPS_MATCHED);
        LoadCustomDataDialog.this.populateMapBox(displayMaps);
        mapToAdd = null;
        load.setEnabled(false);
        setLabel(lErrors, errorIcon, NO_MAP_SELECTED_ERROR);
      }

      LoadCustomDataDialog.this.toFront();

    }

    /**
     * <p>Attempts to determine if the GFF file contains a species name and genome build</p>
     */
    private void validateGFF()
    {
      Scanner scanner;

      try
      {
        scanner = new Scanner(file);

        while ( scanner.hasNextLine() && !directiveEnd )
        {
          String line = scanner.nextLine();

          if (line.startsWith("##FASTA"))
          {
            break; // End of directiives
          }
          else if (line.startsWith("##genome-build"))
          {
            genome_build = line.replaceAll("##genome-build", ""); // Remove directive

            // Remove extra whitespace
            genome_build = genome_build.trim();
          }
          else if (line.startsWith("##species"))
          {
            species = line.replaceAll("##species", ""); // Remove directive

            // Remove extra whitespace
            species = species.trim();
          }
          else if (line.split("[\t]").length > 7)
          {
            directiveEnd = true;
          }
        }

        // Check display maps
        for (DisplayMap dm : displayMaps)
        {
          boolean bSpecies = false;
          boolean bGenomeBuild = false;

          // check species
          if (species != null && species.equalsIgnoreCase(dm.getMap().getSpecies()))
            bSpecies = true;

          // check genome build
          if (genome_build != null && genome_build.equals(dm.getMap().getVersion()))
            bGenomeBuild = true;

          if (bSpecies && bGenomeBuild)
            maps.addElement(dm);
        }

      }
      catch (FileNotFoundException fnfe)
      {
        logger.debug("The file " + file.getName() + " was not found");
      }
    }
  }
}
