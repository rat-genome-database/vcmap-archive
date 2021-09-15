package bioneos.vcmap.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.dialogs.DetailsDialog;
import bioneos.vcmap.gui.dialogs.PreferencesDialog;
import bioneos.vcmap.gui.dialogs.SearchDialog;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.DisplayAnnotation;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.Selection;
import bioneos.vcmap.model.SelectionInterval;
import bioneos.vcmap.model.SyntenyBlock;
import bioneos.vcmap.model.UnitLabel;
import bioneos.vcmap.model.comparators.SortAnnotationByHeight;
import bioneos.vcmap.model.comparators.SortAnnotationByMiddle;
import bioneos.vcmap.model.comparators.SortAnnotationByStart;
import bioneos.vcmap.model.comparators.SortAnnotationByStop;
import bioneos.vcmap.model.comparators.SortDisplayAnnotationByLabelYCoord;
import bioneos.vcmap.model.comparators.SortDisplayAnnotationByMiddle;
import bioneos.vcmap.util.Util;

/**
 * <p>{@link MapNavigator} is a {@link JPanel} that displays maps on the screen based on
 * the {@link DisplayMap} in memory, the current zoom level and based on where
 * the horizontal and vertical scrollbars are located. Specifically, this class
 * displays displayable maps as vertical bars on the screen. The class also
 * displays visible annotation for the displayable maps and lines from one
 * parallel map to another to show the relationship between the maps.</p>
 *
 * <p>Created on: July 2, 2008</p>
 * @author sgdavis@bioneos.com
 */

public class MapNavigator
extends JPanel
implements MouseListener, MouseMotionListener, MouseWheelListener
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  private MainGUI mainGUI;
  private MapHeaders mapHeaders;

  // Data Variables
  private Vector<DisplayMap> displayMaps;
  private DisplaySegment backboneSegment;
  private Vector<DisplaySegment> segments;

  // Locking variables
  private ReentrantLock lock = new ReentrantLock();

  // Drawing/Measurement Variables
  private int headerHeight;
  private int leftMarginWidth;
  private double zoomLevel;
  private double zoomBarPosition;
  private int drawingHeight;
  private int drawingWidth;
  private int maxWatermarkWidth;
  private int numMapsVisible;
  private int unitLabelDisplayThreshold;
  private int minFeatureSelected;
  private int maxFeatureSelected;
  private double selectionPercentSize;
  // Selection Variables
  private Selection selection;

  // Mouse over variables
  private DisplayMap mouseOverMap;
  private DisplaySegment segmentPressed;
  private MouseEvent mousePressedEvent;
  private OverlapBox overlapBoxPressed;

  // Feature Selection variables
  private DisplayMap featureSelectionMap;
  private Vector<Rectangle> featureSelectionBoxes;

  // Drag Display Map Variables
  private Integer titleDragPoint;
  private Rectangle overlapDragRect;
  // Zoom Variables
  private Point doubleClickZoomPos;

  // Show/Hidden variables
  private HashSet<Annotation> hiddenAnnotation;
  private Vector<String> hiddenTypes;

  //Image viewing parameter states
  private boolean otherAnnotationsRemoved;
  private boolean previewing;
  private boolean blueBoxes = true;
  private boolean greenBoxes = true;
  private boolean timing = true;

  // Overlap Box
  //  private OverlapBox overlapBox;
  private Vector<OverlapBox> overlapBoxes;
  private Annotation previousSpotlight;

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());


  // Final variables (could be put in options)
  /**
   * Represents the dash width that is used when creating a dashed line
   */
  public static final float[] DASH_WIDTH = {5.0f};
  /**
   * The {@link BasicStroke} used when drawing on partially selected {@link Annotation} groups
   */
  public static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f,BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, DASH_WIDTH, 0.0f);
  /**
   * The angle used when drawing the stripes on Chromosomes greater than 22
   */
  public static final int ANGLE_FOR_STRIPES = 45;
  /**
   * The number of chromosomes with colors in the system
   */
  public static final int COLORED_CHROMOSOMES = 22;
  /**
   * The portion of the backbone that the synteny takes up
   */
  public static final int SYNTENY_WIDTH_FACTOR = 3;
  /**
   * Used for the lines on the chromosomes that are numbered greater than 22
   */
  public static final BasicStroke CHROMOSOME_LINE_STROKE =
    new BasicStroke(4, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL);
  /**
   * spacing for the lines on the chromosomes greater than 22
   */
  public static final int LINE_SPACING = 20;
  /**
   * <p>Constructor sets the references and initializes the necessary data
   * to display {@link DisplayMap}s.</p>
   *
   * @param parent
   *   {@link MainGUI} is the parent
   */
  public MapNavigator(MainGUI parent)
  {
    mainGUI = parent;
    timing = mainGUI.getVCMap().getDebug();
    segments = new Vector<DisplaySegment>();
    // Set variables to default values
    featureSelectionBoxes = new Vector<Rectangle>();
    hiddenAnnotation = new HashSet<Annotation>();
    hiddenTypes = new Vector<String>();
    headerHeight = mainGUI.getOptions().getIntOption("headerHeight");
    if (headerHeight < 10) headerHeight = 10;
    leftMarginWidth = mainGUI.getOptions().getIntOption("leftMarginWidth");
    displayMaps = new Vector<DisplayMap>();
    backboneSegment = null;
    overlapBoxes = new Vector<OverlapBox>();
    zoomLevel = 1;
    selection = new Selection(this);
    unitLabelDisplayThreshold = 20;
    minFeatureSelected = 0;
    maxFeatureSelected = 0;

    // Set default panel size
    int width = mainGUI.getOptions().getIntOption("mainWindowWidth");
    int height = mainGUI.getOptions().getIntOption("mainWindowHeight");
    setSize(new Dimension(width, height));
    setPreferredSize(new Dimension(width, height));
    logger.debug("Preferred size of Map Navigator set to: " + width + "x" + height);

    setOpaque(true);
    addMouseMotionListener(this);
    addMouseListener(this);
    addMouseWheelListener(this);
  }

  public Lock getLock()
  {
    return lock;
  }

  /**
   * <p>Set the {@link MapHeaders} for this {@link MapNavigator}.</p>
   *
   * @param mapHeaders
   *   {@link MapHeaders} for this {@link MapNavigator}
   */
  public void setMapHeaders(MapHeaders mapHeaders)
  {
    this.mapHeaders = mapHeaders;
  }

  /**
   * <p>Make the necessary calculations to be able to draw a {@link Chromosome}
   * object as a new Backbone.  This method will query the main {@link MainGUI}
   * class for the {@link MapData} objects that have been loaded into memory.
   * This method can safely assume that the user has already been warned that
   * all of the currently loaded data will be erased.</p>
   */
  public void loadBackboneMap()
  {
    logger.debug("New backbone being loaded");

    // Ensure drawingHeight is set
    logger.debug("Ensure drawing height is set properly");
    JViewport vp = (JViewport)getParent();
    int viewportHeight = vp.getBounds().height;
    drawingHeight = (int)(viewportHeight * zoomLevel) - headerHeight - mainGUI.getOptions().getIntOption("footerHeight");

    logger.debug("Clearing old data");
    displayMaps.clear();
    clearSelection();
    updateStatusBar();
    hiddenAnnotation.clear();

    // Build the new display map / segment
    logger.debug("Create DisplayMap");
    segments.clear();
    Chromosome bb = mainGUI.getBackbone();
    DisplayMap newMap = new DisplayMap(bb.getMap());
    DisplaySegment newSeg = new DisplaySegment(newMap, bb.getName(), 0, bb.getLength());
    newSeg.setUnitsPerPercent(bb.getLength() / (double)100);
    newMap.addSegment(newSeg);
    backboneSegment = newSeg;
    newMap.setPosition(0);

    // Add the map
    logger.debug("Add displayMap");
    segments.add(backboneSegment);
    displayMaps.add(newMap);
    buildSiblings();
  }

  /**
   * <p>Load an off-backbone map without synteny</p>
   *
   * @param newMap
   *   {@MapData} to display in the {@link MapNavigator}
   */
  public void loadMap(MapData newMap)
  {
    if (backboneSegment == null) return;
    if (!backboneSegment.getParent().getMap().getSpecies().equals(newMap.getSpecies())) return;

    logger.debug("Load off-backbone map without synteny");

    // Create displayMap
    DisplayMap displayMap = new DisplayMap(newMap);
    determinePosition(displayMap);

    try
    {
      // Load Chromosome
      logger.debug("Load same chromosome as matching map");
      Chromosome chrom = newMap.getChromosome(backboneSegment.getChromosome().getName());
      chrom.loadRegion(0, chrom.getLength());

      logger.debug("Setup DisplayMap");
      DisplaySegment newSeg = new DisplaySegment(displayMap, chrom.getName(), 0, chrom.getLength());
      newSeg.setUnitsPerPercent(chrom.getLength() / (double)100);
      if (!segments.contains(newSeg))
      {
        segments.add(newSeg);
      }
      displayMap.addSegment(newSeg);
    }
    catch (SQLException e)
    {
      String text = "There was a problem while trying to communicate with the VCMap\n";
      text += "database.  Please try again later.\n\n";
      text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
      String log = "Error loading a backbone map: " + e;
      ErrorReporter.handleMajorError(mainGUI, text, log);
    }
    addDisplayMap(displayMap);
  }

  /**
   * <p>Make the necessary calculations to be able to draw a {@link MapData}
   * so that it will align properly with the backbone map in memory.</p>
   *
   * @param newMap
   *   {@link MapData} to be displayed in a visual manner that shows
   *   how the map relates to the backbone.
   * @param synteny
   *   {@link SyntenyBlock}s that relate the {@link DisplayMap} to the
   *   backbone currently in memory.
   */
  public void loadMap(MapData newMap, Vector<SyntenyBlock> synteny)
  {
    if (synteny == null ) loadMap(newMap);
    if (synteny.size() == 0) loadMap(newMap);

    logger.debug("Create DisplayMap for new map to be displayed");

    // Create displayMap
    DisplayMap displayMap = new DisplayMap(newMap);
    determinePosition(displayMap);

    long begin = System.nanoTime();
    logger.debug("Creating DisplayMap - Use synteny to align segments");
    boolean segmentNotLoaded = false;

    for (SyntenyBlock block : synteny)
    {
      // Skip any blocks that had loading errors
      if (block == null) continue;

      // Otherwise, create the DisplaySegment
      int startOnBB = block.getLeftStart();
      int stopOnBB = block.getLeftStop();
      int start = block.getRightStart();
      int stop = block.getRightStop();
      if (block.getLeftStart() > block.getLeftStop())
      {
        // If the left side of the block is flipped, always flip both coords
        // NOTE: This ensures the left side of the block always has 
        //   start < stop, however, it is entirely possible (and correct) to
        //   have the right side of the block with start > stop.  This is
        //   simply rendered as if the block is upside down, with visible top
        //   coordinate > bottom coordinate on the scale. 
        startOnBB = block.getLeftStop();
        stopOnBB = block.getLeftStart();
        start = block.getRightStop();
        stop = block.getRightStart();
      }

      // Check for a problem with loaded synteny
      if (startOnBB == -1
          || stopOnBB == -1
          || start == -1
          || stop == -1)
      {
        StringBuilder error = new StringBuilder();
        error.append("Problem with loaded synteny block\n");

        error.append(newMap.getName()).append(" chr:").append(block.getRightChr().getName());
        error.append("\n");

        error.append("Backbone: ");
        error.append(backboneSegment.getChromosome().getName());
        error.append(" ");
        error.append(backboneSegment.getChromosome().getMap().getName());

        logger.error(error.toString());
        ErrorReporter.handleMajorError(mainGUI, error.toString(), error.toString());
        continue;
      }

      // Create new segment
      DisplaySegment newSeg = new DisplaySegment(displayMap,
          block.getRightChr().getName(),
          start,
          stop);

      // Assign synteny block
      newSeg.setSyntenyBlock(block);

      // Calculate and set unitsPerPercent
      double segLength = Math.abs(stop - start);
      double segPercent = ((stopOnBB - startOnBB) / (double) mainGUI.getBackbone().getLength()) * 100;
      newSeg.setUnitsPerPercent(segLength / segPercent);

      //
      // Check for overlapping segments
      //
      // Math.floor() is in case one segment starts exactly where a previous
      // segment ends. This will allow both segments to be shown if they
      // have the same end/start position
      double newStart = getSegmentYCoord(newSeg);
      double newStop = newStart + getSegmentHeight(newSeg);
      boolean overlap = false;

      // Check for overlaps
      // FIXME - The overlap needs to actually check coordinates off-backbone, not pixels...
      for (DisplaySegment s : displayMap.getSegments())
      {
        double oldStart = getSegmentYCoord(s);
        double oldStop = oldStart + getSegmentHeight(s);

        if (newStart < oldStart && oldStart < newStop)
        {
          overlap = true;
          break;
        }
        else if (newStart < oldStop && oldStop < newStop)
        {
          overlap = true;
          break;
        }
        else if (oldStart < newStart && newStart < oldStop)
        {
          overlap = true;
          break;
        }
        else if (oldStart < newStop && newStop < oldStop)
        {
          overlap = true;
          break;
        }
      }

      // don't add this segment
      if (overlap)
      {
        // remove overlapping segment
        segmentNotLoaded = true;
        continue;
      }
      // Add segment
      if (!segments.contains(newSeg))
      {
        segments.add(newSeg);
        displayMap.addSegment(newSeg);
      }
    }
    long timing = (System.nanoTime() - begin);
    addTiming("MapNavigator - loadMap - Display Map Setup", timing);

    // Alert the user that a segment(s) wasn't loaded
    if (segmentNotLoaded)
    {
      String message = "A segment or segments on map \"" + displayMap.getMap().getName() + "\"\n";
      message += "could not be displayed because they overlap with other segments.  The\n";
      message += "segments without any overlap will be displayed as they normally would be.";
      JOptionPane.showMessageDialog(mainGUI, message, "Segment Overlap", JOptionPane.INFORMATION_MESSAGE);
      logger.warn(message);
    }

    addDisplayMap(displayMap);
  }

  /**
   * <p>Load a map using a non-backbone map.</p>
   *
   * @param map
   *   {@MapData} to display in the {@link MapNavigator}
   */
  public void loadOffBackboneMap(MapData newMap)
  {
    DisplayMap oldDisplayMap = null;

    // Find map with matching species
    for (DisplayMap loadedMap : getDisplayMaps())
    {
      if (loadedMap.getMap().getSpecies().equals(newMap.getSpecies()))
      {
        oldDisplayMap = loadedMap;
        break;
      }
    }

    if (oldDisplayMap == null) return;

    DisplayMap displayMap = new DisplayMap(newMap);
    determinePosition(displayMap);

    for (DisplaySegment oldSeg : oldDisplayMap.getSegments())
    {
      SyntenyBlock block = oldSeg.getSyntenyBlock();
      double percentStart = (double)(oldSeg.getDrawingStart()) / (double)(oldSeg.getChromosome().getLength());
      double percentStop = (double)(oldSeg.getDrawingStop()) / (double)(oldSeg.getChromosome().getLength());

      try
      {
        Chromosome chrom = newMap.getChromosome(oldSeg.getChromosome().getName());

        int start = (int)(chrom.getLength() * percentStart);
        int stop = (int)(chrom.getLength() * percentStop);

        chrom.loadRegion(start, stop);

        DisplaySegment newSeg = new DisplaySegment(displayMap,
            chrom.getName(),
            start,
            stop);

        newSeg.setSyntenyBlock(block);

        // Calculate and set unitsPerPercent
        double segLength = start - stop;

        double segPercent = ((block.getLeftStop() - block.getLeftStart())
            / (double)mainGUI.getBackbone().getLength()) * 100;

        newSeg.setUnitsPerPercent(Math.abs(segLength) / Math.abs(segPercent));
        displayMap.addSegment(newSeg);
        if (!segments.contains(newSeg)) segments.add(newSeg);
      }
      catch (SQLException e)
      {
        String text = "There was a problem while trying to communicate with the VCMap\n";
        text += "database.  Please try again later.\n\n";
        text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
        String log = "Error loading a backbone map: " + e;
        ErrorReporter.handleMajorError(mainGUI, text, log);
      }
    }

    addDisplayMap(displayMap);
  }

  /**
   * <p>Add a {@link DisplayMap} to the {@link MapNavigator} after it has
   * been created.</p>
   *
   * @param displayMap
   *   {@link DisplayMap} to display in the {@link MapNavigator}
   */
  private void addDisplayMap(DisplayMap displayMap)
  {
    long begin = System.nanoTime();

    // Load same annotation types for new map as any loaded types (when possible)
    try
    {
      Vector<String> types = new Vector<String>();
      for (MapData map : mainGUI.getMaps())
        for (AnnotationSet aset : map.getLoadedAnnotationSets())
          if (!types.contains(aset.getSourceId() + ":" + aset.getType()))
            types.add(aset.getSourceId() + ":" + aset.getType());

      for (DisplaySegment segment : displayMap.getSegments())
      {
        Chromosome chrom = segment.getChromosome();
        for (String typeStr : types)
        {
          String sourceId = typeStr.substring(0, typeStr.indexOf(':'));
          String type = typeStr.substring(typeStr.indexOf(':') + 1);
          for (AnnotationSet aset : chrom.getMap().getAllAnnotationSets())
          {
            if (aset.getType().equals(type) && sourceId.equals("" + aset.getSourceId()))
            {
              chrom.loadAnnotation(aset);
              displayMap.addShownSet(aset);
            }
          }
        }
      }
      
      // Go back over our loaded maps to ensure that the default 
      // AnnotationSet is loaded on the already loaded maps
      for (DisplayMap map : displayMaps)
      {
        boolean loaded = false ;
        AnnotationSet def = displayMap.getMap().getDefaultAnnotationSet();
        for (AnnotationSet set : map.getMap().getLoadedAnnotationSets())
        {
          if (set.getType().equals(def.getType()) && set.getSourceId() == def.getSourceId())
          {
            loaded = true ;
            break ;
          }
        }
        
        if (!loaded)
        {
          for (DisplaySegment segment : map.getSegments())
          {
            Chromosome chrom = segment.getChromosome() ;
            for (AnnotationSet aset : chrom.getMap().getAllAnnotationSets())
            {
              if (aset.getType().equals(def.getType()) && aset.getSourceId() == def.getSourceId())
              {
                chrom.loadAnnotation(aset) ;
                map.addShownSet(aset) ;
              }
            }
          }
        }
      }
    }
    catch (SQLException e)
    {
      String text = "There was a problem while trying to communicate with the VCMap\n";
      text += "database.  Please try again later.\n\n";
      text += "If you are seeing this method repeatedly, please contact Bio::Neos Support.";
      String log = "Error loading a backbone map: " + e;
      ErrorReporter.handleMajorError(mainGUI, text, log);
    }
    
    addTiming("MapNavigator - addDisplayMap - Load Backbone Annotation type for Display Map", System.nanoTime() - begin);
    //logger.debug("Loaded same annotation as backbone for display map in: " + (System.nanoTime() - begin) + "ms");

    // Update Hidden Annotation 
    logger.debug("Update information used to show annotation on display map(s)");
    begin = System.nanoTime();
    for (String type : hiddenTypes)
      displayMap.setTypeShown(type, false);

    // Prepare DisplayMap
    prepareDisplayMap(displayMap);

    // Add the map
    displayMaps.add(displayMap);
    logger.debug("Added displayMap");
  }

  /*
   * Helper method to build the set of siblings in our loaded dataset. This is
   * essential for providing the "connecting" lines.  Without caching these  
   * relationships identified, we could not display those lines efficiently.
   */
  public void buildSiblings()
  {
    long begin = System.nanoTime();
    
    HashMap<Integer, Vector<Annotation>> sibMatches = new HashMap<Integer, Vector<Annotation>>();
    for (DisplayMap displayMap : displayMaps)
    {
      for (Chromosome chrom : displayMap.getMap().getLoadedChromosomes())
      {
        for (Annotation annot : chrom.getAnnotation())
        {
          for (int id : annot.getLinkIds())
          {
            Vector<Annotation> siblings = sibMatches.get(id);
            if (siblings == null) siblings = new Vector<Annotation>();
            siblings.add(annot);
            sibMatches.put(id, siblings);
          }
        }
      }
    }
    for (Integer key : sibMatches.keySet())
    {
      Vector<Annotation> siblings = sibMatches.get(key);
      for (int i = 0; i < siblings.size(); i++)
        for (int j = i + 1; j < siblings.size(); j++)
          siblings.get(i).addSibling(siblings.get(j));
    }

    addTiming("MapNavigator - buildSiblings", System.nanoTime() - begin);
    //logger.debug("Updated sibling annotation in: " + (System.nanoTime() - begin) + "ms");
  }

  /**
   * <p>Helper method that calls prepareDisplayMap(DisplayMap)
   * for every {@link DisplayMap} currently in memory.</p>
   *
   */
  public void prepareDisplayMaps()
  {
    logger.debug("Reprepare all DisplayMaps");
    for (DisplayMap displayMap : displayMaps)
      prepareDisplayMap(displayMap);
  }

  /**
   * <p>Since there is a chance of features overlapping one another and the
   * length of the feature names and units displayed are not all known or
   * constant; this method prepares a display map by determining what features
   * overlap one another, what the string widths for the feature and unit
   * labels are, what the y-coordinates for the units and features are,
   * and what the feature and unit widths should be. This method should
   * be called every time a {@link DisplayMap} is loaded into memory, the user
   * zooms in or out, or anything else happens that may change what features
   * may be displayed.</p>
   *
   * @param displayMap
   *   {@link DisplayMap} to determine all the appropriate data for so the
   *   {@link DisplayMap} can be properly displayed in the {@link MapNavigator}
   */
  private void prepareDisplayMap(DisplayMap displayMap)
  {
    long totalStart = System.nanoTime();
    logger.debug("Prepare DisplayMap: " + displayMap);

    if (displayMap == null) return;

    // Determine label string height and descent
    FontMetrics metrics = this.getFontMetrics(VCMap.labelFont);
    FontMetrics metricsItalics = this.getFontMetrics(new Font(VCMap.labelFont.getName(), Font.ITALIC, VCMap.labelFont.getSize()));
    int labelHeight = metrics.getHeight();

    // Get options
    int featureDisplayType = mainGUI.getOptions().getIntOption("featureDisplayType");
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");

    HashMap<AnnotationSet, Boolean> setShown = new HashMap<AnnotationSet, Boolean>();

    // Clear the feature column counts to prepare for new groupings
    displayMap.resetFeatureColumns();

    //
    // Group annotation for each segment on this DisplayMap
    //
    for (DisplaySegment segment : displayMap.getSegments())
    {
      logger.debug("Prepare segment: " + segment);
      long total = System.nanoTime();
      segment.resetFeatureColumns();
      //
      // Determine display features for each type
      //
      for (AnnotationSet set : displayMap.getAllAnnotationSets())
      {
        long timer1 = System.nanoTime();
        logger.debug("Prepare annotation type: " + set.toString());
        TreeSet<DisplayAnnotation> displayAnnotation = new TreeSet<DisplayAnnotation>(new SortDisplayAnnotationByLabelYCoord());
        Vector<Annotation> segmentAnnotations = new Vector<Annotation>(segment.getSegmentFeatures(set));

        // place this type in the list as false
        if (setShown.get(set) == null)
          setShown.put(set, new Boolean(false));

        // check if this type should be shown and therefore set to true
        setShown.put(set, (setShown.get(set).booleanValue() || segmentAnnotations.size() > 0)
            && mainGUI.getOptions().isShown(set.getType()) && !hiddenTypes.contains(set.getType()));

        // move to the next type if this one isn't shown
        if (!setShown.get(set)) continue;
        addTiming("MapNavigator - prepareDisplayMap - Determine DA - Initialize AnnotationSet", System.nanoTime() - timer1);
        //
        // We process QTL data differently.
        // Since QTL tends to be much larger than other types, the algorithm we
        // use is different than for the other types.
        //
        // 3 steps:
        //   - group the Annotation based on their height and position
        //   - determine the label position
        //   - determine which column the DA will be displayed in
        //
        if (set.getType().equals("QTL"))
        {
          //
          // Step 1: group the Annotation based on their height and position
          //
          // sort the annotation by height, smallest first
          //
          // By starting with the smallest Annotation, the list allDA
          // declared below will also be sorted by the smallest Annotation.
          // This is important because when we check to see if an Annotation
          // fits into a group we check the size against only an upper bound.
          // With the list ordered by the smallest Annotation in a group, we
          // know that when a group is found there isn't a better one further
          // down in the list.
          //
          Collections.sort(segmentAnnotations, new SortAnnotationByHeight());

          // set the columnNum to -1 for every
          // DisplayAnnotation before their actual
          // column is determined
          int columnNum = -1;

          // contains every DisplayAnnotation for this type
          ArrayList<DisplayAnnotation> allDA = new ArrayList<DisplayAnnotation>();

          // determine the DA for every annotation
          for (Annotation annot : segmentAnnotations)
          {
            // annotation isn't visible
            if (!isAnnotationVisible(annot)) continue;

            // first time through
            if (allDA.size() == 0)
            {
              // create a new DA
              DisplayAnnotation da = new DisplayAnnotation(metrics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
              da.setAnnotation(annot, columnNum);

              // update the lists
              allDA.add(da);
              continue;
            }
            // find a DA to add to
            else
            {
              // Set to true if this Annotation was added to an existing DA
              boolean added = false;

              // NOTE: This number is used for determining if an Annotation
              // will fit into a DA.  The bigger the number, the fewer
              // number of groups there will potentially be and the groups
              // will have a greater variety in the size of Annotation in
              // them.  The smaller the number, the more groups there will
              // potentially be and the Annotation in them will be more
              // similar in size.
              double sizeMultiplier = 1.75;

              // Check every DA available to see if the current
              //   Annotation can fit into one of them
              for (DisplayAnnotation displayed : allDA)
              {
                // Get the index and determine the middle pos of the annot
                int index = allDA.indexOf(displayed);
                int middle = (annot.getStart() + annot.getStop()) / 2;

                //
                // annot is centered on a DA and is similar in size
                //
                // NOTE: 
                // The middle of the current Annotation lies somewhere within
                // the range of the smallest Annotation currently in this DA.
                // If this is true then the Annotation is in the same area
                // on the segment and can possibly be put into this DA.
                //
                // The Annotation must also be similar in size and this is where
                // the sizeMultiplier is used.  If an Annotation's height is
                // smaller than the minHeightAnnot * sizeMultiplier for this DA
                // then this Annotation is similar in size to the rest of the
                // Annotation already in this group.
                //
                // There isn't any need to check for a lower bound since the
                // tempAnnotation is sorted by height and therefore allDA
                // is sorted by height as a consequence.
                //
                if (displayed.getMinHeightAnnot().getStart() < middle &&
                    middle < displayed.getMinHeightAnnot().getStop() &&
                    annot.getStop() - annot.getStart() < (displayed.getMinHeightAnnot().getStop() - displayed.getMinHeightAnnot().getStart()) * sizeMultiplier)
                {
                  // Current DA (displayed) now needs to be a multiple element DA
                  if (!displayed.getMetrics().equals(metricsItalics))
                    displayed.setMetrics(metricsItalics);

                  // add the annotation
                  long time = displayed.addAnnotation(annot, columnNum);
                  addTiming("MapNavigator - prepareDisplayMap - Determine DA - addAnnotation(QTL)", time);

                  // update the lists
                  allDA.set(index, displayed);
                  added = true;
                  break;
                }
              }

              // annot doesn't fit with any DA
              if (!added)
              {
                // Create a new DA
                DisplayAnnotation da = new DisplayAnnotation(metrics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                da.setAnnotation(annot, columnNum);

                // Update the lists
                allDA.add(da);
              }
            }
          }

          //
          // Step 2: determine the label positions
          //
          // The preferred positioning of the labels is at the
          // midpoint of the DA therefore we need to sort the DA by their
          // midpoints.  We want to start at the top of the map so if
          // the segment is inverted (this happens on off backbone maps)
          // the order is actually in reverse for that segment.
          //
          if (segment.getDrawingStart() > segment.getDrawingStop())
            Collections.sort(allDA, Collections.reverseOrder(new SortDisplayAnnotationByMiddle()));
          else
            Collections.sort(allDA, new SortDisplayAnnotationByMiddle());

          // determine the label positions
          determineDALabelYCoords(segment, allDA, featureDisplayType);

          //
          // Step 3: determine the column the DA will be displayed in
          //
          // Now we need to give each DA a column.  There is nothing
          // really tricky about this part.  The DAs cannot overlap
          // so when a DA can fit it is placed in the current column,
          // otherwise it is skipped and is determined later.
          //
          // The first column is actually column 0.
          columnNum = 0;

          // count of the number of DAs that still need a column
          int remaining = allDA.size();

          //
          // keep going until every DA has a column
          //
          while (remaining > 0)
          {
            DisplayAnnotation previous = null;

            // check every DA
            for (DisplayAnnotation currentDA : allDA)
            {
              // already determined the column...skip
              if (currentDA.getAnnotation().get(0).getColumn() != -1)
                continue;

              // first DA in a column
              if (previous == null)
              {
                // set the column number
                for (DisplayAnnotation.DAMember member : currentDA.getAnnotation())
                  member.setColumn(columnNum);

                currentDA.setColumn(columnNum);
              }
              // check for overlap with the previous DA
              else if (currentDA.getStartAnnot().getStart() < previous.getStopAnnot().getStop())
              {
                // there is overlap...determine this DA next time
                continue;
              }
              // no overlap
              else
              {
                // set the column number
                for (DisplayAnnotation.DAMember member : currentDA.getAnnotation())
                  member.setColumn(columnNum);

                currentDA.setColumn(columnNum);
              }

              // update
              remaining--;
              previous = currentDA;
            }

            // move to the next column
            columnNum++;
          }

          // add the completed DAs to the segment
          displayAnnotation.addAll(allDA);
          segment.addDisplayAnnotation(set, displayAnnotation);
        }
        //
        // NOTE: We process everything else here.  The algorithm used is
        // different than the previous for QTL.  Since QTL data tends to be
        // larger than other types, when grouping other types we use
        // position to group instead of size.
        //
        // The heights and positions of the DAs however are determined by the
        // by the size of the labels.  Annotation is added to a group until
        // it passes the height of a label.  When this happens a new DA is
        // created.
        //
        // This also tries its best to fit minimize the number of columns the
        // DA gets displayed in.  If however a piece ofAnnotation cannot fit
        // into the first column, the bigger pieces are pushed into the the
        // next column.  The max number of columns is not limited in any way.
        //
        // Annotation in different columns may be grouped together to avoid DA
        // that stretch the entire length of the segment for only a few pieces.
        //
        else
        {
          //
          // Group the first column into display annotation
          //
          timer1 = System.nanoTime();
          DisplayAnnotation colOneDA = null; // current display annotation being modified
          DisplayAnnotation previousDA = null; // last display annotation modified
          int previousLabelStop = (int)(getSegmentYCoord(segment)); // end position of last modified da

          // keep track of what FeatureGaps are available
          Vector<FeatureGap> gaps = new Vector<FeatureGap>();

          // the first displayed column is actually column 0
          int columnNum = 0;
          Collections.sort(segmentAnnotations, new SortAnnotationByMiddle());

          Vector<Annotation> notDisplayedYet = new Vector<Annotation>();
          Annotation previousAnnot = null;

          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Group the First Column", System.nanoTime() - timer1);
          //
          // check every Annotation and determine which pieces overlap
          //
          timer1 = System.nanoTime();
          ListIterator<Annotation> annotListIterator = segmentAnnotations.listIterator();
          Vector<Annotation> updatedSegmentAnnotations = new Vector<Annotation>();
          while (annotListIterator.hasNext())
          {
            long timer2 = System.nanoTime();
            Annotation annot = annotListIterator.next();
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Init - getNext",
                System.nanoTime() - timer2);
            // annotation isn't visible
            if (!isAnnotationVisible(annot))
            {
              updatedSegmentAnnotations.add(annot);
              continue;
            }

            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Init - annot is visible",
                System.nanoTime() - timer2);
            // first Annotation
            if (previousAnnot == null)
            {
              previousAnnot = annot;
            }
            else
            {
              Annotation nextAnnot = null;
              long timer3 = System.nanoTime();
              // check if this is the last Annotation
              if (annotListIterator.hasNext())
              {
                nextAnnot = segmentAnnotations.get(annotListIterator.nextIndex());
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Init - get next next annot",
                    System.nanoTime() - timer3);
              }
              else
              {
                nextAnnot = null;
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Init - set as last annot",
                    System.nanoTime() - timer3);
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Init",
                  System.nanoTime() - timer2);
              timer2 = System.nanoTime();
              // not processing the last element
              if (nextAnnot != null)
              {
                // overlap with next annotation
                if (nextAnnot.getStart() < annot.getStop())
                {
                  // next annotation is smaller
                  if (nextAnnot.getStop() - nextAnnot.getStart() < annot.getStop() - annot.getStart())
                  {
                    notDisplayedYet.add(annot);  // add current annotation to next column
                    //annotListIterator.remove();
                    addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - No Overlap",
                        System.nanoTime() - timer2);
                    continue;
                  }
                }
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Overlap Confirmed",
                  System.nanoTime() - timer2);
              timer2 = System.nanoTime();
              // overlap with previous annotation
              //
              // If this is true it means that the current Annotation is bigger.
              // We checked previously when annot was nextAnnot and determined that they
              // overlapped.  However we didn't want to add it to notDisplayedYet because
              // then we would have added it twice.
              if (annot.getStart() < previousAnnot.getStop() && !annot.equals(previousAnnot))
              {
                notDisplayedYet.add(annot);
                //annotListIterator.remove();
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Current Annotation Larger",
                    System.nanoTime() - timer2);
                continue;
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap - Current Annotation Smaller",
                  System.nanoTime() - timer2);

              previousAnnot = annot;
            }
            updatedSegmentAnnotations.add(annot);
          }
          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Check Annotations Overlap",
              System.nanoTime() - timer1);
          timer1 = System.nanoTime();
          // update the ordering for an inverted segment
          if (segment.getDrawingStart() > segment.getDrawingStop())
            Collections.sort(updatedSegmentAnnotations, Collections.reverseOrder(new SortAnnotationByMiddle()));
          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Update Sort",
              System.nanoTime() - timer1);
          //
          // process every feature that hasn't been displayed
          //
          timer1 = System.nanoTime();
          for (Annotation annot : updatedSegmentAnnotations)
          {
            long timer2 = System.nanoTime();
            // annotation isn't visible
            if (!isAnnotationVisible(annot)) continue;

            // start and stop coordinates for annot
            int aStart = (int)(getAnnotationStartCoord(segment, annot));
            int aStop = (int)(getAnnotationStopCoord(segment, annot));

            if (previousDA != null)
            {
              previousLabelStop = previousDA.getLabelYCoord() + labelHeight / 2 + selectionBoxSpacing;
            }
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Init",
                System.nanoTime() - timer2);
            timer2 = System.nanoTime();
            //
            // creating a new DA
            //
            if (colOneDA == null)
            {
              long timer3 = System.nanoTime();
              if (previousLabelStop < (aStart + aStop) / 2 - labelHeight / 2 - selectionBoxSpacing)
              {
                // add to display column
                DisplayAnnotation displayAnnot = new DisplayAnnotation(metrics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                displayAnnot.setAnnotation(annot, columnNum);
                displayAnnot.setLabelYCoord((aStop + aStart) / 2);
                long timer4 = System.nanoTime();
                determineFeatureGap(gaps, segment, previousDA, displayAnnot);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA - Add to Display Column - Feature Gap",
                    System.nanoTime() - timer4);

                displayAnnotation.add(displayAnnot);
                previousDA = displayAnnot;
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA - Add to Display Column",
                    System.nanoTime() - timer3);
              }
              else
              {
                colOneDA = new DisplayAnnotation(metrics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                long timer4 = System.nanoTime();
                colOneDA.setAnnotation(annot, columnNum);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA - New Display Column - setAnnotation",
                    System.nanoTime() - timer4);
                timer4 = System.nanoTime();
                determineFeatureGap(gaps, segment, previousDA, colOneDA);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA - New Display Column - Feature Gap",
                    System.nanoTime() - timer4);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA - New Display Column",
                    System.nanoTime() - timer3);
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Create new DA",
                  System.nanoTime() - timer2);
            }
            //
            // attempt to add to the current DA
            //
            else
            {
              long timer3 = System.nanoTime();
              // check if the Annotation is within the label for colOneDA
              if (previousLabelStop + labelHeight + selectionBoxSpacing * 2 < (aStart + aStop) / 2)
              {
                // There is enough room to fit two labels after the previous label
                if (colOneDA.getAnnotation().size() > 1 && metricsItalics != colOneDA.getMetrics())
                {
                  colOneDA.setMetrics(metricsItalics);
                }

                int dStart;
                int dStop;

                // get start and stop coordinates of the current DA
                if (segment.getDrawingStart() < segment.getDrawingStop())
                {
                  dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStartAnnot()));
                  dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStopAnnot()));
                }
                else
                {
                  dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStopAnnot()));
                  dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStartAnnot()));
                }

                // set the label position
                int labelYCoord = (dStart + dStop) / 2;
                if (labelYCoord < previousLabelStop + labelHeight / 2 + selectionBoxSpacing)
                  labelYCoord += previousLabelStop + labelHeight / 2 + selectionBoxSpacing - labelYCoord;

                colOneDA.setLabelYCoord(labelYCoord);

                // find any FeatureGaps
                if (displayAnnotation.size() > 0)
                  determineFeatureGap(gaps, segment, previousDA, colOneDA);

                // update for next DA
                displayAnnotation.add(colOneDA);
                previousDA = colOneDA;

                // create new DA
                colOneDA = new DisplayAnnotation(metrics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                colOneDA.setAnnotation(annot, columnNum);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Add to old DA - within label",
                    System.nanoTime() - timer3);
              }
              else
              {
                // Else add annotation to colOneDA
                //Vector<DisplayAnnotation.DAMember> annotation = colOneDA.getAnnotation();
                //annotation.add(new DisplayAnnotation.DAMember(annot, columnNum));
                long time = colOneDA.addAnnotation(annot, columnNum);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - addAnnotation", time);

                // change from a single annotation to a grouping of annotation
                if (colOneDA.getMetrics() != metricsItalics)
                  colOneDA.setMetrics(metricsItalics);
                  //colOneDA = new DisplayAnnotation(metricsItalics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));

                // update current DA
                //colOneDA.setAnnotation(annotation, featureDisplayType);

                int dStart;
                int dStop;

                // get start and stop coordinates for the current DA
                if (segment.getDrawingStart() < segment.getDrawingStop())
                {
                  dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStartAnnot()));
                  dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStopAnnot()));
                }
                else
                {
                  dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStopAnnot()));
                  dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStartAnnot()));
                }

                if (previousLabelStop < (dStart + dStop) / 2 - labelHeight / 2 - selectionBoxSpacing
                    && dStop - dStart >= labelHeight + selectionBoxSpacing * 2)
                {
                  colOneDA.setLabelYCoord((dStop + dStart) / 2);

                  if (displayAnnotation.size() > 0)
                    determineFeatureGap(gaps, segment, previousDA, colOneDA);

                  // create new DA
                  displayAnnotation.add(colOneDA);
                  previousDA = colOneDA;
                  colOneDA = null;
                }
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Add to old DA - not within label",
                    System.nanoTime() - timer3);
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features - Add to old DA",
                  System.nanoTime() - timer2);
            }
          }
          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Process other Features",
              System.nanoTime() - timer1);

          // Finish up with the final DA
          if (colOneDA != null)
          {
            if (previousDA != null)
              previousLabelStop = previousDA.getLabelYCoord() + labelHeight / 2 + selectionBoxSpacing;

            int dStart;
            int dStop;

            // get the start and stop coordinates for the DA
            if (segment.getDrawingStart() < segment.getDrawingStop())
            {
              dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStartAnnot()));
              dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStopAnnot()));
            }
            else
            {
              dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStopAnnot()));
              dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStartAnnot()));
            }

            if (previousLabelStop < (dStart + dStop) / 2 - labelHeight / 2 - selectionBoxSpacing)
            {
              if (colOneDA.getAnnotation().size() > 1 && metricsItalics != colOneDA.getMetrics())
              {
                //Vector<DisplayAnnotation.DAMember> annotation = colOneDA.getAnnotation();
                //colOneDA = new DisplayAnnotation(metricsItalics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                //colOneDA.setAnnotation(annotation, featureDisplayType);
                colOneDA.setMetrics(metricsItalics);
              }

              colOneDA.setLabelYCoord((dStop + dStart) / 2);
              displayAnnotation.add(colOneDA);
            }
            else
            {
              if (displayAnnotation.size() > 0)
              {
                DisplayAnnotation lastDA = displayAnnotation.last();
                //Vector<DisplayAnnotation.DAMember> annotation = colOneDA.getAnnotation();
                //annotation.addAll(lastDA.getAnnotation());
                //colOneDA = new DisplayAnnotation(metricsItalics, mainGUI.getOptions().getIntOption("featureLabelColumnWidth"));
                //colOneDA.setAnnotation(annotation, featureDisplayType);
                long time = colOneDA.merge(lastDA);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge", time);
              }

              if (segment.getDrawingStart() < segment.getDrawingStop())
              {
                dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStartAnnot()));
                dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStopAnnot()));
              }
              else
              {
                dStart = (int)(getAnnotationStartCoord(segment, colOneDA.getStopAnnot()));
                dStop = (int)(getAnnotationStopCoord(segment, colOneDA.getStartAnnot()));
              }

              colOneDA.setLabelYCoord((dStop + dStart) / 2);

              if (displayAnnotation.size() > 0)
                displayAnnotation.remove(displayAnnotation.last());
              displayAnnotation.add(colOneDA);
              previousDA = colOneDA;
            }
          }

          // check for last feature gap
          determineFeatureGap(gaps, segment, previousDA, null);

          //
          // Merge together
          //
          timer1 = System.nanoTime();
          ListIterator<Annotation> annotIterator = notDisplayedYet.listIterator();
          while (annotIterator.hasNext())
          {
            long timer2 = System.nanoTime();
            Annotation annot = annotIterator.next();
            int aStart = (int)(getAnnotationStartCoord(segment, annot));
            int aStop = (int)(getAnnotationStopCoord(segment, annot));

            DisplayAnnotation begin = new DisplayAnnotation();
            begin.setLabelYCoord((aStart + aStop) / 2 - labelHeight * 2 - selectionBoxSpacing);
            DisplayAnnotation end = new DisplayAnnotation();
            end.setLabelYCoord((aStart + aStop) / 2 + labelHeight * 2 + selectionBoxSpacing);
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Initialize", System.nanoTime() - timer2);
            timer2 = System.nanoTime();
            SortedSet<DisplayAnnotation> DASet= displayAnnotation.subSet(begin, end);
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Get DA subset", System.nanoTime() - timer2);
            timer2 = System.nanoTime();
            for (DisplayAnnotation da : DASet)
            {
              long timer3 = System.nanoTime();
              long timer4 = System.nanoTime();
              int dStart, dStop;
              if (segment.getDrawingStart() < segment.getDrawingStop())
              {
                long timer5 = System.nanoTime();
                dStart = (int)(getAnnotationStartCoord(segment, da.getStartAnnot()));
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - IF - start", System.nanoTime() - timer5);
                timer5 = System.nanoTime();
                dStop = (int)(getAnnotationStopCoord(segment, da.getStopAnnot()));
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - IF - stop", System.nanoTime() - timer5);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - IF", System.nanoTime() - timer4);
              }
              else
              {
                long timer5 = System.nanoTime();
                dStart = (int)(getAnnotationStartCoord(segment, da.getStopAnnot()));
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - ELSE - start", System.nanoTime() - timer5);
                timer5 = System.nanoTime();
                dStop = (int)(getAnnotationStopCoord(segment, da.getStartAnnot()));
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - ELSE - stop", System.nanoTime() - timer5);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init - ELSE", System.nanoTime() - timer4);
              }
              dStart = Math.max(dStart, da.getLabelYCoord() - labelHeight / 2 - selectionBoxSpacing);
              dStop = Math.max(dStop, da.getLabelYCoord() + labelHeight / 2 + selectionBoxSpacing);
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Init", System.nanoTime() - timer3);
              timer4 = System.nanoTime();
              if (dStart <= aStart && aStop <= dStop)
              {
                // Merge
                //Vector<DisplayAnnotation.DAMember> daAnnot = da.getAnnotation();
                //daAnnot.add(new DisplayAnnotation.DAMember(annot, columnNum));
                long timer5 = da.addAnnotation(annot, columnNum);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Add to current DA - add", timer5);
                timer5 = System.nanoTime();
                annotIterator.remove();
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Add to current DA - remove", System.nanoTime() - timer5);
                addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA - Add to current DA", System.nanoTime() - timer4);
                break;
              }
            }
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations - Build DA", System.nanoTime() - timer2);
          }
          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Merge Display Annotations", System.nanoTime() - timer1);

          // set the DAs of the segment
          segment.addDisplayAnnotation(set, displayAnnotation);

          //
          // determine the group for the Annotation not in column one
          //
          TreeSet<DisplayAnnotation> typeDA = segment.getDisplayAnnotation(set);
          timer1 = System.nanoTime();
          while (notDisplayedYet.size() > 0)
          {
            long timer2 = System.nanoTime();
            columnNum++;
            previousAnnot = null;
            Iterator<DisplayAnnotation> daIterator = typeDA.iterator();
            DisplayAnnotation currentDA = null;

            if (daIterator.hasNext())
              currentDA = daIterator.next();
            annotIterator = notDisplayedYet.listIterator();
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs - Init",
                System.nanoTime() - timer2);
            timer2 = System.nanoTime();
            long timer3 = System.nanoTime();
            while (annotIterator.hasNext())
            {
              timer3 = System.nanoTime();
              Annotation annot = annotIterator.next();
              // annotation isn't visible
              if (!isAnnotationVisible(annot)) continue;

              //
              // determine if annotation will overlap
              //
              if (previousAnnot == null)
              {
                previousAnnot = annot;
              }
              else
              {
                Annotation nextAnnot = null;

                // not processing the last element
                if (!annot.equals(notDisplayedYet.lastElement()) && annotListIterator.hasNext())
                  nextAnnot = notDisplayedYet.get(annotListIterator.nextIndex());

                else
                  nextAnnot = null;

                if (nextAnnot != null)
                {
                  // overlap with next annotation
                  if (nextAnnot.getStart() < annot.getStop())
                  {
                    // next annotation is smaller
                    if (nextAnnot.getStop() - nextAnnot.getStart() < annot.getStop() - nextAnnot.getStart())
                    {
                      continue;
                    }
                  }
                }

                // overlap with previous annotation
                if (annot.getStart() < previousAnnot.getStop() && !annot.equals(previousAnnot))
                {
                  continue;
                }

                previousAnnot = annot;
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs - Loop - Init",
                  System.nanoTime() - timer3);
              timer3 = System.nanoTime();

              //
              // determine which DisplayAnnotation this piece should go with
              //
              int annotStop = annot.getStop();
              int daAnnotStop = currentDA.getStopAnnot().getStop();

              // find which DisplayAnnotation to add to
              while (annotStop > daAnnotStop && daIterator.hasNext())
              {
                currentDA = daIterator.next();
                daAnnotStop = currentDA.getStopAnnot().getStop();
              }
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs - Loop - Figure out DA to add to",
                  System.nanoTime() - timer3);

              // add the current annotation
              //Vector<DisplayAnnotation.DAMember> a = currentDA.getAnnotation();
              //a.add(new DisplayAnnotation.DAMember(annot, columnNum));
              //currentDA.setAnnotation(a, featureDisplayType);
              timer3 = currentDA.addAnnotation(annot, columnNum);
              annotIterator.remove();
              addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs - Loop - Add/Remove Annotation", timer3);
            }
            addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs - Loop", System.nanoTime() - timer2);
          }
          addTiming("MapNavigator - prepareDisplayMap - Determine DA - Display All DAs", System.nanoTime() - timer1);

          // add every DA to the segment
          segment.addDisplayAnnotation(set, displayAnnotation);
        }

        // determine the number of columns for each segment
        // and map for this type
        segment.determineFeatureColumns(set);
        displayMap.determineFeatureColumns(set);
      }
      addTiming("MapNavigator - prepareDisplayMap - Determine DA", System.nanoTime() - total);

      // Determine the total number of columns for the map and each segment
      total = System.nanoTime();
      segment.determineFeatureColumns();
      displayMap.determineFeatureColumns();
      addTiming("MapNavigator - prepareDisplayMap - Determine Feature Columns", System.nanoTime() - total);

      //
      // Determine display units and unitsWidth
      //
      total = System.nanoTime();
      logger.debug("Prepare displayed units");
      determineUnitLabels(segment);
      addTiming("MapNavigator - prepareDisplayMap - Determine Unit Labels", System.nanoTime() - total);
    }

    // Hide types that don't have any displayed annotation
    for (AnnotationSet key : setShown.keySet())
      displayMap.setTypeShown(key.getType(), setShown.get(key).booleanValue());

    if (mainGUI.getOptions().isShown("units"))
    {
      //
      // Move first and last unit labels as needed
      //
      logger.debug("Move first and last units around so should always be displayed");
      long start = System.nanoTime();
      Vector<UnitLabel> unitLabels = new Vector<UnitLabel>();
      for (DisplaySegment segment : displayMap.getSegments())
      {
        if (segment.getUnitLabels().size() > 0)
        {
          unitLabels.add(segment.getUnitLabels().firstElement());
          unitLabels.add(segment.getUnitLabels().lastElement());
        }
      }
      for (int i = 1; i < unitLabels.size(); i++)
      {
        UnitLabel value = unitLabels.get(i);
        int j = i - 1;
        while (j >= 0 && unitLabels.get(j).getLabelYCoord() > value.getLabelYCoord())
        {
          unitLabels.remove(j+1);
          unitLabels.add(j+1, unitLabels.get(j));
          j--;
        }
        unitLabels.remove(j+1);
        unitLabels.add(j+1, value);
      }

      for (int i = 1; i < unitLabels.size(); i++)
      {
        int difference = 0;
        if ((int)(unitLabels.get(i-1).getLabelYCoord()) + labelHeight > (int)(unitLabels.get(i).getLabelYCoord()))
        {
          difference = (int)(unitLabels.get(i-1).getLabelYCoord()) + labelHeight - (int)(unitLabels.get(i).getLabelYCoord());
        }
        else if ((int)(unitLabels.get(i-1).getLabelYCoord()) == (int)(unitLabels.get(i).getLabelYCoord()))
        {
          difference = labelHeight;
        }

        // try to split
        if (difference > 0)
        {
          boolean seperate = true;
          if (i - 2 > 0)
            if ((int)(unitLabels.get(i-1).getLabelYCoord()) - difference / 2 - labelHeight
                < (int)(unitLabels.get(i-2).getLabelYCoord()))
              seperate = false;
          if (i + 1 < unitLabels.size())
            if ((int)(unitLabels.get(i).getLabelYCoord()) + difference / 2 + labelHeight
                > (int)(unitLabels.get(i+1).getLabelYCoord()) )
              seperate = false;

          if (seperate)
          {
            unitLabels.get(i-1).setLabelYCoord(unitLabels.get(i-1).getLabelYCoord() - difference / 2);
            unitLabels.get(i).setLabelYCoord(unitLabels.get(i).getLabelYCoord() + difference / 2);
            difference = 0;
          }
        }

        // try to move up
        if (difference > 0)
        {
          int j = i - 1;
          int spot = - 1;
          while (j >= 0 && spot == -1)
          {
            if (j > 0)
            {
              if ((int)(unitLabels.get(j).getLabelYCoord()) - labelHeight - difference > (int)(unitLabels.get(j-1).getLabelYCoord()))
                spot = j;
            }
            else
            {
              // special case
              if ((int)(unitLabels.get(j).getLabelYCoord()) - labelHeight - difference > labelHeight / 2)
                spot = j;
            }

            j--;
          }

          if (spot != -1)
          {
            for (j = spot; j < i; j++)
              unitLabels.get(j).setLabelYCoord(unitLabels.get(j).getLabelYCoord() - difference);
            difference = 0;
          }
        }

        // try to move down
        if (difference > 0)
        {
          int j = i;
          int spot = -1;
          while (j < unitLabels.size() && spot == -1)
          {
            if (j + 1 < unitLabels.size())
            {
              if ((int)(unitLabels.get(j).getLabelYCoord()) + labelHeight + difference
                  < (int)(unitLabels.get(j+1).getLabelYCoord()))
              {
                spot = j;
              }
            }
            else if (j + 1 == unitLabels.size())
            {
              int lastSpot = drawingHeight + headerHeight
              + mainGUI.getOptions().getIntOption("footerHeight")
              - labelHeight / 2;

              if ((int)(unitLabels.get(j).getLabelYCoord()) + labelHeight + difference < lastSpot)
              {
                spot = j;
              }
            }

            j++;
          }

          if (spot != -1)
          {
            for (j = spot; j >= i; j--)
              unitLabels.get(j).setLabelYCoord(unitLabels.get(j).getLabelYCoord() + difference);
            difference = 0;
          }
        }
      }
      addTiming("MapNavigator - prepareDisplayMap - Fix unit labels", System.nanoTime() - start);
      //logger.debug("Fixed unit labels in: " + (System.nanoTime() - start) + "ms");
    }
    long timing = (System.nanoTime() - totalStart);
    addTiming("MapNavigator - prepareDisplayMap", timing);
    //logger.debug("Total time to prepare display map: " + (System.nanoTime() - totalStart) + "ms");
  }

  /**
   * <p>Determine where the labels for each {@link DisplayAnnotation} will be placed.
   * This method tries to place each label at the midpoint of the {@link DisplayAnnotation}
   * it represents.  If it will not fit there, it tries three ways of placing the label.
   * The first two ways involve checking above the preferred position.  By trying
   * first to place the label above, as much of the room above will be used.  This is
   * important because there is no way to know how many labels still need to be placed
   * below this label and where those labels need to be placed.</p>
   * <p>The first attempt to place a label above involves moving only the current label.
   * It examines where eachlabel above it is and attempts to place the current label
   * directly above a previously determined label.  If there is not room or if it
   * passes the top of the {@link DisplayAnnotation} then it moves on to the second method.</p>
   * <p>The second way of positioning a label involes adjusting the labels above the preferred
   * position.  The current label is placed at its preferred position and the label above
   * is adjusted to fit directly above.  This is continued until all the necessary labels are
   * adjusted or if this method fails to adjust all the labels.</p>
   * <p>When these two methods fail, the label is simply placed below the previous label
   * it overlaps.</p>
   *
   * @param segment
   *    {@link DisplaySegment} that these {@link DisplayAnnotation} belong to
   * @param allDA
   *    the list of all the {@link DisplayAnnotation} that labels need to be
   *    determined for
   * @param featureDispalyType
   *    the type of annotation the {@link DisplayAnnotation} is for
   *
   */
  private void determineDALabelYCoords(DisplaySegment segment, ArrayList<DisplayAnnotation> allDA, int featureDisplayType)
  {
    long begin = System.nanoTime();
    // the height of a label
    int labelHeight = this.getFontMetrics(VCMap.labelFont).getHeight();

    // spacing around the label
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");

    // total height of the label and the spacing
    int boxHeight = labelHeight + (selectionBoxSpacing * 2);

    // holds the list of DAs with a determined label position
    ArrayList<DisplayAnnotation> determined = new ArrayList<DisplayAnnotation>();

    // font style for a multiple element label
    FontMetrics metricsItalics = this.getFontMetrics(new Font(VCMap.labelFont.getName(), Font.ITALIC, VCMap.labelFont.getSize()));

    //
    // determine the label position for each DA
    //
    for (DisplayAnnotation da : allDA)
    {
      // index of the current DA
      int index = allDA.indexOf(da);

      // start and stop coordinates for the DA
      int start = 0;
      int stop = 0;

      // check for inverted segment
      if (segment.getDrawingStart() < segment.getDrawingStop())
      {
        start = (int)(getAnnotationStartCoord(segment, da.getStartAnnot()));
        stop = (int)(getAnnotationStopCoord(segment, da.getStopAnnot()));
      }
      else
      {
        start = (int)(getAnnotationStartCoord(segment, da.getStopAnnot()));
        stop = (int)(getAnnotationStopCoord(segment, da.getStartAnnot()));
      }

      // where the label ideally would liked to be placed
      int preferredPosition = (start + stop) / 2;

      // first label
      if (index == 0)
      {
        da.setLabelYCoord(preferredPosition);
        determined.add(da);
        continue;
      }

      // label fits without moving any labels around
      if (preferredPosition - boxHeight > determined.get(determined.size() - 1).getLabelYCoord())
      {
        da.setLabelYCoord(preferredPosition);
        determined.add(da);
      }
      // adjust labels to fit in the new one
      else
      {
        // offset to the previous label in the list
        int offset = 2;

        // new possible position for the label
        int tempPosition = determined.get(determined.size() - 1).getLabelYCoord() - boxHeight;

        // true when the label has been placed
        boolean skip = false;

        // find a place for the label within the bounds of the DA
        while (tempPosition - (boxHeight / 2) > start && determined.size() - offset >= 0)
        {
          // check if the label will fit here
          if (tempPosition - boxHeight > determined.get(determined.size() - offset).getLabelYCoord())
          {
            da.setLabelYCoord(tempPosition);
            determined.add(determined.size() - offset + 1, da);
            skip = true;
            break;
          }

          // update the possible position and the offset
          tempPosition = determined.get(determined.size() - offset).getLabelYCoord() - boxHeight;
          offset++;
        }

        // determined the label position for this DA
        if (skip)
          continue;

        // check if label fits at the very top
        if (determined.size() - offset < 0 && tempPosition - boxHeight > start)
        {
          da.setLabelYCoord(tempPosition);
          determined.add(0, da);
          continue;
        }


        // recursive method to see if the labels can be adjusted
        if (pushUpLabels(determined.size() - 1, preferredPosition, segment, determined))
        {
          da.setLabelYCoord(preferredPosition);
          determined.add(da);
          continue;
        }

        // place the label below the last one
        if (determined.get(determined.size() - 1).getLabelYCoord() + boxHeight < stop)
        {
          da.setLabelYCoord(determined.get(determined.size() - 1).getLabelYCoord() + boxHeight);
          determined.add(da);
          continue;
        }

        //
        // Combine DAs when a label won't fit
        //
        // Update the font to indicate multiple elements
        if (da.getMetrics() != metricsItalics)
          da.setMetrics(metricsItalics);

        // update the Annotation for the group
        da.merge(determined.get(determined.size() - 1));
        da.setLabelYCoord(determined.get(determined.size() - 1).getLabelYCoord());
        determined.set(determined.size() - 1, da);
      }
    }

    // Reset allDA with the new groupings (groups can be combined in this method)
    allDA.clear();
    allDA.addAll(determined);
    addTiming("MapNavigator - determineDALabelYCoords", System.nanoTime() - begin);
  }

  /**
   * <p>Sometimes the label positions need to be adjusted in order to fit in a new label.
   * This method will adjust one label at a time and determine if this adjustment is
   * enough to fit the new label in.  It is a recursive method and will continue until
   * the new label fits or it reaches the end of the list without being able to place
   * the new label.</p>
   *
   * @param index
   *    the index of the label to adjust
   * @param preferredPosition
   *    the position of the label below index
   * @param segment
   *    {@DisplaySegment} which the {@link DisplayAnnotation} belong
   * @param determined
   *    list of {@link DisplayAnnotation} whose label position has been determined
   *
   * @return
   *    true - the labels were adjusted and the new label fits
   *    false - the labels could not be adjusted and the new label doesn't fit
   */
  private boolean pushUpLabels(int index, int position, DisplaySegment segment, ArrayList<DisplayAnnotation> determined)
  {
    int labelHeight = this.getFontMetrics(VCMap.labelFont).getHeight();
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");
    int boxHeight = labelHeight + (selectionBoxSpacing * 2);
    int start = (int)(getAnnotationStartCoord(segment, determined.get(index).getStartAnnot()));

    // there is no previous label to adjust
    if (index < 1)
      return false;

    // the position of the label before being adjusted
    int original = determined.get(index).getLabelYCoord();

    // adjust the label to its new position
    determined.get(index).setLabelYCoord(position - boxHeight);

    // check if the adjustment is successful and the new label will fit
    if (determined.get(index).getLabelYCoord() - boxHeight > determined.get(index - 1).getLabelYCoord() &&
        determined.get(index).getLabelYCoord() - (boxHeight / 2) > start)
    {
      return true;
    }
    // check if the adjustment goes outside the bounds of the OverlapBox
    else if (determined.get(index).getLabelYCoord() - (boxHeight / 2) < start)
    {
      // replace with the original position of the label...adjustment was not successful
      determined.get(index).setLabelYCoord(original);
      return false;
    }
    // the adjustment was made but caused more overlap...try again
    else
    {
      // recursive call to adjust the labels
      if (pushUpLabels(index - 1, position - boxHeight, segment, determined))
      {
        return true;
      }
      else
      {
        // replace with the original position of the label...adjustment was not successful
        determined.get(index).setLabelYCoord(original);
        return false;
      }
    }
  }

  /**
   * <p>Determines if there is a {@link FeatureGap} between the two pieces of
   * {@link DisplayAnnotation} where another piece of {@link DisplayAnnotation}
   * can presumably be displayed.</p>
   *
   * @param gaps
   *   {@link Vector} of {@link FeatureGap}s to add the new {@link FeatureGap} to
   * @param segment
   *   {@link DisplaySegment} the {@link FeatureGap}s and {@link DisplayAnnotation}
   *   correspond to
   * @param da1
   *   {@link DisplayAnnotation} with the smaller start y-coordinate position
   * @param da2
   *   {@link DisplayAnnotation} with the larger start y-coordinate position
   */
  private void determineFeatureGap(Vector<FeatureGap> gaps, DisplaySegment segment, DisplayAnnotation da1, DisplayAnnotation da2)
  {
    if (gaps == null || segment == null) return;

    FontMetrics metrics = this.getFontMetrics(VCMap.labelFont);
    int labelHeight = metrics.getHeight();
    int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");
    int labelSpace = labelHeight + selectionBoxSpacing * 2;
    int featureSpace = 5; // 5 pixels between the annotation to fit the connecting line

    if (da1 == null && da2 != null)
    {
      // Check top
      int da2AnnotStart;
      if (segment.getDrawingStart() < segment.getDrawingStop())
        da2AnnotStart = (int)getAnnotationStartCoord(segment, da2.getStartAnnot());
      else
        da2AnnotStart = (int)getAnnotationStartCoord(segment, da2.getStopAnnot());

      int da2LabelStart = da2.getLabelYCoord() - labelHeight / 2 - selectionBoxSpacing;

      int segmentAnnotStop = (int)getSegmentYCoord(segment);
      int segmentLabelStop = segmentAnnotStop - labelHeight / 2 - selectionBoxSpacing;

      if (da2LabelStart - segmentLabelStop > labelSpace && da2AnnotStart - segmentAnnotStop > featureSpace)
      {
        FeatureGap gap = new FeatureGap();
        gap.setAnnotationInterval(segmentAnnotStop, da2AnnotStart);
        gap.setLabelInterval(segmentLabelStop, da2LabelStart);
        gaps.add(gap);
      }
    }
    else if (da1 != null && da2 != null)
    {
      // Check middle
      int da1AnnotStop, da2AnnotStart;
      if (segment.getDrawingStart() < segment.getDrawingStop())
      {
        da1AnnotStop = (int)getAnnotationStopCoord(segment, da1.getStopAnnot());
        da2AnnotStart = (int)getAnnotationStartCoord(segment, da2.getStartAnnot());
      }
      else
      {
        da1AnnotStop = (int)getAnnotationStopCoord(segment, da1.getStartAnnot());
        da2AnnotStart = (int)getAnnotationStartCoord(segment, da2.getStopAnnot());
      }
      int da1LabelStop = da1.getLabelYCoord() + labelHeight / 2 + selectionBoxSpacing;
      int da2LabelStart = da2.getLabelYCoord() - labelHeight / 2 - selectionBoxSpacing;

      if (da2LabelStart - da1LabelStop > labelSpace && da2AnnotStart - da1AnnotStop > featureSpace)
      {
        FeatureGap gap = new FeatureGap();
        gap.setAnnotationInterval(da1AnnotStop, da2AnnotStart);
        gap.setLabelInterval(da1LabelStop, da2LabelStart);
        gaps.add(gap);
      }
    }
    else if (da1 != null && da2 == null)
    {
      // Check bottom
      int da1AnnotStop;
      if (segment.getDrawingStart() < segment.getDrawingStop())
        da1AnnotStop = (int)getAnnotationStopCoord(segment, da1.getStopAnnot());
      else
        da1AnnotStop = (int)getAnnotationStopCoord(segment, da1.getStartAnnot());

      int da1LabelStop = da1.getLabelYCoord() + labelHeight / 2 + selectionBoxSpacing;

      int segmentAnnotStart = (int)getSegmentYCoord(segment) + (int)getSegmentHeight(segment);
      int segmentLabelStart = segmentAnnotStart + labelHeight / 2 + selectionBoxSpacing;

      if (segmentLabelStart - da1LabelStop > labelSpace && segmentAnnotStart - da1AnnotStop > featureSpace)
      {
        FeatureGap gap = new FeatureGap();
        gap.setAnnotationInterval(da1AnnotStop, segmentAnnotStart);
        gap.setLabelInterval(da1LabelStop, segmentLabelStart);
        gaps.add(gap);
      }
    }
    else
    {
      // check top, middle, bottom
      int segmentAnnotStop = (int)getSegmentYCoord(segment);
      int segmentLabelStop = segmentAnnotStop - labelHeight / 2 - selectionBoxSpacing;
      int segmentAnnotStart = (int)getSegmentYCoord(segment) + (int)getSegmentHeight(segment);
      int segmentLabelStart = segmentAnnotStart + labelHeight / 2 + selectionBoxSpacing;

      if (segmentLabelStart - segmentLabelStop > labelSpace && segmentAnnotStart - segmentAnnotStop > featureSpace)
      {
        FeatureGap gap = new FeatureGap();
        gap.setAnnotationInterval(segmentAnnotStop, segmentAnnotStart);
        gap.setLabelInterval(segmentLabelStop, segmentLabelStart);
        gaps.add(gap);
      }
    }
  }

  /**
   * <p>Returns the Labels required for the specified segment and zoom multiplier.</p>
   *
   * @param segment
   *    DisplaySegment for which to draw the unit labels.
   * @param zoom
   *    A double representing the zoom multiplier value.
   *
   * @return
   *    TreeSet of {@link UnitLabel}s for this segment and zoom level
   */
  private void determineUnitLabels(DisplaySegment segment)
  {
    long begin = System.nanoTime();
    
    // First remove all existing unit labels
    segment.clearUnitLabels();

    // Now process all the labels for this segment
    FontMetrics metrics = this.getFontMetrics(VCMap.unitFont);
    if (mainGUI.getOptions().isShown("units") && getSegmentHeight(segment) > unitLabelDisplayThreshold)
    {
      long timeStart = System.nanoTime();
      double segmentYCoord = getSegmentYCoord(segment);
      double segmentHeight = getSegmentHeight(segment);
      String unitsString = segment.getParent().getMap().getUnitsString();
      StringBuilder unitsLabel = new StringBuilder();

      // Start unit label
      unitsLabel.append(segment.getDrawingStart() / segment.getParent().getMap().getScale());
      for (int i = unitsLabel.length() - 3; i > 0; i-=3)
        unitsLabel.insert(i, ',');
      unitsLabel.append(" ");
      unitsLabel.append(unitsString);
      UnitLabel unitLabel = new UnitLabel(metrics);
      unitLabel.setLabel(unitsLabel.toString());
      unitLabel.setLabelYCoord(segmentYCoord);
      unitLabel.setUnitYCoord(segmentYCoord);
      segment.addUnitLabel(unitLabel);

      int labelWidth = unitLabel.getLabelWidth();
      if (labelWidth > segment.getParent().getUnitsColumnWidth())
        segment.getParent().setUnitsColumnWidth(labelWidth);

      addTiming("MapNavigator - determineUnitLabels - First Unit Label", System.nanoTime() - timeStart);

      // Determine other units
      logger.debug("Recursive method called to determine units");
      timeStart = System.nanoTime();
      String unitFormat = "";
      int numLabels = calcNumberOfLabels(segment.getHeight() / 100);
      int unitsPerLabel = (numLabels == 0) ? 0 : Math.abs(segment.getDrawingStop() - segment.getDrawingStart()) / numLabels;
      double divisor = 1.0;
      if (numLabels != 0 && unitsString.contains("bp"))
      {
        if (unitsPerLabel >= 1000000)
        {
          unitFormat = "0 M";
          divisor = 1.0;
        }
        else if (unitsPerLabel >= 100000)
        {
          unitFormat = "0.0 M";
          divisor = 10.0;
        }
        else if (unitsPerLabel >= 10000)
        {
          unitFormat = "0.00 M";
          divisor = 100.0;
        }
        else
        {
          unitFormat = "0.000 M";
          divisor = 1000.0;
        }
      }
      else if (numLabels != 0 && unitsString.contains("cR"))
      {
        if (unitsPerLabel >= 10)
        {
          unitFormat = "0 ";
          divisor = 1.0;
        }
        else
        {
          unitFormat = "0.0 ";
          divisor = 10.0;
        }
      }
      else if (numLabels != 0 && unitsString.contains("cM"))
      {
        if (unitsPerLabel >= 100)
        {
          unitFormat = "0 ";
          divisor = 1.0;
        }
        else if (unitsPerLabel >= 10)
        {
          unitFormat = "0.0 ";
          divisor = 100.0;
        }
        else
        {
          unitFormat = "0.00 ";
          divisor = 100.0;
        }
      }
      addTiming("MapNavigator - determineUnitLabels - Unit Label Format", System.nanoTime() - timeStart);
      timeStart = System.nanoTime();
      int midMaxStringWidth = determineDrawnUnits(numLabels - 1, segment,
          segment.getDrawingStart(), segment.getDrawingStop(), unitFormat, divisor);
      addTiming("MapNavigator - determineUnitLabels - Unit Label Recursion", System.nanoTime() - timeStart);
      if (midMaxStringWidth > segment.getParent().getUnitsColumnWidth())
        segment.getParent().setUnitsColumnWidth(midMaxStringWidth);

      // Stop unit label
      timeStart = System.nanoTime();
      unitsLabel = new StringBuilder();
      int commaCorrection = 3;

      // Get the units proper for the type of segment that is loaded
      if (unitsString.contains("bp"))
        unitsLabel.append(segment.getDrawingStop() / segment.getParent().getMap().getScale());
      else
      {
        unitsLabel.append((double) segment.getDrawingStop() / (double) segment.getParent().getMap().getScale());
        if (segment.getParent().getMap().getScale() > 99)
          commaCorrection = 6;
        else if (segment.getParent().getMap().getScale() > 9)
          commaCorrection = 5;
        else
          commaCorrection = 4;
      }

      for (int i = unitsLabel.length() - commaCorrection; i > 0; i -=3)
        unitsLabel.insert(i, ',');

      unitsLabel.append(" ");
      unitsLabel.append(unitsString);
      unitLabel = new UnitLabel(metrics);
      unitLabel.setLabel(unitsLabel.toString());
      unitLabel.setLabelYCoord(segmentYCoord + segmentHeight);
      unitLabel.setUnitYCoord(segmentYCoord + segmentHeight);
      segment.addUnitLabel(unitLabel);
      labelWidth = unitLabel.getLabelWidth();
      if (labelWidth > segment.getParent().getUnitsColumnWidth())
        segment.getParent().setUnitsColumnWidth(labelWidth);
      addTiming("MapNavigator - determineUnitLabels - Last Unit Label", System.nanoTime() - timeStart);
    }
    
    addTiming("MapNavigator - determineUnitLabels", System.nanoTime() - begin);
  }

  /**
   * Calculates the number of labels that will be made for the segment
   * @param segment
   *    The % of the drawingHeight for this segment
   * @return
   *    integer for the number of labels that will be made
   */

  public int calcNumberOfLabels(double segHeight)
  {
    int predictNum = (int)(segHeight * zoomLevel * mainGUI.getOptions().getIntOption("freqUnitLabels"));
    if (predictNum == 0) return 0;

    // Return the next power of two
    // NOTE: This is necessary for the situations where zooming in greatly will
    //   actually return a number of units where for that given # and 
    //   calculated divisor, adjacent units will be identical.  This is simply
    //   avoided by increasing the number of units shown to a power of two
    //   (and thus increasing the divisor -- to add more digits of precision)
    int powerOfTwo = 1;
    while (powerOfTwo < predictNum)
      powerOfTwo = powerOfTwo * 2;
    return powerOfTwo;
  }

  /**
   * <p>Recursive method (with a lot of parameters) that generates
   * what unit labels and where those unit labels should be displayed for a
   * specific {@link DisplaySegment}. While generating these labels the method
   * also keeps track of which label has the longest string width.</p>
   *
   * @param n
   *   The higher this value the more unit labels that will be created
   * @param segment
   *   {@link DisplaySegment} the labels belong to
   * @param lower
   *   The minimum value of area to generate a unit label in the middle of
   * @param upper
   *   The maximum value of area to generate a unit label in the middle of
   * @param unitFormat
   *   {@link String} containing the proper format for this set of unitLabels
   * @param zoom
   *   The zoom value for the unit labels to be processed
   * @return
   *   int value of the maximum string width of all the labels generated
   *
   */
  private int determineDrawnUnits(int n, DisplaySegment segment,
      double lower, double upper, String unitFormat, double divisor)
  {
    long begin = System.nanoTime();
    if (n <= 0 || (lower >= upper && segment.getDrawingStart() < segment.getDrawingStop())
        || (lower <= upper && segment.getDrawingStart() > segment.getDrawingStop()))
    {
      return 0;
    }
    else
    {
      long start = System.nanoTime();
      long begin2 = System.nanoTime();
      StringBuilder unitsLabel = new StringBuilder();
      double mid = (lower + upper) / 2.0;
      mid = Math.round(mid);
      addTiming("MapNavigator - determineDrawnUnits - Init - Format Unit Label - Rounding", System.nanoTime() - begin2);
      String label = formatUnitLabel(mid / segment.getParent().getMap().getScale(), unitFormat, divisor);
      addTiming("MapNavigator - determineDrawnUnits - Init - Format Unit Label", System.nanoTime() - begin2);
      unitsLabel.append(label);
      addTiming("MapNavigator - determineDrawnUnits - Init", System.nanoTime() - start);
      // Put commas in the numbers where required
      if (mid >= 1000 && !segment.getParent().getMap().getUnitsString().contains("bp"))
      {
        int commaCorrection = 4;
        if (unitFormat.equals("0.00 "))
          commaCorrection = 7;
        else if (unitFormat.equals("0.0 "))
          commaCorrection = 6;
        else
          commaCorrection = 4;
        for (int i = unitsLabel.length() - commaCorrection; i > 0; i -=3)
          unitsLabel.insert(i, ',');
      }
      // Determine where the units are located on the segment
      double yCoord = -1;
      if (segment.getDrawingStart() < segment.getDrawingStop())
      {
        yCoord = getSegmentYCoord(segment) + (getSegmentHeight(segment)
            * ((double)(mid - segment.getDrawingStart()) / ((double)segment.getDrawingStop() - (double)segment.getDrawingStart())));
      }
      else
      {
        yCoord = getSegmentYCoord(segment) + (getSegmentHeight(segment)
            * ((double)(segment.getDrawingStart() - mid) / ((double)segment.getDrawingStart() - (double)segment.getDrawingStop())));
      }
      // Edit label
      unitsLabel.append(segment.getParent().getMap().getUnitsString());

      UnitLabel unitLabel = new UnitLabel(this.getFontMetrics(VCMap.unitFont));
      unitLabel.setLabel(unitsLabel.toString());
      unitLabel.setLabelYCoord(yCoord);
      unitLabel.setUnitYCoord(yCoord);
      segment.addUnitLabel(unitLabel);
      int stringWidth = unitLabel.getLabelWidth();
      // Recursively determine other strings...
      addTiming("MapNavigator - determineDrawnUnits", System.nanoTime() - begin);
      int x = determineDrawnUnits(n / 2, segment, mid, upper, unitFormat, divisor);
      int y = determineDrawnUnits(n / 2, segment, lower, mid, unitFormat, divisor);

      return Math.max(Math.max(x, y), stringWidth);
    }
  }

  /**
   * <p>Format a {@link DisplaySegment} unit of measurement to be displayed.</p>
   *
   * @param unitValue
   *   unit to be formatted into a {@link String}
   * @param unitFormat
   *   {@link String} containing the format to be used on the number
   * @return
   *   {@link String} of the formatted unit
   */
  public String formatUnitLabel(double unitValue, String unitFormat, double divisor)
  {
    long start = System.nanoTime();
    String label = "";
    String end = "";
    if (unitFormat.contains("0 M"))
    {
      unitValue /= 1000000;
      end = " M";
    }
    if (divisor == 1.0)
      label += (int)unitValue + end;//formatter.format(unitValue);
    else
      label += (double)((int)(unitValue * divisor) / divisor) + end;
    addTiming("MapNavigator - formatUnitLabel", System.nanoTime() - start);
    return label;
  }

  /**
   * <p>This method tries to keep the number of maps on each side of the
   * backbone balanced. For example, if there are two maps in positions
   * to the right of the backbone and the user decides to load a new
   * map. That map will be positioned to the left of the backbone. The only
   * time this method does not try to balance the sides is when loading a map
   * that is the same species as a currently loaded map. In this case the
   * new map will be placed next to the map of the same species but on the
   * opposite side of the backbone. This is done to try to create the
   * maximum amount of connecting lines possible.</p>
   *
   * @param displayMap
   *   The new {@link DisplayMap} that needs a position to be displayed
   *   on the screen.
   */
  private void determinePosition(DisplayMap displayMap)
  {
    logger.debug("Determine where to postion DisplayMap: " + displayMap);
    Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();

    // Check if the same species as another map
    DisplayMap speciesMatch = null;
    for (DisplayMap map : maps)
    {
      if (map.getMap().getSpecies().equals(displayMap.getMap().getSpecies()))
      {
        speciesMatch = map;
        break;
      }
    }

    if (speciesMatch == null)
    {
      // Count the number of maps to the left of the BB
      int numOnLeft = 0;
      for (DisplayMap map : maps)
      {
        if (map == backboneSegment.getParent())
          break;
        else
          numOnLeft++;
      }

      // Determine if the map goes to the left of the BB
      // or to the right of the BB
      if (numOnLeft >= (maps.size() - numOnLeft - 1))
      {
        displayMap.setPosition(maps.get(maps.size() - 1).getPosition());
        for (DisplayMap map : maps)
          if (map != displayMap)
            map.setPosition(map.getPosition() - 1);
      }
      else
      {
        displayMap.setPosition(maps.get(0).getPosition());
        for (DisplayMap map : maps)
          if (map != displayMap)
            map.setPosition(map.getPosition() + 1);
      }
    }
    else
    {
      if (speciesMatch.getPosition() < backboneSegment.getParent().getPosition())
      {
        // Match is to the left
        for (DisplayMap map : maps)
        {
          if (map == speciesMatch)
            break;
          else
            map.setPosition(map.getPosition() - 1);
        }

        displayMap.setPosition(speciesMatch.getPosition() - 1);
      }
      else
      {
        // Match is to the right
        for (DisplayMap map : maps)
        {
          if (map.getPosition() <= speciesMatch.getPosition())
            continue;
          else
            map.setPosition(map.getPosition() + 1);
        }

        displayMap.setPosition(speciesMatch.getPosition() + 1);
      }
    }
  }

  /**
   * <p>Move the {@link DisplayMap}s to the position they would
   * be in if the user had loaded the maps in the same order they had
   * originally loaded them and had not moved the maps to different
   * positions.</p>
   *
   */
  public void restoreMapPositions()
  {
    // 'Copy' displayMaps
    Vector<DisplayMap> oldDisplayMaps = new Vector<DisplayMap>();
    for (DisplayMap map : displayMaps)
      oldDisplayMaps.add(map);

    // Clear displayMaps
    displayMaps.clear();

    // Redetermine positions
    for (DisplayMap map : oldDisplayMaps)
    {
      if (map == backboneSegment.getParent())
        map.setPosition(0);
      else
        determinePosition(map);

      displayMaps.add(map);
    }

    if (!centerOnBackbone())
      repaint();
  }

  /**
   * <p>Center the {@link JViewport} around the backbone when
   * the backbone is loaded. The backbone may not be exactly centered
   * because of the location of the backbone. At the very least the
   * backbone will appear in the viewport after calling this method.</p>
   *
   * @return
   *   true - {@link JViewport} was centered on the backbone
   *   false - {@link JViewport} was NOT centered on the backbone
   */
  public boolean centerOnBackbone()
  {
    if (backboneSegment == null) return false;

    if (getParent().getWidth() < this.getWidth())
    {
      int mapCoord = getDisplayMapXCoord(backboneSegment.getParent());
      int mapWidth = getDisplayMapWidth(backboneSegment.getParent());
      JViewport vp = (JViewport)getParent();
      int x = mapCoord - vp.getWidth() / 2 + mapWidth / 2;

      // Check to make sure we won't go past our bounds
      if (x < 0)
        x = 0;
      if (x + vp.getWidth() > this.getWidth())
        x -= x + vp.getWidth() - this.getWidth();

      // Change viewport position
      vp.setViewPosition(new Point(x, vp.getViewPosition().y));

      repaint();

      return true;
    }

    return false;
  }

  /**
   * <p>Center the {@link JViewport} on a particular piece of {@link Annotation}.</p>
   *
   * @param annotation
   *   {@link Annotation} to center the {@link JViewport} on
   */
  public void centerOnAnnotation(Annotation annotation)
  {
    JViewport vp = (JViewport)getParent();
    DisplaySegment segment = null;

    for (DisplayMap map : displayMaps)
    {
      for (DisplaySegment seg : map.getSegments())
      {
        if (seg.containsFeature(annotation))
        {
          segment = seg;
          break;
        }
      }
      if (segment != null)
        break;
    }

    if (segment == null) return;

    int yCoord = (int)((getAnnotationStartCoord(segment, annotation)
        + getAnnotationStopCoord(segment, annotation)) / 2.0
        - (vp.getBounds().height / 2.0));

    if (yCoord < 0) yCoord = 0;
    if (yCoord + vp.getBounds().height > this.getBounds().height)
      yCoord = this.getBounds().height - vp.getBounds().height;

    int xCoord = getDisplayMapXCoord(segment.getParent());

    if (xCoord < 0)
      xCoord = 0;
    if (xCoord + vp.getBounds().width > this.getWidth())
      xCoord = this.getWidth() - vp.getBounds().width;

    vp.setViewPosition(new Point(xCoord, yCoord));
  }

  /**
   * <p>Returns the backbone {@link DisplaySegment}</p>
   *
   * @return
   *   Backbone {@link DisplaySegment} or null if
   *   backbone is not loaded yet
   */
  public DisplaySegment getBackboneDisplaySegment()
  {
    return backboneSegment;
  }

  /**
   * <p>Get the drawing height of {@link MapNavigator}</p>
   *
   * @return
   *   int value (in pixels) of the {@link MapNavigator}
   */
  public int getDrawingHeight()
  {
    return drawingHeight;
  }

  /**
   * <p>Get the {@link Selection} for this {@link MapNavigator}
   *
   * @return
   *   {@link Selection} of this {@link MapNavigator}
   */
  public Selection getSelection()
  {
    return selection;
  }

  /**
   * <p>Indicate to the MapNavigator that options have been updated.  Changes
   * are displayed immediately.</p>
   * FIXME this should be done with an event system!!  Otherwise changes are
   * not going to be automatic... On second thought, why does this "setTypeShown()"
   * method exist?  This should simply be checked during paintComponent?!
   * Anyway, this should be redesigned.
   */
  public void optionsUpdated()
  {
    // Change displayMap shown types
    for (DisplayMap displayMap : displayMaps)
      for (AnnotationSet set : displayMap.getAllAnnotationSets())
        displayMap.setTypeShown(set.getType(), mainGUI.getOptions().isShown(set.getType()));

    verifySelectedAnnotation();
    updateSize();
    updateStatusBar();
  }

  /**
   * <p>Returns the width (in pixels) of a {@link DisplayMap}.</p>
   *
   * @param displayMap
   *   The {@link Displaymap} to get the width in pixels for.
   * @return
   *   The int value of the width of the {@link DisplayMap}
   */
  public int getDisplayMapWidth(DisplayMap displayMap)
  {
    if (displayMap == null)
      return - 1;

    if (!displayMaps.contains(displayMap))
      return -1;

    if (displayMap.isVisible())
    {
      return (displayMap.getFeatureColumnsWidth(mainGUI.getOptions())
          + mainGUI.getOptions().getIntOption("segmentWidth")
          + mainGUI.getOptions().getIntOption("featureColumnWidth")
          + displayMap.getUnitsColumnWidth());
    }
    else
      return mainGUI.getOptions().getIntOption("hiddenMapWidth");
  }

  /**
   * <p>Returns where the {@link DisplayMap} begins to be drawn in the
   * {@link MapNavigator}.</p>
   *
   * @param displayMap
   *   The {@link DisplayMap} to determine the x-coordinate on the
   *   screen for.
   * @return
   *   The int value of left x-coordinate of where the {@link DisplayMap}
   *   is displayed on the screen.
   */
  public int getDisplayMapXCoord(DisplayMap displayMap)
  {
    if (displayMap == null)
      return - 1;

    if (!displayMaps.contains(displayMap))
      return -1;

    Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();
    int betweenMapWidth = mainGUI.getOptions().getIntOption("betweenMapWidth");

    int horizontalPosition = leftMarginWidth;

    for (DisplayMap map : maps)
    {
      if (map == displayMap)
        break;
      else
        horizontalPosition += getDisplayMapWidth(map) + betweenMapWidth;
    }

    return horizontalPosition;
  }

  /**
   * <p>Get all the {@link DisplayMap} stored in memory in the order
   * the were loaded into memory.</p>
   *
   * @return
   *   {@link Vector} of all the displayed maps in memory, in the
   *   order they were loaded into memory.
   */
  public Vector<DisplayMap> getDisplayMaps()
  {
    return displayMaps;
  }

  /**
   * <p>Get the {@link DisplayMap} currently in memory in order
   * of how they are positioned on the screen.</p>
   *
   * @return
   *   {@link DisplayMap}s in left to right order, based on position.
   */
  @SuppressWarnings("unchecked")
  public Vector<DisplayMap> getDisplayMapsInOrderOfPosition()
  {
    Vector<DisplayMap> maps = (Vector<DisplayMap>)displayMaps.clone();

    // Basic insertion sort
    for (int i = 1; i < maps.size(); i++)
    {
      DisplayMap value = maps.get(i);
      int j = i - 1;
      while (j >= 0 && maps.get(j).getPosition() > value.getPosition())
      {
        maps.remove(j+1);
        maps.add(j+1, maps.get(j));
        j--;
      }
      maps.remove(j+1);
      maps.add(j+1, value);
    }

    return maps;
  }

  /**
   * <p>Get the closest {@link DisplayMap} on the left and
   * right side of the passed in {@link DisplayMap} if there is any.
   * {@link DisplayMap}s that are not visible will be ignored.</p>
   *
   * @param displayMap
   *   {@link DisplayMap} to find the neighbors for
   * @return
   *   A {@link Vector} of {@link DisplayMap}s. There is a maximum of two
   *   and a mininum of zero neighbors.
   */
  public Vector<DisplayMap> getDisplayMapNeighbors(DisplayMap displayMap)
  {
    Vector<DisplayMap> neighbors = new Vector<DisplayMap>();

    // Check for neighbor to the left
    DisplayMap left = getDisplayMapLeftNeighbor(displayMap);
    if (left != null)
      neighbors.add(left);

    // Check for neighbor to the right
    DisplayMap right = getDisplayMapRightNeighbor(displayMap);
    if (right != null)
      neighbors.add(right);

    return neighbors;
  }

  /**
   * <p>Check if two {@link DisplayMap}s are drawn next to one another
   * in the {@link MapNavigator}.</p>
   *
   * @param one
   *   A distinct {@link DisplayMap} to check
   * @param two
   *   A distinct {@link DisplayMap} to check
   * @return
   *   true - the {@link DisplayMap}s are neighbors
   *   false - the {@link DisplayMap}s are NOT neighbors
   */
  public boolean areDisplayMapNeighbors(DisplayMap one, DisplayMap two)
  {
    if (one == null || two == null || one == two)
      return false;

    return getDisplayMapNeighbors(one).contains(two);
  }

  /**
   * <p>Get the closest {@link DisplayMap} located to the left of the
   * {@link DisplayMap} passed in, that is visible.</p>
   *
   * @param displayMap
   *   Find the left neighbor of this {@link DisplayMap}
   * @return
   *   null if there is no visible {@link DisplayMap} to the left
   *   of the {@link DisplayMap} passed in. Otherwise, a {@link DisplayMap}
   *   is returned
   */
  public DisplayMap getDisplayMapLeftNeighbor(DisplayMap displayMap)
  {
    Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();

    // Check for neighbor to the left
    DisplayMap previousMap = null;
    for (int i = 0; i < maps.size(); i++)
    {
      if (maps.get(i) == displayMap)
      {
        if (previousMap != null)
          return previousMap;
      }
      else if (maps.get(i).isVisible())
        previousMap = maps.get(i);
    }
    return null;
  }

  /**
   * <p>Get the closest {@link DisplayMap} located to the right of the
   * {@link DisplayMap} passed in, that is visible.</p>
   *
   * @param displayMap
   *   Find the right neighbor of this {@link DisplayMap}
   * @return
   *   null if there is no visible {@link DisplayMap} to the right
   *   of the {@link DisplayMap} passed in. Otherwise, a {@link DisplayMap}
   *   is returned
   */
  public DisplayMap getDisplayMapRightNeighbor(DisplayMap displayMap)
  {
    Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();

    // Check for neighbor to the right
    DisplayMap previousMap = null;
    for (int i = maps.size() - 1; i >= 0; i--)
    {
      if (maps.get(i) == displayMap)
      {
        if (previousMap != null)
          return previousMap;
      }
      else if (maps.get(i).isVisible())
        previousMap = maps.get(i);
    }
    return null;
  }

  /**
   * <p>Determine the y-pixel coordinate for the top of a segment.</p>
   *
   * @param displaySegment
   *   The {@link DisplaySegment} to calculate for.
   * @return
   *   The y-coordinate to begin drawing the displayed segment. If the
   *   {@link DisplaySegment} is null, a -1 is returned.
   */
  public double getSegmentYCoord(DisplaySegment displaySegment)
  {
    if (displaySegment == null) return -1;
    
    int start = displaySegment.getDrawingStart();
    double startPercent = start / displaySegment.getUnitsPerPercent() / 100;
    if (displaySegment.getSyntenyBlock() != null)
    {
      start = Math.min(displaySegment.getSyntenyBlock().getLeftStart(), displaySegment.getSyntenyBlock().getLeftStop());
      startPercent = start / backboneSegment.getUnitsPerPercent() / 100;
    }
    return headerHeight + (startPercent * drawingHeight);
  }

  /**
   * <p>Get the segment height in pixels</p>
   *
   * @param displaySegment
   *   {@link DisplaySegment} to determine the height for in pixels
   * @return
   *   The int value of the height of a {@link DisplaySegment} in pixels. If
   *   the {@link DisplaySegment} is null, a -1 is returned.
   */
  public double getSegmentHeight(DisplaySegment segment)
  {
    if (segment == null) return -1;

    return (segment.getHeight() / 100) * drawingHeight;
  }

  /**
   * <p>Get the y-coordinate of a piece of {@link Annotation},
   * so the the {@link Annotation} can be drawn in the proper location in the
   * {@link MapNavigator} panel.<p>
   *
   * @param segment
   *   The {@link DisplaySegment} the {@link Annotation} belongs too
   * @param annotation
   *   The {@link Annotation} to determine the y-cooordinate for
   * @return
   *   The int value of the y-coordinate for the {@link Annotation} start
   *   position. If {@link DisplaySegment} or {@link Annotation} equals null
   *   or the {@link Annotation} does not belong to the same chromosome as
   *   the segment a -1 is returned
   */
  private double getAnnotationStartCoord(DisplaySegment segment, Annotation annotation)
  {
    long st = System.nanoTime();
    if (segment == null || annotation == null)
      return -1;

    if (annotation.getChromosome() != segment.getChromosome())
      return -1;

    if (segment.getDrawingStart() > segment.getDrawingStop())
    {
      double actualStart = (((segment.getDrawingStart() - annotation.getStop()) /
          segment.getUnitsPerPercent()) / 100) * drawingHeight + getSegmentYCoord(segment);
      addTiming("MapNavigator - getAnnotationStartCoord - if", System.nanoTime() - st);

      return (actualStart < getSegmentYCoord(segment)) ?
          getSegmentYCoord(segment) : actualStart;
    }
    else
    {
      double actualStart = (((annotation.getStart() - segment.getDrawingStart()) /
          segment.getUnitsPerPercent()) / 100) * drawingHeight + getSegmentYCoord(segment);
      addTiming("MapNavigator - getAnnotationStartCoord - else", System.nanoTime() - st);

      return (actualStart < getSegmentYCoord(segment)) ?
          getSegmentYCoord(segment) : actualStart;
    }
  }

  /**
   * <p>Get the y-coordinate of the stop position for a piece of
   * {@link Annotation} with the help of the {@link DisplaySegment} for
   * reference.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is being displayed with.
   * @param annotation
   *   {@link Annotation} to determine the y-coordinate for it's stop position.
   * @return
   *   The int value of the y-coordinate for the {@link Annotation} stop
   *   position. If segment or annotation equals null or the annotation does
   *   not belong to the same chromosome as the segment a -1 is returned
   */
  private double getAnnotationStopCoord(DisplaySegment segment, Annotation annotation)
  {
    long st = System.nanoTime();
    if (segment == null || annotation == null)
      return -1;

    if (annotation.getChromosome() != segment.getChromosome())
      return -1;

    if (segment.getDrawingStart() > segment.getDrawingStop())
    {
      double actualStop = (((segment.getDrawingStart() - annotation.getStart()) /
          segment.getUnitsPerPercent()) / 100) * drawingHeight + getSegmentYCoord(segment);
      addTiming("MapNavigator - getAnnotationStopCoord - if", System.nanoTime() - st);

      return (actualStop > getSegmentYCoord(segment) + getSegmentHeight(segment)) ?
          getSegmentYCoord(segment) + getSegmentHeight(segment) : actualStop;
    }
    else
    {
      double actualStop = (((annotation.getStop() - segment.getDrawingStart()) /
          segment.getUnitsPerPercent()) / 100) * drawingHeight + getSegmentYCoord(segment);
      addTiming("MapNavigator - getAnnotationStopCoord - else", System.nanoTime() - st);

      return (actualStop > getSegmentYCoord(segment) + getSegmentHeight(segment)) ?
          getSegmentYCoord(segment) + getSegmentHeight(segment) : actualStop;
    }
  }

  /**
   * <p>Since {@link Annotation} will have a different start and stop position,
   * the height will vary when it is being drawn on the screen. This method
   * determines the height of the annotation relative to the
   * {@link DisplaySegment}</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is being displayed with.
   * @param annotation
   *   {@link Annotation} to determine the relative height in pixels for.
   * @return
   *   The int value of the height of the {@Annotation} to the
   *   {@link DisplaySegment}. If segment or annotation equals null or
   *   the annotation does not belong to the same chromosome as the segment
   *    a -1 is returned
   */
  private double getAnnotationHeight(DisplaySegment segment, Annotation annotation)
  {
    if (segment == null || annotation == null)
      return -1;

    if (annotation.getChromosome() != segment.getChromosome())
      return -1;

    return Math.abs((int)(getAnnotationStopCoord(segment, annotation))
        - (int)(getAnnotationStartCoord(segment, annotation)));
  }

  /**
   * <p>Hide the selected {@link DisplayMap}. The {@link StatusBar} and
   * the {@link Annotation} in {@link Selection} is otherAnnotationsRemoved.</p>
   *
   */
  public void hideSelectedMap()
  {
    long begin = System.nanoTime();
    if (selection.getMap() != null)
    {
      // Set DisplayMap if not visible
      selection.getMap().setVisible(false);

      // Add any display map annotation to the hidden annotation vector
      for (DisplaySegment segment : selection.getMap().getSegments())
        for (Annotation annot : segment.getSegmentFeatures())
          if (!hiddenAnnotation.contains(annot))
            hiddenAnnotation.add(annot);

      verifySelectedAnnotation();
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - hideSelectedMap", System.nanoTime() - begin);
  }

  /**
   * <p>Show the selected {@link DisplayMap}. Upon doing so, the
   * selected {@link Annotation} is updated to show sibling {@link Annotation}
   * of any already selected {@link Annotation} as selected.</p>
   *
   */
  public void showSelectedMap()
  {
    long begin = System.nanoTime();
    if (selection.getMap() == null)
      return;

    if (!selection.getMap().isVisible())
    {
      selection.getMap().setVisible(true);

      // Remove annotation from this displaymap that may be hidden
      for (DisplaySegment segment : selection.getMap().getSegments())
        hiddenAnnotation.removeAll(segment.getSegmentFeatures());

      verifySelectedAnnotation();
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - showSelectedMap", System.nanoTime() - begin);
  }

  /**
   * <p>Sets all of the {@link DisplayMap}s in memory to visible. Upon doing
   * so, the selected {@link Annotation} is updated to show sibling
   * {@link Annotation} of any already selected {@link Annotation} as
   * selected.</p>
   *
   */
  public void showAllMaps()
  {
    long begin = System.nanoTime();
    for (DisplayMap displayMap : displayMaps)
      displayMap.setVisible(true);

    hiddenAnnotation.clear();
    verifySelectedAnnotation();
    updateStatusBar();
    updateSize();
    addTiming("MapNavigator - howAllMaps", System.nanoTime() - begin);
  }

  /**
   * <p>Get the {@link AnnotationSets} of the {@link Annotation} the user has chosen to temporarily
   * hide.</p>
   *
   * @return
   *   {@link Vector} of {@link String} containing the {@link AnnotationSet}s of
   *   {@link Annotation} the user has chosen to hide
   */
  public Vector<String> getHiddenTypes()
  {
    return hiddenTypes;
  }

  /**
   * <p>Returns all the {@link DisplayMap}s saved in memory.</p>
   *
   * @return
   *   All the {@link DisplayMap}s saved in memory
   */
  public DisplayMap getSelectedDisplayMap()
  {
    return selection.getMap();
  }

  /**
   * <p>Returns the currently selected {@link DisplaySegment}</p>
   *
   * @return
   *   Currently selected {@link Display Segment} or null is no
   *   segment is currently selected
   */
  public DisplaySegment getSelectedDisplaySegment()
  {
    return selection.getSegment();
  }

  /**
   * <p>Helper method that gets all the {@link SelectionInterval}s in the
   * {@link Selection} for the {@link MapNavigator}.</p>
   *
   * @return
   *   {@link Vector} of {@link SelectionInterval}s. {@link Vector} will be
   *   empty if there are no {@link SelectionInterval}s.
   */
  public Vector<SelectionInterval> getSelectionIntervals()
  {
    return (selection == null) ? new Vector<SelectionInterval>() : selection.getIntervals();
  }

  /**
   * <p>Returns the selected annotation in a {@link Annotation} {@link Vector}.
   * </p>
   *
   * @return
   *   All the selected {@link Annotation}
   */
  public Vector<Annotation> getSelectedAnnotation()
  {
    return selection.getAnnotation();
  }

  /**
   * <p>This method ensures that a feature is not added to the selected
   * annotation multiple times and that all the siblings of the selected
   * annotation is added to the list of selected annotations.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is a part of. If
   *   this is null, the method will automatically find the {@link DisplaySegment}
   *   the {@link Annotation} belongs to.
   * @param annotation
   *   {@link Annotation} that has been chosen for selection.
   * @param inclusive
   *   true - the {@link Annotation} is added to the list of
   *          selected {@link Annotation}
   *   false - the list of selected {@link Annotation} is cleared
   *           and then the {@link Annotation} is added to the
   *           list of selected
   * @param showChange
   *   true - Update StatusBar and repaint {@link MapNavigator}
   *   false - Just add {@link Annotation} to selected {@link Annotation}
   */
  public void addSelectedAnnotation(DisplaySegment segment, Annotation annotation, boolean inclusive, boolean showChange)
  {
    long begin = System.nanoTime();
    if (annotation == null)
      return;

    if (!inclusive)
    {
      selection.clearAnnotation();
      selection.clearIntervals();
    }

    // Add Annotation and the annotation's (visible) siblings to
    // selected annotation
    if (selection.contains(annotation))
    {
      selection.addAnnotation(selection.getSegment(annotation), annotation);
    }
    else if (isAnnotationVisible(annotation))
    {
      if (segment == null)
      {
        for (DisplayMap map : displayMaps)
        {
          for (DisplaySegment seg : map.getSegments())
          {
            if (seg.containsFeature(annotation))
            {
              segment = seg;
              break;
            }
          }
          if (segment != null)
            break;
        }
      }

      selection.addAnnotation(segment, annotation);
    }

    if (showChange)
    {
      updateStatusBar();
    }
    addTiming("MapNavigator - addSelectedAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>The removes a specific {@link Annotation} from the list
   * of selected {@link Annotation} and all of the {@link Annotation}'s
   * siblings in the selected list.</p>
   *
   * @param annotation
   *   {@link Annotation} to remove from the selected list, as well
   *   as it's siblings.
   * @param showChange
   *   true - Update StatusBar and repaint {@link MapNavigator}
   *   false - Just add {@link Annotation} to selected {@link Annotation}
   */
  public void removeSelectedAnnotation(Annotation annotation, boolean showChange)
  {
    long begin = System.nanoTime();
    if (annotation == null)
      return;

    // Remove annotation
    if (selection.contains(annotation))
    {
      selection.removeAnnotation(annotation);

      for (Annotation sibling : annotation.getSiblings())
        selection.removeAnnotation(sibling);
    }

    if (showChange)
    {
      updateStatusBar();
      repaint();
    }
    addTiming("MapNavigator - removeSelectedAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>Set the {@link Annotation} that is selected in {@link MapNavigator}.</p>
   *
   * @param annotation
   *   {@link Annotation}s to show as selected visually and store as selected
   *   in memory
   */
  public void setSelectedAnnotation(Vector<Annotation> selectedAnnotation)
  {
    long begin = System.nanoTime();
    clearSelection();

    for (Annotation annotation : selectedAnnotation)
      addSelectedAnnotation(null, annotation, true, false);

    updateStatusBar();
    repaint();
    addTiming("MapNavigator - setSelectedAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>Checks to make sure the list of selected {@link Annotation} list is
   * still valid by removing any annotation that is no longer visible.</p>
   *
   */
  private void verifySelectedAnnotation()
  {
    long begin = System.nanoTime();
    for (Annotation annot : selection.getAnnotation())
    {
      if (!isAnnotationVisible(annot))
      {
        selection.removeAnnotation(annot);
      }
    }
    addTiming("MapNavigator - verifySelectedAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>Reinitialize all selection variables so that no annotation is
   * stored as selected in memory and no annotation is shown visually
   * as selected.</p>
   *
   */
  public void clearSelection()
  {
    selection.clear();
    updateStatusBar();
    repaint();
  }

  /**
   * <p>Hide all the {@link Annotation} in all the {@link SelectionInterval}s.</p>
   */
  public void hideSelectedInterval()
  {
    long begin = System.nanoTime();
    for (SelectionInterval mapInterval : selection.getIntervals())
    {
      // Remove mapInterval annotation from the hidden annotation
      hiddenAnnotation.addAll(mapInterval.getSegment().getSegmentFeatures(mapInterval.getStart(), mapInterval.getStop()));
    }

    if (selection.getIntervals().size() > 0)
    {
      verifySelectedAnnotation();
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - hideSelectedInterval", System.nanoTime() - begin);
  }

  /**
   * <p>Hide all of the {@link Annotation}s that are NOT in the selection.</p>
   */
  public void hideOtherFeatures()
  {
    long begin = System.nanoTime();
    otherAnnotationsRemoved = true;
    Vector<Annotation> selectedAnnotations = selection.getAnnotation();

    //Figure out the Annotation Sets that are NOT selected to hide them properly
    Vector<AnnotationSet> annotationSets = new Vector<AnnotationSet>();
    for (DisplaySegment segment : segments)
    {
      Vector<AnnotationSet> segmentAnnotationSets = getAnnotationSets(segment.getSegmentFeatures());
      for (AnnotationSet set : segmentAnnotationSets)
      {
        if (!annotationSets.contains(set))
          annotationSets.add(set);
      }
    }
    Vector<AnnotationSet> selectionAnnotationSets = getAnnotationSets(selectedAnnotations);
    for (AnnotationSet set : annotationSets)
      if (!selectionAnnotationSets.contains(set))
        hiddenTypes.add(set.getType());

    //Hide the annotations that are not selected
    for (DisplaySegment segment : segments)
    {
      Vector<Annotation> annotations = segment.getSegmentFeatures();
      for (Annotation annot : annotations)
      {
        if (selectedAnnotations.contains(annot))
          continue;
        hiddenAnnotation.add(annot);
      }
    }
    if (selection.getAnnotation().size() > 0)
    {
      verifySelectedAnnotation();
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - hideOtherFeatures", System.nanoTime() - begin);
  }

  /**
   * <p>{@link Annotation} is hidden based on what specific {@link AnnotationSet} it is. If
   * {@link Annotation} is hidden, it is not visible on the screen, not
   * selectable and not able to be found with the {@link SearchDialog}.</p>
   *
   * @param set
   *   {@link String} of the {@link Annotation} {@link AnnotationSet} to hide
   */
  public void hideAnnotationSet(String typeString)
  {
    long begin = System.nanoTime();
    if (!hiddenTypes.contains(typeString))
      hiddenTypes.add(typeString);
    else
      return;

    for (DisplayMap map : displayMaps)
    {
      map.setTypeShown(typeString, false);
      for(AnnotationSet set : map.getAllAnnotationSets())
        if(typeString.equals(set.getType()))
        {
          for (DisplaySegment segment : map.getSegments())
          {
            Vector<Annotation> segAnnots = segment.getSegmentFeatures(set);
            hiddenAnnotation.addAll(segAnnots);
            selection.removeAllAnnotation(segAnnots);
          }
        }
    }

    updateStatusBar();
    updateSize();
    addTiming("MapNavigator - hideAnnotationSet", System.nanoTime() - begin);
  }

  /**
   * <p>Hide all the {@link Annotation} marked as selected.</p>
   *
   */
  public void hideSelectedAnnotation()
  {
    long begin = System.nanoTime();
    if (selection.getAnnotation().size() > 0)
    {
      // Add selected to hidden
      hiddenAnnotation.addAll(selection.getAnnotation());

      // Clear/Close possibly affected data
      selection.clearAnnotation();

      // Update
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - hideSelectedAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>Show all the {@link Annotation}, if any, that may be
   * marked at hidden and is in the interval drawn on the backbone
   * map.</p>
   *
   */
  public void showSelectedInterval()
  {
    long begin = System.nanoTime();
    for (SelectionInterval mapInterval : selection.getIntervals())
    {
      HashSet<Annotation> nowShowAnnot = new HashSet<Annotation>();

      // Determine the annotation in the mapInterval
      for (Annotation annot : mapInterval.getSegment().getSegmentFeatures(mapInterval.getStart(), mapInterval.getStop()))
      {
        if (!hiddenTypes.contains(annot.getAnnotationSet()))
        {
          nowShowAnnot.add(annot);
        }
      }

      // Remove mapInterval annotation from the hidden annotation
      hiddenAnnotation.removeAll(nowShowAnnot);
    }

    if (selection.getIntervals().size() > 0)
    {
      updateStatusBar();
      updateSize();
    }
    addTiming("MapNavigator - showSelectedInterval", System.nanoTime() - begin);
  }

  /**
   * Gets the {@link AnnotationSet}s of {@link Annotation}s that are currently hidden in the
   * set of hidden {@link Annotation}s.
   * @return
   *    A Vector of Strings containing the {@link AnnotationSet}s of the {@link Annotation}s
   *    that are hidden
   */
  public Vector<AnnotationSet> hiddenAnnotationSets()
  {
    Vector<AnnotationSet> hiddenAnnotationTypes = new Vector<AnnotationSet>();
    for (Annotation annot : hiddenAnnotation)
    {
      if (!hiddenAnnotationTypes.contains(annot.getAnnotationSet()))
        hiddenAnnotationTypes.add(annot.getAnnotationSet());
    }
    return hiddenAnnotationTypes;
  }

  /**
   * <p> Helper method to get the annotation {@link AnnotationSet}s of a given vector of
   * {@link Annotation}s so that the opposite {@link AnnotationSet}s can be hidden.
   * @param annotations
   *    The set of annotations to look in to find the {@link AnnotationSet}s of the
   *    {@link Annotation}s in the set
   * @return
   *    A {@link Vector} of Strings containing the {@link AnnotationSet} in the {@link Vector}
   *    of {@link Annotation}s
   */
  public Vector<AnnotationSet> getAnnotationSets(Vector<Annotation> annotations)
  {
    Vector<AnnotationSet> annotationSets = new Vector<AnnotationSet>();
    for (Annotation annot : annotations)
    {
      if (!annotationSets.contains(annot.getAnnotationSet()))
        annotationSets.add(annot.getAnnotationSet());
    }
    return annotationSets;
  }

  /**
   * <p>{@link Annotation} is shown based on what specific {@link AnnotationSet} it is.
   * It is important to note that this does not affect the preferences
   * stored for a specific {@link AnnotationSet}. So if a specific {@link AnnotationSet} of
   * the {@link Annotation} is selected to not be visible in the {@link PreferencesDialog} and
   * selected to be visible by this method, the {@link Annotation} still
   * will not be visible.</p>
   *
   * @param string
   *   {@link AnnotationSet} of the {@link Annotation} {@link AnnotationSet} to make visible
   */
  public void showAnnotationSet(String typeString)
  {
    if (hiddenTypes.contains(typeString) || hiddenAnnotationSets().contains(typeString))
    {
      hiddenTypes.remove(typeString);

      Vector<Annotation> nowShowAnnot = new Vector<Annotation>();

      for (DisplayMap map : displayMaps)
      {
        map.setTypeShown(typeString, true);
        for(AnnotationSet set : map.getAllAnnotationSets())
          if(typeString.equals(set.getType()))
          {
            for (DisplaySegment segment : map.getSegments())
            {
              Chromosome chrom = segment.getChromosome();
              int segStart = segment.getDrawingStart();
              int segStop = segment.getDrawingStop();
              nowShowAnnot.addAll(chrom.getAnnotation(set, segStart, segStop));
            }
          }
      }

      hiddenAnnotation.removeAll(nowShowAnnot);

      updateSize();
    }
  }

  /**
   * <p>Mark all the {@link Annotation} currently hidden as viewable.</p>
   *
   */
  public void showAllAnnotation()
  {
    otherAnnotationsRemoved = false;
    if (hiddenAnnotation.size() > 0)
    {
      hiddenAnnotation.clear();

      hiddenTypes.clear();

      for (DisplayMap displayMap : displayMaps)
      {
        Vector<AnnotationSet> hidSets = new Vector<AnnotationSet>();
        for (AnnotationSet set : displayMap.getHiddenSets())
          hidSets.add(set);

        for (AnnotationSet set : hidSets)
          displayMap.setTypeShown(set.getType(), true);
      }

      updateSize();
    }
  }

  /**
   * <p>Finds hidden annotation and shows it in the mainGUI</p>
   *
   * @param species
   *  The species containing the annotation
   * @param mapType
   *  The map type containing the annotation
   * @param mapVersion
   *  The version of the map containing the annotation
   * @param chromosome
   *  The chromosome the annotation is found in
   * @param name
   *  The name of the annotation
   */
  public void showAnnotationByName(String species, String mapType, String mapVersion, String chromosome, String name)
  {
    for (DisplayMap map : displayMaps)
    {
      if (map.getMap().getSpecies().equals(species) && map.getMap().getVersion().equals(mapVersion) )
      {
        for (DisplaySegment segment : map.getSegments())
        {
          if ( segment.getChromosome().getName().equals("chr"+chromosome) )
          {
            for (Annotation annotation : segment.getSegmentFeatures())
            {
              if ( name.equals(annotation.getName() ) && !isAnnotationVisible(annotation) )
              {
                hiddenAnnotation.remove(annotation);
                updateSize();
              }
            }
          }
        }
      }
    }

  }

  /**
   * <p>Update the size of the {@link MapNavigator} when an action may have
   * caused the bounds to change.</p>
   *
   */
  public void updateSize()
  {
    setZoomLevel(zoomBarPosition);
  }

  /**
   * <p>Make sure that the {@link StatusBar} is displaying the newest information
   * from the {@link Selection}.</p>
   *
   */
  private void updateStatusBar()
  {
    if (mainGUI.getStatusBar() != null)
      mainGUI.getStatusBar().updateStatusBar();
  }

  /**
   * <p>Helper method that determines what the width of the {@link MapNavigator} should be.</p>
   *
   * @return
   *   int value of the valid width of the {@link MapNavigator}, in pixels
   */
  private int determineWidth()
  {
    JViewport vp = (JViewport)getParent();

    int paneWidth = this.getWidth();
    int horizontalSpace = 0;

    for (DisplayMap displayMap : displayMaps)
    {
      horizontalSpace += getDisplayMapWidth(displayMap);

      if (displayMap != displayMaps.lastElement())
        horizontalSpace += mainGUI.getOptions().getIntOption("betweenMapWidth");
    }

    horizontalSpace += 2 * mainGUI.getOptions().getIntOption("leftMarginWidth");

    if (horizontalSpace > vp.getBounds().width)
      paneWidth = horizontalSpace;
    else
      paneWidth = vp.getBounds().width;

    return paneWidth;
  }

  /*
   * Overridden method to ensure {@link MapHeaders} is the same width as
   * the {@link MapNavigator}.
   *
   * (non-Javadoc)
   * @see javax.swing.JComponent#setPreferredSize(java.awt.Dimension)
   */
  public void setPreferredSize(Dimension preferredSize)
  {
    super.setPreferredSize(preferredSize);

    //
    // Resize map header pane to match up with new viewport size
    //
    if (mapHeaders != null)
    {
      Dimension size = new Dimension(preferredSize.width, mapHeaders.getPreferredSize().height);
      mapHeaders.setPreferredSize(size);
      mapHeaders.revalidate();
    }
  }

  /**
   * <p>Update the drawing height and width as the user zooms in
   * and out. The method also changes {@link MapNavigator} preferred size
   * and keeps the viewport centered vertically around the pre-zoomed
   * centered location.</p>
   *
   * @param zoomBarPosition
   *   The value of the zoom bar.
   */
  protected void setZoomLevel(double zoomBarPosition)
  {
    //
    // Set new zoomlevel
    //
    this.zoomBarPosition = zoomBarPosition;
    double prevZoomLevel = zoomLevel;
    zoomLevel = Math.pow(1.5, zoomBarPosition);

    // Close overlap box
    if (!overlapBoxes.isEmpty())
    {
      for (OverlapBox overlapBox : overlapBoxes)
      {
        overlapBox.close();
        overlapBox.resizeMapNavigator();
      }

      overlapBoxes.clear();
    }


    // Get viewport height
    JViewport vp = (JViewport)getParent();
    int viewportHeight = vp.getBounds().height;

    //
    // Declare and initialize viewport centering variables
    //
    int xCoord = 0;
    double bbPercent = 0;
    DisplaySegment focusSegment = null;

    //
    // If the mapInterval is not visible and no annotation is selected
    // and we zoom in, just keep centered on the center most point or
    // center where the user has double clicked white space
    //
    SelectionInterval mapInterval = null;
    if (selection.getIntervals().size() > 0)
      mapInterval = selection.getIntervals().lastElement();
    if (backboneSegment != null)
    {
      // If we are zooming because a double click we change the viewport
      // differently than if the zoombar was used
      if (doubleClickZoomPos != null)
      {
        focusSegment = backboneSegment;

        bbPercent = (doubleClickZoomPos.y - getSegmentYCoord(backboneSegment))
        / (getSegmentHeight(backboneSegment));
        xCoord = doubleClickZoomPos.x - vp.getBounds().width / 2;

        doubleClickZoomPos = null;
      }
      else if (mapInterval == null && selection.getAnnotation().size() == 0)
      {
        double middleYCoord = (this.getY() * -1) + (double)viewportHeight / (double)2;

        bbPercent = (middleYCoord - getSegmentYCoord(backboneSegment))
        / (getSegmentHeight(backboneSegment));
        focusSegment = backboneSegment;
        xCoord = vp.getViewPosition().x;
      }
    }

    //
    // Ensure Height does not get too small
    //
    if (headerHeight + mainGUI.getOptions().getIntOption("footerHeight") + 1 > viewportHeight)
      viewportHeight = headerHeight + mainGUI.getOptions().getIntOption("footerHeight") + 1;
    //
    // Calculate drawing height
    //
    // The drawingHeight must be calculated before prepareDisplayMaps() can
    // be called.  prepareDisplayMaps() needs the new drawingHeight so that
    // it can correctly used all the vertical space available to do the
    // feature groupings.
    drawingHeight = (int)(viewportHeight * zoomLevel) - headerHeight - mainGUI.getOptions().getIntOption("footerHeight");
    drawingWidth = vp.getBounds().width;
    //
    // Reprepare maps to be displayed
    //
    // This method will do all the calculations that will determine how
    // each DisplayMap is displayed.  It will determine all the feature
    // groupings and where all the labels for these groups will be positioned.
    // It is important that this method is called after the height is calculated
    // above so that the groupings can be calculated correctly.
    //
    prepareDisplayMaps();

    //
    // Determine how much of space we are taking up on the x-axis
    // Don't want to show the horizontal scrollbar for no reason
    //
    // The width for the MapNavigator needs to be determined after the
    // DisplayMaps have been prepared.  This method uses the width of each
    // DisplayMap to calculate the total width of the MapNavigator and
    // therefore needs to be called after the maps are prepared for display.
    //
    int paneWidth = determineWidth();

    //
    // Change size of MapNavigator
    //
    // This finally updates the size of the MapNavigator with the new dimensions
    // that were calculated above.
    //
    setPreferredSize(new Dimension(paneWidth,
        headerHeight + mainGUI.getOptions().getIntOption("footerHeight") + drawingHeight));

    //
    // Validate changes
    //
    invalidate();
    vp.validate();

    //
    // Center viewport according to whether the the mapInterval is visible,
    // any annotation is selected or the user is just zooming in/out
    //
    if (backboneSegment != null)
    {
      int yCoord = -1;
      if (focusSegment != null)
      {
        //no annotation selected
        yCoord = (int)((bbPercent * getSegmentHeight(focusSegment))
            + getSegmentYCoord(focusSegment));
      }
      else if (mapInterval != null && selection.getAnnotation().size() != 1)
      {
        //multiple annotations selected
        yCoord = (int) ((maxFeatureSelected + minFeatureSelected) / 2.0 / prevZoomLevel * zoomLevel);
        xCoord = vp.getViewPosition().x;
      }
      else if (selection.getAnnotation().size() > 0)
      {
        yCoord = (int) ((maxFeatureSelected + minFeatureSelected) / 2.0 / prevZoomLevel * zoomLevel);
        xCoord = vp.getViewPosition().x;
      }

      if (yCoord > -1)
      {
        yCoord -= viewportHeight / 2;

        // Check make sure we are not outside of our bounds
        if (xCoord < 0)
          xCoord = 0;
        if (xCoord + vp.getBounds().width > this.getWidth())
          xCoord = this.getWidth() - vp.getBounds().width;

        if (yCoord < 0) yCoord = 0;
        if (yCoord + viewportHeight > drawingHeight + mainGUI.getOptions().getIntOption("footerHeight") + headerHeight)
          yCoord = drawingHeight + mainGUI.getOptions().getIntOption("footerHeight") + headerHeight - viewportHeight;

        vp.setViewPosition(new Point(xCoord, yCoord));
      }
    }

    repaint();
  }

  /**
   * <p>This method is used for the {@link MapNavigator} and the
   * {@link MapHeaders} class to communicate. This method allows the user
   * to click on a {@link DisplayMap} title to reposition {@link DisplayMap}s
   * an order they prefer. The method also does the calculations to actually
   * set the positions of the {@link DisplayMap}s to a correct position as a
   * {@link DisplayMap} is dragged to a new position.</p>
   *
   * @param titleDragPoint
   *   The x-coordinate of where the mouse currently is in the
   *   {@link MapHeaders} pane. If a {@link DisplayMap} title is not being
   *   dragged, the this value is null.
   */
  protected void setTitleDragPoint(Integer titleDragPoint)
  {
    if (titleDragPoint != null && this.titleDragPoint == null)
    {
      //
      // Select map user clicked on, if a map title was clicked on
      //
      for (DisplayMap displayMap : displayMaps)
      {
        int startBound = getDisplayMapXCoord(displayMap);
        int stopBound = startBound + getDisplayMapWidth(displayMap);

        if (startBound <= titleDragPoint && titleDragPoint <= stopBound)
        {
          selection.setMap(displayMap);
          updateStatusBar();
          Tutorial.updatePage("selectMap");

          repaint(titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2, 0, mainGUI.getOptions().getIntOption("segmentWidth") + 2, drawingHeight + headerHeight);
          break;
        }
      }
    }
    else if (titleDragPoint != null)
    {
      Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();

      int dragPoint = titleDragPoint.intValue();

      int horizontalPosition = leftMarginWidth;

      if (maps.get(0).isVisible())
        horizontalPosition += maps.get(0).getFeatureColumnsWidth(mainGUI.getOptions())+ mainGUI.getOptions().getIntOption("featureColumnWidth");

      if (dragPoint <= horizontalPosition)
      {
        // Dragged too far left
        selection.getMap().setPosition(maps.get(0).getPosition());

        for (DisplayMap map : maps)
        {
          if (map == selection.getMap())
            break;
          else
            map.setPosition(map.getPosition() + 1);
        }

        if (selection.getMap() == maps.get(0))
        {
          int leftBound = -1;
          int rightBound = -1;

          if (titleDragPoint.intValue() < this.titleDragPoint.intValue())
          {
            leftBound = titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
            rightBound = this.titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
          }
          else
          {
            leftBound = this.titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
            rightBound = titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
          }

          repaint(leftBound - 2, 0, rightBound - leftBound + 4, drawingHeight + headerHeight);
        }
        else
        {
          repaint();
        }
      }
      else
      {
        boolean painted = false; // use to be sure we don't repaint to many times

        for (int i = 0; i < maps.size()-1; i++)
        {
          DisplayMap map = maps.get(i);

          int startBound = getDisplayMapXCoord(map);
          int stopBound = startBound;

          //
          // Set boundries to see where map was moved
          //
          if (map.isVisible() && maps.get(i+1).isVisible())
          {
            startBound += map.getFeatureColumnsWidth(mainGUI.getOptions()) + mainGUI.getOptions().getIntOption("segmentWidth");
            stopBound = getDisplayMapXCoord(maps.get(i+1)) + maps.get(i+1).getFeatureColumnsWidth(mainGUI.getOptions());
          }
          else if (map.isVisible() && !maps.get(i+1).isVisible())
          {
            startBound += map.getFeatureColumnsWidth(mainGUI.getOptions()) + mainGUI.getOptions().getIntOption("segmentWidth");
            stopBound = getDisplayMapXCoord(maps.get(i+1)) + mainGUI.getOptions().getIntOption("hiddenMapWidth");
          }
          else if (!map.isVisible() && maps.get(i+1).isVisible())
          {
            startBound += mainGUI.getOptions().getIntOption("hiddenMapWidth");
            stopBound = getDisplayMapXCoord(maps.get(i+1)) + maps.get(i+1).getFeatureColumnsWidth(mainGUI.getOptions());
          }
          else
          {
            startBound += mainGUI.getOptions().getIntOption("featureColumnWidth");
            stopBound = getDisplayMapXCoord(maps.get(i+1)) + mainGUI.getOptions().getIntOption("featureColumnWidth");
          }

          if (startBound <= dragPoint && dragPoint <= stopBound )
          {
            //
            // Shift maps if within boundries
            //
            if (selection.getMap().getPosition() < map.getPosition())
            {
              selection.getMap().setPosition(map.getPosition());
              for (int j = i; j >= 0; j--)
              {
                if (maps.get(j) == selection.getMap())
                  break;
                else
                  maps.get(j).setPosition(maps.get(j).getPosition() - 1);
              }
            }
            else if (selection.getMap().getPosition() > map.getPosition())
            {
              selection.getMap().setPosition(maps.get(i+1).getPosition());
              for (int j = i + 1; j < maps.size(); j++)
              {
                if (maps.get(j) == selection.getMap())
                  break;
                else
                  maps.get(j).setPosition(maps.get(j).getPosition() + 1);
              }
            }

            painted = true;

            if (selection.getMap() == map)
            {
              int leftBound = -1;
              int rightBound = -1;

              if (titleDragPoint.intValue() < this.titleDragPoint.intValue())
              {
                leftBound = titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
                rightBound = this.titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
              }
              else
              {
                leftBound = this.titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
                rightBound = titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
              }

              repaint(leftBound - 2, 0, rightBound - leftBound + 4, drawingHeight + headerHeight);
            }
            else
            {
              repaint();
            }

            break;
          }
        }

        //
        // Check if the map was dragged to the "far" right
        //
        DisplayMap lastMap = maps.get(maps.size()-1);
        int stopBound = getDisplayMapXCoord(lastMap);

        if (lastMap.isVisible())
          stopBound += lastMap.getFeatureColumnsWidth(mainGUI.getOptions()) + mainGUI.getOptions().getIntOption("segmentWidth");
        else
          stopBound += mainGUI.getOptions().getIntOption("hiddenMapWidth");

        if (stopBound <= dragPoint)
        {
          selection.getMap().setPosition(maps.get(maps.size() - 1).getPosition());

          for (int i = maps.size() - 1; i >= 0; i--)
          {
            if (maps.get(i) == selection.getMap())
              break;
            else
              maps.get(i).setPosition(maps.get(i).getPosition() - 1);
          }

          painted = true;

          if (selection.getMap() == maps.get(maps.size() - 1))
          {
            int leftBound = -1;
            int rightBound = -1;

            if (titleDragPoint.intValue() < this.titleDragPoint.intValue())
            {
              leftBound = titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
              rightBound = this.titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
            }
            else
            {
              leftBound = this.titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
              rightBound = titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;
            }

            repaint(leftBound - 2, 0, rightBound - leftBound + 4, drawingHeight + headerHeight);
          }
          else
          {
            repaint();
          }
        }

        if (!painted)
        {
          repaint(getDisplayMapXCoord(selection.getMap()), 0, getDisplayMapWidth(selection.getMap()), drawingHeight + headerHeight);
        }
      }
    }
    else if (this.titleDragPoint != null)
    {
      int leftBound = this.titleDragPoint.intValue() - mainGUI.getOptions().getIntOption("segmentWidth") / 2;
      int rightBound = this.titleDragPoint.intValue() + mainGUI.getOptions().getIntOption("segmentWidth") / 2;

      repaint(leftBound - 2, 0, rightBound - leftBound + 4, drawingHeight + headerHeight);
    }
    else
    {
      repaint();
    }

    this.titleDragPoint = titleDragPoint;
  }

  /**
   * <p>Creates an {@link BufferedImage} of the {@link MapNavigator}. An {@link AffineTransform}
   * is used to capture the correct {@link Rectangle} of the {@link MapNavigator} panel.</p>
   * @param rectangle
   *    Defines the {@link Rectangle} of the {@link MapNavigator} panel
   *    that will be captured as an {@link BufferedImage}.
   * @return
   *    A {@link BufferedImage} of {@link MapNavigator}.
   */
  public BufferedImage createImage(Rectangle rect, int desiredWidth)
  {
    double scale = (double) desiredWidth / rect.width;
    BufferedImage image = new BufferedImage(desiredWidth , (int) Math.ceil(rect.height * scale), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = image.createGraphics();

    // Transform graphics using rectangle coordinates and scale
    AffineTransform transform = g2d.getTransform();
    transform.scale(scale, scale);
    transform.translate(-1 * rect.x, -1 * rect.y);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.setTransform(transform);

    paint(g2d);
    g2d.dispose();

    return image;
  }
  /**
   * Calculates the zoom value that should be used based on the backbone selection
   * @return
   *    integer that is the zoom value that should be used for setting the zoomBar
   */
  public int calcZoomValue()
  {
    double zoomValue = Math.log(1/selectionPercentSize)/Math.log(1.5);
    if(zoomValue > 100 || zoomValue < 0)
      zoomValue = 0;
    zoomValue = zoomValue > 20 ? 20 : zoomValue;
    return (int) Math.floor(zoomValue);
  }
  /*
   * (non-Javadoc)
   * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
   */
  protected void paintComponent(Graphics g)
  {
    long totalStart = System.nanoTime();
    super.paintComponent(g);
    try
    {
      if (backboneSegment != null)
      {
        if (selection.getIntervals().size()==0)
        {
          minFeatureSelected = maxFeatureSelected = 0;
        }
        else
        {
          minFeatureSelected = selection.getIntervals().get(0).getRectangle(mainGUI.getOptions()).y;
          maxFeatureSelected = selection.getIntervals().get(0).getRectangle(mainGUI.getOptions()).height + minFeatureSelected;
        }
        selectionPercentSize = ((double)(maxFeatureSelected - minFeatureSelected)) / (double)getDrawingHeight();
      }
      // Safe to assume we are using Java > 1.2
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
          RenderingHints.VALUE_FRACTIONALMETRICS_ON);

      // Fill with a white background
      Rectangle clip = g2d.getClipBounds();
      g2d.setColor(mainGUI.getOptions().getColor("background"));
      if (isOpaque()) g2d.fillRect(clip.x, clip.y, clip.width, clip.height);

      // Use a lock to avoid concurrent modification when loading maps and annotation
      if (!lock.tryLock()) return;

      // Font metrics
      g2d.setFont(VCMap.labelFont);
      FontMetrics metrics = g2d.getFontMetrics();

      // Determine label string height and descent
      int labelHeight = metrics.getHeight();
      int labelAscent = metrics.getAscent();

      // Get options
      int betweenMapWidth = mainGUI.getOptions().getIntOption("betweenMapWidth");
      int featureColumnWidth = mainGUI.getOptions().getIntOption("featureColumnWidth");
      int segmentWidth = mainGUI.getOptions().getIntOption("segmentWidth");
      int hiddenMapWidth = mainGUI.getOptions().getIntOption("hiddenMapWidth");
      int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");
      boolean nonAdjConnections = mainGUI.getOptions().isShown("nonAdjConnections");
      boolean adjConnections = mainGUI.getOptions().isShown("adjConnections");
      int featureDisplayType = mainGUI.getOptions().getIntOption("featureDisplayType");
      int featureLabelColumnWidth = mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
      int selectionBoxStroke = mainGUI.getOptions().getIntOption("selectionBoxStroke");
      int unitsLineStroke = mainGUI.getOptions().getIntOption("unitsLineStroke");
      int incForChromLabel = mainGUI.getOptions().getIntOption("incForChromLabel");
      Point mousePos = getMousePosition();

      // NOTE All the display maps must be passed through prepareDisplayMap()
      // method before changes in preferences, hidden/shown annotations and
      // other changes will be displayed

      // Get maps sorted by position
      Vector<DisplayMap> maps = getDisplayMapsInOrderOfPosition();
      // Check number of visible maps
      numMapsVisible = 0;
      for (DisplayMap displayMap : displayMaps)
        if (displayMap.isVisible())
          numMapsVisible++;

      //
      // Determine leftMarginWidth
      //
      long start = System.nanoTime();
      if (maps.size() > 0)
      {
        int totalWidthMaps = 0;
        for (DisplayMap displayMap : maps)
          totalWidthMaps += getDisplayMapWidth(displayMap) + betweenMapWidth;

        if (totalWidthMaps != 0)
          totalWidthMaps -= betweenMapWidth;

        if (totalWidthMaps < getParent().getWidth())
        {
          leftMarginWidth = (getParent().getWidth() - totalWidthMaps) / 2;

          if (leftMarginWidth < mainGUI.getOptions().getIntOption("leftMarginWidth"))
            leftMarginWidth = mainGUI.getOptions().getIntOption("leftMarginWidth");
        }
        else
        {
          leftMarginWidth = mainGUI.getOptions().getIntOption("leftMarginWidth");
        }

        for (OverlapBox overlapBox : overlapBoxes)
        {
          if (overlapBox.isOpen())
            if (overlapBox.getX() < 0)
              leftMarginWidth += Math.abs(overlapBox.getX());
        }
      }
      long timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Determine Left Gap", timing);
      //logger.debug("Determined left gap in: " + (System.nanoTime() - start) + "ms");
      //
      // Draw connecting lines for selected Segment
      //
      start = System.nanoTime();
      if ((selection.getSegments().size() != 0) && (adjConnections || nonAdjConnections)
          && numMapsVisible > 1)
      {
        HashSet<String> drawnLines = new HashSet<String>();

        for (DisplaySegment selectedSegment : selection.getSegments())
        {
          g2d.setColor(mainGUI.getOptions().getColor("connection"));

          Vector<DisplayMap> neighbors = getDisplayMapNeighbors(selectedSegment.getParent());

          for (Annotation annotation : selectedSegment.getSegmentFeatures())
          {
            if (!isAnnotationVisible(annotation))
              continue;

            for (Annotation sibling : annotation.getSiblings())
            {
              if (!hiddenAnnotation.contains(sibling) && mainGUI.getOptions().isShown(sibling.getAnnotationSet().getType()))
              {
                DisplaySegment siblingSegment = null;

                for (DisplayMap map : displayMaps)
                {
                  for (DisplaySegment segment : map.getSegments())
                  {
                    if (segment.containsFeature(sibling))
                    {
                      siblingSegment = segment;
                      break;
                    }
                  }

                  if (siblingSegment != null)
                    break;
                }

                if (!displayFeatureHighlight() || (siblingSegment != null && siblingSegment != selectedSegment
                    && siblingSegment.getParent().isVisible()
                    && ((adjConnections && neighbors.contains(siblingSegment.getParent()))
                        || (nonAdjConnections && !neighbors.contains(siblingSegment.getParent())))))
                {
                  // Draw connecting lines based on where the maps are positioned
                  DisplaySegment leftSeg = null;
                  Annotation leftAnnot = null;
                  DisplaySegment rightSeg = null;
                  Annotation rightAnnot = null;

                  // Determine what is on the left
                  if (selectedSegment.getParent().getPosition() <
                      siblingSegment.getParent().getPosition())
                  {
                    // selected on left
                    leftSeg = selectedSegment;
                    leftAnnot = annotation;
                    rightSeg = siblingSegment;
                    rightAnnot = sibling;
                  }
                  else
                  {
                    // sibling on left
                    leftSeg = siblingSegment;
                    leftAnnot = sibling;
                    rightSeg = selectedSegment;
                    rightAnnot = annotation;
                  }

                  // Draw line to middle
                  int xLeft = getDisplayMapXCoord(leftSeg.getParent())
                  + leftSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions())
                  + segmentWidth + 2;
                  int yLeft = (int)((getAnnotationStartCoord(leftSeg, leftAnnot)
                      + getAnnotationStopCoord(leftSeg, leftAnnot)) / 2.0);
                  int xRight = getDisplayMapXCoord(rightSeg.getParent())
                  + rightSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions());
                  int yRight = (int)((getAnnotationStartCoord(rightSeg, rightAnnot)
                      + getAnnotationStopCoord(rightSeg, rightAnnot)) / 2.0);

                  if (yLeft < getSegmentYCoord(leftSeg))
                    yLeft = (int)(getSegmentYCoord(leftSeg));
                  else if (yLeft > (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg)))
                    yLeft = (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg));

                  if (yRight < getSegmentYCoord(rightSeg))
                    yRight = (int)(getSegmentYCoord(rightSeg));
                  else if (yRight > (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg)))
                    yRight = (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg));

                  // Determine key
                  StringBuilder key = new StringBuilder();
                  key.append(xLeft);
                  key.append(yLeft);
                  key.append(xRight);
                  key.append(yRight);

                  // Draw line
                  if (!drawnLines.contains(key.toString()))
                  {
                    drawnLines.add(key.toString());

                    if (yLeft < yRight)
                    {
                      if (g2d.hitClip(xLeft, yLeft, xRight - xLeft, yRight - yLeft + 1))
                        g2d.drawLine(xLeft, yLeft, xRight, yRight);
                    }
                    else
                    {
                      if (g2d.hitClip(xLeft, yRight, xRight - xLeft, yLeft - yRight + 1))
                        g2d.drawLine(xLeft, yLeft, xRight, yRight);
                    }
                  }
                }
              }
            }
          }
        }
      }
      // Draw all connections when nothing is selected
      else if ((selection.getSegments().size() == 0 || !displayFeatureHighlight())
          && (adjConnections || nonAdjConnections)
          && numMapsVisible > 1 && mainGUI.getOptions().isShown("showConnections"))
      {
        HashSet<String> drawnLines = new HashSet<String>();

        for (DisplayMap drawnMap : displayMaps)
        {
          for (DisplaySegment drawnSegment : drawnMap.getSegments())
          {
            g2d.setColor(mainGUI.getOptions().getColor("connection"));

            Vector<DisplayMap> neighbors = getDisplayMapNeighbors(drawnSegment.getParent());

            for (Annotation annotation : drawnSegment.getSegmentFeatures())
            {
              if (!isAnnotationVisible(annotation)
                  || selection.contains(annotation))
                continue;

              for (Annotation sibling : annotation.getSiblings())
              {
                if (!hiddenAnnotation.contains(sibling) && mainGUI.getOptions().isShown(sibling.getAnnotationSet().getType()))
                {
                  DisplaySegment siblingSegment = null;

                  for (DisplayMap map : displayMaps)
                  {
                    for (DisplaySegment segment : map.getSegments())
                    {
                      if (segment.containsFeature(sibling))
                      {
                        siblingSegment = segment;
                        break;
                      }
                    }

                    if (siblingSegment != null)
                      break;
                  }

                  if (siblingSegment != null && siblingSegment != drawnSegment
                      && siblingSegment.getParent().isVisible()
                      && ((adjConnections && neighbors.contains(siblingSegment.getParent()))
                          || (nonAdjConnections && !neighbors.contains(siblingSegment.getParent()))))
                  {
                    // Draw connecting lines based on where the maps are positioned
                    DisplaySegment leftSeg = null;
                    Annotation leftAnnot = null;
                    DisplaySegment rightSeg = null;
                    Annotation rightAnnot = null;

                    // Determine what is on the left
                    if (drawnSegment.getParent().getPosition() <
                        siblingSegment.getParent().getPosition())
                    {
                      // selected on left
                      leftSeg = drawnSegment;
                      leftAnnot = annotation;
                      rightSeg = siblingSegment;
                      rightAnnot = sibling;
                    }
                    else
                    {
                      // sibling on left
                      leftSeg = siblingSegment;
                      leftAnnot = sibling;
                      rightSeg = drawnSegment;
                      rightAnnot = annotation;
                    }

                    // Draw line to middle
                    int xLeft = getDisplayMapXCoord(leftSeg.getParent())
                    + leftSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions())
                    + segmentWidth + 2;
                    int yLeft = (int)((getAnnotationStartCoord(leftSeg, leftAnnot)
                        + getAnnotationStopCoord(leftSeg, leftAnnot)) / 2.0);
                    int xRight = getDisplayMapXCoord(rightSeg.getParent())
                    + rightSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions());
                    int yRight = (int)((getAnnotationStartCoord(rightSeg, rightAnnot)
                        + getAnnotationStopCoord(rightSeg, rightAnnot)) / 2.0);

                    if (yLeft < getSegmentYCoord(leftSeg))
                      yLeft = (int)(getSegmentYCoord(leftSeg));
                    else if (yLeft > (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg)))
                      yLeft = (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg));

                    if (yRight < getSegmentYCoord(rightSeg))
                      yRight = (int)(getSegmentYCoord(rightSeg));
                    else if (yRight > (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg)))
                      yRight = (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg));

                    // Determine key
                    StringBuilder key = new StringBuilder();
                    key.append(xLeft);
                    key.append(yLeft);
                    key.append(xRight);
                    key.append(yRight);

                    // Draw line
                    if (!drawnLines.contains(key.toString()))
                    {
                      drawnLines.add(key.toString());

                      if (yLeft < yRight)
                      {
                        if (g2d.hitClip(xLeft, yLeft, xRight - xLeft, yRight - yLeft + 1))
                          g2d.drawLine(xLeft, yLeft, xRight, yRight);
                      }
                      else
                      {
                        if (g2d.hitClip(xLeft, yRight, xRight - xLeft, yLeft - yRight + 1))
                          g2d.drawLine(xLeft, yLeft, xRight, yRight);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Connecting Lines", timing);
      //logger.debug("Drew connecting lines in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing connecting lines between maps

      //
      // Draw connecting lines for selection intervals (green lines)
      //
      if (displayIntervalHighlight())
      {
        start = System.nanoTime();
        if ((adjConnections || nonAdjConnections) && numMapsVisible > 1)
        {
          int subStart, subStop;

          g2d.setColor(mainGUI.getOptions().getColor("intervalBorder"));

          for (SelectionInterval parent : selection.getIntervals())
          {
            if (!parent.getSegment().getParent().isVisible() || parent.isBeingSet() || parent.isChild()) continue;

            Rectangle parentRect = parent.getRectangle(mainGUI.getOptions());

            for (SelectionInterval child : parent.getChildren())
            {
              if (!child.getSegment().getParent().isVisible()) continue;

              // Check preferences
              boolean areNeighbors = areDisplayMapNeighbors(child.getSegment().getParent(), parent.getSegment().getParent());

              if (!nonAdjConnections && !areNeighbors) continue;
              if (!adjConnections && areNeighbors) continue;

              Rectangle childRect = child.getRectangle(mainGUI.getOptions());

              if (parent.getSegment().getDrawingStart() < parent.getSegment().getDrawingStop())
              {
                subStart = (int)getAnnotationStartCoord(parent.getSegment(), child.getAnnotationStart());
                subStop = (int)getAnnotationStopCoord(parent.getSegment(), child.getAnnotationStop());
              }
              else
              {
                subStart = (int)getAnnotationStopCoord(parent.getSegment(), child.getAnnotationStart());
                subStop = (int)getAnnotationStartCoord(parent.getSegment(), child.getAnnotationStop());
              }

              if (subStart > subStop)
              {
                int swap = subStart;
                subStart = subStop;
                subStop = swap;
              }

              if (subStart < parentRect.y) subStart = parentRect.y;
              if (subStop < parentRect.y) subStop = parentRect.y;
              if (subStop > parentRect.y + parentRect.height) subStop = parentRect.y + parentRect.height;
              if (subStart > parentRect.y + parentRect.height) subStart = parentRect.y + parentRect.height;

              if (parentRect.x < childRect.x)
              {
                // Draw top line
                g2d.drawLine(parentRect.x + parentRect.width, subStart, childRect.x, childRect.y);

                // Draw bottom line
                g2d.drawLine(parentRect.x + parentRect.width, subStop, childRect.x, childRect.y + childRect.height);
              }
              else
              {
                // Draw top line
                g2d.drawLine(childRect.x + childRect.width, childRect.y, parentRect.x, subStart);

                // Draw bottom line
                g2d.drawLine(childRect.x + childRect.width, childRect.y + childRect.height, parentRect.x, subStop);
              }
            }
          }
        }
        timing = (System.nanoTime() - start);
        addTiming("MapNavigator - paintComponent - Draw Selection Interval Connecting Lines", timing);
      }
      //////////////////////////// end of drawing connecting lines between maps
      //
      // Draw connecting lines for selected annotation (blue lines)
      //
      if (selection.getAnnotation().size() > 0)
      {
        start = System.nanoTime();
        HashSet<String> drawnLines = new HashSet<String>();
        HashSet<Annotation> doneConnecting = new HashSet<Annotation>();
        g2d.setColor(mainGUI.getOptions().getColor("selected"));
        if (!displayFeatureHighlight())
          g2d.setColor(mainGUI.getOptions().getColor("connection"));

        for (DisplaySegment seg : selection.getAnnotationSegments())
        {
          for (Annotation annot : selection.getAnnotation(seg))
          {
            for (Annotation sibling : annot.getSiblings())
            {
              if (!doneConnecting.contains(sibling) && selection.contains(sibling))
              {
                DisplaySegment annotSeg = seg;
                DisplaySegment siblingSeg = selection.getSegment(sibling);

                if (!siblingSeg.getParent().isVisible()) continue;

                boolean areNeighbors = areDisplayMapNeighbors(siblingSeg.getParent(), annotSeg.getParent());

                if (!nonAdjConnections && !areNeighbors) continue;
                if (!adjConnections && areNeighbors) continue;

                DisplaySegment leftSeg = null;
                Annotation leftAnnot = null;
                DisplaySegment rightSeg = null;
                Annotation rightAnnot = null;

                // Determine what is on the left
                if (annotSeg.getParent().getPosition() < siblingSeg.getParent().getPosition())
                {
                  // Annot on left
                  leftSeg = annotSeg;
                  leftAnnot = annot;
                  rightSeg = siblingSeg;
                  rightAnnot = sibling;
                }
                else
                {
                  // Sibling on left
                  leftSeg = siblingSeg;
                  leftAnnot = sibling;
                  rightSeg = annotSeg;
                  rightAnnot = annot;
                }

                // Draw line to middle
                int xLeft = getDisplayMapXCoord(leftSeg.getParent())
                + leftSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions())
                + segmentWidth + 2;
                int yLeft = (int)((getAnnotationStartCoord(leftSeg, leftAnnot)
                    + getAnnotationStopCoord(leftSeg, leftAnnot)) / 2.0);
                int xRight = getDisplayMapXCoord(rightSeg.getParent())
                + rightSeg.getParent().getFeatureColumnsWidth(mainGUI.getOptions());
                int yRight = (int)((getAnnotationStartCoord(rightSeg, rightAnnot)
                    + getAnnotationStopCoord(rightSeg, rightAnnot)) / 2.0);

                if (yLeft < getSegmentYCoord(leftSeg))
                  yLeft = (int)(getSegmentYCoord(leftSeg));
                else if (yLeft > (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg)))
                  yLeft = (int)(getSegmentYCoord(leftSeg)) + (int)(getSegmentHeight(leftSeg));

                if (yRight < getSegmentYCoord(rightSeg))
                  yRight = (int)(getSegmentYCoord(rightSeg));
                else if (yRight > (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg)))
                  yRight = (int)(getSegmentYCoord(rightSeg)) + (int)(getSegmentHeight(rightSeg));

                // Determine key
                StringBuilder key = new StringBuilder();
                key.append(xLeft);
                key.append(yLeft);
                key.append(xRight);
                key.append(yRight);

                // Draw line
                if (!drawnLines.contains(key.toString()))
                {
                  drawnLines.add(key.toString());

                  if (yLeft < yRight)
                  {
                    if (g2d.hitClip(xLeft, yLeft, xRight - xLeft, yRight - yLeft + 1))
                      g2d.drawLine(xLeft, yLeft, xRight, yRight);
                  }
                  else
                  {
                    if (g2d.hitClip(xLeft, yRight, xRight - xLeft, yLeft - yRight + 1))
                      g2d.drawLine(xLeft, yLeft, xRight, yRight);
                  }
                }
              }
            }
          }
        }
        timing = (System.nanoTime() - start);
        addTiming("MapNavigator - paintComponent - Draw Selected Feature Lines", timing);
      }
      ////////////////////////////end of drawing connecting lines between selected annotation

      //
      // Draw Segments
      //
      start = System.nanoTime();
      if (maps.size() > 0)
      {
        Font font = g2d.getFont();
        g2d.setFont(new Font("default", Font.BOLD, font.getSize() + incForChromLabel));

        for (DisplayMap displayMap : maps)
        {
          if (!displayMap.isVisible())
          {
            // Hidden Map
            Rectangle hiddenMap = new Rectangle(getDisplayMapXCoord(displayMap),
                getY() * -1,
                hiddenMapWidth,
                (getParent().getHeight()));

            // If not in clip, don't draw
            if (!g2d.hitClip(hiddenMap.x, hiddenMap.y - 1, hiddenMap.width, hiddenMap.height + 2))
              continue;

            g2d.setColor(mainGUI.getOptions().getColor("hiddenMap"));
            if (mousePos != null && hiddenMap.contains(mousePos))
              g2d.setColor(g2d.getColor().darker());
            Composite comp = g2d.getComposite();
            g2d.setComposite(makeComposite(0.4F));
            g2d.fill(hiddenMap);
            g2d.setComposite(comp);
          }
          else
          {
            // Visible Map
            int horizontalPosition = getDisplayMapXCoord(displayMap) + displayMap.getFeatureColumnsWidth(mainGUI.getOptions());

            if (!g2d.hitClip(horizontalPosition, headerHeight, segmentWidth + 1, drawingHeight))
              continue;

            for (DisplaySegment segment : displayMap.getSegments())
            {
              int segmentYCoord = (int)(getSegmentYCoord(segment));
              int segmentHeight = (int)(getSegmentHeight(segment));
              if (segmentHeight == 0) segmentHeight = 1;

              Rectangle map = new Rectangle(horizontalPosition,
                  segmentYCoord,
                  segmentWidth,
                  segmentHeight);

              // If not in clip, don't draw
              if (!g2d.hitClip(map.x, map.y, map.width, map.height))
                continue;

              // Minor adjustments...easier to fix this one piece than the thousands of lines
              map.x++;

              // Draw rectangle
              Color segColor = mainGUI.getOptions().getColor(segment.getChromosome().getName());
              if(segment.getChromosome().getName().matches("chr\\d+"))
              {
                String chrInfo[] = segment.getChromosome().getName().split("chr");
                int chrNum = Integer.parseInt(chrInfo[1]);
                segColor = mainGUI.getOptions().getColor("chr" + (chrNum % (COLORED_CHROMOSOMES + 1)));
              }
              g2d.setColor(segColor);
              g2d.fill(map);
              // If segment is selected, use a thicker stroke
              int border = 1;
              if (selection.getSegments().contains(segment))
                border = 2;

              // Draw stripes for chromosomes > 22
              if (g2d.hitClip(map.x, map.y, map.width, map.height))
              {
                if (Character.isLetter(segment.getChromosome().getName().substring(3).charAt(0)) == false &&
                    Integer.parseInt(segment.getChromosome().getName().substring(3)) > COLORED_CHROMOSOMES)
                {
                  g2d.setColor(Color.WHITE);
                  g2d.setStroke(CHROMOSOME_LINE_STROKE);
                  Rectangle newClip = new Rectangle(horizontalPosition,
                      segmentYCoord, segmentWidth + 1, segmentHeight + 1);
                  newClip = newClip.intersection(clip);
                  g2d.setClip(newClip);

                  int change = map.width;  // angle of stripe
                  int vertical = segmentYCoord;  // position to begin drawing a stripe

                  for (int i = segmentYCoord; i < segmentHeight + segmentYCoord; i += LINE_SPACING)
                  {
                    if ((vertical + change) < (segmentHeight + segmentYCoord))
                    {
                      g2d.drawLine(map.x, vertical, map.x + map.width, vertical + change);
                      vertical += LINE_SPACING;
                    }
                    else
                    {
                      // draws the last line to the bottom of the segment
                      int opposite = segmentHeight + segmentYCoord - vertical;
                      int adjacent = opposite / (int)Math.tan(ANGLE_FOR_STRIPES);

                      g2d.drawLine(map.x, vertical, map.x + adjacent, segmentHeight + segmentYCoord);
                    }
                  }
                  g2d.setClip(clip);
                }
              }

              //return to a default stroke for the border
              g2d.setStroke(new BasicStroke(border));

              //set the Color to Black for the border
              g2d.setColor(Color.BLACK);
              //correction for the improperly spaced border
              //If there is a hit with the clip, draw the border
              if(g2d.hitClip(map.x, map.y, map.width, map.height))
                g2d.draw(map);

              // Draw segment label
              if (segment != backboneSegment)
              {
                String chromosomeString = segment.getChromosome().getName().substring(3);

                int stringWidth = g2d.getFontMetrics().stringWidth(chromosomeString);
                int chromLabelHeight = g2d.getFontMetrics().getHeight();
                int chromLabelAscent = g2d.getFontMetrics().getMaxAscent();

                if ((chromLabelHeight + 2) < segmentHeight)
                {
                  int xCoord = horizontalPosition + ((segmentWidth - stringWidth) / 2) + 1;
                  int yCoord = segmentYCoord + segmentHeight / 2
                  + (chromLabelAscent - chromLabelHeight / 2);

                  // draw box around label for striped segments
                  if (Character.isLetter(segment.getChromosome().getName().substring(3).charAt(0)) == false &&
                      Integer.parseInt(segment.getChromosome().getName().substring(3)) > COLORED_CHROMOSOMES)
                  {
                    if (g2d.hitClip(xCoord, (yCoord - chromLabelHeight), stringWidth, chromLabelHeight * 2))
                    {
                      g2d.setColor(segColor);
                      g2d.fillRect(xCoord - 1, yCoord - chromLabelHeight + 2, stringWidth + 2, chromLabelHeight + 1);

                      g2d.setColor(g2d.getColor().darker());
                      g2d.draw(new Rectangle(xCoord - 1, yCoord
                          - chromLabelHeight + 2, stringWidth + 2,
                          chromLabelHeight + 1));
                    }
                  }

                  if (segColor.getRed() + segColor.getGreen() + segColor.getBlue() < 250)
                    g2d.setColor(Color.WHITE);
                  else
                    g2d.setColor(Color.BLACK);

                  if (g2d.hitClip(xCoord, (yCoord - chromLabelHeight), stringWidth, chromLabelHeight * 2))
                  {
                    g2d.drawString(chromosomeString, xCoord, yCoord);
                  }
                }
              }
            }
          }
        }
        g2d.setFont(font);
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Segments", timing);
      //logger.debug("Drew segments in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing segments

      //
      // Draw synteny blocks on backbone
      //
      start = System.nanoTime();
      if (backboneSegment != null)
      {
        DisplayMap bbMap = backboneSegment.getParent();
        int bbXCoord = getDisplayMapXCoord(bbMap) + bbMap.getFeatureColumnsWidth(mainGUI.getOptions()) + 1;;

        if (bbMap.isVisible() && g2d.hitClip(bbXCoord, headerHeight, segmentWidth, drawingHeight))
        {
          Vector<DisplayMap> neighbors = getDisplayMapNeighbors(bbMap);

          for (DisplayMap neighbor : neighbors)
          {
            for (DisplaySegment segment : neighbor.getSegments())
            {
              SyntenyBlock block = segment.getSyntenyBlock();

              if (block == null) continue;

              // Draw synteny blocks on backbone
              int syntenyXCoord = 0;
              int syntenyYCoord = (int)(getSegmentYCoord(segment));
              int syntenyHeight = (int)(getSegmentHeight(segment));

              if (neighbor.getPosition() < bbMap.getPosition())
              {
                // Immediate Left neighbor
                syntenyXCoord = bbXCoord;
              }
              else
              {
                // Immediate Right neighbor
                syntenyXCoord = bbXCoord
                + (int) (segmentWidth * (double)(SYNTENY_WIDTH_FACTOR-1)/(double)SYNTENY_WIDTH_FACTOR);
              }
              // Create Rectangle
              Rectangle synteny = new Rectangle(syntenyXCoord,
                  syntenyYCoord,
                  segmentWidth / SYNTENY_WIDTH_FACTOR,
                  syntenyHeight);

              // Set a secondary rectangle to the bounds of the synteny
              // and get the intersection of those two clips
              Rectangle newClip = g2d.getClipBounds();
              newClip.setBounds(synteny.x, synteny.y, synteny.width, synteny.height);
              newClip = newClip.intersection(clip);
              // Clip Check
              if (!g2d.hitClip(synteny.x, synteny.y, synteny.width, synteny.height))
                continue;

              // Draw synteny
              Color segColor = mainGUI.getOptions().getColor(segment.getChromosome().getName());
              if(segment.getChromosome().getName().matches("chr\\d+"))
              {
                String chrInfo[] = segment.getChromosome().getName().split("chr");
                int chrNum = Integer.parseInt(chrInfo[1]);
                segColor = mainGUI.getOptions().getColor("chr" + (chrNum % (COLORED_CHROMOSOMES + 1)));
              }
              g2d.setColor(segColor);
              g2d.fill(synteny);

              if (g2d.hitClip(synteny.x, synteny.y, synteny.width, synteny.height))
              {
                if (Character.isLetter(segment.getChromosome().getName().substring(3).charAt(0)) == false &&
                    Integer.parseInt(segment.getChromosome().getName().substring(3)) > COLORED_CHROMOSOMES)
                {
                  g2d.setColor(Color.WHITE);
                  g2d.setStroke(CHROMOSOME_LINE_STROKE);

                  int syntenyChange = synteny.width;  // angle of stripe
                  int vertical = syntenyYCoord;  // position to begin drawing a stripe

                  // set the clip to only allow drawing in the area of the synteny itself
                  g2d.setClip(newClip);

                  for (int i = syntenyYCoord; i < syntenyYCoord + syntenyHeight; i += (LINE_SPACING))
                  {
                    if ((vertical + syntenyChange) < (syntenyHeight + syntenyYCoord))
                    {
                      g2d.drawLine(synteny.x, vertical, synteny.x + synteny.width, vertical + syntenyChange);
                      vertical += LINE_SPACING;
                    }
                    else
                    {
                      // draws the last line to the bottom of the segment
                      int opposite = syntenyHeight + syntenyYCoord - vertical;
                      int adjacent = opposite / (int)Math.tan(ANGLE_FOR_STRIPES);

                      g2d.drawLine(synteny.x, vertical, synteny.x + adjacent, syntenyHeight + syntenyYCoord);
                    }
                  }
                  //reset the clip so that the other portions may be properly drawn
                  g2d.setClip(clip);
                }
              }

              g2d.setStroke(new BasicStroke(1));
              g2d.setColor(mainGUI.getOptions().getColor("syntenyBorder"));
              //if the synteny hits the clip, draw the border
              if(g2d.hitClip(synteny.x, synteny.y, synteny.width, synteny.height))
                g2d.draw(synteny);
            }
          }
        }
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Synteny on Backbone", timing);
      //logger.debug("Drew flank syntey on backbone in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of flanks & synteny blocks for backbone
      //
      // Draw Features
      //
      start = System.nanoTime();
      for (DisplayMap displayMap : maps)
      {
        if (!displayMap.isVisible()) continue;

        Composite solidComp = makeComposite(1.0F);
        Composite labelBackgroundComp = makeComposite(0.8F);
        Composite selectedComp = makeComposite(0.1F);
        int mapXCoord = getDisplayMapXCoord(displayMap);

        for (DisplaySegment segment : displayMap.getSegments())
        {
          int horizontalPosition = mapXCoord;

          int segYCoord = (int)(getSegmentYCoord(segment));
          int segHeight = (int)(getSegmentHeight(segment));

          if (!g2d.hitClip(horizontalPosition, segYCoord,
              displayMap.getFeatureColumnsWidth(mainGUI.getOptions()), segHeight))
            continue;

          for (AnnotationSet set : displayMap.getShownSets())
          {
            int typeFeatureColumns = displayMap.getFeatureColumns(set);
            if (!mainGUI.getOptions().isShown(set.getType()) || hiddenTypes.contains(set))
              continue;

            // Clip check
            if (!g2d.hitClip(horizontalPosition, 0,
                featureLabelColumnWidth + featureColumnWidth * typeFeatureColumns,
                drawingHeight + headerHeight + mainGUI.getOptions().getIntOption("footerHeight")))
            {
              horizontalPosition += featureLabelColumnWidth + featureColumnWidth * typeFeatureColumns;
              continue;
            }

            horizontalPosition += featureLabelColumnWidth;

            //
            // Draw Feature labels
            //
            if (g2d.hitClip(horizontalPosition - featureLabelColumnWidth,
                0,
                featureLabelColumnWidth,
                drawingHeight + headerHeight + mainGUI.getOptions().getIntOption("footerHeight")))
            {
              for (DisplayAnnotation displayAnnot : segment
                  .getDisplayAnnotation(set, clip.y - labelHeight
                      - selectionBoxSpacing, clip.y + clip.height + labelHeight
                      + selectionBoxSpacing))
              {
                int width = displayAnnot.getLabelWidth() + selectionBoxSpacing * 2;

                if (g2d.hitClip(horizontalPosition - width, (displayAnnot
                    .getLabelYCoord())
                    - labelHeight / 2 - selectionBoxSpacing, width, labelHeight
                    + selectionBoxSpacing * 2))
                {
                  // Create rectangle for a semi-transparent background for the feature
                  // label
                  Rectangle mrkrBackground = new Rectangle(horizontalPosition - displayAnnot.getLabelWidth() - 2,
                      displayAnnot.getLabelYCoord() - labelHeight / 2,
                      displayAnnot.getLabelWidth() + 3,
                      labelHeight);

                  boolean allAnnotSelected = false, annotSelected = false;
                  allAnnotSelected = selection.containsAll(displayAnnot.getAnnotationAsVector());

                  // Check if any of the annotation are in the selection
                  annotSelected = selection.containsAny(displayAnnot.getAnnotationAsVector());
                  if (allAnnotSelected || annotSelected)
                  {
                    mrkrBackground.height += selectionBoxSpacing * 2;
                    mrkrBackground.width += selectionBoxSpacing * 2;
                    mrkrBackground.x -= selectionBoxSpacing * 2;
                    mrkrBackground.y -= selectionBoxSpacing;
                  }
                  // Draw white background for label
                  if (g2d.hitClip(mrkrBackground.x, mrkrBackground.y, mrkrBackground.width, mrkrBackground.height))
                  {
                    g2d.setColor(mainGUI.getOptions().getColor("background"));
                    g2d.setComposite(labelBackgroundComp);
                    g2d.fill(mrkrBackground);
                  }

                  // If all annotations selected draw background
                  if (displayFeatureHighlight() && (allAnnotSelected || annotSelected)
                    && g2d.hitClip(mrkrBackground.x, mrkrBackground.y, mrkrBackground.width, mrkrBackground.height))
                  {
                    g2d.setComposite(selectedComp);
                    g2d.setPaint(Color.BLUE.darker());
                    g2d.fill(mrkrBackground);
                  }

                  // Draw label text
                  if (g2d.hitClip(mrkrBackground.x, mrkrBackground.y, mrkrBackground.width, mrkrBackground.height))
                  {
                    if (featureDisplayType == -1 && !displayAnnot.getDisplayedAnnotation().hasHomoloGeneId())
                      g2d.setColor(Color.gray);
                    else
                      g2d.setColor(mainGUI.getOptions().getColor(set.getType()));

                    if (displayAnnot.getAnnotation().size() > 1)
                      g2d.setFont(displayAnnot.getMetrics().getFont());

                    g2d.setComposite(solidComp);
                    g2d.drawString(displayAnnot.getLabel(),
                        horizontalPosition - displayAnnot.getLabelWidth() - selectionBoxSpacing,
                        displayAnnot.getLabelYCoord() + labelAscent - labelHeight / 2);

                    g2d.setFont(VCMap.labelFont);
                  }

                  //
                  // Draw feature label selection box
                  //
                  // If all annotations selected, draw border
                  if (displayFeatureHighlight())
                  {
                    if (allAnnotSelected && g2d.hitClip(mrkrBackground.x, mrkrBackground.y, mrkrBackground.width, mrkrBackground.height))
                    {
                      // Draw border for selction box if ALL selected
                      g2d.setColor(Color.BLUE.darker());
                      g2d.setStroke(new BasicStroke(selectionBoxStroke));
                      g2d.draw(mrkrBackground);
                    }
                    else if (annotSelected && g2d.hitClip(mrkrBackground.x, mrkrBackground.y, mrkrBackground.width, mrkrBackground.height))
                    {
                      // Draw border for selction box if partially selected
                      g2d.setColor(Color.BLUE.darker());
                      g2d.setStroke(DASHED_STROKE);
                      g2d.draw(mrkrBackground);
                      // Set back to selection box stroke
                      g2d.setStroke(new BasicStroke(selectionBoxStroke));
                    }
                  }
                }
              }
            }

            //
            // Draw Elements (Markers)
            //
            if (g2d.hitClip(horizontalPosition + featureColumnWidth / 4, 0,
                featureColumnWidth * typeFeatureColumns,
                drawingHeight + headerHeight + mainGUI.getOptions().getIntOption("footerHeight")))
            {
              for (DisplayAnnotation displayAnnot : segment.getDisplayAnnotation(set))
              {
                // Determine x & width
                int x = horizontalPosition;
                int width = featureColumnWidth * typeFeatureColumns;

                // Determine y
                int y = -1;
                if (segment.getDrawingStart() < segment.getDrawingStop())
                  y = (int)(getAnnotationStartCoord(segment, displayAnnot.getStartAnnot()));
                else
                  y = (int)(getAnnotationStartCoord(segment, displayAnnot.getStopAnnot()));

                // Determine height
                int height = -1;
                if (segment.getDrawingStart() < segment.getDrawingStop())
                {
                  height = (int)(getAnnotationStopCoord(segment, displayAnnot.getStopAnnot()))
                  - (int)(getAnnotationStartCoord(segment, displayAnnot.getStartAnnot()));
                }
                else
                {
                  height = (int)(getAnnotationStopCoord(segment, displayAnnot.getStartAnnot()))
                  - (int)(getAnnotationStartCoord(segment, displayAnnot.getStopAnnot()));
                }
                height += 1;

                // Clip check
                if (!g2d.hitClip(x, y, width, height))
                  continue;

                width = featureColumnWidth / 2;
                int xAdjust = x + featureColumnWidth / 4;

                g2d.setColor(mainGUI.getOptions().getColor(set.getType()));
                for (DisplayAnnotation.DAMember member : displayAnnot.getAnnotation())
                {
                  if (selection.contains(member.getAnnotation())) continue;

                  x = xAdjust + featureColumnWidth * member.getColumn();
                  y = (int)(getAnnotationStartCoord(segment, member.getAnnotation()));
                  height = (int)(getAnnotationHeight(segment, member.getAnnotation())) + 1;

                  // Clip check
                  if (g2d.hitClip(x, y, width, height))
                    g2d.fillRect(x, y, width, height);
                }

                // Change color to draw selected
                if(displayFeatureHighlight())
                  g2d.setColor(mainGUI.getOptions().getColor("selected"));

                width = featureColumnWidth / 2;
                for (DisplayAnnotation.DAMember member : displayAnnot.getAnnotation())
                {
                  if (!selection.contains(member.getAnnotation())) continue;

                  x = xAdjust + featureColumnWidth * member.getColumn();
                  y = (int)(getAnnotationStartCoord(segment, member.getAnnotation()));
                  height = (int)(getAnnotationHeight(segment, member.getAnnotation())) + 1;

                  // Clip check
                  if (g2d.hitClip(x, y, width, height))
                    g2d.fillRect(x, y, width, height);
                }

                g2d.setColor(Color.BLACK);

                // Draw connecting lines
                if (displayAnnot.getColumn() > 0)
                {
                  y = displayAnnot.getLineYCoord();
                  int begin = horizontalPosition;
                  int finish = begin + featureColumnWidth * displayAnnot.getColumn() + featureColumnWidth / 4;

                  if (g2d.hitClip(begin, y, finish - begin, 1))
                  {
                    for (int j = begin; j < finish; j = j + 2)
                      g2d.drawLine(j, y, j, y);
                  }
                }
              }
            }

            horizontalPosition += featureColumnWidth * typeFeatureColumns;
          }
        }
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Features", timing);
      //logger.debug("Drew features in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing features

      //
      // Draw units
      //
      start = System.nanoTime();

      if (mainGUI.getOptions().isShown("units"))
      {
        Font oldFont = g2d.getFont();
        g2d.setFont(VCMap.unitFont);
        for (DisplayMap displayMap : maps)
        {
          if (!displayMap.isVisible()) continue;

          int horizontalPosition = getDisplayMapXCoord(displayMap)
          + displayMap.getFeatureColumnsWidth(mainGUI.getOptions())
          + segmentWidth + 1;

          if (!g2d.hitClip(horizontalPosition, 0, displayMap.getUnitsColumnWidth(),
              drawingHeight + headerHeight + mainGUI.getOptions().getIntOption("footerHeight")))
          {
            continue;
          }

          g2d.setColor(Color.BLACK);

          for (DisplaySegment segment : displayMap.getSegments())
          {
            Vector<UnitLabel> drawnUnits = segment.getUnitLabels();
            if (drawnUnits.size() == 0) continue;

            g2d.setStroke(new BasicStroke(unitsLineStroke));
            for (UnitLabel unitLabel : drawnUnits)
            {
              // Check if we have gone past the clip check (units must be in ascending order)
              if (unitLabel.getLabelYCoord() + (metrics.getMaxAscent()/2) < clip.y)
              {
                continue;
              }
              if (unitLabel.getLabelYCoord() - (metrics.getMaxAscent()/2) > clip.y + clip.height)
              {
                break;
              }

              // No longer need a hit clip
              // Draw connecting lines
              g2d.drawLine(horizontalPosition,
                  ((int)(unitLabel.getUnitYCoord())),
                  horizontalPosition + featureColumnWidth,
                  ((int)(unitLabel.getLabelYCoord())));

              //fill background color behind label
              Color oldColor = g2d.getColor();
              g2d.setColor(mainGUI.getOptions().getColor("background"));
              Composite comp = g2d.getComposite();
              g2d.setComposite(makeComposite(0.8F));
              g2d.fillRect(horizontalPosition + featureColumnWidth + 2,
                  (int)(unitLabel.getLabelYCoord() - labelHeight / 2),
                  unitLabel.getLabelWidth(), labelHeight);
              g2d.setComposite(comp);
              g2d.setColor(oldColor);

              // Draw unit label
              g2d.drawString(unitLabel.getLabel(),
                  horizontalPosition + featureColumnWidth + 2,
                  (int)(unitLabel.getLabelYCoord())
                  + metrics.getMaxAscent() - labelHeight / 2);
            }
          }
        }
        g2d.setFont(oldFont);
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Units", timing);
      //logger.debug("Drew units in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing units

      //
      // Draw Selection Inverval
      // 
      start = System.nanoTime();
      if (displayIntervalHighlight())
      {
        for (SelectionInterval parent : selection.getIntervals())
        {
          if (!parent.getSegment().getParent().isVisible())
            continue;

          Composite comp = g2d.getComposite();
          Rectangle parentRect = parent.getRectangle(mainGUI.getOptions());
          if(parentRect.y < minFeatureSelected)
            minFeatureSelected = parentRect.y;
          if((parentRect.y + parentRect.height) > maxFeatureSelected)
            maxFeatureSelected = (parentRect.y + parentRect.height);

          selectionPercentSize = ((double)(maxFeatureSelected - minFeatureSelected)) / (double)getDrawingHeight();

          if (parent.isVisible())
          {
            Rectangle clipRect = parent.getRectangle(mainGUI.getOptions());

            // Adjust for clip
            if (clip.y + clip.height < clipRect.y + clipRect.height && clipRect.y >= clip.y)
            {
              clipRect.height = clipRect.y - clip.y + clip.height;
            }
            if (clipRect.y + clipRect.height < clip.y + clip.height && clipRect.y <= clip.y)
            {
              clipRect.height -= clip.y - clipRect.y;
              clipRect.y = clip.y;
            }
            clipRect.x++;
            clipRect.width--;

            if (g2d.hitClip(clipRect.x, clipRect.y, clipRect.width, clipRect.height))
            {
              g2d.setComposite(makeComposite(0.4F));
              g2d.setPaint(mainGUI.getOptions().getColor("interval"));
              g2d.fill(clipRect);

              g2d.setComposite(comp);
              g2d.setPaint(mainGUI.getOptions().getColor("intervalBorder"));
              g2d.setStroke(new BasicStroke(1));
              g2d.draw(clipRect);
            }
          }

          g2d.setColor(mainGUI.getOptions().getColor("interval"));
          for (SelectionInterval child : parent.getChildren())
          {
            int subStart, subStop;
            if (parent.getSegment().getDrawingStart() < parent.getSegment().getDrawingStop())
            {
              subStart = (int)getAnnotationStartCoord(parent.getSegment(), child.getAnnotationStart());
              subStop = (int)getAnnotationStopCoord(parent.getSegment(), child.getAnnotationStop());
            }
            else
            {
              subStart = (int)getAnnotationStopCoord(parent.getSegment(), child.getAnnotationStart());
              subStop = (int)getAnnotationStartCoord(parent.getSegment(), child.getAnnotationStop());
            }

            if (subStart > subStop)
            {
              int swap = subStart;
              subStart = subStop;
              subStop = swap;
            }

            if (subStart < parentRect.y)
              subStart = parentRect.y;
            if (subStop > parentRect.y + parentRect.height)
              subStop = parentRect.y + parentRect.height;

            //draw green limit lines between backbone and off-backbone map
            if (g2d.hitClip(parentRect.x + 1, subStart, parentRect.width, 2))
              g2d.drawLine(parentRect.x + 1, subStart, parentRect.x + parentRect.width, subStart);
            if (g2d.hitClip(parentRect.x + 1, subStop, parentRect.width, 2))
              g2d.drawLine(parentRect.x + 1, subStop, parentRect.x + parentRect.width, subStop);
          }
        }
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Selection Interval", timing);
      //logger.debug("Drew selection interval in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing selection interval

      //
      // Draw map titles
      //
      start = System.nanoTime();
      if (backboneSegment != null && mapHeaders != null)
      {
        mapHeaders.repaint();
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Map Titles", timing);
      //logger.debug("Drew map titles in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing titles

      //
      // Draw feature selection box
      //
      start = System.nanoTime();
      for (Rectangle featureSelectionBox : featureSelectionBoxes)
      {
        // If not in clip, don't draw
        if (g2d.hitClip(featureSelectionBox.x, featureSelectionBox.y, featureSelectionBox.width, featureSelectionBox.height + 1))
        {
          Rectangle clipRect = (Rectangle)featureSelectionBox.clone();
          // Resize rectangle drawn, no need to redraw the whole thing
          //   Check the Y-Axis
          if (clip.y + clip.height < clipRect.y + clipRect.height && clipRect.y >= clip.y)
          {
            clipRect.height = clipRect.y - clip.y + clip.height;
          }
          if (clipRect.y + clipRect.height < clip.y + clip.height && clipRect.y <= clip.y)
          {
            clipRect.height -= clip.y - clipRect.y;
            clipRect.y = clip.y;
          }

          Composite comp = g2d.getComposite();
          g2d.setComposite(makeComposite(0.1F));
          g2d.setPaint(Color.BLUE.darker());
          g2d.fill(clipRect);

          g2d.setComposite(comp);
          g2d.setPaint(Color.BLUE.darker());
          g2d.setStroke(new BasicStroke(1));
          g2d.draw(featureSelectionBox);
        }
      }
      timing = (System.nanoTime() - start);
      addTiming("MapNavigator - paintComponent - Draw Feature Selection Box", timing);

      //
      // Draw tool tip
      //
      start = System.nanoTime();
      if (mousePos != null)
      {
        for (DisplayMap displayMap : displayMaps)
        {
          // Set right and left bounds
          int leftBound = getDisplayMapXCoord(displayMap);
          if (displayMap.isVisible())
            leftBound += displayMap.getFeatureColumnsWidth(mainGUI.getOptions());

          int rightBound = leftBound;
          if (displayMap.isVisible())
            rightBound += segmentWidth;
          else
            rightBound += hiddenMapWidth;

          if (leftBound <= mousePos.x && mousePos.x <= rightBound)
          {
            if (displayMap.isVisible())
            {
              for (DisplaySegment segment : displayMap.getSegments())
              {
                if (getSegmentYCoord(segment) <= mousePos.y
                    && mousePos.y <= (getSegmentYCoord(segment) + getSegmentHeight(segment)))
                {
                  boolean overInterval = false;

                  for (SelectionInterval selInterval : selection.getIntervals(segment))
                  {
                    if (selInterval.isBeingSet())
                      overInterval = true;

                    Rectangle interval = selInterval.getRectangle(mainGUI.getOptions());

                    if (selInterval.getSegment().getParent().isVisible()
                        && interval.contains(new Point(mousePos.x, mousePos.y)))
                    {
                      overInterval = true;
                    }
                  }

                  if (!overInterval)
                  {
                    Color lineColor = Color.BLACK;
                    Color segColor = mainGUI.getOptions().getColor(segment.getChromosome().getName());
                    if(segment.getChromosome().getName().matches("chr\\d+"))
                    {
                      String chrInfo[] = segment.getChromosome().getName().split("chr");
                      int chrNum = Integer.parseInt(chrInfo[1]);
                      segColor = mainGUI.getOptions().getColor("chr" + (chrNum % (COLORED_CHROMOSOMES + 1)));
                    }
                    if (segColor.equals(Color.BLACK))
                      lineColor = Color.WHITE;
                    else
                      lineColor = Color.BLACK;

                    g2d.setColor(lineColor);
                    g2d.drawLine(leftBound + 2, mousePos.y, rightBound, mousePos.y);

                    // Change font and update metrics
                    Font font = g2d.getFont();
                    g2d.setFont(new Font("default", Font.BOLD, font.getSize() + incForChromLabel));
                    metrics = g2d.getFontMetrics();

                    String[] chromName = segment.getChromosome().getName().split("chr");

                    int stringWidth = metrics.stringWidth(chromName[1]);
                    int chrLabelHeight = metrics.getHeight();
                    int chrLabelAscent = metrics.getMaxAscent();

                    // Draw background
                    g2d.setColor(mainGUI.getOptions().getColor("overlapBox"));
                    g2d.fillRect(leftBound + (segmentWidth - stringWidth - 2) / 2,
                        mousePos.y - chrLabelHeight,
                        stringWidth + 1,
                        chrLabelHeight);

                    // Draw border
                    g2d.setColor(lineColor);
                    g2d.drawRect(leftBound + (segmentWidth - stringWidth - 2) / 2,
                        mousePos.y - chrLabelHeight,
                        stringWidth + 1,
                        chrLabelHeight);

                    // Draw label
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(chromName[1],
                        leftBound + (segmentWidth - stringWidth - 2) / 2 + 1,
                        mousePos.y + chrLabelAscent - chrLabelHeight);

                    // Restore font and metrics
                    g2d.setFont(font);
                    metrics = g2d.getFontMetrics();
                  }
                }
              }
              break;
            }
            else
            {
              // Change font and update metrics
              Font font = g2d.getFont();
              g2d.setFont(new Font("default", Font.BOLD, font.getSize() + incForChromLabel));
              metrics = g2d.getFontMetrics();

              String mapName = displayMap.getMap().getName();

              int stringWidth = metrics.stringWidth(mapName);
              int chrLabelHeight = metrics.getHeight();
              int chrLabelAscent = metrics.getMaxAscent();

              int xCoord = mousePos.x - stringWidth - 4;
              if (xCoord < this.getX() * -1)
                xCoord += stringWidth + 14;

              // Draw background
              g2d.setColor(mainGUI.getOptions().getColor("overlapBox"));
              g2d.fillRect(xCoord, mousePos.y, stringWidth + 1, chrLabelHeight);

              // Draw border
              g2d.setColor(Color.BLACK);
              g2d.drawRect(xCoord, mousePos.y, stringWidth + 1, chrLabelHeight);

              // Draw label
              g2d.drawString(mapName, xCoord + 1, mousePos.y + chrLabelAscent);

              // Restore font and metrics
              g2d.setFont(font);
              metrics = g2d.getFontMetrics();
              break;
            }
          }
        }
      }
      timing = (System.nanoTime() - start);
      //addTiming("MapNavigator - paintComponent - Draw Tooltip", timing);
      //logger.debug("Drew tooltip in: " + (System.nanoTime() - start) + "ms");

      //
      // Draw overlapBox
      //
      start = System.nanoTime();
      for (OverlapBox overlapBox : overlapBoxes)
      {
        if (overlapBox.isOpen())
        {
          DisplaySegment overlapBoxSegment = overlapBox.getSegment();

          boolean inverted = overlapBox.isInverted();
          int overlapBoxHeight = (int)overlapBox.getHeight();
          int overlapBoxYCoord = (int)(overlapBox.getY());
          int segXCoord = getDisplayMapXCoord(overlapBoxSegment.getParent()) + overlapBoxSegment.getParent().getFeatureColumnsWidth(mainGUI.getOptions());

          // button settings
          int buttonHeight = mainGUI.getOptions().getIntOption("buttonHeight");

          // Space for labels
          int labelColumnWidth = overlapBox.getLabelColumnWidth();

          // OverlapBox dimensions
          int overlapBoxWidth = (int)(overlapBox.getWidth());
          int overlapBoxXCoord = (int)(overlapBox.getX());

          // Draw connection lines
          int top = (int)(getAnnotationStartCoord(overlapBoxSegment, overlapBox.getStartAnnot()));
          int bottom = (int)(getAnnotationStopCoord(overlapBoxSegment, overlapBox.getStopAnnot()));
          int clipStart = Math.min(overlapBoxYCoord, top);
          int clipStop = Math.max(overlapBoxYCoord + overlapBoxHeight, bottom);

          if (g2d.hitClip(overlapBoxXCoord + overlapBoxWidth,
              clipStart,
              segXCoord - (overlapBoxXCoord + overlapBoxWidth),
              clipStop - clipStart))
          {
            g2d.setColor(Color.BLACK);
            g2d.drawLine(overlapBoxXCoord + overlapBoxWidth,
                overlapBoxYCoord,
                segXCoord,
                (int)(getAnnotationStartCoord(overlapBoxSegment, overlapBox.getStartAnnot())));

            g2d.drawLine(overlapBoxXCoord + overlapBoxWidth,
                overlapBoxYCoord + overlapBoxHeight,
                segXCoord,
                (int)(getAnnotationStopCoord(overlapBoxSegment, overlapBox.getStopAnnot())));
          }

          // Draw actual overlap box
          if (g2d.hitClip(overlapBoxXCoord, overlapBoxYCoord, overlapBoxWidth, overlapBoxHeight))
          {
            // Change composite to make background semi-transparent
            Composite comp = g2d.getComposite();
            g2d.setComposite(makeComposite(0.9F));

            // Draw box background
            g2d.setColor(mainGUI.getOptions().getColor("overlapBox"));
            g2d.fillRect(overlapBoxXCoord,
                overlapBoxYCoord,
                overlapBoxWidth,
                overlapBoxHeight);

            // Change composite to make background opaque
            g2d.setComposite(comp);

            // set the color for the buttons
            g2d.setColor(Color.GRAY);

            // draw top button bar
            g2d.fillRect(overlapBoxXCoord, overlapBoxYCoord, overlapBoxWidth, buttonHeight);

            // draw up and down buttons
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillRect(overlapBoxXCoord, overlapBoxYCoord + buttonHeight,
                labelColumnWidth, buttonHeight);
            g2d.fillRect(overlapBoxXCoord, overlapBoxYCoord + overlapBoxHeight - buttonHeight,
                labelColumnWidth, buttonHeight);
            // divider line color
            g2d.setColor(Color.BLACK);

            // top button bar vertical divider line
            g2d.drawLine(overlapBoxXCoord + (overlapBoxWidth / 2), overlapBoxYCoord,
                overlapBoxXCoord + (overlapBoxWidth / 2), overlapBoxYCoord + buttonHeight);

            // top button bar horizontal divider line
            g2d.drawLine(overlapBoxXCoord, overlapBoxYCoord + buttonHeight,
                overlapBoxXCoord + overlapBoxWidth, overlapBoxYCoord + buttonHeight);

            // up button horizontal divider line
            g2d.drawLine(overlapBoxXCoord, overlapBoxYCoord + (buttonHeight * 2),
                overlapBoxXCoord + labelColumnWidth, overlapBoxYCoord + (buttonHeight * 2));

            // down button horizontal divider line
            g2d.drawLine(overlapBoxXCoord, overlapBoxYCoord + overlapBoxHeight - buttonHeight,
                overlapBoxXCoord + labelColumnWidth, overlapBoxYCoord + overlapBoxHeight - buttonHeight);

            // draw column divider line
            g2d.drawLine(overlapBoxXCoord + labelColumnWidth, overlapBoxYCoord + buttonHeight,
                overlapBoxXCoord + labelColumnWidth, overlapBoxYCoord + overlapBoxHeight);

            // Draw box border
            g2d.drawRect(overlapBoxXCoord,
                overlapBoxYCoord,
                overlapBoxWidth,
                overlapBoxHeight);

            // draw '+' for fit
            g2d.drawString("+",
                overlapBoxXCoord + (overlapBoxWidth / 4) - (metrics.charWidth('+') / 2),
                overlapBoxYCoord + ((buttonHeight  + metrics.getAscent())/ 2) - 1);

            // draw 'x' for close
            g2d.drawString("x",
                overlapBoxXCoord + ((3 * overlapBoxWidth) / 4) - (metrics.charWidth('x') / 2),
                overlapBoxYCoord + ((buttonHeight  + metrics.getAscent())/ 2) - 1);

            // determine the points for the up arrow
            int xPointsUp[] = {overlapBoxXCoord + (labelColumnWidth / 2),
                overlapBoxXCoord + (labelColumnWidth / 2) - 5,
                overlapBoxXCoord + (labelColumnWidth / 2) + 5};
            int yPointsUp[] = {overlapBoxYCoord + buttonHeight + (buttonHeight / 4),
                overlapBoxYCoord + buttonHeight + ((3 * buttonHeight) / 4),
                overlapBoxYCoord + buttonHeight + ((3 * buttonHeight) / 4)};

            // draw the up arrow
            if (!overlapBox.displayUpArrow())
            {
              g2d.setColor(Color.GRAY);
              g2d.fillPolygon(xPointsUp, yPointsUp, xPointsUp.length);
            }
            else
            {
              g2d.setColor(Color.BLACK);
              g2d.fillPolygon(xPointsUp, yPointsUp, xPointsUp.length);
            }

            // determine the points for the down arrow
            int xPointsDown[] = {overlapBoxXCoord + (labelColumnWidth / 2) - 5,
                overlapBoxXCoord + (labelColumnWidth / 2),
                overlapBoxXCoord + (labelColumnWidth / 2) + 5};
            int yPointsDown[] = {overlapBoxYCoord + overlapBoxHeight - ((3 * buttonHeight) / 4),
                overlapBoxYCoord + overlapBoxHeight - (buttonHeight / 4),
                overlapBoxYCoord + overlapBoxHeight - ((3 * buttonHeight) / 4)};

            // draw the down arrow
            if (!overlapBox.displayDownArrow())
            {
              g2d.setColor(Color.GRAY);
              g2d.fillPolygon(xPointsDown, yPointsDown, xPointsDown.length);
            }
            else
            {
              g2d.setColor(Color.BLACK);
              g2d.fillPolygon(xPointsDown, yPointsDown, xPointsDown.length);
            }

            //
            // Draw Dragged OverlapBox
            //
            start = System.nanoTime();
            if (overlapDragRect != null && overlapBoxPressed != null)
            {
              comp = g2d.getComposite();
              g2d.setComposite(makeComposite(0.1F));
              g2d.setPaint(Color.BLUE.darker());
              g2d.fillRect(overlapDragRect.x, overlapDragRect.y, overlapDragRect.width, overlapDragRect.height);

              g2d.setComposite(comp);
              g2d.setPaint(Color.BLUE.darker());
              g2d.setStroke(new BasicStroke(1));
              g2d.drawRect(overlapDragRect.x, overlapDragRect.y, overlapDragRect.width, overlapDragRect.height);
            }
            //logger.debug("Drew dragged overlap box in: " + (System.nanoTime() - start) + "ms");

            //
            // Draw columns
            //
            int labelXCoord = overlapBoxXCoord + mainGUI.getOptions().getIntOption("labelColumnSpacing");
            int annotXCoord = overlapBoxXCoord + labelColumnWidth + mainGUI.getOptions().getIntOption("annotColumnSpacing");
            int featureWidth = mainGUI.getOptions().getIntOption("annotDrawingWidth");
            int betweenAnnotSpacing = mainGUI.getOptions().getIntOption("overlapBoxBetweenAnnotSpacing");

            Composite compNorm = g2d.getComposite();
            Composite compTrans = makeComposite(0.3F);
            Annotation spotlight = overlapBox.getSpotlight(); // annotation being hovered over

            // draw each column
            for (Vector<Annotation> column : overlapBox.getColumns())
            {
              // draw each annotation in the column
              // there should only be one annotation per column
              for (Annotation annot : column)
              {
                // draw in a different color for a selected and spotlight Annotation
                if (selection.contains(annot) || spotlight == annot)
                  g2d.setColor(mainGUI.getOptions().getColor("selected"));
                else
                {
                  if (featureDisplayType == -1 && !annot.hasHomoloGeneId())
                    g2d.setColor(Color.gray);
                  else
                    g2d.setColor(Color.black);
                }

                g2d.setComposite(compNorm);

                // draw the label only if it is in the visible area of the overlap box
                if (overlapBox.isAnnotLabelVisible(annot))
                  g2d.drawString(annot.getName(), labelXCoord, (int)(overlapBox.getAnnotLabelYCoord(annot)));

                if (!g2d.getColor().equals(mainGUI.getOptions().getColor("selected")))
                  g2d.setComposite(compTrans);

                int featureYCoord = (int)(overlapBox.getAnnotYCoord(annot));
                int featureHeight = (int)(overlapBox.getAnnotHeight(annot));

                Polygon startTriangle = null;
                Polygon stopTriangle = null;

                // check for top extending past overlap segment
                if ((!inverted && annot.getStart() < overlapBox.getSegment().getDrawingStart())
                    || (inverted && annot.getStop() > overlapBox.getSegment().getDrawingStart()))
                {
                  int[] x = new int[3];
                  int[] y = new int[3];

                  // top
                  x[0] = annotXCoord + featureWidth / 2;
                  y[0] = (featureYCoord - labelHeight / 3);

                  // bottom left
                  x[1] = annotXCoord;
                  y[1] = (featureYCoord);

                  // bottom right
                  x[2] = annotXCoord + featureWidth;
                  y[2] = y[1];

                  startTriangle = new Polygon(x, y, x.length);
                }
                // check if bottom is extending past overlap segment
                if ((!inverted && annot.getStop() > overlapBox.getSegment().getDrawingStop())
                    || (inverted && annot.getStart() < overlapBox.getSegment().getDrawingStop()))
                {
                  int[] x = new int[3];
                  int[] y = new int[3];

                  // bottom point
                  x[0] = annotXCoord + featureWidth / 2;
                  y[0] = (featureYCoord) + (featureHeight) + (labelHeight / 3);

                  // top left
                  x[1] = annotXCoord;
                  y[1] = (featureYCoord) + (featureHeight);

                  // top right
                  x[2] = annotXCoord + featureWidth;
                  y[2] = y[1];

                  stopTriangle = new Polygon(x, y, x.length);
                }

                if (startTriangle != null)
                  g2d.fillPolygon(startTriangle);
                if (stopTriangle != null)
                  g2d.fillPolygon(stopTriangle);

                if (overlapBox.isAnnotLabelVisible(annot))
                  g2d.setComposite(compNorm);

                // draw the annotation rectangle
                g2d.fillRect(annotXCoord, featureYCoord, featureWidth, featureHeight);

                // draw connecting lines for the spotlight
                if (annot == spotlight)
                {
                  g2d.drawLine(annotXCoord + featureWidth,
                      featureYCoord,
                      getDisplayMapXCoord(overlapBoxSegment.getParent()) + overlapBoxSegment.getParent().getFeatureColumnsWidth(mainGUI.getOptions()),
                      (int)(getAnnotationStartCoord(overlapBoxSegment, spotlight)));
                  g2d.drawLine(annotXCoord + featureWidth,
                      featureYCoord + featureHeight - 1,
                      getDisplayMapXCoord(overlapBoxSegment.getParent()) + overlapBoxSegment.getParent().getFeatureColumnsWidth(mainGUI.getOptions()),
                      (int)(getAnnotationStopCoord(overlapBoxSegment, spotlight)));
                }
              }

              // adjust the x coordinate
              annotXCoord += featureWidth + betweenAnnotSpacing;
            }

            g2d.setComposite(compNorm);
          }
        }
      }
      timing = (System.nanoTime() - start);
      //addTiming("MapNavigator - paintComponent - Draw Overlap Box", timing);
      //logger.debug("Drew overlapes box in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing overlapBox

      //
      // Draw "Dragged" Map
      //
      start = System.nanoTime();
      if (titleDragPoint != null && selection.getMap() != null)
      {
        // Draw segments
        int horizontalPosition = titleDragPoint - segmentWidth / 2;

        for (DisplaySegment segment : selection.getMap().getSegments())
        {
          int xCoordinate = horizontalPosition + 1;
          double yCoordinate = getSegmentYCoord(segment);
          int width = segmentWidth - 1;
          double height = getSegmentHeight(segment) - 1;

          // If not in clip, don't draw
          if (!g2d.hitClip(xCoordinate, (int)(yCoordinate), width, (int)(height) + 1)) continue;

          // Resize rectangle drawn, no need to redraw the whole thing
          //   Check the Y-Axis
          if (clip.y + clip.height < yCoordinate + height && yCoordinate >= clip.y)
          {
            height = yCoordinate - clip.y + clip.height;
          }
          if (yCoordinate + height < clip.y + clip.height && yCoordinate <= clip.y)
          {
            height -= clip.y - yCoordinate;
            yCoordinate = clip.y;
          }

          Composite comp = g2d.getComposite();
          g2d.setComposite(makeComposite(0.1F));
          g2d.setPaint(Color.BLUE.darker());
          g2d.fillRect(xCoordinate, (int)(yCoordinate), width, (int)(height) + 1);

          g2d.setComposite(comp);
          g2d.setPaint(Color.BLUE.darker());
          g2d.setStroke(new BasicStroke(1));
          g2d.drawRect(xCoordinate, (int)(yCoordinate), width, (int)(height) + 1);
        }
      }
      timing = (System.nanoTime() - start);
      //addTiming("MapNavigator - paintComponent - Draw Dragged Map", timing);
      //logger.debug("Drew dragged map in: " + (System.nanoTime() - start) + "ms");
      //////////////////////////// end of drawing dragged map
    }
    finally
    {
      if (lock.isHeldByCurrentThread())
        lock.unlock();
    }
    calculateMaxWatermarkWidth();
    long timing = (System.nanoTime() - totalStart);
    addTiming("MapNavigator - paintComponent", timing);
  }

  /**
   * <p>Opens the user's default browser and displays the relevant webpage
   * from the given {@link Annotation} object's source.<p>
   *
   * @param annotation
   *   {@link Annotation} to display information about in a web browser
   */
  public void openAnnotationURL(Annotation a)
  {
    try
    {
      openObjectURL(a.getURL());
    }
    catch (SQLException e)
    {
      logger.debug("Unable to get annotation url for: "
          + a.getName() + " "
          + a.getChromosome().getName() + " "
          + a.getChromosome().getMap().getName());
    }
  }

  /**
   * <p>Opens the user's default browser and displays the HomoloGene webpage
   * for the given {@link Annotation} object's HomoloGene ID.<p>
   *
   * @param annotation
   *   {@link Annotation} to display information about in a web browser
   */
  public void openHomoloGeneURL(Annotation a)
  {
    try
    {
      openObjectURL(a.getHomoloGeneURL());
    }
    catch (SQLException e)
    {
      logger.debug("Unable to get annotation url for: "
          + a.getName() + " "
          + a.getChromosome().getName() + " "
          + a.getChromosome().getMap().getName());
    }
  }

  /**
   * <p>Opens the user's default browser and displays the relevant webpage
   * from the given {@link Chromosome} object's source.<p>
   *
   * @param c
   *   {@link Chromosome} to display information about in a web browser
   */
  public void openChromosomeURL(Chromosome c)
  {
    try
    {
      openObjectURL(c.getURL());
    }
    catch (SQLException e)
    {
      logger.debug("Unable to get chromosome url for: "
          + c.getName() + " "
          + c.getMap().getName());
    }
  }

  /**
   * <p>If the {@link SelectionInterval} is visible and double clicked, a link
   * will open in the user's web browser and show them the interval they have
   * selected in more detail.</p>
   *
   * @param i
   *   Open the URL for this {@link SelectionInterval}
   */
  private void openIntervalURL(SelectionInterval i)
  {
    try
    {
      openObjectURL(i.getURL());
    }
    catch (SQLException e)
    {
      logger.debug("Unable to get interval url for: "
          + i.getSegment().getChromosome().getName() + " "
          + i.getSegment().getChromosome().getMap().getName());
    }
  }

  /**
   * <p>If a {@link DisplayMap} is double clicked, then information
   * about that {@link DisplayMap} will be displayed in a web browser.<p>
   *
   * @param m
   *   {@link MapData} to display information about in a web browser
   */
  public void openMapURL(MapData m)
  {
    try
    {
      openObjectURL(m.getURL());
    }
    catch (SQLException e)
    {
      logger.debug("Unable to get map url for: " + m.getName());
    }
  }

  /**
   * <p>Helper method that actually opens the URL for any of the objects
   * that may need to have an URL assigned to them.</p>
   *
   * @param url
   *   {@link String} representation of the URL to open in the user's
   *   web browser.
   */
  private void openObjectURL(String url)
  {
    boolean success = false;
    try
    {
      if (url != null) success = Util.openURL(url);
    }
    catch (Exception e)
    {
      logger.error("Problem fetching URL data from database: " + e);
    }
    finally
    {
      if (!success && url != null)
      {
        JOptionPane.showMessageDialog(mainGUI, "Unable to launch your default web browser.\n" +
            "Please enter the following URL into your web browser\n" +
            "for more information about this annotation:\n\n" +
            url,
            "Unable to Launch Default Browser", JOptionPane.INFORMATION_MESSAGE);
      }
      else if (!success)
      {
        JOptionPane.showMessageDialog(mainGUI, "Unable to determine the external URL for this\n" +
            "piece of annotation.",
            "Missing External Link", JOptionPane.INFORMATION_MESSAGE);
      }
    }
  }


  /**
   * <p>Check if the {@link Annotation} is visible based on
   * preferences set by the user and based on the {@link Annotation} the user
   * has decided to hide or show during the use of the program.</p>
   *
   * @param annotation
   *   {@link Annotation} to determine whether visible or not.
   * @return
   *   True - {@link Annotation} is visible
   *   False - {@link Annotation} is NOT visible
   */
  public boolean isAnnotationVisible(Annotation annotation)
  {
    if (!hiddenAnnotation.contains(annotation)
        && mainGUI.getOptions().isShown(annotation.getAnnotationSet().getType())
        && !hiddenTypes.contains(annotation.getAnnotationSet().getType()))
    {
      return true;
    }
    else
      return false;
  }

  /**
   * <p>Check of a {@link SelectionInterval} was selected by the user. If the
   * user double clicked the interval, interval information is displayed. If
   * the use single clicked, the {@link DisplaySegment} for the interval is
   * selected.</p>
   *
   * @param selectedInterval
   *   Check if {@link SelectionInterval} contains the {@link MouseEvent}
   * @param e
   *   {@link MouseEvent} created by the user.
   * @return
   *   true - if the interval contains the event
   *   false - if the interval does NOT contain the event
   */
  private boolean isIntervalSelected(SelectionInterval selectedInterval, MouseEvent e)
  {
    if (!selectedInterval.getSegment().getParent().isVisible()) return false;

    Rectangle interval = selectedInterval.getRectangle(mainGUI.getOptions());

    if (interval.contains(new Point(e.getX(), e.getY())))
    {
      if (e.getClickCount() == 2 && selectedInterval.getSegment().getParent().getMap().getTypeString().equals("Genomic")
          && e.getID() == MouseEvent.MOUSE_RELEASED)
        openIntervalURL(selectedInterval);

      // Create way to check if 'x' was clicked
      String close = "x";
      FontMetrics metrics = this.getFontMetrics(VCMap.labelFont);
      int stringWidth = metrics.stringWidth(close);
      Rectangle closeBounds = new Rectangle(interval.x + interval.width - stringWidth - 1,
          interval.y + 5,
          stringWidth + 1,
          metrics.getHeight());

      // Adjust y if the top of the interval isn't in the mapNavigator
      if (closeBounds.y < this.getY() * -1)
      {
        closeBounds.y = this.getY() * -1 + 5;
      }

      DisplaySegment segment = selectedInterval.getSegment();
      if (selection.getSegments().contains(segment))
      {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown())
        {
          selection.removeSegment(segment);
        }
        else
        {
          selection.setMap(segment.getParent());
          selection.setSegment(segment);
        }
      }
      else
      {
        if (e.isControlDown() || e.isAltDown() || e.isMetaDown())
        {
          selection.addSegment(segment);
        }
        else
        {
          selection.setMap(segment.getParent());
          selection.setSegment(segment);
        }
      }

      repaint();

      return true;
    }

    return false;
  }

  /**
   * <p>Determines if a user clicked on {@link Annotation} drawn
   * on the screen based on the x & y coordinates in the {@link MouseEvent}.
   * If {@link Annotation} was selected, the proper methods are called
   * to update the current annotation selection.</p>
   *
   * @param displayMap
   *   {@link Annotation} of this {@link DisplayMap} are checked
   *   to see if they were clicked on
   * @param e
   *   {@link MouseEvent} to know where the mouse was clicked, how
   *   many times the mouse was clicked and whether CTRL or SHIFT
   *   was pressed while clicked
   * @return
   *   True - if a feature was clicked on
   *   False - if a feature was NOT clicked on
   */
  private boolean isMarkerSelected(DisplayMap displayMap, MouseEvent e)
  {
    long start = System.nanoTime();
    // If displayMap isn't visible, there are not features displayed
    if (!displayMap.isVisible()) return false;

    DisplayAnnotation displayAnnotation = null;
    DisplaySegment featureSegment = null;

    // Determine the font height
    FontMetrics metrics = this.getFontMetrics(VCMap.labelFont);
    int labelHeight = metrics.getHeight();

    // Misc Variables
    int leftBound = getDisplayMapXCoord(displayMap);
    int rightBound = leftBound + displayMap.getFeatureColumnsWidth(mainGUI.getOptions());
    boolean breakOut = false;

    //
    // Did user click within the feature display area for this display map?
    //
    if (leftBound <= e.getX() && e.getX() <= rightBound)
    {
      //
      // Check other features
      //
      for (DisplaySegment segment : displayMap.getSegments())
      {
        double segStartBound = getSegmentYCoord(segment) - labelHeight / 2;
        double segStopBound = segStartBound + getSegmentHeight(segment) + labelHeight / 2;

        //
        // Check if the click happened within the regions of the segment
        //
        if (segStartBound <= e.getY() && e.getY() <= segStopBound)
        {
          // Set what segment may have had annotation selected for
          featureSegment = segment;

          rightBound = getDisplayMapXCoord(displayMap);

          // Check each type
          for (AnnotationSet set : displayMap.getShownSets())
          {
            // If type is hidden it can't be clicked on
            if (hiddenTypes.contains(set) || !mainGUI.getOptions().isShown(set.getType()))
              continue;

            leftBound = rightBound;
            rightBound += mainGUI.getOptions().getIntOption("featureLabelColumnWidth");

            // Check if a label was clicked
            if (leftBound <= e.getX() && e.getX() <= rightBound)
            {
              // Determine y coordinates for all visible features
              for (DisplayAnnotation displayAnnot : segment.getDisplayAnnotation(set, e.getY() - labelHeight / 2, e.getY() + labelHeight / 2))
              {

                int annotLabelTop = displayAnnot.getLabelYCoord() - labelHeight / 2;
                int annotLabelBot = displayAnnot.getLabelYCoord() + labelHeight / 2;
                int annotLabelRight = rightBound;
                int annotLabelLeft = annotLabelRight - displayAnnot.getLabelWidth();

                if (annotLabelTop <= e.getY() && e.getY() <= annotLabelBot
                    && annotLabelLeft <= e.getX() && e.getX() <= annotLabelRight)
                {
                  displayAnnotation = displayAnnot;
                  break;
                }
              }

              breakOut = true;
              break;
            }

            leftBound = rightBound;
            rightBound += mainGUI.getOptions().getIntOption("featureColumnWidth") * segment.getFeatureColumns(set);

            // Check if a feature rectangle was clicked
            if (leftBound <= e.getX() && e.getX() <= rightBound)
            {
              for (DisplayAnnotation displayAnnot : segment.getDisplayAnnotation(set))
              {
                int featureLeft = leftBound + mainGUI.getOptions().getIntOption("featureColumnWidth") * displayAnnot.getColumn()
                + mainGUI.getOptions().getIntOption("featureColumnWidth") / 4;
                int featureRight = featureLeft + mainGUI.getOptions().getIntOption("featureColumnWidth") / 2;

                if (featureLeft <= e.getX() && e.getX() <= featureRight)
                {
                  for (DisplayAnnotation.DAMember member : displayAnnot.getAnnotation())
                  {
                    int featureTop = (int)(getAnnotationStartCoord(segment, member.getAnnotation()));
                    int featureBottom = (int)(getAnnotationStopCoord(segment, member.getAnnotation()));

                    if (featureTop <= e.getY() && e.getY() <= featureBottom)
                    {
                      displayAnnotation = displayAnnot;
                      breakOut = true;
                      break;
                    }
                  }

                  if (breakOut) break;
                }
              }

              breakOut = true;
              break;
            }

            if (breakOut) break;
          }
        }

        if (breakOut) break;
      }
    }

    //
    // Respond appropriately to click
    //
    if (displayAnnotation == null)
      return false;
    else if (displayAnnotation.getAnnotation().size() == 1)
    {
      // close an overlap box if there is one on the same map
      OverlapBox removeBox = null;

      for (OverlapBox overlapBox : overlapBoxes)
      {
        if (displayMap.equals(overlapBox.getDisplayMap()) && overlapBox.isOpen())
        {
          removeBox = overlapBox;
          overlapBox.close();
          overlapBox.resizeMapNavigator();
        }
      }

      overlapBoxes.remove(removeBox);

      Annotation selectedAnnot = displayAnnotation.getStartAnnot();

      // Check is double click to open details dialog
      if (e.getClickCount() == 2 && e.getID() == MouseEvent.MOUSE_RELEASED)
      {
        addSelectedAnnotation(null, selectedAnnot, true, false);
        DetailsDialog.showDetailsDialog(mainGUI);
        Tutorial.updatePage("ViewDetails");
        return true;
      }

      // Update the selected annotation appropriately
      if (selection.contains(selectedAnnot) && (e.isControlDown() || e.isShiftDown() || e.isMetaDown()))
        removeSelectedAnnotation(selectedAnnot, false);
      else if (selection.contains(selectedAnnot))
        addSelectedAnnotation(featureSegment, selectedAnnot, false, false);
      else
        addSelectedAnnotation(featureSegment, selectedAnnot, e.isControlDown() || e.isShiftDown() || e.isMetaDown(), false);

      repaint();
    }
    else
    {
      // close an overlap box if there is one on the same map
      OverlapBox removeBox = null;

      for (OverlapBox overlapBox : overlapBoxes)
      {
        if (displayMap.equals(overlapBox.getDisplayMap()) && overlapBox.isOpen())
        {
          removeBox = overlapBox;
          overlapBox.close();
          overlapBox.resizeMapNavigator();
        }
      }

      overlapBoxes.remove(removeBox);

      // Create new overlap box, multiple annotation selected
      long begin = System.nanoTime();
      OverlapBox overlapBox = new OverlapBox(displayAnnotation.getAnnotationAsVector(), featureSegment, this.getFontMetrics(VCMap.labelFont));
      long timing = (System.nanoTime() - begin);
      addTiming("MapNavigator - isMarkerSelected - new OverlapBox", timing);
      //logger.debug("Constructing the overlap box took: " + (System.nanoTime() - begin) + "ms");
      begin = System.nanoTime();
      overlapBox.resizeMapNavigator();
      overlapBoxes.add(overlapBox);
      timing = (System.nanoTime() - begin);
      addTiming("MapNavigator - isMarkerSelected - Resize MapNavigator", timing);
      //logger.debug("Resizing the mapNavigator for the overlap box took: " + (System.nanoTime() - begin) + "ms");
    }

    // annotation is selected
    Tutorial.updatePage("selectAnnotation");
    addTiming("MapNavigator - isMarkerSelected", System.nanoTime() - start);
    return true;
  }

  /**
   * <p>Determine if the {@link DisplaySegment}s contained in
   * {@link DisplayMap} were clicked on by the user based on the
   * x & y coordinates stored in the {@link MouseEvent}. If a segment
   * was selected the method calls the appropriate methods to change
   * what is currently selected.</p>
   *
   * @param displayMap
   *   {@link DisplaySegment}s in this {@link DisplayMap} will be checked
   *   to see if they were clicked on.
   * @param e
   *   {@link MouseEvent} to know where the mouse was clicked
   * @return
   *   True - if a segment was clicked on
   *   False - if a segment was NOT clicked on
   */
  private boolean isSegmentSelected(DisplayMap displayMap, MouseEvent e)
  {
    // Set the left and right bound parameters
    int leftBound = getDisplayMapXCoord(displayMap);
    int rightBound = leftBound;

    if (displayMap.isVisible())
    {
      leftBound += displayMap.getFeatureColumnsWidth(mainGUI.getOptions());
      rightBound = leftBound + mainGUI.getOptions().getIntOption("segmentWidth");
    }
    else
    {
      rightBound += mainGUI.getOptions().getIntOption("hiddenMapWidth");
    }

    if (leftBound <= e.getX() && e.getX() <= rightBound)
    {
      // If invisible, no specific segments are being drawn only a solid line
      if (!displayMap.isVisible())
      {
        if (e.getClickCount() == 2 && e.getID() == MouseEvent.MOUSE_PRESSED)
        {
          selection.setMap(displayMap);
          showSelectedMap();
        }
        else
        {
          selection.setMap(displayMap);
        }

        mousePressedEvent = null;

        updateStatusBar();
        return true;
      }

      // Determine what segment is selected
      for (DisplaySegment segment : displayMap.getSegments())
      {
        double segYCoord = getSegmentYCoord(segment);
        double segHeight = getSegmentHeight(segment);

        if (segYCoord <= e.getY() && e.getY() <= segYCoord + segHeight)
        {
          segmentPressed = segment;
          return true;
        }
      }
    }

    return false;
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
   */
  public void mousePressed(MouseEvent e)
  {
    long beginMP = System.nanoTime();
    logger.debug("Mouse Pressed");

    // Store mouse event for other methods
    mousePressedEvent = e;

    long begin;
    boolean checkMaps = true;

    if (overlapBoxes.size() > 0)
    {
      begin = System.nanoTime();
      for (OverlapBox overlapBox : overlapBoxes)
      {
        if (overlapBox.contains(e))
        {
          overlapBoxPressed = overlapBox;
          featureSelectionMap = null;
          checkMaps = false;
          break;
        }
      } // End of OverlapBox Loop
      long timing = (System.nanoTime() - begin);
      addTiming("MapNavigator - mousePressed - Check Overlap Box Pressed", timing);
      //logger.debug("Checked OverlapBoxes in: " + (System.nanoTime() - begin) + "ms");
    }

    if (checkMaps) // No OverlapBoxes present
    {
      for (DisplayMap displayMap : displayMaps)
      {
        // Check if in a segment was selected
        begin = System.nanoTime();
        if (isSegmentSelected(displayMap, e))
        {
          break;
        }
        //logger.debug("Checked segments in: " + (System.nanoTime() - begin) + "ms");

        // Check if clicked within feature area
        int xCoord = getDisplayMapXCoord(displayMap);
        if (xCoord <= e.getX() && e.getX() <= xCoord + displayMap.getFeatureColumnsWidth(mainGUI.getOptions()))
        {
          featureSelectionMap = displayMap;

          break;
        }
      }
    }
    addTiming("MapNavigator - mousePressed", System.nanoTime() - beginMP);
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
   */
  public void mouseDragged(MouseEvent e)
  {
    if (featureSelectionMap != null && mousePressedEvent != null)
    {
      //
      // Modifying feature selection box
      //
      if (!mousePressedEvent.isControlDown() && !mousePressedEvent.isShiftDown() && !mousePressedEvent.isMetaDown()
          && featureSelectionBoxes.size() == 0 && !selection.isEmpty())
        clearSelection();

      //
      // Selecting multiple features
      //
      int minX, minY, maxX, maxY;

      if (mousePressedEvent.getPoint().x < e.getPoint().x)
      {
        minX = mousePressedEvent.getPoint().x;
        maxX = e.getPoint().x;
      }
      else
      {
        minX = e.getPoint().x;
        maxX = mousePressedEvent.getPoint().x;
      }
      if (mousePressedEvent.getPoint().y < e.getPoint().y)
      {
        minY = mousePressedEvent.getPoint().y;
        maxY = e.getPoint().y;
      }
      else
      {
        minY = e.getPoint().y;
        maxY = mousePressedEvent.getPoint().y;
      }

      // Ensure we haven't made the selection box too tall
      if (maxY > this.getHeight() - 1)
        maxY = this.getHeight() - 1;
      if (minY < 0)
        minY = 0;

      int mapXCoord = getDisplayMapXCoord(featureSelectionMap);

      int clipY = -1;
      int clipHeight = -1;

      if (featureSelectionBoxes.size() == 0)
      {
        clipY = minY;
        clipHeight = maxY - minY;
      }
      else
      {
        int fsbY = featureSelectionBoxes.firstElement().y;
        int fsbHeight = featureSelectionBoxes.firstElement().height;

        clipY = Math.min(minY, fsbY);
        clipHeight = Math.max(fsbY + fsbHeight, maxY) - clipY;
      }

      // Set new feature selection boxes
      featureSelectionBoxes.clear();
      //////////////////
      // Other Variables
      int labelColumnWidth = mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
      int featureColumnWidth = mainGUI.getOptions().getIntOption("featureColumnWidth");
      int featureColumns = 0;

      int typeLabelXCoord = mapXCoord;

      for (AnnotationSet set : featureSelectionMap.getShownSets())
      {
        if (mainGUI.getOptions().isShown(set.getType()) && !hiddenTypes.contains(set))
        {
          // Calculate feature columns fixes mis-alignment of labels
          int typeFeatureColumns = featureSelectionMap.getFeatureColumns(set);

          int typeColumnStart = typeLabelXCoord + (featureColumnWidth * featureColumns);
          int typeColumnStop = typeLabelXCoord + labelColumnWidth + (featureColumnWidth * (featureColumns + typeFeatureColumns));

          if ((minX >= typeColumnStart && minX <= typeColumnStop) || (maxX >= typeColumnStart && maxX <= typeColumnStop) ||
              (minX < typeColumnStart && maxX > typeColumnStop))
          {
            featureSelectionBoxes.add(new Rectangle(typeColumnStart, minY, typeColumnStop - typeColumnStart, maxY - minY));
          }

          featureColumns += typeFeatureColumns;
          typeLabelXCoord += labelColumnWidth;
        }
      }
      /////////////////////
      featureColumns = 0;
      typeLabelXCoord = mapXCoord;

      for (AnnotationSet set : featureSelectionMap.getShownSets())
      {
        // Calculate feature columns fixes mis-alignment of labels
        int typeFeatureColumns = featureSelectionMap.getFeatureColumns(set);
        int typeColumnStart = typeLabelXCoord + (featureColumnWidth * featureColumns);
        int typeColumnStop = typeLabelXCoord + labelColumnWidth + (featureColumnWidth * (featureColumns + typeFeatureColumns));

        repaint(typeColumnStart, clipY, typeColumnStop - typeColumnStart + 1, clipHeight + 1);

        featureColumns += typeFeatureColumns;

        typeLabelXCoord += labelColumnWidth;
      }
    }
    else if (overlapBoxPressed != null && mousePressedEvent != null)
    {
      //
      // Selecting in OverlapBox
      //
      int OVERLAP_SELECTION_BUFFER = 2;

      int boxX = (int)overlapBoxPressed.getX();
      int boxY = (int)overlapBoxPressed.getY();
      int boxHeight = (int)overlapBoxPressed.getHeight();
      int xCoordinate, width;
      // Remove spotlight if dragged
      overlapBoxPressed.spotlight = null;

      if (overlapBoxPressed.getComponent(mousePressedEvent) == OverlapBox.LABEL_COLUMN)
      {
        xCoordinate = (int)overlapBoxPressed.getX();
        width = overlapBoxPressed.getLabelColumnWidth();
      }
      else if (overlapBoxPressed.getComponent(mousePressedEvent) == OverlapBox.ANNOTATION_COLUMN)
      {
        xCoordinate = (int)overlapBoxPressed.getX() + overlapBoxPressed.getLabelColumnWidth();
        width = (int)overlapBoxPressed.getWidth() - overlapBoxPressed.getLabelColumnWidth();
      }
      else
      {
        overlapBoxPressed = null;
        return;
      }
      int minY, maxY;

      if (mousePressedEvent.getPoint().y < e.getPoint().y)
      {
        minY = mousePressedEvent.getPoint().y;
        maxY = e.getPoint().y;
      }
      else
      {
        minY = e.getPoint().y;
        maxY = mousePressedEvent.getPoint().y;
      }

      // Ensure we haven't made the selection box too tall
      if (maxY > boxY + boxHeight - overlapBoxPressed.buttonHeight - OVERLAP_SELECTION_BUFFER)
        maxY = boxY + boxHeight - overlapBoxPressed.buttonHeight - OVERLAP_SELECTION_BUFFER;
      if (minY < boxY + (overlapBoxPressed.buttonHeight * 2) + OVERLAP_SELECTION_BUFFER)
        minY = boxY + (overlapBoxPressed.buttonHeight * 2) + OVERLAP_SELECTION_BUFFER;


      if (!mousePressedEvent.isControlDown() && !mousePressedEvent.isShiftDown() && !mousePressedEvent.isMetaDown()
          && featureSelectionBoxes.size() == 0 && !selection.isEmpty())
        clearSelection();

      // Calculate distance from left side of OverlapBox to the display segment
      // Done by totaling up
      int segmentStart = getDisplayMapXCoord(overlapBoxPressed.getDisplayMap());

      for (AnnotationSet set : overlapBoxPressed.getDisplayMap().getShownSets())
      {
        if (mainGUI.getOptions().isShown(set.getType()) && !hiddenTypes.contains(set))
        {
          segmentStart += overlapBoxPressed.getDisplayMap().getFeatureColumns(set) *
          mainGUI.getOptions().getIntOption("featureColumnWidth");
          segmentStart += mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
        }
      }
      int clipWidth = segmentStart - boxX + 1;

      //
      // Selecting multiple features
      //
      overlapDragRect = new Rectangle(xCoordinate + OVERLAP_SELECTION_BUFFER, minY, width - (2 * OVERLAP_SELECTION_BUFFER), maxY - minY);
      repaint(boxX, boxY, clipWidth, boxHeight);
    }
    else if (segmentPressed != null && mousePressedEvent != null)
    {
      //
      // Creating a selection interval on a segment
      //
      boolean selectionCleared = false;

      SelectionInterval mapInterval = new SelectionInterval(this);
      mapInterval.setInterval(segmentPressed, mousePressedEvent.getY());
      mapInterval.setEnd(e.getY());

      if (!mousePressedEvent.isShiftDown() && !mousePressedEvent.isControlDown() && !mousePressedEvent.isMetaDown()
          && (selection.getIntervals().size() > 0 || selection.getSegments().size() > 0 || selection.getAnnotation().size() > 0))
      {
        selection.clearIntervals();
        selection.clearAnnotation();
        selection.clearSegments();

        selectionCleared = true;
      }

      selection.addInterval(mapInterval);
      selection.setInterval(mapInterval);

      if (!selectionCleared)
      {
        Rectangle interval = selection.getInterval().getRectangle(mainGUI.getOptions());
        repaint(interval.x - 1, 0, interval.width + 2, headerHeight + drawingHeight);
      }
      else
      {
        repaint();
      }

      segmentPressed = null;
      mousePressedEvent = null;
    }
    else if (selection.getInterval() != null)
    {
      //
      // Modifying a selection interval
      //
      if (selection.getInterval().isBeingSet())
      {
        selection.getInterval().setEnd(e.getY());

        Rectangle interval = selection.getInterval().getRectangle(mainGUI.getOptions());
        repaint(interval.x - 1, 0, interval.width + 2, headerHeight + drawingHeight);
      }
    }
    else
    {
      //
      // None of the above, delete stored mouse event
      //
      mousePressedEvent = null;
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
   */
  public void mouseReleased(MouseEvent e)
  {
    long beginMR = System.nanoTime();
    logger.debug("Mouse Released");

    if (featureSelectionMap != null && featureSelectionBoxes.size() > 0)
    {
      this.setCursor(new Cursor(Cursor.WAIT_CURSOR));
      //
      // Done selected multiple features
      //
      //////////////////
      // Other Variables
      int labelColumnWidth = mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
      int featureColumnWidth = mainGUI.getOptions().getIntOption("featureColumnWidth");
      int mapXCoord = getDisplayMapXCoord(featureSelectionMap);
      int typeLabelXCoord = mapXCoord;
      int featureColumns = 0;

      Vector<AnnotationSet> sets = new Vector<AnnotationSet>();
      int leftBound = featureSelectionBoxes.firstElement().x;
      int rightBound = featureSelectionBoxes.lastElement().x + featureSelectionBoxes.lastElement().width;

      for (AnnotationSet set : featureSelectionMap.getShownSets())
      {
        // Calculate feature columns fixes mis-alignment of labels
        int typeFeatureColumns = featureSelectionMap.getFeatureColumns(set);
        int typeColumnStart = typeLabelXCoord + (featureColumnWidth * featureColumns);
        int typeColumnStop = typeLabelXCoord + labelColumnWidth + (featureColumnWidth * (featureColumns + typeFeatureColumns));

        if (leftBound <= typeColumnStart && typeColumnStop <= rightBound)
          if (!sets.contains(set))
            sets.add(set);

        featureColumns += typeFeatureColumns;

        typeLabelXCoord += labelColumnWidth;
      }

      int start = featureSelectionBoxes.firstElement().y;
      int stop = featureSelectionBoxes.firstElement().y + featureSelectionBoxes.firstElement().height;

      logger.debug("Determine all the annotation selected by multi-annotation selection");
      HashMap<DisplaySegment, TreeSet<Annotation>> selectedAnnot = new HashMap<DisplaySegment, TreeSet<Annotation>>();
      for (AnnotationSet set : sets)
      {
        for (DisplaySegment segment : featureSelectionMap.getSegments())
        {
          TreeSet<Annotation> segAnnot = selectedAnnot.get(segment);
          if (segAnnot == null)
            segAnnot = new TreeSet<Annotation>();

          for (DisplayAnnotation da : segment.getDisplayAnnotation(set, start, stop))
            for (DisplayAnnotation.DAMember member : da.getAnnotation())
              segAnnot.add(member.getAnnotation());

          selectedAnnot.put(segment, segAnnot);
        }
      }
      logger.debug("Done determining annotation, now add all annotation to selection");
      for (DisplaySegment segment : selectedAnnot.keySet())
        selection.addAllAnnotation(segment, selectedAnnot.get(segment)); //Calls the Selection information
      logger.debug("Done adding annotation to selection, now show updates");

      updateStatusBar();
      featureSelectionMap = null;
      featureSelectionBoxes.clear();
      mousePressedEvent = null;
      repaint();
      logger.debug("Done showing updates");
      this.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
    }
    else if (segmentPressed != null && mousePressedEvent != null)
    {
      //
      // Check if segment was selected
      //
      if (e.getClickCount() == 2)
        openChromosomeURL(segmentPressed.getChromosome());

      if (selection.getSegments().contains(segmentPressed))
      {
        if (mousePressedEvent.isControlDown() || mousePressedEvent.isAltDown() || mousePressedEvent.isMetaDown())
        {
          selection.removeSegment(segmentPressed);
        }
        else
        {
          selection.setMap(segmentPressed.getParent());
          selection.setSegment(segmentPressed);
          Tutorial.updatePage("selectSegment");
        }
      }
      else
      {
        if (mousePressedEvent.isControlDown() || mousePressedEvent.isAltDown() || mousePressedEvent.isMetaDown())
        {
          selection.addSegment(segmentPressed);
          Tutorial.updatePage("selectSegment");
        }
        else
        {
          selection.setMap(segmentPressed.getParent());
          selection.setSegment(segmentPressed);
          Tutorial.updatePage("selectSegment");
        }
      }

      segmentPressed = null;
      updateStatusBar();
      repaint();
    }
    else if (selection.getInterval() != null)
    {
      if (selection.getInterval().isBeingSet())
      {
        //
        // Done modifying selection interval on segment
        //
        Tutorial.updatePage("selectInterval");
        selection.getInterval().stopSetting();
        selection.setInterval(null);
        repaint();
      }
    }
    else if (mousePressedEvent != null)
    {
      //
      // Check if overlapbox, selection interval or annotation was selected
      //
      boolean isWhiteSpace = true;
      featureSelectionMap = null;
      logger.debug("Mouse Pressed");

      // Check is interval was selected
      long begin = System.nanoTime();
      for (SelectionInterval selInterval : selection.getIntervals())
      {
        if (isIntervalSelected(selInterval, e))
        {
          isWhiteSpace = false;
          break;
        }
      }
      long timing = (System.nanoTime() - begin);
      addTiming("MapNavigator - mouseReleased - Check Selection Interval", timing);
      //logger.debug("Checked selection intervals in: " + (System.nanoTime() - begin) + "ms");

      //
      // Check selection interval in overlap box
      //
      if (overlapDragRect != null && overlapBoxPressed != null && mousePressedEvent != null)
      {
        if (overlapBoxPressed.getComponent(mousePressedEvent) == OverlapBox.LABEL_COLUMN)
        {
          //
          // Selection of labels in OverlapBox
          //
          for (Annotation annot : overlapBoxPressed.getAnnotation())
          {
            FontMetrics metrics = overlapBoxPressed.getFontMetrics();
            double labelYCoord = overlapBoxPressed.getAnnotLabelYCoord(annot);
            double labelCenter = ((labelYCoord - metrics.getAscent() - metrics.getLeading()) +
                (labelYCoord + metrics.getDescent())) / 2;
            int selectionStart = overlapDragRect.y;
            int selectionStop = overlapDragRect.y + overlapDragRect.height;

            if (labelCenter >= selectionStart && labelCenter <= selectionStop)
            {
              addSelectedAnnotation(overlapBoxPressed.getSegment(), annot, true, false);
            }
          }
        }
        else if (overlapBoxPressed.getComponent(mousePressedEvent) == OverlapBox.ANNOTATION_COLUMN)
        {
          //
          // Selection of annotation in OverlapBox
          //
          for (Annotation annot : overlapBoxPressed.getAnnotation())
          {
            double annotCenter = overlapBoxPressed.getAnnotYCoord(annot) + (overlapBoxPressed.getAnnotHeight(annot) / 2);
            int selectionStart = overlapDragRect.y;
            int selectionStop = overlapDragRect.y + overlapDragRect.height;

            if (annotCenter >= selectionStart && annotCenter <= selectionStop)
            {
              addSelectedAnnotation(overlapBoxPressed.getSegment(), annot, true, false);
            }
          }
        }

        // Clear variables
        overlapDragRect = null;
        isWhiteSpace = false;
        repaint();
      }
      // Check overlapBox
      if (isWhiteSpace)
      {
        //
        // check overlap box first if there is one
        //
        if (overlapBoxes.size() > 0)
        {
          begin = System.nanoTime();
          for (OverlapBox overlapBox : overlapBoxes)
          {
            if (overlapBox.handleEvent(e))
            {
              isWhiteSpace = false;
              break;
            }
          }
          timing = (System.nanoTime() - begin);
          addTiming("MapNavigator - mouseReleased - Check Overlap Box Released", timing);
          //logger.debug("Checked overlapBox in: " + (System.nanoTime() - begin) + "ms");

          if (isWhiteSpace)
          {
            begin = System.nanoTime();
            for (DisplayMap displayMap : displayMaps)
            {
              // Check if in a feature width region was clicked on
              if (isMarkerSelected(displayMap, e))
              {
                isWhiteSpace = false;
                break;
              }
              timing = (System.nanoTime() - begin);
              addTiming("MapNavigator - mouseReleased - Check Markers", timing);
              //logger.debug("Checked markers in: " + (System.nanoTime() - begin2) + "ms");
            }
          }
        }
        else
        {
          begin = System.nanoTime();
          for (DisplayMap displayMap : displayMaps)
          {
            // Check if in a feature width region was clicked on
            if (isMarkerSelected(displayMap, e))
            {
              isWhiteSpace = false;
              break;
            }
            timing = (System.nanoTime() - begin);
            addTiming("MapNavigator - mouseReleased - Check Markers", timing);
            //logger.debug("Checked markers in: " + (System.nanoTime() - begin) + "ns");
          }

          // check overlap box
          if (isWhiteSpace)
          {
            begin = System.nanoTime();
            for (OverlapBox overlapBox : overlapBoxes)
            {
              if (overlapBox.handleEvent(e))
              {
                isWhiteSpace = false;
                timing = (System.nanoTime() - begin);
                addTiming("MapNavigator - mouseReleased - Check Overlap Box", timing);
                //logger.debug("Checked overlapBox in: " + (System.nanoTime() - begin) + "ns");
                break;
              }
            }
          }
        }
      }

      //
      // If true the user clicked on the white space region
      //
      if (isWhiteSpace)
      {
        // If double clicked zoom in on location
        if (e.getClickCount() == 2 && e.getID() == MouseEvent.MOUSE_RELEASED)
        {
          doubleClickZoomPos = this.getMousePosition();
          mainGUI.incrementZoomBar(); //zoom in
        }
        else if (!e.isControlDown() && !e.isShiftDown() || e.isMetaDown())
        {
          clearSelection(); // Reset all variables

          for (OverlapBox overlapBox : overlapBoxes)
          {
            if (overlapBox.isOpen())
            {
              overlapBox.close();
              overlapBox.resizeMapNavigator();
            }
          }

          overlapBoxes.clear();
        }

        // No need for repaint() here, methods above calls repaint()
      }
      else
      {
        updateStatusBar();
      }
    }

    mousePressedEvent = null;
    addTiming("MapNavigator - mouseReleased", System.nanoTime() - beginMR);
  }


  /*
   * (non-Javadoc)
   * @see java.awt.event.MouseMotionListener#mouseMoved(java.awt.event.MouseEvent)
   */
  public void mouseMoved(MouseEvent e)
  {
    // Update overlapBox
    for (OverlapBox overlapBox : overlapBoxes)
    {
      if(overlapBox.handleEvent(e))
      {
        previousSpotlight = overlapBox.getSpotlight();
        overlapBox.repaint();
        return;
      }
      else if (overlapBox.getSpotlight() == null && previousSpotlight != null && overlapBox.isOpen())
      {
        previousSpotlight = null;
        overlapBox.repaint();
      }
    }

    // Update display map if mouse is over it
    for (DisplayMap displayMap : displayMaps)
    {
      int leftBound = getDisplayMapXCoord(displayMap);
      if (displayMap.isVisible())
        leftBound += displayMap.getFeatureColumnsWidth(mainGUI.getOptions());

      int rightBound = leftBound;
      if (displayMap.isVisible())
        rightBound += mainGUI.getOptions().getIntOption("segmentWidth");
      else
        rightBound += mainGUI.getOptions().getIntOption("hiddenMapWidth");

      if (leftBound <= e.getX() && e.getX() <= rightBound)
      {
        mouseOverMap = displayMap;

        if (mouseOverMap.isVisible())
          repaint(leftBound + 2, 0, rightBound - leftBound, drawingHeight + headerHeight);
        else
          repaint();

        return;
      }
    }

    // Repaint to draw tooltip
    if (mouseOverMap != null)
    {
      int leftBound = getDisplayMapXCoord(mouseOverMap);
      if (mouseOverMap.isVisible())
        leftBound += mouseOverMap.getFeatureColumnsWidth(mainGUI.getOptions());

      int rightBound = leftBound;
      if (mouseOverMap.isVisible())
        rightBound += mainGUI.getOptions().getIntOption("segmentWidth");
      else
        rightBound += mainGUI.getOptions().getIntOption("hiddenMapWidth");

      if (mouseOverMap.isVisible())
        repaint(leftBound - 2, 0, rightBound - leftBound + 4, drawingHeight + headerHeight);
      else
        repaint();

      mouseOverMap = null;
    }
  }

  /*
   * (non-Javadoc)
   * Ensure the selection interval is the proper length if the mouse exits the
   * viewport
   * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
   */
  public void mouseExited(MouseEvent e)
  {
    if (selection.getInterval() != null)
    {
      if (selection.getInterval().isBeingSet())
      {
        JViewport vp = (JViewport)getParent();

        if (e.getY() < 0)
          selection.getInterval().setEnd(vp.getViewPosition().y);
        else
          selection.getInterval().setEnd(vp.getViewPosition().y + vp.getHeight());

        Rectangle interval = selection.getInterval().getRectangle(mainGUI.getOptions());
        repaint(interval.x - 1, 0, interval.width + 2, headerHeight + drawingHeight);
      }
    }
  }

  // Unused methods, but required
  public void mouseClicked(MouseEvent e) {}
  public void mouseEntered(MouseEvent e) {}

  /*
   * (non-Javadoc)
   * If CTRL held down while mouse wheel moved, zoom in or out.
   * Otherwise, scroll up and down
   * @see java.awt.event.MouseWheelListener#mouseWheelMoved(java.awt.event.MouseWheelEvent)
   */
  public void mouseWheelMoved(MouseWheelEvent e)
  {
    if (e.isAltDown())
    {
      if (e.getWheelRotation() > 0)
        mainGUI.incrementZoomBar();
      else
        mainGUI.decrementZoomBar();
    }
    else
    {
      JScrollPane mainScroll = (JScrollPane)getParent().getParent();
      JScrollBar vsb = mainScroll.getVerticalScrollBar();

      if (e.getWheelRotation() > 0)
        vsb.setValue(vsb.getValue() + vsb.getBlockIncrement() * 4);
      else
        vsb.setValue(vsb.getValue() - vsb.getBlockIncrement() * 4);
    }
  }

  /**
   * <p>Create an {@link AlphaComposite} to help draw objects as
   * semi-transparent.</p>
   *
   * @param alpha
   *   Sets how translucent the item drawn is. The higher the
   *   value the more opaque. Must be between 0 and 1.
   */
  private AlphaComposite makeComposite(float alpha)
  {
    int type = AlphaComposite.SRC_OVER;
    return (AlphaComposite.getInstance(type, alpha));
  }

  /**
   * Gets the Drawing Width of the navigator
   * @return the drawingWidth
   */
  public int getDrawingWidth()
  {
    return drawingWidth;
  }

  /**
   * takes the leftMarginWidth (the whitespace on the left side of the window) and sets the
   * maxWatermarkWidth to that value if it is between 100 and 200, sets it to 100 if the
   * leftMarginWidth is LESS than 100 and sets it to 200 if the leftMarginWidth is greater than 200
   */
  public void calculateMaxWatermarkWidth()
  {
    if(leftMarginWidth < 100)
      maxWatermarkWidth = 100;
    else if(leftMarginWidth < 200)
      maxWatermarkWidth = leftMarginWidth;
    else
      maxWatermarkWidth = 200;
  }

  /**
   * Returns the maxWatermarkWidth (used for the SaveDialog part)
   * @return the maxWatermarkWidth
   */
  public int getWatermarkWidth()
  {
    calculateMaxWatermarkWidth();
    return maxWatermarkWidth;
  }

  /**
   * @param previewing the previewing to set
   */
  public void setPreviewing(boolean previewing)
  {
    this.previewing = previewing;
  }

  /**
   * @return the previewing
   */
  public boolean isPreviewing()
  {
    return previewing;
  }

  /**
   * @param otherAnnotationsRemoved the otherAnnotationsRemoved to set
   */
  public void setOtherAnnotationsRemoved(boolean removed)
  {
    this.otherAnnotationsRemoved = removed;
  }

  /**
   * @return the otherAnnotationsRemoved
   */
  public boolean isOtherAnnotationsRemoved()
  {
    return otherAnnotationsRemoved;
  }

  public void setFeatureHighlight(boolean blueBoxes)
  {
    this.blueBoxes = blueBoxes;
  }

  public boolean displayFeatureHighlight()
  {
    return blueBoxes;
  }

  public void setIntervalHighlight(boolean greenBoxes)
  {
    this.greenBoxes = greenBoxes;
  }

  public boolean displayIntervalHighlight()
  {
    return greenBoxes;
  }

  /**
   * <p>{@link OverlapBox} helps to keep from having to make lots of calculations for a
   * {@link OverlapBox} everytime we need to draw the box or see if a feature is
   * selected in the {@link OverlapBox}.</p>
   *
   * @author jaaseby@bioneos.com
   */
  public class OverlapBox
  {
    private HashMap<Annotation, Double> annotation;
    private DisplaySegment segment;
    private Vector<Vector<Annotation>> columns;
    private int labelColumnWidth;
    private FontMetrics metrics;
    private boolean open;
    private double height;
    private Annotation spotlight;
    private Annotation startAnnot;
    private Annotation stopAnnot;
    private int stop;
    private int start;
    private StringBuilder stopLabel;
    private StringBuilder startLabel;
    private int startLabelWidth;
    private int stopLabelWidth;
    private double yCoord;
    private boolean inverted;
    private int yCoordAdjust;
    private int buttonHeight;
    private int scrollSize;
    private UnitLabel startUnitLabel;
    private UnitLabel stopUnitLabel;
    private Vector<UnitLabel> hiddenLabels = new Vector<UnitLabel>();

    // Final Class Variables Representing Different Components of the OverlapBox
    /**
     * Represents the up arrow button in the {@link OverlapBox}
     */
    public static final int UP_ARROW = 1;

    /**
     * Represents the down arrow button in the {@link OverlapBox}
     */
    public static final int DOWN_ARROW = 2;

    /**
     * Represents the plus button in the {@link OverlapBox}
     */
    public static final int PLUS_BUTTON = 3;

    /**
     * Represents the x button in the {@link OverlapBox}
     */
    public static final int X_BUTTON = 4;

    /**
     * Represents the label column part of the {@link OverlapBox}
     */
    public static final int LABEL_COLUMN = 5;

    /**
     * Represents the annotation column part of the {@link OverlapBox}
     */
    public static final int ANNOTATION_COLUMN = 6;

    /**
     * <p>Default Constructor. {@link OverlapBox} will not be open or
     * usable.</p>
     */
    public OverlapBox()
    {
      open = false;
      inverted = false;
      segment = null;
      columns = new Vector<Vector<Annotation>>();
      labelColumnWidth = 0;
      height = 0;
      startAnnot = null;
      stopAnnot = null;
      yCoord = 0;
      annotation = new HashMap<Annotation, Double>();
    }

    /**
     * <p>Constructor of {@link OverlapBox}.</p>
     *
     * @param overlapAnnot
     *   {@link Vector} of {@link Annotation} that is overlapping each other
     *   in the {@link MapNavigator}
     * @param overlapBoxSegment
     *   {@link DisplaySegment} the {@link Annotation} belong to.
     * @param metrics
     *   {@link FontMetrics} of the {@link MapNavigator}
     */
    public OverlapBox(Vector<Annotation> annotation, DisplaySegment segment, FontMetrics metrics)
    {
      long begin = System.nanoTime();
      // Set "constants"
      int labelHeight = metrics.getHeight();

      // Set global variables
      this.segment = segment;
      this.metrics = metrics;
      columns = new Vector<Vector<Annotation>>();
      this.annotation = new HashMap<Annotation, Double>();
      open = true;
      buttonHeight = mainGUI.getOptions().getIntOption("buttonHeight");

      // number of labels to scroll on a press of an arrow button
      scrollSize = mainGUI.getOptions().getIntOption("scrollSize");

      //
      // Scrolling is done by adding an offset to the Y coordinate for
      // each label.  The Annotation is stored in the variable
      // annotation of type HashMap<Annotation, Double>.  The Double value
      // refers to the original Y coordinate for every Annotation.  This value
      // never changes.  Scrolling is then done by adding an offset to this
      // original value.
      //
      // amount of adjustment to the y coordinates needed for scrolling
      yCoordAdjust = 0;

      if (segment.getDrawingStart() > segment.getDrawingStop())
        inverted = true;
      else
        inverted = false;

      labelColumnWidth = 0;

      // Sort annotation
      if (!inverted)
      {
        Collections.sort(annotation, new SortAnnotationByStart());
      }
      else
      {
        Collections.sort(annotation, Collections.reverseOrder(new SortAnnotationByStop()));
      }

      //
      // Place annotation into seperate columns without overlapping
      //
      for (int i = 0; i < annotation.size(); i++)
      {
        Annotation annot = annotation.get(i);

        if (columns.size() == 0)
        {
          // Base case
          Vector<Annotation> column = new Vector<Annotation>();
          column.add(annot);
          columns.add(column);
          int stringWidth = metrics.stringWidth(annot.getName());
          labelColumnWidth = stringWidth;
          startAnnot = annot;
          stopAnnot = annot;
        }
        else
        {
          int j = 0;

          // Check if the annotation can be added to an existing column
          for (; j < columns.size(); j++)
          {
            Annotation lastAnnot = columns.get(j).lastElement();
            if ((!inverted && lastAnnot.getStop() < annot.getStart()) || (inverted && lastAnnot.getStart() > annot.getStop()))
            {
              columns.get(j).add(annot);

              int stringWidth = metrics.stringWidth(annot.getName());
              if (stringWidth > labelColumnWidth)
                labelColumnWidth = stringWidth;

              if (!inverted && annot.getStop() > stopAnnot.getStop())
                stopAnnot = annot;
              if (inverted && annot.getStart() < stopAnnot.getStart())
                stopAnnot = annot;

              break;
            }
          }

          // If that annotation couldn't be added, create a new column
          if (j == columns.size())
          {
            Vector<Annotation> column = new Vector<Annotation>();
            column.add(annot);
            columns.add(column);
            int stringWidth = metrics.stringWidth(annot.getName());
            if (stringWidth > labelColumnWidth)
              labelColumnWidth = stringWidth;
            if (!inverted && annot.getStop() > stopAnnot.getStop())
              stopAnnot = annot;
            if (inverted && annot.getStart() < stopAnnot.getStart())
              stopAnnot = annot;
          }
        }
      }

      // adjust labelColumnWidth to account for spacing when drawn
      labelColumnWidth += (2 * mainGUI.getOptions().getIntOption("labelColumnSpacing"));

      //
      // Determine height
      //
      // The height can be any of three options:
      // 1.  maxHeight - the maximum height it can be and stay on the screen
      // 2.  labelsHeight - tall enough to display every label
      // 3.  annotsHeight - tall enough to display the annotation on the
      //                    same scale as the segment
      //
      // The height must not be determined any later than this.  It is
      // needed below to determine other information about the overlap
      // box.
      //
      double maxHeight = getParent().getHeight() - (2 * mainGUI.getOptions().getIntOption("overlapBoxBorderSpacing"));
      double labelsHeight = (3 * buttonHeight) + (annotation.size() * labelHeight) + (labelHeight / 2);
      double annotsHeight = (3 * buttonHeight) + (2 * mainGUI.getOptions().getIntOption("annotColumnSpacing")) + getAnnotationStopCoord(segment, stopAnnot) - getAnnotationStartCoord(segment, startAnnot);
      double allHeight = Math.max(labelsHeight, annotsHeight);

      // set the height
      height = Math.min(maxHeight, allHeight);

      // set the start and stop position of the overlap box
      if (!inverted)
      {
        start = startAnnot.getStart();
        stop = stopAnnot.getStop();

        if (start < segment.getDrawingStart())
          start = segment.getDrawingStart();
        if (stop > segment.getDrawingStop())
          stop = segment.getDrawingStop();
      }
      else
      {
        start = startAnnot.getStop();
        stop = stopAnnot.getStart();

        if (start > segment.getDrawingStart())
          start = segment.getDrawingStart();
        if (stop < segment.getDrawingStop())
          stop = segment.getDrawingStop();
      }

      // create start and stop labels
      startLabel = new StringBuilder();
      stopLabel = new StringBuilder();
      createLabels();

      //
      // Add labels to the display
      //  This labels are marked as temporary labels so they can easily be
      //  removed when the OverlapBox is closed.
      //
      // Determine the position on the segment for the start and stop labels
      startUnitLabel = new UnitLabel(metrics, true);
      startUnitLabel.setLabel(getStartLabel());
      startUnitLabel.setUnitYCoord(getAnnotationStartCoord(getSegment(), getStartAnnot()));
      startUnitLabel.setLabelYCoord(getAnnotationStartCoord(getSegment(), getStartAnnot()));

      stopUnitLabel = new UnitLabel(metrics, true);
      stopUnitLabel.setLabel(getStopLabel());
      stopUnitLabel.setUnitYCoord(getAnnotationStopCoord(getSegment(), getStopAnnot()));
      stopUnitLabel.setLabelYCoord(getAnnotationStopCoord(getSegment(), getStopAnnot()));
      UnitLabel previousLabel = null;
      if (segment.getUnitLabels().size() > 0)
      {
        previousLabel = segment.getUnitLabels().lastElement();
        // swap labels if needed
        if (startUnitLabel.getLabelYCoord() > stopUnitLabel.getLabelYCoord())
        {
          UnitLabel temp = startUnitLabel;
          startUnitLabel = stopUnitLabel;
          stopUnitLabel = temp;
        }

        // check if labels will overlap
        if (startUnitLabel.getLabelYCoord() > stopUnitLabel.getLabelYCoord() - labelHeight)
          stopUnitLabel.setLabelYCoord(startUnitLabel.getLabelYCoord() + labelHeight);

        // check if labels would extend past segment
        if (stopUnitLabel.getLabelYCoord() > getSegment().getUnitLabels().lastElement().getLabelYCoord())
        {
          startUnitLabel.setLabelYCoord(getSegment().getUnitLabels().lastElement().getLabelYCoord() - labelHeight);
          stopUnitLabel.setLabelYCoord(getSegment().getUnitLabels().lastElement().getLabelYCoord());
        }

        //
        // Add the start and stop labels to the unit labels for the map.
        // If either the start or stop label overlaps with a unit label
        // the start or stop label will replace the unit label while the
        // overlap box is open.  The unit label will get replaced when
        // the overlap box closes.
        //
        for (UnitLabel unitLabel : segment.getUnitLabels())
        {
          // Found position for startUnitLabel
          if (previousLabel.getLabelYCoord() < startUnitLabel.getLabelYCoord() &&
              startUnitLabel.getLabelYCoord() <= unitLabel.getLabelYCoord())
          {
            // Check if they overlap and remove the unit label if they do
            if (previousLabel.getLabelYCoord() > startUnitLabel.getLabelYCoord() - labelHeight)
            {
              hiddenLabels.add(previousLabel);
              segment.removeUnitLabel(previousLabel);
            }

            if (startUnitLabel.getLabelYCoord() > unitLabel.getLabelYCoord() - labelHeight)
            {
              hiddenLabels.add(previousLabel);
              segment.removeUnitLabel(unitLabel);
            }

            // Add to the list
            segment.addUnitLabel(true, startUnitLabel);
          }

          // Found position for stopUnitLabel
          if (previousLabel.getLabelYCoord() < stopUnitLabel.getLabelYCoord() &&
              stopUnitLabel.getLabelYCoord() <= unitLabel.getLabelYCoord())
          {
            // Check if they overlap and remove the unit label if they do
            if (previousLabel.getLabelYCoord() > stopUnitLabel.getLabelYCoord() - labelHeight)
            {
              hiddenLabels.add(previousLabel);
              segment.removeUnitLabel(previousLabel);
            }

            if (stopUnitLabel.getLabelYCoord() > unitLabel.getLabelYCoord() - labelHeight)
            {
              hiddenLabels.add(previousLabel);
              segment.removeUnitLabel(unitLabel);
            }

            // Add to the list
            segment.addUnitLabel(true, stopUnitLabel);
          }

          // Update pointer to last label
          previousLabel = unitLabel;
        }

        // Do the best to center overlapbox vertically
        // and do the best to keep the overlapbox on the screen
        if (!inverted)
        {
          yCoord = (getAnnotationStopCoord(segment, stopAnnot)
              + getAnnotationStartCoord(segment, startAnnot)) / 2.0 - getHeight() / 2;
        }
        else
        {
          yCoord = (getAnnotationStartCoord(segment, startAnnot)
              + getAnnotationStopCoord(segment, stopAnnot)) / 2.0 - getHeight() / 2;
        }

        // keep overlap box on the screen
        Rectangle viewRect = ((JScrollPane)getParent().getParent()).getViewport().getViewRect();

        if (yCoord < viewRect.y)
          yCoord = viewRect.y + mainGUI.getOptions().getIntOption("overlapBoxBorderSpacing");

        if (yCoord + getHeight() > viewRect.y + viewRect.height)
          yCoord = viewRect.y + viewRect.height - height - mainGUI.getOptions().getIntOption("overlapBoxBorderSpacing");

        //
        // Determine the position of the annotation labels
        //
        // check if the labels will be placed at the midpoints
        // Labels will be placed close to their midpoints if the height of the box is bigger than the space it takes
        // to display all of the labels.
        if (annotation.size() < Math.floor((getHeight() - (3 * buttonHeight) - (labelHeight / 2)) / metrics.getHeight()))
        {
          // the height that labels can be drawn in
          double actualHeight = height - (3 * buttonHeight);

          // position of the first label
          double actualY = yCoord + (2 * buttonHeight) + labelHeight;

          // Determine initial label y coord...the midpoint of the annotation it represents
          for (Annotation annot : annotation)
          {
            double y = getAnnotYCoord(annot) + getAnnotHeight(annot) / 2.0 + (metrics.getMaxAscent() - labelHeight / 2);

            if (y - metrics.getMaxAscent() < actualY)
              y += actualY - (y - metrics.getMaxAscent());

            this.annotation.put(annot, y);
          }

          // Check for overlapping labels and correct as needed
          for (Annotation annot : annotation)
          {
            double difference = 0;
            int index = annotation.indexOf(annot);

            // first label
            if (index == 0)
              continue;

            // they overlap some
            if (this.annotation.get(annotation.get(index)) - labelHeight < this.annotation.get(annotation.get(index - 1)))
            {
              difference = this.annotation.get(annotation.get(index - 1)) + labelHeight - this.annotation.get(annotation.get(index));
            }
            // they are in the same position
            else if (this.annotation.get(annotation.get(index)) == this.annotation.get(annotation.get(index - 1)))
            {
              difference = labelHeight;
            }

            //
            // adjust so they don't overlap
            //
            if (difference > 0)
            {
              //
              // split the labels apart
              //
              if (this.annotation.get(annotation.get(index - 1)) - labelHeight - (difference / 2) > actualY &&
                  this.annotation.get(annotation.get(index - 1)) + labelHeight + (difference / 2) < actualY + actualHeight &&
                  index < 2)
              {
                this.annotation.put(annotation.get(index - 1), new Double(this.annotation.get(annotation.get(index - 1)).doubleValue() - difference / 2));
                this.annotation.put(annotation.get(index), new Double(this.annotation.get(annotation.get(index)).doubleValue() + difference / 2));
                continue;
              }
              else if (index >= 2 &&
                  this.annotation.get(annotation.get(index - 1)) - labelHeight - (difference / 2) > this.annotation.get(annotation.get(index - 2)) &&
                  this.annotation.get(annotation.get(index - 1)) + labelHeight + (difference / 2) < actualY + actualHeight)
              {
                this.annotation.put(annotation.get(index - 1), new Double(this.annotation.get(annotation.get(index - 1)).doubleValue() - difference / 2));
                this.annotation.put(annotation.get(index), new Double(this.annotation.get(annotation.get(index)).doubleValue() + difference / 2));
                continue;
              }

              //
              // move the previous labels up
              //
              // recursive method to adjust the label positions
              if (adjustLabels(index - 1, annotation))
                continue;

              //
              // move the current label down
              //
              this.annotation.put(annotation.get(index), new Double(this.annotation.get(annotation.get(index - 1)).doubleValue() + labelHeight));
            }
          }
        }
        // the labels cannot be adjusted any...place them one after another
        else
        {
          for (Annotation annot : annotation)
          {
            double annotIndex = this.annotation.size();
            double labelYCoord = getY() + (2 * buttonHeight) + labelHeight + (annotIndex * labelHeight);

            if (!inverted && (start != startAnnot.getStart() || stop != stopAnnot.getStop()))
              labelYCoord += metrics.getHeight() / 2;
            else if (inverted && (start != startAnnot.getStop() || stop != stopAnnot.getStart()))
              labelYCoord += metrics.getHeight() / 2;

            this.annotation.put(annot, new Double(labelYCoord));
          }
        }
      }
      addTiming("MapNavigator - OverlapBox", System.nanoTime() - begin);
    }

    /**
     * <p>Sometimes the labels in the overlap box need to be adjusted to fit each
     * label.  This method will adjust the labels above where the current label
     * would like to be placed.  It is recursive and continues until the labels
     * have been adjusted and the new one fits or until it realizes that
     * adjusting the labels will not work.</p>
     *
     * @param index
     *    the index of the annotation to determine the label for
     * @param annotation
     *    the {@link Annotation} being displayed by the overlap box
     *
     * @return
     *    true - the labels were adjusted and the new label fit
     *    false - the labels cannot be adjusted to fit the new label
     */
    private boolean adjustLabels(int index, Vector<Annotation> annotation)
    {
      int labelHeight = metrics.getHeight();

      // coordinate of the first label in the overlap box
      double actualY = yCoord + (2 * buttonHeight);

      // save the old position
      double original = this.annotation.get(annotation.get(index));

      // give the new position
      this.annotation.put(annotation.get(index), new Double(this.annotation.get(annotation.get(index + 1)).doubleValue() - labelHeight));

      // first element...compare to the box instead of the previous label
      if (index < 1)
      {
        // check if the adjustment is successful and the new label fits
        if (this.annotation.get(annotation.get(index)) - labelHeight > actualY)
        {
          return true;
        }
        else
        {
          // replace with the original position of the label...adjustment was not successful
          this.annotation.put(annotation.get(index), new Double(original));
          return false;
        }
      }

      // check if the move caused more overlap
      if (this.annotation.get(annotation.get(index)) - labelHeight > this.annotation.get(annotation.get(index - 1)))
      {
        return true;
      }
      else
      {
        // recursive call to adjust the labels
        if (adjustLabels(index - 1, annotation))
        {
          return true;
        }
        else
        {
          // replace with the original position of the label...adjustment was not successful
          this.annotation.put(annotation.get(index), new Double(original));
          return false;
        }
      }
    }

    /**
     * <p>Get the y-coordinate (in pixels) to position the {@link OverlapBox}.
     * </p>
     *
     * @return
     *   int value of where the y-coordinate of the {@link OverlapBox}
     */
    public double getY()
    {
      return yCoord;
    }

    /**
     * <p>Get the height of the {@link OverlapBox} (in pixels).</p>
     *
     * @return
     *   int value of the height of the {@link OverlapBox}
     */
    public double getHeight()
    {
      return height;
    }

    /**
     * <p>Get the width of the {@link OverlapBox} in pixels.</p>
     *
     * @return
     *   int value of the width of the {@link OverlapBox}
     */
    public double getWidth()
    {
      int annotDrawingWidth = mainGUI.getOptions().getIntOption("annotDrawingWidth");
      int annotColumnSpacing = mainGUI.getOptions().getIntOption("annotColumnSpacing");
      int selectionBoxSpacing = mainGUI.getOptions().getIntOption("selectionBoxSpacing");
      int overlapBoxWidth = labelColumnWidth + (2 * annotColumnSpacing);
      int numDrawnColumns = columns.size();

      overlapBoxWidth += (annotDrawingWidth * numDrawnColumns) + ((numDrawnColumns - 1) * selectionBoxSpacing);

      return overlapBoxWidth;
    }

    /**
     * <p>Get the x-coordinate of the {@link OverlapBox} in the
     * {@link MapNaviagtor}.</p>
     *
     * @return
     *  int value of the x-coordinate of the {@link OverlapBox}
     */
    public double getX()
    {
      int featureColumnWidth = mainGUI.getOptions().getIntOption("featureColumnWidth");
      int featureLabelColumnWidth = mainGUI.getOptions().getIntOption("featureLabelColumnWidth");
      int overlapBoxGap = mainGUI.getOptions().getIntOption("overlapBoxGap");

      // Determine width
      int overlapBoxWidth = (int)(getWidth());

      int overlapBoxXCoord = getDisplayMapXCoord(segment.getParent());
      for (AnnotationSet set : segment.getParent().getShownSets())
      {
        if (hiddenTypes.contains(set) || !mainGUI.getOptions().isShown(set.getType()))
          continue;

        overlapBoxXCoord += featureLabelColumnWidth;
        if (set.equals(startAnnot.getAnnotationSet().getType()))
        {
          overlapBoxXCoord += featureColumnWidth / 4;
          break;
        }
      }

      overlapBoxXCoord -= overlapBoxWidth + overlapBoxGap;

      return overlapBoxXCoord;
    }

    /**
     * <p>Determines the component of the {@link OverlapBox} that
     * a {@link MouseEvent} occurs in.</p>
     *
     * @param e
     *  The {@link MouseEvent} that contains a {@link Point}
     *  that themethod can use to check inside the {@link OverlapBox}
     * @return
     *   PLUS_BUTTON - if the event occurs within the Plus Button Component<br>
     *   X_BUTTON - if the event occurs within the X Button Component<br>
     *   UP_ARROW - if the event occurs within the Up Arrow Component<br>
     *   DOWN_ARROW - if the event occurs within the Down Arrow Component<br>
     *   LABEL_COLUMN - if the event occurs within the Label Column Component<br>
     *   ANNOTATION_COLUMN - if the event occurs within the Annotation Column Component<br>
     *   null - if the event does not occcur in the {@link OverlapBox}
     */
    public int getComponent(MouseEvent e)
    {
      double x = this.getX();
      double y = this.getY();
      double width = this.getWidth();
      double height = this.getHeight();

      // '+' button
      if (x <= e.getX() && e.getX() <= x + (width / 2) &&
          y <= e.getY() && e.getY() <= y + buttonHeight)
      {
        return PLUS_BUTTON;
      }
      // 'x' button
      else if (x + (width / 2) <= e.getX() && e.getX() <= x + width &&
          y <= e.getY() && e.getY() <= y + buttonHeight)
      {
        return X_BUTTON;
      }
      // up arrow
      else if (x <= e.getX() && e.getX() <= x + labelColumnWidth &&
          y + buttonHeight <= e.getY() && e.getY() <= y + (buttonHeight * 2))
      {
        return UP_ARROW;
      }
      // down arrow
      else if (x <= e.getX() && e.getX() <= x + labelColumnWidth &&
          y + height - buttonHeight <= e.getY())
      {
        return DOWN_ARROW;
      }
      // label column
      else if (x <= e.getX() && x + labelColumnWidth >= e.getX() &&
          y + (buttonHeight * 2) <= e.getY() && e.getY() <= y + height - buttonHeight)
      {
        return LABEL_COLUMN;
      }
      // annotation column
      else if (x + labelColumnWidth <= e.getX() && e.getX() <= x + width &&
          y + buttonHeight <= e.getY() && e.getY() <= y + height)
      {
        return ANNOTATION_COLUMN;
      }
      // Not in OverlapBox
      else
      {
        return -1;
      }
    }

    /**
     * <p>Check to see if a particular {@link MouseEvent} occurred within the
     * bounds of the {@link OverlapBox}.</p>
     *
     * @param e
     *   {@link MouseEvent} that is being checked
     * @return
     *   true - if the event occurred within the {@link OverlapBox} bounds
     *   false - if the event did NOT occur within the {@link OverlapBox} bounds
     */
    public boolean contains(MouseEvent e)
    {
      return (e.getX() >= getX()) && (e.getX() <= getX() + getWidth()) &&
        (e.getY() >= e.getY()) && (e.getY() <= getY() + getHeight());
    }

    /**
     * <p>Check to see if a particular {@link MouseEvent} occurred within the
     * bounds of the {@link OverlapBox} and respond appropriately.</p>
     *
     * @param e
     *   {@link MouseEvent} that is being checked
     * @return
     *   true - if the event occurred within the {@link OverlapBox} bounds and is handled
     *   false - if the event did NOT occurr within the {@link OverlapBox} bounds
     */
    public boolean handleEvent(MouseEvent e)
    {
      boolean defaultCursor = true;
      spotlight = null;

      if (e == null || !open) return false;

      double x = this.getX();
      double y = this.getY();
      double width = this.getWidth();
      double height = this.getHeight();

      // check if event occured within the overlap box
      if (x <= e.getX() && e.getX() <= x + width && y <= e.getY() && e.getY() <= y + height)
      {
        // '+' button
        if (x <= e.getX() && e.getX() <= x + (width / 2) &&
            y <= e.getY() && e.getY() <= y + buttonHeight)
        {
          setCursor(new Cursor(Cursor.HAND_CURSOR));
          defaultCursor = false;

          if (e.getClickCount() > 0 && e.getID() == MouseEvent.MOUSE_RELEASED)
          {
            fitToViewport();
            defaultCursor = true;
          }
        }
        // 'x' button
        else if (x + (width / 2) <= e.getX() && e.getX() <= x + width &&
            y <= e.getY() && e.getY() <= y + buttonHeight)
        {
          setCursor(new Cursor(Cursor.HAND_CURSOR));
          defaultCursor = false;

          if (e.getClickCount() > 0 && e.getID() == MouseEvent.MOUSE_RELEASED)
          {
            int index = overlapBoxes.indexOf(this);
            overlapBoxes.get(index).close();
            overlapBoxes.get(index).resizeMapNavigator();
            overlapBoxes.remove(index);
            defaultCursor = true;
          }
        }
        // up arrow
        else if (x <= e.getX() && e.getX() <= x + labelColumnWidth &&
            y + buttonHeight <= e.getY() && e.getY() <= y + (buttonHeight * 2))
        {
          if (displayUpArrow())
          {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            defaultCursor = false;

            if (e.getClickCount() > 0 && e.getID() == MouseEvent.MOUSE_RELEASED)
            {
              // the start label is closer than the scroll size...don't need to scroll the full amount
              if (getAnnotLabelYCoord(getStartAnnot()) >= getY() + (2 * buttonHeight) + metrics.getHeight() - (scrollSize * metrics.getHeight()))
                yCoordAdjust = 0;
              else
                yCoordAdjust += (scrollSize * metrics.getHeight());

              repaint();
            }
          }
        }
        // down arrow
        else if (x <= e.getX() && e.getX() <= x + labelColumnWidth &&
            y + height - buttonHeight <= e.getY())
        {
          if (displayDownArrow())
          {
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            defaultCursor = false;

            if (e.getClickCount() > 0 && e.getID() == MouseEvent.MOUSE_RELEASED)
            {
              // the stop label is closer than the scroll size...don't need to scroll the full amount
              if (getAnnotLabelYCoord(getStopAnnot()) <= getY() + height - buttonHeight + (scrollSize * metrics.getHeight()))
              {
                int count = 1;

                // figure out how much to adjust to have the last label appear in the correct position
                while (getAnnotLabelYCoord(getStopAnnot()) >= getY() + height - buttonHeight + (count * metrics.getHeight()))
                  count++;

                yCoordAdjust -= (count * metrics.getHeight());
              }
              else
                yCoordAdjust -= (scrollSize * metrics.getHeight());

              repaint();
            }
          }
        }
        // label column
        else if (x + labelColumnWidth >= e.getX())
        {
          spotlight = null;

          for (Annotation annot : annotation.keySet())
          {
            double labelYCoord = getAnnotLabelYCoord(annot);

            if ((labelYCoord - metrics.getAscent() - metrics.getLeading() <= e.getY() &&
                e.getY() <= labelYCoord + metrics.getDescent()) && isAnnotLabelVisible(annot))
            {
              spotlight = annot;

              if (e.getClickCount() == 1 && e.getID() == MouseEvent.MOUSE_RELEASED)
              {
                if (selection.contains(annot) && (e.isControlDown() || e.isShiftDown() || e.isMetaDown()))
                  removeSelectedAnnotation(annot, false);
                else if (selection.contains(annot))
                  addSelectedAnnotation(segment, annot, false, false);
                else
                  addSelectedAnnotation(segment, annot, e.isControlDown() || e.isShiftDown() || e.isMetaDown(), false);

                MapNavigator.this.repaint();
              }
              else if (e.getClickCount() == 2 && e.getID() == MouseEvent.MOUSE_RELEASED)
              {
                DetailsDialog.showDetailsDialog(mainGUI);
                Tutorial.updatePage("ViewDetails");
              }

              break;
            }
          }
        }
        // annotation column
        else if (x + labelColumnWidth <= e.getX())
        {
          spotlight = null;

          for (Annotation annot : annotation.keySet())
          {
            double annotYCoord = getAnnotYCoord(annot);
            double annotHeight = getAnnotHeight(annot);
            double annotXCoord = x + labelColumnWidth + mainGUI.getOptions().getIntOption("annotColumnSpacing");
            double annotWidth = mainGUI.getOptions().getIntOption("annotDrawingWidth");
            double betweenAnnotSpacing = mainGUI.getOptions().getIntOption("overlapBoxBetweenAnnotSpacing");

            annotXCoord += getColumn(annot) * (annotWidth + betweenAnnotSpacing);

            if (annotYCoord <= e.getY() && e.getY() <= annotYCoord + annotHeight &&
                annotXCoord <= e.getX() && e.getX() <= annotXCoord + annotWidth)
            {
              spotlight = annot;
              centerOnAnnotation(annot);

              if (e.getClickCount() == 1 && e.getID() == MouseEvent.MOUSE_RELEASED)
              {
                if (selection.contains(annot) && (e.isControlDown() || e.isShiftDown() || e.isMetaDown()))
                  removeSelectedAnnotation(annot, false);
                else if (selection.contains(annot))
                  addSelectedAnnotation(segment, annot, false, false);
                else
                  addSelectedAnnotation(segment, annot, e.isControlDown() || e.isShiftDown() || e.isMetaDown(), false);

                MapNavigator.this.repaint();
              }
              else if (e.getClickCount() == 2 && e.getID() == MouseEvent.MOUSE_RELEASED)
              {
                DetailsDialog.showDetailsDialog(mainGUI);
                Tutorial.updatePage("ViewDetails");
              }

              break;
            }
          }
        }
        else
          spotlight = null;

        if (defaultCursor)
          setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

        return true;
      }

      if (defaultCursor)
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));

      spotlight = null;
      return false;
    }

    /**
     * <p>Get the column this {@link Annotation} is displayed in.</p>
     *
     * @param annot
     *    {@link Annotation} to get the column for.
     *
     * @return
     *    the column for annot
     */
    public int getColumn(Annotation annot)
    {
      for (Vector<Annotation> column : columns)
      {
        if (column.contains(annot))
        {
          return columns.indexOf(column);
        }
      }

      return -1;
    }

    /**
     * <p>Determine if the up arrow is needed</p>
     *
     * @return
     *    true - the arrow is needed to scroll
     *    false - the arrow is not needed to scroll
     */
    public boolean displayUpArrow()
    {
      if (isAnnotLabelVisible(getStartAnnot()))
        return false;
      else
        return true;
    }

    /**
     * <p>Determine if the down arrow is needed</p>
     *
     * @return
     *    true - the arrow is needed to scroll
     *    false - the arrow is not needed to scroll
     */
    public boolean displayDownArrow()
    {
      if (isAnnotLabelVisible(getStopAnnot()))
        return false;
      else
        return true;
    }

    /**
     * <p>Determine if an {@link Annotation} label is visible in the overlap box</p>
     *
     * @param a
     *    {@link Annotation} to check if its label is visible.
     *
     * @return
     *    true - the label is visible
     *    false - the label is not visible
     */
    public boolean isAnnotLabelVisible(Annotation a)
    {
      if (getY() + (2 * buttonHeight) + metrics.getHeight() <= getAnnotLabelYCoord(a) &&
          getAnnotLabelYCoord(a) <= getY() + getHeight() - buttonHeight)
        return true;
      else
        return false;
    }

    /**
     * <p>When the user is moving their mouse within the annotation column
     * of the overlap box, this method will center the annotation in the
     * overlap box on whichever annotation their mouse is hovering over.</p>
     *
     * @param a
     *    the annotation to be centered in the overlap box
     */
    public void centerOnAnnotation(Annotation a)
    {
      // return if everything is visible
      if (!displayUpArrow() && !displayDownArrow())
        return;
      else
      {
        // number of labels visible in the overlap box
        int numVisibleLabels = (int)Math.floor((getHeight() - (3 * buttonHeight)) / metrics.getHeight());

        // position of the label without the scrolling adjustment
        double originalPosition = annotation.get(a);

        // position of the label currently in the overlap box
        double currentPosition = getAnnotLabelYCoord(a);

        // the center of the overlap box...where the label needs to be positioned
        double newPosition = (2 * buttonHeight) + (metrics.getHeight() / 2) + ((numVisibleLabels / 2) * metrics.getHeight());

        // how far away the label is from the middle
        double offset = Math.abs(currentPosition - newPosition);

        // offset to the top and bottom from the middle position
        double offsetConstantTop = newPosition - getY();
        double offsetConstantBottom = getY() + getHeight() - newPosition;

        //
        // adjust to middle, or if its at an extreme do as best as possible
        // in order to keep the box filled
        //
        if (currentPosition < newPosition)
        {
          // if its original position is closer to the top
          // than the middle, place it at the original position
          // instead of the middle because we always want the box
          // to be filled
          if (originalPosition - offsetConstantTop < annotation.get(getStartAnnot()))
            yCoordAdjust = 0;
          else
            yCoordAdjust += offset;
        }
        else if (currentPosition > newPosition)
        {
          // if its original position is closer to the bottom than
          // the middle, place it at the original position instead
          // of the middle because we always want the box to be filled
          if (originalPosition + offsetConstantBottom > annotation.get(getStopAnnot()))
            yCoordAdjust = -((annotation.size() - numVisibleLabels) * metrics.getHeight());
          else
            yCoordAdjust -= offset;
        }
      }
    }

    /**
     * <p>Create the start and stop {@link UnitLabel}s for the {@link OverlapBox}.</p>
     */
    public void createLabels()
    {
      // start label
      startLabel.append(start / segment.getChromosome().getMap().getScale());

      for (int i = startLabel.length() - 3; i > 0; i-=3)
        startLabel.insert(i, ',');

      startLabel.append(" ");
      startLabel.append(segment.getChromosome().getMap().getUnitsString());
      startLabelWidth = metrics.stringWidth(startLabel.toString());

      // stop label
      stopLabel.append(stop / segment.getChromosome().getMap().getScale());

      for (int i = stopLabel.length() - 3; i > 0; i-=3)
        stopLabel.insert(i, ',');

      stopLabel.append(" ");
      stopLabel.append(segment.getChromosome().getMap().getUnitsString());
      stopLabelWidth = metrics.stringWidth(stopLabel.toString());
    }

    /**
     * <p>Get the start {@link UnitLabel}.</p>
     *
     * @return
     *    the start {@link UnitLabel}
     */
    public UnitLabel getStartUnitLabel()
    {
      return startUnitLabel;
    }


    /**
     * <p>Get the stop {@link UnitLabel}.</p>
     *
     * @return
     *    the stop {@link UnitLabel}
     */
    public UnitLabel getStopUnitLabel()
    {
      return stopUnitLabel;
    }

    /**
     * <p>Get the {@link Annotation} that the user's mouse is currently
     * hovering over.</p>
     *
     * @return
     *   {@link Annotation} if the user's mouse is hovering a piece of
     *   {@link Annotation}'s label or null if the user's mouse if hovering
     *   somewhere else
     */
    public Annotation getSpotlight()
    {
      return spotlight;
    }

    /**
     * <p>Get the {@link FontMetrics} for the {@link OverlapBox}</p>
     *
     * @return
     *  The {@link FontMetrics} for the {@link OverlapBox}
     */
    public FontMetrics getFontMetrics()
    {
      return metrics;
    }

    /**
     * <p>Get the y-coordinate of label for {@link Annotation} inside
     * of the {@link OverlapBox}.</p>
     *
     * @param annotation
     *   {@link Annotation} to determine the y-coordinate for the label
     * @return
     *   int value of the y-coordinate of the {@link Annotation} label
     */
    public double getAnnotLabelYCoord(Annotation annotation)
    {
      Double index = this.annotation.get(annotation);

      if (index != null)
        return index.doubleValue() + yCoordAdjust;
      else
        return -1;
    }

    /**
     * <p>Get the y-coordinate of {@link Annotation} inside
     * of the {@link OverlapBox}.</p>
     *
     * @param annotation
     *   {@link Annotation} to determine the y-coordinate
     * @return
     *   int value of the y-coordinate of the {@link Annotation}
     */
    public double getAnnotYCoord(Annotation annotation)
    {
      // Avoid dividing by zero
      if (stop - start == 0)
        return 0.5 * height + getY() + metrics.getHeight();
      else if (!inverted)
      {
        double annotStart = annotation.getStart();
        double annotStop = annotation.getStop();

        if (annotStart < start)
          annotStart = start;
        if (annotStop > stop)
          annotStop = stop;

        double yCoord = getY() + (2 * buttonHeight) + mainGUI.getOptions().getIntOption("annotColumnSpacing");
        yCoord += (((annotStart - start) / (stop - start)) * (height - (buttonHeight * 3) - (2 * mainGUI.getOptions().getIntOption("annotColumnSpacing"))));

        if (start != startAnnot.getStart() || stop != stopAnnot.getStop())
          yCoord += metrics.getHeight() / 2;

        return yCoord;
      }
      else
      {
        double annotStart = annotation.getStart();
        double annotStop = annotation.getStop();
        if (annotStart < stop)
          annotStart = stop;
        if (annotStop > start)
          annotStop = start;

        double yCoord = getY() + (2 * buttonHeight) + mainGUI.getOptions().getIntOption("annotColumnSpacing");
        yCoord += (((start - annotStop) / (start - stop)) * (height - (buttonHeight * 3) - (2 * mainGUI.getOptions().getIntOption("annotColumnSpacing"))));

        if (start != startAnnot.getStop() || stop != stopAnnot.getStart())
          yCoord += metrics.getHeight() / 2;

        return yCoord;
      }
    }

    /**
     * <p>Get the height of the {@link Annotation} relative to the
     * size of the {@link OverlapBox}.</p>
     *
     * @param annotation
     *   Height of the {@link Annotation}
     * @return
     *   int value of the height of the {@link Annotation}
     */
    public double getAnnotHeight(Annotation annotation)
    {
      // Avoid dividing by zero
      if (stopAnnot.getStop() - startAnnot.getStart() == 0)
        return 1;
      else
      {
        if (!inverted)
        {
          double annotStart = annotation.getStart();
          double annotStop = annotation.getStop();

          if (annotStart < start)
            annotStart = start;
          if (stop < annotStop)
            annotStop = stop;

          return (annotStop - annotStart)
          / (stop - start) * (height - (buttonHeight * 3) - (2 * mainGUI.getOptions().getIntOption("annotColumnSpacing"))) + 1;
        }
        else
        {
          double annotStart = annotation.getStart();
          double annotStop = annotation.getStop();

          if (annotStart < stop)
            annotStart = stop;
          if (start < annotStop)
            annotStop = start;

          return (annotStop - annotStart)
          / (start - stop) * (height - (buttonHeight * 3) - (2 * mainGUI.getOptions().getIntOption("annotColumnSpacing"))) + 1;
        }
      }
    }

    /**
     * <p>Returns whether the {@link OverlapBox} is currently being
     * displayed</p>
     *
     * @return
     *   true - {@link OverlapBox} is open
     *   false - {@link OverlapBox} is NOT open
     */
    public boolean isOpen()
    {
      return open;
    }

    /**
     * <p>Returns if the {@link OverlapBox} belongs to an inverted
     * {@link DisplaySegment}.</p>
     *
     * @return
     *   true - if the {@link DisplaySegment} is inverted
     *   false - if the {@link DisplaySegment} is NOT inverted
     */
    public boolean isInverted()
    {
      return inverted;
    }

    /**
     * <p>This makes the makes the {@link OverlapBox} not open and clears
     * the feature width for the {@link DisplayMap} the {@link OverlapBox} is
     * associated with. The <code>prepareDisplayMaps()</code> method
     * must be called after this method is used to display the associated
     * {@link DisplayMap}s correctly.
     *
     * NOTE Once closed, that instance of {@link OverlapBox} cannot
     * become open again.</p>
     *
     */
    public void close()
    {
      if (open)
      {
        open = false;
        repaintLabels(true);
      }
    }

    /**
     * <p>Repaints {@link MapNavigator} only in the area that {@link OverlapBox}
     * is located.</p>
     *
     */
    public void repaint()
    {
      // x and y clip
      int x = (int)getX();
      int y = (int)getY();

      // the "+1" is to account for drawing the border around the box
      int width = getDisplayMapXCoord(getSegment().getParent()) +
      getSegment().getParent().getFeatureColumnsWidth(mainGUI.getOptions()) - x + 1;
      int height = (int)getHeight() + 1;

      MapNavigator.this.repaint(x, y, width, height);
    }

    /**
     * <p>Repaints the labels, if the OverlapBox is closing, the close parameter should be true to that
     * the {@link DisplaySegment}'s {@link UnitLabel}s may be recalculated.</p>
     * @param close
     *   boolean value for whether to recalculate the unit labels for the segment.
     */
    public void repaintLabels(boolean close)
    {
      int x = getDisplayMapXCoord(segment.getParent()) + segment.getParent().getFeatureColumnsWidth(mainGUI.getOptions()) +
        mainGUI.getOptions().getIntOption("segmentWidth") + 1;
      int y = (int)(getStartUnitLabel().getLabelYCoord() + metrics.getMaxAscent() - (metrics.getHeight() / 2) - metrics.getHeight());
      int width = Math.max(getStartUnitLabel().getLabelWidth(), getStopUnitLabel().getLabelWidth()) +
        mainGUI.getOptions().getIntOption("featureColumnWidth") + 12;
      int height = (int)(getStopUnitLabel().getLabelYCoord() + metrics.getMaxAscent() - (metrics.getHeight() / 2) - y);
      if (close)
      {
        // On close, remove the two OverlapBox start/stop UnitLabels
        segment.clearTempUnitLabels();
        // And restore any labels that were hidden
        for (UnitLabel u : hiddenLabels)
          segment.addUnitLabel(u);
      }
      MapNavigator.this.repaint(x, y, width, height);
    }

    /**
     * <p>Fit the {@link Annotation} within the {@link OverlapBox} so that is fills
     * the {@link MapNavigator}'s {@link JViewport}.</p>
     *
     */
    public void fitToViewport()
    {
      // Variables
      int zoomBarPos = 0;
      JViewport vp = (JViewport)getParent();
      int viewportHeight = vp.getBounds().height;
      int originalDrawingHeight = drawingHeight;

      // Determine where to center around when zooming
      int start = (int)(getAnnotationStartCoord(segment, startAnnot));
      int stop = (int)(getAnnotationStopCoord(segment, stopAnnot));
      doubleClickZoomPos = new Point(vp.getBounds().x + vp.getBounds().width / 2, (start + stop) / 2);

      // Determine best zoom level
      for (int i = 0; i < mainGUI.getZoomBar().getMaximum(); i++)
      {
        double zl = Math.pow(1.5, i);

        drawingHeight = (int)(viewportHeight * zl) - headerHeight - mainGUI.getOptions().getIntOption("footerHeight");
        start = (int)(getAnnotationStartCoord(segment, startAnnot));
        stop = (int)(getAnnotationStopCoord(segment, stopAnnot));

        if (stop - start < viewportHeight - (viewportHeight / 5))
          zoomBarPos = i;
        else
          break;
      }

      // Change drawingHeight back
      drawingHeight = originalDrawingHeight;

      // Change zoom level
      if (zoomBarPos != mainGUI.getZoomBar().getValue())
      {
        mainGUI.getZoomBar().setValue(zoomBarPos);
      }
      else
      {
        doubleClickZoomPos = null;
        close();
        resizeMapNavigator();
        //repaint();
      }
    }

    /**
     * <p>Resize the {@link MapNavigator} when there are instances that
     * the {@link OverlapBox} has a height taller than the current
     * {@link MapNavigator} or the {@link OverlapBox} would be drawn of the left
     * or right side of {@link MapNavigator} without affecting the current map
     * grouping.</p>
     *
     */
    public void resizeMapNavigator()
    {
      if (displayMaps.size() == 0) return;

      // Initialize variables
      MapNavigator mn = MapNavigator.this;
      JViewport vp = (JViewport)getParent();

      int paneWidth = mn.getWidth();
      if (getX() < 0)
        paneWidth += getX() * -1;
      else
        paneWidth = determineWidth();

      int normalHeight = headerHeight + mainGUI.getOptions().getIntOption("footerHeight") + drawingHeight;

      if ((getHeight() + getY() > normalHeight && open) || mn.getHeight() != normalHeight || mn.getWidth() != paneWidth)
      {

        // Determine new height
        int newVPHeight;
        if (getHeight() + getY() > normalHeight && open)
          newVPHeight = headerHeight + mainGUI.getOptions().getIntOption("footerHeight") + (int)(getHeight() + getY());
        else
          newVPHeight = normalHeight;

        // Set MapNavigator size
        mn.setPreferredSize(new Dimension(paneWidth, newVPHeight));

        mn.revalidate();
        vp.validate();
      }
      else
      {
        // Overlap box was smaller than the viewport, just repaint
        MapNavigator.this.repaint();
        repaintLabels(false);
      }
    }

    /**
     * <p>Returns {@link Annotation} seperated in seperate columns so
     * that {@link Annotation} can be potentially be displayed without
     * overlap.<p>
     *
     * @return
     *   {@link Vector} of {@link Vector}s of {@link Annotation}. Each
     *   {@link Vector} of {@link Annotation} is considered a column.
     */
    public Vector<Vector<Annotation>> getColumns()
    {
      return columns;
    }

    /**
     * <p>Returns the widths (in pixels) of largest feature label for
     * each column. The column widths are in the same order as the
     * columns returned in <code>getColumns()</code>.</p>
     *
     * @return
     *   {@link Vector} of {@link Integer}s representing the width of each
     *   column in the {@link OverlapBox}.
     */
    public int getLabelColumnWidth()
    {
      return labelColumnWidth;
    }

    /**
     * <p>Get the start position of the {@link OverlapBox}.</p>
     *
     * @return
     *   start position of the {@link OverlapBox}
     */
    public int getStart()
    {
      return start;
    }

    /**
     * <p>Get the stop position of the {@link OverlapBox}.</p>
     *
     * @return
     *   stop position of the {@link OverlapBox}
     */
    public int getStop()
    {
      return stop;
    }

    /**
     * <p>Get the {@link Annotation} in the {@link OverlapBox} that has the
     * smallest start position.</p>
     *
     * @return
     *   {@link Annotation} with the smallest start position
     */
    public Annotation getStartAnnot()
    {
      return startAnnot;
    }

    /**
     * <p>Get the {@link Annotation} in the {@link OverlapBox} that has the
     * largest stop position.</p>
     *
     * @return
     *   {@link Annotation} with the largest stop position
     */
    public Annotation getStopAnnot()
    {
      return stopAnnot;
    }

    /**
     * <p>Get the label that is displayed at the top of the {@link OverlapBox}.</p>
     *
     * @return
     *   String containing the start label
     */
    public String getStartLabel()
    {
      return startLabel.toString();
    }

    /**
     * <p>Get the label that is displayed at the bottom of the
     * {@link OverlapBox}.</p>
     *
     * @return
     *   String containing the stop label
     */
    public String getStopLabel()
    {
      return stopLabel.toString();
    }

    /**
     * <p>Get the width of the start label</p>
     *
     * @return
     *   int value of the width of the start label in pixels
     */
    public int getStartLabelWidth()
    {
      return startLabelWidth;
    }

    /**
     * <p>Get the width of the stop label</p>
     *
     * @return
     *   int value of the width of the stop label in pixels
     */
    public int getStopLabelWidth()
    {
      return stopLabelWidth;
    }

    /**
     * <p>Get the {@link Annotation} displayed in the {@link OverlapBox}.</p>
     *
     * @return
     *   {@link Vector} of {@link Annotation} in {@link OverlapBox}
     */
    public Vector<Annotation> getAnnotation()
    {
      return new Vector<Annotation>(annotation.keySet());
    }

    /**
     * <p>Get the {@link DisplaySegment} associated with the
     * {@link OverlapBox}.</p>
     *
     * @return
     *   null if there is not {@link DisplaySegment} associated with the
     *   {@link OverlapBox}. Otherwise, the associated {@link DisplaySegment}
     *   will be returned.
     */
    public DisplaySegment getSegment()
    {
      return segment;
    }

    /**
     * <p>Get the {@link DisplayMap} associated with the{@link OverlapBox}.</p>
     *
     * @return
     *   null if there is not {@link DisplayMap} associated with the
     *   {@link OverlapBox}. Otherwise, the associated {@link DisplayMap}
     *   will be returned.
     */
    public DisplayMap getDisplayMap()
    {
      if (segment == null)
        return null;
      else
        return segment.getParent();
    }
  }

  /**
   * <p>Internal class that is used to help keep track of where there
   * are no {@link Annotation} next to a {@link DisplayMap}.</p>
   *
   * @author jaaseby@bioneos.com
   *
   */
  public class FeatureGap
  {
    private int labelStart;
    private int labelStop;
    private int annotStart;
    private int annotStop;

    /**
     * <p>Set the interval where there are no {@link Annotation}.<p>
     *
     * @param segment
     *   {@link DisplaySegment} the {@link Annotation} belong to
     * @param start
     *   {@link Annotation} at the start of the gap or the pixel value of where
     *   the {@link Annotation} starts
     * @param stop
     *   {@link Annotation} at the stop of the gap or the pixel value of where
     *   the {@link Annotation} stops
     */
    public void setAnnotationInterval(DisplaySegment segment, Annotation start, Annotation stop)
    {
      setAnnotationInterval((int)(getAnnotationStopCoord(segment, start)), (int)(getAnnotationStartCoord(segment, stop)));
    }
    public void setAnnotationInterval(int start, int stop)
    {
      if (start > stop)
      {
        int swap = start;
        start = stop;
        stop = swap;
      }
      annotStart = start;
      annotStop = stop;
    }

    /**
     *  <p>Set the interval where there are no labels.<p>
     *
     * @param start
     * @param stop
     */
    public void setLabelInterval(int start, int stop)
    {
      labelStart = start;
      labelStop = stop;
    }

    /**
     * <p>Get the start of the label interval.</p>
     *
     * @return
     *   y-coordinate of where the label interval starts
     */
    public int getLabelStart()
    {
      return labelStart;
    }

    /**
     * <p>Get the stop of the label interval.</p>
     *
     * @return
     *   y-coordinate of where the label interval stops
     */
    public int getLabelStop()
    {
      return labelStop;
    }

    /**
     * <p>Get the start of the {@link Annotation} interval.</p>
     *
     * @return
     *   y-coordinate of where the {@link Annotation} interval starts
     */
    public int getAnnotationStart()
    {
      return annotStart;
    }

    /**
     * <p>Get the stop of the {@link Annotation} interval.</p>
     *
     * @return
     *   y-coordinate of where the {@link Annotation} interval stops
     */
    public int getAnnotationStop()
    {
      return annotStop;
    }
  }
  public void addTiming(String operation, long time)
  {
    if (timing)
      mainGUI.getVCMap().addTiming(operation, time);
  }

  public void addTiming(String operation, Vector<Long> time)
  {
    if (timing)
      mainGUI.getVCMap().addTiming(operation, time);
  }
}
