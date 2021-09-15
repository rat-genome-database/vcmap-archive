package bioneos.vcmap.model;

import java.awt.FontMetrics;
import java.util.Vector;

import bioneos.vcmap.gui.MapNavigator;

/**
 * <p>This class is a helper class that keeps track of information that is
 * needed to display a group of overlapping {@link Annotation}.</p>
 *
 * <p>Created on: November 4, 2008</p>
 * @author jaaseby@bioneos.com
 */
public class DisplayAnnotation
{
  // Global Variables
  private Vector<DAMember> annotation;
  private Annotation startAnnot;
  private Annotation stopAnnot;

  private int maxLabelWidth;
  private int labelWidth;
  private String label;

  private FontMetrics metrics;
  private int maxColumn;
  private int column;
  private Annotation maxHeightAnnot;
  private Annotation minHeightAnnot;
  private int yCoord;
  private int lineYCoord;

  /**
   * <p>Constructor for {@link DisplayAnnotation}. Sets up the variables
   * needed to properly use this class.</p>
   *
   * @param metrics
   *   {@link FontMetrics} used when displaying the {@link DisplayAnnotation}
   * @param maxLabelWidth
   *   The value of the maximum width (in pixels) for the label of this
   *   {@link DisplayAnnotation} element.
   */
  public DisplayAnnotation(FontMetrics metrics, int maxLabelWidth)
  {
    this.metrics = metrics;
    this.maxLabelWidth = (maxLabelWidth < 0) ? 0 : maxLabelWidth;

    lineYCoord = -1;
    labelWidth = -1;
    maxColumn = -1;
    annotation = new Vector<DAMember>();
  }
  /**
   * The default constructor for this class simply created a DisplayAnnotation
   * object that is not able to measure its label width (no {@link FontMetrics})
   */
  public DisplayAnnotation()
  {
    this(null, 0);
  }

  public void setAnnotation(Annotation annotation, int column)
  {
    if (annotation == null) return;

    DAMember annot = new DAMember(annotation, column);
    this.annotation.add(annot);
    startAnnot = annotation;
    stopAnnot = startAnnot;
    maxHeightAnnot = startAnnot;
    minHeightAnnot = startAnnot;
    maxColumn = column;
  }

  /**
   * <p>Set the {@link Annotation} of the {@link DisplayAnnotation}.</p>
   *
   * @param annotation
   *   This {@link DisplayAnnotation} displays information for this
   *   {@link Vector} of {@link Annotation}
   * @param featureLabelType
   *   What label of the {@link Annotation} to use when displaying
   */
  public long merge(DisplayAnnotation otherDA)
  {
    long timer = System.nanoTime();
    label = null;
    if (otherDA.getDisplayedAnnotation().getLength() > maxHeightAnnot.getLength())
      maxHeightAnnot = otherDA.getDisplayedAnnotation();
    else if (otherDA.getMinHeightAnnot().getLength() < minHeightAnnot.getLength())
      minHeightAnnot = otherDA.getMinHeightAnnot();
    if (otherDA.getStopAnnot().getStop() > stopAnnot.getStop())
      stopAnnot = otherDA.getStopAnnot();
    else if (otherDA.getStartAnnot().getStart() < startAnnot.getStart())
      startAnnot = otherDA.getStartAnnot();
    
    annotation.addAll(otherDA.getAnnotation());
    return System.nanoTime() - timer;
  }

  public long addAnnotation(Annotation annotation, int column)
  {
    long timer = System.nanoTime();
    DAMember newAnnot = new DAMember(annotation, column);
    this.annotation.add(newAnnot);
    maxColumn = Math.max(maxColumn, column);
    if (newAnnot.getHeight() > maxHeightAnnot.getLength())
      maxHeightAnnot = annotation;
    else if (newAnnot.getHeight() < minHeightAnnot.getLength())
      minHeightAnnot = annotation;
    if (newAnnot.getStop() > stopAnnot.getStop())
      stopAnnot = annotation;
    else if (newAnnot.getStart() < startAnnot.getStart())
      startAnnot = annotation;
    label = null;
    return (System.nanoTime() - timer);
  }

  /**
   * <p>Set what column the {@link DisplayAnnotation} is displayed in.</p>
   *
   * @param column
   *   int value of the column the {@link DisplayAnnotation} is displayed in.
   */
  public void setColumn(int column)
  {
    this.column = column;
    maxColumn = Math.max(maxColumn, column);
  }

  /**
   * <p>Set the y-coordinate of the line that connects the label to the
   * displayed {@link Annotation}. Used only when the {@link Annotation}
   * is in a column other than the first column.</p>
   *
   * @param yCoord
   *   int value of the y-coordinate of the connecting line
   */
  public void setLineYCoord(int yCoord)
  {
    lineYCoord = yCoord;
  }

  /**
   * <p>Helper method that determines what the label for this
   * {@link DisplayAnnotation} will be.</p>
   *
   */
  public void determineLabel()
  {
    String multipleElementsString = "(" + annotation.size() + ")";
    StringBuilder lbl = new StringBuilder();
    StringBuilder dlbl = new StringBuilder();

    // Determine label
    if (annotation.size() == 1)
    {
      lbl.append(startAnnot.getName());
    }
    else
    {
      lbl.append(maxHeightAnnot.getName());
      lbl.append(multipleElementsString);
    }

    dlbl.append(lbl.toString());

    // Check to see if display label is too long
    if (metrics != null)
    {
      if (metrics.stringWidth(lbl.toString()) > maxLabelWidth)
      {
        int adjust = 0;
        if (annotation.size() > 1)
          adjust += multipleElementsString.length();

        dlbl.insert(dlbl.length() - adjust, "...");
        adjust += 3;

        while (metrics.stringWidth(dlbl.toString()) > maxLabelWidth && dlbl.length() - adjust - 1 > 0)
        {
          dlbl.delete(dlbl.length() - adjust - 1, dlbl.length() - adjust);
        }

        label = dlbl.toString();
        labelWidth = metrics.stringWidth(label);
      }
      else
      {
        label = lbl.toString();
        labelWidth = metrics.stringWidth(label);
      }
    }
  }

  /**
   * <p>Get the {@link FontMetrics} associated with this
   * {@link DisplayAnnotation}.</p>
   *
   * @return
   *   {@link FontMetrics} of this {@link DisplayAnnotation}. null
   *   if there is no {@link FontMetrics} for this {@link DisplayAnnotation}
   */
  public FontMetrics getMetrics()
  {
    return metrics;
  }

  public void setMetrics(FontMetrics metrics)
  {
    this.metrics = metrics;
    determineLabel();
  }

  /**
   * <p>Get the {@link Annotation} whose name is displayed.</p>
   *
   * @return
   *   {@link Annotation} whose name is displayed or null if there
   *   is no annotation
   */
  public Annotation getDisplayedAnnotation()
  {
    return maxHeightAnnot;
  }

  /**
   * <p>Get the overlapping {@link Annotation} this {@link DisplayAnnotation}
   * represents.</p>
   *
   * @return
   *   {@link Vector} of {@link Annotation}
   */
  public Vector<DAMember> getAnnotation()
  {
    return annotation;
  }

  public Vector<Annotation> getAnnotationAsVector()
  {
    Vector<Annotation> v = new Vector<Annotation>();

    for (DAMember member : annotation)
      v.add(member.getAnnotation());

    return v;
  }

  /**
   * <p>Get the {@link Annotation} with the smallest start point on the
   * {@link DisplaySegment}</p>
   *
   * @return
   *   {@link Annotation} with the smallest start point, or null if this
   *   {@link DisplayAnnotation} does not have any annotation in it.
   */
  public Annotation getStartAnnot()
  {
    return startAnnot;
  }

  /**
   * <p>Get the {@link Annotation} with the largest stop point on the
   * {@link DisplaySegment}</p>
   *
   * @return
   *   {@link Annotation} with the largest stop point, or null if this
   *   {@link DisplayAnnotation} does not have any annotation in it.
   */
  public Annotation getStopAnnot()
  {
    return stopAnnot;
  }

  /**
   * <p>Get the lowest start point on the {@link DisplaySegment}</p>
   *
   * @return
   *  int value of the lowest start point or -1 if there is no
   *  {@link Annotation} in stored in the {@link DisplayAnnotation}
   */
  public int getStart()
  {
    if (startAnnot == null) return -1;
    return startAnnot.getStart();
  }

  /**
   * <p>Get the largest stop point on the {@link DisplaySegment}</p>
   *
   * @return
   *  int value of the largest stop point or -1 if there is no
   *  {@link Annotation} in stored in the {@link DisplayAnnotation}
   */
  public int getStop()
  {
    if (stopAnnot == null) return -1;
    return stopAnnot.getStop();
  }

  /**
   * <p>Get the columns the {@link Annotation} should be displayed in.</p>
   *
   * @return
   *   {@link Vector} of a {@link Vector} of {@link Annotation} representing
   *   a the vertical columns of {@link Annotation} displayed in the
   *   {@link MapNavigator}
   */
  public int getColumn()
  {
    return column;
  }

  public int getMaxColumn()
  {
    return maxColumn;
  }
  /**
   * <p>Get the label that is displayed in the {@link Annotation} columns of
   * a {@link DisplayMap}.</p>
   *
   * @return
   *   {@link String} value of the {@link Annotation} displayed label
   */
  public String getLabel()
  {
    if (label == null) determineLabel();
    return label;
  }

  /**
   * <p>Set the baseline of the displayed label.</p>
   *
   * @param ycoordinate
   *   int value of the y-coordinate of the baseline of the label
   */
  public void setLabelYCoord(int ycoordinate)
  {
    this.yCoord = ycoordinate;
  }

  /**
   * <p>Get the baseline of the displayed label.</p>
   *
   * @return
   *   int value of the y-coordinate of the baseline of the label
   */
  public int getLabelYCoord()
  {
    return yCoord;
  }

  /**
   * <p>String width of the displayed label for the {@link DisplayAnnotation}.
   * </p>
   *
   * @return
   *   int value of the width of the displayed label
   */
  public int getLabelWidth()
  {
    if (label == null) determineLabel();
    return labelWidth;
  }

  /**
   * <p>Get the y-coordinate of the line that connects the label to the
   * displayed {@link Annotation}. Used only when the {@link Annotation}
   * is in a column other than the first column.</p>
   *
   * @return
   *   int value of the y-coordinate of the connecting line
   */
  public int getLineYCoord()
  {
    if (lineYCoord == -1)
      return yCoord;
    else
      return lineYCoord;
  }

  /**
   * <p>Check if the {@link DisplayAnnotation} contains a specific piece of
   * {@link Annotation}.
   *
   * @param annotation
   *   Determine if this {@link Annotation} is in this
   *   {@link DisplayAnnotation}
   * @return
   *   true - The {@link Annotation} is in this {@link DisplayAnnotation}
   *   false - The {@link Annotation} is NOT in this {@link DisplayAnnotation}
   */
  public boolean contains(Annotation annotation)
  {
    if (annotation == null) return false;

    for (DAMember member : this.annotation)
    {
      if (member.getAnnotation().equals(annotation))
        return true;
    }

    return false;
  }

  /**
   * <p>Returns the smallest {@link Annotation} in this {@link DisplayAnnotation}.</p>
   *
   * @return
   *    the smallest {@link Annotation} in this {@link DisplayAnnotation}
   */
  public Annotation getMinHeightAnnot()
  {
    return minHeightAnnot;
  }

  /**
   * <p>Represents a member of this {@link DisplayAnnotation}.  This class
   * contains each {@link Annotation} that is in this {@link DisplayAnnotation}
   * and which column that {@link Annotation} is displayed in.
   *
   * @author tstiefel@bioneos.com
   *
   */
  public static class DAMember
  {
    private int column;
    private Annotation annot;
    private int start;
    private int stop;
    private int height;

    public DAMember(Annotation annot, int column)
    {
      this.column = column;
      this.annot = annot;
      start = annot.getStart();
      stop = annot.getStop();
      height = annot.getLength();
    }

    public void setAnnot(Annotation annot)
    {
      this.annot = annot;
    }

    public void setColumn(int column)
    {
      this.column = column;
    }

    public Annotation getAnnotation()
    {
      return annot;
    }

    public int getColumn()
    {
      return column;
    }

    public int getStart()
    {
      return start;
    }

    public int getStop()
    {
      return stop;
    }

    public int getHeight()
    {
      return height;
    }
  }
}