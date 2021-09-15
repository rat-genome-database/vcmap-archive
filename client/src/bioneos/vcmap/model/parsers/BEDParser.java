package bioneos.vcmap.model.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Vector;

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
public class BEDParser
  implements FileParser
{
//Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private HashSet<Integer> errors;
  private String errorString;

  /**
   * <p>Constructor initializes class variables</p>
   */
  public BEDParser()
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
    //Define our helper variables
    HashMap<String, Chromosome> chromosomes = new HashMap<String, Chromosome>();
    Vector<Annotation> annotation = new Vector<Annotation>();
    HashMap<String, String> extraInfo = new HashMap<String, String>();

    AnnotationSet
    set = Factory.getCustomAnnotationSet("BED" + SUFFIX, displayMap.getMap(), file, "BED file");

    // Build chromosome HashMap
    for (DisplaySegment seg : displayMap.getSegments())
      chromosomes.put(seg.getChromosome().getName(), seg.getChromosome());

    displayMap.getMap().getAllAnnotationSets();

    try
    {
      BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

      int lineNum = 1; // keep track of lines
      String line;

      while ((line = bufferedReader.readLine()) != null)
      {
        if (line.startsWith("track"))//TODO not sure what to do with this
          //type = parseTrackLine(line, extraInfo);

        mainGUI.setProgressPercentageDone(lineNum);
        line = line.trim();

        // Ignore comment lines
        if (!line.startsWith("#"))
        {
          String[] values = line.trim().split("\\t");
          Annotation annot;

          // Find choromsome
          Chromosome chr = chromosomes.get(values[0]);
          if (chr != null)
            annot = new Annotation(chr);
          else
            continue;

          // Determine start/stop
          int start;
          int stop;

          try
          {
            if (!values[1].equals(".") && !values[1].equals("") ||
                !values[2].equals(".") && !values[2].equals(""))
            {
              start = Integer.parseInt(values[1]);
              stop = Integer.parseInt(values[2]);
            }
            else // Start and stop columns can't be empty
            {
              errorString = "[ERROR] Invalid start/stop on line " + lineNum;
              errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
              return null;
            }

            if (start > 0 && start < stop)
            {
              annot.setStart(start);
              annot.setStop(stop);
            }
            else
            {
              errorString = "[ERROR] Invalid start/stop on line " + lineNum;
              errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
              return null;
            }
          }
          catch (NumberFormatException e)
          {
            errorString = "[ERROR] Invalid start/stop on line " + lineNum;
            errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
            return null;
          }

          // Find name
          if (values[3] != null && !values[3].equals(".") && !values[3].equals(""))
            annot.addName(values[3]);
          else
            annot.addName(chr.getName() + ":" + start + "-" + stop);

          annot.setAnnotationSet(set);

          // Strand
          if (!values[5].equals(".") && !values[5].equals("") &&
              (values[5].equals("+") || values[5].equals("-") || values[5].equals("?")))
            annot.addInfo("Strand", values[5]);

          // Blocks (Exons)
          try
          {
            if (values[9] != null && !values[9].equals(".") && !values[9].equals(""))
            {
              int blockCount = Integer.parseInt(values[9]);
              if (blockCount > 0)
              {
                annot.addInfo("Exons", String.valueOf(blockCount));

                if (values[10] != null && !values[10].equals(".") && !values[10].equals(""))
                  annot.addInfo("Exon Lenght(s)", values[10]);

                if (values[11] != null && !values[11].equals(".") && !values[11].equals(""))
                  annot.addInfo("Exon Starting Positions", values[11]);
              }
            }
          }
          catch (NumberFormatException e)
          {
            errorString = "[ERROR] Block count not a number on line" + lineNum;
            errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
            return null;
          }

          // Add the extraInfo from the track info to every piece of anotation
          annot.addInfo(extraInfo);

          annotation.add(annot);
        }

        lineNum++; // Increment line number
      }

      return annotation;
    }
    catch (IOException e)
    {
      return null;
    }
  }

  /**
   * <p>Grabs data from a track line of the BED file</p>
   */
  private String parseTrackLine(String line, HashMap<String, String> extraInfo)
  {
    String type = "BED" + SUFFIX;
    Scanner scanner = new Scanner(line);
    scanner.useDelimiter(" ");
    boolean typeSet = false;

    String column = scanner.next(); // Remove track tag
    while (scanner.hasNext())
    {
      // Append more to column if it contains a quotation mark
      if ((column.length() - column.replaceAll("\"", "").length()) % 2 != 0)
        column += " " + scanner.next();
      else
      {
        String[] keyValue = column.split("=");
        if (keyValue.length == 2)
        {
          String key = keyValue[0];
          String value = keyValue[1].replaceAll("\"", "");

          // Type tag is not specified in track line documentation
          // but is supported in case one wants to specify the type
          if (key.toLowerCase().equals("type"))
          {
            type = value + SUFFIX;
            typeSet = true;
          }
          // Otherwise the name of the track is used
          else if (!typeSet && key.toLowerCase().equals("name"))
            type = value + SUFFIX;
          // if the tag has nothing to do with the name add it to the extraInfo HashMap
          else
            extraInfo.put(key, value);
        }
        column = scanner.next();
      }
    }

    return type;
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
}
