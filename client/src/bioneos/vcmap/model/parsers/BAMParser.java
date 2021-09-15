package bioneos.vcmap.model.parsers;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecord.SAMTagAndValue;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.Factory;

/**
 * <p>Provides a method to parse {@link Annotation} for a {@link DisplayMap}
 * for files in the GFF format.</p>
 * @author cgoodman
 * <p>Created on: August 11, 2010</p>
 */
public class BAMParser
  implements FileParser
{
  //Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private HashSet<Integer> errors;
  private String errorString;

  /**
   * <p>Constructor initializes class variables</p>
   */
  public BAMParser()
  {
    errors = new HashSet<Integer>();
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.model.parsers.FileParser#parseFile(java.io.File, bioneos.vcmap.model.DisplayMap, bioneos.vcmap.gui.MainGUI)
   */
  public Vector<Annotation> parseFile(File file, DisplayMap displayMap, MainGUI mainGUI)
    throws OutOfMemoryError, SQLException
  {
    // Setup our helper variables
    AnnotationSet set;
    HashMap<String, Chromosome> chromosomes = new HashMap<String, Chromosome>();
    Vector<Annotation> annotation = new Vector<Annotation>();

    // Build chromosome HashMap
    for (DisplaySegment seg : displayMap.getSegments())
      chromosomes.put(seg.getChromosome().getName(), seg.getChromosome());

    displayMap.getMap().getAllAnnotationSets();

    int lineNum = 1;
    try
    {
      // Create reader
      SAMFileReader fileReader = new SAMFileReader(file);

      // Set type of data
      String prefix = (fileReader.isBinary()) ? "BAM" : "SAM";

      // Determine annotation set
      set = Factory.getCustomAnnotationSet(prefix + "" + SUFFIX, displayMap.getMap(), file, prefix + " file");


      // Iterate through each line
      for (SAMRecord samRecord : fileReader)
      {
        mainGUI.setProgressPercentageDone(lineNum);
        lineNum++;

        // Check for chromosome
        String chrId = samRecord.getReferenceName().trim().toLowerCase().replaceAll("\\.", "");
        if (chrId.toLowerCase().startsWith("atchr")) // Format used in PlantGDB
          chrId = chrId.substring(2);
        else if (!chrId.toLowerCase().startsWith("chr"))
          chrId = "chr" + chrId;
        Chromosome chromosome = chromosomes.get(chrId);

        // If chromosome is found in MainGUI create a new Annotation
        if (chromosome != null)
        {
          Annotation newAnnot = new Annotation(chromosome);
          newAnnot.setStart(samRecord.getAlignmentStart());
          newAnnot.setStop(samRecord.getAlignmentEnd());

          // TODO - Set aliases for ReadGroup ID (if present)
          newAnnot.addName(samRecord.getReadName());
          newAnnot.setAnnotationSet(set);

          for (SAMTagAndValue stav : samRecord.getAttributes())
          {
            if (!(stav.tag.startsWith("X") || stav.tag.startsWith("Y") || stav.tag.startsWith("Z")))
              newAnnot.addInfo(tagToHuman(stav.tag), stav.value.toString());
          }

          annotation.add(newAnnot);
        }
      }
    }
    catch (Exception e)
    {
      logger.error("File read error in SAM/BAM file (line: " + lineNum + "): " + e);
      errorString = "File read error in SAM/BAM file on line " + lineNum;
      errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
      return null;
    }

    return annotation;
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.model.parsers.FileParser#hasError(int)
   */
  public boolean hasError(int errorCode)
  {
    return errors.contains(new Integer(errorCode));
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.model.parsers.FileParser#getErrorString()
   */
  public String getErrorString()
  {
    return errorString;
  }

  /**
   * Helper method for translating SAM tags into human readable format text.
   */
  private String tagToHuman(String tag)
  {
    // NOTE: some of these (like HI -> hit index) might actually serve to
    //   enable us to tie some of the hits together or might otherwise be
    //   better used elsewhere than dumping them into the "additional info".
    if (tag.equals("RG")) return "Read Group";
    else if (tag.equals("LB")) return "Library";
    else if (tag.equals("PU")) return "Platform Unit";
    else if (tag.equals("AS")) return "Alignment Score";
    else if (tag.equals("OQ")) return "Original Base Quality";
    else if (tag.equals("E2")) return "Second-most Likely Base Call";
    else if (tag.equals("U2")) return "Log-lk Ratio";
    else if (tag.equals("MQ")) return "Mapping Quality";
    else if (tag.equals("NM")) return "# of Nucleotide Differences";
    else if (tag.equals("H0")) return "# of Hits";
    else if (tag.equals("H1")) return "# of 1-difference Hits";
    else if (tag.equals("H2")) return "# of 2-difference Hits";
    else if (tag.equals("UQ")) return "Read sequence likelihood";
    else if (tag.equals("PQ")) return "Read pair likelihood";
    else if (tag.equals("NH")) return "Number of Reported Hits";
    else if (tag.equals("IH")) return "Number of Stored Hits (in this file)";
    else if (tag.equals("HI")) return "Query hit-index";
    else if (tag.equals("MD")) return "CIGAR string";
    else if (tag.equals("CS")) return "Color Read Sequence";
    else if (tag.equals("CQ")) return "Color Read Quality";
    else if (tag.equals("CM")) return "# of Color Differences";
    else if (tag.equals("R2")) return "Mate Read Sequence";
    else if (tag.equals("Q2")) return "Mate Read Phred Quality";
    else if (tag.equals("S2")) return "Encoded Base Probablities for Read Mate";
    else if (tag.equals("CC")) return "Reference Name of Next Hit";
    else if (tag.equals("CP")) return "Leftmost Coordinate of Next Hit";
    else if (tag.equals("SM")) return "Mapping Quality";
    else if (tag.equals("AM")) return "Smaller Single-end Mapping Quality";
    else if (tag.equals("MF")) return "MAQ Pair Flag";
    else return "Unknown Tag";
  }
}
