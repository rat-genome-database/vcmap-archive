package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.OntologyNode;

/**
 * <p>{@link Comparator} to sort {@link OntologyNode} by category namees
 * in ascending order.</p>
 *
 * <p>Created on: June 9, 2009</p>
 * @author pradeep@bioneos.com
 */

public class SortOntologyByName
 implements Comparator<OntologyNode>
{
 public int compare(OntologyNode o1, OntologyNode o2)
 {
   return o1.getCategory().compareToIgnoreCase(o2.getCategory());
 }
}
