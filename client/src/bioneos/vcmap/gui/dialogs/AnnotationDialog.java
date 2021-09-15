package bioneos.vcmap.gui.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.AnnotationLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.Tutorial;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.Factory;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.OntologyNode;
import bioneos.vcmap.model.comparators.SortOntologyByName;

/**
 * <p>This class displays a {@link JDialog} that displays options a user can
 * choose from to load a specific type of annotation for multiple maps.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class AnnotationDialog
  extends VCMDialog
  implements ActionListener, ItemListener, ListSelectionListener, MouseListener, AnnotationLoader
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  // Singleton design pattern
  private static HashMap<MainGUI, AnnotationDialog> instances = new HashMap<MainGUI, AnnotationDialog>();

  // MainGUI
  private MainGUI mainGUI;

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // GUI Components
  private JComboBox annotTypes;
  private JComboBox ontology;
  private ArrayList<JLabel> ontologyLabels;
  private JList asetList;
  private JButton load;
  private JButton cancel;

  // Variables
  private ArrayList<OntologyNode> ontologyHierarchy = new ArrayList<OntologyNode>();
  private ArrayList<ArrayList<OntologyNode>> ontologyNodes = new ArrayList<ArrayList<OntologyNode>>();
  private int currentOntologyLevel = -1;
  private ArrayList<Boolean> enabledOntology = new ArrayList<Boolean>();
  private ArrayList<String> ontologyNames = new ArrayList<String>();
  private int initialVerticalOffset = 95;

  private Vector<DisplayMap> mapsToLoad;
  private Vector<DisplayMap> mapsLoaded;
  private Vector<String> errorList;

  /**
   * <p>Constructor for {@link AnnotationDialog}. Creates
   * {@link AnnotationDialog} from the information in the {@link MapNavigator}
   * of the {@link MainGUI}. The constructor is private so that only this class
   * can create an instance of {@link AnnotationDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private AnnotationDialog(MainGUI parent)
  {
    super(parent, false);

    this.mainGUI = parent;

    // Component setup
    annotTypes = new JComboBox();
    annotTypes.addItemListener(this);
    String[] items = {"Please select a type of annotation..."};
    asetList = new JList(items);
    asetList.addListSelectionListener(this);
    asetList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    asetList.setVisibleRowCount(5);
    JScrollPane mapsScroll = new JScrollPane(asetList);
    mapsScroll.setPreferredSize(new Dimension(350,100));
    ontologyLabels = new ArrayList<JLabel>();
    JLabel defaultLabel = new JLabel("All Data");
    defaultLabel.setFont(new Font("default", Font.PLAIN, 10));
    defaultLabel.setEnabled(false);
    ontologyLabels.add(defaultLabel);
    ontology = new JComboBox();
    ontology.addItemListener(this);
    ontology.setRenderer(new ComboRenderer());
    ontology.setPreferredSize(new Dimension(350,25));
    ontology.setEnabled(false);
    JLabel annotTypesL = new JLabel("Choose a Type of Annotation:");
    JLabel mapsL = new JLabel("Select the Maps:");
    JLabel ontologyL = new JLabel("Select Ontology Terms to Filter by:");

    load = new JButton("Load");
    load.addActionListener(this);
    load.setEnabled(false);

    cancel = new JButton("Cancel");
    cancel.addActionListener(this);
    cancel.setEnabled(true);

    // Component Layout
    SpringLayout s = new SpringLayout();
    JPanel main = new JPanel(s);
    s.putConstraint(SpringLayout.NORTH, annotTypesL, 5, SpringLayout.NORTH, main);
    s.putConstraint(SpringLayout.WEST, annotTypesL, 5, SpringLayout.WEST, this);
    s.putConstraint(SpringLayout.NORTH, annotTypes, 5, SpringLayout.SOUTH, annotTypesL); //10
    s.putConstraint(SpringLayout.WEST, annotTypes, 0, SpringLayout.WEST, annotTypesL); //10
    s.putConstraint(SpringLayout.NORTH, mapsL, 10, SpringLayout.SOUTH, annotTypes); //20
    s.putConstraint(SpringLayout.WEST, mapsL, 0, SpringLayout.WEST, annotTypesL);
    s.putConstraint(SpringLayout.NORTH, mapsScroll, 5, SpringLayout.SOUTH, mapsL); //10
    s.putConstraint(SpringLayout.WEST, mapsScroll, 0, SpringLayout.WEST, annotTypes);
    s.putConstraint(SpringLayout.NORTH, defaultLabel, 10, SpringLayout.SOUTH, ontology);
    s.putConstraint(SpringLayout.WEST, defaultLabel, 0, SpringLayout.WEST, annotTypesL);
    s.putConstraint(SpringLayout.WEST, ontologyL, 0, SpringLayout.WEST, annotTypesL);
    s.putConstraint(SpringLayout.NORTH, ontologyL, 10, SpringLayout.SOUTH, mapsScroll);
    s.putConstraint(SpringLayout.NORTH, ontology, 5, SpringLayout.SOUTH, ontologyL);
    s.putConstraint(SpringLayout.WEST, ontology, 0, SpringLayout.WEST, mapsScroll);
    s.putConstraint(SpringLayout.SOUTH, main, initialVerticalOffset, SpringLayout.SOUTH, ontologyLabels.get(0));
    s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, mapsScroll);
    s.putConstraint(SpringLayout.SOUTH, load, -5, SpringLayout.SOUTH, main);
    s.putConstraint(SpringLayout.EAST, load, -5, SpringLayout.WEST, cancel);
    s.putConstraint(SpringLayout.SOUTH, cancel, -5, SpringLayout.SOUTH, main);
    s.putConstraint(SpringLayout.EAST, cancel, 0, SpringLayout.EAST, mapsScroll);

    main.add(annotTypesL);
    main.add(annotTypes);
    main.add(mapsL);
    main.add(mapsScroll);
    main.add(ontologyL);
    main.add(ontology);
    main.add(defaultLabel);
    main.add(cancel);
    main.add(load);
    setContentPane(main);

    // Final setup
    setTitle("Load Annotation");
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    main.revalidate();
    pack();
  }

  /**
   * <p>This sets up the {@link AnnotationDialog} so that the information
   * displayed in the {@link JDialog} is up to date and reinitialized everytime
   * the {@link JDialog} is shown.</p>
   *
   */
  public void setupComponents()
  {
    Vector<AnnotationSet> annotSets = new Vector<AnnotationSet>();
    Vector<String> annotTypesToAdd = new Vector<String>();
    for (MapData map : mainGUI.getMaps())
      for (AnnotationSet set : map.getAllAnnotationSets())
      {
        boolean add = false;
        // NOTE: Would be better to actually check ontology filters here
        for (DisplayMap dMap : mainGUI.getMapNavigator().getDisplayMaps())
          if(!dMap.getShownSets().contains(set) || set.getType().equals("QTL"))
            add = true;
        if (!annotSets.contains(set) && add)
          annotSets.add(set);
      }

    annotTypes.removeAllItems();
    annotTypes.addItem("");
    if (annotSets.size() != 0)
    {
      for (AnnotationSet set : annotSets)
        if (!annotTypesToAdd.contains(set.getType()))
          annotTypesToAdd.add(set.getType());
      for (String type : annotTypesToAdd)
        annotTypes.addItem(type);
    }
    else
    {
      logger.warn("No AnnotationSets Available to Load");
    }

    SpringLayout s = (SpringLayout) getContentPane().getLayout();
    s.putConstraint(SpringLayout.SOUTH, getContentPane(), initialVerticalOffset, SpringLayout.SOUTH, ontologyLabels.get(0));

    resetOntologyComponents();
  }

  /**
   * <p>Show the instance of {@link AnnotationDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link AnnotationDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link AnnotationDialog}
   */
  public static void showAnnotationDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new AnnotationDialog(parent));
    AnnotationDialog instance = instances.get(parent);

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
   * <p>Removes the instance of the {@link AnnotationDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link AnnotationDialog}
   */
  public static void closeAnnotationDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Queries the {@link Factory} for all relevant top-level ontology categories
   * for all currently loaded {@link DisplayMap}s. This data is used to
   * populate the ontology combobox.
   */
  public void getTopLevelOntology()
  {
    // reset the hierarchy
    ontologyHierarchy = new ArrayList<OntologyNode>();
    // Update ontology section
    // first, construct a list of all loaded chromosomes
    ArrayList<MapData> selectedMaps = new ArrayList<MapData>();
    for (Object obj : asetList.getSelectedValues())
      selectedMaps.add(((AnnotationSet) obj).getMap());
    ArrayList<Chromosome> loadedChrs = new ArrayList<Chromosome>();
    for (MapData m : selectedMaps)
    {
      Vector<Chromosome> chrs = m.getLoadedChromosomes();
      for (Chromosome c : chrs)
        loadedChrs.add(c);
    }

    // get a list of all top-level ontology nodes relevant to the loaded chromosomes
    ArrayList<OntologyNode> topLevel = new ArrayList<OntologyNode>();
    ontologyNodes.clear();
    ArrayList<OntologyNode> nodes = Factory.getOntology(null, loadedChrs);
    for (OntologyNode n : nodes)
    {
      if (!topLevel.contains(n))
        topLevel.add(n);
    }
    ontologyNodes.add(topLevel);

    currentOntologyLevel = 0;
    resetOntologyComponents();
    populateOntologyComboList();
    ontology.setSelectedIndex(0);
    ontology.setEnabled(true);
    ontologyLabels.get(0).setEnabled(true);
  }

  /**
   * Adds a new ontology label to the annotation dialog, showing users the
   * currently selected ontology hierarchy.
   *
   * @param label
   *    A string to be used as the label.
   */
  public void addOntologyLabel(String label)
  {
    // We're increasing the depth of the label tree by one, so make the
    // previous label mouse clickable
    ontologyLabels.get(ontologyLabels.size() - 1).addMouseListener(this);

    // Create a new JLabel and add it to our list of labels
    JLabel newLabel = new JLabel(">>" + label);
    newLabel.setFont(new Font("default", Font.PLAIN, 10));
    int depthOffset = (ontologyLabels.size() - 1) * 2;
    if (label.length() > 40 - depthOffset)
    {
      label = label.substring(0, 39 - depthOffset).concat("...");
      newLabel.setText(">>" + label);
    }

    // We've changed the contents of the dialog, so ensure that our layout
    // remains correct.
    SpringLayout s = (SpringLayout) getContentPane().getLayout();
    s.putConstraint(SpringLayout.NORTH, newLabel, 4, SpringLayout.SOUTH, ontologyLabels.get(ontologyLabels.size()-1));//10
    s.putConstraint(SpringLayout.WEST, newLabel, 10 * (currentOntologyLevel), SpringLayout.WEST,  ontologyLabels.get(0));
    if (ontologyLabels.size() > 3)
      s.putConstraint(SpringLayout.SOUTH, getContentPane(),
          ((ontologyLabels.size() + 1) * 20) + 15, SpringLayout.SOUTH, ontologyLabels.get(0));
    newLabel.setEnabled(true);
    newLabel.setVisible(true);
    ontologyLabels.add(newLabel);

    // Add our new label to the JPanel and make sure everything looks right
    getContentPane().add(newLabel);
    
    // NOTE: Avoiding this by adding a scrollbar or alternative GUI 
    //   representation might be a good idea in the future to avoid the 
    //   automatic resizing of the dialog...
    SwingUtilities.invokeLater(new Thread() { public void run() { pack(); } });
  }

  /**
   * Adds the appropriate ontology category names to the ontology combobox.
   */
  public void populateOntologyComboList()
  {
    enabledOntology = new ArrayList<Boolean>();
    ontologyNames = new ArrayList<String>();

    ontology.removeAllItems();
    ontology.addItem(currentOntologyLevel == 0 ? "Select a category:" : "Select a subcategory:");
    ArrayList<OntologyNode> nodes = new ArrayList<OntologyNode>();
    ArrayList<OntologyNode> WithDecendants = new ArrayList<OntologyNode>();
    ArrayList<OntologyNode> WithoutDecendants = new ArrayList<OntologyNode>();
    nodes.addAll(ontologyNodes.get(currentOntologyLevel));
    for (OntologyNode n : nodes)
    {
      int size = -1;
      if (n != null)
      {
       size = n.getAnnotationDescendantCount();
       if (size > 0)
       {
        WithDecendants.add(n);
       }
       else
       {
        WithoutDecendants.add(n);
       }
      }
    }
    Collections.sort(WithDecendants,new SortOntologyByName());
    Collections.sort(WithoutDecendants,new SortOntologyByName());
    ontologyNodes.get(currentOntologyLevel).removeAll(nodes);
    ontologyNodes.get(currentOntologyLevel).addAll(WithDecendants);
    ontologyNodes.get(currentOntologyLevel).addAll(WithoutDecendants);

    for (OntologyNode n : ontologyNodes.get(currentOntologyLevel))
    {
      int size = -1;
      if (n != null)
      {
        size = n.getAnnotationDescendantCount();
        String itemString = n.getCategory();
        ontologyNames.add(itemString);
        int maxLength = (int)(ontology.getPreferredSize().getWidth()/9.0);
        if (itemString.length() > maxLength)
          itemString = itemString.substring(0,maxLength).concat("...");
        if (size > 0)
        {
          itemString = itemString + (size > 0 ? " (" + size + ")" : "");
          enabledOntology.add(true);
        }
        else
          enabledOntology.add(false);

        ontology.addItem(itemString);
      }
    }

    // Test if we're at the lowest level (nothing to populate with)
    if (ontology.getItemCount() == 1)
    {
      ontology.removeAllItems();
      if (currentOntologyLevel > 0)
        ontology.addItem("No further subcategories");
      else
        ontology.addItem("No ontology loaded");
    }
  }

  /**
   * Sets the current ontology level by removing ontology label(s) with an
   * index greater than the specified index from the dialog box.
   *
   * @param index
   *    An integer value representing the index of the clicked JLabel.  All labels
   *    with an index greater than <code>index</code> will be removed.
   */
  public void setCurrentOntology(int index)
  {
    // Given the clicked-on index, remove the appropriate range, starting
    // at the end of the list
    SpringLayout s = (SpringLayout) getContentPane().getLayout();
    for (int i = ontologyLabels.size() - 1; i > index; i--)
    {
      getContentPane().remove(ontologyLabels.get(i));
      s.removeLayoutComponent(ontologyLabels.get(i));
      ontologyLabels.remove(i);
      ontologyHierarchy.remove(i - 1);
      if (i == ontologyNodes.size() - 1)
        ontologyNodes.remove(i);
    }
    currentOntologyLevel = index;

    // Must validate / repaint since we removed components from a displayed layout 
    getContentPane().validate();
    repaint();

    // We don't want the lowest level of the "label tree" to be clickable
    JLabel lowest = ontologyLabels.get(ontologyLabels.size() - 1);
    lowest.removeMouseListener(this);
    lowest.setForeground(new Color(0, 0, 0));
    populateOntologyComboList();

    // Ensure the proper height for the dialog box
    int verticalOffset = ((ontologyLabels.size()) * 20) + 15;
    s.putConstraint(SpringLayout.SOUTH, getContentPane(), 
        (verticalOffset > initialVerticalOffset ? verticalOffset : initialVerticalOffset),
        SpringLayout.SOUTH, ontologyLabels.get(0));

    // NOTE: Avoiding this by adding a scrollbar or alternative GUI 
    //   representation might be a good idea in the future to avoid the 
    //   automatic resizing of the dialog...
    SwingUtilities.invokeLater(new Thread() { public void run() { pack(); } });
  }

  /**
   * Restores all ontology-related {@link AnnotationDialog} components to their
   * initial, default state.
   */
  public void resetOntologyComponents()
  {
    // remove all but the zeroth ("all data") ontology labels
    for (int i = ontologyLabels.size()-1; i > 0; i--)
    {
      getContentPane().remove(ontologyLabels.get(i));
      ontologyLabels.remove(i);
    }

    ontology.removeAllItems();
    ontology.addItem("");
    ontology.setEnabled(false);
    ontologyLabels.get(0).setEnabled(false);
    ontologyLabels.get(0).removeMouseListener(this);
    ontologyHierarchy.clear();
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.AnnotationLoader#annotationLoadCompleted(boolean, int, java.lang.String)
   */
  public void annotationLoadCompleted(boolean successful, int messageType, String message)
  {

    if (!successful && messageType != MainGUI.NO_DATA_LOADED_ERROR)
      errorList.addElement("\tError #" + messageType + ":" + message);

    if (!mapsLoaded.containsAll(mapsToLoad))
    {
      // Load the other maps before checking for errors
      for (DisplayMap displayMap : mapsToLoad)
      {
        if (!mapsLoaded.contains(displayMap))
        {
          for (AnnotationSet set : displayMap.getMap().getAllAnnotationSets())
          {
            if (set.getType().equals(annotTypes.getSelectedItem()))
            {
              mapsLoaded.add(displayMap);
              // Load lowest level of Ontology otherwise load all
              if (ontologyHierarchy.size() > 0)
                mainGUI.loadAnnotation(displayMap, set, ontologyHierarchy.get(ontologyHierarchy.size() - 1), this);
              else
                mainGUI.loadAnnotation(displayMap, set, this);
              
              // NOTE: The callback will load the other maps.  We only want to load
              //   the first unloaded map.
              return;
            }
          }
        }
      }
    }
    else if (errorList.size() > 0)
    {
      // Done loading, but had errors
      StringBuilder errors = new StringBuilder();
      for (String e : errorList)
        errors.append(e).append("\n");
      String text = "There was a problem while trying to loading some of the annotation," +
        "some annotation may be missing\n" +
        "If you are seeing this message repeatedly, please contact the administrator.";
      String log = "Problem loading annotation.";
      ErrorReporter.handleMajorError(mainGUI, text, log + "\nError list:\n" + errors);     
    }
  }

  /*
   * Called when Load or Cancel {@link JButton} is pressed
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

      Object[] selectedObjects = (Object[]) asetList.getSelectedValues();
      if (selectedObjects.length > 0)
        setVisible(false);

      mapsToLoad = new Vector<DisplayMap>();
      mapsLoaded = new Vector<DisplayMap>();
      errorList = new Vector<String>();

      // Load Annotation for selected maps using MainGUIs AnnotationLoaderThread
      if (selectedObjects.length == 0)
      {
        // No maps selected -- warn user
        JOptionPane.showMessageDialog(this, "Please select at least one annotation set to load.",
            "Select data to load", JOptionPane.WARNING_MESSAGE);
        return;
      }
      else
      {
        for (Object obj : selectedObjects)
        {
          AnnotationSet aset = (AnnotationSet) obj;
          DisplayMap map = null;
          for (DisplayMap m : mainGUI.getMapNavigator().getDisplayMaps())
            if (m.getMap().getId() == aset.getMapId())
              map = m;
          
          if (map != null)
          {
            if (annotTypes.getSelectedItem().equals("QTL") && ontologyHierarchy.size() > 0)
              mainGUI.loadAnnotation(map, aset, ontologyHierarchy.get(ontologyHierarchy.size() - 1), this);
            else
              mainGUI.loadAnnotation(map, aset, this);
          }
          else
          {
            logger.error("Could not find map associated with Annotation Set? -- " + aset + " (id: " + aset.getId() + ")");
          }
        }
      }
    }

    // re-enable the mainGUI after the dialog has closed
    Tutorial.updatePage("mapInteraction");
    mainGUI.setEnabled(true);
  }

  /*
   * Called when a selection in the maps {@link JList}, types {@link JComboBox}
   * or ontology {@link JComboBox} is altered.
   */
  public void itemStateChanged(ItemEvent ie)
  {
    if (ie.getStateChange() == ItemEvent.SELECTED && ie.getSource() == annotTypes)
    {
      logger.debug( String.valueOf(annotTypes.getSelectedIndex())
                    + " selected annotation type(s)");
      if (annotTypes.getSelectedIndex() == 0)
      {
        String[] items = {"Please select a type of annotation..."};
        asetList.setListData(items);
        asetList.setSelectedIndex(-1);
        load.setEnabled(false);
      }
      else
      {
        Vector<AnnotationSet> asets = new Vector<AnnotationSet>();
        
        // Don't display already loaded data
        Vector<AnnotationSet> loaded = new Vector<AnnotationSet>();
        for (MapData m : mainGUI.getMaps())
          loaded.addAll(m.getLoadedAnnotationSets());

        String type = (String) annotTypes.getSelectedItem();
        for (MapData map : mainGUI.getMaps())
        {
          for (AnnotationSet set : map.getAllAnnotationSets())
          {
            // NOTE: Would be better to actually check ontology filters here... 
            if (set.getType().equals(type) && !asets.contains(set) && (!loaded.contains(set) || type.equals("QTL")))
            {
              asets.add(set);
            }
          }
        }

        if (asets.size() != 0)
          load.setEnabled(true);

        asetList.setListData(asets);

        int upperBound = asetList.getModel().getSize() - 1;
        if (upperBound >= 0 ) asetList.setSelectionInterval(0, upperBound);
      }

      // NOTE - we might be using ontology data for types other than QTL
      if (!((String)annotTypes.getSelectedItem()).equalsIgnoreCase("QTL"))
        resetOntologyComponents();
      else if (ie.getItem().equals("QTL"))
      {
        //getTopLevelOntology();
      }
    }
    else if (ie.getStateChange() == ItemEvent.SELECTED && ie.getSource() == ontology)
    {
      if (!ie.getItem().equals("") && !ie.getItem().equals("No further subcategories")
          && !ie.getItem().equals("Select a category:") && !ie.getItem().equals("Select a subcategory:")
          && !ie.getItem().equals("No ontology loaded"))
      {
        // ensure users can't select disabled categories
        if (!enabledOntology.get(ontology.getSelectedIndex()-1))
        {
          ontology.setSelectedIndex(0);
          return;
        }

        //add the selected category to the "tree" of ontology selections
        OntologyNode current = ontologyNodes.get(currentOntologyLevel).get(ontology.getSelectedIndex()-1);
        ontologyHierarchy.add(current);
        currentOntologyLevel++;

        // Build label from ontologyHierarchy
        String label = ontologyHierarchy.get(currentOntologyLevel-1).getCategory();
        addOntologyLabel(label);

        //repopulate the JCombobox given our new selection
        ArrayList<OntologyNode> levelNodes = Factory.getOntology(current, null);
        ontologyNodes.add(levelNodes);

        populateOntologyComboList();
      }
    }
  }

  /*
   * (non-Javadoc)
   * @see javax.swing.event.ListSelectionListener#valueChanged(javax.swing.event.ListSelectionEvent)
   */
  public void valueChanged(ListSelectionEvent lse)
  {
    if (!lse.getValueIsAdjusting() && lse.getSource().equals(asetList)
        && annotTypes.getSelectedItem() != "  "
        && ((String) annotTypes.getSelectedItem()).equalsIgnoreCase("QTL"))
    {
      resetOntologyComponents();
      getTopLevelOntology();
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
   */
  public void mouseClicked(MouseEvent e)
  {
    setCurrentOntology(ontologyLabels.indexOf(e.getSource()));
    setCursor(Cursor.getDefaultCursor());
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
   */
  public void mouseEntered(MouseEvent e)
  {
    ontologyLabels.get(ontologyLabels.indexOf(e.getSource())).setForeground(new Color(0, 0, 255));
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
   */
  public void mouseExited(MouseEvent e)
  {
    ontologyLabels.get(ontologyLabels.indexOf(e.getSource())).setForeground(new Color(0, 0, 0));
    setCursor(Cursor.getDefaultCursor());
  }

  // Obligatory overrides
  public void mouseReleased(MouseEvent e) {}
  public void mousePressed(MouseEvent e) {}

  /**
   * <p>Implements a {@link ListCellRenderer} for the ontology combobox.  This
   * modifies the default behavior of the combobox to allow disabled (grayed)
   * items as well as tooltips for long ontology category names.</p>
   *
   * @author dquaken@bioneos.com
   */
  class ComboRenderer extends JLabel implements ListCellRenderer
  {
    /* Not used */
    private static final long serialVersionUID = 1L;

    /**
     * Default Constructor.
     */
    public ComboRenderer()
    {
      setOpaque(true);
      setBorder(new EmptyBorder(1, 1, 1, 1));
    }

    /*
     * (non-Javadoc)
     * @see javax.swing.ListCellRenderer#getListCellRendererComponent(javax.swing.JList, java.lang.Object, int, boolean, boolean)
     */
    public Component getListCellRendererComponent( JList list,
           Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
      if (isSelected)
      {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
        String toolTipString = null;

        if (ontologyNames.size() > 0 && index > 0 && !ontologyNames.get(index-1).concat(" ").equals(value.toString().split("\\(")[0])
              && enabledOntology.get(index - 1))
        {
          toolTipString = ontologyNames.get(index-1);
        }

        list.setToolTipText(toolTipString);
      }
      else
      {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }

      if (enabledOntology.size() > 0 && index > 0)
      {
        if (!enabledOntology.get(index - 1))
        {
          setBackground(list.getBackground());
          setForeground(UIManager.getColor("Label.disabledForeground"));
          setEnabled(false);
        }
        else
          setEnabled(true);
      }

      setFont(list.getFont());
      setText((value == null) ? "" : value.toString());
      return this;
    }
  }
}
