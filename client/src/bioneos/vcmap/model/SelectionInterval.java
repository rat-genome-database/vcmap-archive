package bioneos.vcmap.model;

import java.awt.Rectangle;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.options.GlobalOptions;

/**
 * <p>Stores the data to represent a selection interval in the
 * {@link MapNavigator}.</p>
 *
 * <p>Created on: January 9th, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class SelectionInterval
{
  // Private variables
  private int beginning;
  private int start;
  private int stop;
  private boolean beingSet;
  private boolean visible;
  private boolean inverted;
  private DisplaySegment segment;
  private MapNavigator mapNavigator;
  private Selection selection;
  private Annotation annotation;
  private HashSet<SelectionInterval> children;
  private SelectionInterval parent;
  private Annotation annotStart;
  private Annotation annotStop;

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  /**
   * <p>{@link SelectionInterval} constructor initializes basic variables
   * needed for {@link SelectionInterval} to work properly.</p>
   *
   * @param mapNavigator
   *   {@link MapNavigator} the {@link SelectionInterval} will be displayed
   *   within
   */
  public SelectionInterval(MapNavigator mn)
  {
    mapNavigator = mn;
    selection = mapNavigator.getSelection();
    children = new HashSet<SelectionInterval>();
  }

  /**
   * <p>Set the {@link DisplaySegment} the {@link SelectionInterval} is
   * displayed on.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link SelectionInterval} is on
   */
  public void setSegment(DisplaySegment segment)
  {
    this.segment = segment;
  }

  /**
   * <p>Sets what {@link DisplaySegment} the {@link SelectionInterval} will
   * belong to and sets the starting location for the
   * {@link SelectionInterval}</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link SelectionInterval} belongs to
   * @param beginning
   *   y-coord of the starting location of the {@link SelectionInterval} in
   *   pixels
   */
  public void setInterval(DisplaySegment segment, int beginning)
  {
    // Convert to local coordinate system for segment
    this.beginning = beginning;
    this.segment = segment;

    if (segment.getDrawingStop() < segment.getDrawingStart())
      inverted = true;
    else
      inverted = false;

    this.beginning = convertToMapLocation(beginning);
    start = this.beginning;
    stop = this.beginning;
    visible = true;
    beingSet = true;
  }

  /**
   * <p>Sets what {@link DisplaySegment} the {@link SelectionInterval} will
   * belong to and sets the bounds of the {@link SelectionInterval} to
   * the start and stop position of the {@link Annotation}</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link SelectionInterval} belongs to
   * @param annotation
   *   {@link SelectionInterval}'s bounds will be set from the
   *   {@link Annotation}'s start and stop
   */
  public void setInterval(DisplaySegment segment, Annotation annotation)
  {
    this.annotation = annotation;

    setInterval(segment, annotation.getStart(), annotation.getStop());
  }

  /**
   * <p>Sets what {@link DisplaySegment} the {@link SelectionInterval} will
   * belong to and sets the bounds of the {@link SelectionInterval} to
   * the start and stop position.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link SelectionInterval} belongs to
   * @param start
   *   start position of the {@link SelectionInterval}
   * @param stop
   *   stop position of the {@link SelectionInterval}
   */
  public void setInterval(DisplaySegment segment, int start, int stop)
  {
    this.segment = segment;

    visible = true;
    beingSet = false;
    this.start = start;
    this.stop = stop;

    if (segment.getDrawingStop() < segment.getDrawingStart())
    {
      if (this.start < segment.getDrawingStop())
        this.start = segment.getDrawingStop();
      if (this.stop > segment.getDrawingStart())
        this.stop = segment.getDrawingStart();

      inverted = true;
    }
    else
    {
      if (this.start < segment.getDrawingStart())
        this.start = segment.getDrawingStart();
      if (this.stop > segment.getDrawingStop())
        this.stop = segment.getDrawingStop();

      inverted = false;
    }

    beginning = this.start;

    if (this.start == this.stop)
      this.stop++;
  }

  /**
   * <p>Changes the end position of the {@link SelectionInterval}. The end
   * position may be the start or stop position of the interval based on
   * whether the point is less than or greater than the beginning point.<p>
   *
   * @param beginning
   *   y-coordinate of second (end) point for map interval
   */
  public void setEnd(int currentEnd)
  {
    if (!beingSet)
      return;

    currentEnd = convertToMapLocation(currentEnd);

    // Set the top and bottom bound for the interval
    if (currentEnd <= beginning)
    {
      stop = beginning;
      start = currentEnd;

      if (!inverted && start < segment.getDrawingStart())
        start = segment.getDrawingStart();
      else if (inverted && start < segment.getDrawingStop())
        start = segment.getDrawingStop();
    }
    else
    {
      start = beginning;
      stop = currentEnd;

      if (!inverted && stop > segment.getDrawingStop())
        stop = segment.getDrawingStop();
      else if (inverted && stop > segment.getDrawingStart())
        stop = segment.getDrawingStart();
    }
  }

  /**
   * <p>Tells whether the {@link SelectionInterval}'s initial interval
   * is being set or not.</p>
   *
   * @return
   *   true - {@link SelectionInterval} is being set
   *   false - {@link SelectionInterval} is NOT being set
   */
  public boolean isBeingSet()
  {
    return beingSet;
  }

  /**
   * <p>Should be called when the user is done selecting the
   * {@link SelectionInterval}.</p>
   *
   */
  public void stopSetting()
  {
    if (beingSet)
    {
      beingSet = false;
      selection.createCorrespondingIntervals(this);
      selection.verifyIntervals();
    }
  }

  /**
   * <p>Set the parent {@link SelectionInterval}.</p>
   *
   * @param parent
   *   parent {@link SelectionInterval}
   */
  public void setParent(SelectionInterval parent)
  {
    this.parent = parent;
  }

  /**
   * <p>Set whether the {@link SelectionInterval} is visible or not.</p>
   *
   * @param b
   *   true - visible
   *   false - NOT visible
   */
  public void setVisible(boolean b)
  {
    visible = b;
  }

  /**
   * <p>Get if the {@link SelectionInterval} is visible or not.</p>
   *
   * @return
   *   true - is visible
   *   false - is NOT visible
   */
  public boolean isVisible()
  {
    return visible;
  }

  /**
   * <p>Get the parent {@link SelectionInterval} of this
   * {@link SelectionInterval}.</p>
   *
   * @return
   *   parent {@link SelectionInterval} or null if there is no parent
   */
  public SelectionInterval getParent()
  {
    return parent;
  }

  /**
   * <p>Check if this {@link SelectionInterval} has a parent.</p>
   *
   * @return
   *   true - has a parent
   *   false - does not have a parent
   */
  public boolean isChild()
  {
    return parent != null;
  }

  /**
   * <p>Check if this {@link SelectionInterval} is a child.</p>
   *
   * @return
   *   true - is a parent
   *   false - is NOT have a parent
   */
  public boolean isParent()
  {
    return parent == null;
  }

  /**
   * <p>The {@link Annotation} bounds help a child {@link SelectionInterval}
   * keep track what area of its parent {@link SelectionInterval} it corresponds
   * back to.</p>
   *
   * @param start
   *   {@link Annotation} with the start position.
   * @param stop
   *   {@link Annotation} with the stop position.
   */
  public void setAnnotationBounds(Annotation start, Annotation stop)
  {
    annotStart = start;
    annotStop = stop;
  }

  /**
   * <p>Get the {@link Annotation} with the start position.</p>
   *
   * @return
   *   {@link Annotation} with the start position. null if there
   *   is not {@link Annotation}
   */
  public Annotation getAnnotationStart()
  {
    return annotStart;
  }

  /**
   * <p>Get the {@link Annotation} with the stop position.</p>
   *
   * @return
   *   {@link Annotation} with the stop position. null if there
   *   is not {@link Annotation}
   */
  public Annotation getAnnotationStop()
  {
    return annotStop;
  }

  /**
   * <p>Add a set of {@link SelectionInterval} as children for this
   * {@link SelectionInterval}.</p>
   *
   * @param children
   *   children {@link SelectionInterval}
   */
  public void addChildren(Vector<SelectionInterval>  children)
  {
    if (children == null) return;

    for (SelectionInterval child : children)
      addChild(child);
  }

  /**
   * <p>Add a {@link SelectionInterval} as a child for this
   * {@link SelectionInterval}.</p>
   *
   * @param child
   *   child {@link SelectionInterval}
   */
  public void addChild(SelectionInterval child)
  {
    if (child == null || children.contains(child))
      return;

    child.setParent(this);
    children.add(child);
  }

  /**
   * <p>Get the children {@link SelectionInterval}s for this
   * {@link SelectionInterval}.</p>
   *
   * @return
   *   {@link Vector} of children {@link SelectionInterval}s or an empty
   *   {@link Vector}
   */
  public Vector<SelectionInterval> getChildren()
  {
    return new Vector<SelectionInterval>(children);
  }

  /**
   * <p>Clear all the children for this {@link SelectionInterval}.</p>
   *
   */
  public void clearChildren()
  {
    children.clear();
  }

  /**
   * <p>Remove a specific child from this parent {@link SelectionInterval}.</p>
   *
   * @param interval
   *   child {@link SelectioInterval} to remove
   */
  public void removeChild(SelectionInterval interval)
  {
    if (interval == null)
      return;

    children.remove(interval);
  }

  /**
   * <p>Helper method that converts the y-coordinate (in pixels) to the
   * local position on the display segment.</p>
   *
   * @param pixelYCoordinate
   *   Y-coordinate of a location (in pixels)
   * @return
   *   -1 if the segment {@link DisplaySegment} is not set, otherwise
   *   returns the location position on the {@link DisplaySegment}
   */
  private int convertToMapLocation(int pixelYCoordinate)
  {
    if (segment == null || mapNavigator == null)
      return -1;

    double positionPercent = (double)(pixelYCoordinate - mapNavigator.getSegmentYCoord(segment))
      / mapNavigator.getSegmentHeight(segment);
    if (!inverted)
      return (int)(positionPercent * (double)(segment.getDrawingStop() - segment.getDrawingStart()) + segment.getDrawingStart());
    else
      return (int)(segment.getDrawingStart() - positionPercent * (double)(segment.getDrawingStart() - segment.getDrawingStop()));
  }

  /**
   * <p>Get the start position relative to the segment
   * {@link DisplaySegment}.</p>
   *
   * @return
   *   int value of the start position on the segment
   */
  public int getStart()
  {
    return start;
  }

  /**
   * <p>Get the stop position relative to the segment
   * {@link DisplaySegment}.</p>
   *
   * @return
   *   int value of the stop position on the segment
   */
  public int getStop()
  {
    return stop;
  }

  /**
   * <p>Get the {@link DisplaySegment} the {@link SelectionInterval} belongs to.</p>
   *
   * @return
   *   {@link DisplaySegment} the {@link SelectionInterval is drawn on
   */
  public DisplaySegment getSegment()
  {
    return segment;
  }

  /**
   * <p>{@link Rectangle} representing the {@link SelectionInterval} is returned.</p>
   *
   * @param options
   *   A reference to the active {@link GlobalOptions} so that calculations can be
   *   correctly based on current settings.
   * @return
   *   If the {@link SelectionInterval} is not properly setup a {@link Rectangle}
   *   with the values (0,0,0,0) will be returned. Otherwise, a
   *   {@link Rectangle} representing the {@link SelectionInterval} is returned
   */
  public Rectangle getRectangle(GlobalOptions options)
  {
    if (segment == null || mapNavigator == null || start < 0 || stop < 0)
      return new Rectangle(0,0,0,0);

    DisplayMap displayMap = segment.getParent();

    int x = mapNavigator.getDisplayMapXCoord(displayMap) + displayMap.getFeatureColumnsWidth(options);
    int width = options.getIntOption("segmentWidth") + 1;
    int y = -1;
    int height = -1;
    if (!inverted)
    {
      y = (int)((((start - segment.getDrawingStart()) / segment.getUnitsPerPercent()) / 100)
        * (double)mapNavigator.getDrawingHeight() + mapNavigator.getSegmentYCoord(segment));
      height = (int)((((stop - segment.getDrawingStart()) / segment.getUnitsPerPercent()) / 100)
        * (double)mapNavigator.getDrawingHeight() + mapNavigator.getSegmentYCoord(segment) - y);
    }
    else
    {
      y = (int)((((segment.getDrawingStart() - stop) / segment.getUnitsPerPercent()) / 100)
          * (double)mapNavigator.getDrawingHeight() + mapNavigator.getSegmentYCoord(segment));
      height = (int)((((segment.getDrawingStart() - start) / segment.getUnitsPerPercent()) / 100)
        * (double)mapNavigator.getDrawingHeight() + mapNavigator.getSegmentYCoord(segment) - y);
    }

    if (height == 0) height++;
    return new Rectangle(x, y, width, height);
  }


  /**
   * <p>Get all the {@link Annotation} within the bounds of the
   * {@link SelectionInterval}.</p>
   *
   * @return
   *   {@link Vector} of all the {@link Annotation} whose start and stop
   *   is within the bounds of the {@link SelectionInterval}
   */
  public Vector<Annotation> getAnnotation()
  {
    if (segment == null) return new Vector<Annotation>();

    return segment.getSegmentFeatures(start, stop);
  }

  /**
   * <p>Tells if a piece of {@link Annotation} is located within a
   * {@link SelectionInterval}.</p>
   *
   * @param annotation
   *   {@link Annotation} to check if it is posited inbetween the
   *   {@link SelectionInterval}'s start and stop bounds
   * @return
   *   true - If the {@link Annotation} is within the bounds
   *   false - If the {@link Annotation} is NOT within the bounds
   */
  public boolean containsAnnotation(Annotation annotation)
  {
    if (segment != null)
    {
      if (annotation.getChromosome() == segment.getChromosome()
        && ((start <= annotation.getStart() && annotation.getStart() <= stop)
          || (start <= annotation.getStop() && annotation.getStop() <= stop)))
      {
        return true;
      }
    }

    return false;
  }

  /**
   * <p>Returns the URL of a {@link SelectionInterval}</p>
   *
   * @return
   *   String containing the {@link SelectionInterval}'s URL
   * @throws SQLException
   */
  public String getURL()
    throws SQLException
  {
    Chromosome chr = segment.getChromosome();
    if (chr == null)
    {
      logger.error("Could not determine the chromosome for this selectionInterval.");
      return null;
    }

    String chrID = chr.getName().split("chr")[1];
    HashMap<String, String> params = new HashMap<String, String>();
    MapData map = segment.getParent().getMap();
    if (map == null)
    {
      logger.error("Could not determine the map for this selectionInterval.");
      return null;
    }
    params.put("taxID", "" + map.getTaxID());
    params.put("chrID", chrID);
    params.put("start", "" + start);
    params.put("stop", "" + stop);

    return Factory.getURL(map.getSourceId(), "chromosome", "interval", params);
  }
}
