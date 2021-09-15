package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.DisplayAnnotation;

/**
 * <p>{@link Comparator} to sort {@link DisplayAnnotation} by their middle in ascending order.</p>
 *
 * <p>Created on: April 14, 2010</p>
 * @author tstiefel@bioneos.com
 */

public class SortDisplayAnnotationByMiddle implements Comparator<DisplayAnnotation>
{
  public int compare(DisplayAnnotation a1, DisplayAnnotation a2)
  {
    int a1Middle = (a1.getStart() + a1.getStop()) / 2;
    int a2Middle = (a2.getStart() + a2.getStop()) / 2;

    return a1Middle - a2Middle;
  }
}
