package bioneos.vcmap.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.apache.log4j.Logger;

import bioneos.vcmap.gui.MainGUI;

/**
 * <p>Builds the internal {@link OntologyTree} on a background thread.</p>
 *
 * <p>Created on: march 26th, 2009</p>
 * @author dquacken@bioneos.com
 */

public class TreeBuilder
  extends Thread
{
  private Statement st = null;
  private ResultSet rs = null;
  private String query = null;
  private Logger logger;
  private OntologyTree ontologyTree;
  private MainGUI mainGUI = null;
  int treeID = -1;

  public TreeBuilder(Logger logger, Statement st, MainGUI mainGUI)
  {
    this.logger = logger;
    this.st = st;
    this.mainGUI = mainGUI;
  }

  /**
   * Returns a reference to the {@link OntologyTree}.
   *
   * @return
   *   the constructed {@link OntologyTree}.
   */
  public OntologyTree getOntologyTree()
  {
    return ontologyTree;
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Thread#run()
   */
  public void run()
  {
    try
    {
      query = "SELECT * FROM ontology_tree";
      rs = st.executeQuery(query);
      if (rs.next())
      {
        if (!conditionalYield())
          return;
        treeID = rs.getInt(1);
        ontologyTree = new OntologyTree(treeID, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
        logger.info("Created ontologyTree in Factory.");
      }
      else
      {
        logger.error("Error loading ontology: no ontology tree in DB.");
        return;
      }

      if (!conditionalYield())
        return;
      // now parse through the tree, creating new ontologyNodes
      HashMap<OntologyNode, Integer> parents = new HashMap<OntologyNode, Integer>();
      query = "SELECT * FROM ontology_node";
      rs = st.executeQuery(query);
      while (rs.next())
      {
        if (!conditionalYield())
          return;
        int nodeId = rs.getInt(1);
        int parentId = rs.getInt(3);
        String category = rs.getString(4);
        String description = rs.getString(5);
        OntologyNode node = new OntologyNode(nodeId, null, category, description);
        ontologyTree.addNode(node);
        parents.put(node, parentId);
      }

      // parse through ontology_children table and assign children to nodes
      query = "SELECT * FROM ontology_children";
      rs = st.executeQuery(query);
      while (rs.next())
      {
        if (!conditionalYield())
          return;
        OntologyNode currentNode = ontologyTree.getNodeByID(rs.getInt(1));
        OntologyNode currentChild = ontologyTree.getNodeByID(rs.getInt(2));

        if (currentNode != null && currentChild != null)
          currentNode.addChild(currentChild);
        else if (currentNode == null)
          logger.error("Error while adding children: unknown parent node: " + rs.getInt(1));
        else if (currentChild== null)
          logger.error("Error while adding children: unknown child node: " + rs.getInt(2));
      }

      // now that all nodes are created, go back and assign parents
      for (OntologyNode n : ontologyTree.getNodes())
      {
        if (!conditionalYield())
          return;
        OntologyNode parent = ontologyTree.getNodeByID(parents.get(n));
        n.addParent(parent);
      }

      //
      // To speed things up later, explicitly tell each bottom-level
      // ontology node which chromosomes, annotations and species
      // that it references.
      //
      HashMap<Integer, String> speciesIDs = new HashMap<Integer, String>();
      HashMap<Integer, Integer> chromIDs = new HashMap<Integer, Integer>();
      query = "SELECT id, species FROM maps";
      rs = st.executeQuery(query);
      while (rs.next())
      {
        if (!conditionalYield())
          return;
        speciesIDs.put(rs.getInt(1), rs.getString(2));
      }

      query= "SELECT id, map_id FROM chromosomes";
      rs = st.executeQuery(query);
      while (rs.next())
      {
        if (!conditionalYield())
          return;
        chromIDs.put(rs.getInt(1), rs.getInt(2));
      }

      // Query once for the 'ontology' attribute ID
      int attrID = -1;
      query = "SELECT id FROM attributes WHERE type='ontology'";
      rs = st.executeQuery(query);
      if (rs.next())
        attrID = rs.getInt(1);

      query = "SELECT v.value, avp.annotation_id, a.chromosome_id FROM ";
      query += "annotation a, annotation_avps avp, vals v WHERE ";
      query += "a.id=avp.annotation_id AND avp.attribute_id=" + attrID + " AND avp.value_id=v.id";
      yield();
      rs = st.executeQuery(query);

      while (rs.next())
      {
        if (!conditionalYield())
          return;
        int ontologyID = rs.getInt(1);
        OntologyNode current = ontologyTree.getNodeByID(ontologyID);
        if (current != null)
        {
          current.addReferencedAnnotation(rs.getInt(2), rs.getInt(3));
          current.addReferencedChromosome(rs.getInt(3));
          String species = speciesIDs.get(chromIDs.get(rs.getInt(3)));
          current.addSpecies(species);
        }
        else
          logger.error("Error assigning annotation to ontology node " + ontologyID);
      }
    }
    catch (SQLException e)
    {
      logger.error("There was an error while building the ontology tree: " + e);
    }
  }

  /**
   * Before yielding, ensures that the {@link MainGUI} is still visible.
   *
   * @return
   *   <code>true</code> if the MainGUI is visible, <code>false</code> otherwise.
   */
  private boolean conditionalYield()
  {
    if (!mainGUI.isVisible())
      return false;
    else
      yield();

    return true;
  }
}
