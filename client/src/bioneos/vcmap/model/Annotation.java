package bioneos.vcmap.model;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * <p>This class represents a collection of all the data that is needed to be
 * stored for each piece of annotation. Whether the annotation is a marker,
 * QTL data, etc.</p>
 *
 * <p>Created on: July 11, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class Annotation implements Comparable<Annotation>
{
  // Logging
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // Variables
  private int id;
  private Chromosome chromosome;
  private OntologyNode ontology;
  private String[] names;
  private int start;
  private int stop;
  private AnnotationSet annotationSet;
  private Vector<Integer> linkIds;
  private Vector<Annotation> siblings;
  private int sourceId;
  private String referenceId;
  protected HashMap<String, String> info;

  /**
   * <p>Constructor for {@link Annotation}. Used when the database id is not
   * available.</p>
   *
   * @param parent
   *   {@link Chromosome} of the {@link Annotation}
   */
  public Annotation(Chromosome parent)
  {
    this(parent, -1, null, -1, null);
  }

  /**
   * <p>Constructor for {@link Annotation}. Used when the database id is
   * available.</p>
   *
   * @param parent
   *   {@link Chromosome} of the {@link Annotation}
   * @param id
   *   <code>int</code> value of the {@link Annotation}'s database id
   * @param ontology
   *   {@link OntologyNode} to which this {@link Annotation} object is associated.
   * @param sourceId
   *   <code>int</code> value of the {@link Annotation}'s source id
   * @param referenceId
   *   <code>String</code> value of the {@link Annotation}'s source-specific id
   */
  public Annotation(Chromosome parent, int id, OntologyNode ontology, int sourceId, String referenceId)
  {
    this.id = id;
    chromosome = parent;
    this.ontology = ontology;
    start = -1;
    stop = -1;
    annotationSet = null;
    info = null;
    linkIds = new Vector<Integer>();
    siblings = new Vector<Annotation>();
    this.sourceId = sourceId;
    this.referenceId = referenceId;
    names = null;
  }

  /**
   * <p>Get the database id of the {@link Annotation}.</p>
   *
   * @return
   *   int value of the the {@link Annotation}'s database id
   */
  public int getId()
  {
    return id;
  }

  /**
   * <p>Gets the external reference id of this {@link Annotation}.</p>
   *
   * @return
   *   This {@link Annotation}'s external reference id.
   */
  public String getReferenceId()
  {
    return referenceId;
  }

  /**
   * <p>Gets this {@link Annotation}'s source Id</p>
   *
   * @return
   *   int value of the the {@link Annotation}'s source Id.
   */
  public int getSourceId()
  {
    return sourceId;
  }

  /**
   * <p>Return {@link Chromosome} {@link Annotation} is located on.</p>
   *
   * @return
   *   {@link Chromosome} {@link Annotation} is located on
   */
  public Chromosome getChromosome()
  {
    return chromosome;
  }

  /**
   * <p>Return the {@link AnnotationSet} for this {@link Annotation}.</p>
   * @return
   *   {@link AnnotationSet} that the {@link Annotation} is a part of
   */
  public AnnotationSet getAnnotationSet()
  {
    return annotationSet;
  }

  /**
   * returns the {@link HashMap} of link id's for the given {@link Annotation}
   * @return
   */

  public Vector<Integer> getLinkIds()
  {
    return linkIds;
  }

  /**
   * <p>Return {@link String} array with all known names of {@link Annotation}.
   * The first two positions of this array are special.  The element at index
   * 0 will always hold the "default" name (most commonly used name across all
   * species) for this feature.  And the name at index 1 will always refer to
   * the name most commonly used for this feature for the species from which
   * this {@link Annotation} object has been loaded.
   * </p>
   *
   * @return
   *   {@link String} array of {@link Annotation} names
   */
  public String[] getNames()
  {
    if (info == null) loadInfo();
    return names;
  }

  /**
   * <p>Return default name of {@link Annotation}.  This "default" name is the
   * name that is most commonly used used across all species that have
   * homologous copies of this feature.  This is equivalent to
   * Annotation.getName(0).
   * </p>
   *
   * @see #getName(int)
   * @see #getNames
   * @return
   *   {@link String} value of name
   */
  public String getName()
  {
    return getName(0);
  }

  /**
   * <p>Return specific name of {@link Annotation}.  This names array has a
   * specific structure as noted in the docs for the getNames() method.
   * <p>
   *
   * @see #getNames
   * @param index
   *   int value of where specific name is saved in array
   * @return
   *   {@link String} value of specific name or an empty string for a bad index.
   */
  public String getName(int index)
  {
    // Treat name[0] and name[1] as a special case so we only load the aliases
    // if absolutely necessary.
    if (index > 1 && info == null)
      loadInfo();

    if (names.length == 0)
      return "";

    if (index >= 0 && index < names.length)
      return names[index];
    else if (index == -1 && hasHomoloGeneId())
      return getInfo("homologene_id");
    else
      return getName(0);
  }

  /**
   * <p>Return start location of {@link Annotation}</p>
   *
   * @return
   *   int value of {@link Annotation}'s start location
   */
  public int getStart()
  {
    return start;
  }

  /**
   * <p>Return stop location of {@link Annotation}</p>
   *
   * @return
   *   int value of {@link Annotation}'s stop location
   */
  public int getStop()
  {
    return stop;
  }

  /**
   * <p>Return the length of the {@link Annotation}</p>
   *
   * @return
   *    The value of the {@link Annotation}'s length (in number of units)
   */

  public int getLength()
  {
    return (stop - start) + 1;  // TESTing! (added +1 for more accurate result)
  }

  /**
   * <p>Return all loaded siblings of {@link Annotation}</p>
   *
   * @return
   *   {@link Vector} of all sibling {@link Annotation}
   */
  public Vector<Annotation> getSiblings()
  {
    return siblings;
  }

  /**
   * <p>Get the additional information of the {@link Annotation}.</p>
   *
   * @return
   *   {@link HashMap} of the additional information for the {@link Annotation}
   */
  public HashMap<String, String> getAllInfo()
  {
    if (info == null) loadInfo();

    return info;
  }

  /**
   * <p>Get the keys for the additional information saved for this
   * {@link Annotation}.</p>
   *
   * @return
   *   Array of {@link String} with the keys for the additional information
   */
  public String[] getInfoKeys()
  {
    if (info == null) loadInfo();

    if (info.keySet().size() == 0)
      return null;
    return info.keySet().toArray(new String[info.keySet().size()]);
  }

  /**
   * <p>Get a specific piece of information about the {@link Annotation}.</p>
   *
   * @param key
   *   {@link String} of the key for the information
   * @return
   *   {@link String} of the information on the {@link Annotation}. null is
   *   returned if an invalid key was used.
   */
  public String getInfo(String key)
  {
    if (info == null) loadInfo();

    return info.get(key);
  }

  /**
   * <p>Load AVP Information from the {@link Factory} when it is needed.</p>
   *
   */
  private void loadInfo()
  {
    try
    {
      info = new HashMap<String, String>();
      if (id != -1)
        Factory.getAnnotationAVPInformation(this);
    }
    catch (SQLException sql)
    {
      logger.warn("Was unable to get info annotationId: " + id);
    }
  }

  /**
   * <p>Tests if this {@link Annotation} object has a HomoloGene reference.</p>
   *
   * @return
   *   true - has a homologene id
   *   false - does NOT have a homologene id
   */
  public boolean hasHomoloGeneId()
  {
    return getInfo("homologene_id") != null;
  }

  /**
   * <p>Returns a URL describing this {@link Annotation}.</p>
   *
   * @return
   *   {@link String} containing this annotation's URL.
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public String getURL()
    throws SQLException
  {
    HashMap<String, String> params = new HashMap<String, String>();

    // Fix some NCBI identifiers
    // NOTE: This could be fixed on the load script, but it was easier to
    //   handle here, so that it won't break the homology code.
    String id = referenceId;
    if (referenceId.indexOf(':') != -1)
      id = referenceId.substring(referenceId.indexOf(':') + 1);
    
    // Specify the species for sites that don't conform to our naming 
    if (sourceId == Factory.getSourceId("Ensembl"))
    {
      params.put("geneID", referenceId);
      String species = chromosome.getMap().getSpecies();
      if (species == null)
      {
        logger.error("Error assigning species name.");
        return null;
      }
      species = species.replaceAll(" ", "_");
      species = species.substring(0, species.indexOf("_") + 1) +
      species.substring(species.indexOf("_") + 1, species.indexOf("_") + 2).toLowerCase() +
      species.substring(species.indexOf("_") + 2);
      params.put("species", species);
    }
    else if (sourceId == Factory.getSourceId("ISU"))
    {
      String species = chromosome.getMap().getSpecies();
      if (species == null)
      {
        logger.error("Error assigning species name.");
        return null;
      }
      else if (species.equals("Bos Taurus"))
        species = "BT";
      else if (species.equals("Sus Scrofa"))
        species = "SS";
      else if (species.equals("Gallus Gallus"))
        species = "GG";
      params.put("species", species);
    }


    // Now put our proper identifier in the parameters (varies depending on
    // our type).
    if (annotationSet.getType().equalsIgnoreCase("GENE"))
    {
      params.put("geneID", id);
      return Factory.getURL(sourceId, "annotation", "GENE", params);
    }
    else if (annotationSet.getType().equalsIgnoreCase("QTL"))
    {
      params.put("qtlID", id);
      return Factory.getURL(sourceId, "annotation", "QTL", params);
    }
    else if (annotationSet.getType().equalsIgnoreCase("STS"))
    {
      params.put("stsID", id);
      return Factory.getURL(sourceId, "annotation", "STS", params);
    }
    else
    {
      params.put("id", id);
      return Factory.getURL(sourceId, "annotation", params);
    }
  }

  /**
   * <p>Returns a URL containing the link to the HomoloGene page
   * for this {@link Annotation}.</p>
   *
   * @return
   *   {@link String} containing this annotation's HomoloGene URL.
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public String getHomoloGeneURL()
    throws SQLException
  {
    HashMap<String, String> params = new HashMap<String, String>();

    if (info.containsKey("homologene_id"))
      params.put("homologeneID", getInfo("homologene_id"));
    else
    {
      logger.error("This annotation has no associated HomoloGene Id.");
      return null;
    }
    /*
     * NOTE:  We use the "generic" Homologene entry here so that we don't have
     *   to determine what version of Homologene was loaded when this feature
     *   was inserted into the database.  This isn't causing any problems now,
     *   but it may in the future.  I wouldn't expect it, but here is a good
     *   place to start looking if the Homologene links start to break.
     */
    return Factory.getURL(Factory.getSourceId("NCBI Homologene"), "annotation",  params);
  }

  /**
   * <p>Add additional information for the {@link Annotation}.</p>
   *
   * @param key
   *   Title for the information
   * @param element
   *   Information for the title
   */
  public void addInfo(String key, String element)
  {
    if (info == null) loadInfo();

    info.put(key, element);
  }

  /**
   * <p>Add additional information for the {@link Annotation}.</p>
   * @param map
   *    {@link HashMap} whose info is added to each {@link Annotation}
   */
  public void addInfo(HashMap<String, String> map)
  {
    if (info == null) loadInfo();

    info.putAll(map);
  }


  /**
   * <p>Add a sibling to the {@link Annotation} located on a different
   * {@link MapData}</p>
   *
   * @param sibling
   *   {@link Annotation} that is a sibling to this {@link Annotation}
   */
  public boolean addSibling(Annotation sibling)
  {
    // Error check
    if (sibling == null)
      return false;
    // Check for same object in memory
    if (siblings.contains(sibling) || this == sibling)
      return true;

    // Check our link ids against the potential siblings
    boolean linkThem = false;
    for (int linkId : sibling.getLinkIds())
    {
      if (linkIds.contains(linkId))
      {
        linkThem = true;
        break;
      }
    }
    if (!linkThem) return false;

    // Add passed in sibling
    siblings.add(sibling);
    sibling.addSibling(this);
    return true;
  }

  /**
   * <p>Set the {@link AnnotationSet} for the {@link Annotation}
   *
   * @param
   *    annotationSet The {@link AnnotationSet} of the {@link Annotation} to set
   */
  public void setAnnotationSet(AnnotationSet annotationSet)
  {
    this.annotationSet = annotationSet;
  }

  /**
   * <p>Set the list of names for this {@link Annotation}</p>
   *
   * @param names
   *   Array of {@link String} with the {@link Annotation}'s names
   */
  public void setNames(String[] names)
  {
    this.names = names;
  }

  /**
   * Add a new name alias to the names for this {@link Annotation} object.  Use
   * this method with care because it affects the getName methods.
   * @param name
   *   The new name to append to this list of names.
   * @see getName
   * @see getName(int)
   * @see getNames
   */
  public void addName(String name)
  {
    if (names == null)
    {
      names = new String[2];
      names[0] = names[1] = name;
    }
    else
    {
      String[] newNames = new String[names.length + 1];
      for (int old = 0; old < names.length; old++)
        newNames[old] = names[old];
      newNames[names.length] = name;
      names = newNames;
    }
  }

  /**
   * Set the Link IDs for the annotation
   * @param linkID
   */
  public void setLinkID(Vector<Integer> linkIDs)
  {
    for(int id : linkIDs)
      addLinkId(id);
  }

  /**
   * Add a link ID to the set of linkIDs.
   * @param linkID
   */
  public void addLinkId(int linkID)
  {
    linkIds.add(linkID);
  }

  /**
   * <p>This sets the {@link Annotation} start and stop position to the same
   * value.</p>
   *
   * @param position
   *   int value of the position the {@link Annotation} is located on the
   *   {@link Chromosome}
   */
  public void setPosition(int position)
  {
    start = position;
    stop = position;
  }

  /**
   * <p>This sets the {@link Annotation} start position.</p>
   *
   * @param start
   *   int value of where the {@link Annotation} starts on the
   *   {@link Chromosome}
   */
  public void setStart(int start)
  {
    this.start = start;
  }

  /**
   * <p>This sets the {@link Annotation} stop position.</p>
   *
   * @param stop
   *   int value of where the {@link Annotation} stops on the
   *   {@link Chromosome}
   */
  public void setStop(int stop)
  {
    this.stop = stop;
  }

  /**
   * <p>Returns a list containing this {@link Annotation}
   * object's ontology category/categories.</p>
   *
   * @return
   *   {@link Vector} of {@link String}s representing this
   *   {@link Annotation}'s ontology category/categories
   */
  public Vector<String> getOntology()
  {
    // return immediately if this Annotation object has no OntologyNode reference
    Vector<String> ontologyCategories = new Vector<String>();
    if (ontology == null)
      return ontologyCategories;

    ontologyCategories = getOntology(ontology, ontologyCategories);
    //return (ontologyCategories + (ontologyCategories.equals("") ? "" : " >> ") + ontology.getCategory());
    ontologyCategories.add(ontology.getCategory());
    return ontologyCategories;
  }

  /**
   * <p>Recursive method to determine the path of ontology categories.</p>
   *
   * @param currentNode
   *   Find the parent of this {@link OntologyNode}
   * @param ontologyCategory
   *   {@link Vector} of {@link String}s to add the {@link String} of the parent node
   * @return
   *   {@link Vector} of {@link String}s with the list of ontology categories
   */
  public Vector<String> getOntology(OntologyNode currentNode, Vector<String> ontologyCategory)
  {
    OntologyNode parent = null;
    if (currentNode != null)
      parent = currentNode.getParent();

    if (parent != null)
    {
      ontologyCategory = getOntology(parent, ontologyCategory);
      ontologyCategory.add(parent.getCategory());
    }

    return ontologyCategory;
  }

  /*
   * This overridden method is used to identify those Annotations that are
   * actually referencing the same data (but may not be the same object in
   * memory).
   */
  @Override
  public boolean equals(Object o)
  {
    if (!(o instanceof Annotation))
    {
      return false;
    }
    else
    {
      Annotation a = (Annotation) o;
      if (a.getId() != -1 && a.getId() == getId())
        return true;
      else if (getId() == -1 && a.getId() == -1 && getName().equals(a.getName()) &&
               getStart() == a.getStart() && getStop() == a.getStop())
        return true;
      
      return false;
    }
  }

  /*
   * Implemented to allow the Annotation to be stored in a TreeSet in the
   * Chromosome, to improve performance for loading new Annotation from custom
   * data files.
   * Since order isn't really important, we will attempt to order them by
   * position first, but for those that have matching start and stop positions,
   * we will compare their names.  This will be roughly consistent to the
   * .equals() method (which is important for TreeSets).
   * (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Annotation o)
  {
    if (o.equals(this))
      return 0;
    else if (o.getStart() < getStart())
      return -1;
    else if (o.getStart() > getStart())
      return 1;
    else
    {
      if (o.getStop() < getStop())
        return -1;
      else if (o.getStop() > getStop())
        return 1;
      else
        return o.getName().compareTo(getName());
    }
  }
}
