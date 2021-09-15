package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.gui.MapNavigator.FeatureGap;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.DisplayAnnotation;

/**
 * <p>{@link Comparator} to sort {@link Annotation} or {@link DisplayAnnotation}
 * or {@link FeatureGap} by start positions, in ascending order.</p>
 *
 * <p>Created on: January 29, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class SortAnnotationByStart
  implements Comparator<Object>
{
  public int compare(Object a1, Object a2)
  {
    if (a1 instanceof Annotation && a2 instanceof Annotation)
    {
      return ((Annotation)a1).getStart() - ((Annotation)a2).getStart();
    }
    else if (a1 instanceof DisplayAnnotation && a2 instanceof DisplayAnnotation)
    {
      return ((DisplayAnnotation)a1).getStart() - ((DisplayAnnotation)a2).getStart();
    }
    else if (a1 instanceof FeatureGap && a2 instanceof FeatureGap)
    {
      return ((FeatureGap)a1).getAnnotationStart() - ((FeatureGap)a2).getAnnotationStart();
    }
    else if (a1 instanceof DisplayAnnotation.DAMember && a2 instanceof DisplayAnnotation.DAMember)
    {
      return ((DisplayAnnotation.DAMember)a1).getAnnotation().getStart() - ((DisplayAnnotation.DAMember)a1).getAnnotation().getStart();
    }
    else
      throw new ClassCastException();
  }
}
