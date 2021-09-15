package bioneos.vcmap.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.text.Segment;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MapNavigator;

/**
 * <p>Manages all the selected {@link SelectionInterval}s, {@link Annotation},
 * {@link DisplaySegment}s and {@link DisplayMap}s for a {@link MapNavigator}.
 *
 * <p>Created on: January 29th, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class Selection
{
  private MapNavigator mapNavigator;
  private DisplayMap selectedMap;
  private Vector<DisplaySegment> segments;
  private SelectionInterval selectedInterval;
  private Annotation lastAnnotAdded;
  private HashMap<Annotation, DisplaySegment> annotToSeg;
  private HashMap<DisplaySegment, Vector<Annotation>> segToAnnot;
  private HashMap<DisplaySegment, HashSet<SelectionInterval>> intervals;

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  /**
   * <p>{@link Selection} constructor. Set up the variables needed for this
   * {@link Selection} to work properly.</p>
   *
   * @param mapNavigator
   *   {@link MapNavigator} the {@link Selection} belongs to
   */
  public Selection(MapNavigator mapNavigator)
  {
    this.mapNavigator = mapNavigator;
    annotToSeg = new HashMap<Annotation, DisplaySegment>();
    segToAnnot = new HashMap<DisplaySegment, Vector<Annotation>>();
    intervals = new HashMap<DisplaySegment, HashSet<SelectionInterval>>();
    segments = new Vector<DisplaySegment>();
  }

  /**
   * <p>Set the selected {@link DisplayMap}</p>
   *
   * @param selectedMap
   *   selected {@link DisplayMap}
   */
  public void setMap(DisplayMap selectedMap)
  {
    this.selectedMap = selectedMap;
  }

  /**
   * <p>Get the selected map.</p>
   *
   * @return
   *   {@link DisplayMap} that is selected or null if there is no selected map.
   */
  public DisplayMap getMap()
  {
    return selectedMap;
  }

  /**
   * <p>Clear all {@link DisplaySegment}s that are currently selected and
   * select the {@link DisplaySegment} that was passed in.
   *
   * @param selectedSegment
   *   {@link DisplaySegment} to select
   */
  public void setSegment(DisplaySegment selectedSegment)
  {
    if (selectedSegment == null)
      return;

    segments.clear();
    segments.add(selectedSegment);
  }

  /**
   * <p>Add a {@link DisplaySegment} to the list of selected
   * {@link DisplaySegment}s</p>
   *
   * @param selectedSegment
   *   {@link DisplaySegment} to add to the list of selected.
   */
  public void addSegment(DisplaySegment selectedSegment)
  {
    if (selectedSegment == null)
      return;

    if (!segments.contains(selectedSegment))
      segments.add(selectedSegment);
  }

  /**
   * <p>Remove a {@link DisplaySegment} that is currently selected.</p>
   *
   * @param selectedSegment
   *   {@link DisplaySegment} to unselect
   */
  public void removeSegment(DisplaySegment selectedSegment)
  {
    if (selectedSegment == null)
      return;

    segments.remove(selectedSegment);
  }

  /**
   * <p>Get the {@link DisplaySegment} that was selected last.</p>
   *
   * @return
   *   {@link DisplaySegment} that was selected last or null if there
   *   is not a selected {@link DisplaySegment}
   */
  public DisplaySegment getSegment()
  {
    if (segments.size() > 0)
      return segments.lastElement();
    else
      return null;
  }

  /**
   * <p>Get the {@link DisplaySegment}s currently selected.</p>
   *
   * @return
   *   {@link Vector} of {@link DisplaySegments} that are selected
   */
  public Vector<DisplaySegment> getSegments()
  {
    return segments;
  }

  /**
   * <p>Get the {@link SelectionInterval} that was added last.</p>
   *
   * @return
   *   {@link SelectionInterval} last added or null if there are no
   *   {@link SelectionInterval}s being displayed
   */
  public SelectionInterval getInterval()
  {
    return selectedInterval;
  }

  /**
   * <p>Get all the displayed {@link SelectionInterval}s.</p>
   *
   * @return
   *   {@link Vector} of all displayed {@link SelectionInterval}s
   */
  public Vector<SelectionInterval> getIntervals()
  {
    Vector<SelectionInterval> selections = new Vector<SelectionInterval>();

    for (DisplaySegment segment : intervals.keySet())
      selections.addAll(intervals.get(segment));

    return selections;
  }

  /**
   * <p>Get all the displayed {@link SelectionInterval}s for a particular
   * {@link DisplaySegment}</p>
   *
   * @param segment
   *   {@link DisplaySegment} to get {@link SelectionInterval}s for
   * @return
   *   {@link Vector} of all {@link SelectionInterval}s for the
   *   {@link DisplaySegment}
   */
  public Vector<SelectionInterval> getIntervals(DisplaySegment segment)
  {
    Vector<SelectionInterval> selections = new Vector<SelectionInterval>();

    if (intervals.get(segment) == null) return selections;

    selections.addAll(intervals.get(segment));

    return selections;
  }

  /**
   * <p>Set the {@link SelectionInterval} that is selected.</p>
   *
   * @param interval
   *   selected {@link SelectionInterval}
   */
  public void setInterval(SelectionInterval interval)
  {
    selectedInterval = interval;
  }

  /**
   * <p>Add a {@link SelectionInterval} to the {@link Selection}. If the new
   * interval is not being set when added, the interval is automatically
   * verified.</p>
   *
   * @param interval
   *   {@link SelectionInterval} to add to the {@link Selection}
   */
  public void addInterval(SelectionInterval interval)
  {
    if (interval == null) return;

    // Add interval to list of intervals
    HashSet<SelectionInterval> selections = intervals.get(interval.getSegment());
    if (selections == null) selections = new HashSet<SelectionInterval>();
    selections.add(interval);
    intervals.put(interval.getSegment(), selections);
  }

  /**
   * <p>Remove an {@link SelectionInterval} from the {@link Selection}.</p>
   *
   * @param interval
   *   {@link SelectionInterval} to remove
   */
  public void removeInterval(SelectionInterval interval)
  {
    if (interval == null) return;
    else if (intervals.get(interval.getSegment()) == null) return;
    else intervals.get(interval.getSegment()).remove(interval);
  }

  /**
   * <p>Creates the correspoding {@link SelectionInterval}s to a specific
   * {@link SelectionInterval} and adds the new intervals to be displayed.</p>
   *
   * @param interval
   *   {@link SelectionInterval} to find corresponding
   *   {@link SelectionInterval}s for
   */
  protected void createCorrespondingIntervals(SelectionInterval interval)
  {
    long start = System.nanoTime();
    if (interval == null) return;
    logger.debug("Creating corresponding intervals");
    HashMap<DisplaySegment, Vector<Annotation>> segToAnnot = new HashMap<DisplaySegment, Vector<Annotation>>();
    HashMap<Annotation, Annotation> sibToSib = new HashMap<Annotation, Annotation>();

    // Clear children
    long begin = System.nanoTime();
    for (SelectionInterval child : interval.getChildren())
      removeInterval(child);
    interval.clearChildren();
    mapNavigator.addTiming("Selection - createCorrespondingIntervals - Clear Children", System.nanoTime() - begin);

    // Relate each annotation within interval sibling to a display segment
    begin = System.nanoTime();
    for (Annotation annot : interval.getAnnotation())
    {
      for (Annotation sibling : annot.getSiblings())
      {
        DisplaySegment siblingSeg = null;

        for (DisplayMap displayMap : mapNavigator.getDisplayMaps())
        {
          if (displayMap == interval.getSegment().getParent()) continue;

          for (DisplaySegment seg : displayMap.getSegments())
          {
            if (seg.containsFeature(sibling))
            {
              siblingSeg = seg;
              break;
            }
          }

          if (siblingSeg != null)
            break;
        }

        if (siblingSeg == null) continue;

        Vector<Annotation> annotation = segToAnnot.get(siblingSeg);
        if (annotation == null) annotation = new Vector<Annotation>();
        annotation.add(sibling);
        segToAnnot.put(siblingSeg, annotation);

        sibToSib.put(sibling, annot);
      }
    }
    mapNavigator.addTiming("Selection - createCorrespondingIntervals - Match Annotation to Segments", System.nanoTime() - begin);

    // Create children intervals
    begin = System.nanoTime();
    for (DisplaySegment seg : segToAnnot.keySet())
    {
      Vector<Annotation> annotation = segToAnnot.get(seg);

      if (annotation == null)
      {
        logger.debug("Error: Could not create child interval.");
        continue;
      }

      for (Annotation annot : annotation)
      {
        SelectionInterval newInterval = new SelectionInterval(mapNavigator);
        newInterval.setInterval(seg, annot);
        newInterval.setParent(interval);
        newInterval.setAnnotationBounds(sibToSib.get(annot), sibToSib.get(annot));
        interval.addChild(newInterval);

        addInterval(newInterval);
      }
    }
    mapNavigator.addTiming("Selection - createCorrespondingIntervals - Create Intervals", System.nanoTime() - begin);
    mapNavigator.addTiming("Selection - createCorrespondingIntervals", System.nanoTime() - start);
  }

  /**
   * <p>These methods check for overlapping {@link SelectionInterval}s and
   * merge, delete, and create new {@link SelectionInterval}s when appropriate.</p>
   *
   *@param intervalType
   *  'parents' verifies only parent {@link SelectionInterval}s
   *  'children' verifies only child {@link SelectionInterval}s
   *  null or anything else verifies all {@link SelectionInterval}s at once
   */
  // FIXME - selection boxes
  public void verifyIntervals()
  {
    verifyChildIntervals();
    verifyParentIntervals();
  }
  public void verifyParentIntervals()
  {
    verifyIntervals("parents");
  }
  public void verifyChildIntervals()
  {
    verifyIntervals("children");
  }
  private void verifyIntervals(String intervalType)
  {
    long timerStart = System.nanoTime();
    long beginning = System.nanoTime();
    if (intervalType == null) intervalType = "";
    HashSet<SelectionInterval> removeIntervals = new HashSet<SelectionInterval>();
    HashSet<SelectionInterval> addIntervals = new HashSet<SelectionInterval>();
    String preMessage = "";
    boolean parents;
    boolean children;
    int threshold = 1000000;//threshold for "merging" the selected intervals

    if (intervalType.equals("parents"))
    {
      parents = true;
      children = false;
      preMessage = "Selection - Verify Parent Intervals";
    }
    else if (intervalType.equals("children"))
    {
      parents = false;
      children = true;
      preMessage = "Selection - Verify Parent Intervals";
    }
    else
    {
      parents = true;
      children = true;
    }
    mapNavigator.addTiming(preMessage + " - Initialize", System.nanoTime() - beginning);
    for (DisplaySegment seg : intervals.keySet())
    {
      SelectionInterval prevInterval = null;

      Vector<SelectionInterval> selection = getIntervals(seg);

      Collections.sort(selection, new SortIntervalByStart());


      for (SelectionInterval nextInterval : selection)
      {
        mapNavigator.addTiming(preMessage + " - Traverse Interval", System.nanoTime() - beginning);
        beginning = System.nanoTime();
        long begin = System.nanoTime();
        threshold = (int) (nextInterval.getSegment().getChromosome().getLength() * 0.01);
        if (!children && parents && nextInterval.isChild()) continue;
        if (children && !parents && nextInterval.isParent()) continue;

        if (prevInterval == null)
        {
          prevInterval = nextInterval;
          continue;
        }
        if (nextInterval == prevInterval || nextInterval.getSegment() != prevInterval.getSegment())
          continue;
        mapNavigator.addTiming(preMessage + " - Traverse Interval - Verify Process",
            System.nanoTime() - begin);
        //
        // Check if current prevInterval contains other prevInterval
        //
        begin = System.nanoTime();
        if (prevInterval.getStart() <= nextInterval.getStart() && nextInterval.getStop() <= prevInterval.getStop())
        {
          if (prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null)
          {
            nextInterval.setVisible(false);
          }
          else
          {
            removeIntervals.add(nextInterval);
            if (nextInterval.isChild()) nextInterval.getParent().removeChild(nextInterval);
            prevInterval.addChildren(nextInterval.getChildren());
          }

          continue;
        }
        mapNavigator.addTiming(preMessage + " - Traverse Interval - check if prevInt contains other prevInt",
            System.nanoTime() - begin);

        //
        // Check if new prevInterval is contained in existing prevInterval
        //
        begin = System.nanoTime();
        if (nextInterval.getStart() <= prevInterval.getStart() && prevInterval.getStop() <= nextInterval.getStop())
        {
          if (prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null)
          {
            prevInterval.setVisible(false);
          }
          else
          {
            removeIntervals.add(prevInterval);
            if (prevInterval.isChild()) prevInterval.getParent().removeChild(prevInterval);
            nextInterval.addChildren(prevInterval.getChildren());
          }

          prevInterval = nextInterval;
          continue;
        }
        mapNavigator.addTiming(preMessage + " - Traverse Interval - check if prevInt contained in existing prevInt",
            System.nanoTime() - begin);

        //
        // Check if there is an overlap
        //
        begin = System.nanoTime();
        if (prevInterval.getStart() <= nextInterval.getStart() && nextInterval.getStart() <= prevInterval.getStop())
        {
          // create new interval
          long begin2 = System.nanoTime();
          if (prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null)
          {
            prevInterval.setVisible(false);
            nextInterval.setVisible(false);
          }
          else
          {
            removeIntervals.add(nextInterval);
            removeIntervals.add(prevInterval);
            if (nextInterval.isChild()) nextInterval.getParent().removeChild(nextInterval);
            if (prevInterval.isChild()) prevInterval.getParent().removeChild(prevInterval);
          }

          int start = nextInterval.getStart();
          if (prevInterval.getStart() < start)
            start = prevInterval.getStart();
          int stop = prevInterval.getStop();
          if (nextInterval.getStop() > stop)
            stop = nextInterval.getStop();

          DisplaySegment segment = prevInterval.getSegment();
          if (segment.getDrawingStop() < segment.getDrawingStart())
          {
            if (start < segment.getDrawingStop())
              start = segment.getDrawingStop();
            if (stop > segment.getDrawingStart())
              stop = segment.getDrawingStart();
          }
          else
          {
            if (start < segment.getDrawingStart())
              start = segment.getDrawingStart();
            if (stop > segment.getDrawingStop())
              stop = segment.getDrawingStop();
          }

          SelectionInterval newInterval = new SelectionInterval(mapNavigator);
          newInterval.setInterval(segment, start, stop);
          mapNavigator.addTiming(preMessage + " - Traverse Interval - check for overlap - create Interval",
              System.nanoTime() - begin2);
          begin2 = System.nanoTime();
          if (children)
          {
            Annotation selAnnotStart = nextInterval.getAnnotationStart();
            Annotation selAnnotStop = nextInterval.getAnnotationStop();
            Annotation intAnnotStart = prevInterval.getAnnotationStart();
            Annotation intAnnotStop = prevInterval.getAnnotationStop();
            Annotation annotStart = null;
            Annotation annotStop = null;

            if (selAnnotStart.getStart() < intAnnotStart.getStart())
              annotStart = selAnnotStart;
            else
              annotStart = intAnnotStart;

            if (selAnnotStop.getStop() > intAnnotStop.getStop())
              annotStop = selAnnotStop;
            else
              annotStop = intAnnotStop;

            newInterval.setAnnotationBounds(annotStart, annotStop);

            if (!(prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null))
              nextInterval.getParent().addChild(newInterval);
          }
          mapNavigator.addTiming(preMessage + " - Traverse Interval - check for overlap - add Children",
              System.nanoTime() - begin2);
          begin2 = System.nanoTime();
          newInterval.addChildren(prevInterval.getChildren());
          newInterval.addChildren(nextInterval.getChildren());

          addIntervals.add(newInterval);
          prevInterval = newInterval;
          mapNavigator.addTiming(preMessage + " - Traverse Interval - check for overlap - add Interval",
              System.nanoTime() - begin2);

          continue;
        }
        mapNavigator.addTiming(preMessage + " - Traverse Interval - check for overlap",
            System.nanoTime() - begin);

        //
        // Check for merge
        //
        long mergeStart = System.nanoTime();
        if (prevInterval != selectedInterval && nextInterval != selectedInterval)
        {
          boolean merge = true;
          Vector<Annotation> segAnnot;
          begin = System.nanoTime();
          long begin2 = System.nanoTime();
          // Get segment annotation between intervals
          if (nextInterval.getStop() < prevInterval.getStart())
            segAnnot = prevInterval.getSegment().getSegmentFeatures(nextInterval.getStop() + 1, prevInterval.getStart() - 1);
          else
            segAnnot = prevInterval.getSegment().getSegmentFeatures(prevInterval.getStop() + 1, nextInterval.getStart() - 1);
          mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge - Initialize - get Segment Annotation",
              System.nanoTime() - begin2);
          begin2 = System.nanoTime();
          //determine if change is less than the threshold
          if(Math.abs(nextInterval.getStart()-prevInterval.getStop()) > threshold)
            merge = false;
          mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge - Initialize - ABS function",
              System.nanoTime() - begin2);
          mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge - Initialize",
              System.nanoTime() - begin);
          // Determine if we should merge intervals
          begin = System.nanoTime();
          for (Annotation annot : segAnnot)
          {
            if (annot.getSiblings().size() > 0)
            {
              if (children)
              {
                for (Annotation sibling : annot.getSiblings())
                {
                  if (sibling.getChromosome() == nextInterval.getParent().getSegment().getChromosome())
                  {
                    merge = false;
                    break;
                  }
                }
              }

              if (!merge)
                break;
            }
          }
          mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge - Determine Merge",
              System.nanoTime() - begin);
          begin = System.nanoTime();
          if (merge)
          {
            // Remove old intervals
            if (prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null)
            {
              prevInterval.setVisible(false);
              nextInterval.setVisible(false);
            }
            else
            {
              removeIntervals.add(nextInterval);
              removeIntervals.add(prevInterval);
              if (nextInterval.isChild()) nextInterval.getParent().removeChild(nextInterval);
              if (prevInterval.isChild()) prevInterval.getParent().removeChild(prevInterval);
            }

            // Determine start and stop
            int start = nextInterval.getStart();
            if (prevInterval.getStart() < start)
              start = prevInterval.getStart();
            int stop = prevInterval.getStop();
            if (nextInterval.getStop() > stop)
              stop = nextInterval.getStop();

            // Check bounds of new start and stop
            DisplaySegment segment = prevInterval.getSegment();
            if (segment.getDrawingStop() < segment.getDrawingStart())
            {
              if (start < segment.getDrawingStop())
                start = segment.getDrawingStop();
              if (stop > segment.getDrawingStart())
                stop = segment.getDrawingStart();
            }
            else
            {
              if (start < segment.getDrawingStart())
                start = segment.getDrawingStart();
              if (stop > segment.getDrawingStop())
                stop = segment.getDrawingStop();
            }

            // Create prevInterval
            SelectionInterval newInterval = new SelectionInterval(mapNavigator);
            newInterval.setInterval(segment, start, stop);
            newInterval.setParent(nextInterval.getParent());

            if (children)
            {
              Annotation selAnnotStart = nextInterval.getAnnotationStart();
              Annotation selAnnotStop = nextInterval.getAnnotationStop();
              Annotation intAnnotStart = prevInterval.getAnnotationStart();
              Annotation intAnnotStop = prevInterval.getAnnotationStop();
              Annotation annotStart = null;
              Annotation annotStop = null;

              if (selAnnotStart.getStart() < intAnnotStart.getStart())
                annotStart = selAnnotStart;
              else
                annotStart = intAnnotStart;

              if (selAnnotStop.getStop() > intAnnotStop.getStop())
                annotStop = selAnnotStop;
              else
                annotStop = intAnnotStop;

              newInterval.setAnnotationBounds(annotStart, annotStop);

              if (!(prevInterval.getParent() != nextInterval.getParent() && prevInterval.getParent() != null))
                nextInterval.getParent().addChild(newInterval);
            }

            newInterval.addChildren(prevInterval.getChildren());
            newInterval.addChildren(nextInterval.getChildren());

            addIntervals.add(newInterval);
            prevInterval = newInterval;
            mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge - Merge",
                System.nanoTime() - begin);
            mapNavigator.addTiming(preMessage + " - Traverse Interval - Check for Merge",
                System.nanoTime() - mergeStart);
            continue;
          }
        }

        //
        // Check if we should change the prevInterval
        //
        if (nextInterval.getStop() > prevInterval.getStop())
          prevInterval = nextInterval;
      }
      mapNavigator.addTiming(preMessage + " - Traverse Interval", System.nanoTime() - beginning);
    }

    for (SelectionInterval prevInterval : removeIntervals)
    {
      removeInterval(prevInterval);
      addIntervals.remove(prevInterval);
    }

    for (SelectionInterval prevInterval : addIntervals)
    {
      addInterval(prevInterval);
    }
    long time = System.nanoTime() - timerStart;
    mapNavigator.addTiming(preMessage, time);
  }

  /**
   * <p>Clear all the selected {@link DisplaySegment}s</p>
   *
   */
  public void clearSegments()
  {
    segments.clear();
  }

  /**
   * <p>Clear all the displayed {@link SelectionInterval}s</p>
   *
   */
  public void clearIntervals()
  {
    intervals.clear();
  }

  /**
   * <p>Clear all the selected {@link Annotation}s</p>
   *
   */
  public void clearAnnotation()
  {
    annotToSeg = new HashMap<Annotation, DisplaySegment>();
    segToAnnot = new HashMap<DisplaySegment, Vector<Annotation>>();
  }

  /**
   * <p>Get the last {@link Annotation} that was added to the
   * {@link Selection}.</p>
   *
   * @return
   *   {@link Annotation} that was added last or null if no
   *   {@link Annotation} is currently selected
   */
  public Annotation getLastAnnotationAdded()
  {
    return lastAnnotAdded;
  }

  /**
   * <p>Optimized version of the <code>addAnnotation()</code> method. This adds
   * a large amount of {@link Annotation} belonging to the same
   * {@link DisplaySegment} quicker than the <code>addAnnotation()</code> method
   * and still create the {@link SelectionInterval}s for the {@link Annotation}.
   * </p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} are located on
   * @param annotation
   *   {@link Vector} of {@link Annotation} to add to {@link Selection}
   */
  public void addAllAnnotation(DisplaySegment segment, TreeSet<Annotation> annotation)
  {
    long begin = System.nanoTime();
    if (segment == null || annotation == null)
      return;
    long start = System.nanoTime();
    for (Annotation annot : annotation)
    {
      if (contains(annot)) continue;

      storeAnnotationAndSiblings(segment, annot);

      // create interval
      SelectionInterval newInterval = new SelectionInterval(mapNavigator);
      newInterval.setInterval(segment, annot);
      addInterval(newInterval);
    }
    if (annotation.size() > 0)
      lastAnnotAdded = annotation.last();

    long timing = System.nanoTime() - start;
    mapNavigator.addTiming("Selection - addAllAnnotation - Create the Interval", timing);
    start = System.nanoTime();
    verifyParentIntervals();
    timing = System.nanoTime() - start;
    mapNavigator.addTiming("Selection - addAllAnnotation - Verify Parent Intervals", timing);
    start = System.nanoTime();
    for (SelectionInterval interval : getIntervals(segment))
    {
      createCorrespondingIntervals(interval);
    }
    timing = System.nanoTime() - start;
    mapNavigator.addTiming("Selection - addAllAnnotation - Create Corresponding Intervals", timing);
    start = System.nanoTime();
    verifyChildIntervals();
    timing = System.nanoTime() - start;
    mapNavigator.addTiming("Selection - addAllAnnotation - Verify Child Intervals", timing);
    mapNavigator.addTiming("Selection - addAllAnnotation", System.nanoTime() - begin);
  }

  /**
   * <p>Add a piece of {@link Annotation} to the {@link Selection} and create
   * a corresponding {@link SelectionInterval}.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is a part of
   * @param annotation
   *   {@link Annotation} to add to the {@link Selection}
   */
  public void addAnnotation(DisplaySegment segment, Annotation annotation)
  {
    if (segment == null || annotation == null)
      return;
    if (segment.getChromosome() != annotation.getChromosome())
      return;

    // Set last annot
    lastAnnotAdded = annotation;

    if (contains(annotation)) return;

    // Set selected map to that of annot
    selectedMap = segment.getParent();

    storeAnnotationAndSiblings(segment, annotation);

    // Create interval for annotation
    SelectionInterval newInterval = new SelectionInterval(mapNavigator);
    newInterval.setInterval(segment, annotation);
    addInterval(newInterval);
    createCorrespondingIntervals(newInterval);
    verifyIntervals();
  }

  /**
   * <p>Stores the {@link Annotation} and its siblings in the {@link Selection}.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is a part of
   * @param annotation
   *   {@link Annotation} to add to the {@link Selection}
   */
  private void storeAnnotationAndSiblings(DisplaySegment segment, Annotation annotation)
  {
    if (contains(annotation)) return;

    storeAnnotation(segment, annotation);

    // Add siblings
    for (Annotation sibling : annotation.getSiblings())
    {
      if (contains(sibling) || !mapNavigator.isAnnotationVisible(sibling)) continue;

      DisplaySegment sibSegment = null;

      for (DisplayMap map : mapNavigator.getDisplayMaps())
      {
        for (DisplaySegment seg : map.getSegments())
        {
          if (seg.containsFeature(sibling))
          {
            sibSegment = seg;
            break;
          }
        }
        if (sibSegment != null)
          break;
      }

      storeAnnotation(sibSegment, sibling);
    }
  }

  /**
   * <p>Helper method that actually save the {@link Annotation} in the
   * {@link Selection}.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is a part of
   * @param annotation
   *   {@link Annotation} to add to the {@link Selection}
   */
  private void storeAnnotation(DisplaySegment segment, Annotation annotation)
  {
    if (segment == null || annotation == null) return;
    if (annotation.getChromosome() != segment.getChromosome() || contains(annotation)) return;

    annotToSeg.put(annotation, segment);

    Vector<Annotation> segAnnot;
    if (segToAnnot.get(segment) == null)
      segAnnot = new Vector<Annotation>();
    else
      segAnnot = segToAnnot.get(segment);

    // Add annotation to segment's list of annotation
    segAnnot.add(annotation);
    segToAnnot.put(segment, segAnnot);
  }

  /**
   * <p>Remove the {@link Annotation} belonging a particular
   * {@link DisplaySegment}.</p>
   *
   * @param segment
   *   Remove {@link Annotation} from this {@link DisplaySegment}
   */
  public void removeAnnotation(DisplaySegment segment)
  {
    if (segToAnnot.get(segment) != null)
    {
      Vector<Annotation> annotation = segToAnnot.get(segment);
      segToAnnot.remove(segment);

      for (Annotation annot : annotation)
        annotToSeg.remove(annot);
    }
  }

  /**
   * <p>Remove a specific piece of {@link Annotation} from the
   * {@link Selection}.</p>
   *
   * @param annotation
   *   {@link Annotation} to remove from the {@link Selection}
   */
  public void removeAnnotation(Annotation annotation)
  {
    if (annotation == null)
      return;

    if (annotToSeg.get(annotation) != null)
    {
      DisplaySegment segment = annotToSeg.get(annotation);
      segToAnnot.get(segment).remove(annotation);
      annotToSeg.remove(annotation);
    }
  }

  /**
   * <p>Remove more than one {@link Annotation} from the {@link Selection}.</p>
   *
   * @param annotation
   *   {@link Vector} of {@link Annotation} to remove from the {@link Selection}
   */
  public void removeAllAnnotation(Vector<Annotation> annotation)
  {
    for (Annotation annot : annotation)
      removeAnnotation(annot);
  }

  /**
   * <p>Get all of the selected {@link Annotation}.</p>
   *
   * @return
   *   {@link Vector} of currently selected {@link Annotation}. An empty {@link Vector}
   *   is returned if there is not and {@link Annotation} selected.
   */
  public Vector<Annotation> getAnnotation()
  {
    Vector<Annotation> annotation = new Vector<Annotation>();

    for (Object key : segToAnnot.keySet())
      annotation.addAll(segToAnnot.get(key));

    return annotation;
  }

  /**
   * <p>Get the selected {@link Annotation} that belong to a particular
   * {@link DisplaySegment}.</p>
   *
   * @param segment
   *   {@link DisplaySegment} the {@link Annotation} is a part of
   * @return
   *   {@link Vector} of currently selected {@link Annotation} belonging
   *   to the particular {@link DisplaySegment}. An empty {@link Vector}
   *   is returned if there is not {@link Annotation} for that {@link DisplaySegment}
   *   selected.
   */
  public Vector<Annotation> getAnnotation(DisplaySegment segment)
  {
    if (segToAnnot.get(segment) == null)
      return new Vector<Annotation>();
    else
      return segToAnnot.get(segment);
  }

  /**
   * <p>Get all of the {@link DisplaySegments} that have selected
   * {@link Annotation}.</p>
   *
   * @return
   *   {@link Vector} of {@link DisplaySegments}. If there are no
   *   {@link DisplaySegment}s with selected {@link Annotation}, an empty
   *   {@link Vector} is returned.
   */
  public Vector<DisplaySegment> getAnnotationSegments()
  {
    return new Vector<DisplaySegment>(segToAnnot.keySet());
  }

  /**
   * <p>Get the {@link Segment} a particular {@link Annotation} belongs to.</p>
   *
   * @param annotation
   *   {@link Annotation} to find {@link DisplaySegment} for
   * @return
   *   {@link DisplaySegment} the {@link Annotation} belongs to. null is
   *   returned if the {@link Annotation} is not selected.
   */
  public DisplaySegment getSegment(Annotation annotation)
  {
    return annotToSeg.get(annotation);
  }

  /**
   * <p>Clear all of the data saved in the {@link Selection}.</p>
   *
   */
  public void clear()
  {
    selectedMap = null;
    segments.clear();
    annotToSeg.clear();
    segToAnnot.clear();
    intervals.clear();
    selectedInterval = null;
  }

  /**
   * <p>Returns whether there is anything selected or not.</p>
   *
   * @return
   *   true - something is selected in the {@link MapNavigator}
   *   false - nothing is selected in the {@link MapNavigator}
   */
  public boolean isEmpty()
  {
    if (segments.size() > 0 || annotToSeg.size() > 0 || intervals.size() > 0)
      return false;
    else
      return true;
  }

  /**
   * <p>Determine if a {@link SelectionInterval} is a part of the
   * {@link Selection}.</p>
   *
   * @param interval
   * @return
   *   true - if {@link Selection} contains the {@link SelectionInterval}
   *   false - if {@link Selection} does NOT contain the {@link SelectionInterval}
   */
  public boolean contains(SelectionInterval interval)
  {
    for (DisplaySegment segment : intervals.keySet())
      if (segment == interval.getSegment())
        if (intervals.get(segment).contains(interval))
          return true;

    return false;
  }

  /**
   * <p>Determine if a {@link Annotation} is a part of the
   * {@link Selection}.</p>
   *
   * @param annotation
   * @return
   *   true - if {@link Selection} contains the {@link Annotation}
   *   false - if {@link Selection} does NOT contain the {@link Annotation}
   */
  public boolean contains(Annotation annotation)
  {
    if (annotToSeg.get(annotation) == null)
      return false;
    else
      return true;
  }

  /**
   * <p>Determine if a set of {@link Annotation} is a part of the
   * {@link Selection}.</p>
   *
   * @param annotation
   * @return
   *   true - if {@link Selection} contains all of the {@link Annotation}
   *   false - if {@link Selection} does NOT contain all of the {@link Annotation}
   */
  public boolean containsAll(Vector<Annotation> annotation)
  {
    for (Annotation annot : annotation)
      if (!contains(annot))
        return false;

    return true;
  }

  /**
   * <p>Determine if a any {@link Annotation} in a set is a part
   * of the {@link Selection}.</p>
   *
   * @param annotation
   * @return
   *   true - if {@link Selection} contains any of the {@link Annotation}
   *   false - if {@link Selection} does NOT contain any of the {@link Annotation}
   */
  public boolean containsAny(Vector<Annotation> annotation)
  {
    for (Annotation annot : annotation)
      if (contains(annot))
        return true;

    return false;
  }


  /**
   * <p>Determine if a {@link DisplaySegment} is a part of the
   * {@link Selection}.</p>
   *
   * @param segment
   * @return
   *   true - if {@link Selection} contains the {@link DisplaySegment}
   *   false - if {@link Selection} does NOT contain the {@link DisplaySegment}
   */
  public boolean contains(DisplaySegment segment)
  {
    if (segToAnnot.get(segment) == null)
      return false;
    else
      return true;
  }

  /**
   * <p>Inner class that helps sort {@link SelectionInterval}s by their
   * start position on a {@link DisplaySegment}.</p>
   *
   * @author jaaseby
   *
   */
  public class SortIntervalByStart implements Comparator<Object>
  {
    public int compare(Object s1, Object s2)
    {
      if (s1 instanceof SelectionInterval && s2 instanceof SelectionInterval)
      {
        SelectionInterval inter1 = (SelectionInterval)s1;
        SelectionInterval inter2 =  (SelectionInterval)s2;
        return inter1.getStart() -  inter2.getStart();
      }
      else
        throw new ClassCastException();
    }
  }
}
