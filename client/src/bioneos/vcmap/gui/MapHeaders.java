package bioneos.vcmap.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.locks.Lock;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.dialogs.ImageDialog;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.LabelRect;
import bioneos.vcmap.model.parsers.FileParser;

/**
 * <p>{@link MapHeaders} serves as a customized column header for the
 * {@link JScrollPane} that contains the {@link MapNavigator}. The
 * {@link MapHeaders} displays the appropriate map title information
 * positioned where the maps are drawn in the {@link MapNavigator}.
 * This method also allows the user to click in the region where a map
 * title is positioned to allow the repositioning of a specific map.<p>
 *
 * <p>Created on: August 28, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class MapHeaders
  extends JPanel
  implements MouseListener, MouseMotionListener
{
  private static final long serialVersionUID = 1L;

  // Global Variables
  private MainGUI mainGUI;
  private MapNavigator mapNavigator;
  private StatusBar status;
  private Integer titleDragPoint;
  private int clickDifference;
  private Vector<String> hiddenTypes;
  private boolean qtlHover;
  private Vector<LabelRect> labelRects;
  private Vector<LabelRect> versionRects;
  private DisplayMap hoveredMap;
  private boolean imageOutput;

  private static final int MAX_LABEL_LENGTH = 10;

  /**
   * <p>Constructor for {@link MapHeaders}. Initializes all the variables
   * needed to display the {@link DisplayMap} data for the
   * {@link MapNavigator}.</p>
   *
   * @param parent
   *   {@link MainGUI} containing the {@link MapHeaders}
   */
  public MapHeaders(MainGUI parent)
  {
    clickDifference = -1;
    qtlHover = false;
    mainGUI = parent;
    mapNavigator = mainGUI.getMapNavigator();
    hiddenTypes = mapNavigator.getHiddenTypes();
    addMouseListener(this);
    addMouseMotionListener(this);
    versionRects = new Vector<LabelRect>();
    labelRects = new Vector<LabelRect>();
    imageOutput = false;
  }

  public BufferedImage createImage(int desiredWidth)
  {
    // Boolean to draw image differently
    imageOutput = true;
    double scale = (double) desiredWidth / getWidth();
    BufferedImage image = new BufferedImage(desiredWidth, (int) Math.ceil(getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();

    // Transform graphics using rectangle coordinates and scale
    AffineTransform transform = g2d.getTransform();
    transform.scale(scale, scale);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.setTransform(transform);

    paint(g2d);
    g2d.dispose();

    // Set background color to null so default color is used
    imageOutput = false;

    return image;
  }

  /*
   * (non-Javadoc)
   * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
   */
  protected void paintComponent(Graphics g)
  {
    super.paintComponent(g);

    // Lock while we paint to avoid loading during our custom rendering 
    Lock lock = mainGUI.getMapNavigator().getLock();
    if (!lock.tryLock()) return;

    labelRects = new Vector<LabelRect>();
    versionRects = new Vector<LabelRect>();

    // Safe to assume we are using Java > 1.2
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    Color background = mainGUI.getOptions().getColor("header");

    // Set header color if image is being captured
    if (imageOutput)
      background = ImageDialog.HEADER_COLOR;

    g2d.setColor(background);
    Rectangle clip = g2d.getClipBounds();
    g2d.fill(clip);

    // Font Variables
    g2d.setFont(VCMap.labelFont);
    FontMetrics metrics = g2d.getFontMetrics();
    int labelHeight = metrics.getHeight();
    int labelDescent = metrics.getDescent();
    int labelAscent = metrics.getAscent();

    // Other Variables
    int yCoord = labelAscent + 1;
    int segmentWidth = mainGUI.getOptions().getIntOption("segmentWidth");
    int labelColumnWidth = mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");

    DisplayMap selectedMap = mapNavigator.getSelectedDisplayMap();
    DisplaySegment bbSegment = mapNavigator.getBackboneDisplaySegment();

    for (DisplayMap map : mapNavigator.getDisplayMaps())
    {
      if (!map.isVisible())
        continue;

      // Determine the widths of the annotation columns from the segments on this map
      HashMap<AnnotationSet, Integer> mapTypesCols = new HashMap<AnnotationSet, Integer>() ;
      for(DisplaySegment seg : map.getSegments())
      {
        for(AnnotationSet set : map.getShownSets())
        {
          int featureColsForSeg = seg.getFeatureColumns(set) ;
          if(featureColsForSeg == 0)
          {
            // This segment does not have this AnnotationSet, skip
            continue ;
          }
          
          if(!mapTypesCols.containsKey(set))
          {
            mapTypesCols.put(set, featureColsForSeg) ;
          }
          else
          {
            mapTypesCols.put(set, Math.max(mapTypesCols.get(set), featureColsForSeg)) ;
          }
        }
      }
      
      String name = map.getMap().getName();
      int nameWidth = metrics.stringWidth(name);
      int mapXCoord = mapNavigator.getDisplayMapXCoord(map);
      int nameXCoord = mapXCoord + map.getFeatureColumnsWidth(mainGUI.getOptions())
        + segmentWidth / 2
        - nameWidth / 2;

      // Draw annotation labels
      if (map.getSegments() != null && map.getSegments().size() > 0)
      {
        int typeLabelXCoord = mapXCoord + labelColumnWidth;
        
        for (AnnotationSet set : map.getShownSets())
        {
          if (mainGUI.getOptions().isShown(set.getType()) && !hiddenTypes.contains(set))
          {            
            // Adjust labels so they are not too long
            String adjType = set.getType(); 
            if (set.isCustom() && set.getType().endsWith(FileParser.SUFFIX))
              adjType = set.getType().substring(0, set.getType().length() - FileParser.SUFFIX.length());
            if (adjType.length() > MAX_LABEL_LENGTH) // Shorten names that are too long
              adjType = adjType.subSequence(0, MAX_LABEL_LENGTH - 3).toString().trim() + "...";
            String adjVersion = set.getSource();
            if (adjVersion.length() > MAX_LABEL_LENGTH) // Shorten names that are too long
              adjVersion = adjVersion.subSequence(0, MAX_LABEL_LENGTH - 3).toString().trim() + "...";

            // Cache a few important rendering numbers
            int height = metrics.getHeight();
            int versionWidth = metrics.stringWidth(adjVersion);
            int typeWidth = metrics.stringWidth(adjType);
            
            versionRects.addElement(new LabelRect(set, adjVersion, typeLabelXCoord - versionWidth,
                getHeight() - (labelHeight * 2) - 1, versionWidth, height));

            labelRects.addElement(new LabelRect(set, adjType, typeLabelXCoord - versionWidth
                + (versionWidth - typeWidth) / 2, getHeight() - labelHeight - 1, typeWidth, height));

            // Draw the source string first
            g2d.setColor(Color.BLACK);
            LabelRect lastVersion = versionRects.lastElement();
            LabelRect lastType = labelRects.lastElement();
            g2d.drawString(lastVersion.labelText, (int) lastVersion.getX(),
                (int) lastVersion.getY() + labelAscent);

            // And now the type
            if (set.getType().equals("QTL") && qtlHover && hoveredMap == map)
              g2d.setColor(Color.BLUE);
            g2d.drawString(lastType.labelText, (int) lastType.getX(), (int) lastType.getY() + labelAscent);

            if (mapTypesCols.containsKey(set))
            {
              
              typeLabelXCoord += mainGUI.getOptions().getIntOption("featureLabelColumnWidth") 
                + (mainGUI.getOptions().getIntOption("featureColumnWidth") * mapTypesCols.get(set)) ;
            }
            else
            {
              typeLabelXCoord += labelColumnWidth
                + mainGUI.getOptions().getIntOption("featureColumnWidth")
                * mainGUI.getOptions().getIntOption("featureColumns");
            }
          }
        }
      }

      //
      // Draw title
      //
      g2d.setColor(Color.BLACK);

      // Draw Background and border if selected
      if (selectedMap == map && titleDragPoint == null && !imageOutput)
      {
        g2d.setColor(Color.WHITE);
        g2d.fillRect(nameXCoord - selectionBoxSpacing,
          yCoord - labelDescent - labelHeight / 2 - selectionBoxSpacing,
          nameWidth + selectionBoxSpacing * 2, labelHeight);

        g2d.setColor(Color.BLACK);
        g2d.drawRect(nameXCoord - selectionBoxSpacing,
          yCoord - labelDescent - labelHeight / 2 - selectionBoxSpacing,
          nameWidth + selectionBoxSpacing * 2, labelHeight);
      }

      // Draw Map Name
      g2d.drawString(name, nameXCoord, yCoord);

      // Draw backbone label if needed
      if (bbSegment != null)
      {
        if (map == bbSegment.getParent())
        {
          String chrString = "Backbone: " + bbSegment.getChromosome().getName();
          int chrStringWidth = metrics.stringWidth(chrString);

          int chrXCoord = nameXCoord + nameWidth / 2 - chrStringWidth / 2;
          g2d.drawString(chrString, chrXCoord, yCoord + labelHeight);
        }
      }
    }

    if (selectedMap != null && titleDragPoint != null)
    {
      int nameWidth = metrics.stringWidth(selectedMap.getMap().getName());
      int xCoord = titleDragPoint - nameWidth / 2;

      g2d.setColor(Color.WHITE);
      g2d.fillRect(xCoord - selectionBoxSpacing,
        yCoord - labelDescent - labelHeight / 2 - selectionBoxSpacing,
        nameWidth + selectionBoxSpacing * 2, labelHeight);

      g2d.setColor(Color.BLACK);
      g2d.drawRect(xCoord - selectionBoxSpacing,
        yCoord - labelDescent - labelHeight / 2 - selectionBoxSpacing,
        nameWidth + selectionBoxSpacing * 2, labelHeight);

      g2d.drawString(selectedMap.getMap().getName(), xCoord, yCoord);
    }
    
    // Allow loading or paint again
    lock.unlock();
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
   */
  public void mousePressed(MouseEvent e)
  {
    FontMetrics metrics = getFontMetrics(VCMap.labelFont);
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");

    for (DisplayMap map : mapNavigator.getDisplayMaps())
    {
      if (!map.isVisible())
        continue;

      int nameWidth = metrics.stringWidth(map.getMap().getName());
      int nameXCoord = mapNavigator.getDisplayMapXCoord(map)
        + map.getFeatureColumnsWidth(mainGUI.getOptions())
        + mainGUI.getOptions().getIntOption("segmentWidth") / 2
        - nameWidth / 2;

      if (nameXCoord - selectionBoxSpacing <= e.getX() && e.getX() <= nameXCoord + nameWidth + selectionBoxSpacing
        && 0 <= e.getY() && e.getY() <= metrics.getHeight() * 2)
      {
        // Check for a double click
        if (e.getClickCount() == 2)
        {
          // If a double click, load the URL for this map
          mapNavigator.openMapURL(map.getMap());
          break;
        }
        else
        {
          clickDifference = nameXCoord + nameWidth / 2 - e.getX();
          int mapTitleCenter = e.getX() + clickDifference;
          titleDragPoint = new Integer(mapTitleCenter);
          mapNavigator.setTitleDragPoint(titleDragPoint);
          break;
        }
      }
    }

    for (LabelRect rect : labelRects)
    {
      if (rect.labelText.equals("QTL") && rect.contains(e.getPoint()))
      {
        if (status == null)
          status = mainGUI.getStatusBar();

        status.showLoadedQTL(e, rect.x);
      }
    }

    repaint();
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
   */
  public void mouseDragged(MouseEvent e)
  {
    if (titleDragPoint != null)
    {
      setCursor(new Cursor(Cursor.MOVE_CURSOR));

      int mapTitleCenter = e.getX() + clickDifference;
      titleDragPoint = new Integer(mapTitleCenter);
      mapNavigator.setTitleDragPoint(titleDragPoint);
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
   */
  public void mouseReleased(MouseEvent e)
  {
    if (titleDragPoint != null)
    {
      titleDragPoint = null;
      clickDifference = -1;

      mapNavigator.setTitleDragPoint(titleDragPoint);

      // Set cursor
      mouseMoved(e);
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
   */
  public void mouseMoved(MouseEvent e)
  {
    // Remove previous tooltips after the mouse moves
    if (getToolTipText() != null) setToolTipText(null);

    FontMetrics metrics = getFontMetrics(VCMap.labelFont);
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");
    boolean defaultCursor = true;
    hoveredMap = null;

    for (DisplayMap map : mapNavigator.getDisplayMaps())
    {

      int xStart = mapNavigator.getDisplayMapXCoord(map);
      if (xStart < e.getX() && e.getX() < xStart + map.getFeatureColumnsWidth(mainGUI.getOptions()))
        hoveredMap = map;

      if (!map.isVisible())
        continue;

      int nameWidth = metrics.stringWidth(map.getMap().getName());
      int nameXCoord = mapNavigator.getDisplayMapXCoord(map)
        + map.getFeatureColumnsWidth(mainGUI.getOptions())
        + mainGUI.getOptions().getIntOption("segmentWidth") / 2
        - nameWidth / 2;

      if (qtlHover)
      {
        qtlHover = false;
        repaint();
      }

      if (nameXCoord - selectionBoxSpacing <= e.getX() && e.getX() <= nameXCoord + nameWidth + selectionBoxSpacing
        && 0 <= e.getY() && e.getY() <= metrics.getHeight())
      {
        defaultCursor = false;
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        break;
      }
    }

    // Check for truncated "type" labels
    for (LabelRect rect : labelRects)
    {
      if (rect.contains(e.getPoint()))
      {
        if (rect.labelText.equals("QTL"))
        {
          defaultCursor = false;
          qtlHover = true;
          setCursor(new Cursor(Cursor.HAND_CURSOR));
          repaint();
        }
        
        // Show ToolTip for truncated labels
        if (!rect.labelText.equals(rect.set.getType()))
          setToolTipText(rect.set.getType());
        
        // Already found the label we are inside, no need to check others
        break;
      }
    }
    
    // Check for truncated "version" labels
    for (LabelRect rect : versionRects)
    {
      if (rect.contains(e.getPoint()))
      {
        // Show ToolTip for all version labels
        setToolTipText("<html>" + rect.set.getSource() + "<br>Version: " + rect.set.getVersion() + 
            "<br>Released on: " + rect.set.getReleaseDate() + "</html>");
        
        // Already found the label we are inside, no need to check others
        break;
      }
    }

    // Fix our cursor
    if (defaultCursor) setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
   */
  public void mouseExited(MouseEvent e)
  {
    if (qtlHover)
    {
      qtlHover = false;
      repaint();
    }
    if (getToolTipText() != null)
    {
      setToolTipText(null);
    }
  }

  // Unused methods, but required
  public void mouseEntered(MouseEvent e) {}
  public void mouseClicked(MouseEvent e) {}
}
