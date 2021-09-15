package bioneos.vcmap.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * <p>This class represents the Ontology Tree data held in the ontology_tree
 * table of the database.</p>
 *
 * <p>Created: 2008.12.11</p>
 * @author dquacken@bioneos.com
 */

public class OntologyTree
{
  // Logging
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // Variables
  private int id = -1;
  private String name = null;
  private String type = null;
  private String version = null;
  private String loaded = null;
  private ArrayList<OntologyNode> nodes = null;
  private HashMap<Integer, OntologyNode> nodesByID = null;

  // Constants
  public static final String ROOT_NODE = "Trait";

  /**
   * <p>Constructor for {@link OntologyTree}.</p>
   */
  public OntologyTree(int id, String name, String type, String version, String loaded)
  {
    this.id = id;
    this.name = name;
    this.type = type;
    this.version = version;
    this.loaded = loaded;

    nodes = new ArrayList<OntologyNode>();
    nodesByID = new HashMap<Integer, OntologyNode>();
  }


  /**
   * <p>Gets the database id of the {@link OntologyTree}.</p>
   *
   * @return
   *   this {@link OntologyTree}'s database id
   */
  protected int getID()
  {
    return id;
  }

  /**
   * <p>Returns a <code>String</code> containing the name of the {@link OntologyTree}</p>
   *
   * @return
   *   the name.
   */
  public String getName()
  {
    return name;
  }

  /**
   * <p>Returns a <code>String</code> containing the type of the {@link OntologyTree}</p>
   *
   * @return
   *   the type of this node.
   */
  public String getType()
  {
    return type;
  }

  /**
   * <p>Return a <code>String</code> containing the version of the {@link OntologyTree}</p>
   *
   * @return
   *   the version.
   */
  public String getVersion()
  {
    return version;
  }

  /**
   * <p>Return the {@link OntologyNode} matching the given ID.</p>
   *
   * @param id
   *  <code>Integer</code> representing the {@link OntologyNode}'s ID.
   *
   * @return
   *   {@link OntologyNode} index of the root node.
   */
  public OntologyNode getNodeByID(int id)
  {
    return nodesByID.get(id);
  }

  /**
   * <p>Returns all {@link OntologyNode}s associated with this {@link OntologyTree}.</p>
   *
   * @return
   *   <code>ArrayList</code> of {@link OntologyNode}s.
   */
  public ArrayList<OntologyNode> getNodes()
  {
    return nodes;
  }

  /**
   * Determines if an {@link OntologyNode} with the given ID exists in this tree.
   *
   * @param id
   *   An <code>int</code> representing a database ID.
   * @return
   *   <code>boolean</code> value indicating if the node exists.
   */
  public boolean nodeExists(int id)
  {
    if (nodesByID.containsKey(id))
      return true;
    else
      return false;
  }

  /**
   * <p>Adds a {@link OntologyNode} to the {@link ArrayList} of nodes.</p>
   */
  public void addNode(OntologyNode node)
  {
    nodes.add(node);
    nodesByID.put(node.getID(), node);
  }

  /**
   * <p>Recursively finds all relevant descendants of a given
   * {@link OntologyNode}'s descendants.</p>
   *
   * @param node
   *   {@link OntologyNode} that is the root of the ontology hierarchy.
   *
   * @return
   *   <code>ArrayList</code> containing all (relevant) descendants of the
   *   parent node.
   *
   * @author dquacken@bioneos.com
   */
  public ArrayList<OntologyNode> traverseTree(OntologyNode node)
  {
    ArrayList<OntologyNode> nodes = new ArrayList<OntologyNode>();

    // make sure we add the 'root' node to our list
    if (!nodes.contains(node))
      nodes.add(node);

    if (node.getFilterChildren() != null)
    {
      for (OntologyNode n : node.getFilterChildren())
        nodes.addAll(traverseTree(n));
    }
    else
    {
      nodes.add(node);
      return nodes;
    }
    return nodes;
  }
}
