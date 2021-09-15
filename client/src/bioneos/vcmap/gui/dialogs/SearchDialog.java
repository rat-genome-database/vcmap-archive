package bioneos.vcmap.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.AnnotationLoader;
import bioneos.vcmap.callback.MapLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.components.FilterPanel;
import bioneos.vcmap.gui.components.SearchResultsCellRenderer;
import bioneos.vcmap.gui.components.SearchResultsTableModel;
import bioneos.vcmap.gui.components.SortingTable;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.Factory;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.util.Util;

/**
 * <p>This class displays a {@link JDialog} that allows the user to search
 * the currently displayed maps by keyword or search a particular displayed
 * map based on location. The keyword search does not require any use of
 * wildcard symbols.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby
 * @author cgoodman
 * @author sgdavis
 */

public class SearchDialog
  extends VCMDialog
  implements ActionListener, MouseListener, MapLoader, AnnotationLoader
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  // Single window per MainGUI
  private static HashMap<MainGUI, SearchDialog> instances = new HashMap<MainGUI, SearchDialog>();

  // Constants
  private static final int FETCH_BLOCK_SIZE = Integer.MIN_VALUE;
  private static final int MAX_RESULTS = 225000;

  // Reference back to our containing window
  private MainGUI mainGUI;

  // Logging (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // Lock for controlling results
  private Object resultsLock = new Object();

  // GUI Components
  private JButton close;
  private SortingTable hits;
  private JPanel advPanel;
  private JPanel labelPanel;
  private JPanel filterListPanel;
  private JPanel searchType;

  private JLabel lFilterType;
  private JLabel lSearchCriteria;

  private SpringLayout advLayout;
  private JScrollPane advScroll;
  private JButton searchAdv;

  private JLabel lDBAdvancedResults;
  private JLabel lLocalAdvancedResults;
  private JRadioButton matchAny;
  private JRadioButton matchAll;
  private ButtonGroup matches;
  private JButton addFilter;
  private JLabel lLoaderWheel;

  // Filter and JCombo
  private Vector<FilterPanel> filterList;
  private Vector<MapData> allAvailableMaps;
  private Vector<MapData> mapsLeft;
  private Vector<String> speciesList;
  private Vector<String> annotTypeList;
  private Vector<String> featureSourceList;
  private Vector<String> mapTypeList;
  private Vector<String> versionList;
  private Vector<String> unitList;
  private HashMap<Integer, String> sources;

  // Component used when loading annotation
  private JCheckBox checkbox;

  // References to MainGUI and annotation
  private String newAnnotationName;
  private MainGUI newMainGUI;
  private double annotStart;
  private double annotStop;
  private AnnotationSet annotSet;

  // Flags
  private boolean canClearData;
  private boolean mouseListenerIsActive;

  // Seperate threads
  private AdvancedSearchDatabase asd;
  private static LoadData ld;

  // Constants
  public final String[] FILTER_TYPES = {"Species", "Map Type", "Map Version", "Feature Type", "Feature Source", "Feature Name", "Location"};
  public final String LOAD_TEXT = "Would you like to load the map associated with your selection?";
  public final String INFO_TEXT = "The map associated with your selection has already been loaded.";
  public final String UNHIDE_TEXT = "The map associated with your selection has already been loaded," +
  		                    " but the annotation is hidden. Would you like to show this annotation?";

  /**
   * <p>Constructor for {@link SearchDialog}. Creates
   * {@link SearchDialog} from the information in the {@link MapNavigator}
   * of the {@link MainGUI}. The constructor is private so that only this class
   * can create an instance of {@link SearchDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private SearchDialog(MainGUI parent)
  {
    super(parent, false);

    this.mainGUI = parent;
    sources = Factory.getAllSources();
    close = new JButton("Close");
    close.addActionListener(this);
    close.setEnabled(true);

    // Setup the search results table
    hits = new SortingTable(new SearchResultsTableModel(parent.getMapNavigator()));
    hits.setDefaultRenderer(String.class, new SearchResultsCellRenderer());
    hits.setColumnSelectionAllowed(false);
    hits.setRowSelectionAllowed(true);
    hits.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    hits.addMouseListener(this);

    // Select annotation in MapNavigator when a row for that annotation is selected
    ListSelectionModel hitsSM = hits.getSelectionModel();
    hitsSM.addListSelectionListener(new ListSelectionListener()
      {
        public void valueChanged(ListSelectionEvent e)
        {
          if (e.getValueIsAdjusting()) return;

          ListSelectionModel hTSM = (ListSelectionModel)e.getSource();

          // Perform this selection change only as results are locked to ensure
          // things are not changed in the middle
          synchronized (resultsLock)
          {
            if (!hTSM.isSelectionEmpty())
            {
              int min = hTSM.getMinSelectionIndex();
              int max = hTSM.getMaxSelectionIndex();
              mainGUI.getMapNavigator().clearSelection();

              for (int i = min; i <= max; i++)
              {
                if (hTSM.isSelectedIndex(i) && i < hits.getModel().getRowCount())
                {
                  Object o = ((SearchResultsTableModel) hits.getModel()).getRow(i);
                  if (o instanceof Annotation)
                  {
                    mainGUI.getMapNavigator().addSelectedAnnotation(null,
                      (Annotation) o, true, (i == max) ? true : false);
                  }
                }
              }
            }
          }
        }
      });

    JScrollPane hitsScroll = new JScrollPane(hits,
      JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    // Set initial column widths
    TableColumn col = hits.getColumnModel().getColumn(0);
    col.setPreferredWidth(col.getPreferredWidth() + 35);

    col = hits.getColumnModel().getColumn(1);
    col.setPreferredWidth(col.getPreferredWidth() - 15);

    col = hits.getColumnModel().getColumn(4);
    col.setPreferredWidth(col.getPreferredWidth() - 15);

    col = hits.getColumnModel().getColumn(5);
    col.setPreferredWidth(col.getPreferredWidth() - 20);

    col = hits.getColumnModel().getColumn(6);
    col.setPreferredWidth(col.getPreferredWidth() + 175);

    BorderLayout bl = new BorderLayout();
    advPanel = new JPanel(bl);

    // Setup up JPanel for FilterPanels
    advLayout = new SpringLayout();
    filterListPanel = new JPanel(advLayout);
    filterList = new Vector<FilterPanel>();

    advScroll = new JScrollPane(filterListPanel,
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    // Setup search button and match criteria JRadioButtons
    searchAdv = new JButton("Search");
    searchAdv.setActionCommand("AdvancedSearch");
    searchAdv.addActionListener(this);
    lLocalAdvancedResults = new JLabel();
    lLocalAdvancedResults.setToolTipText("Search results from the loaded maps.");
    lDBAdvancedResults = new JLabel();
    lDBAdvancedResults.setToolTipText("Search results from the database.");

    lLoaderWheel = null ;
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/ajax-loader.gif") ;
      if(imageUrl != null)
      {
        lLoaderWheel = new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl))) ;
      }
    }
    catch (Exception e)
    {
      logger.warn("Error retrieving the Loder Icon: " + e) ;
    }

    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/add.png");
      if(imageUrl != null)
      {
        addFilter = new JButton(new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl)));
        addFilter.setPreferredSize(new Dimension(16,16));
        addFilter.setMaximumSize(new Dimension(16,16));
        addFilter.setBorderPainted(false);
        addFilter.setOpaque(false);
        addFilter.setContentAreaFilled(false);
      }
      else
      {
        addFilter = new JButton("+");
        addFilter.setPreferredSize(new Dimension(50, (int) addFilter.getPreferredSize().getHeight()));
      }
    }
    catch (Exception e)
    {
      addFilter = new JButton("+");
      addFilter.setPreferredSize(new Dimension(50, (int) addFilter.getPreferredSize().getHeight()));
    }

    addFilter.setActionCommand("AddFilter");
    addFilter.addActionListener(this);
    addFilter.setEnabled(false);
    addFilter.setToolTipText("Add a Filter");

    // Setup Components
    matches = new ButtonGroup();
    matchAny = new JRadioButton("Match Any");
    matchAll = new JRadioButton("Match All");

    matchAny.setActionCommand("Match Any");
    matchAll.setActionCommand("Match All");
    matchAny.addActionListener(this);
    matchAll.addActionListener(this);
    matchAny.setSelected(true);
    matches.add(matchAny);
    matches.add(matchAll);

    unitList = new Vector<String>();
    unitList.addElement("All");

    SpringLayout s = new SpringLayout();
    searchType = new JPanel(s);

    s.putConstraint(SpringLayout.EAST, searchType, 0, SpringLayout.EAST, filterListPanel);

    s.putConstraint(SpringLayout.EAST, searchAdv, -5, SpringLayout.EAST, searchType);
    s.putConstraint(SpringLayout.SOUTH, searchType, 5, SpringLayout.SOUTH, searchAdv);
    s.putConstraint(SpringLayout.NORTH, searchAdv, 5, SpringLayout.NORTH, searchType);

    s.putConstraint(SpringLayout.EAST, matchAll, -5, SpringLayout.WEST, searchAdv);
    s.putConstraint(SpringLayout.NORTH, matchAll, 5, SpringLayout.NORTH, searchType);

    s.putConstraint(SpringLayout.EAST, matchAny, -5, SpringLayout.WEST, matchAll);
    s.putConstraint(SpringLayout.NORTH, matchAny, 5, SpringLayout.NORTH, searchType);

    s.putConstraint(SpringLayout.EAST, lLoaderWheel, -10, SpringLayout.WEST, matchAny);
    s.putConstraint(SpringLayout.NORTH, lLoaderWheel, 3, SpringLayout.NORTH, searchType);

    s.putConstraint(SpringLayout.EAST, lLocalAdvancedResults, -5, SpringLayout.WEST, lLoaderWheel);
    s.putConstraint(SpringLayout.NORTH, lLocalAdvancedResults, 2, SpringLayout.NORTH, searchType);
    s.putConstraint(SpringLayout.WEST, lLocalAdvancedResults, 5, SpringLayout.WEST, searchType);

    s.putConstraint(SpringLayout.EAST, lDBAdvancedResults, -5, SpringLayout.WEST, lLoaderWheel);
    s.putConstraint(SpringLayout.NORTH, lDBAdvancedResults, 2, SpringLayout.SOUTH, lLocalAdvancedResults);
    s.putConstraint(SpringLayout.WEST, lDBAdvancedResults, 5, SpringLayout.WEST, searchType);

    lLoaderWheel.setVisible(false);
    searchType.add(lLoaderWheel);
    searchType.add(searchAdv);
    searchType.add(matchAny);
    searchType.add(matchAll);
    searchType.add(lLocalAdvancedResults);
    searchType.add(lDBAdvancedResults);

    // Setup JLabels
    lFilterType = new JLabel("Filter Type");
    lSearchCriteria = new JLabel("Search Criteria");

    addNewFilter(); // Adds a new FilterPanel

    s = new SpringLayout();
    JPanel lLabelPanel = new JPanel(s);
    s.putConstraint(SpringLayout.EAST, lFilterType, -5, SpringLayout.EAST, lLabelPanel);
    s.putConstraint(SpringLayout.SOUTH, lLabelPanel, 5, SpringLayout.SOUTH, lFilterType);
    s.putConstraint(SpringLayout.NORTH, lFilterType, 10, SpringLayout.NORTH, lLabelPanel);
    s.putConstraint(SpringLayout.WEST, lFilterType, 40, SpringLayout.WEST, lLabelPanel);

    lLabelPanel.add(lFilterType);

    s = new SpringLayout();
    JPanel rLabelPanel = new JPanel(s);
    s.putConstraint(SpringLayout.EAST, lSearchCriteria, -5, SpringLayout.EAST, rLabelPanel);
    s.putConstraint(SpringLayout.SOUTH, rLabelPanel, 5, SpringLayout.SOUTH, lSearchCriteria);
    s.putConstraint(SpringLayout.NORTH, lSearchCriteria, 10, SpringLayout.NORTH, rLabelPanel);
    s.putConstraint(SpringLayout.WEST, lSearchCriteria, 5, SpringLayout.WEST, rLabelPanel);
    rLabelPanel.add(lSearchCriteria);

    labelPanel = new JPanel(new GridLayout(1,2));

    labelPanel.add(lLabelPanel);
    labelPanel.add(rLabelPanel);

    advScroll.setPreferredSize(new Dimension(advScroll.getWidth(), 150));

    // Set up panel before adding it to the JTabbedPane
    advPanel.add(labelPanel, BorderLayout.NORTH);
    advPanel.add(advScroll, BorderLayout.CENTER);
    advPanel.add(searchType, BorderLayout.SOUTH);

    this.add(advPanel);

    SpringLayout m = new SpringLayout();
    JPanel p = new JPanel(m);
    m.putConstraint(SpringLayout.EAST, close, 0, SpringLayout.EAST, p);
    m.putConstraint(SpringLayout.SOUTH, p, 0, SpringLayout.SOUTH, close);
    m.putConstraint(SpringLayout.NORTH, p, 0, SpringLayout.NORTH, close);
    m.putConstraint(SpringLayout.WEST, p, 5, SpringLayout.WEST, close);
    p.add(close);

    BorderLayout layout = new BorderLayout(5, 5);
    JPanel main = new JPanel();
    main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    main.setLayout(layout);
    main.add(advPanel, BorderLayout.NORTH);
    main.add(hitsScroll, BorderLayout.CENTER);
    main.add(p, BorderLayout.SOUTH);

    add(main);

    // Start the background thread to gather our cached data
    filterList.firstElement().setFilterTypeEnabled(false);
    matchAll.setEnabled(false);
    matchAny.setEnabled(false);
    searchAdv.setEnabled(false);

    filterList.firstElement().setFilterTypeItemListenerEnabled(false);
    filterList.firstElement().getFilterTypeBox().addItem("Please wait...");
    filterList.firstElement().getFilterTypeBox().setSelectedItem("Please wait...");

    ld = new LoadData();
    // SGD - no!
    //ld.setPriority(Thread.MAX_PRIORITY);
    ld.start();

    // Final setup
    setTitle("Find");
    setResizable(true);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    pack();
    setSize(new Dimension(775,700));

    newAnnotationName = null;
    canClearData = true;
    mouseListenerIsActive = true;

    logger.debug("Search Dialog assembled and displayed");
  }

  /**
   * <p>Get the MainGUI instance</p>
   *
   * @return
   *  The MainGUI instance
   */
  public MainGUI getMainGUI()
  {
    return mainGUI;
  }

  /**
   * <p>Adds a new {@link FilterPanel} to the Advanced Search's {@link JPanel}
   * and a {@link Vector} conatining a list of the current {@link FilterPanel}s.</p>
   *
   */
  public void addNewFilter()
  {
    clearTable();

    FilterPanel newFilter = new FilterPanel(this);

    if ( filterList.isEmpty() ) // First panel to be added
    {
      newFilter.setRemoveButtonEnabled(false); // Disable removeFilter button
      advLayout.putConstraint(SpringLayout.NORTH, newFilter, 0, SpringLayout.NORTH, filterListPanel);
      advLayout.putConstraint(SpringLayout.WEST, newFilter, 0, SpringLayout.WEST, filterListPanel);

      advLayout.putConstraint(SpringLayout.NORTH, addFilter, 10, SpringLayout.SOUTH, newFilter);
      advLayout.putConstraint(SpringLayout.WEST, addFilter, 5, SpringLayout.WEST, filterListPanel);

      advLayout.putConstraint(SpringLayout.SOUTH, filterListPanel, 10, SpringLayout.SOUTH, addFilter);
      advLayout.putConstraint(SpringLayout.EAST, filterListPanel, 0, SpringLayout.EAST, newFilter);
      filterListPanel.add(newFilter);
      filterListPanel.add(addFilter);
    }
    else // Add filterList at the bottom of the list
    {
      filterListPanel.remove(addFilter);

      // enable removeFilter button since there are multiple filterList now
      if(filterList.size() == 1)
        filterList.firstElement().setRemoveButtonEnabled(true);

      FilterPanel prev = filterList.lastElement();

      advLayout.putConstraint(SpringLayout.EAST, newFilter, 0, SpringLayout.EAST, prev);
      advLayout.putConstraint(SpringLayout.NORTH, newFilter, 5, SpringLayout.SOUTH, prev);
      advLayout.putConstraint(SpringLayout.WEST, newFilter, 0, SpringLayout.WEST, filterListPanel);

      advLayout.putConstraint(SpringLayout.NORTH, addFilter, 10, SpringLayout.SOUTH, newFilter);
      advLayout.putConstraint(SpringLayout.WEST, addFilter, 5, SpringLayout.WEST, filterListPanel);

      advLayout.putConstraint(SpringLayout.SOUTH, filterListPanel, 10, SpringLayout.SOUTH, addFilter);
      advLayout.putConstraint(SpringLayout.EAST, filterListPanel, 0, SpringLayout.EAST, newFilter);
      filterListPanel.add(newFilter);
      filterListPanel.add(addFilter);
    }
    filterList.addElement(newFilter); // Add FilterPanel to vector

    repaintPanel();
    // Scroll to the bottom of the JScrollPane
    advScroll.getVerticalScrollBar().setValue(advScroll.getVerticalScrollBar().getMaximum());
  }

  /**
   * <p>Removes a {@link FilterPanel} from the {@link JPanel} and the {@link Vector}
   * containing all the current search filterList. The constraints of the remaining
   * {@link FilterPanel}s and the {@link JPanel} are adjusted.</p>
   *
   * @param filter
   *   The {@link FilterPanel} to be removed
   */
  public void removeFilter(FilterPanel filter)
  {
    clearTable();

    // First filter in list
    if (filter == filterList.firstElement())
    {
      // Set the second filter to be at the top of the JPanel
      FilterPanel tempFilter = filterList.get(1);

      advLayout.putConstraint(SpringLayout.NORTH, tempFilter, 0, SpringLayout.NORTH, filterListPanel);
      advLayout.putConstraint(SpringLayout.WEST, tempFilter, 0, SpringLayout.WEST, filterListPanel);

    }
    // Last Filter in List
    else if (filter == filterList.lastElement())
    {
      filterListPanel.remove(addFilter);

      // Set the JPanel to end at the second to last filter
      FilterPanel tempFilter = filterList.get(filterList.size() - 2);

      advLayout.putConstraint(SpringLayout.NORTH, addFilter, 10, SpringLayout.SOUTH, tempFilter);
      advLayout.putConstraint(SpringLayout.WEST, addFilter, 5, SpringLayout.WEST, filterListPanel);

      advLayout.putConstraint(SpringLayout.SOUTH, filterListPanel, 10, SpringLayout.SOUTH, addFilter);
      advLayout.putConstraint(SpringLayout.EAST, filterListPanel, 0, SpringLayout.EAST, tempFilter);

      filterListPanel.add(addFilter);
    }
    // Middle of List
    else
    {

      // Set the FilterPanel after the removed filter to the constraints of the previous FilterPanel
      FilterPanel nextFilter = filterList.get( filterList.indexOf(filter) + 1 ); // Get the next filter
      FilterPanel prevFilter = filterList.get( filterList.indexOf(filter) - 1 ); // Get the previous filter

      advLayout.putConstraint(SpringLayout.EAST, nextFilter, 0, SpringLayout.EAST, prevFilter);
      advLayout.putConstraint(SpringLayout.NORTH, nextFilter, 5, SpringLayout.SOUTH, prevFilter);
      advLayout.putConstraint(SpringLayout.WEST, nextFilter, 0, SpringLayout.WEST, filterListPanel);
    }

    advLayout.getConstraints(filter);
    filterList.remove(filter); // Remove the filter from the vector
    filterListPanel.remove(filter); // Remove the filter from the JPanel

    // Disable the removeFilter JButton if there is only 1 filter left
    if(filterList.size() == 1)
        filterList.firstElement().setRemoveButtonEnabled(false);

    if (matchAll.isSelected())
    {
      updateFilterPanelLists();
    }

    // Re-enable addFilter JButtons if the empty filter has been removed
    setAddButtonEnabled();

    repaintPanel();
  }

  /**
   * <p>Updates the advPanel contents</p>
   */
  public void repaintPanel()
  {
    advPanel.validate();
    advPanel.repaint();
    advScroll.repaint();
  }

  /**
   * <p>Gets the list of {@link FilterPanel}s</p>
   *
   * @return
   *  A {@link Vector} of {@link FilterPanel}s
   */
  public Vector<FilterPanel> getFilterList()
  {
    return filterList;
  }

  /**
   * <p>Determine whether the matchAll {@link JRadioButton} is selected.
   *
   * @return
   *  The {@link JRadioButton}'s "isSelected" value true/false
   */
  public boolean matchAllIsSelected()
  {
    return matchAll.isSelected();
  }

  /**
   * <p>Get the {@link Vector} of all available {@link MapData}</p>
   *
   * @return
   *  {@link Vector} of all available {@link MapData}
   */
  public Vector<MapData> getMapData()
  {
    return allAvailableMaps;
  }

  /**
   * <p>Get the list of possible species types</p>
   *
   * @return
   *  {@link Vector} of Species Name {@link String}s
   */
  public Vector<String> getSpeciesList()
  {
    return speciesList;
  }

  /**
   * <p>Get the list of possible annotation types</p>
   *
   * @return
   *  {@link Vector} of {@link Annotation} type {@link String}s
   */
  public Vector<String> getAnnotSetList()
  {
    return annotTypeList;
  }

  /**
   * <p>Get the list of possible map types</p>
   *
   * @return
   *  {@link Vector} of Map Type {@link String}s
   */
  public Vector<String> getMapTypeList()
  {
    return mapTypeList;
  }

  /**
   * <p>Get the list of possible map versions</p>
   *
   * @return
   *  {@link Vector} of Map Version {@link String}s
   */
  public Vector<String> getVersionList()
  {
    return versionList;
  }

  /**
   * <p>Get the list of possible units</p>
   *
   * @return
   *  {@link Vector} of Map Version {@link String}s
   */
  public Vector<String> getUnitList()
  {
    return unitList;
  }

  /**
   * <p>Builds the {@link String} {@link Vector}s for each type of filter. The
   * {@link Vector}s contain all possible selections.<p>
   */
  public void buildAllVectors()
  {
    speciesList = new Vector<String>();
    mapTypeList = new Vector<String>();
    versionList = new Vector<String>();
    annotTypeList = new Vector<String>();
    featureSourceList = new Vector<String>();

    mapsLeft.clear();
    mapsLeft.addAll(allAvailableMaps);

    // Add all options to the lists for the JComboBoxes
    for (MapData map : mapsLeft)
    {
      if (!speciesList.contains(map.getSpecies()))
        speciesList.addElement(map.getSpecies());

      if (!mapTypeList.contains(map.getTypeString()))
        mapTypeList.addElement(map.getTypeString());

      if (!versionList.contains(map.getVersion()))
        versionList.addElement(map.getVersion());
      
      for (AnnotationSet set : map.getAllAnnotationSets())
      {
        if (!annotTypeList.contains(set.getType()))
          annotTypeList.addElement(set.getType());
  
        if (!featureSourceList.contains(sources.get(set.getSourceId())))
          featureSourceList.addElement(sources.get(set.getSourceId()));
      }
    }

    // Add all options to the JComboBoxes in every FilterPanel
    for (FilterPanel fp : filterList)
    {
      if ( fp.getFilterType().equals("Species") )
        fp.updateSecondarySelectionBox(speciesList, "");

      if ( fp.getFilterType().equals("Map Type") )
        fp.updateSecondarySelectionBox(mapTypeList, "");

      if ( fp.getFilterType().equals("Map Version") )
        fp.updateSecondarySelectionBox(versionList, "");

      if ( fp.getFilterType().equals("Feature Type") )
        fp.updateSecondarySelectionBox(annotTypeList, "");

      if ( fp.getFilterType().equals("Feature Source") )
        fp.updateSecondarySelectionBox(featureSourceList, "");
    }
  }

  /**
   * <p>Builds the {@link String} {@link Vector}s for each type of filter. This is done
   *  by determining previous selections and eliminating choices fro the lists
   *  that would yield no results.</p>
   *
   * @param filter
   */
  public void buildFilterVectors(FilterPanel filter)
  {
    String species = "";
    String mapType = "";
    String mapVersion = null;
    String annotType = "";
    String featSource = "";

    // Get the currently selected items for each filter
    for (FilterPanel fp : filterList)
    {
      if (!fp.getFilterType().equals( filter.getFilterType() ))
      {
        if (fp.getFilterType().equals("Species"))
          species = (String) fp.getSecondarySelection();

        if (fp.getFilterType().equals("Map Type"))
          mapType = (String) fp.getSecondarySelection();

        if (fp.getFilterType().equals("Map Version"))
          mapVersion = (String) fp.getSecondarySelection();

        if (fp.getFilterType().equals("Feature Type"))
          annotType = (String) fp.getSecondarySelection();

        if (fp.getFilterType().equals("Feature Source"))
          featSource = (String) fp.getSecondarySelection();
      }
    }

    speciesList = new Vector<String>();
    mapTypeList = new Vector<String>();
    versionList = new Vector<String>();
    annotTypeList = new Vector<String>();
    featureSourceList = new Vector<String>();
    Vector<AnnotationSet> availableSets = new Vector<AnnotationSet>();

    mapsLeft.clear();

    // Add all matching maps to the vector
    for (MapData map : allAvailableMaps)
    {
      if ((map.getSpecies().equals(species) || species == null || species == "")
          && (map.getTypeString().equals(mapType) || mapType == null || mapType == "")
          && (map.getVersion().equals(mapVersion) || mapVersion == null)
          && (map.getAllAnnotationSetTypes().contains(annotType) || annotType == null || annotType == "")
          && (map.getAllAnnotationSetSources().contains(featSource) || featSource == null || featSource == ""))
      {
        mapsLeft.addElement(map);
        availableSets.addAll(map.getAllAnnotationSets());
      }
    }
    // Build new lists with only valid choices
    for (MapData map : mapsLeft)
    {
      if (!speciesList.contains(map.getSpecies()))
        speciesList.addElement(map.getSpecies());

      if (!mapTypeList.contains(map.getTypeString()))
        mapTypeList.addElement(map.getTypeString());

      if (!versionList.contains(map.getVersion()))
        versionList.addElement(map.getVersion());
      
      for (AnnotationSet set : map.getAllAnnotationSets())
      {
        if (!annotTypeList.contains(set.getType()))
          annotTypeList.addElement(set.getType());
  
        if (!featureSourceList.contains(sources.get(set.getSourceId())))
          featureSourceList.addElement(sources.get(set.getSourceId()));
      }
    }
  }

  /**
   * <p>Updates the {@link JComboBox}es contained in each {@link FilterPanel}.  Called
   * only when matchAll {@link JRadioButton} is selected.</p>
   */
  public void updateFilterPanelLists()
  {
    for (FilterPanel fp : filterList) // Do this for all filterList with JComboBoxes
    {
      buildFilterVectors(fp);

      if ( fp.getFilterType().equals("Species") )
        fp.updateSecondarySelectionBox(speciesList, "");

      if ( fp.getFilterType().equals("Map Type") )
        fp.updateSecondarySelectionBox(mapTypeList, "");

      if ( fp.getFilterType().equals("Map Version") )
        fp.updateSecondarySelectionBox(versionList, "");

      if ( fp.getFilterType().equals("Feature Type"))
        fp.updateSecondarySelectionBox(annotTypeList, "");

      if ( fp.getFilterType().equals("Feature Source"))
        fp.updateSecondarySelectionBox(featureSourceList, "");

      fp.repaint();
    }
    advPanel.repaint();
  }

  /**
   * <p>Displays the most recently modified {@link FilterPanel} in the {@link JScrollPane}</p>
   *
   * @param f
   *   The {@link FilterPanel} to be displayed
   */
  public void adjustView(FilterPanel f)
  {
    filterListPanel.scrollRectToVisible(f.getBounds());
    filterListPanel.repaint();
  }

  /**
   * <p>Checks the {@link Vector} of {@link FilterPanel}s to see if there are conflicting filterList</p>
   *
   * @return
   *  False if conflicting filterList are found, true otherwise
   */
  public boolean noMatchFound()
  {
    Vector<String> filterTypes = new Vector<String>();

    for(FilterPanel fp : filterList)
    {
      if ( !fp.getFilterType().equals("") )
      {
        if ( filterTypes.contains(fp.getFilterType()) )
          return false;

        filterTypes.add(fp.getFilterType());
      }
    }

    return true;
  }

  /**
   * <p>Checks the {@link Vector} of {@link FilterPanel}s for empty filterList.  If there are
   * empty filterList all addFilter {@link JButton}s are disabled otherwise they are enabled.</p>
   */
  public void setAddButtonEnabled()
  {

    boolean noEmptyfilterList = true;

    // Check each filter for a valid secondary selection
    for (FilterPanel fp : filterList)
    {
      if ( fp.getFilterType().equals("Location") || fp.getFilterType().equals("Feature Name") )
      {
        if ( fp.getTextField().getForeground() == Color.LIGHT_GRAY || fp.getKeyword().length() <= 0 )
        {
          noEmptyfilterList = false;
        }
      }

      else if ( fp.getSecondarySelection() == null || fp.getFilterType().equals("") || fp.getSecondarySelection().equals("") )
        noEmptyfilterList = false;
    }

    addFilter.setEnabled(noEmptyfilterList);
  }

  /**
   * <p>Show the instance of {@link SearchDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link PreferencesDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link SearchDialog}
   */
  public static void showSearchDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new SearchDialog(parent));
    SearchDialog instance = instances.get(parent);

    // Center dialog on parent
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
   * <p>Removes the instance of the {@link SearchDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link SearchDialog}
   */
  public static void closeSearchDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Clears the old information and cancels any current database searching
   * {@link Thread}s.  This is performed in a Thread safe manner by obtaining
   * lock for the results table.
   * </p>
   */
  public void clearTable()
  {
    synchronized (resultsLock)
    {
      if(asd != null) asd.cancel();

      // Tell the model to clear
      ((SearchResultsTableModel) hits.getModel()).clear();

      // Hide labels
      lDBAdvancedResults.setVisible(false);
      lLocalAdvancedResults.setVisible(false);
      lLoaderWheel.setVisible(false);
    }
  }

  /**
   * <p>Helper method that converts the input from the start and stop
   * JTextFields in the search by location area of the search dialog
   * into an integer.</p>
   *
   * @param text String to be converted into a integer
   * @return integer Value of input string. Will return -1 is unable to
   *   convert String
   */
  public int convertLocationTextToInt(String text)
  {
    StringBuilder stringToConvert = new StringBuilder();

    // Remove commas
    String[] commasRemoved = text.split(",");
    for (String tempString : commasRemoved)
      stringToConvert.append(tempString);

    // Convert
    int tempInt = -1;

    try
    {
      tempInt = Integer.parseInt(stringToConvert.toString());
    }
    catch (NumberFormatException nfe)
    {
      logger.debug("Unable to convert text in search location input fields to int");

      tempInt = -1;
    }

    return tempInt;
  }

  /**
   * <p>Builds a search string</p>
   *
   * @param s
   *  {@link String} that is being built for the search
   * @return
   *  A search {@link String} that does not require wildcards and ignores case
   */
  private String buildSearchString(String s)
  {
    char[] inputArray = s.toCharArray();

    StringBuilder inputCorrected = new StringBuilder();
    for (char inputChar : inputArray)
    {
      if (!Character.isDigit(inputChar) && !Character.isLetter(inputChar))
      {
        inputCorrected.append("\\");
        inputCorrected.append(inputChar);
      }
      else
      {
        inputCorrected.append(inputChar);
      }
    }

    // Properly format string so the search will ignore case and
    // not require wildcards
    return "(?i).*" + inputCorrected.toString() + ".*";

  }

  /**
   * <p>Enables or disables the {@link MouseListener} on the
   * {@link JTable}
   *
   * @param b
   *  True or false determines whether the {@link MouseListener} should
   *  be enabled or disabled
   */
  public void setJTableMouseListenerEnabled(boolean b)
  {
    mouseListenerIsActive = b;
  }

  /**
   * <p>Shows a {@link JOptionPane} containing data for the selected
   * {@link Annotation} in the {@link SearchDialog}s {@link JTable}.</p>
   */
  public void showOptionPane()
  {
    int row = hits.getSelectedRow();
    String[] str = ((String)hits.getValueAt(row, 6)).split("[\\s,][\\s-\\s]"); // Parse Species, map type, and map version
    DecimalFormat formatter = new DecimalFormat("###,###,###.###");

    // Get annotation info
    String featureName = "Feature Name: " + ((String)hits.getValueAt(row, 0));
    String species = "Species: " + str[0];
    String chromosome = "Chromosome: " + hits.getValueAt(row, 5);
    String mapType = "Map Type: " + str[1].substring(1);
    String mapVersion = "Map Version: " + str[2];
    String start, stop;
    try
    {
      start = "Start: " + formatter.format(Double.parseDouble(hits.getValueAt(row, 2).toString())) + hits.getValueAt(row, 4).toString();
      stop = "Stop: " + formatter.format(Double.parseDouble(hits.getValueAt(row, 3).toString())) + hits.getValueAt(row, 4).toString();
    }
    catch (NumberFormatException nfe)
    {
      start = "Start:";
      stop = "Stop:";
    }

    String message;


    Object[] options = {"Load", "Cancel"};
    checkbox = null;
    String title;

    if ( ((String)hits.getValueAt(row, 7)).equals("No") )
    {
      message = LOAD_TEXT;
      checkbox = new JCheckBox("Load map in a new window");
      title = "Load Map";
      options[0] = "Load";
    }
    else if ( ((String)hits.getValueAt(row, 7)).equals("Yes") )
    {
      message = INFO_TEXT;
      title = "Annotation Details";
      options[0] = "OK";
    }
    else
    {
      message = UNHIDE_TEXT;
      title = "Show Annotation";
      options[0] = "Show";
    }

    Object[] parameters = {featureName, species, chromosome, mapType, mapVersion, start, stop, message, checkbox};

    // Get user choice
    int choice = JOptionPane.showOptionDialog(this,
        parameters,
        title,
        JOptionPane.YES_NO_OPTION,
        JOptionPane.INFORMATION_MESSAGE,
        null,
        options,
        options[0]);

    String selection = (String)options[choice];
    String chr = "chr" + hits.getValueAt(row, 5).toString();

    if (selection.equals("Load"))
    {
      MapData newMap = null;
      for (MapData m : allAvailableMaps)
      {
        if (m.getName().equals(hits.getValueAt(row, 6)))
          newMap = m;
      }

      newAnnotationName = (String)hits.getValueAt(row, 0);
      annotSet = (AnnotationSet)hits.getValueAt(row, 1);
      try
      {
        annotStart = Double.parseDouble(hits.getValueAt(row, 2).toString());
        annotStop = Double.parseDouble(hits.getValueAt(row, 3).toString());
      }
      catch (NumberFormatException nfe)
      {
        annotStart = -1;
        annotStop = -1;
      }

      if (checkbox.isSelected())
      {
        newMainGUI = new MainGUI(mainGUI.getVCMap());
      }
      else
      {
        canClearData = false;
        setVisible(false);
        newMainGUI = mainGUI;
      }

      newMainGUI.loadMap(true, newMap, chr, this);
    }
    else if (selection.equals("Show"))
    {
      String type = str[1].substring(1);

      mainGUI.getMapNavigator().showAnnotationByName(str[0], type, str[2], hits.getValueAt(row, 5).toString(), hits.getValueAt(row, 0).toString());
      setVisible(false);
    }
  }

  /**
   * <p>Selects the annotation that initiated the loading of a new backbone map.</p>
   */
  public void selectAnnotation()
  {
    // Set annotation as selected
    for (DisplayMap map : newMainGUI.getMapNavigator().getDisplayMaps())
      for (DisplaySegment segment : map.getSegments())
        for (Annotation annotation : segment.getSegmentFeatures())
        {
          double start = ((double)annotation.getStart())/((double)map.getMap().getScale());
          double stop = ((double)annotation.getStop())/((double)map.getMap().getScale());

          if (start == annotStart && stop == annotStop && annotation.getName().equals(newAnnotationName))
          {
            newMainGUI.getMapNavigator().getSelection().addAnnotation(
                newMainGUI.getMapNavigator().getBackboneDisplaySegment(), annotation);
            newAnnotationName = null;
          }
        }
    // Show selection in status bar
    mainGUI.getStatusBar().updateStatusBar();
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.MapLoader#mapLoadCompleted(boolean, int, java.lang.String)
   */
  public void mapLoadCompleted(boolean successful, int messageType, String message)
  {
    canClearData = true;

    if (successful)
    {
      logger.debug(message);

      if (!checkbox.isSelected())
        clearTable();

      if (newAnnotationName != null)
        for (DisplayMap map : newMainGUI.getMapNavigator().getDisplayMaps())
        {
          // Only load annotation if it hasn't been loaded already
          if (!map.getAllAnnotationSets().contains(annotSet))
            newMainGUI.loadAnnotation(map, annotSet, this);
          else
            selectAnnotation();
        }
    }
    else
    {
      if (messageType == MainGUI.SYNTENY_ERROR)
        JOptionPane.showMessageDialog(this,
            message,
            "No Data Loaded",
            JOptionPane.ERROR_MESSAGE);
    }
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.AnnotationLoader#annotationLoadCompleted(boolean, int, java.lang.String)
   */
  public void annotationLoadCompleted(boolean successful, int messageType, String message)
  {
    if (successful)
    {
      newMainGUI.getMapNavigator().repaint();
      selectAnnotation();
    }
  }

  /*
   * Overridden to ensure that when the SearchDialog is visible, the parent
   * is no longer enabled, but whenever the SearchDialog is hidden, the parent
   * is enabled again.
   * NOTE: handling clearing the table is done here, but we need to ensure
   * that the table is not cleared on a failed load...
   */
  public void setVisible(boolean show)
  {
    super.setVisible(show);

    if (!show && canClearData) clearTable();
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand() == "Close")
    {
      setVisible(false);
    }
    else if (ae.getActionCommand() == "AddFilter")
    {
      addNewFilter();
      setAddButtonEnabled();
    }
    else if (ae.getActionCommand() == "Match Any")
    {
      buildAllVectors();
    }
    else if (ae.getActionCommand() == "Match All")
    {
      if (noMatchFound()) // Check for duplicate filterList
        updateFilterPanelLists();
      else
      {
        JOptionPane.showMessageDialog(this,
            "The search contains one or more conditions\n" +
            "that confilict with each other. This may\n" +
            "result in the search returning no results.\n" +
            "Please remove these conflicting search \n" +
            "parameters before selecting the match all\n" +
            "radio button.", "Error!",
            JOptionPane.ERROR_MESSAGE);

        matchAny.setSelected(true); // Reset selection back to Match Any
      }
    }
    else if (ae.getActionCommand() == "AdvancedSearch")
    {
      // Clear old results / cancel any old searches
      clearTable();

      // Now start the database search
      asd = new AdvancedSearchDatabase();
      // SGD - no!
      //asd.setPriority(Thread.MAX_PRIORITY);
      asd.start();
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
   */
  public void mouseClicked(MouseEvent e)
  {
    if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2 && mouseListenerIsActive) // If double clicked
    {
      // Clear row
      hits.clearSelection();
      Point point = e.getPoint();
      int row = hits.rowAtPoint(point);
      hits.addRowSelectionInterval(row, row);

      // Create a new JOptionPane using the data from the row
      showOptionPane();
    }
  }

  public void mouseEntered(MouseEvent e){}
  public void mouseExited(MouseEvent e) {}
  public void mousePressed(MouseEvent e) {}
  public void mouseReleased(MouseEvent e) {}

  /**
   * This class defines a separate {@link Thread} that can be used to load the
   * {@link MapData} objects into the {@link SearchDialog} so the {@link SearchDialog}
   * frame can still be rendered.
   * @author cgoodman
   *
   */
  class LoadData
    extends Thread
  {
    public void run()
    {
      allAvailableMaps = null;
      annotTypeList = new Vector<String>();

      try
      {
        // Get all maps
        allAvailableMaps = Factory.getAvailableMaps();

        // Used when determining selections left for JComboBoxes
        mapsLeft = new Vector<MapData>();
        mapsLeft.addAll(allAvailableMaps);

        // Get all annot types
        for (MapData map : allAvailableMaps)
          for (AnnotationSet set : map.getAllAnnotationSets())
            if (!annotTypeList.contains(set.getType()))
              annotTypeList.addElement(set.getType());
      } // end try
      catch (SQLException e)
      {
        if (allAvailableMaps == null)
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

          filterList.firstElement().setFilterTypeEnabled(true);
        }
      } // end catch

      buildAllVectors();
      filterList.firstElement().getFilterTypeBox().removeItem("Please wait...");
      filterList.firstElement().getFilterTypeBox().setSelectedItem("");
      filterList.firstElement().setFilterTypeItemListenerEnabled(true);

      // Enable components
      matchAll.setSelected(true);
      filterList.firstElement().setFilterTypeEnabled(true);
      matchAll.setEnabled(true);
      matchAny.setEnabled(true);
      addFilter.setEnabled(false);
      searchAdv.setEnabled(true);

      logger.debug("SearchDialog: Finishing loading all map data...");
    }
  }

  /**
   * <p>
   * Searches the database for {@link Annotation} on a separate {@link Thread}
   * so the dialog doesn't block during the potentially slow search.
   * </p>
   *
   * @return
   *   {@link Annotation} {@link Vector} containing the search matches
   */
  class AdvancedSearchDatabase
    extends Thread
  {
    boolean canceled = false;

    /**
     * <p>Cancel the database search.
     * <p>NOTE: This {@link Thread} only ever adds data to
     * the search results table never removes, therefore, anything that calls
     * this cancel() method must also make sure to clear the results table as
     * well.
     */
    public void cancel()
    {
      canceled = true;
    }

    /**
     * <p>Searches the maps loaded into memory using the Advanced Search in the {@link SearchDialog}</p>
     *
     * @param dm
     *   {@link Vector} of {@link DispayMap}s
     * @return
     *   The count of the number of local results found.
     */
    private int advancedSearchLocal(Vector<DisplayMap> dm)
    {
      int localResults = 0;

      boolean match, matchAllValue = matchAll.isSelected();

      for (DisplayMap map : dm)
      {
        for (DisplaySegment segment : map.getSegments())
        {
          for (Annotation annotation : segment.getSegmentFeatures())
          {
            match = matchAllValue;
            for (FilterPanel fp : filterList) // Compare all filterList to each annotation
            {
              /*
               * For match any search, add annotation to the list if any match to the filterList is found
               * For match all search, do not add annotation if it fails to meet any of filter criteria
               */
              if (fp.getFilterType().equals("Feature Name") )
              {
                if (fp.getKeyword() != "" )
                {
                  String name = annotation.getName();

                  if (name != null)
                    if ( ( !matchAllValue && name.matches( buildSearchString(fp.getKeyword()) ) ) ||
                       ( ( matchAllValue && !name.matches( buildSearchString(fp.getKeyword()) ) ) ) )
                      match = !matchAllValue;
                }
              }
              else if (fp.getFilterType().equals("Location"))
              {
                String[] split = null;
                String word = null;
                String unit = null;

                if (!fp.getKeyword().equals("") && fp.getTextField().getForeground() != Color.LIGHT_GRAY)
                {
                  word = fp.getKeyword();

                  split = word.split("[-: ]");

                  int wordStart = -1;
                  int wordStop = -1;
                  wordStart = word.indexOf('(');
                  wordStop = word.indexOf(')');

                  if (wordStart >= 0 && wordStop > 0)
                    unit = word.substring(wordStart + 1, wordStop);

                  String chromosome = "";
                  int start = -1;
                  int stop = -1;

                  if (split.length > 0 && split[0] != null)
                    chromosome = split[0];

                  if (split.length > 1 && split[1] != null)
                    start = convertLocationTextToInt(split[1]);

                  if (split.length > 2 && split[2] != null)
                    stop = convertLocationTextToInt(split[2]);

                  if ( ( !matchAllValue && ( unit == null || ( unit != null &&
                      unit.equalsIgnoreCase( annotation.getChromosome().getMap().getUnitsString() ) ) ) &&
                         chromosome.equals( annotation.getChromosome().getName() ) &&
                         ( ( start <= annotation.getStart() && annotation.getStop() <= stop ) ||
                           ( start <= annotation.getStart() && stop <= 0 ) ||
                           ( start <= 0 && stop <= 0 ) ) ) ||
                        ( matchAllValue && ( !chromosome.equals( annotation.getChromosome().getName() ) ||
                        ( unit != null &&
                          !unit.equalsIgnoreCase( annotation.getChromosome().getMap().getUnitsString() ) ) ||
                        ( start >  annotation.getStart() ||
                            ( annotation.getStop() >  stop && stop > 0 ) ) ) ) )
                    match = !matchAllValue;
                }
              }
              else if (fp.getFilterType().equals("Species"))
              {
                if ( ( !matchAllValue && fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getSpecies() ) ) ||
                     ( matchAllValue && !fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getSpecies() ) ) )
                    match = !matchAllValue;
              }
              else if (fp.getFilterType().equals("Feature Type"))
              {
                if ( ( !matchAllValue && fp.getSecondarySelection().equals( annotation.getAnnotationSet().getType() ) ) ||
                     ( matchAllValue && !fp.getSecondarySelection().equals( annotation.getAnnotationSet().getType() ) ) )
                    match = !matchAllValue;
              }
              else if (fp.getFilterType().equals("Feature Source"))
              {
                if ( ( !matchAllValue && fp.getSecondarySelection().equals( sources.get(annotation.getChromosome().getMap().getSourceId()) ) ) ||
                     ( matchAllValue && !fp.getSecondarySelection().equals( sources.get(annotation.getChromosome().getMap().getSourceId()) ) ) )
                    match = !matchAllValue;
              }
              else if (fp.getFilterType().equals("Map Type"))
              {
                if ( ( !matchAllValue && fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getTypeString() ) ) ||
                     ( matchAllValue && !fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getTypeString() ) ) )
                    match = !matchAllValue;
              }
              else if (fp.getFilterType().equals("Map Version"))
              {
                if ( ( !matchAllValue && fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getVersion() ) ) ||
                     ( matchAllValue && !fp.getSecondarySelection().equals( annotation.getChromosome().getMap().getVersion() ) ) )
                    match = !matchAllValue;
              }
            } // End filter loop

            if (match) // if the search meets the criteria add it to the Annotation vector
            {
              synchronized (resultsLock)
              {
                if (!canceled)
                {
                  localResults++;
                  ((SearchResultsTableModel) hits.getModel()).addRow(annotation);
                }
                else
                {
                  // When canceled, don't display the local results label
                  return -1;
                }
              }
            }
          }
        }
      }

      return localResults;
    }

    /**
     * Query the database and add the results in real-time.  This {@link Thread}
     * must be careful to add the results in a {@link Thread}-safe manner.
     */
    public void run()
    {
      //
      // Disable components
      //
      lLoaderWheel.setVisible(true);
      hits.setRowSortingEnabled(false);
      setJTableMouseListenerEnabled(false);

      //
      // First, local results
      //
      Vector<DisplayMap> displayMaps = mainGUI.getMapNavigator().getDisplayMapsInOrderOfPosition();
      int localResults = advancedSearchLocal(displayMaps);

      // Display the local results label
      if (localResults == 0)
      {
        lLocalAdvancedResults.setText("No Results From Loaded Maps");
        lLocalAdvancedResults.setForeground(Color.RED);
        lLocalAdvancedResults.setVisible(true);
      }
      else if (localResults > 0)
      {
        lLocalAdvancedResults.setText("Results From Loaded Maps: " + localResults);
        lLocalAdvancedResults.setForeground(Color.black);
        lLocalAdvancedResults.setVisible(true);
      }

      //
      // Second, database results
      //
      StringBuilder query = new StringBuilder();

      // Build initial query
      query.append("SELECT a.id, a.name, aset.type, a.start, a.stop, m.units, ");
      query.append("c.name, m.species, m.type, v.human_name, m.scale, m.id ");
      query.append("FROM annotation a,  chromosomes c, maps m, annotation_sets aset, ");
      for (FilterPanel fp : filterList)
        if(fp.getFilterType().equals("Feature Name"))
          query.append("annotation_avps ap, attributes at, vals, ");
      query.append("versions v, versions v2 ");
      query.append("WHERE a.annotation_set_id=aset.id AND m.id=aset.map_id AND ");
      query.append("a.chromosome_id=c.id AND c.map_id=m.id AND m.version_id=v.id AND ");
      query.append("aset.version_id=v2.id AND ( ");

      // Add additional search criteria based on each filter
      for (FilterPanel fp : filterList) // Compare all filterList to each annotation
      {
        String str = "";

        if (fp.getFilterType().equals("Feature Name") )
        {
          if (!fp.getKeyword().equals("") )
          {
            str = "( ap.annotation_id=a.id AND ap.attribute_id=at.id AND ap.value_id=vals.id";
            str += " AND (at.type='alias') ) AND ";
            str += "(a.name LIKE '%" + fp.getKeyword() + "%' OR vals.value LIKE '%" + fp.getKeyword() + "%' ) ";
          }
        }
        else if (fp.getFilterType().equals("Location") )
        {
          String[] split = null;
          String word = null;
          String unit = null;

          if (!fp.getKeyword().equals("") && fp.getTextField().getForeground() != Color.LIGHT_GRAY)
          {
            word = fp.getKeyword();

            split = word.split("[-: ]");

            int wordStart = -1;
            int wordStop = -1;
            wordStart = word.indexOf('(');
            wordStop = word.indexOf(')');

            if (wordStart >= 0 && wordStop > 0)
              unit = word.substring(wordStart + 1, wordStop);

            String chromosome = "";
            int start = -1;
            int stop = -1;

            if (split.length > 0 && split[0] != null)
              chromosome = split[0];

            if (split.length > 1 && split[1] != null)
              start = convertLocationTextToInt(split[1]);

            if (split.length > 2 && split[2] != null)
              stop = convertLocationTextToInt(split[2]);

            str = "(c.name='" + chromosome + "'";

            if (unit != null)
              str += " AND m.units='" + unit + "'";

            if (start > 1 || stop > 0)
              str += " AND ";

            if (start <= 1 && stop <= 0)
              str += ""; // Search the whole chromosome, searches nothing if no chromosome is selected

            else if (start > 1 && stop > 0)
              str += "(a.start>=" + start + " AND a.stop<=" + stop + ") ";

            else if (start > 1)
              str += "a.start>=" + start;

            else if (stop > 0)
              str += "a.stop<=" + stop;

            str += ") ";
          }
        }
        else if ( fp.getSecondarySelection() != "" )
        {
          if (fp.getFilterType().equals("Species") )
          {
            str = "m.species='" + fp.getSecondarySelection() + "' ";
          }
          else if (fp.getFilterType().equals("Feature Type") )
          {
            str = "aset.type='" + fp.getSecondarySelection() + "' ";
          }
          else if (fp.getFilterType().equals("Feature Source") )
          {
            String source = (String) fp.getSecondarySelection();
            int id = -1;
            for (int key : sources.keySet())
              if(source.equals(sources.get(key)))
              {
                id = key;
                str = "v2.source_id=" + id + " ";
                break;
              }
          }
          else if (fp.getFilterType().equals("Map Type") )
          {
            if (fp.getSecondarySelection().equals("Radiation Hybrid") )
              str = "m.type='RH' ";
            else
              str = "m.type='" + fp.getSecondarySelection() + "' ";
          }
          else if (fp.getFilterType().equals("Map Version") )
          {
            str = "m.human_name='" + fp.getSecondarySelection() + "' ";
          }
        }

        // Append query string if it is valid
        if ( str != "" )
        {
          // AND search criteria if the Match All JRadioButton is selected
          // Otherwise OR search criteria together
          if (fp != filterList.firstElement())
          {
            if (matchAll.isSelected())
              query.append("AND ");
            else
              query.append("OR ");
          }
          query.append(str);
        }
      } // End filter loop
      query.append(") LIMIT 0, " + MAX_RESULTS);
      // Counter for database related search results
      int count = 0, dbResults = 0;
      Statement st = null;
      try
      {
        st = Factory.getStatement();
        st.setFetchSize(FETCH_BLOCK_SIZE);

        // Execute query
        ResultSet rs = st.executeQuery(query.toString());
        Font f = lDBAdvancedResults.getFont();
        lDBAdvancedResults.setFont(f.deriveFont(Font.PLAIN));

        //
        // Loop through and add results until finished or canceled
        //
        while (rs.next() && !canceled)
        {
          // We count DB results whether or not they are duplicates
          dbResults++;

          // Only add results that have not been found locally
          if (!((SearchResultsTableModel) hits.getModel()).containsId(rs.getInt(1), rs.getInt(12)))
          {
            // We only display the non-duplicate count of DB results
            count++;
            lDBAdvancedResults.setText("Results From Database: " + count);
            lDBAdvancedResults.setForeground(Color.black);
            lDBAdvancedResults.setVisible(true);

            // {"Name", "Type", "Start", "Stop", "Chromosome", "Map", "Database"};
            try
            {
              String[] result = new String[8];
              result[0] = rs.getString(2).intern();
              result[1] = rs.getString(3).intern();
              result[2] = Util.formatUnit(rs.getInt(4), rs.getInt(11));
              result[3] = Util.formatUnit(rs.getInt(5), rs.getInt(11));
              result[4] = rs.getString(6);
              // Remove the 'chr' from the beginning of the chromosome name
              result[5] = rs.getString(7).substring(3).intern();
              result[6] = (rs.getString(8) + " - " + rs.getString(9).replace("RH", "Radiation Hybrid") + ", " + rs.getString(10)).intern();
              result[7] = "No";

              // Add results synchronously
              synchronized (resultsLock)
              {
                if (!canceled) ((SearchResultsTableModel) hits.getModel()).addRow(result);
              }
            }
            catch (OutOfMemoryError e)
            {
              //System.out.println("Results Loaded: " + count);
              logger.warn("Out of memory. Database results " + count);

              break;
            }
          }
        }

        logger.debug("Fetched search results from database successfully");
      }
      catch (SQLException e)
      {
        logger.warn("Factory: Problem querying for search results" + " (" + e + ")");
      }
      finally
      {
        // Close the Statement if it was opened
        try
        {
          if (st != null) st.cancel();
          if (st != null) st.close();
        }
        catch (SQLException e)
        {
          // Do Nothing
        }

        // Ensure results aren't cleared or a cancel doesn't happen in the
        // middle of this.
        synchronized (resultsLock)
        {
          if (!canceled)
          {
            // Display the database results label
            if (count == 0)
            {
              lDBAdvancedResults.setText("No Results From Database");
              lDBAdvancedResults.setForeground(Color.RED);
              lDBAdvancedResults.setVisible(true);
            }
            else if (dbResults >= MAX_RESULTS)
            {
              Font f = lDBAdvancedResults.getFont();
              lDBAdvancedResults.setFont(f.deriveFont(Font.BOLD));

              lDBAdvancedResults.setText("Results From Database: >" + MAX_RESULTS);
              lDBAdvancedResults.setForeground(Color.BLACK);
              lDBAdvancedResults.setVisible(true);

              JOptionPane.showMessageDialog(SearchDialog.this,
                  "Your search has returned more than " + Util.commify(MAX_RESULTS) + "\n" +
                  "results. To reduce memory usage only the first\n" +
                  Util.commify(MAX_RESULTS) + " results were returned. To shorten the\n" +
                  "list of results try adding more search\n" +
                  "criteria by selecting the '+' button located\n" +
                  "below the search criteria, or narrow the results\n" +
                  "by selecting the 'Match All' option located\n" +
                  "near the 'Search' button.",
                  "Warning",
                  JOptionPane.WARNING_MESSAGE);
            }
            else
            {
              lDBAdvancedResults.setText("Results From Database: " + count);
              lDBAdvancedResults.setForeground(Color.black);
              lDBAdvancedResults.setVisible(true);
            }
          }
        }
      }

      //
      // Finally, Re-enable components
      //
      hits.setRowSortingEnabled(true);
      lLoaderWheel.setVisible(false);
      setJTableMouseListenerEnabled(true);
    }
  }
}
