package bioneos.vcmap.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.dialogs.AnnotationDialog;
import bioneos.vcmap.options.GlobalOptions;

/**
 * <p>This class is used to keep track of {@link DisplaySegment}s, as well as
 * the position of the displayable portion of a map and whether the user has
 * chosen to hide the displayable map or not.</p>
 *
 * <p>Created on: July 11, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class DisplayMap
{
  // Variables
  private MapData map;
  private Vector<DisplaySegment> segments;
  private int position;
  private boolean visible;
  private int unitsColumnWidth;
  private Vector<AnnotationSet> shownSets;
  private Vector<AnnotationSet> hiddenSets;
  private ArrayList<OntologyNode> ontologyFilters;
  private int featureColumns;
  private HashMap<AnnotationSet, Integer>typeFeatureColumns;

  /**
   * <p>{@link DisplayMap} constructor. Initializes everything that
   * is needed to properly use this {@link DisplayMap}.</p>
   *
   * @param map
   *   {@link MapData} of the {@link DisplayMap}
   */
  public DisplayMap(MapData map)
  {
    this.map = map;
    segments = new Vector<DisplaySegment>();
    hiddenSets = new Vector<AnnotationSet>();
    shownSets = new Vector<AnnotationSet>();
    position = 0;
    visible = true;
    unitsColumnWidth = 0;
    featureColumns = 0;
    typeFeatureColumns = null;
  }

  /**
   * <p>Returns the {@link MapData} associated with this {@link DisplayMap}.
   * </p>
   *
   * @return
   *   {@link MapData} that is being visually displayed by this
   *   {@link DisplayMap}
   */
  public MapData getMap()
  {
    return map;
  }

  /**
   * <p>{@link DisplaySegment} that are drawn with the {@link DisplayMap}.</p>
   *
   * @return
   *   {@link Vector} of {@link DisplaySegment} that are drawn for this
   *   {@link DisplayMap}
   */
  public Vector<DisplaySegment> getSegments()
  {
    return segments;
  }

  /**
   * <p>Returns the position of where the {@link DisplayMap} is drawn in the
   * {@link bioneos.vcmap.gui.MapNavigator}.</p>
   *
   * @return
   *   int value of the position of the {@link DisplayMap}
   */
  public int getPosition()
  {
    return position;
  }

  /**
   * <p>Returns the total width the column where {@link Annotation} is drawn.
   * </p>
   *
   * @param options
   *   {@link GlobalOptions} stores system preferences that are needed
   * @return
   *   int value of the size of the column needed to display {@link Annotation}
   */
  public int getFeatureColumnsWidth(GlobalOptions options)
  {
    if (options == null) return 0;

    int featureWidth = 0;

    featureWidth = (options.getIntOption("featureLabelColumnWidth") * shownSets.size()) +
        (options.getIntOption("featureColumnWidth") * getFeatureColumns());

    if (shownSets.size() == 0)
      featureWidth = options.getIntOption("featureLabelColumnWidth") + options.getIntOption("featureColumnWidth");

    return featureWidth;
  }

  /**
   * <p>Get the width of the column the units are displayed in.</p>
   *
   * @return
   *   int value of the column the units are displayed in
   */
  public int getUnitsColumnWidth()
  {
    return unitsColumnWidth;
  }

  /**
   * <p>Returns whether the {@link DisplayMap} is visible or not.<p>
   *
   * @return
   *   true - The {@link DisplayMap} is visible
   *   false - The {@link DisplayMap} is NOT visible
   */
  public boolean isVisible()
  {
    return visible;
  }

  /**
   * <p>Add a {@link Annotation} type that will be shown.</p>
   *
   * @param type
   *   {@link String} representing the {@link Annotation} type
   */
  public void addShownSet(AnnotationSet set)
  {
    if (!shownSets.contains(set) && !hiddenSets.contains(set))
      shownSets.add(set);
  }

  /**
   * <p>Get all of the types of {@link Annotation} this {@link DisplayMap}
   * has.</p>
   *
   * @return
   *   {@link Vector} of {@link String}s representing the {@link Annotation}
   *   types
   */
  public Vector<AnnotationSet> getAllAnnotationSets()
  {
    Vector<AnnotationSet> allAnnotationSets = new Vector<AnnotationSet>();
    allAnnotationSets.addAll(shownSets);
    allAnnotationSets.addAll(hiddenSets);

    return allAnnotationSets;
  }

  /**
   * <p>Get all the {@link Annotation} types that are set to be shown in the
   * {@link MapNavigator}.</p>
   *
   * @return
   *   {@link Vector} of {@link String}s representing the {@link Annotation}
   *   types
   */
  public Vector<AnnotationSet> getShownSets()
  {
    return shownSets;
  }

  /**
   * <p>Get all the {@link Annotation} types that are set to be hidden in the
   * {@link MapNavigator}.</p>
   *
   * @return
   *   {@link Vector} of {@link String}s representing the {@link Annotation}
   *   types
   */
  public Vector<AnnotationSet> getHiddenSets()
  {
    return hiddenSets;
  }

  /**
   * <p>Set a {@link Annotation} type to shown or hidden.</p>
   *
   * @param type
   *   {@link String} representation of the {@link Annotation} type
   * @param shown
   *   true - show the {@link Annotation} type
   *   false - hide the {@link Annotation} type
   */
  public void setTypeShown(String type, boolean shown)
  {
    if (shown)
    {
      Iterator<AnnotationSet> i = hiddenSets.iterator();
      while (i.hasNext())
      {
        AnnotationSet set = i.next();
        if (set.getType().equals(type))
        {
          i.remove();
          if (!shownSets.contains(set))
            shownSets.add(set);
        }
      }
    }
    else
    {
      Iterator<AnnotationSet> i = shownSets.iterator();
      while (i.hasNext())
      {
        AnnotationSet set = i.next();
        if (set.getType().equals(type))
        {
          i.remove();
          if (!hiddenSets.contains(set))
            hiddenSets.add(set);
        }
      }
    }
  }

  /**
    * <p>Set whether the {@link DisplayMap} is visible or not.<p>
   *
   * @param visible
   *   true - The {@link DisplayMap} is visible
   *   false - The {@link DisplayMap} is NOT visible
   */
  public void setVisible(boolean visible)
  {
    this.visible = visible;
  }

  /**
   * <p>Set the width of the column that the unit labels are displayed in.</p>
   *
   * @param numberOfPixels
   *   int value width of the units column
   */
  public void setUnitsColumnWidth(int numberOfPixels)
  {
    unitsColumnWidth = numberOfPixels;
  }

  /**
   * <p>Set the position the {@link DisplayMap} is displayed in the
   * {@link bioneos.vcmap.gui.MapNavigator}.</p>
   *
   * @param position
   *   int value of the position the {@link DisplayMap} is displayed.
   */
  public void setPosition(int position)
  {
    this.position = position;
  }

  /**
   * <p>Add a {@link DisplaySegment} to the {@link DisplayMap}</p>
   *
   * @param displaySegment
   *   {@link DisplaySegment} to be displayed with the {@link DisplayMap}
   */
  public void addSegment(DisplaySegment displaySegment)
  {
    segments.add(displaySegment);
    for (AnnotationSet set : displaySegment.getChromosome().getAnnotationSets())
    {
      if (!shownSets.contains(set) && !hiddenSets.contains(set))
        shownSets.add(set);
    }
  }

  /**
   * <p>This method is called by {@link AnnotationDialog} to keep track of all
   * QTL categories loaded for this {@link DisplayMap}.</p>
   *
   * @param hierarchy
   *   An {@link ArrayList} of {@link OntologyNode}s representing the loaded
   *   ontology category hierarchy.
   */
  public void addToOntologyFilters(OntologyNode filter)
  {
    if (ontologyFilters == null)
      ontologyFilters = new ArrayList<OntologyNode>();

    // see if 'all' ontology has already been loaded; if so, do nothing
    // if filter has already been loaded do nothing
    else if (ontologyFilters.contains(filter) || ontologyFilters.size() == 0)
      return;

    // first check to see if all ontology has been loaded (no filters)
    if (filter == null)
    {
      ontologyFilters.clear();
      return;
    }

    // New ArrayList
    ArrayList<OntologyNode> newList = new ArrayList<OntologyNode>();

    for (OntologyNode node : ontologyFilters)
    {
      // Cancel if one of the existing filters is the parent node of the new filter
      if (node.isParent(filter))
        return;
      // Add filters that are not children of the filter to the list
      else if (!filter.isParent(node))
        newList.add(node);
    }

    // If we got this far add filter to the list then save newList
    newList.add(filter);
    ontologyFilters = newList;
  }

  /**
   * <p>This method returns all loaded ontology cateogry hierarchies for this
   * {@link DisplayMap}.</p>
   *
   * @return
   *   A {@link HashMap} mapping the top-level category name to all nodes
   *   below it in the hierarchy.
   */
  public ArrayList<OntologyNode> getOntologyFilters()
  {
    return ontologyFilters;
  }

  /**
   * <p>Determines the number of feature columns this {@link DisplayMap} has.</p>
   */
  public void determineFeatureColumns()
  {
    if (typeFeatureColumns == null)
      return;

    featureColumns = 0;
    for (AnnotationSet set : typeFeatureColumns.keySet())
      featureColumns += typeFeatureColumns.get(set);
  }

  /**
   * <p>Determines the number of feature columns this {@link DisplayMap}
   * has for the given type.</p>
   *
   * @param type
   *    the type to determine feature columns for
   *
   */
  public void determineFeatureColumns(AnnotationSet set)
  {
    int column = 0;

    if (typeFeatureColumns == null)
      typeFeatureColumns = new HashMap<AnnotationSet, Integer>();

    for (DisplaySegment segment : segments)
      column = Math.max(column, segment.getFeatureColumns(set));

    typeFeatureColumns.put(set, new Integer(column));
  }

  /**
   * <p>Returns the total number of feature columns for this {@link DisplayMap}.</p>
   *
   * @return
   *    int value of the total number of feature columns for this {@link DisplayMap}
   */
  public int getFeatureColumns()
  {
    if (featureColumns == 0) determineFeatureColumns();
    return featureColumns;
  }

  /**
   * <p>Returns the total number of feature columns for this {@link DisplayMap}
   * for the given type.</p>
   *
   * @param type
   *    the type to get the number of feature columns for
   * @return
   *    int value of the total number of feature columns for this {@link DisplayMap}
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
   * <p>Reset the number of feature columns for this {@link DisplayMap}.</p>
   */
  public void resetFeatureColumns()
  {
    featureColumns = 0;
    typeFeatureColumns = null;
  }
}
