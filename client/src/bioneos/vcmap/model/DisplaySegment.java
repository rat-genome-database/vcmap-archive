package bioneos.vcmap.model;

import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import bioneos.vcmap.gui.MapNavigator.OverlapBox;

/**
 * <p>This class has the information needed to display a certain portion of
 * chromosome. This includes the start and stop position of the displayed part
 * of the chromosome, annotation that is visible at the current zoom level and
 * other information needed to draw a segment properly on the screen.</p>
 *
 * <p>Created on: July 11, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class DisplaySegment
{
  // Variables
  private DisplayMap parent;
  private Chromosome chromosome;
  private int drawingStart;
  private int drawingStop;
  private double unitsPerPercent;
  private SyntenyBlock syntenyBlock;
  private SortedSet<UnitLabel> unitLabels;
  private HashMap<AnnotationSet, TreeSet<DisplayAnnotation>> displayAnnotation;
  private int featureColumns;
  private HashMap<AnnotationSet, Integer>typeFeatureColumns;

  /**
   * This constructor initializes all the variables for this class.
   * The method automatically sets the {@link Chromosome} for this class by
   * using the chromosomeName String passed in and then automatically loads
   * all the markers for that {@link Chromosome} specified by the start and
   * stop variables.
   *
   * @param parent - {@link DisplayMap} this {@link DisplaySegment} belongs to
   * @param chromosomeName - String representation of chromosome name
   * @param start - beginning point of where to start loading data from
   * @param stop - ending point of where to stop loading data too
   */
  public DisplaySegment(DisplayMap parent, String chromosomeName, int start, int stop)
  {
    this.parent = parent;
    drawingStart = start;
    drawingStop = stop;

    syntenyBlock = null;
    displayAnnotation = new HashMap<AnnotationSet, TreeSet<DisplayAnnotation>>();
    unitLabels = new TreeSet<UnitLabel>();
    featureColumns = 0;
    typeFeatureColumns = null;

    // Assign chromosome based of parent map and chromosome name
    try
    {
      if (parent != null)
        chromosome = parent.getMap().getChromosome(chromosomeName);
    }
    catch (Exception e)
    {
      // Do nothing
    }
  }

  /**
   * <p>Get the {@link DisplayMap} parent of the {@link DisplaySegment}.</p>
   *
   * @return
   *   {@link DisplayMap} parent of the {@link DisplaySegment}
   */
  public DisplayMap getParent()
  {
    return parent;
  }

  /**
   * <p>Get the {@link Chromosome} of the {@link DisplaySegment}.</p>
   *
   * @return
   *   {@link Chromosome} of the {@link DisplaySegment}
   */
  public Chromosome getChromosome()
  {
    return chromosome;
  }

  /**
   * <p>Get the start of the {@link DisplaySegment}.</p>
   *
   * @return
   *   int value of the start of the {@link DisplaySegment}
   */
  public int getDrawingStart()
  {
    return drawingStart;
  }

  /**
   * <p>Get the stop of the {@link DisplaySegment}.</p>
   *
   * @return
   *   int value of the stop of the {@link DisplaySegment}
   */
  public int getDrawingStop()
  {
    return drawingStop;
  }

  /**
   * <p>Get the height of the {@link DisplaySegment} as a percentage.</p>
   *
   * @return
   *   double value of the height of the {@link DisplaySegment} as a percentage
   */
  public double getHeight()
  {
    return Math.abs(drawingStop - drawingStart) / unitsPerPercent;
  }

  /**
   * <p>Get the number of units that are displayed per percent.</p>
   *
   * @return
   *   double value of the number of units that are displayed per percent
   */
  public double getUnitsPerPercent()
  {
    return unitsPerPercent;
  }

  /**
   * <p>Get the {@link Annotation} for the {@link DisplaySegment}.</p>
   *
   * @param type
   *   Type of {@link Annotation} to get. null will get all {@link Annotation}.
   * @param start
   *   Start value of the interval of {@link Annotation} to return. The value
   *   must be greater than or equal to the {@link DisplaySegment}'s drawing
   *   start or the value will be changed accordingly.
   * @param stop
   *   Stop value of the interval of {@link Annotation} to return. The value
   *   must be less than or equal to the {@link DisplaySegment}'s drawing
   *   stop or the value will be changed accordingly.
   * @return
   *   {@link Vector} of {@link Annotation} for the given type and in the
   *   given interval. In the case there is no {@link Annotation} in the
   *   interval, an empty {@link Vector} will be returned.
   */
  public Vector<Annotation> getSegmentFeatures()
  {
    return this.getSegmentFeatures(null, drawingStart, drawingStop);
  }
  public Vector<Annotation> getSegmentFeatures(AnnotationSet set)
  {
    return this.getSegmentFeatures(set, drawingStart, drawingStop);
  }
  public Vector<Annotation> getSegmentFeatures(int start, int stop)
  {
    return this.getSegmentFeatures(null, start, stop);
  }
  public Vector<Annotation> getSegmentFeatures(AnnotationSet set, int start, int stop)
  {
    if (drawingStart < drawingStop)
    {
      if (start < drawingStart) start = drawingStart;
      if (stop < drawingStart) stop = drawingStart;

      if (drawingStop < stop) stop = drawingStop;
      if (drawingStop < start) start = drawingStop;
    }
    else
    {
      if (start < drawingStop) start = drawingStop;
      if (stop < drawingStop) stop = drawingStop;

      if (drawingStart < stop) stop = drawingStart;
      if (drawingStart < start) start = drawingStart;
    }

    return chromosome.getAnnotation(set, start, stop);
  }

  /**
   * <p>Determine if a piece of {@link Annotation} is contained by
   * the {@link DisplaySegment}</p>
   *
   * @param annotation
   *   {@link Annotation} to check
   * @return
   *   true - if the {@link Annotation} is contained by this {@link DisplaySegment}
   *   false - if the {@link Annotation} is NOT contained by this {@link DisplaySegment}
   */
  public boolean containsFeature(Annotation annotation)
  {
    if (chromosome == annotation.getChromosome()
      && ((drawingStart <= annotation.getStart() && annotation.getStart() <= drawingStop)
        || (drawingStart <= annotation.getStop() && annotation.getStop() <= drawingStop)
        || (annotation.getStart() <= drawingStart && drawingStop < annotation.getStop())))
    {
      return true;
    }

    return false;
  }

  /**
   * <p>Get the {@link SyntenyBlock} for this {@link DisplaySegment}.</p>
   *
   * @return
   *   {@link SyntenyBlock} if the {@link DisplaySegment} is based on a
   *   {@link SyntenyBlock}, otherwise null will be returned.
   */
  public SyntenyBlock getSyntenyBlock()
  {
    return syntenyBlock;
  }

  /**
   * <p>Set the {@link SyntenyBlock} that is used to determine how the
   * {@link DisplaySegment} is related to the backbone {@link DisplayMap}.</p>
   *
   * @param syntenyBlock
   *   {@link SyntenyBlock} relating the {@link DisplaySegment} to the
   *   backbone {@link DisplayMap}
   */
  public void setSyntenyBlock(SyntenyBlock syntenyBlock)
  {
    this.syntenyBlock = syntenyBlock;
  }

  /**
   * <p>Add a type of {@link DisplayAnnotation}. This does not combine any
   * existing {@link DisplayAnnotation} for this type. This method writes over
   * any previous {@link DisplayAnnotation} of the same type.</p>
   *
   * @param type
   *   {@link String} of the type of {@link Annotation}
   * @param displayAnnotation
   *   {@link Vector} of {@link DisplayAnnotation} for the type
   */
  public void addDisplayAnnotation(AnnotationSet set, TreeSet<DisplayAnnotation> displayAnnotation)
  {
    this.displayAnnotation.put(set, displayAnnotation);
  }

  /**
   * <p>Get the {@link DisplayAnnotation} for the {@link DisplaySegment}.</p>
   *
   * @return
   *   {@link Vector} of {@link DisplayAnnotation} for the
   *   {@link DisplaySegment}
   */
  public TreeSet<DisplayAnnotation> getDisplayAnnotation()
  {
    TreeSet<DisplayAnnotation> displayAnnotation = new TreeSet<DisplayAnnotation>();
    for (AnnotationSet set : chromosome.getAnnotationSets())
      displayAnnotation.addAll(this.displayAnnotation.get(set));

    return displayAnnotation;
  }

  /**
   * <p>Get the {@link DisplayAnnotation} of a certain type
   * for the {@link DisplaySegment}.</p>
   *
   * @param type
   *   {@link String} of the type of {@link Annotation}
   * @return
   *   {@link Vector} of {@link DisplayAnnotation} for the
   *   {@link DisplaySegment}
   */
  public TreeSet<DisplayAnnotation> getDisplayAnnotation(AnnotationSet set)
  {
    if (displayAnnotation.get(set) == null) return new TreeSet<DisplayAnnotation>();
    return displayAnnotation.get(set);
  }

  /**
   * <p>Get the {@link DisplayAnnotation} for this {@link DisplaySegment} based
   * on the y-coordinates of the {@link DisplayAnnotation}.</p>
   *
   * @param type
   *   Type of {@link DisplayAnnotation}
   * @param start
   *   Start y-coordinate of search interval
   * @param stop
   *   Stop y-coordinate of search interval
   * @return
   *   {@link TreeSet} of {@link DisplayAnnotation} that are within the search
   *   interval
   */
  public TreeSet<DisplayAnnotation> getDisplayAnnotation(AnnotationSet set, int start, int stop)
  {
    if (displayAnnotation.get(set) == null) return new TreeSet<DisplayAnnotation>();

    DisplayAnnotation begin = new DisplayAnnotation();
    begin.setLabelYCoord(start);
    DisplayAnnotation end = new DisplayAnnotation();
    end.setLabelYCoord(stop);

    return (TreeSet<DisplayAnnotation>)displayAnnotation.get(set).subSet(begin, end);
  }

  /**
   * <p>Clear all the {@link DisplayAnnotation} for each type of
   * {@link Annotation}</p>
   *
   */
  public void clearDisplayAnnotation()
  {
    displayAnnotation.clear();
  }
  
  /**
   * Remove all {@link UnitLabel}s from this {@link DisplaySegment}.
   */
  public void clearUnitLabels()
  {
    unitLabels.clear();
  }
  
  /**
   * Remove all temporary {@link UnitLabel}s from this DisplaySegment. 
   */
  public void clearTempUnitLabels()
  {
    Vector<UnitLabel> remove = new Vector<UnitLabel>();
    for (UnitLabel u : unitLabels)
      if (u.isTemporary())
        remove.add(u);
    for (UnitLabel u : remove)
      unitLabels.remove(u);
  }

  /**
   * <p>Add a specific {@link UnitLabel}.</p>
   *
   * @param unitLabel
   *   {@link UnitLabel} to add to the {@link DisplaySegment}
   */
  public void addUnitLabel(UnitLabel unitLabel)
  {
    unitLabels.add(unitLabel);
  }

  /**
   * <p>Add a {@link UnitLabel} to the middle somewhere.  The
   * {@link UnitLabel} getting added is a start or stop label
   * for an opened {@link OverlapBox}.</p>
   *
   * @param middle
   *    is this label being added to the middle of the list
   * @param unitLabel
   *    the {@link UnitLabel} to be added to the list
   */
  public void addUnitLabel(boolean middle, UnitLabel unitLabel)
  {
    if (!middle)
      addUnitLabel(unitLabel);

    else
    {
      // create temp set
      TreeSet<UnitLabel> temp = new TreeSet<UnitLabel>();
      temp.add(unitLabel);
      temp.addAll((TreeSet<UnitLabel>)unitLabels.tailSet(unitLabel));

      // remove the label if present in order to add the new one
      for (UnitLabel u : temp)
      {
        removeUnitLabel(u);
        addUnitLabel(u);
      }
    }
  }

  /**
   * <p>Removes an {@link UnitLabel} from the list.</p>
   *
   * @param unitLabel
   *    the {@link UnitLabel} to be removed
   *
   */
  public void removeUnitLabel(UnitLabel unitLabel)
  {
    unitLabels.remove(unitLabel);
  }

  /**
   * <p>Get the {@link UnitLabel}s for this {@link DisplaySegment}.</p>
   *
   * @return
   *   {@link Vector} of {@link UnitLabel}s
   */
  public Vector<UnitLabel> getUnitLabels()
  {
    return new Vector<UnitLabel>(unitLabels);
  }

  /**
   * <p>Set the units per percent.</p>
   *
   * @param unitsPerPercent
   *   double value of the units per percent
   */
  public void setUnitsPerPercent(double unitsPerPercent)
  {
    this.unitsPerPercent = unitsPerPercent;
  }

  /**
   * <p>Determine the number of feature columns of the given type
   * for this {@DisplaySegment}.</p>
   *
   * @param type
   *    the type to determine the feature columns for
   */
  public void determineFeatureColumns(AnnotationSet set)
  {
    int column = 0;

    if (typeFeatureColumns == null)
      typeFeatureColumns = new HashMap<AnnotationSet, Integer>();

    if (!displayAnnotation.containsKey(set))
      return;

    for (DisplayAnnotation da : displayAnnotation.get(set))
      column = Math.max(column, da.getMaxColumn());

    // 1 is because column numbering in DA begins at 0
    typeFeatureColumns.put(set, new Integer(column + 1));
  }

  /**
   * <p>Determine the total number of feature columns for
   * this {@link DispalySegment}.</p>
   *
   */
  public void determineFeatureColumns()
  {
    if (typeFeatureColumns == null) return;

    for (AnnotationSet set : typeFeatureColumns.keySet())
      featureColumns += typeFeatureColumns.get(set);
  }

  /**
   * <p>Returns the total number of feature columns for this {@link DisplaySegment}.</p>
   *
   * @return
   *    int value of the total number of feature columns for this {@link DisplaySegment}
   */
  public int getFeatureColumns()
  {
    if (featureColumns == 0) determineFeatureColumns();
    return featureColumns;
  }

  /**
   * <p>Returns the number of feature columns for this {@link DisplaySegment}
   * for the given type.</p>
   *
   * @return
   *    int value of the number of feature columns for this {@link DisplaySegment}
   *    for the given type
   */
  public int getFeatureColumns(AnnotationSet set)
  {
    if (typeFeatureColumns == null) determineFeatureColumns(set);

    if (!typeFeatureColumns.containsKey(set))
      return 0;
    else
      return typeFeatureColumns.get(set);
  }

  /**
   * <p>Resets the number of feature columns for this {@link DisplaySegment}.</p>
   */
  public void resetFeatureColumns()
  {
    featureColumns = 0;
    typeFeatureColumns = null;
  }
}
