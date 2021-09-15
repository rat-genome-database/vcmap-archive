package bioneos.vcmap.model.comparators;

import java.util.Comparator;

/**
 * Simple {@link Comparator} to correctly order chromosome names following the
 * standard "chrX" naming convention.
 *
 * <p>Created on: October 15, 2008</p>
 * @author sgdavis@bioneos.com
 */

public class ChromosomeComparator
  implements Comparator<String>
{
  public int compare(String first, String second)
  {
    if (first.matches("chr.*")) first = first.substring(3);
    if (second.matches("chr.*")) second = second.substring(3);

    if (first.matches("\\d+") && second.matches("\\d+"))
    {
      // Integer comparison
      try
      {
        int firstInt = Integer.parseInt(first) ;
        int secondInt = Integer.parseInt(second) ;
        if (firstInt < secondInt) return -1 ;
        else if (firstInt > secondInt) return 1 ;
      }
      catch (NumberFormatException e)
      {
        // Do nothing
      }
    }
    
    // Fallback
    return first.compareTo(second);
  }
}
