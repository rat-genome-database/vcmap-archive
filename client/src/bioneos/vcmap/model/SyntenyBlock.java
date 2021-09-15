package bioneos.vcmap.model;

import bioneos.vcmap.gui.MapNavigator;

/**
 * <p>This class defines a syntenic region for two maps from two different
 * species.  The block does not actually store position information, it simply
 * keeps track of the outermost flanking markers that map from each of the
 * {@link MapData} objects.  These flanks are refered to as left and right
 * flanks for simplicity, although they may appear in the opposite order when
 * displayed in the {@link MapNavigator}.  Potentially, each
 * {@link SyntenyBlock} could have four of these flanking markers in the most
 * complex situation.  In the simplest situation, there will still be four
 * flanking markers, but both the top and bottom markers will be siblings of
 * each other.</p>
 *
 * <p>Created on: July 30th, 2008</p>
 * @author sgdavis@bioneos.com
 */

public class SyntenyBlock
{
  private Chromosome leftChromosome;
  private Chromosome rightChromosome;
  private int leftStart;
  private int leftStop;
  private int rightStart;
  private int rightStop;

  /**
   * Set the {@link Annotation} markers used to denote the start and stop
   * boundaries of the left {@link Chromosome} involved in this block.  This
   * method assumes requires that both the start and stop {@link Annotation}s
   * are from the same {@link Chromosome}. Please note that the start marker
   * may occur earlier on the {@link Chromosome} than the stop marker, for
   * example, in the case of an inversion.
   *
   * @param start
   *   The int value of the start of this block.
   * @param stop
   *   The int value of the stop of this block.
   */
  public void setLeftMarkers(Chromosome chr, int start, int stop)
  {
    if (start < 0 || stop < 0)
      throw new IllegalArgumentException("SyntenyBlock does not support null markers");
    if (chr == null)
      throw new IllegalArgumentException("Chromosome does not exist");
    leftChromosome = chr;
    leftStart = start;
    leftStop = stop;
  }

  /**
   * Set the {@link Annotation} markers used to denote the start and stop
   * boundaries of the right {@link Chromosome} involved in this block.  This
   * method assumes requires that both the start and stop {@link Annotation}s
   * are from the same {@link Chromosome}. Please note that the start marker
   * may occur earlier on the {@link Chromosome} than the stop marker, for
   * example, in the case of an inversion.
   *
   * @param start
   *   The {@link Annotation} representing the start of this block.
   * @param stop
   *   The {@link Annotation} representing the stop of this block.
   */
  public void setRightMarkers(Chromosome chr, int start, int stop)
  {
    if (start < 0 || stop < 0)
      throw new IllegalArgumentException("SyntenyBlock does not support null markers");
    if (chr == null)
      throw new IllegalArgumentException("Chromosome does not exist");
    rightChromosome = chr;
    rightStart = start;
    rightStop = stop;
  }

  /**
   * Get the {@link MapData} object for the left side of this block.
   *
   * @return
   *   The {@link MapData} object containing the left Chromosome
   */
  public Chromosome getLeftChr()
  {
    return leftChromosome;
  }

  /**
   * Get the {@link MapData} object for the right side of this block.
   *
   * @return
   *   The {@link MapData} object containing the right Chromosome
   */
  public Chromosome getRightChr()
  {
    return rightChromosome;
  }

  /**
   * The start marker for the left side of this block.  May have a start value
   * less than the start value of the stop marker (inverted SyntenyBlock).
   *
   * @return
   *   The left start int.
   */
  public int getLeftStart()
  {
    return leftStart;
  }

  /**
   * The stop value (int) for the left side of this block.  May have a stop value
   * greater than the stop value of the start marker (inverted SyntenyBlock).
   *
   * @return
   *   The left stop int.
   */
  public int getLeftStop()
  {
    return leftStop;
  }

  /**
   * The start value (int) for the right side of this block.  May have a start value
   * less than the start value of the stop marker (inverted SyntenyBlock).
   *
   * @return
   *   The right start {@link Annotation} object.
   */
  public int getRightStart()
  {
    return rightStart;
  }

  /**
   * The stop value (int) for the right side of this block.  May have a stop value
   * greater than the stop value of the start marker (inverted SyntenyBlock).
   *
   * @return
   *   The right stop {@link Annotation} object.
   */
  public int getRightStop()
  {
    return rightStop;
  }
}
