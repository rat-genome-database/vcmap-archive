package bioneos.vcmap.gui.dialogs;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableModel;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.components.SortingTable;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.SelectionInterval;
import bioneos.vcmap.util.Util;

/**
 * <p>This class displays a {@link JDialog} that shows the user information of
 * data they have selected in the {@link MapNavigator}. The user has the
 * ability to alter how this data is displayed and has the ability to save the
 * data in a .csv file.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class DownloadDialog
  extends VCMDialog
  implements ActionListener, ListSelectionListener, MouseListener
{
  // Singleton design pattern
  private static HashMap<MainGUI, DownloadDialog> instances = new HashMap<MainGUI, DownloadDialog>();

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // MapNavigator
  private MapNavigator mapNavigator;

  // GUI Components
  private JList types;
  private JList shownData;
  private JList hiddenData;
  private DefaultListModel shownDataModel;
  private DefaultListModel hiddenDataModel;
  private SortingTable previewTable;
  private SortingTable intervalTable;
  private JButton open;
  private JButton cancel;
  private JButton typeSelectAll;
  private JButton intervalSelectAll;
  private JButton hideData;
  private JButton showData;
  private JLabel lNumAnnotationChosen;
  private DefaultTableModel previewTableModel;
  private DefaultTableModel intervalTableModel;
  private DefaultListModel typesModel;

  private String numAnnotationString = "Number of Annotation: ";
  private String mapString = "Map: ";
  private String intervalStartString = "Inverval Start: ";
  private String intervalStopString = "Interval Stop: ";

  private HashMap<SelectionInterval, Vector<Annotation>> intervalToAnnot;
  private Vector<SelectionInterval> intervals;
  private HashSet<SelectionInterval> selectedIntervals;
  private HashSet<Annotation> annotation;
  private Vector<Annotation> filteredAnnotation;
  private Map<String, Integer> columnWidth;
  private boolean manualColumnChange = false;
  private boolean settingUp = true;

  // Strings
  private String[] intervalTableTitles = {"Map", "Chromosome", "Start", "Stop", "Units"};
  private String[] defaultHiddenTitles = {"Alias",
                                         "Annotation Type",
                                         "Start Position",
                                         "Stop Position",
                                         "Units",
                                         "Chromosome",
                                         "Species",
                                         "Map Type",
                                         "Source",
                                         "Version",
                                         "Details"};
  private String[] defaultShownTitles = {"Name"};

  /**
   * <p>Constructor for {@link DownloadDialog}. Creates {@link DownloadDialog}
   * from the information in the {@link MapNavigator} of the {@link MainGUI}.
   * The constructor is private so that only this class can create an instance
   * of {@link DownloadDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private DownloadDialog(MainGUI parent)
  {
    super(parent, false);

    logger.debug("Creating instance of DownloadDebug");

    this.mapNavigator = parent.getMapNavigator();

    // Sets edges for all borders
    Border paneEdge = BorderFactory.createEmptyBorder(0,0,0,0);

    // Initialize misc variables
    selectedIntervals = new HashSet<SelectionInterval>();
    intervals = new Vector<SelectionInterval>();

    // Initialize columnWidth variable
    columnWidth = new HashMap<String, Integer>();

    // Component setup
    JLabel typesL = new JLabel("Type:");
    JLabel shownDataL = new JLabel("Shown Data:");
    JLabel hiddenDataL = new JLabel("Hidden Data:");
    lNumAnnotationChosen = new JLabel(numAnnotationString);

    intervalTableModel = new DefaultTableModel();
    intervalTable = new SortingTable(intervalTableModel)
      {
        public boolean isCellEditable(int row, int column)
        {
          return false;
        }
      };
    intervalTable.setColumnSelectionAllowed(false);
    intervalTable.setRowSelectionAllowed(true);
    intervalTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    intervalTable.getTableHeader().addMouseListener(this);

    ListSelectionModel intervalTableSM = intervalTable.getSelectionModel();
    intervalTableSM.addListSelectionListener(new ListSelectionListener()
      {
        public void valueChanged(ListSelectionEvent e)
        {
          if (e.getValueIsAdjusting()) return;

          ListSelectionModel iTSM = (ListSelectionModel)e.getSource();

          if (!iTSM.isSelectionEmpty() && !settingUp)
          {
            int min = iTSM.getMinSelectionIndex();
            int max = iTSM.getMaxSelectionIndex();
            selectedIntervals = new HashSet<SelectionInterval>();

            for (int i = min; i <= max; i++)
            {
              if (iTSM.isSelectedIndex(i) && i < intervals.size())
                selectedIntervals.add(intervals.get(i));
            }

            generateTableData();
          }
        }
      });


    for (String title : intervalTableTitles)
      intervalTableModel.addColumn(title);

    JScrollPane intervalTableScrollPane = new JScrollPane(intervalTable);
    intervalTableScrollPane.setPreferredSize(new Dimension(425, 75));

    previewTableModel = new DefaultTableModel();
    previewTable = new SortingTable(previewTableModel)
      {
        public boolean isCellEditable(int row, int column)
        {
          return false;
        }
      };
    previewTable.setBackground(previewTable.getSelectionBackground().brighter());
    previewTable.setColumnSelectionAllowed(false);
    previewTable.setRowSelectionAllowed(false);
    previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    previewTable.getTableHeader().addMouseListener(this);

    previewTable.getColumnModel().addColumnModelListener(new TableColumnModelListener()
      {
        public void columnMoved(TableColumnModelEvent e)
        {
          // Make sure order column titles is the same order as the list of shown
          shownDataModel.clear();

          for (int i = 0; i < previewTable.getColumnCount(); i++)
            shownDataModel.add(i, previewTable.getColumnName(i));
        }

        public void columnMarginChanged(ChangeEvent e)
        {
          // When column resized, save this column size for later use
          if (!manualColumnChange)
            for (int i = 0 ; i < previewTableModel.getColumnCount(); i++)
              if (shownDataModel.contains(previewTable.getColumnName(i)))
                columnWidth.put(previewTable.getColumnName(i),
                    new Integer(previewTable.getColumnModel().getColumn(i).getWidth()));
        }
        public void columnAdded(TableColumnModelEvent e) {}
        public void columnRemoved(TableColumnModelEvent e) {}
        public void columnSelectionChanged(ListSelectionEvent e) {}
      });

    JScrollPane previewTableScrollPane = new JScrollPane(previewTable);
    previewTableScrollPane.setPreferredSize(new Dimension(425, 150));

    typesModel = new DefaultListModel();
    types = new JList(typesModel);
    types.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    types.setVisibleRowCount(5);
    types.addListSelectionListener(this);
    JScrollPane typesScroll = new JScrollPane(types);

    shownDataModel = new DefaultListModel();
    shownData = new JList(shownDataModel);
    shownData.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    shownData.setVisibleRowCount(5);
    shownData.addListSelectionListener(this);
    shownData.addMouseListener(this);
    JScrollPane shownDataScroll = new JScrollPane(shownData);
    shownDataScroll.setVerticalScrollBarPolicy(shownDataScroll.VERTICAL_SCROLLBAR_ALWAYS);

    hiddenDataModel = new DefaultListModel();
    hiddenData = new JList(hiddenDataModel);
    hiddenData.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    hiddenData.setVisibleRowCount(5);
    hiddenData.addListSelectionListener(this);
    hiddenData.addMouseListener(this);
    JScrollPane hiddenDataScroll = new JScrollPane(hiddenData);
    hiddenDataScroll.setVerticalScrollBarPolicy(hiddenDataScroll.VERTICAL_SCROLLBAR_ALWAYS);

    for (String columnTitle : defaultHiddenTitles)
      hiddenDataModel.addElement(columnTitle);

    for (String columnTitle : defaultShownTitles)
      shownDataModel.addElement(columnTitle);

    JTextField forSpacing = new JTextField();
    forSpacing.setVisible(false);
    forSpacing.setFont(new Font("default", Font.PLAIN, 1));

    typeSelectAll = new JButton("Select All");
    typeSelectAll.addActionListener(this);
    typeSelectAll.setEnabled(false);

    intervalSelectAll = new JButton("Select All");
    intervalSelectAll.addActionListener(this);
    intervalSelectAll.setEnabled(false);

    hideData = new JButton("<");
    hideData.addActionListener(this);
    hideData.setEnabled(false);
    hideData.setActionCommand("Hide");

    showData = new JButton(">");
    showData.addActionListener(this);
    showData.setEnabled(false);
    showData.setActionCommand("Show");

    open = new JButton("Open");
    open.addActionListener(this);
    open.setEnabled(false);
    open.setToolTipText("Open data in your default spreadsheet editor.");

    cancel = new JButton("Cancel");
    cancel.addActionListener(this);
    cancel.setEnabled(true);

    //
    // Construct Panel 'main' panel
    //
    SpringLayout s = new SpringLayout();
    JPanel main = new JPanel(s);

    //
    //  Construct Panel and Layout for 'Interval Info' portion
    //
    JPanel intervalBorder = new JPanel();
    intervalBorder.setBorder(paneEdge);
    intervalBorder.setLayout(new BoxLayout(intervalBorder, BoxLayout.Y_AXIS));

    SpringLayout iL = new SpringLayout();
    JPanel iComp = new JPanel(iL, false);

    iL.putConstraint(SpringLayout.WEST, intervalTableScrollPane, 5, SpringLayout.WEST, iComp);
    iL.putConstraint(SpringLayout.NORTH, intervalTableScrollPane, 5, SpringLayout.NORTH, iComp);
    iL.putConstraint(SpringLayout.NORTH, intervalSelectAll, 5, SpringLayout.SOUTH, intervalTableScrollPane);
    iL.putConstraint(SpringLayout.EAST, intervalSelectAll, 0, SpringLayout.EAST, intervalTableScrollPane);
    iL.putConstraint(SpringLayout.SOUTH, iComp, 5, SpringLayout.SOUTH, intervalSelectAll);
    iL.putConstraint(SpringLayout.EAST, iComp, 5, SpringLayout.EAST, intervalTableScrollPane);

    iComp.add(intervalTableScrollPane);
    iComp.add(intervalSelectAll);
    iComp.setBorder(BorderFactory.createTitledBorder("Interval Info") );

    // Finalize 'Interval' panel
    intervalBorder.add(Box.createRigidArea(new Dimension(0, 10)));
    intervalBorder.add(iComp);

    //
    // Construct Panel and Layout for 'Annotation Selection' portion
    //
    JPanel annotSelBorder = new JPanel();
    annotSelBorder.setBorder(paneEdge);
    annotSelBorder.setLayout(new BoxLayout(annotSelBorder, BoxLayout.Y_AXIS));

    SpringLayout aL = new SpringLayout();
    JPanel aComp = new JPanel(aL, false);

    // Set up layout of 'Annotation Selection' portion
    aL.putConstraint(SpringLayout.WEST, typesL, 5, SpringLayout.WEST, aComp);
    aL.putConstraint(SpringLayout.NORTH, typesL, 5, SpringLayout.NORTH, aComp);
    aL.putConstraint(SpringLayout.NORTH, typesScroll, 5, SpringLayout.SOUTH, typesL);
    aL.putConstraint(SpringLayout.WEST, typesScroll, 0, SpringLayout.WEST, typesL);
    aL.putConstraint(SpringLayout.NORTH, typeSelectAll, 3, SpringLayout.SOUTH, typesScroll);
    aL.putConstraint(SpringLayout.EAST, typeSelectAll, 0, SpringLayout.EAST, typesScroll);
    aL.putConstraint(SpringLayout.EAST, aComp, 5, SpringLayout.EAST, typeSelectAll);
    aL.putConstraint(SpringLayout.SOUTH, aComp, 5, SpringLayout.SOUTH, typeSelectAll);

    // Add components to 'Annotation Selection' panel
    aComp.add(typesScroll);
    aComp.add(typesL);
    aComp.add(typeSelectAll);
    aComp.setBorder(BorderFactory.createTitledBorder("Annotation Selection") );

    // Finalize 'Annotation Selection' panel
    annotSelBorder.add(Box.createRigidArea(new Dimension(0, 10)));
    annotSelBorder.add(aComp);

    //
    // Construct Panel and Layout for 'Show/Hide Data' portion
    //
    JPanel dataSelBorder = new JPanel();
    dataSelBorder.setBorder(paneEdge);
    dataSelBorder.setLayout(new BoxLayout(dataSelBorder, BoxLayout.Y_AXIS));

    SpringLayout dL = new SpringLayout();
    JPanel dComp = new JPanel(dL, false);

    // Set up layout of 'Show/Hide Data' portion
    dL.putConstraint(SpringLayout.WEST, hiddenDataL, 5, SpringLayout.WEST, dComp);
    dL.putConstraint(SpringLayout.NORTH, hiddenDataL, 5, SpringLayout.NORTH, dComp);
    dL.putConstraint(SpringLayout.WEST, hiddenDataScroll, 0, SpringLayout.WEST, hiddenDataL);
    dL.putConstraint(SpringLayout.NORTH, hiddenDataScroll, 5, SpringLayout.SOUTH, hiddenDataL);
    dL.putConstraint(SpringLayout.NORTH, showData, 0, SpringLayout.NORTH, hiddenDataScroll);
    dL.putConstraint(SpringLayout.WEST, showData, 5, SpringLayout.EAST, hiddenDataScroll);
    dL.putConstraint(SpringLayout.EAST, hideData, 0, SpringLayout.EAST, showData);
    dL.putConstraint(SpringLayout.WEST, hideData, 0, SpringLayout.WEST, showData);
    dL.putConstraint(SpringLayout.NORTH, hideData, 5, SpringLayout.SOUTH, showData);
    dL.putConstraint(SpringLayout.NORTH, shownDataL, 0, SpringLayout.NORTH, hiddenDataL);
    dL.putConstraint(SpringLayout.WEST, shownDataL, 0, SpringLayout.WEST, shownDataScroll);
    dL.putConstraint(SpringLayout.WEST, shownDataScroll, 5, SpringLayout.EAST, showData);
    dL.putConstraint(SpringLayout.NORTH, shownDataScroll, 0, SpringLayout.NORTH, hiddenDataScroll);
    dL.putConstraint(SpringLayout.SOUTH, dComp, 5, SpringLayout.SOUTH, hiddenDataScroll);
    dL.putConstraint(SpringLayout.EAST, dComp, 5, SpringLayout.EAST, shownDataScroll);

    // Add components to 'Show/Hide Data' panel
    dComp.add(hiddenDataL);
    dComp.add(shownDataL);
    dComp.add(shownDataScroll);
    dComp.add(hiddenDataScroll);
    dComp.add(showData);
    dComp.add(hideData);
    dComp.setBorder(BorderFactory.createTitledBorder("Show/Hide Data") );

    // Finalize 'Annotation Selection' panel
    dataSelBorder.add(Box.createRigidArea(new Dimension(0, 10)));
    dataSelBorder.add(dComp);

    //
    // Construct Panel and Layout for 'Preview' portion
    //
    JPanel previewBorder = new JPanel();
    previewBorder.setBorder(paneEdge);
    previewBorder.setLayout(new BoxLayout(previewBorder, BoxLayout.Y_AXIS));

    SpringLayout pL = new SpringLayout();
    JPanel pComp = new JPanel(pL, false);

    // Set up layout of 'Preview' portion
    pL.putConstraint(SpringLayout.WEST, lNumAnnotationChosen, 5, SpringLayout.WEST, pComp);
    pL.putConstraint(SpringLayout.NORTH, lNumAnnotationChosen, 5, SpringLayout.NORTH, pComp);
    pL.putConstraint(SpringLayout.NORTH, previewTableScrollPane, 2, SpringLayout.SOUTH, lNumAnnotationChosen);
    pL.putConstraint(SpringLayout.WEST, previewTableScrollPane, 0, SpringLayout.WEST, lNumAnnotationChosen);
    pL.putConstraint(SpringLayout.EAST, cancel, -5, SpringLayout.EAST, pComp);
    pL.putConstraint(SpringLayout.NORTH, cancel, 5, SpringLayout.SOUTH, previewTableScrollPane);
    pL.putConstraint(SpringLayout.EAST, pComp, 5, SpringLayout.EAST, previewTableScrollPane);
    pL.putConstraint(SpringLayout.SOUTH, pComp, 5, SpringLayout.SOUTH, cancel);
    pL.putConstraint(SpringLayout.EAST, open, -7, SpringLayout.WEST, cancel);
    pL.putConstraint(SpringLayout.NORTH, open, 0, SpringLayout.NORTH, cancel);

    // Add components to 'Preview' panel
    pComp.add(lNumAnnotationChosen);
    pComp.add(previewTableScrollPane);
    pComp.add(open);
    pComp.add(cancel);
    pComp.setBorder(BorderFactory.createTitledBorder("Preview") );

    // Finalize 'Preview' panel
    previewBorder.add(Box.createRigidArea(new Dimension(0, 10)));
    previewBorder.add(pComp);

    //
    // Set layout constraints for 'main' panel
    //
    s.putConstraint(SpringLayout.EAST, intervalBorder, -5, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.EAST, dataSelBorder, -5, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.NORTH, intervalBorder, 0, SpringLayout.NORTH, main);
    s.putConstraint(SpringLayout.WEST, intervalBorder, 5, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.NORTH, annotSelBorder, 0, SpringLayout.SOUTH, intervalBorder);
    s.putConstraint(SpringLayout.EAST, annotSelBorder, 0, SpringLayout.EAST, intervalBorder);
    s.putConstraint(SpringLayout.WEST, annotSelBorder, 0, SpringLayout.WEST, intervalBorder);
    s.putConstraint(SpringLayout.WEST, previewBorder, 5, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.WEST, dataSelBorder, 0, SpringLayout.WEST, intervalBorder);
    s.putConstraint(SpringLayout.NORTH, dataSelBorder, 0, SpringLayout.SOUTH, annotSelBorder);
    s.putConstraint(SpringLayout.NORTH, previewBorder, 0, SpringLayout.SOUTH, dataSelBorder);
    s.putConstraint(SpringLayout.SOUTH, main, 5, SpringLayout.SOUTH, previewBorder);
    s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, previewBorder);

    // Add panels and buttons to 'main' panel
    main.add(annotSelBorder);
    main.add(intervalBorder);
    main.add(dataSelBorder);
    main.add(previewBorder);
    setContentPane(main);

    // Final setup for 'main' panel
    setTitle("Download");
    setResizable(false);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    pack();
  }

  /**
   * <p>Show the instance of {@link DownloadDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link DownloadDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link DownloadDialog}
   */
  public static void showDownloadDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new DownloadDialog(parent));
    DownloadDialog instance = instances.get(parent);

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
   * <p>Removes the instance of the {@link DownloadDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link DownloadDialog}
   */
  public static void closeDownloadDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>This displays the types of annotation available for annotation based on what
   * annotation was selected in the {@link MapNavigator}. It also initializes all the
   * components so the dialog box can work properly each time it is displayed</p>
   *
   */
  public void setupComponents()
  {
    settingUp = true;
    logger.debug("Get available annotation");

    typesModel.clear();
    intervalTableModel.getDataVector().removeAllElements();

    Vector<String> typeSet = new Vector<String>();

    intervals = mapNavigator.getSelectionIntervals();

    // Retrieve Selected Annotation
    annotation = new HashSet<Annotation>();
    for (SelectionInterval interval : intervals)
      annotation.addAll(interval.getAnnotation());

    // Determine the types & number of aliases
    for (Annotation annot : annotation)
    {
      if (!typeSet.contains(annot.getAnnotationSet().getType()))
        typeSet.add(annot.getAnnotationSet().getType());
    }

    // If there are types, display them in the types JList
    for (String type : typeSet)
    {
      typesModel.addElement(type);
    }

    if (typeSet.size() != 0)
    {
      logger.debug("Types available to selection in DownloadDialog");

      // Map name, chromosome, start, stop
      for (SelectionInterval interval : intervals)
      {
        Vector<Object> row = new Vector<Object>();
        row.add(interval.getSegment().getParent().getMap().getName());
        row.add(interval.getSegment().getChromosome().getName().substring(3));
        row.add(Util.formatUnit(interval.getStart(), interval.getSegment().getParent().getMap().getScale()));
        row.add(Util.formatUnit(interval.getStop(), interval.getSegment().getParent().getMap().getScale()));
        row.add(interval.getSegment().getParent().getMap().getUnitsString());
        intervalTableModel.addRow(row);
      }

      intervalTable.addRowSelectionInterval(0, intervalTable.getRowCount()-1);
      intervalSelectAll.setEnabled(true);
      typeSelectAll.setEnabled(true);
    }
    else
    {
      intervalSelectAll.setEnabled(false);
      typeSelectAll.setEnabled(false);
      typesModel.addElement("No data available");
    }

    // Select all types in types list
    int upperBound = typesModel.getSize()-1;
    if (upperBound >= 0)
      types.setSelectionInterval(0, upperBound);

    // Disable open button if there is not info to display
    open.setEnabled(mapNavigator.getBackboneDisplaySegment() != null);

    settingUp = false;
    selectedIntervals.addAll(intervals);
    generateTableData();
  }

  /**
   * <p>This takes the information stored in a series of {@link Annotation}
   * (up to 10) and displays the information in the preview table.<p>
   *
   */
  private void generateTableData()
  {
    logger.debug("Clear previous data in preview table");
    previewTableModel.setColumnCount(0);
    previewTableModel.setRowCount(0);
    this.pack();

    logger.debug("Add shown columns to preview table");
    for (int i = 0; i < shownDataModel.size(); i++)
      previewTableModel.addColumn((String)shownDataModel.get(i));

    logger.debug("Manually change the column width sizes");
    manualColumnChange = true;
    for (String key : columnWidth.keySet())
    {
      try
      {
        previewTable.getColumn(key).setPreferredWidth(columnWidth.get(key).intValue());
      }
      catch (IllegalArgumentException e) {}
    }
    manualColumnChange = false;

    logger.debug("Determine the annotation to display");
    // Get selected types
    Object[] objChoices = types.getSelectedValues();
    Vector<String> choices = new Vector<String>();

    for (Object choice : objChoices)
      choices.add((String)choice);

    // Filter choices out of selected annotation
    filteredAnnotation = new Vector<Annotation>();
    for (Annotation annot : annotation)
    {
      for (SelectionInterval interval : selectedIntervals)
      {
        if (interval.containsAnnotation(annot) && choices.contains(annot.getAnnotationSet().getType()))
        {
          filteredAnnotation.add(annot);
          break;
        }
      }
    }

    logger.debug("Display Annotation Information in preview table");
    for (int i = 0; i < filteredAnnotation.size() && i < 10; i++)
    {
      Annotation annot = filteredAnnotation.get(i);
      Object[] newRowData = new Object[shownDataModel.size()];

      for (int j = 0; j < newRowData.length; j++)
      {
        String columnTitle = (String)shownDataModel.get(j);

        newRowData[j] = getAnnotationInfo(columnTitle, annot);
      }

      previewTableModel.addRow(newRowData);
    }

    // Update number of annotations label
    lNumAnnotationChosen.setText(numAnnotationString + filteredAnnotation.size());
  }

  /**
   * <p>This get the appropriate information from the {@link Annotation} for
   * a specific column.</p>
   *
   * @param columnTitle
   *   {@link String} containing the title of the column
   * @param annot
   *   {@link Annotation} to get information from
   * @return
   *   {@String} value of the information. If the information is not contained
   *   in the {@link Annotation}, an {@link String} with the message "Error" is
   *   returned
   */
  private String getAnnotationInfo(String columnTitle, Annotation annot)
  {
    if (columnTitle.compareTo("Details") == 0)
    {
      // Create details string
      String details = "";
      String[] infoKeys = annot.getInfoKeys();

      // Display all info in details
      if (infoKeys != null)
      {
        for (String key : infoKeys)
        {
          details += key + ": ";
          details += annot.getInfo(key);
          details += ";";
        }
      }

      return details;
    }
    else if (columnTitle.compareTo("Name") == 0)
    {
      return annot.getName();
    }
    else if (columnTitle.compareTo("Start Position") == 0)
    {
      return Double.toString((double)annot.getStart() / (double)annot.getChromosome().getMap().getScale());
    }
    else if (columnTitle.compareTo("Stop Position") == 0)
    {
      return Double.toString((double)annot.getStop() / (double)annot.getChromosome().getMap().getScale());
    }
    else if (columnTitle.equals("Units"))
    {
      return annot.getChromosome().getMap().getUnitsString();
    }
    else if (columnTitle.compareTo("Chromosome") == 0)
    {
      String chromName = annot.getChromosome().getName();

      if (chromName.contains("chr"))
        return chromName.substring(3);
      else
        return chromName;
    }
    else if (columnTitle.compareTo("Species") == 0)
    {
      return annot.getChromosome().getMap().getSpecies();
    }
    else if (columnTitle.compareTo("Map Type") == 0)
    {
      return annot.getChromosome().getMap().getTypeString();
    }
    else if (columnTitle.compareTo("Annotation Type") == 0)
    {
      return annot.getAnnotationSet().getType();
    }
    else if (columnTitle.compareTo("Source") == 0)
    {
      return annot.getChromosome().getMap().getSource();
    }
    else if (columnTitle.compareTo("Version") == 0)
    {
      return annot.getChromosome().getMap().getVersion().toString();
    }
    else
    {
      return "Error";
    }
  }

  /**
   * <p>This method uses the information stored in the preview table to save
   * the {@link Annotation} chosen by the user as a .csv file location
   * specified by the user.</p>
   *
   * @param file
   *   {@link String} representing the file name and directory
   */
  private void saveTableData(String file)
  {
    logger.debug("Open button was pressed");
    //
    // Write selected data to a .csv file
    //
    try
    {
      int lineCount = 0;

      logger.debug("Saving data to file: " + file);
      BufferedWriter out = new BufferedWriter(new FileWriter(file));

      // Write backbone information
      if (mapNavigator.getBackboneDisplaySegment() != null)
      {
        out.write(formatString("Backbone Information:"));
        maxOutputLines(++lineCount);
        out.newLine();

        // Chromosome info
        out.write(formatString("Chromosome:") + ",");
        out.write(formatString(mapNavigator.getBackboneDisplaySegment().getChromosome().getName().substring(3)));
        maxOutputLines(++lineCount);
        out.newLine();

        // Map info
        out.write(formatString(mapString) + ",");
        out.write(formatString(mapNavigator.getBackboneDisplaySegment().getParent().getMap().getName()));
        maxOutputLines(++lineCount);
        out.newLine();

        // Interval Info
        // Map name, chromosome, start, stop
        maxOutputLines(++lineCount);
        out.newLine();
        out.write(formatString("Interval Information:"));
        maxOutputLines(++lineCount);
        out.newLine();
        for (String title : intervalTableTitles)
          out.write(formatString(title) + ",");
        maxOutputLines(++lineCount);
        out.newLine();

        for (SelectionInterval interval : selectedIntervals)
        {
          out.write(formatString(interval.getSegment().getParent().getMap().getName()) + ",");
          out.write(formatString(interval.getSegment().getChromosome().getName().substring(3))  + ",");
          out.write((interval.getStart() / (double)interval.getSegment().getParent().getMap().getScale()) + ",");
          out.write((interval.getStop()  / (double)interval.getSegment().getParent().getMap().getScale()) + ",");
          out.write(formatString(interval.getSegment().getParent().getMap().getUnitsString()) + ",");
          maxOutputLines(++lineCount);
          out.newLine();
        }

        maxOutputLines(++lineCount);
        out.newLine();
        out.write(formatString("Annotation Information:"));
        maxOutputLines(++lineCount);
        out.newLine();
        out.write("Number of Annotation:," + filteredAnnotation.size());
        maxOutputLines(++lineCount);
        out.newLine();

        // Write selected column headers first
        for (int i = 0; i < previewTableModel.getColumnCount(); i++)
        {
          out.write(formatString((String)previewTable.getColumnModel().getColumn(i).getHeaderValue()));
          if (i < (previewTableModel.getColumnCount() - 1))
            out.write(",");
        }

        // Write selected data
        for (Annotation annot : filteredAnnotation)
        {
          maxOutputLines(++lineCount);
          out.newLine();

          for (int i = 0; i < previewTableModel.getColumnCount(); i++)
          {
            String columnTitle = (String)previewTable.getColumnModel().getColumn(i).getHeaderValue();
            String cellValue = getAnnotationInfo(columnTitle, annot);

            if (!cellValue.contains("\"\""))
              out.write("=");

            out.write(formatString(cellValue));

            if (i < (previewTableModel.getColumnCount() - 1))
              out.write(",");
          }
        }
      }
      else
      {
        out.write(formatString("No Data Available"));
        maxOutputLines(++lineCount);
        out.newLine();
      }

      out.close();
    }
    catch (IOException e)
    {
      logger.debug("Error writing file: " + e.getMessage());
    }
    catch (Exception e)
    {
      JOptionPane.showMessageDialog(this,
        "The output file has been truncated because" +
        "too the document has reached the maximum number" +
        "of lines that is can output.",
        "Maximum Number of Lines Reached",
        JOptionPane.ERROR_MESSAGE);

      logger.debug("Document truncated: " + e.getMessage());
    }
  }

  /**
   * <p>Format a {@link String} to be outputted into a .csv file</p>
   *
   * @param string
   *   {@link String} to be formatted
   * @return
   *   Formatted {@link String} of string
   */
  public String formatString(String string)
  {
    return "\"" + string.replaceAll("\"", "\"\"") + "\"";
  }

  /**
   * <p>If the number of outputted lines reaches 65536 an exception is
   * thrown.</p>
   *
   * @param lineCount
   *   int value of the number of lines outputted into a file
   */
  private void maxOutputLines(int lineCount)
    throws Exception
  {
    if (lineCount >= 65536)
      throw new Exception("Max number of lines reached");
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand().equals("Show"))
    {
      logger.debug("Adding column(s) to preview table");
      int selectedIndex = hiddenData.getSelectedIndex();

      while (selectedIndex != -1)
      {
        // add object to shown list
        shownDataModel.addElement((String)hiddenDataModel.get(selectedIndex));

        hiddenDataModel.remove(selectedIndex);

        selectedIndex = hiddenData.getSelectedIndex();
      }

      if (hiddenDataModel.getSize() > 0)
        hiddenData.setSelectedIndex(0);

      generateTableData();
    }
    if (ae.getActionCommand().equals("Hide"))
    {
      logger.debug("Removing column(s) to preview table");
      int selectedIndex = shownData.getSelectedIndex();
      while (selectedIndex != -1)
      {
        // Add column title to hidden list
        hiddenDataModel.addElement((String)shownDataModel.get(selectedIndex));

        // Remove column title from shown list
        shownDataModel.remove(selectedIndex);

        selectedIndex = shownData.getSelectedIndex();
      }

      if (shownDataModel.getSize() > 0)
        shownData.setSelectedIndex(0);

      generateTableData();
    }
    if (ae.getActionCommand().equals("Open"))
    {
      try
      {
        File file = File.createTempFile("vcmap-output-", ".csv");
        String fileName = file.getName();
        saveTableData(fileName);

        String osName = System.getProperty("os.name");

        if (osName.contains("Linux"))
        {
          String[] apps = {"ooffice"};
          for (String app : apps)
          {
            if (Runtime.getRuntime().exec(new String[] {"which", app}).waitFor() == 0)
              Runtime.getRuntime().exec(new String[] {app, fileName});
          }
        }
        else if (osName.contains("Mac OS"))
        {
          Process p = Runtime.getRuntime().exec("open " + fileName);
        }
        else if (osName.contains("Windows"))
        {
          Runtime.getRuntime().exec("rundll32 SHELL32.DLL,ShellExec_RunDLL " + fileName);
        }
      }
      catch (Exception e)
      {
        logger.debug("Unable to display URL in web browser");
      }
    }
    else if (ae.getActionCommand().equals("Cancel"))
    {
      logger.debug("Download cancelled, window closed");

      // close window
      setVisible(false);
    }
    else if (ae.getSource() == typeSelectAll)
    {
      logger.debug("Select all types button was pressed");

      // select all
      int upperBound = typesModel.getSize()-1;
      if (upperBound >= 0 ) types.setSelectionInterval(0, upperBound);
    }
    else if (ae.getSource() == intervalSelectAll)
    {
      logger.debug("Select all intervals button was pressed");

      intervalTable.addRowSelectionInterval(0, intervalTable.getRowCount()-1);
    }
  }

  /*
   * (non-Javadoc)
   * Automatically update the preview table each time the user changes their selection
   * of the type of annotation they want to export
   * @see javax.swing.event.ListSelectionListener#valueChanged(javax.swing.event.ListSelectionEvent)
   */
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getSource() == shownData)
    {
      logger.debug("Changing whether the button for moving shown column titles "
        + "to hidden is enabled");
      if (shownData.getSelectedIndices().length == 0)
        hideData.setEnabled(false);
      else
        hideData.setEnabled(true);
    }
    else if (e.getSource() == hiddenData)
    {
      logger.debug("Changing whether the button for moving hidden column titles "
        + "to shown is enabled");
      if (hiddenData.getSelectedIndices().length == 0)
        showData.setEnabled(false);
      else
        showData.setEnabled(true);
    }
    else if (e.getSource() == types && !settingUp)
    {
      logger.debug("Selected of what type of annotation to show has been changed.");
      // Generate those annotations filtered into a preview table
      generateTableData();

      // Enable saving file now
      open.setEnabled(true);
    }
  }

  /*
   * (non-Javadoc)
   * Move hide/shown selection if double clicked
   * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
   */
  public void mousePressed(MouseEvent e)
  {
    if (e.getSource() instanceof JList)
    {
      if (e.getClickCount() == 2)
      {
        if (e.getSource() == shownData)
        {
          actionPerformed(new ActionEvent(hideData, 0, "Hide"));
        }
        else if (e.getSource() == hiddenData)
        {
          actionPerformed(new ActionEvent(showData, 0, "Show"));
        }
      }
    }
  }

  // Required but not used
  public void mouseClicked(MouseEvent e) {}
  public void mouseReleased(MouseEvent e) {}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) {}
}
