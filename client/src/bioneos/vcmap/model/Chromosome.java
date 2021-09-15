package bioneos.vcmap.model;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.Vector;

/**
 * <p>This class represents a collection of all the data that is needed to be
 * stored for each chromosome.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class Chromosome
{
  // Variables
  private MapData map;
  private int id;
  private int length;
  private String name;
  private Hashtable<AnnotationSet, TreeSet<Annotation>> annotation;
  private Vector<Region> loaded;

  /**
   * <p>Very minimal internal class to represent sub-regions of the entire
   * Chromosome.  Start values are always smaller than stop values.</p>
   *
   * @author sgdavis.@bioneos.com
   */
  public class Region
  {
    private int start;
    private int stop;

    public Region(int start, int stop)
    {
      this.start = (start < stop) ? start : stop;
      this.stop = (start < stop) ? stop : start;
    }
    public int getStart()
    {
      return start;
    }
    public int getStop()
    {
      return stop;
    }

    public boolean overlaps(Region r)
    {
      return ((r.getStart() <= start && r.getStop() >= stop) ||
          (r.getStart() >= start && r.getStart() <= stop) ||
          (r.getStop() >= start && r.getStop() <= stop));
    }

    public Region union(Region r)
    {
      if (!overlaps(r)) return null;
      return new Region((r.getStart() < start) ? r.getStart() : start,
          (r.getStop() > stop) ? r.getStop() : stop);
    }

    public boolean equals(Region r)
    {
      if (start == r.getStart() && stop == r.getStop())
        return true;
      else
        return false;
    }
  }

  /**
   * <p>Constructor creates a default Chromosome object with a reference to its
   * parent {@link MapData} object.</p>
   *
   * @param map
   *   The {@link MapData} parent of this Chromosome.
   * @param dbId
   *   The internal database id for this Chromosome (or -1 if this Chromosome
   *   is not stored in the database backend.
   */
  public Chromosome(MapData map, int dbId)
  {
    this.map = map;
    id = dbId;
    annotation = new Hashtable<AnnotationSet, TreeSet<Annotation>>();
    loaded = new Vector<Region>();
    length = 0;
  }
  public Chromosome(MapData map)
  {
    this(map, -1);
  }

  /**
   * <p>Load {@link Annotation} for this {@link Chromosome} of a particular type.</p>
   *
   * @param type
   *   {@link String} represenation of the {@link Annotation} type
   * @param ontologyFilter
   *   {@link OntologyNode} needed only if loading a particular part of an
   *   {@link Annotation} type's ontology tree
   * @return
   *    the number of annotation that was loaded
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public int loadAnnotation(AnnotationSet annotationSet, OntologyNode ontologyFilter)
    throws SQLException
  {
    // Add a new Vector to our list
    if (annotation.get(annotationSet) == null)
    {
      TreeSet<Annotation> list = new TreeSet<Annotation>();
      annotation.put(annotationSet, list);
    }

    int annotAdded = 0;

    // Populate that Vector
    for (Region r : loaded)
      for (Annotation newA : Factory.getAnnotation(this, annotationSet, r.getStart(), r.getStop(), ontologyFilter))
        if (addAnnotation(newA))
          annotAdded++;

    return annotAdded;
  }

  /**
   * <p>Load a specific type of annotation for all the {@link Chromosome}'s
   * loaded {@link Region}s.</p>
   *
   * @param type
   *   type of annotation to load
   * @return
   *    the number of annotation that was loaded
   * @throws SQLExceptions
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public int loadAnnotation(AnnotationSet annotationSet)
    throws SQLException
  {
    return loadAnnotation(annotationSet, null);
  }

  /**
   * <p>Load the {@link Annotation} for a particular region.</p>
   *
   * @param start
   *   starting point (lower bound) of the region to load (inclusive)
   * @param stop
   *   stopping point (upper bound) of the region to load (inclusive)
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public void loadRegion(int start, int stop)
    throws SQLException
  {
    // Create a new Region to ensure start/stop order
    Region newR = new Region(start, stop);

    // Add this region to our list
    Region overlap = null;
    for (Region r : loaded)
    {
      if (r.equals(newR))
      {
        return;
      }
      else if (r.overlaps(newR))
      {
        overlap = r;
        break;
      }
    }

    if (overlap != null)
    {
      loaded.remove(overlap);
      newR = overlap.union(newR);
    }

    loaded.add(newR);

    // Update our annotation lists
    for (AnnotationSet key : annotation.keySet())
      for (Annotation newA : Factory.getAnnotation(this, key, newR.getStart(), newR.getStop()))
        if (map.getAllAnnotationSets().contains(key))
          addAnnotation(newA);
  }

  /**
   * <p>Helper method for ensuring that duplicates of Annotation objects are not
   * loaded into our annotations Hashtable.</p>
   *
   * @param newA
   *   {@link Annotation} to add if it is not already in the {@link Chromosome}
   * @return
   *    false if the {@link Annotation} is already loaded or the list doesn't
   *    exist, otherwise true meaning a successful added annotation
   */
  private boolean addAnnotation(Annotation newA)
  {
    // Get the right Vector according to type
    TreeSet<Annotation> annots = annotation.get(newA.getAnnotationSet());

    // Check for dups
    boolean dup = annots.contains(newA);
    
    if (!dup) annots.add(newA);
    return !dup;
  }

  /**
   * Load a piece of annotation into our memory structure for this Chromosome.
   * @param annot
   *   The piece of Annotation to load into the data structure in memory.
   * @return
   *   True if the Annotation was added successfully.  False if it was not
   *   loaded for some reason (usually because it is a duplicate of existing
   *   features already loaded into memory).
   */
  public boolean loadAnnotation(Annotation annot)
  {
    AnnotationSet annotationSet = annot.getAnnotationSet();

    // Add a new Vector to our list
    if (!annotation.keySet().contains(annotationSet))
    {
      TreeSet<Annotation> list = new TreeSet<Annotation>();
      annotation.put(annotationSet, list);
    }

    return addAnnotation(annot);
  }

  /**
   * <p>Get the {@link MapData} of the {@link Chromosome}.</p>
   *
   * @return
   *   {@link MapData} of the {@link Chromosome}
   */
  public MapData getMap()
  {
    return map;
  }

  /**
   * <p>Get the length of the {@link Chromosome}.</p>
   *
   * @return
   *   int value of the length of the {@link Chromosome}
   */
  public int getLength()
  {
    return length;
  }

  /**
   * <p>Get the name of the {@link Chromosome}.</p>
   *
   * @return
   *   {@link String} value of the name of the {@link Chromosome}
   */
  public String getName()
  {
    return name;
  }

  /**
   * <p>Get the types of {@link Annotation} loaded for this
   * {@link Chromosome}.</p>
   *
   * @return
   *   Array of {@link String}s containing the types of loaded {@link Annotation}
   */
  public Vector<AnnotationSet> getAnnotationSets()
  {
    Vector<AnnotationSet> annotationSets = new Vector<AnnotationSet>();
    annotationSets.addAll(annotation.keySet());
    return annotationSets;
  }

  /**
   * This returns requested annotation data by requesting data from the
   * for that specific type.  Can return an empty Vector, but never null.
   *
   * @param type - type of annotation to load (ex: QTL). If null, this method
   *   will return all annotation regardless of annotation set.
   * @param start - start position to load data from
   * @param stop - stop position to load data
   * @return
   *   All available annotation of a specific type in a specific region,
   *   never null.  Can return an empty {@link Vector}.
   */
  public Vector<Annotation> getAnnotation(AnnotationSet annotationSet, int start, int stop)
  {
    // Determine type information
    if (start == -1) start = 1;
    if (stop == -1) stop = length + 1;
    Region r = new Region(start, stop);
    Vector<Annotation> ret = new Vector<Annotation>();
    for (AnnotationSet aset : annotation.keySet())
    {
      if (annotationSet == null || aset.equals(annotationSet))
        for (Annotation a : annotation.get(aset))
          if (r.overlaps(new Region(a.getStart(), a.getStop())))
            ret.add(a);
    }
    return ret;
  }
  public Vector<Annotation> getAnnotation(int start, int stop)
  {
    return getAnnotation(null, start, stop);
  }
  public Vector<Annotation> getAnnotation(AnnotationSet annotationSet)
  {
    return getAnnotation(annotationSet, -1, -1);
  }
  public Vector<Annotation> getAnnotation()
  {
    return getAnnotation(null, -1, -1);
  }

  /**
   * <p>Get {@link Annotation} by the {@link Annotation}'s database id.  Take
   * care to only use this method for an Annotation with an id > 0.  This is due
   * to the fact that all custom Annotation set their id's as -1 and therefore
   * there will be overlap on id == -1 and possible inconsistent behavior of
   * this method.</p>
   *
   * @param id
   *   Database id of the {@link Annotation}
   * @return
   *   {@link Annotation} with the matching database id or null
   *   if there is no matching {@link Annotation}
   */
  protected Annotation getAnnotationById(int id)
  {
    for (AnnotationSet key : annotation.keySet())
      for (Annotation a : annotation.get(key))
        if (a.getId() == id)
          return a;

    return null;
  }

  /**
   * <p>Set the length of the {@link Chromosome}.</p>
   *
   * @param length
   *   int value of the length of the {@link Chromosome}
   */
  public void setLength(int length)
  {
    this.length = length;
  }

  /**
   * <p>Set the name of the {@link Chromosome}.</p>
   *
   * @param name
   *   {@link String} with the {@link Chromosome}'s name
   */
  public void setName(String name)
  {
    this.name = name;
  }

  /**
   * <p>Get the database id of the {@link Chromosome}.</p>
   *
   * @return
   *   int value of the database id of the {@link Chromosome}
   */
  protected int getId()
  {
    return id;
  }

  /**
   * <p>Get the source URL for this {@link Chromosome}.</p>
   *
   * @return
   *   {@link String} containing the source URL for this {@link Chromosome}.
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public String getURL()
    throws SQLException
  {
    String chrID = name.split("chr")[1];

    HashMap<String, String> params = new HashMap<String, String>();
    params.put("taxID", "" + map.getTaxID());
    params.put("chrID", chrID);

    return Factory.getURL(map.getSourceId(), "chromosome", params);
  }
}
