package bioneos.vcmap.model.parsers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
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
public class GFF3Parser
  implements FileParser
{
//Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private HashMap<String, Chromosome> chromosomes;
  private GFF3Feature rootFeature;
  private Vector<Annotation> annotation;
  private Hashtable<String, AnnotationSet> asets;
  private HashSet<Integer> errors;
  private String errorString;
  private Vector<String> missingFeatures;
  private DisplayMap dMap;
  private File file;

  public static final int MATCHING_ID_ERROR = 6;

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.model.parsers.FileParser#parseFile(java.io.File, bioneos.vcmap.model.DisplayMap, bioneos.vcmap.gui.MainGUI)
   */
  public Vector<Annotation> parseFile(File file, DisplayMap displayMap, MainGUI mainGUI)
    throws OutOfMemoryError, SQLException
  {
    // Create root GFF3Feature
    rootFeature = new GFF3Feature();
    errors = new HashSet<Integer>();
    annotation = new Vector<Annotation>();
    asets = new Hashtable<String, AnnotationSet>();
    missingFeatures = new Vector<String>();
    this.file = file;
    this.dMap = displayMap;

    // Build chromosome HashMap
    chromosomes = new HashMap<String, Chromosome>();

    for (DisplaySegment seg : displayMap.getSegments())
      chromosomes.put(seg.getChromosome().getName(), seg.getChromosome());

    try
    {
      BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

      int lineNum = 1; // keep track of lines
      String line;

      while ((line = bufferedReader.readLine()) != null)
      {
        GFF3Feature gff3Feature;
        line = line.trim();

        mainGUI.setProgressPercentageDone(lineNum);

        // Ignore blank lines or comment lines
        if (!line.startsWith("#") && line.length() > 0)
        {
          gff3Feature = new GFF3Feature(line, lineNum);

          if (gff3Feature.isValid() && gff3Feature.isCreated())
          {
            if (gff3Feature.getParentIds() != null)
            {
              GFF3Feature child;

              // Remove feature if it exists as a top level feature
              if ((child = rootFeature.getChild(gff3Feature.getID())) != null)
              {
                rootFeature.removeChild(child);
                // Double check but child should not have been created yet
                if (!child.isCreated())
                  child.parseLine(line, lineNum); // Set data for child
                else
                {
                  logger.warn("Duplicate IDs lines " + child.getLineNumber() + " and " + lineNum);
                  errors.add(new Integer(FileParser.MATCHING_ID_ERROR));
                  lineNum++;
                  continue;
                }
              }
              else
                child = gff3Feature;

              // Add all parents to the HashMap
              for (String parentId : child.getParentIds())
              {
                GFF3Feature parentFeature = getFeature(parentId, rootFeature);

                // Parent featue has allready been created
                if (parentFeature != null)
                  parentFeature.addChild(child);
                else // create the new parent feature
                  rootFeature.addChild(new GFF3Feature(parentId, child));
              }
            }
            else if (gff3Feature.getID() != null) // No parent Features
            {
              GFF3Feature child;

              // Remove feature if it exists as a top level feature
              if ((child = rootFeature.getChild(gff3Feature.getID())) == null)
                rootFeature.addChild(gff3Feature);
              else
              {
                if (!child.isCreated())
                  child.parseLine(line, lineNum); // Set data for child
                else
                {
                  logger.warn("Matching IDs lines " + child.getLineNumber() + " and " + lineNum);
                  errors.add(new Integer(FileParser.MATCHING_ID_ERROR));
                  lineNum++;
                  continue;
                }
              }
            }
            // Add any unlinked features directly to the list of Annotation
            else
            {
              Annotation a = null;

              if (gff3Feature.isValid() && gff3Feature.isCreated())
                a = gff3Feature.toAnnotation();

              if (a != null)
                annotation.addElement(a);
            }
          }
          if (!gff3Feature.isValid() && gff3Feature.isCreated())
          {
            return null;
          }
        }
        // No data refers back so dump all features
        // into Annotation Vector
        else if (line.startsWith("###"))
        {
          addAnnotation();
        }
        // End of gff sequence
        else if (line.startsWith("##FASTA")) break;

        lineNum++; // Increment line number
      }

      // Add remaining annotation
      addAnnotation();

      return annotation;
    }
    catch (IOException e)
    {
      return null;
    }
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
   * <p>Adds all top level {@link GFF3Feature}s to the
   * {@link Annotation} {@link Vector}.</p>
   */
  private void addAnnotation()
  {
    adjustPosition(rootFeature);

    // Create annotation and clear features
    for (GFF3Feature feature : rootFeature.getChildren())
    {
      Annotation a = feature.toAnnotation();

      if (a != null)
        annotation.addElement(a);
      else
      {
        missingFeatures.addElement(feature.id);
        errors.add(FileParser.FEATURE_MISSING_ERROR);
      }

    }

    // Clear tree
    rootFeature = new GFF3Feature();
  }

  /**
   * <p>Gets the list of missing {@link GFF3Feature} IDs</p>\
   *
   * @return
   *    A {@link Vector} of the missing {@link GFF3Feature}s ID {@link String}s
   */
  public Vector<String> getMissingFeatures()
  {
    return missingFeatures;
  }

  /**
   * <p>Adjusts the position of the root {@link GFF3Feature} so that the start position
   * and the stop position are the smallest start and stop positions of all its children</p>
   *
   * @param feature
   *  the "root feature" whose position is being adjusted
   */
  private GFF3Feature adjustPosition(GFF3Feature feature)
  {
    for (GFF3Feature c : feature.getChildren())
    {
      GFF3Feature child = adjustPosition(c);

      if (child.getStart() != -1 && (feature.getStart() < 0 || child.getStart() < feature.getStart()))
        feature.setStart(child.getStart());
      if (child.getStop() != -1 && (feature.getStop() < 0 || child.getStop() > feature.getStop()))
        feature.setStop(child.getStop());
    }

    return feature;
  }

  /**
   * <p>Recursivly searches for a {@link GFF3Feature} matching
   * the {@link String} id.</p>
   * @param id
   *    The of the {@link GFF3Feature} we are searching for
   * @param feature
   *    The feature being compared to the {@link String} id
   *
   * @return
   *    The {@link GFF3Feature} if it is found otherwise null
   */
  private GFF3Feature getFeature(String id, GFF3Feature feature)
  {
    if (id.equals(feature.getID()))
      return feature;

    for (GFF3Feature child : feature.getChildren())
    {
      GFF3Feature matchedFeature = getFeature(id, child);
      if (matchedFeature != null)
        return matchedFeature;
    }

    return null;
  }

  /**
   * <p>Class to contain a line from a GFF3 file.  Used to
   * create a tree of features relating to a "root" feature.</p>
   * @author cgoodman
   * <p>Created on: August 11, 2010</p>
   *
   */
  class GFF3Feature
  {
    // GFF3 fields
    private String seqId;
    private String source;
    private String type;
    private int start;
    private int stop;
    private Double score;
    private String strand;
    private String phase;

    // Attributes
    private String id;
    private String name;
    private String[] aliasIds;
    private String[] parentIds;
    private HashMap<String, String> otherAttributes;

    // GFF3Feature variables
    private Vector<GFF3Feature> children;
    private int lineNumber;
    private AnnotationSet annotSet;
    // Boolean if errors or warnings are found
    private boolean created;
    private boolean valid;
    private boolean chrMatch;

    /**
     * <p>Constructor for root {@link GFF3Feature}</p>
     */
    public GFF3Feature()
    {
      start = -1;
      stop = -1;

      children = new Vector<GFF3Feature>();
      lineNumber = -1;

      created = false;
      valid = false;
      chrMatch = false;
    }

    /**
     * <p>Constructor for creating an empty parent {@link GFF3Feature}</p>
     *
     * @param id
     *  the id of the {@link GFF3Feature}
     * @param child
     *  the child {@link GFF3Feature} that created this Object
     */
    public GFF3Feature(String id, GFF3Feature child)
    {
      this.id = id;

      this.seqId = child.seqId;
      start = child.start;
      stop = child.stop;

      children = new Vector<GFF3Feature>();
      children.addElement(child);
      lineNumber = -1;

      created = false;
      valid = false;
      chrMatch = false;
      annotSet = null;
    }

    /**
     * <p>Creates a new {@link GFF3Feature} from a line from a GFF3 file</p>
     *
     * @param line
     *  the line that is being parsed for the {@link GFF3Feature}
     */
    public GFF3Feature(String line, int lineNum)
    {
      start = -1;
      stop = -1;

      children = new Vector<GFF3Feature>();
      parseLine(line, lineNum);
    }

    /**
     * <p>Parses a line from a GFF file and populates the values within
     * the {@link GFF3Feature}</p>
     *
     * @param line
     *  a line from the GFF file that is being parsed
     */
    public void parseLine(String line, int lineNum)
    {
      lineNumber = lineNum;
      valid = true;

      String[] values = line.trim().split("\\t");

      for (String value : values)
        value = value.trim();

      if (values.length > 7)
      {
        // Column 1 - SeqId
        if (!values[0].equals(".") && !values[0].equals("") && // Not empty
            values[0].matches("[a-zA-Z0-9\\.\\:\\^\\*\\$\\@\\!\\+\\_\\?\\-\\|\\%]+"))
        {
          // Remove spaces, capitals, and periods for cases such as " Chr.1"
          seqId = values[0].trim().toLowerCase().replaceAll("\\.", "");

          if (!seqId.toLowerCase().startsWith("chr"))
            seqId = "chr" + seqId;

          if (chromosomes.get(seqId) == null)
          {
            valid = false;
            chrMatch = false;
          }
          else
            chrMatch = true;
        }
        else
        {
          setFormatError("[ERROR] Invalid seqid on line " + lineNum);
          valid = false;
        }

        // Column 2 - Source
        if (values[1].matches("[a-zA-Z0-9\\.\\: \\^\\*\\$\\@\\!\\+\\_\\?\\-\\%]+"))
          source = values[1].trim();
        else
        {
          setFormatError("[ERROR] Invalid source on line " + lineNum);
          valid = false;
        }

        // Column 3 - Type
        if (!values[2].equals(".") && !values[2].equals("") && // Not empty
            values[2].matches("[a-zA-Z0-9\\.\\: \\^\\*\\$\\@\\!\\+\\_\\?\\-]+"))
        {
          type = values[2].toUpperCase();
          try
          {
            // Create a new annotation set for this type of feature, if we haven't already
            annotSet = GFF3Parser.this.asets.get(type);
            if (annotSet == null)
            {
              annotSet = Factory.getCustomAnnotationSet(type, dMap.getMap(), file, "GFF3 file");
              GFF3Parser.this.asets.put(type, annotSet);
            }
          }
          catch (SQLException e)
          {
            logger.error("Error getting the Custom AnnotationSet for this file: " + e);
          }
        }
        else
        {
          setFormatError("[ERROR] Invalid type on line " + lineNum);
          valid = false;
        }

        // Column 4 - Start
        if (!values[3].equals(".") && !values[3].equals(""))
        {
          try
          {
            int startPos = Integer.parseInt(values[3]);

            if (start < 0 || start > startPos)
              start = startPos;
          }
          catch (NumberFormatException e)
          {
            start = -1;
            valid = false;
          }
        }
        else
        {
          start = -1;
          valid = false;
        }

        // Column 5 - Stop
        if (!values[4].equals(".") && !values[4].equals(""))
        {
          try
          {
            int stopPos = Integer.parseInt(values[4]);

            if (stop < 0 || stopPos > stop)
              stop = stopPos;
          }
          catch (NumberFormatException e)
          {
            stop = -1;
            valid = false;
          }
        }
        else
        {
          stop = -1;
          valid = false;
        }

        // Ensure correct positioning
        if (start <= 0 || start > stop)
        {
          setFormatError("[ERROR] Invalid start/stop positon on line " + lineNum);
          valid = false;
        }

        // Column 6 - Score
        if (!values[5].equals(".") && !values[5].equals(""))
        {
          try
          {
            score = new Double(values[5]);
          }
          catch (NumberFormatException e)
          {
            setFormatError("[WARNING] Invalid score on line " + lineNum);
            score = null;
          }
        }
        else
          score = null;

        // Column 7 - Strand
        if (!values[6].equals(".") && !values[6].equals(""))
        {
          if (values[6].equals("+") || values[6].equals("-") || values[6].equals("?"))
          {
            strand = values[6];
          }
          else
          {
            setFormatError("[WARNING] Invalid strand on line " + lineNum);
            strand = null;
          }
        }
        else
          strand = null;

        // Column 8 - Phase
        if (!values[7].equals(".") && !values[7].equals(""))
        {
          if (values[7].equals("0") || values[7].equals("1") || values[7].equals("2"))
          {
            phase = values[7];
          }
          else
          {
            setFormatError("[WARNING] Invalid phase on line " + lineNum);
            phase = null;
          }
        }
        else
          phase = null;

        // Column 9 - Attributes
        if (values.length > 8)
        {
          otherAttributes = new HashMap<String, String>();

          if (!values[8].equals(".") && !values[8].equals(""))
          {
            for (String attribute : values[8].split("\\;"))
            {
              String[] tagValue = attribute.split("\\=");

              if (tagValue.length != 2) // Invalid tags
              {
                setFormatError("[WARNING] Invalid attributes on line " + lineNum);
                continue;
              }

              String tag = tagValue[0].trim();
              String value = tagValue[1].trim();

              if (tag.toLowerCase().equals("id")) id = value;
              else if (tag.toLowerCase().equals("name")) name = value;
              else if (tag.toLowerCase().equals("alias")) aliasIds = value.split("\\,");
              else if (tag.toLowerCase().equals("parent")) parentIds = value.split("\\,");
              else
              {
                if (otherAttributes == null)
                  otherAttributes = new HashMap<String, String>();

                // Put all values in attribute HashMap
                otherAttributes.put(tag, value);
              }
            }
          }
        }
      }
      else // Incorrect number of columns
      {
        setFormatError("[ERROR] Invalid number of columns on line " + lineNum);
        valid = false;
      }

      created = true;
    }

    /**
     * <p>Adds a {@link GFF3Feature} to this {@link GFF3Feature}'s
     *  list of children features.</p>
     *
     * @param child
     *  the child feature that is being added
     */
    public void addChild(GFF3Feature child)
    {
      children.addElement(child);
    }

    /**
     * <p>Method to remove child from the {@link GFF3Feature}
     * should only be used by the rootFeature</p>
     *
     * @param child
     *  the child {@link GFF3Feature} being removed
     */
    public void removeChild(GFF3Feature child)
    {
      children.removeElement(child);
    }

    /**
     * <p>Checks for a matching child {@link GFF3Feature}.  This method should
     * only be called by the "root" of the {@link GFF3Feature} tree.</p>
     *
     * @param id
     *  the ID of the {@link GFF3Feature} being searched for
     * @return
     *  the feature matching the ID otherwise null
     */
    public GFF3Feature getChild(String id)
    {
      for (GFF3Feature child : children)
        if (child.getID().equals(id))
          return child;

      return null;
    }

    /**
     * <p>All children {@link GFF3Feature}s</p>
     *
     * @return
     *  {@link Vector} of all children {@link GFF3Feature}s
     */
    public Vector<GFF3Feature> getChildren()
    {
      return children;
    }

    /**
     * <p>Converts the {@link GFF3Feature} to an {@link Annotation} object.</p>
     *
     * @return
     *  null if the {@link GFF3Feature} is not valid otherwise an
     *  {@link Annotation} object from the {@link GFF3Feature}s data.
     */
    public Annotation toAnnotation()
    {
      if (!valid || !created || !chromosomes.containsKey(seqId))
        return null;

      Annotation annotation = new Annotation(chromosomes.get(seqId));

      if (name != null)
        annotation.addName(name);
      if (aliasIds != null)
        for (String alias : aliasIds)
          annotation.addName(alias);

      // No name found yet
      if (annotation.getName() == null)
      {
        if (id != null)
          annotation.addName(id);

        if (otherAttributes != null && otherAttributes.size() > 0)
        {
          // Try to add unique identifier as name if no name is found yet
          if (annotation.getName() == null)
          {

            for (String key : otherAttributes.keySet())
            {
              if (key.toLowerCase().contains("id") ||
                  key.toLowerCase().contains("tag") ||
                  key.toLowerCase().contains("name"))
              {
                annotation.addName(otherAttributes.get(key));
                break;
              }
            }
          }
        }
      }

      // Last resort - use position data
      if (annotation.getNames() == null)
        if (source != null)
          annotation.addName(source + ", " + seqId + ": " + start + "-" + stop);
        else
          annotation.addName(seqId + ": " + start + "-" + stop);

      // Annotation is invalid if there is no type
      if (annotSet == null)
        return null;
      else
        annotation.setAnnotationSet(annotSet);

      //adjustPosition(this);

      annotation.setStart(start);
      annotation.setStop(stop);

      if (score != null)
        annotation.addInfo("Score", String.valueOf(score));

      if (strand != null)
        annotation.addInfo("Strand", strand);

      if (phase != null)
        annotation.addInfo("Phase", String.valueOf(phase));

      if (otherAttributes != null)
        for (String key : otherAttributes.keySet())
        annotation.addInfo(key, otherAttributes.get(key));

      return annotation;
    }

    /**
     * <p>Adds error type to errors vector, sets the error text and
     * marks the {@link GFF3Feature} as invalid</p>
     */
    public void setFormatError(String error)
    {
      errorString = error;
      errors.add(new Integer(FileParser.FILE_FORMAT_ERROR));
      valid = false;
    }

    /**
     * <p>Return whether the {@link GFF3Feature} has been created or not</p>
     *
     * @return
     *  true if the {@link GFF3Feature} has been created from a GFF3
     *  line, false if it was created as a placeholder "parent" {@link GFF3Feature}
     */
    public boolean isCreated()
    {
      return created;
    }

    /**
     * <p>Return if the {@link GFF3Feature} is valid. Can't be valid if the
     * feature hasn't been created</p>
     *
     * @return
     *  true if all data found in the feature is valid and it has been created
     *  false otherwise
     */
    public boolean isValid()
    {
      return valid;
    }

    /**
     * <p>Return whether a chromosome has been matched to the {@link GFF3Feature}</p>
     *
     * @return
     *  true if a chromosome has been matched to this {@link GFF3Feature}, otherwise false
     */
    public boolean hasMatch()
    {
      return chrMatch;
    }

    /**
     * <p>Return the GFF3 feature id used for linking it to other
     * GFF3 features</p>
     *
     * @return
     *  the GFF3 id of the feature in the form of a {@link String}
     */
    public String getID()
    {
      return id;
    }

    /**
     * <p>Return the list of {@link GFF3Feature}s parent IDs</p>
     *
     * @return
     *  an array of the features
     */
    public String[] getParentIds()
    {
      return parentIds;
    }

    /**
     * <p>Sets the start positon of the {@link GFF3Feature}</p>
     *
     * @param start
     *  the new starting position of the {@link GFF3Feature}
     */
    public void setStart(int start)
    {
      this.start = start;
    }

    /**
     * <p>The starting position of this {@link GFF3Feature}</p>
     *
     * @return
     *  the beginning position of this feature
     *
     */
    public int getStart()
    {
      return start;
    }

    /**
     * <p>The stop position of this {@link GFF3Feature}</p>
     *
     * @param stop
     *  the new stop position of the {@link GFF3Feature}
     */
    public void setStop(int stop)
    {
      this.stop = stop;
    }

    /**
     * <p>The ending position of this {@link GFF3Feature}</p>
     *
     * @return
     *  the ending position of this feature
     */
    public int getStop()
    {
      return stop;
    }

    /**
     * <p>Gets the number of the line that this {@link GFF3Feature} was created from</p>
     *
     * @return
     *  the int line number of this {@link GFF3Feature}
     */
    public int getLineNumber()
    {
      return lineNumber;
    }
  }

}
