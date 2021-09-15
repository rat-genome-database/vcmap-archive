package bioneos.vcmap.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * <p>This class represents an 'ontology_node' entry from the database.  In
 * addition to the information stored in one row of 'ontology_node', an
 * <code>OntologyNode</code> object contains references to all
 * {@link Annotation}, {@link Chromosome}s, and species that the node references.
 * </p>
 *
 * @author dquacken@bioneos.com
 */

public class OntologyNode
{
  // Variables
  private int id = -1;
  private OntologyNode parent = null;
  private ArrayList<OntologyNode> children = null;
  private ArrayList<OntologyNode> filterChildren = null;
  private HashMap<Integer, ArrayList<Integer>> annotationIDs;
  private ArrayList<Integer> chrIDs;
  private ArrayList<String> species = null;
  private String category = null;
  private String description = null;
  private boolean isFilter = false;
  private int annotationDescendants = 0;

  /**
   * <p>Constructor for {@link OntologyNode}.</p>
   */
  public OntologyNode(int id, OntologyNode parent, String cat, String desc)
  {
    this.id = id;
    this.parent = parent;
    this.category = cat;
    this.description = desc;

    children = new ArrayList<OntologyNode>();
    filterChildren = new ArrayList<OntologyNode>();
    annotationIDs = new HashMap<Integer, ArrayList<Integer>>();
    chrIDs = new ArrayList<Integer>();
    species = new ArrayList<String>();
  }


  /**
   * <p>Gets the database ID of the {@link OntologyNode}.</p>
   *
   * @return
   *   int value of the the {@link OntologyTree}'s database ID.
   */
  protected Integer getID()
  {
    return id;
  }

  /**
   * <p>Gets a reference to this {@link OntologyNode}'s parent.</p>
   *
   * @return
   *   This {@link OntologyNode}'s parent.
   */
  public OntologyNode getParent()
  {
    return parent;
  }

  /**
   * <p>Gets all of the children of this {@link OntologyNode}.</p>
   *
   * @return
   *   <code>ArrayList</code> of {@link OntologyNode}s.
   */
  public ArrayList<OntologyNode> getChildren()
  {
    return children;
  }

  /**
   * Gets a list of all of an <code>OntologyNode</code>'s children that are
   * 'filters' (i.e. they are relevant to the currently-loaded species/chromosome).
   *
   * @return
   *   <code>ArrayList</code> of relevant <code>OntologyNode</code>s.
   */
  public ArrayList<OntologyNode> getFilterChildren()
  {
    if (filterChildren.size() > 0)
      return filterChildren;
    else
      return null;
  }

  /**
   * <p>Return a {@link String} containing the trait/subtrait that this
   * {@link OntologyNode} represents.</p>
   *
   * @return
   *   {@link String} containing the trait/subtrait.
   */
  public String getCategory()
  {
    return category;
  }

  /**
   * <p>Return a {@link String} containing the description of the trait/subtrait
   * that this {@Link OntologyNode} represents.</p>
   *
   * @return
   *   {@link String} containing the description.
   */
  public String getDescription()
  {
    return description;
  }

  /**
   * Returns an <code>ArrayList</code> containing all species with which this
   * <code>OntologyNode</code> is associated.
   *
   * @return
   *   <code>ArrayList</code> of <code>String</code>s containing species names.
   */
  public ArrayList<String> getSpecies()
  {
    return species;
  }

  /**
   * Returns an <code>ArrayList</code> containing database IDs for all the
   * annotation that this <code>OntologyNode</code> references.
   *
   * @return
   *   An <code>ArrayList</code> of <code>Integers</code> representing the
   *   database IDs of all the referenced annotation.
   */
  public ArrayList<Integer> getReferencedAnnotationIDs(Integer chr)
  {
    return annotationIDs.get(chr);
  }

  /**
   * Returns an <code>Integer</code> representing the number of pieces of
   * annotation that are referenced on the current chromosome.
   *
   * @param chr
   *   An <code>int</code> representing the database ID of the relevant chromosome.
   *
   * @return
   *   An <code>int</code> representing the number of pieces of referenced
   *   annotation.
   */
  public int getReferencedAnnotationCount(Integer chr)
  {
    ArrayList<Integer> ids = null;
    if (annotationIDs.containsKey(chr))
      ids = annotationIDs.get(chr);
    else
      return 0;

    return ids.size();
  }

  /**
   * Returns an <code>ArrayList</code> containing database IDs for all the
   * chromosomes that this <code>OntologyNode</code> references.
   *
   * @return
   *   An <code>ArrayList</code> of <code>Integers</code> representing the
   *   database IDs of all the referenced chromosomes.
   */
  public ArrayList<Integer> getReferencedChrIDs()
  {
    return chrIDs;
  }

  /**
   * <p>Add an {@link OntologyNode} representing a child of this node.</p>
   *
   * @param child
   *  The child {@link OntologyNode}.
   */
  public void addChild(OntologyNode child)
  {
    children.add(child);
  }

  /**
   * <p>Add an {@link OntologyNode} representing the parent of this node.</p>
   *
   * @param parent
   *  The parent {@link OntologyNode}.
   */
  public void addParent(OntologyNode parent)
  {
    this.parent = parent;
  }

  /**
   * Adds a reference to a piece of annotation.  References are by database ID,
   * and stored by chromosome.
   *
   * @param aID
   *   <code>Integer</code> value of the database ID for the annotation.
   *
   * @param chr
   *   <code>Integer</code> value of the database ID for the chromosome on which
   *   the referenced annotation exists.
   */
  public void addReferencedAnnotation(int aID, int chr)
  {
    if (annotationIDs.containsKey(chr))
    {
      ArrayList<Integer> aIDs = annotationIDs.get(chr);
      if (!aIDs.contains(aID))
        aIDs.add(aID);
    }
    else
    {
      ArrayList<Integer> aIDs = new ArrayList<Integer>();
      aIDs.add(aID);
      annotationIDs.put(chr, aIDs);
    }
  }

  /**
   * <p>Adds a chromosome (more specifically, its database ID) to this
   * <code>OntologyNode</code>'s list of referenced chromosomes.</p>
   *
   * @param cID
   *  <code>int</code> value of the chromosome's database ID.
   */
  public void addReferencedChromosome(int cID)
  {
    chrIDs.add(cID);
  }

  /**
   * <p>This is a convenience method and is equivalent to calling
   * <code>setFilter(boolean filter, null)</code>.
   *
   * @param filter
   *   <code>boolean</code> value denoting if this node is a filter.
   */
  public void setFilter(boolean filter)
  {
    setFilter(filter, null);
  }

  /**
   * Marks an <code>OntologyNode</code> as a "filter", which means that it is
   * relevant to the currently loaded species/chromosome.
   *
   * @param filter
   *   <code>boolean</code> value denoting if this node is a filter.
   * @param filterChild
   *   <code>OntologyNode</code> to add to this node's list of "filter" children.
   */
  public void setFilter(boolean filter, OntologyNode filterChild)
  {
    this.isFilter = filter;
    if (!filterChildren.contains(filterChild) && filterChild != null)
      filterChildren.add(filterChild);

    if (!filter)
    {
      filterChildren.clear();
    }
  }

  /**
   * Determines if this <code>OntologyNode</code> is a "filter" (if it is
   * relevant to the currently loaded species/chromosome).
   *
   * @return
   *   <code>true</code> if this node is a filter, <code>false</code> otherwise.
   */
  public boolean isFilter()
  {
    return isFilter;
  }

  /**
   * Associates a species with this <code>OntologyNode</code>.
   *
   * @param newSpecies
   *   the name of the species to add.
   */
  public void addSpecies(String newSpecies)
  {
    if (!species.contains(species))
      species.add(newSpecies);
  }

  /**
   * Increments this <code>OntologyNode</code>'s referenced annotation count by
   * one.
   *
   */
  public void incrementAnnotationDescendantCount()
  {
    incrementAnnotationDescendantCount(1);
  }

  /**
   * Increments this <code>OntologyNode</code>'s referenced annotation count by
   * <code>n</code>.
   *
   * @param n
   *   the amount by which to increment the count.
   */
  public void incrementAnnotationDescendantCount(int n)
  {
    annotationDescendants += n;
  }

  /**
   * Sets this <code>OntologyNode</code>'s referenced annotation count to a
   * arbitrary amount.
   *
   * @param n
   *   the number to set the count to.
   */
  public void setAnnotationDescendantCount(int n)
  {
    annotationDescendants = n;
  }

  /**
   * Gets the number of pieces of annotation (relevant to the current species/
   * chromosome/ontology category) with which this <code>OntologyNode</code> is
   * associated.
   *
   * @return
   *   the annotation count.
   */
  public int getAnnotationDescendantCount()
  {
    return annotationDescendants;
  }


  /**
   * <p>Checks to see if an {@link OntologyNode} is this {@link OntologyNode}s parent.</p>
   * @param child
   *    the node that is being tested as the descendant of this {@link OntologyNode}
   * @return
   *    true if the parent is an ancestor of the this {@link OntologyNode}, othewise false
   */
  public boolean isParent(OntologyNode child)
  {
    OntologyNode node = child;

    while (node.getParent() != null)
    {
      // Parent is the node to test
      node = node.getParent();

      if (node == this)
        return true;
    }

    return false;
  }

}
