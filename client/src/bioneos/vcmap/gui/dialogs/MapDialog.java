package bioneos.vcmap.gui.dialogs;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.MapLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.model.Factory;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.comparators.ChromosomeComparator;

/**
 * <p>This class displays a {@link JDialog} that displays options a user can
 * choose from to load a specific map as a backbone map or a comparative map.
 * </p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby
 */
public class MapDialog
  extends VCMDialog
  implements ActionListener, ItemListener, MapLoader
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  // Singleton design pattern
  private static HashMap<MainGUI, MapDialog> instances = new HashMap<MainGUI, MapDialog>();

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // Parent
  private MainGUI mainGUI;

  // GUI Components
  private JComboBox species;
  private JComboBox mapType;
  private JComboBox release;
  private JCheckBox backbone;
  private JLabel lChromosome;
  private JComboBox chromosome;
  private JButton load;
  private JButton cancel;
  private JPanel main;

  // Data
  private Vector<MapData> data = null;
  private static UpdateData update;

  /**
   * <p>Constructor for {@link MapDialog}. Creates {@link MapDialog}
   * from the information in the {@link MapNavigator} of the {@link MainGUI}.
   * The constructor is private so that only this class can create an instance
   * of {@link MapDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private MapDialog(MainGUI parent)
  {
    super(parent, false);
    mainGUI = parent;

    // Component setup
    species = new JComboBox(new String[] {"Please wait..."});
    species.setEnabled(false);
    species.addItemListener(this);
    mapType = new JComboBox();
    mapType.setEnabled(false);
    mapType.addItemListener(this);
    release = new JComboBox();
    release.setEnabled(false);
    release.addItemListener(this);
    backbone = new JCheckBox("Use this map as the Backbone");
    backbone.setSelected(mainGUI.getBackbone() == null);
    backbone.addItemListener(this);
    chromosome = new JComboBox();
    chromosome.setEnabled(mainGUI.getBackbone() == null);
    chromosome.addItemListener(this);
    JLabel speciesL = new JLabel("Choose a Species:");
    JLabel mapTypeL = new JLabel("Choose a Type of Map:");
    JLabel releaseL = new JLabel("Choose a Map Release:");
    lChromosome = new JLabel("Chromosome: ");

    load = new JButton("Load");
    load.addActionListener(this);
    load.setEnabled(false);

    cancel = new JButton("Cancel");
    cancel.addActionListener(this);
    cancel.setEnabled(true);

    // Component Layout
    SpringLayout s = new SpringLayout();
    main = new JPanel(s);
    s.putConstraint(SpringLayout.NORTH, speciesL, 5, SpringLayout.NORTH, main);
    s.putConstraint(SpringLayout.WEST, speciesL, 5, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.NORTH, mapTypeL, 10, SpringLayout.SOUTH, speciesL);
    s.putConstraint(SpringLayout.WEST, mapTypeL, 0, SpringLayout.WEST, speciesL);
    s.putConstraint(SpringLayout.NORTH, releaseL, 10, SpringLayout.SOUTH, mapTypeL);
    s.putConstraint(SpringLayout.WEST, releaseL, 0, SpringLayout.WEST, speciesL);
    s.putConstraint(SpringLayout.SOUTH, species, 5, SpringLayout.SOUTH, speciesL);
    s.putConstraint(SpringLayout.WEST, species, 15, SpringLayout.EAST, releaseL);
    s.putConstraint(SpringLayout.SOUTH, mapType, 5, SpringLayout.SOUTH, mapTypeL);
    s.putConstraint(SpringLayout.EAST, mapType, 0, SpringLayout.EAST, species);
    s.putConstraint(SpringLayout.WEST, mapType, 0, SpringLayout.WEST, species);
    s.putConstraint(SpringLayout.SOUTH, release, 5, SpringLayout.SOUTH, releaseL);
    s.putConstraint(SpringLayout.EAST, release, 0, SpringLayout.EAST, species);
    s.putConstraint(SpringLayout.WEST, release, 0, SpringLayout.WEST, species);
    s.putConstraint(SpringLayout.NORTH, backbone, 15, SpringLayout.SOUTH, releaseL);
    s.putConstraint(SpringLayout.WEST, backbone, 0, SpringLayout.WEST, releaseL);

    s.putConstraint(SpringLayout.NORTH, lChromosome, 10, SpringLayout.SOUTH, backbone);
    s.putConstraint(SpringLayout.WEST, lChromosome, 15, SpringLayout.WEST, backbone);
    s.putConstraint(SpringLayout.SOUTH, chromosome, 5, SpringLayout.SOUTH, lChromosome);
    s.putConstraint(SpringLayout.WEST, chromosome, 15, SpringLayout.EAST, lChromosome);

    s.putConstraint(SpringLayout.NORTH, cancel, 20, SpringLayout.SOUTH, chromosome);
    s.putConstraint(SpringLayout.EAST, cancel, 0, SpringLayout.EAST, species);
    s.putConstraint(SpringLayout.NORTH, load, 0, SpringLayout.NORTH, cancel);
    s.putConstraint(SpringLayout.EAST, load, -5, SpringLayout.WEST, cancel);
    s.putConstraint(SpringLayout.SOUTH, main, 5, SpringLayout.SOUTH, cancel);
    s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, cancel);
    main.add(speciesL);
    main.add(mapTypeL);
    main.add(releaseL);
    main.add(species);
    main.add(mapType);
    main.add(release);
    main.add(backbone);
    main.add(cancel);
    main.add(load);
    main.add(lChromosome);
    main.add(chromosome);
    setContentPane(main);

    setName("MapDialog");

    // Final setup
    setTitle("Load a Map");
    setResizable(true);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    update = new UpdateData();
    update.setPriority(Thread.MAX_PRIORITY);
    SwingUtilities.invokeLater(update);
  }

  /**
   * Display the {@link MapData} loading {@link JDialog}.
   *
   * @param parent
   *   The parent {@link JFrame} for the modal {@link JDialog}.
   */
  public static void showMapDialog(MainGUI parent)
  {
    // Create the dialog (if needed)
    if (instances.get(parent) == null)
      instances.put(parent, new MapDialog(parent));
    MapDialog instance = instances.get(parent);

    // Setup components
    instance.setupComponents();

    // Center the dialog
    instance.pack();
    Point center = parent.getLocation();
    center.x += parent.getWidth() / 2;
    center.y += parent.getHeight() / 2;
    center.x -= instance.getWidth() / 2;
    center.y -= instance.getHeight() / 2;
    if (center.x < 0) center.x = 0;
    if (center.y < 0) center.y = 0;

    instance.setLocation(center);
    instance.toFront();
    instance.setVisible(true);
  }

  /**
   * <p>Removes the instance of the {@link MapDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link MapDialog}
   */
  public static void closeMapDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Setup the different components of the {@link MapDialog} so it is
   * displayed properly upon opening.</p>
   *
   */
  public void setupComponents()
  {
    if (species.getItemCount() > 1)
    {
      species.setSelectedIndex(0);
    }
    if (mainGUI.isBackboneSet())
    {
      backbone.setSelected(false);
    }

    enableAppropriateInputs();
    main.revalidate();
    main.repaint();
    pack();
  }

  /**
   * <p>Helper method to disable all the Swing components.</p>
   *
   */
  public void disableAllInputs()
  {
    load.setEnabled(false);
    species.setEnabled(false);
    mapType.setEnabled(false);
    release.setEnabled(false);
    backbone.setEnabled(false);
    chromosome.setEnabled(false);
    cancel.setEnabled(false);
  }

  /**
   * <p>Helper method to renable Swing components based on their current values.</p>
   *
   */
  public void enableAppropriateInputs()
  {
    cancel.setEnabled(true);

    if (species.getItemCount() > 1)
    {
      species.setEnabled(true);
      backbone.setEnabled(true);
    }
    if (species.getSelectedIndex() > 0)
    {
      mapType.setEnabled(true);
    }
    if (mapType.getSelectedIndex() > 0)
    {
      release.setEnabled(true);
    }
    if (!backbone.isSelected())
    {
      chromosome.setEnabled(false);
    }
    else
    {
      chromosome.setEnabled(true);
    }
    if ((!backbone.isSelected() && release.getSelectedIndex() > 0) || (backbone.isSelected() && chromosome.getSelectedIndex() > 0))
    {
      load.setEnabled(true);
    }
  }

  /**
   * DOC
   *
   * @return
   */
  public boolean checkForExit()
  {
    if (mainGUI.isVisible())
      return true;
    else
      return false;
  }

  /**
   * DOC
   *
   * @return
   */
  public static boolean isInitialized()
  {
    if (update.isAlive())
      return false;
    else
      return true;
  }

  /*
   * Implemented from java.awt.ActionListener interface.
   */
  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand().equals("Cancel"))
    {
      setVisible(false);
    }
    else if (ae.getActionCommand().equals("Load"))
    {
      logger.debug("Load button pressed");

      MapData newMap = null;
      for (MapData m : data)
      {
        if (m.getSpecies().equals(species.getSelectedItem()) &&
            m.getTypeString().equals(mapType.getSelectedItem()) &&
            m.getVersion().equals(release.getSelectedItem()))
          newMap = m;
      }

      setVisible(false);
      mainGUI.loadMap(backbone.isSelected(), newMap, (String)chromosome.getSelectedItem(), this);
    }
  }

  /*
   * Implemented from ItemListener interface.
   */
  public void itemStateChanged(ItemEvent ie)
  {
    if (data == null) return;

    // Handle the backbone options
    if (ie.getSource().equals(backbone))
    {
      // Force the backbone / chromosome selection when nothing is loaded
      if (mainGUI.getBackbone() == null) backbone.setSelected(true);

      // Enable / disable the chromosome drop down
      if (backbone.isSelected())
      {
        chromosome.setEnabled(true);
        lChromosome.setEnabled(true);
      }
      else
      {
        chromosome.setEnabled(false);
        lChromosome.setEnabled(false);
        chromosome.setSelectedIndex(0);
      }

      // Handle the load button
      load.setEnabled((release.getSelectedIndex() > 0 && !backbone.isSelected())
        || (backbone.isSelected() && chromosome.getSelectedIndex() > 0));
    }
    else if (ie.getSource().equals(chromosome))
    {
      load.setEnabled(chromosome.getSelectedIndex() > 0 ||
          (!backbone.isSelected() && release.getSelectedIndex() > 0));
    }

    // Handle the top three drop downs
    if (ie.getStateChange() != ItemEvent.SELECTED) return;
    if (ie.getSource().equals(species))
    {
      // Setup the items for the Map Type drop down
      mapType.removeAllItems();
      mapType.addItem("");
      Vector<String> mapTypeList = new Vector<String>();
      for (MapData map : data)
        if (map.getSpecies().equals(species.getSelectedItem()) &&
            !mapTypeList.contains(map.getTypeString()))
            mapTypeList.addElement(map.getTypeString());
      for (String s : mapTypeList)
        mapType.addItem(s);

      mapType.setEnabled(species.getSelectedIndex() > 0);
      if (mapType.getItemCount() == 2) mapType.setSelectedIndex(1);
    }
    else if (ie.getSource().equals(mapType))
    {
      // Setup the items for the Release drop down
      release.removeAllItems();
      release.addItem("");
      Vector<String> versionList = new Vector<String>();
      for (MapData map : data)
        if (map.getSpecies().equals(species.getSelectedItem()) &&
            map.getTypeString().equals(mapType.getSelectedItem()) &&
            !versionList.contains(map.getVersion().toString()))
        {
          versionList.addElement(map.getVersion().toString());
          release.addItem(map.getVersion());
        }

      release.setEnabled(mapType.getSelectedIndex() > 0);
      // Per Dr. Kwitek's request --> if (release.getItemCount() == 2)
      if (release.getItemCount() == 2) release.setSelectedIndex(1);
    }
    else if (ie.getSource().equals(release))
    {
      // Load all of the available Chromosomes into the drop down
      chromosome.removeAllItems();
      chromosome.addItem("");
      MapData map = null;
      for (MapData m : data)
        if (m.getSpecies().equals(species.getSelectedItem()) &&
            m.getTypeString().equals(mapType.getSelectedItem()) &&
            m.getVersion().equals(release.getSelectedItem()))
          map = m;
      try
      {
        Vector<String> chrNames = Factory.getAvailableChromosomes(map);
        Collections.sort(chrNames, new ChromosomeComparator());
        for (String s : chrNames)
          chromosome.addItem(s);
      }
      catch (SQLException e)
      {
        String text = "There was a problem while trying to communicate with the VCMap\n";
        text += "database.  Please try again later.\n\n";
        text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
        String log = "Error retrieving chromosome list from database: " + e;
        ErrorReporter.handleMajorError(mainGUI, text, log);
      }

      load.setEnabled(!backbone.isSelected() && release.getSelectedIndex() > 0);
    }
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.MapLoader#mapLoadCompleted(boolean, int, java.lang.String)
   */
  public void mapLoadCompleted(boolean successful, int messageType, String message)
  {
    setVisible(!successful);

    if (!successful)
      if (messageType == MainGUI.SYNTENY_ERROR || messageType == MainGUI.ALREADY_LOADED)
        JOptionPane.showMessageDialog(this,
            message,
            "No Data Loaded",
            JOptionPane.ERROR_MESSAGE);
  }

  /**
   * <p>Update the cached data that represents the available MapData that can
   * be loaded from the backend.  This will query the backend for any changes
   * in the database since the last time the dialog was shown.</p>
   */
  class UpdateData
    extends Thread
  {
    public void run()
    {
      try
      {
        data = null;
        Vector<MapData> maps = Factory.getAvailableMaps();
        species.removeAllItems();
        species.addItem("");
        mapType.removeAllItems();
        mapType.addItem("");
        release.removeAllItems();
        release.addItem("");
        Vector<String> speciesList = new Vector<String>();
        Vector<String> typeList = new Vector<String>();
        Vector<String> versionList = new Vector<String>();
        for (MapData map : maps)
        {
          if (!checkForExit())
            return;
          if (!speciesList.contains(map.getSpecies()))
            speciesList.addElement(map.getSpecies());

          if (!checkForExit())
            return;
          if (!typeList.contains(map.getTypeString()))
            typeList.addElement(map.getTypeString());

          if (!checkForExit())
            return;
          if (!versionList.contains(map.getVersion().toString()))
            versionList.addElement(map.getVersion().toString());
        }
        for (String s : speciesList)
        {
          if (!checkForExit())
            return;
          species.addItem(s);
        }

        for (String s : typeList)
        {
          if (!checkForExit())
            return;
          mapType.addItem(s);
        }

        for (String s : versionList)
        {
          if (!checkForExit())
            return;
          release.addItem(s);
        }

        // Cache the map list
        data = maps;

        // Ensure selection is correct
        species.setSelectedIndex(0);
      }
      catch (SQLException e)
      {
        if (data == null)
        {
          String text = "There was a problem while trying to communicate with the VCMap\n";
          text += "database.  Please try again later.\n\n";
          text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
          String log = "Error retrieving map list from database: " + e;
          ErrorReporter.handleMajorError(mainGUI, text, log);
        }
        else
        {
          logger.warn("There was a problem retrieving the map list from the database, " +
              "but we have an old copy of the map list that we can use: " + e);
        }
      }

      // Guess on the backbone checkbox
      chromosome.removeAllItems();
      chromosome.addItem("");
      if (mainGUI.getBackbone() != null)
      {
        backbone.setSelected(false);
        chromosome.setEnabled(false);
      }
      mapType.setEnabled(false);
      release.setEnabled(false);

      species.setEnabled(true);
      
      // Resize our dialog properly
      SwingUtilities.invokeLater(new Thread() { public void run() { pack(); }});

      // this is hackish, but we want to make sure this thread has finished
      // before constructing the ontology tree in the background
      Factory.buildOntologyTree(mainGUI);
    }
  }
}
