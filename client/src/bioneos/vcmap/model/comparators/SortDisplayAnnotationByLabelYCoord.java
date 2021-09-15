package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.DisplayAnnotation;

/**
 * <p>{@link Comparator} to sort {@link DisplayAnnotation} by their label
 * y-coordinates, in ascending order.</p>
 *
 * <p>Created on: March 24, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class SortDisplayAnnotationByLabelYCoord
  implements Comparator<DisplayAnnotation>
{
  public int compare(DisplayAnnotation a1, DisplayAnnotation a2)
  {
    return a1.getLabelYCoord() - a2.getLabelYCoord();
  }
}