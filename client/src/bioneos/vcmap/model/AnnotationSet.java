package bioneos.vcmap.model;

import java.io.File;

/**
 * <p>AnnotationSet Class which contains the information required to link annotations
 * to a type, {@link MapData} and {@link Version}.  </p>
 * @author ddellspe@bioneos.com
 * @author sgdavis@bioneos.com
 */
public class AnnotationSet
{
  // Member variables
  private int id;
  private int mapId;
  private MapData map;
  private String type;
  private int sourceId;
  private String source;
  private String version;
  private boolean custom;
  private File customFile;
  private String releaseDate;

  /**
   * <p>Constructor for {@link AnnotationSet} which is to be used to make a reference to the
   * {@link AnnotationSet} when the reference is needed without all of the information known.<p>
   */
  public AnnotationSet()
  {
    this(-1, -1, null, null, "");
  }

  /**
   * <p>Constructor for {@link AnnotationSet} which is used when the {@link MapData}, and {@link Version}
   * have already been loaded into the program.</p>
   *
   * @param id
   *    Integer representing the id that is found in the database
   * @param map
   *    {@link MapData} that is the map for this {@link AnnotationSet}
   * @param type
   *    String that holds the type for this annotation
   * @param version
   *    {@link Version}
   */
  public AnnotationSet(int id, int mapId, String type, String version, String releaseDate)
  {
    this.id = id;
    this.setMapId(mapId);
    this.map = null;
    this.type = type;
    this.version = version;
    this.releaseDate = releaseDate;
    this.custom = false;
    this.customFile = null;
  }

  /**
   * <p>Returns the ID of the {@link AnnotationSet} as found in the Database</p>
   *
   * @return
   *    The ID found in the Database for this {@link AnnotationSet}
   */
  public int getId()
  {
    return id;
  }

  /**
   * <p>Returns the {@link MapData} for this {@link AnnotationSet}</p>
   *
   * @return
   *    {@link MapData} object
   */
  public MapData getMap()
  {
    return map;
  }

  /**
   * <p>Returns the type of the {@link AnnotationSet}</p>
   *
   * @return
   *    The String containing the type
   */
  public String getType()
  {
    return (type == null) ? "" : type;
  }

  /**
   * <p>Returns the human readable version of the {@link AnnotationSet}
   * @return
   *    {@link Version} of the {@link AnnotationSet}
   */
  public String getVersion()
  {
    return (version == null) ? "" : version;
  }

  /**
   * <p>Set the ID of the {@link AnnotationSet} to the id specified</p>
   *
   * @param id
   *    Integer that represents the ID of the {@link AnnotationSet} in the Database
   */
  public void setId(int id)
  {
    this.id = id;
  }

  /**
   * <p>Sets the {@link MapData} for the {@link AnnotationSet} so that this {@link AnnotationSet}
   * may give the proper links to the maps from the {@link Annotation}s.</p>
   *
   * @param map
   *    {@link DisplayMap} that represents the map for this {@link AnnotationSet}
   */
  public void setMap(MapData map)
  {
    this.map = map;
  }

  /**
   * <p>Sets the type of the annotation, examples (GENE, STS, PSEUDO, QTL)</p>
   *
   * @param type
   *    String of the type for the {@link AnnotationSet}
   */
  public void setType(String type)
  {
    this.type = type;
  }

  /**
   * <p>Set the human readable name for the version of this {@link AnnotationSet}</p>
   * @param version
   *    {@link Version} object to be set
   */
  public void setVersion(String version)
  {
    this.version = version;
  }

  /**
   * Set the internal database identifier used to identify the map this 
   * AnnotationSet is associated with.
   * @param mapId
   */
  public void setMapId(int mapId)
  {
    this.mapId = mapId;
  }

  /**
   * Get the internal database identifier used to identify the map this 
   * AnnotationSet is associated with.
   * @param mapId
   */
  public int getMapId()
  {
    return mapId;
  }

  /**
   * Designate this AnnotationSet as a custom dataset from specified file.
   * @param custom
   *   True if this is a custom dataset, false otherwise.
   * @param customFile
   *   The data file from which this dataset was loaded, can be null when not
   *   applicable.
   */
  public void setCustom(boolean custom, File customFile)
  {
    this.id = -1;
    this.sourceId = -1;
    this.custom = custom;
    this.customFile = customFile;
  }

  /**
   * Check if this is a custom dataset, or loaded from the VCMap database.
   * @return
   *   True if it was loaded from a file, false if loaded from the database.
   */
  public boolean isCustom()
  {
    return custom;
  }

  /**
   * Get the file handle for the source of this custom AnnotationSet data.
   * @return
   */
  public File getCustomFile()
  {
    return customFile;
  }

  public void setSourceId(int sourceId)
  {
    this.sourceId = sourceId;
  }

  public int getSourceId()
  {
    return sourceId;
  }

  public void setSource(String source)
  {
    this.source = source;
  }

  public String getSource()
  {
    return source;
  }
  
  public String getReleaseDate()
  {
    return releaseDate;
  }

  public String toString()
  {
    return source + " (" + map.getSpecies() + ":" + map.getTypeString() + " - " + version + "): " + type + " (" + releaseDate + ")";
  }
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    if (o == null)
      return false;
    if (o instanceof AnnotationSet)
    {
      AnnotationSet annot = (AnnotationSet) o;

      // Assume custom annotation sets are equal when created from same file,
      // and their types are the same
      if (custom)
      {
        if (!annot.isCustom())
          return false;
        else if (annot.getCustomFile().equals(customFile) && annot.getType().equals(type))
          return true;
      }

      // Otherwise compare as normal
      if (annot.getId() == id && annot.getMapId() == mapId && annot.getType().equals(type)
          && annot.getVersion().equals(version) && annot.getReleaseDate().equals(releaseDate))
        return true;
    }
    return false;
  }
}
