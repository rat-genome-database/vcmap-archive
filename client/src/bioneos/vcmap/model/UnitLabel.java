package bioneos.vcmap.model;

import java.awt.FontMetrics;

import bioneos.vcmap.gui.MapNavigator;

/**
 * <p>Stores the information needed to display a unit label in the
 * {@link MapNavigator}.</p>
 *
 * <p>Created on: December 11th, 2008</p>
 * @author jaaseby@bioneos.com
 */
public class UnitLabel
  implements Comparable<UnitLabel>
{
  private FontMetrics metrics;
  private String label;
  private double labelYCoord;
  private double unitYCoord;
  private int labelWidth;
  private boolean temporary;

  /**
   * <p>Constructor for {@link UnitLabel}. Stores the metrics needed to do
   * string measurements.</p>
   *
   * @param metrics
   *   {@link FontMetrics} used when displaying the {@link UnitLabel}
   */
  public UnitLabel(FontMetrics metrics)
  {
    this(metrics, false);
  }
  
  /**
   * Create a UnitLabel and specify whether or not the label is a temporary one
   * (created when opening an {@link OverlapBox}) or regular.
   * @param metrics
   *   {@link FontMetrics} used when displaying the {@link UnitLabel}.
   * @param temp
   *   True for a temporary label, false for a regular label.
   */
  public UnitLabel(FontMetrics metrics, boolean temp)
  {
    this.metrics = metrics;
    this.temporary = temp;
  }

  /**
   * <p>Set the {@link String} value of the {@link UnitLabel}</p>
   *
   * @param unitLabel
   *   {@link String} value of the {@link UnitLabel}
   */
  public void setLabel(String unitLabel)
  {
    label = unitLabel;
    labelWidth = metrics.stringWidth(label);
  }

  /**
   * <p>Set the int value of the y-coordinate for the label of {@link UnitLabel}</p>
   *
   * @param yCoord
   *   int value of the y-coordinate of the label for the {@link UnitLabel}
   */
  public void setLabelYCoord(double yCoord)
  {
    labelYCoord = yCoord;
  }

  /**
   * <p>Set the int value of the y-coordinate for the connecting line
   * of {@link UnitLabel}</p>
   *
   * @param yCoord
   *   int value of the y-coordinate of the connecting line for the
   *   {@link UnitLabel}
   */
  public void setUnitYCoord(double yCoord)
  {
    unitYCoord = yCoord;
  }

  /**
   * <p>Get the {@link String} for the {@link UnitLabel}.</p>
   *
   * @return
   *   {@link String} value the {@link UnitLabel}
   */
  public String getLabel()
  {
    return label;
  }

  /**
   * <p>Get the int value of the y-coordinate for the connecting line
   * of {@link UnitLabel}</p>
   *
   * @return
   *   int value of the y-coordinate of the connecting line for the
   *   {@link UnitLabel}
   */
  public double getUnitYCoord()
  {
    return unitYCoord;
  }

  /**
   * <p>Get the int value of the y-coordinate for the label of {@link UnitLabel}</p>
   *
   * @return
   *   int value of the y-coordinate of the label for the {@link UnitLabel}
   */
  public double getLabelYCoord()
  {
    return labelYCoord;
  }

  /**
   * <p>Get the width of the label for the {@link UnitLabel} in pixels</p>
   *
   * @return
   *   int value for the width of the {@link UnitLabel}'s width
   */
  public int getLabelWidth()
  {
    return labelWidth;
  }

  /**
   * Whether or not the label is temporary.
   * @return
   *   True if the label is temporary (from an {@link OverlapBox) or false if
   *   the label is a regular, permanent label. 
   */
  public boolean isTemporary()
  {
    return temporary;
  }

  /*
   * (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(UnitLabel otherUnitLabel)
  {
    return (int)(labelYCoord - otherUnitLabel.getLabelYCoord());
  }
}
