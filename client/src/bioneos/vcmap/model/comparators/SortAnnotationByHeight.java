package bioneos.vcmap.model.comparators;

import java.util.Comparator;

import bioneos.vcmap.model.Annotation;

public class SortAnnotationByHeight implements Comparator<Object>
{
  public int compare(Object a1, Object a2)
  {
    if (a1 instanceof Annotation && a2 instanceof Annotation)
    {
      int a1Height = ((Annotation)a1).getStop() - ((Annotation)a1).getStart();
      int a2Height = ((Annotation)a2).getStop() - ((Annotation)a2).getStart();

      return a1Height - a2Height;
    }
    else
      throw new ClassCastException();
  }
}
