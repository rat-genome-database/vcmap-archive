package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.DisplayAnnotation;

/**
 * <p>{@link Comparator} to sort {@link Annotation} or {@link DisplayAnnotation}
 * by stop positions, in ascending order.</p>
 *
 * <p>Created on: January 29, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class SortAnnotationByStop
  implements Comparator<Object>
{
  public int compare(Object a1, Object a2)
  {
    if (a1 instanceof Annotation && a2 instanceof Annotation)
    {
      return ((Annotation)a1).getStop() - ((Annotation)a2).getStop();
    }
    else if (a1 instanceof DisplayAnnotation && a2 instanceof DisplayAnnotation)
    {
      return ((DisplayAnnotation)a1).getStop() - ((DisplayAnnotation)a2).getStop();
    }
    else if (a1 instanceof DisplayAnnotation.DAMember && a2 instanceof DisplayAnnotation.DAMember)
    {
      return ((DisplayAnnotation.DAMember)a1).getAnnotation().getStop() - ((DisplayAnnotation.DAMember)a1).getAnnotation().getStop();
    }
    else
      throw new ClassCastException();
  }
}
