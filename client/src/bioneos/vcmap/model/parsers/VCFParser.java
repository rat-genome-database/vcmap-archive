package bioneos.vcmap.model.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>Provides a method to parse {@link Annotation}s for a {@link DisplayMap} in a
 * VCF Format.</p>
 *
 * @author ddellspe
 * <p>Created on July 29, 2011</p>
 *
 */
public class VCFParser
  implements FileParser
{
//Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private HashSet<Integer> errors;
  private String errorString;


  HashMap<String, Chromosome> chromosomes;
  Vector<Annotation> annotation;
  HashMap<String, String> infoStrings;
  HashMap<String, String> filterStrings;
  HashMap<String, String> altStrings;
  Vector<String> flags;

  /**
   * <p>Constructor initializes class variables</p>
   */
  public VCFParser()
  {
    errors = new HashSet<Integer>();
    flags = new Vector<String>();
    altStrings = new HashMap<String, String>();
    filterStrings = new HashMap<String, String>();
    infoStrings = new HashMap<String, String>();
    annotation = new Vector<Annotation>();
    chromosomes = new HashMap<String, Chromosome>();
  }

  public String getErrorString()
  {
    return errorString;
  }

  public boolean hasError(int errorCode)
  {
    return errors.contains(new Integer(errorCode));
  }

  public Vector<Annotation> parseFile(File file, DisplayMap displayMap, MainGUI mainGUI)
      throws SQLException, OutOfMemoryError
  {
    //Define our helper variables
    Pattern infoPattern = Pattern.compile("##INFO=<ID=(.+),Number=(-?[\\d|\\.]+),Type=(.+),Description=\"(.+)\">\\s?");
    Pattern filterPattern = Pattern.compile("##FILTER=<ID=(.+),Description=\"(.+)\">\\s?");
    Pattern altPattern = Pattern.compile("##ALT=<ID=(.+),Description=\"(.+)\">\\s?");
    //Pattern formatPattern = Pattern.compile("##FORMAT=<ID=(\\w+),Number=(-?[\\d||\\.]+),Type=(\\w+),Description=\"([\\w|\\s]+)\">\\s?");

    AnnotationSet set = Factory.getCustomAnnotationSet("VCF " + SUFFIX, displayMap.getMap(), file, "VCF file");

    // Build chromosome HashMap
    for (DisplaySegment seg : displayMap.getSegments())
      chromosomes.put(seg.getChromosome().getName(), seg.getChromosome());

    int lineNum = 0; //keep track of lines
    String line = "";
    boolean properFormat = false;
    Vector<String> warnings = new Vector<String>();
    try
    {
      BufferedReader bufferedReader;
      bufferedReader = new BufferedReader(new FileReader(file));
      while (bufferedReader.ready())
      {
        line = bufferedReader.readLine();
        if (line.matches("##fileformat=VCFv4.\\d+"))
        {
          properFormat = true;
          break;
        }
        else if (line.matches("##fileformat=VCFv\\d+\\.\\d+"))
        {
          errorString = "This file is formatted with an unsupported VCF format: " + line;
          errors.add(FileParser.INVALID_FORMAT_ERROR);
          logger.error(errorString);
          return null; 
        }

      }
      if (!properFormat)
      {
        errorString = "VCF file is not a valid VCF v 4.x formatted file.";
        errors.add(FileParser.INVALID_FORMAT_ERROR);
        logger.error(errorString);
        return null;
      }
      bufferedReader = new BufferedReader(new FileReader(file));
      while(bufferedReader.ready())
      {
        lineNum++;
        mainGUI.setProgressPercentageDone(lineNum);
        line = bufferedReader.readLine();
        if (line.startsWith("##"))
        {
          Matcher infoMatcher = infoPattern.matcher(line);
          Matcher filterMatcher = filterPattern.matcher(line);
          Matcher altMatcher = altPattern.matcher(line);
          //Matcher formatMatcher = formatPattern.matcher(line);
          if (infoMatcher.matches())
          {
            infoStrings.put(infoMatcher.group(1), infoMatcher.group(4));
            if (infoMatcher.group(3).equals("Flag"))
              flags.add(infoMatcher.group(1));
          }
          if (filterMatcher.matches())
          {
            filterStrings.put(filterMatcher.group(1), filterMatcher.group(2));
          }
          if (altMatcher.matches())
          {
            altStrings.put(altMatcher.group(1), altMatcher.group(2));
          }
        }
        else if (!line.startsWith("#") && line.length() > 0)
        {
          String[] splits = line.split("\t");

          // Attempt minor modifications to chromosome name if needed
          String chrName = splits[0].toLowerCase();
          if (chrName.startsWith("chr-") || chrName.startsWith("chr."))
            chrName = "chr" + chrName.substring(4);
          else if (!chrName.startsWith("chr"))
            chrName = "chr" + chrName;
          Chromosome chr = chromosomes.get(chrName);

          Annotation a = new Annotation(chr, -1, null, set.getSourceId(), splits[2]);
          a.setPosition(Integer.parseInt(splits[1]));
          a.setAnnotationSet(set);
          
          // Naming
          if(splits[4].contains("<") && splits[4].contains(">"))
          {
            a.addName(getNameStringAltBracket(splits[2], splits[3], splits[4], a.getStart(), a));
          }
          else
          {
            a.addName(getNameStringStandard(splits[2], splits[3], splits[4], a.getStart(), a));
          }
          a.addInfo("Reference Sequence", splits[3]);
          a.addInfo("Alternate Sequence(s)", splits[4]);
          a.addInfo("Quality Score", splits[5]);
          String filter = splits[6];
          if (filter.equals("PASS"))
            a.addInfo("Filter Information", "Passed All Filters");
          else
          {
            String[] filterSplits = filter.split(";");
            filter = "";
            for(String filt : filterSplits)
              filter+=filterStrings.get(filt) + ", ";
            a.addInfo("Filter Information", filter.substring(0, filter.length()-2));
          }
          if(!splits[7].equals("."))
          {
            String info = splits[7];
            String[] infoSplits = info.split(";");
            for (String inf : infoSplits)
            {
              if(flags.contains(inf))
              {
                a.addInfo(infoStrings.get(inf), "");
              }
              else
              {
                String[] infSplit = inf.split("=");
                if (infoStrings.get(infSplit[0]) == null)
                {
                  // Record this error, but still load data
                  String warning = "Missing INFO data for: '" + infSplit[0] + "'\n" + 
                      "Check the file to ensure that the INFO header exists for " + infSplit[0];
                  if (!warnings.contains(warning)) logger.warn(warning);
                  warnings.add(warning);
                }
                else if (infSplit[0].equals("SVTYPE"))
                {
                  infSplit[1] = altStrings.get(infSplit[1]);
                }
                else
                {
                  a.addInfo(infoStrings.get(infSplit[0]),infSplit[1]);
                }
              }
            }
          }
          a.addInfo("File and Position", (file.getAbsolutePath() + ":" + lineNum));
          annotation.add(a);
        }
        else
          continue;
      }
    }
    catch (IOException e)
    {
      e.printStackTrace();
      return null;
    }
    catch (Exception e)
    {
      e.printStackTrace();
      return null;
    }
    return annotation;
  }
  private String getNameStringStandard(String nameCol, String refCol, String altCol, int pos, Annotation a)
  {
    if (refCol.length() > 10)
      refCol = shortenName(refCol);
    if (altCol.length() > 10)
      altCol = shortenName(altCol);
    if(nameCol.equals("."))
    {
      if(refCol.equals(".")) //insertion
        return "g." + pos + "_" + (pos + 1) + "ins" + altCol;
      else if (altCol.equals(".")) //deletion
        return "g." + pos + "del" + refCol;
      else
        return "g." + pos + "" + refCol + ">" + altCol;
    }
    else
    {
      if(refCol.equals(".")) //insertion
        return "g." + nameCol + "ins" + altCol;
      else if (altCol.equals(".")) //deletion
        return "g." + nameCol + "del" + refCol;
      else
        return "g." + nameCol + "" + refCol + ">" + altCol;
    }
  }
  private String getNameStringAltBracket(String nameCol, String refCol, String altCol, int pos, Annotation a)
  {
    if (refCol.length() > 10)
      refCol = shortenName(refCol);
    String altColInfo = "";
    Matcher match = Pattern.compile("<([\\w|:]+)>").matcher(altCol);
    if (match.matches())
      altColInfo = match.group(1);
    if(nameCol.equals("."))
    {
      if(altCol.contains("INS")) //insertion
      {
        a.addInfo("Insertion Information", altStrings.get(altColInfo));
        return "g." + pos + "_" + (pos + 1) + "ins";
      }
      else if (altCol.contains("DEL")) //deletion
      {
        a.addInfo("Deletion Information", altStrings.get(altColInfo));
        return "g." + pos + "del";
      }
      else if (altCol.contains("DUP"))
      {
        a.addInfo("Duplication Information", altStrings.get(altColInfo));
        return "g." + pos + "dup";
      }
      else if (altCol.contains("INV"))
      {
        a.addInfo("Inversion Information", altStrings.get(altColInfo));
        return "g." + pos + "inv";
      }
      else if (altCol.contains("CNV"))
      {
        a.addInfo("Copy Number Variable Information", altStrings.get(altColInfo));
        return "g." + pos + "cnv";
      }
      else
        return "g." + pos + "" + refCol + ">" + altCol;
    }
    else
    {
      if(altCol.contains("INS")) //insertion
      {
        a.addInfo("Insertion Information", altStrings.get(altColInfo));
        return "g." + nameCol + "ins";
      }
      else if (altCol.contains("DEL")) //deletion
      {
        a.addInfo("Deletion Information", altStrings.get(altColInfo));
        return "g." + nameCol + "del";
      }
      else if (altCol.contains("DUP"))
      {
        a.addInfo("Duplication Information", altStrings.get(altColInfo));
        return "g." + nameCol + "dup";
      }
      else if (altCol.contains("INV"))
      {
        a.addInfo("Inversion Information", altStrings.get(altColInfo));
        return "g." + nameCol + "inv";
      }
      else if (altCol.contains("CNV"))
      {
        a.addInfo("Copy Number Variable Information", altStrings.get(altColInfo));
        return "g." + nameCol + "cnv";
      }
      else
        return "g." + nameCol + "" + refCol + ">" + altCol;
    }
  }
  private String shortenName(String name)
  {
    String start = name.substring(0, 3);
    String end = name.substring(name.length() - 4);
    return start + "..." + end;
  }
}
