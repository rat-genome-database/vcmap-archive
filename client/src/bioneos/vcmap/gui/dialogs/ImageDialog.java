package bioneos.vcmap.gui.dialogs;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.components.ImageFileFilter;
import bioneos.vcmap.gui.components.JHistoryTextField;
import bioneos.vcmap.gui.components.SaveJFileChooser;
import bioneos.vcmap.model.DisplayMap;

/**
 * <p>This Dialog is used to save a publication quality image of the current
 * data that is displayed by the VCMap {@link MainGUI}.</p>
 * <p>Created on: June 16, 2010</p>
 *
 * @author cgoodman
 */
public class ImageDialog extends VCMDialog implements ActionListener, KeyListener,
    ComponentListener, MouseListener, MouseMotionListener, ChangeListener, FocusListener
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  private static HashMap<MainGUI, ImageDialog> instances = new HashMap<MainGUI, ImageDialog>();

  private MainGUI mainGUI;

  private JLabel lHistoryField = new JLabel("Title:");
  private static JHistoryTextField historyField;
  private SpringLayout s;

  // Components related to the preview image
  private JLabel imageTitle;
  private JLabel lPreview = new JLabel("Preview:");
  private BufferedImage mapHeader;
  private BufferedImage fullMapNavigator;
  private BufferedImage croppedMapNavigator;
  private BufferedImage imagePreview;
  private ImageIcon icon;
  private BufferedImage logoImage;
  private JLabel previewLabel;

  // Image Manipulation Properties (watermark size, crop percentage, DPI info, image size)
  private JLabel watermarkSizeLabel = new JLabel("Watermark Size:");
  private JSlider watermarkSize;
  private JLabel cropSliderLabel = new JLabel("Crop Percentage:");
  private JSlider cropSlider;
  private JLabel sizeEntryLabel = new JLabel("Width (in):");
  private JTextField sizeEntry;
  private JLabel DPISelectLabel = new JLabel("Output Resolution:");
  private JComboBox DPISelect;
  private JLabel outputDimensionLabel = new JLabel("Output Dimensions:");
  private JLabel outputDimensionPixels;
  private JLabel displayFeaturesLabel = new JLabel("Display:");
  private JCheckBox displayOnlySelected = new JCheckBox("Only Selected Features");
  private JCheckBox displayFeatureHighlight = new JCheckBox("<html>Feature Selection<br>Highlighting</html>");
  private JCheckBox displayIntervalHighlight = new JCheckBox("<html>Interval Selection<br>Highlighting</html>");
  private boolean resizingWatermark;
  private boolean DPIChanging;
  private boolean widthChanging;
  private boolean cropChanging;
  private boolean annotationsRemoved = false;
  private boolean originallyRemoved = false;
  private boolean drawnSinceRemoved = true;

  // JRadioButton group
  private JLabel lWatermark = new JLabel("Watermark Location:");
  private JRadioButton northWest = new JRadioButton("Top Left");
  private JRadioButton northEast = new JRadioButton("Top Right");
  private JRadioButton southWest = new JRadioButton("Bottom Left");
  private JRadioButton southEast = new JRadioButton("Bottom Right");
  private JRadioButton custom = new JRadioButton("Custom");
  private ButtonGroup watermark;

  // Components related with choosing the file path
  private JLabel lPath = new JLabel("Path:");
  private JTextField path;
  private JButton choosePath = new JButton("Browse...");
  private SaveJFileChooser fileChooser;
  private File file;
  private String extension;

  private JButton saveButton = new JButton("Save");
  private JButton cancelButton = new JButton("Cancel");

  private JPanel mainPanel;
  private MouseEvent mousePressEvent;
  private MouseEvent mouseMovedEvent;
  private boolean mousePressedWatermark;
  private boolean mousePressedMap;
  private Rectangle watermarkRect;
  private Rectangle scaledWatermarkRect;
  private Rectangle mapRect;
  private Rectangle scaledMapRect;
  private double scalingRatio;
  private int watermarkWidth = 0;
  private double watermarkRatio;

  // image output settings
  private double DPI; // DPI settings value
  private double outputWidth; // output width from the JTextField
  private double cropValue; // percentage to crop the image
  private int cropHeight; // height to crop the image at
  private double DPIScale; // Scale of the DPI to screen DPI
  private double prevDPIScale;
  private int actualOutputWidth; // width to be used to obtain the image
  private int previousWidth; // first backup of width (used to calculate the scale for watermark)
  private int backupWidth; // second backup of width (used if a backlog is needed)
  private double backupDPI;

  private static final int COMPONENT_SIZE = 225;
  private int MINIMUM_HEIGHT = 500;

  private static final int BORDER_SIZE = 1;
  private static final int LOGO_BORDER = 30;
  private int LOGO_TEXT_HEIGHT = 16; // text size + 1 pixel border

  public static final Color OUTER_BORDER = Color.BLACK;
  public static final Color MIDDLE_BORDER = new Color(153, 153, 153);
  public static final Color HEADER_COLOR = new Color(231, 231, 231);

  public static final String[] EXTENSIONS =
  { "jpeg", "bmp", "gif", "png" };

  private static final double INCH_2_CM = 2.54;

  private static final int MIN_WATERMARK_WIDTH = 75;

  private static final int SCREEN_DPI = Toolkit.getDefaultToolkit().getScreenResolution();

  private static final int MAX_SCREEN_WIDTH = Toolkit.getDefaultToolkit().getScreenSize().width;

  private static final int MAX_SCREEN_HEIGHT = Toolkit.getDefaultToolkit().getScreenSize().height;

  // The DPI Options for the combobox
  private static final String[] DPI_OPTIONS =
  { "Screen (72)", "Low (150)", "High (300)", "Very High (600)" };

  // The DPI Setting Values
  private static final double[] DPI_OPTION_VALUES =
  { 72.0, 150.0, 300.0, 600.0 };

  // 190MB in bytes, using less than 256 to be safe
  private static final int MAX_IMAGE_SIZE = 199229440;

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  /**
   * <p>
   * Constructor
   * </p>
   */
  private ImageDialog(MainGUI parent)
  {
    super(parent, false);
    this.mainGUI = parent;

    historyField = new JHistoryTextField();
    historyField.addKeyListener(this);

    // Build logo image
    logoImage = null;
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/vcmap-logo-watermark.png");
      if (imageUrl != null)
      {
        logoImage = ImageIO.read(imageUrl);
      }
    }
    catch (IOException e)
    {
      logger.warn("Error retrieving the VCMap Logo: " + e);
    }

    // JLabel containing preview image
    previewLabel = new JLabel();
    Font labelFont = new Font(previewLabel.getFont().getName(), Font.PLAIN, previewLabel.getFont()
        .getSize());
    previewLabel.addMouseListener(this);
    previewLabel.addMouseMotionListener(this);

    // Setup the JComboBox
    DPISelect = new JComboBox(DPI_OPTIONS);
    DPISelect.addActionListener(this);

    // Output settings
    file = new File(System.getProperty("user.home") + System.getProperty("file.separator")
        + "untitled.png");
    extension = "png";
    DPI = DPI_OPTION_VALUES[0];
    backupDPI = DPI_OPTION_VALUES[0];
    outputWidth = 12;
    previousWidth = (int) (outputWidth * DPI);
    backupWidth = (int) (outputWidth * DPI);
    cropValue = 0.0;
    DPIScale = 1.0;
    prevDPIScale = 1.0;
    watermarkWidth = logoImage.getWidth();
    cropHeight = 0;
    updateWatermarkRatio();

    // Set up JFileChooser
    fileChooser = new SaveJFileChooser(file);
    fileChooser.setApproveButtonText("Select");
    fileChooser.setDialogTitle("Save File As...");
    fileChooser.setAcceptAllFileFilterUsed(false);
    for (String str : EXTENSIONS)
      fileChooser.addChoosableFileFilter(new ImageFileFilter(str));

    // Image change properties
    DPISelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, DPISelect.getPreferredSize().height));
    watermarkSize = new JSlider(50, 100, 100);
    cropSlider = new JSlider(0, 75, 0);
    sizeEntry = new JTextField();

    // Setup the sliders
    watermarkSizeLabel.setToolTipText("Size of the watermark");
    watermarkSize.addChangeListener(this);
    cropSliderLabel.setToolTipText("The Percent of the image to crop off");
    cropSlider.setPaintTicks(true);
    cropSlider.setPaintLabels(true);
    cropSlider.setMajorTickSpacing((cropSlider.getMaximum() - cropSlider.getMinimum()) / 5);
    cropSlider.setMinorTickSpacing(5);
    cropSlider.setFont(labelFont);
    cropSlider.addChangeListener(this);

    // Setup the size entry
    sizeEntry.setMaximumSize(new Dimension(Integer.MAX_VALUE, sizeEntry.getPreferredSize().height));
    sizeEntry.addActionListener(this);
    sizeEntry.addFocusListener(this);
    sizeEntry.setText(Double.toString(outputWidth));

    outputDimensionPixels = new JLabel();
    outputDimensionPixels.setFont(labelFont);

    // JRadioButton group properties
    watermark = new ButtonGroup();
    watermark.add(northWest);
    watermark.add(northEast);
    watermark.add(southWest);
    watermark.add(southEast);
    watermark.add(custom);
    northWest.setToolTipText("Place Watermark in the Top Left");
    northEast.setToolTipText("Place Watermark in the Top Right");
    southWest.setToolTipText("Place Watermark in the Bottom Left");
    southEast.setToolTipText("Place Watermark in the Bottom Right");
    custom.setToolTipText("Click and drag the logo image to move it to a custom location");
    northWest.setMaximumSize(DPISelect.getMaximumSize());
    northWest.setActionCommand("MoveLogo");
    northEast.setActionCommand("MoveLogo");
    southWest.setActionCommand("MoveLogo");
    southEast.setActionCommand("MoveLogo");
    custom.setActionCommand("MoveLogo");
    northWest.addActionListener(this);
    northEast.addActionListener(this);
    southWest.addActionListener(this);
    southEast.addActionListener(this);
    custom.addActionListener(this);

    // Setup JTextField containing path information
    path = new JTextField();
    path.setEditable(false);
    path.addMouseListener(new MouseAdapter()
      {
        public void mouseReleased(MouseEvent me)
        {
          if (SwingUtilities.isLeftMouseButton(me))
          {
            showFileChooser();
          }
        }
      });
    path.setToolTipText("Click to change the file path or name");

    choosePath.setActionCommand("SaveFile");
    choosePath.addActionListener(this);

    saveButton.addActionListener(this);
    cancelButton.addActionListener(this);

    if (mainGUI.getMapNavigator().isOtherAnnotationsRemoved())
    {
      annotationsRemoved = true;
      originallyRemoved = true;
    }
    else
    {
      annotationsRemoved = false;
      originallyRemoved = false;
    }

    displayOnlySelected.setActionCommand("displayOnlySelectedFeatures");
    displayFeatureHighlight.setActionCommand("displayFeatureSelectHighlight");
    displayIntervalHighlight.setActionCommand("displayIntervalSelectHighlight");
    displayOnlySelected.setToolTipText("Only display features that are selected in the output image");
    displayFeatureHighlight.setToolTipText("Display the blue selection highlights from around the features");
    displayIntervalHighlight.setToolTipText("Display the green selection highlights for intervals on and off backbone");
    displayOnlySelected.addActionListener(this);
    displayFeatureHighlight.addActionListener(this);
    displayIntervalHighlight.addActionListener(this);
    displayOnlySelected.setSelected(annotationsRemoved);
    displayIntervalHighlight.setSelected(true);
    displayFeatureHighlight.setSelected(true);

    if (mainGUI.getMapNavigator().getSelection().getIntervals().size() == 0)
    {
      displayOnlySelected.setEnabled(false);
      displayFeatureHighlight.setEnabled(false);
      displayIntervalHighlight.setEnabled(false);
    }

    JPanel optionsPanel = new JPanel();
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
    optionsPanel.setMaximumSize(new Dimension(200, Integer.MAX_VALUE));
    optionsPanel.add(lWatermark);
    optionsPanel.add(northWest);
    optionsPanel.add(northEast);
    optionsPanel.add(southWest);
    optionsPanel.add(southEast);
    optionsPanel.add(custom);
    optionsPanel.add(watermarkSizeLabel);
    watermarkSize.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    optionsPanel.add(watermarkSize);
    optionsPanel.add(cropSliderLabel);
    cropSlider.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    optionsPanel.add(cropSlider);
    optionsPanel.add(sizeEntryLabel);
    sizeEntry.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    optionsPanel.add(sizeEntry);
    optionsPanel.add(DPISelectLabel);
    DPISelect.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    optionsPanel.add(DPISelect);
    optionsPanel.add(Box.createRigidArea(new Dimension(optionsPanel.getWidth(), 20)));
    optionsPanel.add(outputDimensionLabel);
    optionsPanel.add(outputDimensionPixels);
    optionsPanel.add(Box.createRigidArea(new Dimension(optionsPanel.getWidth(), 20)));
    optionsPanel.add(displayFeaturesLabel);
    optionsPanel.add(displayOnlySelected);
    optionsPanel.add(displayIntervalHighlight);
    optionsPanel.add(displayFeatureHighlight);
    optionsPanel.add(Box.createVerticalGlue());

    JPanel previewPane = new JPanel();
    previewPane.setLayout(new BoxLayout(previewPane, BoxLayout.Y_AXIS));
    previewPane.add(Box.createVerticalGlue());
    previewPane.add(previewLabel);
    previewPane.add(Box.createVerticalGlue());

    JPanel previewPanel = new JPanel();
    previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.X_AXIS));
    previewPanel.add(Box.createHorizontalGlue());
    previewPanel.add(previewPane);
    previewPanel.add(Box.createHorizontalGlue());
    optionsPanel.add(Box.createRigidArea(new Dimension(50, previewPanel.getHeight())));

    s = new SpringLayout();
    mainPanel = new JPanel(s);

    // Layout components
    s.putConstraint(SpringLayout.NORTH, lHistoryField, 20, SpringLayout.NORTH, mainPanel);
    s.putConstraint(SpringLayout.WEST, lHistoryField, 20, SpringLayout.WEST, mainPanel);
    s.putConstraint(SpringLayout.EAST, historyField, -20, SpringLayout.EAST, mainPanel);
    s.putConstraint(SpringLayout.NORTH, historyField, 5, SpringLayout.SOUTH, lHistoryField);
    s.putConstraint(SpringLayout.WEST, historyField, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.EAST, lPreview, -20, SpringLayout.EAST, mainPanel);
    s.putConstraint(SpringLayout.NORTH, lPreview, 10, SpringLayout.SOUTH, historyField);
    s.putConstraint(SpringLayout.WEST, lPreview, 20, SpringLayout.WEST, mainPanel);
    s.putConstraint(SpringLayout.EAST, optionsPanel, -20, SpringLayout.EAST, mainPanel);
    s.putConstraint(SpringLayout.SOUTH, optionsPanel, 0, SpringLayout.SOUTH, previewPanel);
    s.putConstraint(SpringLayout.NORTH, optionsPanel, 5, SpringLayout.SOUTH, lPreview);
    s.putConstraint(SpringLayout.WEST, optionsPanel, -optionsPanel.getMaximumSize().width, SpringLayout.EAST, optionsPanel);
    s.putConstraint(SpringLayout.EAST, previewPanel, 0, SpringLayout.WEST, optionsPanel);
    s.putConstraint(SpringLayout.NORTH, previewPanel, 5, SpringLayout.SOUTH, lPreview);
    s.putConstraint(SpringLayout.WEST, previewPanel, 20, SpringLayout.WEST, mainPanel);

    s.putConstraint(SpringLayout.NORTH, lPath, 10, SpringLayout.SOUTH, previewPanel);
    s.putConstraint(SpringLayout.WEST, lPath, 20, SpringLayout.WEST, mainPanel);
    s.putConstraint(SpringLayout.NORTH, path, 5, SpringLayout.SOUTH, lPath);
    s.putConstraint(SpringLayout.WEST, path, 20, SpringLayout.WEST, mainPanel);
    s.putConstraint(SpringLayout.EAST, path, -5, SpringLayout.WEST, choosePath);
    s.putConstraint(SpringLayout.EAST, choosePath, 0, SpringLayout.EAST, historyField);
    s.putConstraint(SpringLayout.SOUTH, choosePath, 0, SpringLayout.SOUTH, path);

    s.putConstraint(SpringLayout.EAST, saveButton, 0, SpringLayout.EAST, historyField);
    s.putConstraint(SpringLayout.NORTH, saveButton, 10, SpringLayout.SOUTH, path);
    s.putConstraint(SpringLayout.EAST, cancelButton, -10, SpringLayout.WEST, saveButton);
    s.putConstraint(SpringLayout.NORTH, cancelButton, 10, SpringLayout.SOUTH, path);

    s.putConstraint(SpringLayout.SOUTH, mainPanel, 20, SpringLayout.SOUTH, cancelButton);

    Dimension max = historyField.getMaximumSize();
    Dimension pref = historyField.getPreferredSize();
    max.height = pref.height;
    historyField.setMaximumSize(max);
    max = path.getMaximumSize();
    pref = path.getPreferredSize();
    max.height = pref.height;
    path.setMaximumSize(max);
    mainPanel.add(lHistoryField);
    mainPanel.add(historyField);
    mainPanel.add(lPreview);
    mainPanel.add(previewPanel);
    mainPanel.add(optionsPanel);
    mainPanel.add(lPath);
    mainPanel.add(path);
    mainPanel.add(choosePath);
    mainPanel.add(saveButton);
    mainPanel.add(cancelButton);

    MINIMUM_HEIGHT = optionsPanel.getPreferredSize().height + COMPONENT_SIZE;

    southEast.setSelected(true);
    setTitle("Export Image");
    this.add(mainPanel);
    this.setSize(900, 760);
    this.addComponentListener(this);
  }

  /**
   * <p>
   * Calculates the DPIScale of the current DPI setting
   * </p>
   */
  private void calcDPI()
  {
    backupDPI = prevDPIScale;
    prevDPIScale = DPIScale;
    DPIScale = DPI / SCREEN_DPI;
  }

  /**
   * <p>
   * Show the instance of {@link ImageDialog} already created for a specific {@link MainGUI} or
   * creates a new instance of {@link ImageDialog} if an instance does not exist for the
   * {@link MainGUI}.
   * </p>
   *
   * @param parent
   *          {@link MainGUI} that is the parent of the {@link ImageDialog}
   */
  public static void showSaveDialog(MainGUI parent)
  {
    if (parent.getMapNavigator().getDisplayMaps().size() > 0)
    {
      List<String> prevHistory = null;
      if (instances.get(parent) != null)
      {
        instances.remove(parent);
        prevHistory = historyField.getHistory(); // used to preserve the history textfield
      }
      if (instances.get(parent) == null)
      {
        instances.put(parent, new ImageDialog(parent));
        if(prevHistory!= null)
          historyField.setHistory(prevHistory); // used to preserver the history textfield
      }

      ImageDialog instance = instances.get(parent);

      instance.setupComponents();

      Point center = parent.getLocation();
      center.x += parent.getWidth() / 2;
      center.y += parent.getHeight() / 2;
      center.x -= instance.getWidth() / 2;
      center.y -= instance.getHeight() / 2;

      if (center.x < 0) center.x = 0;
      if (center.y < 0) center.y = 0;

      instance.setLocation(center);
      instance.setVisible(true);
    }
    else
    {
      JOptionPane.showMessageDialog(null, "There are no loaded maps to export as an image.",
          "No Loaded Maps", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * <p>
   * Removes the instance of the {@link ImageDialog} for the {@link MainGUI}
   * </p>
   *
   * @param parent
   *          {@link MainGUI} that is the parent of the {@link ImageDialog}
   */
  public static void closeSaveDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>
   * Updates the maps combobox to display the current maps in memory, clear any previous results that
   * were displayed and clear any previous data entered into the text fields.
   * </p>
   *
   */
  public void setupComponents()
  {
    logger.debug("Updating save map dialog.");

    historyField.setPopupMenuEnabled(false);
    historyField.setText(getTitleString());
    historyField.setPopupMenuEnabled(true);

    drawImage();

    path.setText(file.getPath());
    this.requestFocus();
    this.repaint();
  }

  /**
   * <p>
   * Uses the information gathered in {@link drawMap} method and the cropSlider itself to calculate
   * and resize the mapNavigator image to use the percentage that it is supposed to be cropped
   * </p>
   */
  private void cropImage()
  {
    // get initial dimensions
    int difference = cropHeight;
    int width = fullMapNavigator.getWidth();
    int origHeight = fullMapNavigator.getHeight();
    int height = (int) ((double) origHeight * ((100.0 - cropValue) / 100.0));
    if (croppedMapNavigator.getHeight() == origHeight)
      difference = 0;
    else if (difference > origHeight - height) difference = origHeight - height;

    // create the temporary buffered image
    croppedMapNavigator = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = croppedMapNavigator.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(fullMapNavigator, 0, 0, width, height, 0, difference, width, height + difference,
        null);
    g.dispose();

    // calculate the current image size
    height += mapHeader.getHeight();
    height += imageTitle.getHeight();

    // correction to add in the size of the border
    height += 2 * BORDER_SIZE;
    width += 2 * BORDER_SIZE;
    double aspectRatio = (double) width / (double) height;
    outputDimensionPixels.setText(String.format("%d px x %d px", width, height));
    if ((int) (width * (SCREEN_DPI / DPI)) > this.getWidth() - 250)
    {
      width = (int) ((this.getWidth() - 250) * (DPI / SCREEN_DPI));
      height = (int) ((double) width / aspectRatio);
    }
    previewLabel.setMaximumSize(new Dimension((int) (width * (SCREEN_DPI / DPI)),
        (int) (height * (SCREEN_DPI / DPI))));
    cropChanging = false;
  }

  /**
   * Gets the images for the imagePreview
   */
  public void getImages()
  {
    calcDPI();
    if (prevDPIScale != DPIScale || fullMapNavigator == null || mapHeader == null || DPIChanging
        || widthChanging || cropChanging || !drawnSinceRemoved)
    {
      try
      {
        calcWidth();

        // attempt to free up memory before using more
        if (cropChanging)
        {
          croppedMapNavigator = null;
        }
        else
        {
          if (fullMapNavigator != null) fullMapNavigator = null;
          if (mapHeader != null) mapHeader = null;
        }
        mapHeader = mainGUI.getHeaderImage(actualOutputWidth);
        fullMapNavigator = mainGUI.getNavigatorImage(actualOutputWidth);
        if (croppedMapNavigator == null) croppedMapNavigator = fullMapNavigator;
      }
      catch (OutOfMemoryError e)
      {
        rollbackSettings();
      }
    }
  }

  /**
   * Calculates the width (in pixels) of the final output image The last part is the correction for
   * having the border around the image
   */
  private void calcWidth()
  {
    backupWidth = previousWidth;
    previousWidth = actualOutputWidth;
    actualOutputWidth = (int) (DPI * outputWidth) - 2 * BORDER_SIZE;
  }

  /**
   * <p>
   * Determines the title of the map by checking the names of the maps that are currently opened.
   * </p>
   *
   * @return A string containing the proper Image Title
   */
  private String getTitleString()
  {
    String title = "Comparative Analysis of Species - ";

    Vector<String> speciesList = new Vector<String>();

    Vector<DisplayMap> maps = mainGUI.getMapNavigator().getDisplayMaps();

    if (maps.size() == 1) return title + maps.firstElement().getMap().getSpecies();

    // Get all visible map titles
    for (DisplayMap map : maps)
    {
      if (map.isVisible())
      {
        speciesList.addElement(map.getMap().getSpecies());
      }
    }

    // Append all map names seperated by a comma and put "and" between the last two
    for (String str : speciesList)
    {
      if (str == speciesList.get(speciesList.size() - 2))
        title += str + " and ";
      else if (str != speciesList.lastElement())
        title += str + ", ";
      else
        title += str;
    }

    return title;
  }

  /**
   * <p>
   * Calls the methods to build a new BufferedImage and create a scaled image to fit in the dialog
   * </p>
   */
  private void drawImage()
  {
    buildBufferedImage();
    setPreviewIcon();
    resizeWindow();
  }

  /**
   * <p>
   * Calls the methods to create a new preview icon to be displayed in the {@link ImageDialog}
   * </p>
   */
  private void setPreviewIcon()
  {
    if (this.getHeight() - COMPONENT_SIZE > 0 && this.getWidth() > 0)
    {
      icon = getPreviewIcon();
      previewLabel.setIcon(icon);
      previewLabel.repaint();
    }
  }

  /**
   * <p>
   * Creates an title {@link JLabel} for the image and merges it with the map header
   * {@link BufferedImage} and the map navigator {@link BufferedImage} into one {@link BufferedImage}
   * </p>
   */
  private void buildBufferedImage()
  {
    try
    // try-catch block to avoid memory overloads
    {
      getImages();
      imageTitle = new JLabel(historyField.getText());
      imageTitle.setSize(mapHeader.getWidth(), mapHeader.getHeight() * 2);
      imageTitle.setHorizontalAlignment(SwingConstants.CENTER);
      imageTitle.setVerticalAlignment(SwingConstants.CENTER);
      // Maximze font size
      Font labelFont = imageTitle.getFont();
      String labelText = imageTitle.getText();

      int stringWidth = imageTitle.getFontMetrics(labelFont).stringWidth(labelText);
      int componentWidth = imageTitle.getWidth() - 30;

      // Find out how much the font can grow in width.
      double widthRatio = (double) componentWidth / (double) stringWidth;
      int newFontSize = (int) (labelFont.getSize() * widthRatio);
      int componentHeight = imageTitle.getHeight();

      // Pick a new font size so it will not be larger than the height of label.
      int fontSizeToUse = Math.min(newFontSize, componentHeight - 14);

      imageTitle.setText("<html><p>" + imageTitle.getText() + "</p></html>");

      // Set the label's font size to the newly determined size.
      imageTitle.setFont(new Font(labelFont.getName(), Font.PLAIN, fontSizeToUse));

      // Create buffered image from title
      BufferedImage titleImage = new BufferedImage(imageTitle.getWidth(), imageTitle.getHeight(),
          BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = titleImage.createGraphics();

      // Draw color on background
      g2.setColor(HEADER_COLOR);
      g2.fillRect(0, 0, titleImage.getWidth(), titleImage.getHeight());

      imageTitle.paint(g2);
      g2.dispose();
      drawMap();
      if (!drawnSinceRemoved
          || imagePreview == null
          || imagePreview.getWidth() != (mapHeader.getWidth() + (BORDER_SIZE * 2))
          || imagePreview.getHeight() != (mapHeader.getHeight() + croppedMapNavigator.getHeight()
              + imageTitle.getHeight() + (BORDER_SIZE * 2)))
      {
        imagePreview = new BufferedImage(mapHeader.getWidth() + (BORDER_SIZE * 2), mapHeader
            .getHeight()
            + croppedMapNavigator.getHeight() + imageTitle.getHeight() + (BORDER_SIZE * 2),
            BufferedImage.TYPE_INT_RGB);
        drawnSinceRemoved = true;
      }
      Graphics g2d = imagePreview.createGraphics();

      g2d.drawImage(titleImage, BORDER_SIZE, BORDER_SIZE, titleImage.getWidth(),
          titleImage.getHeight(), OUTER_BORDER, null);
      g2d.drawImage(mapHeader, BORDER_SIZE, titleImage.getHeight() + BORDER_SIZE,
          mapHeader.getWidth(), mapHeader.getHeight(), OUTER_BORDER, null);
      g2d.drawImage(croppedMapNavigator, BORDER_SIZE, mapHeader.getHeight()
          + titleImage.getHeight() + BORDER_SIZE, croppedMapNavigator.getWidth(),
          croppedMapNavigator.getHeight(), OUTER_BORDER, null);

      // Draw Middle Border
      g2d.setColor(MIDDLE_BORDER);
      g2d.fillRect(BORDER_SIZE, mapHeader.getHeight() + titleImage.getHeight(), imagePreview
          .getWidth()
          - (2 * BORDER_SIZE), BORDER_SIZE);

      g2d.dispose();

      drawWatermark();
    }
    catch (OutOfMemoryError e)
    {
      rollbackSettings();
    }
  }

  /*
   * Update the ratio of watermark to image size to ensure that the
   * watermark is neither too big nor too small when the image is output. 
   */
  private void updateWatermarkRatio()
  {
    watermarkRatio = ((outputWidth * DPI) / 4) / logoImage.getWidth();
  }

  /**
   * Rolls back the settings to the previous functioning settings if the desired settings throw an
   * exception.
   */
  private void rollbackSettings()
  {
    if (widthChanging)
    {
      actualOutputWidth = previousWidth;
      previousWidth = backupWidth;
      backupWidth = (int) (12 * DPI_OPTION_VALUES[0]);
      outputWidth = (actualOutputWidth + 2 * BORDER_SIZE) / DPI;
      sizeEntry.setText(Double.toString(outputWidth));
    }
    if (DPIChanging)
    {
      DPIScale = prevDPIScale;
      prevDPIScale = backupDPI;
      backupDPI = DPI_OPTION_VALUES[0];
      DPI = DPIScale * SCREEN_DPI;
      for (int i = 0; i < DPI_OPTION_VALUES.length; i++)
        if (DPI_OPTION_VALUES[i] == DPI) DPISelect.setSelectedIndex(i);
    }
    JOptionPane.showMessageDialog(null, "The selected settings produced an error.\n"
        + "Reverting to last functioning settings.", "Memory Error", JOptionPane.ERROR_MESSAGE);
    drawImage();
  }

  /**
   * <p>
   * Takes the {@link BufferedImage} of the VCMap logo and paints it on top of the main image. The
   * {@link BufferedImage} of the watermark is painted on the corner of the image defined by the
   * {@link JRadioButton}s
   * </p>
   */
  private void drawWatermark()
  {
    // Determine watermark size
    watermarkWidth = (int) Math.round((double) (watermarkSize.getValue() / 100.0) * watermarkRatio
        * (double) logoImage.getWidth());
    int x = LOGO_BORDER;
    int y = LOGO_BORDER + mapHeader.getHeight() + imageTitle.getHeight();
    int width = logoImage.getWidth();
    int height = logoImage.getHeight();
    // scale determination to size the watermark down
    double scale = 1.0;
    if (width > watermarkWidth && watermarkWidth < MIN_WATERMARK_WIDTH)
      scale = (double) MIN_WATERMARK_WIDTH / (double) width;
    else
      scale = (double) watermarkWidth / (double) width;

    String version = "Version " + Integer.toString(VCMap.RELEASE) + "."
        + Integer.toString(VCMap.FEATURE) + "." + Integer.toString(VCMap.BUGFIX);

    // Create JLabel with version
    JLabel versionLabel = new JLabel("<html><p><b>" + version + "</b></p></html>");
    versionLabel.setHorizontalAlignment(SwingConstants.CENTER);
    versionLabel.setVerticalAlignment(SwingConstants.CENTER);

    int vLabelWidth = versionLabel.getPreferredSize().width;
    int vLabelHeight = versionLabel.getPreferredSize().height;
    double vLabelAspectRatio = (double) vLabelWidth / (double) vLabelHeight;

    Graphics2D g2d = imagePreview.createGraphics();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);

    if (mousePressedWatermark) custom.setSelected(true);
    int newWidth = (int) (width * scale);
    int newHeight = (int) (height * scale);
    vLabelWidth = (int) Math.round((double) newWidth * 0.75);
    vLabelHeight = (int) Math.ceil((double) vLabelWidth / vLabelAspectRatio);
    // Maximze font size
    Font labelFont = versionLabel.getFont();

    int stringWidth = versionLabel.getFontMetrics(labelFont).stringWidth(version);
    int componentWidth = vLabelWidth;

    // Find out how much the font can grow in width.
    double widthRatio = (double) componentWidth / (double) stringWidth;
    int newFontSize = (int) (labelFont.getSize() * widthRatio);

    // Pick a new font size so it will not be larger than the height of label.
    versionLabel.setText("<html><p>" + version + "</p></html>");

    // Set the label's font size to the newly determined size.
    versionLabel.setFont(new Font(labelFont.getName(), Font.BOLD, newFontSize));
    int newerWidth = versionLabel.getFontMetrics(versionLabel.getFont()).stringWidth(version);
    int oldLTH = LOGO_TEXT_HEIGHT;
    LOGO_TEXT_HEIGHT = newFontSize + 1;

    versionLabel.setPreferredSize(new Dimension(newerWidth, LOGO_TEXT_HEIGHT));
    versionLabel.setSize(versionLabel.getPreferredSize());

    // Determine where to draw the watermark
    if (southEast.isSelected())
    {
      x = imagePreview.getWidth() - newWidth - LOGO_BORDER;
      y = imagePreview.getHeight() - newHeight - LOGO_TEXT_HEIGHT - LOGO_BORDER;
    }
    else if (southWest.isSelected())
    {
      y = imagePreview.getHeight() - newHeight - LOGO_TEXT_HEIGHT - LOGO_BORDER;
    }
    else if (northEast.isSelected())
    {
      x = imagePreview.getWidth() - newWidth - LOGO_BORDER;
    }
    else if (custom.isSelected())
    {
      x = watermarkRect.x;
      y = watermarkRect.y;

      if (mousePressedWatermark)
      {
        x += (int) ((mouseMovedEvent.getX() - mousePressEvent.getX()) / scalingRatio);
        y += (int) ((mouseMovedEvent.getY() - mousePressEvent.getY()) / scalingRatio);
      }
      if (resizingWatermark)
      {
        int centerX = x + watermarkRect.width / 2;
        int centerY = y + watermarkRect.height / 2;
        x = centerX - newWidth / 2;
        y = centerY - (newHeight + LOGO_TEXT_HEIGHT) / 2;
        resizingWatermark = false;
      }
      if (DPIChanging)
      {
        double factor = ((double) actualOutputWidth / (double) previousWidth);
        int centerX = x + watermarkRect.width / 2;
        int centerY = y + (watermarkRect.height - oldLTH) / 2;
        centerX = (int) Math.round(centerX * factor);
        centerY = (int) Math.round(centerY * factor);
        factor = watermarkWidth / (double) watermarkRect.width;
        x = centerX - watermarkWidth / 2;
        y = centerY - (int) Math.round((double) watermarkRect.height * factor + LOGO_TEXT_HEIGHT)
            / 2;
        DPIChanging = false;
      }
      if (widthChanging)
      {
        double factor = ((double) actualOutputWidth / (double) previousWidth);
        int centerX = x + watermarkRect.width / 2;
        int centerY = y + (watermarkRect.height + oldLTH) / 2;
        centerX = (int) Math.round(centerX * factor);
        centerY = (int) Math.round(centerY * factor);
        factor = watermarkWidth / (double) watermarkRect.width;
        x = centerX - watermarkWidth / 2;
        y = centerY - ((int) Math.round((double) (watermarkRect.height + LOGO_TEXT_HEIGHT) * factor)/ 2);
        widthChanging = false;
      }
    }
    widthChanging = false;
    DPIChanging = false;
    // Prevent image from being drawn off the image
    if (x < 0) x = 0;
    if (y < imageTitle.getHeight() + mapHeader.getHeight() + 2 * BORDER_SIZE)
      y = imageTitle.getHeight() + mapHeader.getHeight() + 2 * BORDER_SIZE;
    if (x + newWidth > imagePreview.getWidth()) x = imagePreview.getWidth() - newWidth;
    if (y + newHeight > imagePreview.getHeight() - LOGO_TEXT_HEIGHT)
      y = imagePreview.getHeight() - newHeight - LOGO_TEXT_HEIGHT;
    // Draw the watermark Image

    g2d.drawImage(logoImage, x, y, x + newWidth, y + newHeight, 0, 0, width, height, null);

    if (!mousePressedWatermark)
      watermarkRect = new Rectangle(x, y, newWidth, newHeight + LOGO_TEXT_HEIGHT);

    BufferedImage versionImage = new BufferedImage(versionLabel.getPreferredSize().width,
        versionLabel.getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = versionImage.createGraphics();
    // Draw the watermark version label onto "versionLabel"

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    versionLabel.paint(g2);
    g2.dispose();

    x += (newWidth - versionLabel.getPreferredSize().width) / 2;
    y += newHeight;

    // Draw version label on the canvas
    g2d.drawImage(versionImage, x, y, versionLabel.getWidth(), versionLabel.getHeight(), null);

    g2d.dispose();
  }

  /**
   * Draws the map when the map is cropped
   */
  private void drawMap()
  {
    int x = BORDER_SIZE;
    int y = mapHeader.getHeight() + imageTitle.getHeight() + (2 * BORDER_SIZE);
    int width, height;
    int yTemp = y;
    if (mapRect != null)
    {
      if (mousePressedMap)
      {
        yTemp = mapRect.y;
        yTemp += ((int) ((mouseMovedEvent.getY() - mousePressEvent.getY()) / scalingRatio));
        mousePressEvent = mouseMovedEvent;
      }
      else if (DPIChanging)
      {
        cropHeight = ((int) ((double) cropHeight * ((double) (DPIScale / prevDPIScale))));
        yTemp = y - cropHeight;
      }
      else
      {
        yTemp = mapRect.y;
      }
    }
    if (yTemp > y)
    {
      yTemp = y;
    }
    if (!DPIChanging
        && yTemp < y + croppedMapNavigator.getHeight() + BORDER_SIZE - fullMapNavigator.getHeight())
    {
      yTemp = y + croppedMapNavigator.getHeight() + BORDER_SIZE - fullMapNavigator.getHeight();
    }
    cropHeight = y - yTemp;
    if (cropHeight < 0) cropHeight = 0;
    y = yTemp;
    cropImage();
    width = fullMapNavigator.getWidth();
    height = fullMapNavigator.getHeight();
    if (cropValue > 0)
    {
      mapRect = new Rectangle(x, y, width, height);
    }
    else
      mapRect = null;
  }

  /**
   * <p>
   * Creates a scaled {@link ImageIcon} of the main image to be saved. Determines the size of the
   * image based on the current {@link JDialog} size
   * </p>
   *
   * @return {@link ImageIcon} of the scaled {@link BufferedImage}
   */
  private ImageIcon getPreviewIcon()
  {
    try
    {
      int width, height;

      // Calculate width of scaled image based on height
      height = this.getHeight() - COMPONENT_SIZE;
      if (height > previewLabel.getMaximumSize().height)
        height = previewLabel.getMaximumSize().height;
      scalingRatio = (height) / (double) imagePreview.getHeight();
      width = (int) (imagePreview.getWidth() * scalingRatio);

      // if the width exceeds the size calculate height based on width
      if (width > this.getWidth() - 40)
      {
        width = this.getWidth() - 40;
        scalingRatio = (width) / (double) imagePreview.getWidth();
        height = (int) (imagePreview.getHeight() * scalingRatio);
      }

      scaledWatermarkRect = new Rectangle((int) (watermarkRect.x * scalingRatio),
          (int) (watermarkRect.y * scalingRatio), (int) (watermarkRect.width * scalingRatio),
          (int) (watermarkRect.height * scalingRatio));

      if (mapRect != null)
      {
        scaledMapRect = new Rectangle((int) (mapRect.x * scalingRatio),
            (int) (mapRect.y * scalingRatio), (int) (mapRect.width * scalingRatio),
            (int) (mapRect.height * scalingRatio));
      }
      else
        scaledMapRect = null;

      BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2d = scaledImage.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
          RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.drawImage(imagePreview, 0, 0, width, height, Color.WHITE, null);
      g2d.dispose();

      return new ImageIcon(scaledImage);
    }
    catch (IllegalArgumentException iae)
    {
      return icon; // Return original icon
    }
  }

  /**
   * <p>
   * Shows the {@link JFileChooser} window and handles the changing of the {@link File} if the user
   * selects the approve {@link JButton}.
   * </p>
   */
  public void showFileChooser()
  {
    // Set file to have no extension
    extension = fileChooser.getFileFilter().getDescription();
    int stop = file.getPath().length() - extension.length() - 1;
    fileChooser.setSelectedFile(new File(file.getPath().substring(0, stop)));

    int retVal = fileChooser.showDialog(this, "Select");

    if (retVal == JFileChooser.APPROVE_OPTION)
    {
      file = fileChooser.getSelectedFile(); // Create and store file
      path.setText(file.getPath()); // Set file path text
      String[] fileInfo = file.getPath().split("\\.");
      extension = fileInfo[fileInfo.length - 1];
    }
  }

  /**
   * <p>
   * Takes the user selected {@link File} from the {@link JFileChooser} and properly formats its
   * extension
   * </p>
   *
   * @param f
   *          The {@link File} whose extension will be formatted
   *
   * @return The properly formatted {@link File}
   */
  public File getFile(File f)
  {
    for (FileFilter filter : fileChooser.getChoosableFileFilters())
      if ((f.getName().endsWith("." + filter.getDescription())
          || f.getName().endsWith("." + filter.getDescription().toUpperCase()))
          && !f.getName().equals("." + filter.getDescription()))
      {
        fileChooser.setFileFilter(filter);
        return f; // Return file and set filter if extension matches one of the FileFilters
      }

    // Otherwise create a new file and append the currently selected extension to the end
    return new File(f.getPath() + "." + fileChooser.getFileFilter().getDescription());
  }

  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand().equals("Close") || ae.getActionCommand().equals("Cancel"))
    {
      setVisible(false);
      // re-enable mainGUI
      mainGUI.getMapNavigator().setPreviewing(false);
      mainGUI.getMapNavigator().setFeatureHighlight(true);
      mainGUI.getMapNavigator().setIntervalHighlight(true);
      if (originallyRemoved)
        mainGUI.getMapNavigator().hideOtherFeatures();
      else
        mainGUI.getMapNavigator().showAllAnnotation();
      mainGUI.getMapNavigator().setOtherAnnotationsRemoved(originallyRemoved);
      mainGUI.setEnabled(true);
      return;
    }

    else if (ae.getActionCommand().equals("MoveLogo"))
    {
      drawImage();
    }

    else if (ae.getActionCommand().equals("SaveFile"))
    {
      showFileChooser();
    }

    else if (ae.getActionCommand().equals("Save"))
    {
      historyField.addToHistory(historyField.getText());

      if (file.exists())
      {
        Object[] options =
        { "Continue", "Cancel" };
        int choice = JOptionPane.showOptionDialog(this, "The file you have specified already\n"
            + "exists. Would you like to overwrite\n" + "this file?", "Overwrite?",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
            options[options.length - 1]);

        if (choice == 0)
          saveImage();
        else
          return;
      }

      else
      {
        saveImage();
      }

      setVisible(false);
      // re-enable mainGUI
      mainGUI.getMapNavigator().setPreviewing(false);
      if (originallyRemoved)
        mainGUI.getMapNavigator().hideOtherFeatures();
      else
        mainGUI.getMapNavigator().showAllAnnotation();
      mainGUI.setEnabled(true);
    }
    else if (ae.getSource().equals(DPISelect))
    {
      double backupDPI = DPI;
      double prevWidth = outputWidth;
      DPI = DPI_OPTION_VALUES[DPISelect.getSelectedIndex()];
      outputWidth = Math.round(backupDPI * prevWidth / DPI * 100) / 100.0;
      if (4 * DPI * outputWidth * ((DPI / backupDPI)
          * (croppedMapNavigator.getHeight() + mapHeader.getHeight() + imageTitle.getHeight())) > MAX_IMAGE_SIZE)
      {
        JOptionPane.showMessageDialog(null, "The selected settings produced an error.\n"
            + "Reverting to last settings.", "Memory Error", JOptionPane.ERROR_MESSAGE);
        DPI = backupDPI;
        outputWidth = prevWidth;
        for (int i = 0; i < DPI_OPTION_VALUES.length; i++)
        {
          if (DPI == DPI_OPTION_VALUES[i]) DPISelect.setSelectedIndex(i);
        }
      }
      updateWatermarkRatio();
      updateWidthField(outputWidth);
      DPIChanging = true;
      drawImage();
    }
    else if (ae.getSource().equals(sizeEntry))
    {
      if (outputWidth != getDesiredWidth())
      {
        double prevWidth = outputWidth;
        outputWidth = getDesiredWidth();
        if (4 * DPI * outputWidth * ((outputWidth / prevWidth)
            * (croppedMapNavigator.getHeight() + mapHeader.getHeight() + imageTitle.getHeight()))
            > MAX_IMAGE_SIZE)
        {
          JOptionPane.showMessageDialog(null, "The selected settings produced an error.\n"
              + "Reverting to last settings.", "Memory Error", JOptionPane.ERROR_MESSAGE);
          outputWidth = prevWidth;
          sizeEntry.setText(Double.toString(outputWidth));
        }
        updateWatermarkRatio();
        widthChanging = true;
        drawImage();
      }
    }
    else if (ae.getActionCommand().equals("displayOnlySelectedFeatures"))
    {
      if (!annotationsRemoved)
        mainGUI.getMapNavigator().hideOtherFeatures();
      else
        mainGUI.getMapNavigator().showAllAnnotation();
      annotationsRemoved = !annotationsRemoved;
      drawnSinceRemoved = false;
      drawImage();
    }
    else if (ae.getActionCommand().equals("displayFeatureSelectHighlight"))
    {
      mainGUI.getMapNavigator().setFeatureHighlight(displayFeatureHighlight.isSelected());
      drawnSinceRemoved = false;
      drawImage();
    }
    else if (ae.getActionCommand().equals("displayIntervalSelectHighlight"))
    {
      mainGUI.getMapNavigator().setIntervalHighlight(displayIntervalHighlight.isSelected());
      drawnSinceRemoved = false;
      drawImage();
    }
  }

  public void updateWidthField(double value)
  {
    sizeEntry.setText(Double.toString(value));
  }

  public void keyReleased(KeyEvent e)
  {
    drawImage();
  }

  public void componentMoved(ComponentEvent e)
  {
    drawImage();
  }

  public void componentResized(ComponentEvent e)
  {
    drawImage();
  }

  /**
   * resizes the window based on the current height, width and minimum height and width
   */

  public void resizeWindow()
  {
    if (this.getHeight() < MINIMUM_HEIGHT) this.setSize(this.getWidth(), MINIMUM_HEIGHT);
    if (this.getHeight() > MAX_SCREEN_HEIGHT) this.setSize(this.getWidth(), MAX_SCREEN_HEIGHT);

    // calculate minimum width of the window 50 is for all of the padding
    int minWidth = previewLabel.getWidth() + DPISelect.getWidth() + 40;
    if (this.getWidth() < minWidth) this.setSize(minWidth, this.getHeight());
    if (this.getWidth() > MAX_SCREEN_WIDTH + 10) this.setSize(MAX_SCREEN_WIDTH, this.getHeight());
  }

  public void stateChanged(ChangeEvent e)
  {
    JSlider source = (JSlider) e.getSource();
    if (DPI <= 150)
    {
      if (source.equals(cropSlider))
      {
        cropValue = cropSlider.getValue();
        cropChanging = true;
        drawImage();
      }
      else if (source.equals(watermarkSize))
      {
        watermarkWidth = (int) ((double) watermarkSize.getValue()
            / 100.0 * watermarkRatio * (double) logoImage.getWidth());
        resizingWatermark = true;
        drawImage();
      }
    }
    else if (DPI > 150 && !source.getValueIsAdjusting())
    {
      if (source.equals(cropSlider))
      {
        cropValue = cropSlider.getValue();
        cropChanging = true;
        drawImage();
      }
      else if (source.equals(watermarkSize))
      {
        watermarkWidth = (int) ((double) watermarkSize.getValue()
            / 100.0 * watermarkRatio * (double) logoImage.getWidth());
        resizingWatermark = true;
        drawImage();
      }
    }
  }

  /**
   * if the width in the sizeField is different than the currently set width, the function will
   * update the width and return it to the function call
   *
   * @return The desired width from the user, the current width if no change was made
   */

  public double getDesiredWidth()
  {
    String value = sizeEntry.getText();
    if (!value.isEmpty())
    {
      double newWidth = Double.parseDouble(value);
      newWidth = ((double) (Math.round(newWidth * 100) / 100.0));
      sizeEntry.setText(Double.toString(newWidth));
      return newWidth;
    }
    else
      sizeEntry.setText(Double.toString(outputWidth));
    return outputWidth;
  }

  public void mousePressed(MouseEvent e)
  {
    if (scaledMapRect != null && scaledMapRect.contains(e.getPoint()))
    {
      mousePressedMap = true;
      mousePressEvent = e;
    }
    if (scaledWatermarkRect.contains(e.getPoint()))
    {
      mousePressedWatermark = true;
      mousePressedMap = false;
      mousePressEvent = e;
    }

  }

  public void mouseReleased(MouseEvent e)
  {
    if (mousePressedWatermark)
    {
      mousePressedWatermark = false;

      watermarkRect.x += (int) ((mouseMovedEvent.getX() - mousePressEvent.getX()) / scalingRatio);
      watermarkRect.y += (int) ((mouseMovedEvent.getY() - mousePressEvent.getY()) / scalingRatio);

      drawImage();

    }
    if (mousePressedMap)
    {
      mousePressedMap = false;

      mapRect.y += (int) ((mouseMovedEvent.getY() - mousePressEvent.getY()) / scalingRatio);

      drawImage();
    }

  }

  public void mouseDragged(MouseEvent e)
  {
    if (mousePressedWatermark)
    {
      mouseMovedEvent = e;
      drawImage();
    }
    else if (mousePressedMap)
    {
      mouseMovedEvent = e;
      drawImage();
    }

  }

  public void mouseMoved(MouseEvent e)
  {
    if (scaledWatermarkRect.contains(e.getPoint()))
    {
      previewLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else if (scaledMapRect != null && scaledMapRect.contains(e.getPoint()))
    {
      previewLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
    }
    else
    {
      previewLabel.setCursor(Cursor.getDefaultCursor());
    }

  }

  public void focusLost(FocusEvent e)
  {
    if (outputWidth != getDesiredWidth())
    {
      double prevWidth = outputWidth;
      outputWidth = getDesiredWidth();
      if (4 * DPI * outputWidth * ((outputWidth / prevWidth)
          * (croppedMapNavigator.getHeight() + mapHeader.getHeight() + imageTitle.getHeight())) > MAX_IMAGE_SIZE)
        System.out.println("Possible Error");
      widthChanging = true;
      drawImage();
    }
  }

  /**
   * <p>
   * Saves the {@link BufferedImage} to a {@link File}
   * </p>
   *
   */
  public void saveImage()
  {
    IIOMetadata metadata = null;
    ImageWriter writer = null;
    ImageWriteParam writeParam = null;
    ImageTypeSpecifier typeSpecifier = null;
    for (Iterator<ImageWriter> iw = ImageIO.getImageWritersByFormatName(extension); iw.hasNext();)
    {
      writer = iw.next();
      writeParam = writer.getDefaultWriteParam();
      typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
      metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);
      if (metadata.isReadOnly() || !metadata.isStandardMetadataFormatSupported())
      {
        continue;
      }
    }
    try
    {
      setDPI(metadata);
    }
    catch (IIOInvalidTreeException ite)
    {
    }

    try
    {
      final ImageOutputStream stream = ImageIO.createImageOutputStream(file);
      writer.setOutput(stream);
      writer.write(metadata, new IIOImage(imagePreview, null, metadata), writeParam);
      stream.close();
    }
    catch (IOException ioe)
    {
    }
  }

  public void setDPI(IIOMetadata metadata) throws IIOInvalidTreeException
  {
    if (extension.equals("png"))
    {
      double dotsPerMilli = 1.0 * DPI / INCH_2_CM / 10.0;

      IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
      horiz.setAttribute("value", Double.toString(dotsPerMilli));

      IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
      vert.setAttribute("value", Double.toString(dotsPerMilli));

      IIOMetadataNode dim = new IIOMetadataNode("Dimension");
      dim.appendChild(horiz);
      dim.appendChild(vert);

      IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
      root.appendChild(dim);

      metadata.mergeTree("javax_imageio_1.0", root);
    }
    else if (extension.equals("jpeg"))
    {
      double decimeterPerDot = 1.0 / DPI * INCH_2_CM / 10.0;

      IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
      horiz.setAttribute("value", Double.toString(decimeterPerDot));

      IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
      vert.setAttribute("value", Double.toString(decimeterPerDot));

      IIOMetadataNode dim = new IIOMetadataNode("Dimension");
      dim.appendChild(horiz);
      dim.appendChild(vert);

      IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
      root.appendChild(dim);

      metadata.mergeTree("javax_imageio_1.0", root);
    }
  }

  // Unused methods
  public void keyPressed(KeyEvent e)
  {
  }

  public void keyTyped(KeyEvent e)
  {
  }

  public void componentHidden(ComponentEvent e)
  {
  }

  public void componentShown(ComponentEvent e)
  {
  }

  public void mouseClicked(MouseEvent e)
  {
  }

  public void mouseEntered(MouseEvent e)
  {
  }

  public void mouseExited(MouseEvent e)
  {
  }

  public void focusGained(FocusEvent e)
  {
  }

}
