package bioneos.vcmap.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.common.instance.BNApplication;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.AnnotationLoader;
import bioneos.vcmap.callback.CustomAnnotationLoader;
import bioneos.vcmap.callback.MapLoader;
import bioneos.vcmap.gui.dialogs.AboutDialog;
import bioneos.vcmap.gui.dialogs.AnnotationDialog;
import bioneos.vcmap.gui.dialogs.DetailsDialog;
import bioneos.vcmap.gui.dialogs.DownloadDialog;
import bioneos.vcmap.gui.dialogs.LoadCustomDataDialog;
import bioneos.vcmap.gui.dialogs.MapDialog;
import bioneos.vcmap.gui.dialogs.PreferencesDialog;
import bioneos.vcmap.gui.dialogs.ImageDialog;
import bioneos.vcmap.gui.dialogs.SearchDialog;
import bioneos.vcmap.gui.dialogs.SwapMapDialog;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.Factory;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.OntologyNode;
import bioneos.vcmap.model.SyntenyBlock;
import bioneos.vcmap.model.parsers.BAMParser;
import bioneos.vcmap.model.parsers.BEDParser;
import bioneos.vcmap.model.parsers.FileParser;
import bioneos.vcmap.model.parsers.GFF3Parser;
import bioneos.vcmap.model.parsers.VCFParser;
import bioneos.vcmap.options.GlobalOptions;

/**
 * <p>This class creates a {@link JFrame} that contains all the graphical data
 * that is displayed to the user, along with the means for the user to interact
 * with that data. This class also manages all of the map data that is stored
 * in local memory.</p>
 *
 * <p>Created on: October 8, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class MainGUI
  extends JFrame
  implements ActionListener, ChangeListener, MenuListener
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  private VCMap vcmap;

  // GUI Components
  private MapNavigator mapNavigator;
  private MapHeaders mapHeaders;
  private JSlider zoomBar;
  private JMenuBar menu;
  private StatusBar status;
  private JScrollPane mapScroll;
  private JMenuItem mapSwap;
  private JMenuItem viewDetails;
  private ProgressPopup progress;

  // Variables
  private Chromosome backbone;
  private Vector<MapData> maps;

  // Used for Determinate progress bar
  private int totalLines;

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  //Constants
  public static final int ALREADY_LOADED = -1;
  public static final int LOAD_CANCELED = 0;
  public static final int LOAD_SUCCESSFUL = 1;
  public static final int BACKBONE_ERROR = 2;
  public static final int OUT_OF_MEMORY_ERROR = 2;
  public static final int SQL_ERROR = 3;
  public static final int FILE_READ_ERROR = 4;
  public static final int SYNTENY_ERROR = 4;
  public static final int NO_DATA_LOADED_ERROR = 5;
  public static final int GFF3_FEATURE_ERROR = 6;
  public static final int CUSTOM_ANNOTATION_WARNING_LIMIT = 5000;

  /**
   * <p>Constructor for {@link MainGUI}. Initializes all the variables so that
   * the main user interface can be displayed to the user.</p>
   *
   * @param app
   *   The Application that controls this {@link MainGUI}.  This parameter must
   *   be a {@link BNApplication}, but is assumed that it can be cast into a
   *   {@link VCMap} Object.
   */
  public MainGUI(BNApplication app)
  {
    this.vcmap = (VCMap) app;

    maps = new Vector<MapData>();

    logger.debug("Performing component setup");
    // Component setup
    mapNavigator = new MapNavigator(this);
    mapHeaders = new MapHeaders(this);
    mapNavigator.setMapHeaders(mapHeaders);
    mapScroll = new JScrollPane(mapNavigator);
    int maxZoomValue = (getOptions().getIntOption("maxZoomBarValue") > 0) ?
      getOptions().getIntOption("maxZoomBarValue") : 15;
    zoomBar = new JSlider(JSlider.VERTICAL, 0, maxZoomValue, 0);
    zoomBar.setAlignmentX(Component.CENTER_ALIGNMENT);
    zoomBar.addChangeListener(this);
    zoomBar.setMajorTickSpacing(1);
    zoomBar.setSnapToTicks(true);
    zoomBar.setInverted(true);
    zoomBar.setPaintTicks(true);
    JLabel zoomL = new JLabel("Zoom");
    zoomL.setFont(new Font("default", Font.PLAIN, 10));
    zoomL.setAlignmentX(Component.CENTER_ALIGNMENT);
    status = new StatusBar(this);

    // Set glass pane
    setGlassPane(new CustomGlassPane(this));

    // Determine approximate width of three maps
    int approxWidth = (getOptions().getIntOption("segmentWidth")
      + getOptions().getIntOption("featureColumnWidth") * 3
      + getOptions().getIntOption("featureLabelColumnWidth") * 2
      + getOptions().getIntOption("unitsWidth")) * 3
      + getOptions().getIntOption("betweenMapWidth") * 2
      + getOptions().getIntOption("leftMarginWidth") * 2;

    // Resize mapNavigator to fit properly on user's screen
    Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
    int mapNavigatorWidth = (approxWidth < screenDimension.width * 3.0 / 4.0) ? approxWidth : (int)(screenDimension.width * 3.0 / 4.0);
    int mapNavigatorHeight = (approxWidth * (3.0 / 4.0) < screenDimension.height * 3 / 4) ? (int)(approxWidth * (3.0 / 4.0)) : (int)(screenDimension.height * 3.0 / 4.0);

    Dimension mapNavigatorSize = new Dimension(mapNavigatorWidth, mapNavigatorHeight);
    mapNavigator.setPreferredSize(mapNavigatorSize);

    // Set some color preferences and size preferences
    FontMetrics metrics = this.getFontMetrics(VCMap.labelFont);
    mapHeaders.setPreferredSize(new Dimension(mapNavigator.getWidth(), metrics.getHeight() * 4));
    mapHeaders.setBackground(getOptions().getColor("header"));
    mapScroll.setColumnHeaderView(mapHeaders);
    mapScroll.setBackground(getOptions().getColor("header"));
    mapScroll.getColumnHeader().setBackground(getOptions().getColor("header"));
    mapScroll.addComponentListener(new ComponentListener()
      {
        public void componentResized(ComponentEvent e)
        {
          // Ensure everything displayed in the mapNavigator is still correct
          mapNavigator.updateSize();
        }

        public void componentHidden(ComponentEvent e) {}
        public void componentMoved(ComponentEvent e){}
        public void componentShown(ComponentEvent e) {}
      });

    mapScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    mapScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    mapNavigator.setBackground(getOptions().getColor("background"));
    mapScroll.getViewport().setBackground(getOptions().getColor("background"));

    // Component layout
    JPanel zoom = new JPanel();
    BoxLayout box = new BoxLayout(zoom, BoxLayout.Y_AXIS);
    zoom.setLayout(box);
    zoom.add(zoomL);
    zoom.add(Box.createVerticalStrut(5));
    zoom.add(zoomBar);
    zoom.setBorder(new javax.swing.border.EmptyBorder(15, 5, 15, 5));
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(status, "North");
    mainPanel.add(mapScroll, "Center");
    mainPanel.add(zoom, "West");
    setContentPane(mainPanel);
    // Setup Main Menubar
    logger.debug("Setting up main menubar");
    setupMenu();

    // Center the window
    pack();
    Dimension tempSize = Toolkit.getDefaultToolkit().getScreenSize();
    Point temppoint = new Point(tempSize.width / 2, tempSize.height / 2);
    try
    {
      if (Double.parseDouble(System.getProperty("java.version").substring(0,3)) >= 1.4)
        temppoint = GraphicsEnvironment.getLocalGraphicsEnvironment().getCenterPoint();
      else
        temppoint = new Point(tempSize.width / 2, tempSize.height / 2);
    }
    catch (NumberFormatException nfe)
    {
      temppoint = new Point(tempSize.width / 2, tempSize.height / 2);
    }
    temppoint.x -= getSize().width / 2;
    temppoint.y -= getSize().height / 2;
    setLocation(temppoint);

    // Final setup
    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter()
      {
        public void windowClosing(WindowEvent we)
        {
          userDispose();
        }
      });
    setTitle(VCMap.NAME + " v" + VCMap.RELEASE + "." + VCMap.FEATURE);

    mapNavigator.requestFocusInWindow();

    setName("MainGUI");
    setVisible(true);
  }

  /**
   * <p>Gets an image of the {@link MapHeaders} panel and sets the background to
   * the defined color in the {@link ImageDialog} class</p>
   * @return
   *    The {@link BufferedImage} created by the {@link MapHeaders} class
   */
  public BufferedImage getHeaderImage(int desiredWidth)
  {
    return mapHeaders.createImage(desiredWidth);
  }

  /**
   * <p>Gets an image of the {@link MapNavigator} image defined by its visible
   * {@link Rectangle} in the {@link JScrollPane}.  The {@link Rectangle} can be
   * changed in future to capture more than just the visible {@link Rectangle}.</p>
   * @return
   *    A {@link BufferedImage} of the {@link MapNavigator}s visible {@link Rectangle}
   */
  public BufferedImage getNavigatorImage(int desiredWidth)
  {
    // Get the visible rectangle of the mapNavigator
    Rectangle rectangle = mapNavigator.getVisibleRect();
    rectangle.x = 0;
    rectangle.width = mapNavigator.getWidth();

    return mapNavigator.createImage(rectangle, desiredWidth);
  }

  /**
   * <p>Get a reference to the current Backbone {@link Chromosome} object.</p>
   *
   * @return
   *   A reference to the current backbone {@link Chromosome}, or null if no
   *   maps have yet been loaded into the system.
   */
  public Chromosome getBackbone()
  {
    return backbone;
  }

  /**
   * <p>This method returns a {@link MapData} that is stored in memory</p>
   *
   * @param mapName
   *   Properly formatted map name, like map name returned by
   *   <code>getName()</code> in {@link MapData}
   * @return
   *   {@link MapData} reference of map requested, or null if
   *   {@link MapData} not loaded in memory
   */
  public MapData getMap(String mapName)
  {
    for (MapData map : maps)
      if (mapName.compareTo(map.getName()) == 0)
        return map;

    return null;
  }

  /**
   * <p>This function returns a reference to all {@link MapData} in memory</p>
   *
   * @return
   *    Reference to all maps already loaded in memory
   */
  public Vector<MapData> getMaps()
  {
    return maps;
  }

  /**
   * <p>Get the maximum value of the zoom bar.</p>
   *
   * @return
   *   int value of the maximum value for the zoom bar
   */
  public JSlider getZoomBar()
  {
    return zoomBar;
  }

  /**
   * <p>Get the {@link MapNavigator} of this {@link MainGUI}.</p>
   *
   * @return
   *   {@link MapNavigator} of this {@link MainGUI}
   */
  public MapNavigator getMapNavigator()
  {
    return mapNavigator;
  }

  /**
   * <p>Get the {@link StatusBar} of this {@link MainGUI}.</p>
   *
   * @return
   *   {@link StatusBar} of this {@link MainGUI}
   */
  public StatusBar getStatusBar()
  {
    return status;
  }

  /**
   * <p>Get the {@link VCMap} this {@link MainGUI} belongs too.</p>
   *
   * @return
   *   {@link VCMap} this {@link MainGUI} belongs too
   */
  public VCMap getVCMap()
  {
    return vcmap;
  }

  /**
   * </p>Check if the backbone is set or not</p>
   *
   * @return
   *   true if backbone is set, false if the backbone is not set
   */
  public boolean isBackboneSet()
  {
    if (backbone == null) return false;
    return true;
  }

  /**
   * <p>Increment the zoom bar</p>
   */
  public void incrementZoomBar()
  {
    zoomBar.setValue(zoomBar.getValue() + zoomBar.getMajorTickSpacing());
  }

  /**
   * <p>Decrement the zoom bar</p>
   */
  public void decrementZoomBar()
  {
    zoomBar.setValue(zoomBar.getValue() - zoomBar.getMajorTickSpacing());
  }

  /**
   * <p>Returns the {@link GlobalOptions} saved in {@link VCMap} class.
   * {@link GlobalOptions} stores all of the program preferences globally for
   * all of the {@link MainGUI} objects in one central location.</p>
   *
   * @return
   *   {@link GlobalOptions} stored in the {@link VCMap} {@link BNApplication}
   *   class.
   */
  public GlobalOptions getOptions()
  {
    return vcmap.getOptions();
  }

  /**
   * <p>Set preference options</p>
   *
   * @param options
   *   New {@link GlobalOptions} for {@link MainGUI}
   */
  public void setOptions(GlobalOptions options)
  {
    vcmap.setOptions(options);
    // FIXME - should use the event system approach here
    mapNavigator.optionsUpdated();
  }

  public void setViewDetails(boolean viewDetails)
  {
    this.viewDetails.setEnabled(viewDetails);
  }

  /**
   * <p>Sets up the menu displayed on the top of the main window for the program.</p>
   *
   */
  private void setupMenu()
  {
    menu = new JMenuBar();

    // File menu
    JMenu file = new JMenu("File");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      file.setMnemonic('F');
    JMenuItem newWindow = new JMenuItem("New Window");
    newWindow.setActionCommand("newWindow");
    newWindow.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N
        , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    newWindow.addActionListener(this);
    newWindow.setMnemonic('N');
    file.add(newWindow);
    JMenuItem fileSave = new JMenuItem("Export Image...");
    fileSave.setEnabled(true);
    fileSave.setActionCommand("Save");
    fileSave.addActionListener(this);
    fileSave.setMnemonic('E');
    file.add(fileSave);
    JMenuItem fileDownload = new JMenuItem("Download...");
    fileDownload.setEnabled(true);
    fileDownload.setActionCommand("Download");
    fileDownload.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    fileDownload.addActionListener(this);
    fileDownload.setMnemonic('D');
    file.add(fileDownload);
    file.add(new JSeparator());
    JMenuItem filePrefs = new JMenuItem("Edit Preferences...");
    filePrefs.setActionCommand("Preferences");
    filePrefs.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    filePrefs.addActionListener(this);
    filePrefs.setMnemonic('P');
    file.add(filePrefs);
    file.add(new JSeparator());
    JMenuItem fileQuit = new JMenuItem("Quit");
    fileQuit.setActionCommand("Quit");
    fileQuit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    fileQuit.addActionListener(this);
    fileQuit.setMnemonic('Q');
    file.add(fileQuit);
    menu.add(file);

    // Map menu
    JMenu map = new JMenu("Map");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      map.setMnemonic('M');
    JMenuItem mapLoad = new JMenuItem("Load...");
    mapLoad.setActionCommand("Load");
    mapLoad.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    mapLoad.addActionListener(this);
    mapLoad.setMnemonic('L');
    map.add(mapLoad);
    mapSwap = new JMenuItem("Swap Backbones...");
    mapSwap.setActionCommand("SwapBB");
    mapSwap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B
        , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    mapSwap.addActionListener(this);
    mapSwap.setMnemonic('B');
    mapSwap.setEnabled(false);
    map.add(mapSwap);
    map.add(new JSeparator());
    JMenuItem mapReposition = new JMenuItem("Restore Positions");
    mapReposition.setActionCommand("RestorePositions");
    mapReposition.addActionListener(this);
    mapReposition.setMnemonic('R');
    map.add(mapReposition);
    JMenuItem zoomOnSelection = new JMenuItem("Zoom on Selection");
    zoomOnSelection.setActionCommand("zoomOnSelect");
    zoomOnSelection.addActionListener(this);
    zoomOnSelection.setMnemonic('Z');
    map.add(zoomOnSelection);
    JMenuItem centerPosition = new JMenuItem("Center on Backbone");
    centerPosition.setActionCommand("CenterOnBB");
    centerPosition.addActionListener(this);
    centerPosition.setMnemonic('C');
    map.add(centerPosition);
    map.add(new JSeparator());
    JMenuItem mapHide = new JMenuItem("Hide Selected");
    mapHide.setActionCommand("HideMaps");
    mapHide.addActionListener(this);
    mapHide.setMnemonic('H');
    map.add(mapHide);
    JMenuItem mapShowSelected = new JMenuItem("Show Selected");
    mapShowSelected.setActionCommand("ShowMap");
    mapShowSelected.addActionListener(this);
    mapShowSelected.setMnemonic('S');
    map.add(mapShowSelected);
    JMenuItem mapShowAll = new JMenuItem("Show All");
    mapShowAll.setActionCommand("ShowMaps");
    mapShowAll.addActionListener(this);
    mapShowAll.setMnemonic('A');
    map.add(mapShowAll);
    menu.add(map);

    // Annotation menu
    JMenu annot = new JMenu("Annotation");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      annot.setMnemonic('A');
    JMenuItem annotLoad = new JMenuItem("Load Annotation...");
    annotLoad.setActionCommand("LoadAnnot");
    annotLoad.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    annotLoad.addActionListener(this);
    annotLoad.setMnemonic('L');
    annot.add(annotLoad);
    JMenuItem loadCustom = new JMenuItem("Import from File...");
    loadCustom.setActionCommand("CustomAnnotation");
    loadCustom.addActionListener(this);
    loadCustom.setMnemonic('I');
    annot.add(loadCustom);
    viewDetails = new JMenuItem("View Details...");
    viewDetails.setActionCommand("ViewDetails");
    viewDetails.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T
        , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    viewDetails.addActionListener(this);
    viewDetails.setMnemonic('V');
    setViewDetails(false);
    annot.add(viewDetails);
    annot.add(new JSeparator());
    JMenu annotHide = new JMenu("Hide");
    annotHide.setMnemonic('H');
    JMenuItem annotHideSelected = new JMenuItem("All Selected");
    annotHideSelected.setActionCommand("HideAnnotSelected");
    annotHideSelected.addActionListener(this);
    annotHideSelected.setMnemonic('A');
    annotHide.add(annotHideSelected);
    JMenuItem annotHideInterval = new JMenuItem("All in Selected Intervals");
    annotHideInterval.setActionCommand("HideAnnotsInterval");
    annotHideInterval.addActionListener(this);
    annotHideInterval.setMnemonic('I');
    annotHide.add(annotHideInterval);
    JMenuItem annotHideOther = new JMenuItem("All Other");
    annotHideOther.setActionCommand("HideOther");
    annotHideOther.addActionListener(this);
    annotHideOther.setMnemonic('O');
    annotHide.add(annotHideOther);
    JMenu annotHideType = new JMenu("All of Type...");
    annotHideType.setName("annotHideType");
    annotHideType.addMenuListener(this);
    annotHideType.setMnemonic('T');
    annotHide.add(annotHideType);
    annot.add(annotHide);
    JMenu annotShow = new JMenu("Show");
    annotShow.setMnemonic('S');
    JMenuItem annotShowAll = new JMenuItem("All");
    annotShowAll.setActionCommand("ShowAnnotsAll");
    annotShowAll.addActionListener(this);
    annotShowAll.setMnemonic('A');
    annotShow.add(annotShowAll);
    JMenuItem annotShowInterval = new JMenuItem("All in Selected Intervals");
    annotShowInterval.setActionCommand("ShowAnnotsInterval");
    annotShowInterval.addActionListener(this);
    annotShowInterval.setMnemonic('I');
    annotShow.add(annotShowInterval);
    JMenu annotShowType = new JMenu("All of Type...");
    annotShowType.setName("annotShowType");
    annotShowType.addMenuListener(this);
    annotShowType.setMnemonic('T');
    annotShow.add(annotShowType);
    annot.add(annotShow);
    menu.add(annot);

    // Search menu
    JMenu search = new JMenu("Search");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      search.setMnemonic('S');
    JMenuItem searchFind = new JMenuItem("Find...");
    searchFind.setActionCommand("Find");
    searchFind.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F
          , Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
    searchFind.addActionListener(this);
    searchFind.setMnemonic('F');
    search.add(searchFind);
    menu.add(search);

    // Help menu
    JMenu help = new JMenu("Help");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      help.setMnemonic('H');
    JMenuItem helpContents = new JMenuItem("Contents...");
    helpContents.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
    helpContents.setActionCommand("Help");
    helpContents.addActionListener(this);
    helpContents.setMnemonic('C');
    help.add(helpContents);
    JMenuItem helpAbout = new JMenuItem("About...");
    helpAbout.setActionCommand("About");
    helpAbout.addActionListener(this);
    helpAbout.setMnemonic('A');
    help.add(helpAbout);
    JMenuItem helpTutorial = new JMenuItem("Tutorial...");
    helpTutorial.setActionCommand("Tutorial");
    helpTutorial.addActionListener(this);
    helpTutorial.setMnemonic('T');
    help.add(helpTutorial);
    menu.add(help);

    JMenu debugMenu = new JMenu("Debug");
    if (System.getProperty("os.name").toLowerCase().indexOf("mac") == -1)
      debugMenu.setMnemonic('D');
    debugMenu.setVisible(vcmap.getDebug());
    JMenuItem timerDisplay = new JMenuItem("Display All Timings");
    timerDisplay.setActionCommand("All Timings");
    timerDisplay.addActionListener(this);
    timerDisplay.setMnemonic('A');
    timerDisplay.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK + KeyEvent.SHIFT_DOWN_MASK));
    debugMenu.add(timerDisplay);
    JMenuItem parentTimerDisplay = new JMenuItem("Display Parent Timings");
    parentTimerDisplay.setActionCommand("Timings Only Parents");
    parentTimerDisplay.addActionListener(this);
    parentTimerDisplay.setMnemonic('P');
    parentTimerDisplay.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK + KeyEvent.SHIFT_DOWN_MASK));
    debugMenu.add(parentTimerDisplay);
    menu.add(debugMenu);

    setJMenuBar(menu);
  }

  /*
   * Helper method to perform a graceful shutdown of our components when the
   * user closes the main application window.
   */
  private void userDispose()
  {
    dispose();
    vcmap.closeWindow(this);
  }

  /*
   * Overridden for the graceful shutdown of our components.
   */
  public void dispose()
  {
    // Remove all instances of dialogs for this MainGUI
    AboutDialog.closeAboutDialog(this);
    AnnotationDialog.closeAnnotationDialog(this);
    DownloadDialog.closeDownloadDialog(this);
    MapDialog.closeMapDialog(this);
    PreferencesDialog.closePreferencesDialog(this);
    SearchDialog.closeSearchDialog(this);
    ImageDialog.closeSaveDialog(this);
    DetailsDialog.closeDetailsDialog(this);
    LoadCustomDataDialog.closeLoadCustomDataDialog(this);
    // Now perform our superclass cleanup
    super.dispose();
  }

  /*
   * (non-Javadoc)
   * Change zoom level in MapNavigator when zoom bar value is changed
   * @see java.awt.event.AdjustmentListener#adjustmentValueChanged(java.awt.event.AdjustmentEvent)
   */
  public void stateChanged(ChangeEvent e)
  {
    if (e.getSource() == zoomBar && backbone != null && !((JSlider) e.getSource()).getValueIsAdjusting())
    {
      logger.debug("Zoom level changed to: " + zoomBar.getValue());
      mapNavigator.setZoomLevel(zoomBar.getValue());

      // Change unitIncrement so it "acts" like the block increment
      JScrollBar tempScrollBar = mapScroll.getVerticalScrollBar();
      tempScrollBar.setUnitIncrement(tempScrollBar.getBlockIncrement(0));

      tempScrollBar = mapScroll.getHorizontalScrollBar();
      tempScrollBar.setUnitIncrement(tempScrollBar.getBlockIncrement(0));
    }
  }

  /*
   * (non-Javadoc)
   * Handle all the menu events
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  public void actionPerformed(ActionEvent ae)
  {
    // Hide QTL popup if it is visible
    if (status.getQTLPopup().isVisible())
      status.getQTLPopup().setVisible(false);

    if (ae.getActionCommand().equals("Quit"))
    {
      logger.debug("Program Quit");
      vcmap.quit();
    }
    else if (ae.getActionCommand().equals("zoomOnSelect"))
    {
      zoomBar.setValue(mapNavigator.calcZoomValue());
    }
    else if (ae.getActionCommand().equals("newWindow"))
    {
      MainGUI mg = new MainGUI(vcmap);
      vcmap.openWindow(mg);
      MapDialog.showMapDialog(mg);
    }
    else if (ae.getActionCommand().equals("Load"))
    {
      logger.debug("'Load map' menu item chosen");
      MapDialog.showMapDialog(this);
    }
    else if (ae.getActionCommand().equals("CustomAnnotation"))
    {
      LoadCustomDataDialog.showLoadCustomDataDialog(this);
    }
    else if (ae.getActionCommand().equals("Preferences"))
    {
      logger.debug("'Preferences' menu item chosen");
      PreferencesDialog.showPreferencesDialog(this);
    }
    else if (ae.getActionCommand().equals("Download"))
    {
      logger.debug("'Download' menu item chosen");
      DownloadDialog.showDownloadDialog(this);
    }
    else if (ae.getActionCommand().equals("LoadAnnot"))
    {
      logger.debug("'Load Annotation' menu item chosen");
      AnnotationDialog.showAnnotationDialog(this);
    }
    else if (ae.getActionCommand().equals("ViewDetails"))
    {
      logger.debug("'View Details' menu item chosen");
      DetailsDialog.showDetailsDialog(this);
    }
    else if (ae.getActionCommand().equals("SwapBB"))
    {
      logger.info("'Swap Backbone...' menu item chosen");
      SwapMapDialog.showSwapMapDialog(this);
    }
    else if (ae.getActionCommand().equals("Find"))
    {
      logger.debug("'Search' menu item chosen");
      SearchDialog.showSearchDialog(this);
    }
    else if (ae.getActionCommand().equals("Help"))
    {
      logger.debug("'Help' menu item chosen");
      Point center = getLocation();
      center.x += (getSize().width / 2) ;
      center.y += (getSize().height / 2) ;
      vcmap.launchHelp(center);
    }
    else if (ae.getActionCommand().equals("About"))
    {
      logger.debug("'About' menu item chosen");
      AboutDialog.showAboutDialog(this);
    }
    else if (ae.getActionCommand().equals("Tutorial"))
    {
      logger.debug("'Tutorial' menu item chosen");
      Tutorial.showTutorial(this);
    }
    else if (ae.getActionCommand().equals("ShowMaps"))
    {
      logger.debug("'Show All' maps menu item chosen");
      mapNavigator.showAllMaps();
    }
    else if (ae.getActionCommand().equals("ShowMap"))
    {
      logger.debug("'Show Selected' map menu item chosen");
      mapNavigator.showSelectedMap();
    }
    else if (ae.getActionCommand().equals("HideMaps"))
    {
      logger.debug("'Hide selected map' menu item chosen");
      mapNavigator.hideSelectedMap();
    }
    else if (ae.getActionCommand().equals("HideAnnotSelected"))
    {
      logger.debug("'Hide selected annots' menu item chosen");
      mapNavigator.hideSelectedAnnotation();
    }
    else if (ae.getActionCommand().equals("HideAnnotsInterval"))
    {
      logger.debug("'Hide annots in interval' menu item chosen");
      mapNavigator.hideSelectedInterval();
    }
    else if (ae.getActionCommand().equals("HideOther"))
    {
      logger.debug("'Hide other annotations' menu item chosen");
      mapNavigator.hideOtherFeatures();
    }
    else if (ae.getActionCommand().equals("ShowAnnotsAll"))
    {
      logger.debug("'Show all annots' menu item chosen");
      mapNavigator.showAllAnnotation();
    }
    else if (ae.getActionCommand().equals("ShowAnnotsInterval"))
    {
      mapNavigator.showSelectedInterval();
    }
    else if (ae.getActionCommand().startsWith("hideType"))
    {
      mapNavigator.hideAnnotationSet(ae.getActionCommand().substring(8));
    }
    else if (ae.getActionCommand().startsWith("showType"))
    {
      mapNavigator.showAnnotationSet(ae.getActionCommand().substring(8));
    }
    else if (ae.getActionCommand().equals("RestorePositions"))
    {
      mapNavigator.restoreMapPositions();
    }
    else if (ae.getActionCommand().equals("CenterOnBB"))
    {
      mapNavigator.centerOnBackbone();
    }
    else if (ae.getActionCommand().equals("Save"))
    {
      logger.debug("'Save' menu item chosen");
      getMapNavigator().setPreviewing(true);
      getMapNavigator().setFeatureHighlight(true);
      getMapNavigator().setIntervalHighlight(true);
      ImageDialog.showSaveDialog(this);

    }
    else if (ae.getActionCommand().equals("Timings Only Parents"))
      loadTimingDialog(true);
    else if (ae.getActionCommand().equals("All Timings"))
      loadTimingDialog(false);
    // update the tutorial page
    Tutorial.updatePage(ae.getActionCommand());
  }

  private void loadTimingDialog(boolean parents)
  {
    if (parents)
    {
      if (JOptionPane.showConfirmDialog(this, generateTimingOutput(parents), "Parent Timings",
          JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null) == JOptionPane.YES_OPTION)
        parents = false;
    }
    if (!parents)
    {
      if (JOptionPane.showConfirmDialog(this, generateTimingOutput(parents), "All Timings",
          JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE, null) == JOptionPane.YES_OPTION)
        vcmap.getTimings().clear();
    }

  }

  private JComponent generateTimingOutput(boolean parents)
  {
    DecimalFormat formatterNS = new DecimalFormat("0.000 ms");
    DecimalFormat formatterS = new DecimalFormat("0");
    if(vcmap.getTimings().size() == 0)
    {
      return new JLabel("<html>No Timing Entries Found<br><center>Clear the timings?</center></html>");
    }
    String message = "<html><table border=\"1\"><tr>" +
    		"<th>Operation</th>" +
    		"<th>Last Time</th>" +
    		"<th>Average time</th>" +
    		"<th>Total time</th>" +
    		"<th>Max time</th>" +
    		"<th>Runs</th></tr>";
    Set<String> set = vcmap.getTimings().keySet();
    String[] keys = new String[set.size()];
    set.toArray(keys);
    List<String> tmpkeyList = Arrays.asList(keys);

    Collections.sort(tmpkeyList);
    Vector<String> parentStrings = new Vector<String>();
    for (String key : tmpkeyList)
    {
      int i = 0;
      String operation = key;
      if (parentStrings.isEmpty() || !key.contains(parentStrings.firstElement()))
      {
        parentStrings.clear();
        parentStrings.add(key);
      }
      else if (key.contains(parentStrings.firstElement() + " - "))
      {
        for (i = parentStrings.size() - 1 ; i >= 0 ; i--)
        {
          if (key.contains(parentStrings.elementAt(i) + " - "))
          {
            String[] temp = key.split(parentStrings.lastElement() + " - ");
            String dashes = "";
            for(int j = 0 ; j < i + 1 ; j++)
              dashes += "---";
            operation = dashes + temp[temp.length - 1];
            parentStrings.add(key);
            i++;
            break;
          }
          else
            parentStrings.remove(i);
        }
        if (parents && i > 0)
          continue;
      }
      Vector<Long> timings = vcmap.getTimings().get(key);
      long sum = 0;
      long max = 0;
      for (long time : timings)
      {
        if (time > max)
          max = time;
        sum += time;
      }
      message += "<tr><td>" + operation + "</td>"; //opeartion (key)
      message += "<td align=\"right\">" + formatterNS.format((double)timings.lastElement() / 1000000.0) + "</td>"; //latest run
      message += "<td align=\"right\">" + formatterNS.format((((double)sum)/((double)timings.size())) / 1000000.0) + "</td>"; //averate runtime
      message += "<td align=\"right\">" + formatterNS.format((double)sum / 1000000.0) + "</td>"; //total runtime
      message += "<td align=\"right\">" + formatterNS.format((double)max / 1000000.0) + "</td>"; // max runtime
      message += "<td align=\"right\">" + formatterS.format(timings.size()) + "</td></tr>"; // total entries
    }
    message += "</table></html>";
    JLabel tempLabel = new JLabel(message);
    if (parents)
      message = "<html><br><center>Open All Timings? (Must click yes to clear Timings)</center></html>";
    else
      message = "<html><br><center>Clear the timings?</center></html>";
    JLabel tempLabel2 = new JLabel(message);
    JPanel tempPanel = new JPanel();
    tempPanel.setLayout(new BoxLayout(tempPanel, BoxLayout.Y_AXIS));
    if (tempLabel.getPreferredSize().height > 600)
    {
      JScrollPane scrollPane = new JScrollPane(tempLabel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width > 800 ? 800 : scrollPane.getPreferredSize().width + 15, 600));
      tempPanel.add(scrollPane);
    }
    else
      tempPanel.add(tempLabel);
    tempPanel.add(tempLabel2);
    return tempPanel;
  }

  /*
   * (non-Javadoc)
   * Dynamically build the annotation types submenu for hide and show annotation
   * @see javax.swing.event.MenuListener#menuSelected(javax.swing.event.MenuEvent)
   */
  public void menuSelected(MenuEvent e)
  {
    JMenu showType = (JMenu)e.getSource();
    showType.removeAll();

    if (mapNavigator == null) return;

    String showHide = "";
    if (showType.getName().equals("annotShowType"))
      showHide = "showType";
    else
      showHide = "hideType";

    Vector<String> types = new Vector<String>();

    // Determine sets
    for (DisplayMap map : mapNavigator.getDisplayMaps())
      for (AnnotationSet set : map.getAllAnnotationSets())
        if (!types.contains(set.getType()))
          types.add(set.getType());

    // Add sets
    for (String type : types)
    {
      JMenuItem menuItem = new JMenuItem(type);
      menuItem.setActionCommand(showHide + type);
      menuItem.addActionListener(this);
      showType.add(menuItem);
    }

    if (types.size() == 0)
      showType.add(new JMenuItem("None Loaded"));
  }

  // Unused, but required
  public void menuDeselected(MenuEvent e) {}
  public void menuCanceled(MenuEvent e) {}

  /**
   * <p>Sets the percentage of {@link JProgressBar} in the the {@link ProgressPopup}.
   * Does this by dividing the number of lines read by the total number of lines in
   * the file.</p>
   *
   * @param lineNum
   *    the line of the file that is being parsed
   */
  public void setProgressPercentageDone(int lineNum)
  {
    progress.getProgressBar().setValue((int)(100 * ((double)lineNum/(double)totalLines)));
  }

  /**
   * <p>Ensures progress bar has been initialized before hiding it</p>
   */
  public void killProgressBar()
  {
    if (MainGUI.this.progress != null)
      MainGUI.this.progress.setVisible(false);
  }

  /**
   * <p>Starts the {@link LoadMapThread}</p>
   * @param bb
   *    true to load a backbone map, false to load off-backbone map
   * @param md
   *    the map to load
   * @param chr
   *    the chromosome to load.  Only necessary if loading a backbone map.
   * @param call
   *    the class that made the call to this function and will have
   *    its "callback" method called
   */
  public void loadMap(boolean bb, MapData md, String chr, MapLoader call)
  {
    new LoadMapThread(bb, md, chr, call).start();
  }

  /**
   * <p>Alternate loadAnnotation method with no {@link OntologyNode} filter.</p>
   * @param displayMap
   *  The map that the {@link Annotation} will be loaded to
   * @param set
   *  The {@link AnnotationSet} of the {@link Annotation} that will be added
   * @param callback
   *  The {@link AnnotationLoader} callback class
   */
  public void loadAnnotation(DisplayMap displayMap, AnnotationSet set, AnnotationLoader callback)
  {
    this.loadAnnotation(displayMap, set, null, callback);
  }

  /**
   * <p>Initializes a new {@link AnnotationLoaderThread} to load {@link Annotation} for a
   * {@link DislayMap}.</p>
   * @param displayMap
   *  The map that the {@link Annotation} will be loaded to
   * @param set
   *  The {@link AnnotationSet} of the {@link Annotation} that will be added
   * @param ontologyF
   *  The parent {@link OntologyNode} filter for the {@link Annotation}
   * @param callback
   *  The {@link AnnotationLoader} callback class
   */
  public void loadAnnotation(DisplayMap displayMap, AnnotationSet set, OntologyNode ontologyFilter, AnnotationLoader callback)
  {
    new AnnotationLoaderThread(displayMap, set, ontologyFilter, callback).start();
  }

  /**
   * <p>Initializes a new {@link CustomAnnotationLoaderThread} to load custom
   * {@link Annotation} from a file.</p>
   *
     * @param file
     *  the file that is being parsed by the {@link CustomAnnotationLoaderThread}
     * @param mapName
     *  the {@link DisplayMap} name in the form of a {@link String}
     * @param callback
     *  the original method that started this thread
   */
  public void loadCustomAnnotation(File file, String fileType, String mapName, CustomAnnotationLoader callback)
  {
    new CustomAnnotationLoaderThread(file, fileType, mapName, callback).start();
  }

  /**
   * <p>This class defines a separate {@link Thread} that can be used to load the
   * {@link MapData} objects into the {@link MainGUI} so that a progress bar can
   * still be rendered (on the EDT thread).</p>
   *
   * @author cgoodman
   * @author jaaseby
   *
   */
  class LoadMapThread
    extends Thread
  {
    private boolean backbone;
    private MapData map;
    private String chromosome;
    private MapLoader callback;

    /**
     * <p>Constructor to initilaize parameters for the {@link LoadMapThread}</p>
     * @param bb
     *    true to load a backbone map, false to load off-backbone map
     * @param md
     *    the map to load
     * @param chr
     *    the chromosome to load.  Only necessary if loading a backbone map.
     * @param call
     *    the class that made the call to this function and will have
     *    its "callback" method called
     */
    public LoadMapThread(boolean bb, MapData md, String chr, MapLoader call)
    {
      backbone = bb;
      map = md;
      chromosome = chr;
      callback = call;
    }

    /*
     * Implemented from java.lang.Thread.
     */
    public void run()
    {
      long start = System.nanoTime();
      //get the lock (mainly for pre-loading
      Lock lock = MainGUI.this.getMapNavigator().getLock();

      while(!lock.tryLock()){}
      MainGUI.this.setEnabled(false);
      lock.unlock();

      // Load the map
      if (backbone)
      {
        if (map == null)
        {
          callback.mapLoadCompleted(false, MainGUI.BACKBONE_ERROR, "The map you are trying to load was not found");
          return;
        }

        killProgressBar();
        ProgressPopup newProgress = new ProgressPopup(MainGUI.this, map.getName());
        progress = newProgress;

        logger.debug("Loading map as backbone.");
        int choice = 0;

        // Prompt the user that the following action will replace all loaded data
        if (MainGUI.this.isBackboneSet())
        {
          Object[] options = {"Continue",
                              "Cancel"};
          choice = JOptionPane.showOptionDialog(MainGUI.this,
              "Loading a new backbone will clear\n"
                + "all previously loaded data.",
              "Load Different Backbone",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.WARNING_MESSAGE,
              null,
              options,
              options[options.length-1]);
        }

        if (choice == 0)
        {
          // The user indicated it was okay to replace the loaded data
          // Load new backbone (start over)
          progress.setVisible(true);

          try
          {
            lock = MainGUI.this.getMapNavigator().getLock();

            while (!lock.tryLock()){lock.tryLock();}
            loadBackboneMap(map, chromosome);
            newProgress.setVisible(false);
            killProgressBar();

            // Finally update the Tutorial
            Tutorial.updatePage("backboneLoaded");
            callback.mapLoadCompleted(true, LOAD_SUCCESSFUL, "Loaded " + map.getName() + " successfully");
          }
          catch (SQLException e)
          {
            String text = "There was a problem while trying to communicate with the VCMap\n";
            text += "database.  Please try again later.\n\n";
            text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.</html>";
            String log = "MainGUI: Error loading backbone: " + e;
            ErrorReporter.handleMajorError(MainGUI.this, text, log);

            newProgress.setVisible(false);
            killProgressBar();
            callback.mapLoadCompleted(false, BACKBONE_ERROR, text);
          }
          catch (Exception e)
          {
            String text = "There was an unexpected error while trying load the " + map.getName() + " map.\n";
            text += "Please try again later.\n\n";
            text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
            String log = "MainGUI: Error loading backbone map: " + e;
            logger.error(text);
            VCMap.printStackTrace(e);
            ErrorReporter.handleMajorError(MainGUI.this, text, log);
            callback.mapLoadCompleted(false, BACKBONE_ERROR, text);

            newProgress.setVisible(false);
            killProgressBar();
          }
          finally
          {
            // Enable paint
            ReentrantLock rLock = (ReentrantLock)MainGUI.this.getMapNavigator().getLock();

            if (rLock.isHeldByCurrentThread())
              rLock.unlock();

            // This gets the mapNavigator to show new annotation
            MainGUI.this.getMapNavigator().updateSize();
            MainGUI.this.getMapNavigator().centerOnBackbone();

            newProgress.setVisible(false);
            killProgressBar();
          }
        }
        else
        {
          callback.mapLoadCompleted(false, LOAD_CANCELED, "User has chosen not to replace the backbone map.");
        }
      }
      else
      {
        logger.debug("User has chosen to load additional map.");

        if (map == null)
        {
          callback.mapLoadCompleted(false, MainGUI.SYNTENY_ERROR, "The map you are trying to load was not found");
          return;
        }

        killProgressBar();
        ProgressPopup newProgress = new ProgressPopup(MainGUI.this, map.getName());
        progress = newProgress;

        boolean alreadyLoaded = false;
        for (MapData oldMap : MainGUI.this.getMaps())
        {
          if (map.equals(oldMap))
          {
            alreadyLoaded = true;
          }
        }

        if (!alreadyLoaded)
        {
          progress.setVisible(true);

          try
          {
            lock = MainGUI.this.getMapNavigator().getLock();
            while (!lock.tryLock()){lock.tryLock();}
            loadOffBackboneMap(map);
            newProgress.setVisible(false);
            killProgressBar();


            // Update the Tutorial
            Tutorial.updatePage("mapLoaded");
            callback.mapLoadCompleted(true, LOAD_SUCCESSFUL, "Loaded " + map.getName() + " successfully");
          }
          catch (SQLException e)
          {
            String text = "There was a problem while trying to communicate with the VCMap\n";
            text += "database.  Please try again later.\n\n";
            text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
            String log = "MainGUI: Error loading additional map: " + e;
            ErrorReporter.handleMajorError(MainGUI.this, text, log);

            newProgress.setVisible(false);
            killProgressBar();
            callback.mapLoadCompleted(false, SQL_ERROR, text);
          }
          catch (Exception e)
          {
            newProgress.setVisible(false);
            killProgressBar();

            if ("No Synteny".equals(e.getMessage()))
            {
              String text = null;

              if (callback instanceof SwapMapDialog)
                text = map.getName();
              else
                text = "No syntenic regions can be found for the "+ map.getName() +"\n" +
                  "map given the backbone map that is currently loaded.  This may exist,\n" +
                  "but is not loaded into the VCMap database.\n\n" +
                  "Please try a different map.";
              callback.mapLoadCompleted(false, SYNTENY_ERROR, text);
            }
            else
            {
              String text = "There was an unexpected error while trying load the " + map.getName() + " map.\n";
              text += "Please try again later.\n\n";
              text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
              String log = "MainGUI: Error loading synteny for an additional map: " + e;
              logger.error(text);
              VCMap.printStackTrace(e);
              ErrorReporter.handleMajorError(MainGUI.this, text, log);
              callback.mapLoadCompleted(false, SYNTENY_ERROR, text);
            }

          }
          finally
          {
            // Enable paint
            ReentrantLock rLock = (ReentrantLock)MainGUI.this.getMapNavigator().getLock();

            if (rLock.isHeldByCurrentThread())
              rLock.unlock();

            newProgress.setVisible(false);
            killProgressBar();
            // This gets the mapNavigator to show new annotation
            MainGUI.this.getMapNavigator().updateSize();
            MainGUI.this.getMapNavigator().centerOnBackbone();
          }
        }
        else
        {
          String text = "The \"" + map.getName() + "\""
              + " map has already been loaded. \nIf you do not"
              + " currently see the map, it may be hidden.";

          callback.mapLoadCompleted(false, ALREADY_LOADED, text);
        }
      }
      MainGUI.this.setEnabled(true);
      getVCMap().addTiming("MainGUI - Load Map", System.nanoTime() - start);
    }

    /**
     * <p>Load a backbone {@link MapData} object into memory.</p>
     *
     * @param map
     *   The MapData object to load as the backbone map in memory.
     * @param chromosome
     *   The name of the chromosome to use as the backbone.
     * @throws SQLException
     *   If there is a problem connecting with the VCMap server, an exception
     *   will be thrown
     */
    public void loadBackboneMap(MapData map, String chromosome)
      throws SQLException
    {
      // Reset zoom level to zero
      zoomBar.setValue(0);

      // Disable backbone swap
      if (mapSwap != null) mapSwap.setEnabled(false);

      // Clear previous data
      map.clearChromosomes();
      map.clearSynteny();

      // Load Backbone map data
      MainGUI.this.backbone = map.getChromosome(chromosome);
      MainGUI.this.backbone.loadRegion(0, MainGUI.this.backbone.getLength());
      MainGUI.this.maps = new Vector<MapData>();
      MainGUI.this.maps.add(map);

      // Now handle the display data
      MainGUI.this.mapNavigator.loadBackboneMap();
    }

    /**
     * Load a {@link MapData} object into memory.  This method is only used to
     * load off-backbone maps.  To load a backbone map, use the
     * <code>loadMap(MapData, String)</code> version of this method specifying
     * the {@link Chromosome} name as the second parameter.
     *
     * @param map
     *   The MapData object to load into memory.
     * @throws Exception
     *   If there is a problem connecting with the VCMap server, an
     *   {@link SQLException} will be thrown. If there a problem with synteny
     *   not loading properly, an special exception will be thrown.
     */
    public void loadOffBackboneMap(MapData map)
      throws Exception
    {
      logger.debug("MainGUI: Load map " + map.getName());

      // Clear any previous data
      map.clearChromosomes();
      map.clearSynteny();

      // First determine if we are loading a new map type for the same species
      // as the currently loaded Backbone Map.
      if (MainGUI.this.getBackbone().getMap().getSpecies().equals(map.getSpecies()))
      {
        logger.debug("MainGUI: Loading a new map type (" + map.getType() +
          ") for the backbone species: " + MainGUI.this.getBackbone().getMap().getSpecies());

        // We have to load a full chromosome for a new Map Type
        MainGUI.this.maps.addElement(map);
        MainGUI.this.getMapNavigator().loadMap(map);
        MainGUI.this.getMapNavigator().buildSiblings();

        // Enable backbone swap
        if (MainGUI.this.mapSwap != null)
          MainGUI.this.mapSwap.setEnabled(true);

        return;
      }

      // Now determine if we should load partial chromosomes with new map types
      // for one of the off backbone maps
      for (MapData loadedMap : MainGUI.this.getMaps())
      {
        if (loadedMap.getSpecies().equals(map.getSpecies()))
        {
          logger.debug("MainGUI: Loading a new map type (" + map.getType() +
            ") for the off-backbone species: " + loadedMap.getSpecies());
          MainGUI.this.maps.addElement(map);
          MainGUI.this.getMapNavigator().loadOffBackboneMap(map);

          // Enable backbone swap
          if (MainGUI.this.mapSwap != null)
            MainGUI.this.mapSwap.setEnabled(true);

          return;
        }
      }

      // Finally, we must be attempting to load syntenic regions, for a new
      // species that is not yet loaded in the interface
      Vector<SyntenyBlock> synteny = new Vector<SyntenyBlock>();
      long begin = System.nanoTime();
      if (!Factory.getSyntenyTest(getBackbone(), map))
        throw new Exception("No Synteny");
      synteny = map.getSynteny(getBackbone());
      logger.debug("Retrieved map synteny in: " + (System.nanoTime() - begin) + "ns");

      // Now check for errors with the synteny blocks
      int errorCount = 0;
      for (SyntenyBlock b : synteny)
        if (b == null)
          errorCount++;

      if (errorCount > 0)
      {
        String error = "There was a problem while loading the syntenic regions\n" +
          "of this map.\n\n" + errorCount + " syntenic region" + (errorCount > 1 ? "s" : "") +
          " had loading errors.\n\n" +
          "The remainder of the syntenic regions will be correctly\n" +
          "displayed, but please consider reporting this error so\n" +
          "that it can be corrected.";
        String log = "MainGUI: detected " + errorCount + " bad SyntenyBlocks";
        ErrorReporter.handleMajorError(MainGUI.this, error, log);
      }

      // Now actually load the syntenic regions
      if (synteny.size() == 0)
      {
        throw new Exception("No Synteny");
      }
      else
      {
        MainGUI.this.maps.addElement(map);
        MainGUI.this.getMapNavigator().loadMap(map, synteny);
        MainGUI.this.getMapNavigator().buildSiblings();

        // Enable backbone swap
        if (MainGUI.this.mapSwap != null)
          MainGUI.this.mapSwap.setEnabled(true);
      }
    }
  }

  /**
   * <p>Once the "loadAnnotation" method is called, this class is instantiated and
   * a background thread is started for annotation loading.</p>
   *
   * @author dquacken@bioneos.com
   * @author cgoodman@bioneos.com
   *
   */
  class AnnotationLoaderThread
    extends Thread
  {
    private DisplayMap displayMap;
    private AnnotationSet annotSet;
    private OntologyNode ontologyFilter;
    private AnnotationLoader callback;

    /**
     * <p>Constructor to initialize objects used in thread.</p>
     * @param displayMap
     *  The map that the {@link Annotation} will be loaded to
     * @param set
     *  The type of {@link Annotation} that will be added
     * @param ontologyF
     *  The parent {@link OntologyNode} filter for the {@link Annotation}
     * @param callback
     *  The {@link AnnotationLoader} callback class
     */
    public AnnotationLoaderThread(DisplayMap displayMap, AnnotationSet set, OntologyNode ontologyF, AnnotationLoader callback)
    {
      this.displayMap = displayMap;
      this.annotSet = set;
      this.ontologyFilter = ontologyF;
      this.callback = callback;
    }

    public void run()
    {
      try
      {
        // Count annotation added to see if we need to add an OntologyNode filter
        int annotAdded = 0;

        // Disable paint by acquiring the lock
        Lock lock = MainGUI.this.getMapNavigator().getLock();
        while (!lock.tryLock());

        progress = new ProgressPopup(MainGUI.this, "Loading " + annotSet + " annotation: ");
        progress.setVisible(true);
        MainGUI.this.setEnabled(false);

        SQLException sqle = null;
        HashSet<Chromosome> loadedChr = new HashSet<Chromosome>();
        for (DisplaySegment segment : displayMap.getSegments())
        {
          Chromosome chrom = segment.getChromosome();

          // Because of how annotation types are loaded by the chromosome
          // class, we only need to load annot for a chrom once
          if (loadedChr.contains(chrom))
            continue;
          else
            loadedChr.add(chrom);

          try
          {
            if (ontologyFilter != null)
            {
              annotAdded += chrom.loadAnnotation(annotSet, ontologyFilter);
            }
            else
            {
              if (Factory.getOntologyTree() != null)
              {
                // Ontology Filter is not null, so indicate that "All Data"
                // was loaded by passing the root of the tree
                // NOTE: This should be unnecessary and handled elsewhere...
                annotAdded += chrom.loadAnnotation(annotSet, Factory.getOntologyTree().getNodeByID(1));
              }
              else
              {
                annotAdded += chrom.loadAnnotation(annotSet, null);
              }
            }
          }
          catch (SQLException se)
          {
            StringBuilder log = new StringBuilder();
            log.append("There was a SQL error when loading annotation type: ");
            log.append(annotSet);
            log.append("\nFor chromosome: ");
            log.append(chrom.getName());
            log.append("\nSQL Error: ");
            log.append(se);

            logger.error(log.toString());

            sqle = se;
            killProgressBar();
          }
        }

        displayMap.addShownSet(annotSet);

        if (ontologyFilter != null && annotAdded > 0)
          displayMap.addToOntologyFilters(ontologyFilter);

        if (sqle != null)
        {
          // Database error!
          SwingUtilities.invokeLater(new Thread() { 
            public void run()
            {
              callback.annotationLoadCompleted(false, SQL_ERROR, displayMap.getMap().getName());
            }
          });
        }
        else if (annotAdded == 0)
        {
          // No data loaded (possibly map was already loaded)
          final String text = displayMap.getMap().getName() + " - " + annotSet + 
            (ontologyFilter != null ? ", " + ontologyFilter.getCategory() : "");

          SwingUtilities.invokeLater(new Thread() { 
            public void run()
            {
              callback.annotationLoadCompleted(false, NO_DATA_LOADED_ERROR, text);
            }  
          });
        }
        else
        {
          // Success!
          SwingUtilities.invokeLater(new Thread() { 
            public void run()
            {
              callback.annotationLoadCompleted(true, LOAD_SUCCESSFUL, "The " + annotSet + " was loaded successfully");
            }
          });
        }
      }
      finally
      {
        // This gets the mapNavigator to show new annotation
        MainGUI.this.getMapNavigator().updateSize();
        MainGUI.this.getMapNavigator().buildSiblings();
        killProgressBar();

        // enable paint
        ReentrantLock lock = (ReentrantLock)MainGUI.this.getMapNavigator().getLock();

        if (lock.isHeldByCurrentThread())
          lock.unlock();
        MainGUI.this.setEnabled(true);
      }
    }
  }

  /**
   * <p>This class defines a seperate {@link Thread} that is used to load custom user
   * data as {@link Annotation} objects so a progress bar can be rendered still (on
   * the EDT thread).</p>
   * @author cgoodman
   * <p>Created on: August 11, 2010</p>
   *
   */
  public class CustomAnnotationLoaderThread
    extends Thread
  {
    private DisplayMap displayMap;
    private String mapName;
    private File file;
    private String fileType;
    private CustomAnnotationLoader callback;

    /**
     * <p>Constructor for the {@link CustomAnnotationLoaderThread} that initializes class
     * variables.</p>
     *
     * @param file
     *  the file that is being parsed by the {@link CustomAnnotationLoaderThread}
     * @param mapName
     *  the {@link DisplayMap} name in the form of a {@link String}
     * @param callback
     *  the original method that started this thread
     */
    public CustomAnnotationLoaderThread(File file, String fileType, String mapName, CustomAnnotationLoader callback)
    {
      this.file = file;
      this.fileType = fileType;
      this.callback = callback;
      this.mapName = mapName;
    }

    /*
     * Implemented from java.lang.Thread.
     */
    public void run()
    {
      // create progress bar
      progress = new ProgressPopup(MainGUI.this, "Reading File", 0, 100);
      progress.getProgressBar().setValue(0);
      progress.setVisible(true);

      try
      {
        totalLines = countLines(file);
      }
      catch (IOException e) {}

      // Determine corresponding display map
      for (DisplayMap map : getMapNavigator().getDisplayMaps())
      {
        if (map.getMap().getName().equals(mapName))
        {
          displayMap = map;
          break;
        }
      }

      // Get annotation from the file
      Vector<Annotation> annotation = new Vector<Annotation>();
      FileParser parser = null;
      try
      {
        if (fileType.equals("GFF3"))
          parser = new GFF3Parser();
        else if (fileType.equals("BED"))
          parser = new BEDParser();
        else if (fileType.equals("SAM/BAM"))
          parser = new BAMParser();
        else if (fileType.equals("VCF"))
          parser = new VCFParser();
        annotation = parser.parseFile(file, displayMap, MainGUI.this);
        if (annotation == null) annotation = new Vector<Annotation>();
      }
      catch (OutOfMemoryError e)
      {
        callback.customAnnotationLoadCompleted(false, OUT_OF_MEMORY_ERROR, e.toString());
        killProgressBar();
        return;
      }
      catch (SQLException e)
      {
        callback.customAnnotationLoadCompleted(false, SQL_ERROR, e.toString());
        killProgressBar();
        return;
      }
      catch (Exception e)
      {
        callback.customAnnotationLoadCompleted(false, FileParser.GENERAL_ERROR, e.toString());
        killProgressBar();
        return;
      }
      boolean noLoading = true;
      for (Annotation annot : annotation)
        for (DisplayMap map : getMapNavigator().getDisplayMaps())
          if (map.getMap().getLoadedChromosomes().contains(annot.getChromosome()))
            noLoading = false;

      if (annotation.size() == 0 || noLoading)
      {
        // Add file format errors and parser errors
        if (parser.hasError(new Integer(FileParser.FILE_FORMAT_ERROR)))
        {
          callback.customAnnotationLoadCompleted(false, FileParser.FILE_FORMAT_ERROR,
              "There is an error in the file you are attempting to load:\n\t" +
              parser.getErrorString() + "\n\nPlease check the file to ensure it doesn't " +
              		"contain additional errors.");
        }
        else if (parser.hasError(new Integer(FileParser.INVALID_FORMAT_ERROR)))
        {
          callback.customAnnotationLoadCompleted(false, FileParser.INVALID_FORMAT_ERROR,
              "There is an error in the file you are attempting to load:\n\t" +
              parser.getErrorString() + "\n\nPlease check the file to ensure it doesn't " +
                        "contain additional errors.");
        }
        else if (parser.hasError(new Integer(FileParser.MISSING_INFO_ERROR)))
        {
          callback.customAnnotationLoadCompleted(false, FileParser.MISSING_INFO_ERROR,
              "There is an error in the file you are attempting to load:\n\t" +
              parser.getErrorString() + "\n\nPlease check the file to ensure it doesn't " +
                        "contain additional errors.");
        }
        else if (noLoading)
        {
          callback.customAnnotationLoadCompleted(false, FileParser.NO_DATA_LOADED_ERROR,
              "None of the data in the file that you attempted to load matched the chromosomes\n" +
              "that are currently loaded.");
        }
        else
        {
          callback.customAnnotationLoadCompleted(false, FileParser.NO_DATA_LOADED_ERROR,
              "There are no features that match the selected map.\nThere may" +
              " be problems with matching the sequence ID to a chromosome.\n" +
              "Check that the first column is in the format \"chrXX\" or \"X\".");
        }

        killProgressBar();
        return;
      }
      else if (annotation.size() > CUSTOM_ANNOTATION_WARNING_LIMIT)
      {
        String msg = "The file you are attempting to load has defined " + annotation.size() + " annotations.\n" +
          "This many features can take a long time to load and can\n" +
          "cause a significant slowdown in the operation of the tool.\n\n" +
          "Are you sure you want to continue?";
        int choice = JOptionPane.showConfirmDialog(MainGUI.this, msg, "Warning: Large number of annotations",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.NO_OPTION)
          annotation = new Vector<Annotation>();
        progress.getProgressBar().setValue(0);
      }

      Lock lock = MainGUI.this.getMapNavigator().getLock();
      while (!lock.tryLock());

      int annotAdded = 0;
      progress.getProgressBar().setMaximum(annotation.size());
      progress.setName("Loading Annotations into Program");
      // Load each type of annotation for each chromosome
      for (Annotation annot : annotation)
      {
        if (annot.getChromosome().loadAnnotation(annot))
        {
          // TODO -- eventually it would be nice to support ontology filters on
          //   custom datasets here.
          if (annot.getAnnotationSet().getType().equals("QTL"))
            displayMap.addToOntologyFilters(null);

          displayMap.addShownSet(annot.getAnnotationSet());
          annotAdded++;
          progress.getProgressBar().setValue(annotAdded);
        }
      }

      logger.debug("Loaded " + annotAdded + " annotation objects");

      // enable paint
      ReentrantLock reLock = (ReentrantLock)MainGUI.this.getMapNavigator().getLock();

      if (reLock.isHeldByCurrentThread())
        reLock.unlock();

      // This gets the mapNavigator to show new annotation
      MainGUI.this.getMapNavigator().updateSize();

      progress.setVisible(false);

      int error = LOAD_SUCCESSFUL;
      String message = "";

      if (parser.hasError(FileParser.MATCHING_ID_ERROR))
      {
        error = GFF3_FEATURE_ERROR;
        message +=
            "Some annotation has been loaded but there are multiple features\n" +
            "in the GFF3 file that have the same ID.\n" +
            "    The matching lines have been logged in the debug log.\n" +
            "    Check with the source of the data for solutions to these errors.\n\n";
      }
      if (parser.hasError(FileParser.FEATURE_MISSING_ERROR))
      {
        error = GFF3_FEATURE_ERROR;

        message += "The following parent features are referenced but do not exist in the file.\n";

        for (String id : ((GFF3Parser)parser).getMissingFeatures())
          message += "    " + id + "\n";
      }

      if (error == LOAD_SUCCESSFUL)
        message = "The custom annotation was loaded successfully";

      callback.customAnnotationLoadCompleted(true, error, message);
    }

    /**
     * <p>Counts the lines of the file being parsed.  This number is used
     * in rendering the ProgressPopup's percentage done.</p>
     * @param file
     *  the file whose length is being determined
     * @return
     *  the number of lines in a {@link File}
     * @throws IOException
     */
    public int countLines(File file)
      throws IOException
    {
      long time = System.nanoTime();

      FileReader fr = new FileReader(file);
      LineNumberReader lnr = new LineNumberReader(fr);

      int count = 0;
      while (lnr.readLine() != null)
        count++;

      lnr.close();
      fr.close();
      long timing = (System.nanoTime() - time);
      vcmap.addTiming("MainGUI - Count file lines", timing);

      //logger.debug("Counted " + count + " lines in " + (System.nanoTime() - time) + " ms");

      return count;
    }

  }
}
