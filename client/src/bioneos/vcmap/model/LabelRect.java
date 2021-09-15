package bioneos.vcmap.model;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * <p></p>
 * <p>Created on: August 2, 2010</p>
 * @author cgoodman
 *
 */
public class LabelRect
  extends Rectangle
{
  private static final long serialVersionUID = 1L;

  // Variables
  public String labelText;
  public AnnotationSet set;

  /**
   * <p>Initialize parameters for the labelRect</p>
   * @param type
   *    the {@link String} title of the label
   * @param x
   *    the leftmost x-value of the label
   * @param y
   *    the topmost y-value of the label
   * @param width
   *    the width of the label
   * @param height
   *    the height of the label
   */
  public LabelRect(AnnotationSet set, String type, int x, int y, int width, int height)
  {
    super(x, y, width, height);
    this.set = set;
    labelText = type;
  }

  /**
   * <p>Determines if a {@link Point} is contained within the {@link LabelRect}s
   * {@link Rectangle} bounds</p>
   * @param point
   *    the {@link Point} being checked
   * @return
   *    true if the point is within the rectangle bounds otherwise false
   */
  public boolean containsPoint (Point point)
  {
    return (this.x <= point.x && point.x <= this.x + this.width &&
            this.y <= point.y && point.y <= this.y + this.height);
  }
}