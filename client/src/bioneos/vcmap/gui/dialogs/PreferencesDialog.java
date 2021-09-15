package bioneos.vcmap.gui.dialogs;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.options.Defaults;
import bioneos.vcmap.options.GlobalOptions;

/**
 * <p>This class displays a {@link JDialog} that displays the preferences a
 * user can choose from to customize some visual setting for how maps are
 * displayed</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby
 */

public class PreferencesDialog
  extends VCMDialog
  implements ActionListener, ItemListener, MouseListener, ChangeListener
{
  // Singleton design pattern
  private static HashMap<MainGUI, PreferencesDialog> instances = new HashMap<MainGUI, PreferencesDialog>();

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private VCMap vcmap;
  private MainGUI mainGUI;
  private GlobalOptions newOptions;
  private GlobalOptions oldOptions;

  // GUI Components
  private JComboBox featureDisplayType;
  private JComboBox showUnitsType;
  private JComboBox selectMarkerType;
  private JComboBox markerShownType;
  private JCheckBox adjConnections;
  private JCheckBox nonAdjConnections;
  private JCheckBox showConnections;
  private JCheckBox tutorialOnStartup;
  private JCheckBox enableLogging;
  private JSlider   freqUnitLabels;
  private JTextField logPath;

  private JLabel    currentColor;
  private JButton   browse;
  private JButton   accept;
  private JButton   restore;
  private JButton   cancel;

  // String Constants
  private static final String[] displayTypeStrings = {"Species Specific", "Common Name", "Homologene ID"};
  private static final String[] yesNoStrings = {"Yes", "No"};

  /**
   * <p>Constructor for {@link PreferencesDialog}. Creates
   * {@link PreferencesDialog} from the information in the {@link MapNavigator}
   * of the {@link MainGUI}. The constructor is private so that only this class
   * can create an instance of {@link PreferencesDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private PreferencesDialog(MainGUI parent)
  {
    super(parent, false);

    this.vcmap = parent.getVCMap();
    this.mainGUI = parent;

    newOptions = vcmap.getOptions();

    // Component setup
    featureDisplayType = new JComboBox(displayTypeStrings);
    featureDisplayType.addItemListener(this);

    showUnitsType = new JComboBox(yesNoStrings);
    showUnitsType.addItemListener(this);

    // Create a HashMap so we can determine the types in O(n) time
    // and without repeats
    HashMap<AnnotationSet, String> typesMap = new HashMap<AnnotationSet, String>();

    for (MapData map : mainGUI.getMaps())
      for (Chromosome chromosome : map.getLoadedChromosomes())
        for (Annotation annotation : chromosome.getAnnotation())
          typesMap.put(annotation.getAnnotationSet(), annotation.getAnnotationSet().getType());

    selectMarkerType = new JComboBox((String[])typesMap.values().toArray(new String[typesMap.size()]));
    selectMarkerType.addItemListener(this);

    markerShownType = new JComboBox(yesNoStrings);
    markerShownType.addItemListener(this);

    JLabel featureDisplayTypeL = new JLabel("Marker name displayed:");
    JLabel showUnitsTypeL = new JLabel("Show units:");
    JLabel selectMarkerTypeL = new JLabel("Marker type:");
    JLabel markerShownTypeL = new JLabel("Visible:");
    JLabel currentColorL = new JLabel("Color:");
    JLabel freqUnitLabelsL = new JLabel("Freq. of unit labels:");
    String msg = "<html>Enabling debug logging allows VCMap to write<br>"
      + "debug output to a file. In the event of an error, the <br>"
      + "debug log can be helpful in determining the cause <br>"
      + "of the error.</html>";
    JLabel loggingInfoL = new JLabel(msg);

    logPath = new JTextField();

    enableLogging = new JCheckBox("Enable Logging");
    enableLogging.addItemListener(this);

    adjConnections = new JCheckBox("Show connections between adjacent maps");
    adjConnections.addItemListener(this);

    nonAdjConnections = new JCheckBox("Show connections between non-adjacent maps");
    nonAdjConnections.addItemListener(this);

    showConnections = new JCheckBox("Show connections when no segments are selected");
    showConnections.addItemListener(this);

    tutorialOnStartup = new JCheckBox("Show tutorial on startup");
    tutorialOnStartup.addItemListener(this);

    currentColor = new JLabel(" ");
    currentColor.setOpaque(true);
    currentColor.setBackground(newOptions.getColor("unknown"));
    currentColor.setForeground(newOptions.getColor("unknown"));
    currentColor.addMouseListener(this);

    browse = new JButton("Browse");
    browse.addActionListener(this);
    browse.setEnabled(true);

    restore = new JButton("Restore Defaults");
    restore.addActionListener(this);
    restore.setEnabled(true);

    accept = new JButton("Accept");
    accept.addActionListener(this);
    accept.setEnabled(true);

    cancel = new JButton("Cancel");
    cancel.addActionListener(this);
    cancel.setEnabled(true);

    freqUnitLabels = new JSlider(JSlider.HORIZONTAL, 0, 18, 1);
    freqUnitLabels.addChangeListener(this);

    //////////
    // Component Layout
    //
    JTabbedPane tabs = new JTabbedPane();

    //////////
    // General tab
    SpringLayout spring = new SpringLayout();
    JPanel generalTab = new JPanel(new java.awt.BorderLayout());
    generalTab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    JPanel generalTabInner = new JPanel(spring);


    // Setup layout of General tab
    spring.putConstraint(SpringLayout.EAST, featureDisplayType, 0, SpringLayout.EAST, freqUnitLabels);
    spring.putConstraint(SpringLayout.NORTH, featureDisplayType, 0, SpringLayout.NORTH, generalTabInner);
    spring.putConstraint(SpringLayout.WEST, featureDisplayTypeL, 0, SpringLayout.WEST, generalTabInner);
    spring.putConstraint(SpringLayout.NORTH, featureDisplayTypeL, 2, SpringLayout.NORTH, featureDisplayType);
    spring.putConstraint(SpringLayout.EAST, showUnitsType, 0, SpringLayout.EAST, featureDisplayType);
    spring.putConstraint(SpringLayout.WEST, showUnitsType, 0, SpringLayout.WEST, featureDisplayType);
    spring.putConstraint(SpringLayout.NORTH, showUnitsType, 2, SpringLayout.SOUTH, featureDisplayType);
    spring.putConstraint(SpringLayout.WEST, showUnitsTypeL, 0, SpringLayout.WEST, featureDisplayTypeL);
    spring.putConstraint(SpringLayout.NORTH, showUnitsTypeL, 2, SpringLayout.NORTH, showUnitsType);
    spring.putConstraint(SpringLayout.WEST, freqUnitLabelsL, 0, SpringLayout.WEST, showUnitsTypeL);
    spring.putConstraint(SpringLayout.SOUTH, freqUnitLabelsL, 0, SpringLayout.SOUTH, freqUnitLabels);
    spring.putConstraint(SpringLayout.WEST, freqUnitLabels, 5, SpringLayout.EAST, freqUnitLabelsL);
    spring.putConstraint(SpringLayout.NORTH, freqUnitLabels, 2, SpringLayout.SOUTH, showUnitsType);
    spring.putConstraint(SpringLayout.EAST, generalTabInner, 0, SpringLayout.EAST, freqUnitLabels);
    spring.putConstraint(SpringLayout.SOUTH, generalTabInner, 0, SpringLayout.SOUTH, freqUnitLabels);

    // Add components to General tab
    generalTabInner.add(featureDisplayTypeL);
    generalTabInner.add(featureDisplayType);
    generalTabInner.add(showUnitsTypeL);
    generalTabInner.add(showUnitsType);
    generalTabInner.add(freqUnitLabels);
    generalTabInner.add(freqUnitLabelsL);
    generalTab.add(generalTabInner, "North");

    //////////
    // Marker tab
    spring = new SpringLayout();
    JPanel markersTab = new JPanel(spring);
    markersTab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Setup layout of Markers tab
    spring.putConstraint(SpringLayout.WEST, selectMarkerTypeL, 0, SpringLayout.WEST, markersTab);
    spring.putConstraint(SpringLayout.NORTH, selectMarkerTypeL, 2, SpringLayout.NORTH, selectMarkerType);
    spring.putConstraint(SpringLayout.EAST, selectMarkerType, 0, SpringLayout.EAST, markersTab);
    spring.putConstraint(SpringLayout.NORTH, selectMarkerType, 0, SpringLayout.NORTH, markersTab);
    spring.putConstraint(SpringLayout.EAST, markerShownType, 0, SpringLayout.EAST, selectMarkerType);
    spring.putConstraint(SpringLayout.WEST, markerShownType, 0, SpringLayout.WEST, selectMarkerType);
    spring.putConstraint(SpringLayout.NORTH, markerShownType, 2, SpringLayout.SOUTH, selectMarkerType);
    spring.putConstraint(SpringLayout.NORTH, markerShownTypeL, 2, SpringLayout.NORTH, markerShownType);
    spring.putConstraint(SpringLayout.WEST, markerShownTypeL, 0, SpringLayout.WEST, selectMarkerTypeL);
    spring.putConstraint(SpringLayout.NORTH, currentColor, 2, SpringLayout.SOUTH, markerShownType);
    spring.putConstraint(SpringLayout.EAST, currentColor, 0, SpringLayout.EAST, selectMarkerType);
    spring.putConstraint(SpringLayout.WEST, currentColor, 0, SpringLayout.WEST, selectMarkerType);
    spring.putConstraint(SpringLayout.NORTH, currentColorL, 0, SpringLayout.NORTH, currentColor);
    spring.putConstraint(SpringLayout.WEST, currentColorL, 0, SpringLayout.WEST, selectMarkerTypeL);
    //spring.putConstraint(SpringLayout.EAST, markersTab, 0, SpringLayout.EAST, );
    //spring.putConstraint(SpringLayout.SOUTH, markersTab, 0, SpringLayout.SOUTH, currentColor);

    // Add components to Markers tab
    markersTab.add(selectMarkerTypeL);
    markersTab.add(selectMarkerType);
    markersTab.add(markerShownType);
    markersTab.add(markerShownTypeL);
    markersTab.add(currentColor);
    markersTab.add(currentColorL);

    //////////
    // Connections tab
    JPanel connectionsTab = new JPanel();
    connectionsTab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    connectionsTab.setLayout(new BoxLayout(connectionsTab, BoxLayout.Y_AXIS));
    connectionsTab.add(adjConnections);
    connectionsTab.add(Box.createVerticalStrut(2));
    connectionsTab.add(nonAdjConnections);
    connectionsTab.add(Box.createVerticalStrut(2));
    connectionsTab.add(showConnections);
    connectionsTab.add(Box.createVerticalGlue());

    //////////
    // Tutorial tab
    JPanel tutorialTab = new JPanel();
    tutorialTab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    tutorialTab.setLayout(new BoxLayout(tutorialTab, BoxLayout.Y_AXIS));
    tutorialTab.add(tutorialOnStartup);
    tutorialTab.add(Box.createVerticalGlue());

    //////////
    // Logging tab
    spring = new SpringLayout();
    JPanel loggingTab = new JPanel(new java.awt.BorderLayout());
    JPanel loggingTabInner = new JPanel(spring);
    loggingTab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // Setup layout of Logging tab
    spring.putConstraint(SpringLayout.WEST, loggingInfoL, 0, SpringLayout.WEST, loggingTabInner);
    spring.putConstraint(SpringLayout.NORTH, loggingInfoL, 0, SpringLayout.NORTH, loggingTabInner);
    spring.putConstraint(SpringLayout.NORTH, enableLogging, 5, SpringLayout.SOUTH, loggingInfoL);
    spring.putConstraint(SpringLayout.WEST, enableLogging, 0, SpringLayout.WEST, loggingInfoL);
    spring.putConstraint(SpringLayout.EAST, logPath, 0, SpringLayout.EAST, loggingTabInner);
    spring.putConstraint(SpringLayout.WEST, logPath, 0, SpringLayout.WEST, loggingTabInner);
    spring.putConstraint(SpringLayout.NORTH, logPath, 5, SpringLayout.SOUTH, enableLogging);
    spring.putConstraint(SpringLayout.EAST, browse, 0, SpringLayout.EAST, logPath);
    spring.putConstraint(SpringLayout.NORTH, browse, 5, SpringLayout.SOUTH, logPath);
    spring.putConstraint(SpringLayout.SOUTH, loggingTabInner, 0, SpringLayout.SOUTH, browse);

    // Add components
    loggingTabInner.add(loggingInfoL);
    loggingTabInner.add(enableLogging);
    loggingTabInner.add(logPath);
    loggingTabInner.add(browse);
    loggingTab.add(loggingTabInner, "North");

    //////////
    // Main Panel
    //
    spring = new SpringLayout();
    JPanel main = new JPanel(spring);
    main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    tabs.addTab("General", generalTab);
    tabs.addTab("Markers", markersTab);
    tabs.addTab("Connections", connectionsTab);
    tabs.addTab("Tutorial", tutorialTab);
    tabs.addTab("Logging", loggingTab);

    // Setup layout of Main panel
    spring.putConstraint(SpringLayout.WEST, tabs, 0, SpringLayout.WEST, main);
    spring.putConstraint(SpringLayout.NORTH, tabs, 0, SpringLayout.NORTH, main);

    spring.putConstraint(SpringLayout.NORTH, cancel, 5, SpringLayout.SOUTH, tabs);
    spring.putConstraint(SpringLayout.EAST, cancel, -2, SpringLayout.EAST, main);
    spring.putConstraint(SpringLayout.SOUTH, accept, 0, SpringLayout.SOUTH, cancel);
    spring.putConstraint(SpringLayout.EAST, accept, -5, SpringLayout.WEST, cancel);
    spring.putConstraint(SpringLayout.SOUTH, restore, 0, SpringLayout.SOUTH, cancel);
    spring.putConstraint(SpringLayout.WEST, restore, 2, SpringLayout.WEST, main);
    spring.putConstraint(SpringLayout.EAST, main, 0, SpringLayout.EAST, tabs);
    spring.putConstraint(SpringLayout.SOUTH, main, 0, SpringLayout.SOUTH, cancel);

    // Add Components to Main layout
    main.add(tabs);
    main.add(restore);
    main.add(accept);
    main.add(cancel);
    setContentPane(main);

    //////////
    // Final setup
    //
    setTitle("Preferences");
    setResizable(false);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    pack();
  }

  /**
   * <p>Show the instance of {@link PreferencesDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link PreferencesDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link PreferencesDialog}
   */
  public static void showPreferencesDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new PreferencesDialog(parent));
    PreferencesDialog instance = instances.get(parent);

    instance.updateOptions();
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

  /**
   * <p>Removes the instance of the {@link PreferencesDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link PreferencesDialog}
   */
  public static void closePreferencesDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>This sets up all the components in the {@link PreferencesDialog} so the
   * {@link JDialog} is properly displayed everytime the it is opened.</p>
   *
   */
  public void setupComponents()
  {
    // Different types of annotation
    Vector<String> annotVers = new Vector<String>();

    for (MapData map : mainGUI.getMaps())
      for (Chromosome chromosome : map.getLoadedChromosomes())
        for (AnnotationSet set : chromosome.getAnnotationSets())
          if (!annotVers.contains(set.getType()))
            annotVers.add(set.toString());

    selectMarkerType.removeAllItems();

    if (annotVers.size() == 0)
      annotVers.add("     ");

    for (String type : annotVers)
      selectMarkerType.addItem(type);

    if (newOptions.isShown("units"))
      showUnitsType.setSelectedIndex(0);
    else
      showUnitsType.setSelectedIndex(1);

    if (newOptions.getBooleanOption("DebugEnabled"))
    {
      enableLogging.setSelected(true);
      logPath.setEnabled(true);
      browse.setEnabled(true);
    }
    else
    {
      enableLogging.setSelected(false);
      logPath.setEnabled(false);
      browse.setEnabled(false);
    }
    logPath.setText(newOptions.getStringOption("DebugFile"));

    adjConnections.setSelected(newOptions.isShown("adjConnections"));
    nonAdjConnections.setSelected(newOptions.isShown("nonAdjConnections"));
    showConnections.setSelected(newOptions.isShown("showConnections"));
    tutorialOnStartup.setSelected(newOptions.getBooleanOption("showTutorial", true));

    // Special case for the homologene id
    int fdtIndex = newOptions.getIntOption("featureDisplayType");
    if (fdtIndex == -1) fdtIndex = 2;
    featureDisplayType.setSelectedIndex(fdtIndex);
    freqUnitLabels.setValue(newOptions.getIntOption("freqUnitLabels"));
  }

  /**
   * <p>Gets the {@link GlobalOptions} from the {@link MainGUI}, so the user
   * can modify the preferences in the {@link PreferencesDialog}</p>
   *
   */
  public void updateOptions()
  {
    newOptions = vcmap.getOptions();
    oldOptions = (GlobalOptions)newOptions.clone();
  }

  /*
   * Overridden to ensure that when the PreferencesDialog is visible, the parent
   * is no longer enabled, but whenever the PreferencesDialog is hidden, the parent
   * is enabled again.
   */
  public void setVisible(boolean b)
  {
    super.setVisible(b);

    if (!b) vcmap.setOptions(oldOptions);
  }

  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand().equals("Accept"))
    {
      logger.debug("Accept button was pressed");
      // Change logging path
      if (enableLogging.isSelected())
        newOptions.setOption("DebugFile", logPath.getText());

      oldOptions = newOptions;
      // Change preferences in all open windows

      mainGUI.repaint();

      // Close window
      setVisible(false);
    }
    else if (ae.getActionCommand().equals("Restore Defaults"))
    {
      // restore all the defaults for the program
      logger.debug("Restore Defaults button was pressed");
      Defaults.restoreDefaults(newOptions);

      setupComponents();

      vcmap.setOptions(newOptions);
    }
    else if (ae.getActionCommand().equals("Cancel"))
    {
      // Close window
      setVisible(false);

      // NOTE old options restored in setVisible()
    }
    else if(ae.getActionCommand().equals("Browse"))
    {
      File debug = null ;
      String path = (logPath.getText().equals("")) ? System.getProperty("user.dir", "") : logPath.getText() ;
      if(System.getProperty("os.name").toLowerCase().indexOf("mac") != -1)
      {
        // If a mac, see if we are in GANT.app, if so, open filechooser outside the .app
        // This is kind of a hack, but the best we can do for this situation
        File tmp = new File(path) ;
        if(tmp.getParentFile().getParentFile().getParentFile().getPath().endsWith("GANT.app"))
        {
          path = tmp.getParentFile().getParentFile().getParentFile().getParent() ;
        }
      }

      JFileChooser fc = new JFileChooser(path);
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
      fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      fc.addChoosableFileFilter(new javax.swing.filechooser.FileFilter()
        {
          public boolean accept(File f)
          {
            return f.isDirectory() || f.getName().endsWith(".log");
          }

          public String getDescription()
          {
            return "Log files (*.log)";
          }
        });
      fc.setDialogTitle("Select the location for Debug Log...");

      if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
      {
        if (fc.getSelectedFile().getParentFile().canWrite() && !fc.getSelectedFile().isDirectory())
          debug = fc.getSelectedFile();

        if (debug == null)
        {
          logPath.setText("");
          logPath.setToolTipText("");
        }
        else
        {
          if (!debug.getAbsolutePath().endsWith(".log"))
          {
            logPath.setText(debug.getAbsolutePath() + ".log");
            logPath.setToolTipText(debug.getAbsolutePath() + ".log");
          }
          else
          {
            logPath.setText(debug.getAbsolutePath());
            logPath.setToolTipText(debug.getAbsolutePath());
          }
        }
      }
    }
  }

  public void stateChanged(ChangeEvent e)
  {
    if (e.getSource() == freqUnitLabels)
    {
      if (!freqUnitLabels.getValueIsAdjusting())
      {
        newOptions.setOption("freqUnitLabels", new Integer(freqUnitLabels.getValue()));

        vcmap.setOptions(newOptions);
      }
    }
  }

  public void itemStateChanged(ItemEvent ie)
  {
    if (ie.getSource() == featureDisplayType)
    {
      int index = featureDisplayType.getSelectedIndex();

      // Special case for homologene id
      if (index == 2) index = -1;

      newOptions.setOption("featureDisplayType", new Integer(index));
    }
    else if (ie.getSource() == enableLogging)
    {
      if (enableLogging.isSelected())
      {
        newOptions.setOption("DebugEnabled", new Boolean(true));
        logPath.setEnabled(true);
        browse.setEnabled(true);
      }
      else
      {
        newOptions.setOption("DebugEnabled", new Boolean(false));
        logPath.setEnabled(false);
        browse.setEnabled(false);
      }
    }
    else if (ie.getSource() == showUnitsType)
    {
      if (showUnitsType.getSelectedIndex() == 0)
        newOptions.setOption("shown_units", new Boolean(true));
      else
        newOptions.setOption("shown_units", new Boolean(false));
    }
    else if (ie.getSource() == selectMarkerType)
    {
      String markerType = (String)selectMarkerType.getSelectedItem();

      if (markerType != null)
      {
        // Set shown combo box
        if (newOptions.isShown(markerType))
          markerShownType.setSelectedIndex(0);
        else
          markerShownType.setSelectedIndex(1);

        // Set color label
        currentColor.setBackground(newOptions.getColor(markerType));
        currentColor.setForeground(newOptions.getColor(markerType));
      }
    }
    else if (ie.getSource() == markerShownType)
    {
      if (selectMarkerType.getSelectedItem() != null)
      {
        if (markerShownType.getSelectedIndex() == 0)
          newOptions.setOption("shown_" + ((String)selectMarkerType.getSelectedItem()).toLowerCase(), true);
        else
          newOptions.setOption("shown_" + ((String)selectMarkerType.getSelectedItem()).toLowerCase(), false);
      }
    }
    else if (ie.getSource() == adjConnections)
    {
      newOptions.setOption("shown_adjConnections".toLowerCase(), new Boolean(adjConnections.isSelected()));
    }
    else if (ie.getSource() == nonAdjConnections)
    {
      newOptions.setOption("shown_nonAdjConnections".toLowerCase(), new Boolean(nonAdjConnections.isSelected()));
    }
    else if (ie.getSource() == showConnections)
    {
      newOptions.setOption("shown_showConnections".toLowerCase(), new Boolean(showConnections.isSelected()));
    }
    else if (ie.getSource() == tutorialOnStartup)
    {
      newOptions.setOption("showTutorial", new Boolean(tutorialOnStartup.isSelected()));
    }

    vcmap.setOptions(newOptions);
  }

  public void mouseClicked(MouseEvent e)
  {
    if (e.getSource() == currentColor)
    {
      // Get current color to display in color chooser dialog
      logger.debug( "Change Color button for a marker was pressed");
      int ccIndex = selectMarkerType.getSelectedIndex();

      Object cc = selectMarkerType.getItemAt(ccIndex);
      Color newColor = JColorChooser.showDialog(
          PreferencesDialog.this,
          "Choose " +  cc + " Marker Color",
          currentColor.getBackground());

      if (newColor != null)
      {
        logger.debug( "The color chosen was: " + newColor.toString() );
        currentColor.setBackground(newColor);
        currentColor.setForeground(newColor);

        newOptions.setOption((String)selectMarkerType.getSelectedItem(), newColor);

        vcmap.setOptions(newOptions);
      }
      else
      {
        logger.debug("No new color was chosen");
      }
    }
  }

  // Unused methods, but required from interfaces
  public void mousePressed(MouseEvent e) {}
  public void mouseReleased(MouseEvent e) {}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) {}
}
