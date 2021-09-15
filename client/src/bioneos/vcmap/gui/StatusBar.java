package bioneos.vcmap.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.OntologyNode;
import bioneos.vcmap.model.OntologyTree;
import bioneos.vcmap.model.Selection;
import bioneos.vcmap.util.Util;

/**
 * <p>This class is a {@link JPanel} that shows the user the elements they
 * have selected in the {@link MapNavigator}.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author sgdavis@bioneos.com
 */

public class StatusBar extends JPanel
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private int featureDisplayType;

  private MainGUI mainGUI;
  private MapNavigator mapNavigator;

  // GUI variables
  private JLabel status;
  private JLabel extended;
  private JScrollPane extendedScrollPane;
  private JButton details;

  private int extendedWidth;
  private int extendedHeight;
  private Vector<Annotation> selectedAnnotation;
  private DisplaySegment selectedSegment;
  private DisplayMap selectedMap;
  private boolean extendedUpToDate;

  private JLabel qtlLabel;
  private JScrollPane qtlPopup;
  private int qtlLabelWidth;
  private int qtlLabelHeight;

  /**
   * <p>Constructor for {@link StatusBar}. Sets up the layout for the
   * {@link StatusBar} and initializes the glass pane.</p>
   *
   * @param  parent
   *   {@link MainGUI} that the {@link StatusBar} is a part of
   */
  public StatusBar(MainGUI parent)
  {
    mainGUI = parent;

    mapNavigator = mainGUI.getMapNavigator();
    featureDisplayType = mainGUI.getOptions().getIntOption("featureDisplayType");

    // Component setup
    logger.debug("StatusBar is defaulted to no selected elements");
    details = new JButton("Details");
    details.setActionCommand("ViewDetails");
    details.addActionListener(mainGUI);
    details.setEnabled(false);
    status = new JLabel("No elements");
    status.setFont(new Font("default", Font.ITALIC, 12));
    extended = new JLabel("<html></html>");
    extended.setOpaque(true);
    extended.setBackground(Color.WHITE);
    extendedScrollPane = new JScrollPane(extended);

    extendedUpToDate = true;
    selectedAnnotation = new Vector<Annotation>();

    qtlLabel = new JLabel("<HTML></HTML>");
    qtlLabel.setOpaque(true);
    qtlLabel.setBackground(Color.WHITE);
    qtlPopup = new JScrollPane(qtlLabel);
    qtlPopup.setBackground(Color.WHITE);
    mainGUI.getLayeredPane().add(qtlPopup, JLayeredPane.POPUP_LAYER);

    // Layout
    BoxLayout box = new BoxLayout(this, BoxLayout.X_AXIS);
    setLayout(box);
    JLabel select = new JLabel("Selected:");
    select.setFont(new Font("default", Font.PLAIN, 10));
    add(select);
    add(Box.createHorizontalStrut(5));
    add(status);
    add(Box.createHorizontalGlue());
    add(details);
    mainGUI.getLayeredPane().add(extendedScrollPane, JLayeredPane.POPUP_LAYER);
    setBorder(new javax.swing.border.EmptyBorder(5, 15, 5, 15));

    // Final setup
    status.addMouseListener(new MouseAdapter()
      {
        public void mouseEntered(MouseEvent me)
        {
          logger.debug("Mouse entered over \"x elements\" label");
          if (selectedAnnotation.size() > 1)
          {
            status.setFont(new Font("default", Font.BOLD, 12));
            status.setCursor(new Cursor(Cursor.HAND_CURSOR));
          }
          else
            status.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        public void mouseExited(MouseEvent me)
        {
          logger.debug("Mouse exited from \"x elements\" label");
          if (status.getText().compareTo("No elements") == 0)
          {
            status.setFont(new Font("default", Font.ITALIC, 12));
          }
          else
            status.setFont(new Font("default", Font.PLAIN, 12));

          status.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        public void mouseClicked(MouseEvent me)
        {
          logger.debug("Mouse clicked on \"x elements\" label");
          if (selectedAnnotation.size() > 1)
          {
            // Is the data up to date? If not, update it
            if (!extendedUpToDate)
            {
              status.setText("Loading...");
              status.getParent().update(status.getParent().getGraphics());
              configureExtended();
            }

            int x = getX() + status.getX() + mainGUI.getContentPane().getX();
            int y = getY() + status.getY() + mainGUI.getContentPane().getY();
            int spWidth = extendedWidth
              + extendedScrollPane.getVerticalScrollBar().getWidth();
            int spHeight = extendedHeight
              + extendedScrollPane.getHorizontalScrollBar().getHeight() + 1;

            // Disable scrollbars
            extendedScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            extendedScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            // Check of the scrollpane is too large
            if (spHeight > mainGUI.getHeight() - y)
            {
              spHeight = (int)((double)mainGUI.getHeight() * (4.0/5.0)) - y;
              extendedScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
            }
            if (spWidth > mainGUI.getWidth() - x)
            {
              spWidth = (int)((double)mainGUI.getWidth() * (4.0/5.0)) - x;
              extendedScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
            }

            extended.setBounds(0, 0, extendedWidth, extendedHeight);
            extendedScrollPane.setBounds(x, y, spWidth, spHeight);
            extendedScrollPane.setVisible(true);
            mainGUI.getGlassPane().setVisible(true);
          }
        }
      });
  }

  protected JScrollPane getExtendedScrollPane()
  {
    return extendedScrollPane;
  }

  protected JScrollPane getQTLPopup()
  {
    return qtlPopup;
  }

  /**
   * <p>Updates the {@link StatusBar} to display what objects are currently
   * selected in the {@link MapNavigator}.
   */
  public void updateStatusBar()
  {
    if (mapNavigator == null) return;

    Selection selection = mapNavigator.getSelection();
    selectedAnnotation = selection.getAnnotation();
    selectedSegment = selection.getSegment();
    selectedMap = selection.getMap();

    // Disable/Enable Details button
    if (selectedAnnotation.size() == 0)
    {
      details.setEnabled(false);
      mainGUI.setViewDetails(false);
    }
    else
    {
      details.setEnabled(true);
      mainGUI.setViewDetails(true);
    }

    if (selectedAnnotation.size() == 0 && selectedMap == null && selectedSegment == null)
    {
      status.setFont(new Font("default", Font.ITALIC, 12));
      status.setText("No elements");
      extended.setText("<html></html>");
    }
    else if (selectedAnnotation.size() == 0 && selectedMap != null && selectedSegment != null)
    {
      //Display Segment info
      status.setFont(new Font("default", Font.PLAIN, 12));

      status.setText(formatDisplaySegmentData(selectedSegment));
      extended.setText("<html></html>");
    }
    else if (selectedAnnotation.size() == 0 && selectedMap != null && selectedSegment == null)
    {
      // Display Map info
      status.setFont(new Font("default", Font.PLAIN, 12));
      status.setText(formatDisplayMapData(selectedMap));
      extended.setText("<html></html>");
    }
    else if (selectedAnnotation.size() == 1)
    {
      String statusString = formatAnnotationData(selectedAnnotation.firstElement());
      status.setFont(new Font("default", Font.PLAIN, 12));
      status.setText(statusString);
      extended.setText("<html></html>");
    }
    else
    {
      status.setFont(new Font("default", Font.PLAIN, 12));
      status.setText(selectedAnnotation.size() + " elements");

      extendedUpToDate = false;
    }
  }

  public boolean isExtendedUpToDate()
  {
    return extendedUpToDate;
  }

  public void setExtendedUpToDate(boolean b)
  {
    extendedUpToDate = b;
  }

  /**
   * <p>Fills the extended {@link JLabel} with all the selection information.</p>
   *
   */
  private void configureExtended()
  {
    if (selectedAnnotation.size() == 0) return;

    FontMetrics metrics = extended.getFontMetrics(extended.getFont());
    StringBuilder extendedString = new StringBuilder("<html>");
    HashMap<String, StringBuilder> mapAnnotation = new HashMap<String, StringBuilder>();

    int lineExtraSpace = metrics.stringWidth("  ");
    int lineDashSpace = metrics.stringWidth("-");
    extendedWidth = 0;

    // Determine the max width of each line while creating each line
    // This puts annotation by the appropriate map in O(n)
    int stringWidth = 0;
    for (Annotation annot : selectedAnnotation)
    {
      MapData map = annot.getChromosome().getMap();
      StringBuilder mapAnnot = mapAnnotation.get(map.getName());
      if (mapAnnot == null)
      {
        mapAnnot = new StringBuilder(map.getName());
        mapAnnot.append("<br>"); // new line

        // Check if max width
        stringWidth = metrics.stringWidth(map.getName()) + lineExtraSpace;
        if (stringWidth > extendedWidth)
          extendedWidth = stringWidth;
      }

      // Add annot info
      String annotationData = formatAnnotationData(annot);
      mapAnnot.append("-");
      mapAnnot.append(annotationData);
      mapAnnot.append("<br>");

      // Check if max width
      stringWidth = lineDashSpace + metrics.stringWidth(annotationData) + lineExtraSpace;
      if (stringWidth > extendedWidth)
        extendedWidth = stringWidth;

      mapAnnotation.put(map.getName(), mapAnnot);
    }

    // Add all the lines to the extended string
    for (Object key : mapAnnotation.keySet())
      extendedString.append(mapAnnotation.get(key));

    // Set extended label's text
    extendedString.append("</html>");
    extended.setText(extendedString.toString());

    // Determine the height
    extendedHeight = metrics.getHeight() * (selectedAnnotation.size() + mapAnnotation.keySet().size());
    extendedUpToDate = true;
  }

  /**
   * <p>Data stored in a {@link DisplayMap} is formatted to be displayed
   * to the user</p>
   *
   * @param displayMap
   *   Form data into a string from this {@link DisplayMap}
   * @return
   *   Data from {@link DisplayMap} formatted into a {@link String}
   */
  private String formatDisplayMapData(DisplayMap displayMap)
  {
    StringBuilder mapData = new StringBuilder();

    mapData.append("Map - ");
    mapData.append(displayMap.getMap().getName());
    mapData.append(" (Number of Segments: ");
    mapData.append(displayMap.getSegments().size());
    mapData.append(")");

    return mapData.toString();
  }

  /**
   * <p>Data stored in a {@link DisplaySegment} is formatted to be displayed
   * to the user</p>
   *
   * @param segment
   *   Form data into a string from this {@link DisplaySegment}
   * @return
   *   Data from {@link DisplaySegment} formatted into a {@link String}
   */
  private String formatDisplaySegmentData(DisplaySegment segment)
  {
    String[] chromosomeString = segment.getChromosome().getName().split("chr");

    StringBuilder segmentData = new StringBuilder();
    segmentData.append("Segment - Chromosome: ");
    segmentData.append(chromosomeString[1]);
    segmentData.append(" (Start: " );
    segmentData.append(segment.getDrawingStart());
    segmentData.append(segment.getParent().getMap().getUnitsString());
    segmentData.append(")");
    segmentData.append(" (Stop: ");
    segmentData.append(segment.getDrawingStop());
    segmentData.append(segment.getParent().getMap().getUnitsString());
    segmentData.append(")");
    segmentData.append(" (Species: ");
    segmentData.append(segment.getParent().getMap().getSpecies());
    segmentData.append(")");
    segmentData.append(" (Number of Features: ");
    segmentData.append(segment.getSegmentFeatures().size());
    segmentData.append(")");

    return segmentData.toString();
  }

  /**
   * <p>Data stored in a {@link Annotation} is formatted to be displayed
   * to the user</p>
   *
   * @param annotation
   *   Form data into a string from this {@link Annotation}
   * @return
   *   Data from {@link Annotation} formatted into a {@link String}
   */
  private String formatAnnotationData(Annotation annotation)
  {
    featureDisplayType = mainGUI.getOptions().getIntOption("featureDisplayType");
    StringBuilder annotationData = new StringBuilder(annotation.getName(featureDisplayType));

    // Type
    annotationData.append(" [").append(annotation.getAnnotationSet().getType()).append("] ");

    // Position
    annotationData.append(annotation.getChromosome().getName()).append(":");
    annotationData.append(Util.formatUnit(annotation.getStart(), annotation.getChromosome().getMap().getScale()));
    if (annotation.getStart() != annotation.getStop())
    {
      annotationData.append("-");
      annotationData.append(Util.formatUnit(annotation.getStop(), annotation.getChromosome().getMap().getScale()));
    }

    // Units
    annotationData.append("(").append(annotation.getChromosome().getMap().getUnitsString()).append("); ");

    // Map
    annotationData.append(annotation.getChromosome().getMap().getName());

    return annotationData.toString();
  }

  /**
   * Creates and displays a popup on the Glasspane to show all currently loaded
   * ontology categories for a given {@link DisplayMap}.
   *
   * @param e
   *  The {@link MouseEvent} generated in {@link MapHeaders}.
   *
   * @param xPos
   *  An <code>integer</code> represeting the x-coordinate at which to display
   *  the popup.
   *
   *  @author dquacken@bioneos.com
   */
  public void showLoadedQTL(MouseEvent e, int xPos)
  {
    DisplayMap chosenMap = null;

    // determine which (if multiple loaded) DisplayMap this corresponds to
    for (DisplayMap map : mapNavigator.getDisplayMaps())
    {
      int xStart = mapNavigator.getDisplayMapXCoord(map);
      if (xStart < e.getX() && e.getX() < xStart + map.getFeatureColumnsWidth(mainGUI.getOptions()))
        chosenMap = map;
    }

    ArrayList<OntologyNode> ontologyFilters = null;
    if (chosenMap != null)
      ontologyFilters = chosenMap.getOntologyFilters();
    else
      return;

    String initString = "Loaded ontology filters:";
    FontMetrics metrics = qtlPopup.getFontMetrics(qtlPopup.getFont());
    StringBuilder contentString = new StringBuilder("<html>");
    contentString.append(initString + "<br>");
    int lineExtraSpace = metrics.stringWidth("  ");
    qtlLabelWidth = metrics.stringWidth(initString);
    int stringWidth = 0;
    int numFilters = 1;

    if (ontologyFilters == null || ontologyFilters.size() == 0)
    {
      contentString.append("(all)<br>");
      stringWidth = metrics.stringWidth("(all)" + lineExtraSpace);
      if (stringWidth > qtlLabelWidth)
        qtlLabelWidth = stringWidth;
    }
    else
    {
      numFilters = ontologyFilters.size();
      for (OntologyNode node : ontologyFilters)
      {
        ArrayList<OntologyNode> currentPath = new ArrayList<OntologyNode>();

        // Build list for each filter
        OntologyNode parent = node;
        while (parent != null && !parent.getCategory().equals(OntologyTree.ROOT_NODE))
        {
          currentPath.add(0, parent);
          parent = parent.getParent();
        }

        // Build display string for each filter
        StringBuilder hierarchy = new StringBuilder();
        hierarchy.append("-");
        for (OntologyNode n : currentPath)
          hierarchy.append((hierarchy.length() > 1 ? " >> " : "")).append(n.getCategory());

        stringWidth = metrics.stringWidth(hierarchy.toString() + lineExtraSpace);
        if (stringWidth > qtlLabelWidth)
          qtlLabelWidth = stringWidth;

        hierarchy.append("<br>");
        contentString.append(hierarchy);
      }
    }

    contentString.append("</html>");
    qtlLabel.setText(contentString.toString());

    qtlLabelHeight = metrics.getHeight() * (numFilters);

    qtlLabel.setBounds(0, 0, qtlLabelWidth, qtlLabelHeight);
    qtlPopup.setBounds(xPos, 85, qtlLabelWidth + 40, qtlLabelHeight + 20);
    qtlPopup.setVisible(true);
    mainGUI.getGlassPane().setVisible(true);
  }
}
