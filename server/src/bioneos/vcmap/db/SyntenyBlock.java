package bioneos.vcmap.db;

import java.util.Vector;

/**
 * Class to store the data needed to represent a block of synteny.
 */
public class SyntenyBlock
{
  public int targetChr;
  public int matches = 0;
  public Vector<Integer> aIds = new Vector<Integer>();

  public SyntenyBlock(int target, int aid)
  {
    targetChr = target;
    aIds.add(aid);
  }
}
