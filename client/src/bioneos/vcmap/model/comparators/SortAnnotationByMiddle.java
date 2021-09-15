package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.Annotation;

public class SortAnnotationByMiddle implements Comparator<Object>
{
  public int compare(Object a1, Object a2)
  {
    if (a1 instanceof Annotation && a2 instanceof Annotation)
    {
      int a1Middle = (((Annotation)a1).getStop() + ((Annotation)a1).getStart()) / 2;
      int a2Middle = (((Annotation)a2).getStop() + ((Annotation)a2).getStart()) / 2;

      if ((a1Middle - a2Middle) == 0)
        return 1;

      return a1Middle - a2Middle;
    }
    else
      throw new ClassCastException();
  }
}
