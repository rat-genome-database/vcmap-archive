package bioneos.vcmap.model;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

/**
 * <p>This class keeps track of the chromosomes, species, source, and other
 * data for a specfic map of genetic information.</p>
 *
 * <p>Created on: July 11, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class MapData
{
  // Constants
  public static final int UNKNOWN = -1;

  // Unit Constants
  public static final int BP_UNITS = 0;
  public static final int CM_UNITS = 1;
  public static final int CR_UNITS = 2;

  // Type Constants
  public static final int GENOMIC = 3;
  public static final int GENETIC = 4;
  public static final int RADIATION_HYBRID = 5;

  // Variables
  private int id;
  private int sourceId;
  private String source;
  private String species;
  private int taxId;
  private String version;
  private int units;
  private int type;
  private int scale;
  private String name;

  private Vector<Chromosome> chromosomes;
  private Hashtable<String, Vector<SyntenyBlock>> synteny;
  private Vector<AnnotationSet> availableAnnotationSets;
  private AnnotationSet defaultAnnotationSet;

  /**
   * <p>Default Constructor. Creates a map without a database id.</p>
   */
  public MapData()
  {
    this(-1);
  }

  /**
   * <p>Constructor. Creates a map with a database id.</p>
   *
   * @param dbId
   */
  public MapData(int dbId)
  {
    id = dbId;
    chromosomes = new Vector<Chromosome>();
    synteny = new Hashtable<String, Vector<SyntenyBlock>>();
    sourceId = -1;
    units = -1;
    type = -1;
    scale = 1;
    availableAnnotationSets = new Vector<AnnotationSet>();
  }

  /**
   * <p>Returns the map name in the form:
   * SPECIES - TYPE, VERSION</p>
   *
   * @return
   *   Map name as a {@link String}
   */
  public String getName()
  {
    if (this.name == null)
    {
      StringBuilder name = new StringBuilder();
      name.append(species);
      name.append(" - ");
      name.append(getTypeString());
      name.append(", ");
      name.append(version);
      this.name = name.toString();
    }

    return this.name;
  }

  /**
   * <p>Get the species of the {@link MapData}.</p>
   *
   * @return
   *   {@link String} of the species
   */
  public String getSpecies()
  {
    return species;
  }

  /**
   * <p>Get the units of the {@link MapData}.</p>
   *
   * @return
   *   int value of the units
   */
  public int getUnits()
  {
    return units;
  }

  /**
   * <p>Get the source of the {@link MapData}.</p>
   *
   * @return
   *   {@link String} of the source
   */
  public String getSource()
  {
    return source;
  }

  /**
   * <p>Get the version of the {@link MapData}.</p>
   *
   * @return
   *   A human readable {@link String} of the version of this map.
   */
  public String getVersion()
  {
    return (version == null) ? "" : version;
  }

  /**
   * <p>Get the type of the {@link MapData}.</p>
   *
   * @return
   *   int value of the type
   */
  public int getType()
  {
    return type;
  }

  /**
   * <p>Get the taxonomy ID of the {@link MapData}.</p>
   *
   * @return
   *   int value of the taxonomy ID
   */
  public int getTaxID()
  {
    return taxId;
  }

  /**
   * <p>Get the default {@link AnnotationSet} of the {@link MapData}/</p>
   *
   * @return
   *    {@link AnnotationSet} of the default {@link AnnotationSet}
   */
  public AnnotationSet getDefaultAnnotationSet()
  {
    return defaultAnnotationSet;
  }

  /**
   * <p>Returns all {@link AnnotationSet}s for the map</p>
   *
   * @return
   *    Vector of {@link AnnotationSet}s that correspond to the map
   */
  public Vector<AnnotationSet> getAllAnnotationSets()
  {
    return availableAnnotationSets;
  }
  
  /**
   * Query the Chromosomes of this map to determine what AnnotationSets
   * have been loaded.
   * @return
   *   A Vector of the loaded AnnotationSets, never null
   */
  public Vector<AnnotationSet> getLoadedAnnotationSets()
  {
    Vector<AnnotationSet> asets = new Vector<AnnotationSet>();
    for (Chromosome c : chromosomes)
      for (AnnotationSet a : c.getAnnotationSets())
        if (!asets.contains(a))
          asets.add(a);
    return asets;
  }

  /**
   * <p>Set the scale value for this map.</p>
   *
   * @param scale
   *   The scale value as explained in the getScale() documentation.
   */
  public void setScale(int scale)
  {
    this.scale = scale;
  }

  /**
   * <p>Get the scale value for this map.</p>
   *
   * @return
   *   Positive integer value of 1 or else divisible by 10.  A scale of 10
   *   would mean that all position integers returned by any children of this
   *   {@link MapData}, including {@link Chromosome}s and {@link Annotation}s
   *   will actually represent 10x the actual value.  100 would mean 100x. A
   *   scale of 1 means that all position information is unscaled.
   */
  public int getScale()
  {
    return scale;
  }

  /**
   * Returns a {@link String} representation of the specified map type.
   *
   * @param type
   *   The int value representing a map type .
   * @return
   *   The English readable version for the input map type.
   */
  public String getTypeString()
  {
    if (type == GENOMIC)
      return "Genomic";
    else if (type == GENETIC)
      return "Genetic";
    else if (type == RADIATION_HYBRID)
      return "Radiation Hybrid";
    return "Unknown";
  }

  /**
   * <p>Returns chromosome based on chromosome name passed in. If the requested
   * chromosome isn't loaded in memory yet, it is created from the backend.</p>
   *
   * @param chromosomeName
   *   Name of chromosome in format: 'chr[\dXY]', 'MT' for mitochrodrial, or
   *   'unmapped' for all the unmapped data.
   * @return
   *   {@link Chromosome} reference to chromosome, or null if no
   *   {@link Chromosome} exists by that name in the database.
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public Chromosome getChromosome(String chromosomeName)
    throws SQLException
  {
    // If chromosome is already loaded, return chromosome
    for (Chromosome chr : chromosomes)
      if (chromosomeName.compareTo(chr.getName()) == 0)
        return chr;

    // Otherwise try to grab a new chromosome from the factory
    Chromosome newChr = Factory.getChromosome(this, chromosomeName);
    if (newChr != null) chromosomes.add(newChr);

    return newChr;
  }

  /**
   * <p>Get the synteny for this {@link MapData} and the {@link Chromosome}
   * of another species.</p>
   *
   * @param otherSpecies
   *   {@link Chromosome} of another species
   * @return
   *   {@link Vector} of {@link SyntenyBlock} representing the synteny
   * @throws SQLExcepetion
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public Vector<SyntenyBlock> getSynteny(Chromosome otherSpecies)
    throws SQLException
  {
    if (otherSpecies.getMap().getSpecies().equals(getSpecies()))
      throw new IllegalArgumentException("You cannot get Synteny from the same species");

    // Check if we are already loaded
    String key = otherSpecies.getMap().getSpecies() + ";" + otherSpecies.getName();
    if (synteny.get(key) != null) return synteny.get(key);

    // Otherwise load from the Factory
    Vector<SyntenyBlock> syn = new Vector<SyntenyBlock>();
    try
    {
      Factory.getSynteny(syn, otherSpecies, this);
    }
    catch (IllegalArgumentException e)
    {
      synteny.put(key, syn);

      throw e;
    }

    synteny.put(key, syn);

    return syn;
  }

  /**
   * <p>Delete the synteny for this {@link MapData}.</p>
   *
   */
  public void clearSynteny()
  {
    synteny.clear();
  }

  /**
   * <p>Get a list of the {@link Chromosome}s already loaded for this
   * {@link MapData}.</p>
   *
   * @return
   *   {@link Vector} of {@link Chromosome}s already loaded
   */
  public Vector<Chromosome> getLoadedChromosomes()
  {
    return chromosomes;
  }

  /**
   * <p>Delete the {@link Chromosome}s loaded for this {@link MapData}.</p>
   *
   */
  public void clearChromosomes()
  {
    chromosomes.clear();
  }

  /**
   * <p>Get the databased id of the {@link MapData}.</p>
   *
   * @return
   *   int value of the database id
   */
  public int getId()
  {
    return id;
  }

  public boolean equals(Object o)
  {
    if(this == o)
      return true;
    if(o == null || this == null)
      return false;
    if(o instanceof MapData)
    {
      MapData map = (MapData) o;
      if (map.getId() == id)
        return true;
      if (map.getId() == id && map.getSourceId() == sourceId && map.getSource().equals(source)
          && map.getSpecies().equals(species) && map.getTaxID() == taxId
          && ((version == null && map.getVersion() == null) || (map.getVersion() != null
              && map.getVersion().equals(version)))
          && map.getUnits() == units && map.getType() == type
          && map.getScale() == scale && map.getName().equals(name))
        return true;
    }
    return false;
  }

  /**
   * <p>Set the units of the {@link MapData}.</p>
   *
   * @param units
   *   BP_UNITS - BP
   *   CM_UNITS - CM
   *   CR_UNITS - CR
   */
  public void setUnits(int units)
  {
    this.units = (units < BP_UNITS || units > CR_UNITS) ? UNKNOWN : units;
  }

  /**
   * <p>Set the species of the {@link MapData}.</p>
   *
   * @param species
   *   {@link String} of the species
   */
  public void setSpecies(String species)
  {
    this.species = species;
  }

  /**
   * <p>Set the taxonomy ID of the {@link MapData}.</p>
   *
   * @param id
   *   {@link Integer} representing this map's taxonomy ID.
   */
  public void setTaxId(int id)
  {
    taxId = id;
  }

  /**
   * <p>Set the source of the {@link MapData}.</p>
   *
   * @param dbId
   *   int value of the database id
   * @param name
   *   {@link String} name of the database
   */
  public void setSource(int dbId, String name)
  {
    sourceId = dbId;
    source = name;
  }

  /**
   * <p>Get the database id of this {@link MapData}'s source.</p>
   *
   * @return
   *   int value of the source's database id
   */
  public int getSourceId()
  {
    return sourceId;
  }

  /**
   * <p>Set the human readable version string for this {@link MapData}.</p>
   *
   * @param version
   *   {@link String} of the version.
   */
  public void setVersion(String version)
  {
    this.version = version;
  }

  /**
   * <p>Set the type of the {@link MapData}.</p>
   *
   * @param type
   *   GENOMIC          - Genomic
   *   GENETIC          - Genetic
   *   RADIATION_HYBRID - Radiation Hybrid
   */
  public void setType(int type)
  {
    this.type = (type < GENOMIC || type > RADIATION_HYBRID) ? UNKNOWN : type;
  }

  /**
   * <p>Sets the {@link Vector} of {@link AnnotationSet}s that are available
   * for this map.</p>
   *
   * @param availableAnnotationSets
   *    Vector of the available {@link AnnotationSet}s for this map.
   */
  public void setAvailableAnnotationSets(Vector<AnnotationSet> availableAnnotationSets)
  {
    this.availableAnnotationSets = availableAnnotationSets;
  }

  /**
   * <p>Get the units of the {@link MapData}.</p>
   *
   * @return
   *   {@link String} of the units
   */
  public String getUnitsString()
  {
    if (units == BP_UNITS)
      return "bp";
    else if (units == CM_UNITS)
      return "cM";
    else if (units == CR_UNITS)
      return "cR";
    else
      return "";
  }

  /**
   * <p>Get the URL for the homepage of the {@link MapData}.</p>
   *
   * @return
   *   {@link String} containing the URL.
   * @throws SQLException
   *   If there is a problem connecting with the VCMap server, an exception
   *   will be thrown
   */
  public String getURL()
    throws SQLException
  {
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("taxID", "" + taxId);

    return Factory.getURL(sourceId, "map", params);

  }

  /**
   * Set the default {@link AnnotationSet} for this MapData.
   * @param annotationSet
   *   The {@link AnnotationSet} to mark as default.
   */
  public void setDefaultAnnotationSet(AnnotationSet annotationSet)
  {
    defaultAnnotationSet = annotationSet;
  }

  /**
   * <p>Returns All {@link AnnotationSet} Types for this map</p>
   *
   * @return
   *   Vector of Strings containing all of the {@link AnnotationSet} Types
   */
  public Vector<String> getAllAnnotationSetTypes()
  {
    Vector<String> types = new Vector<String>();
    for (AnnotationSet set : availableAnnotationSets)
      if(!types.contains(set.getType()))
        types.add(set.getType());
    return types;
  }

  /**
   * <p>Returns All {@link AnnotationSet} Source Strings for this map</p>
   *
   * @return
   *    Vector of Strings containing all of the {@link AnnotationSet} Sources
   */
  public Vector<String> getAllAnnotationSetSources()
  {
    Vector<String> sourceStrings = new Vector<String>();
    for (AnnotationSet set : availableAnnotationSets)
      if(!sourceStrings.contains(set.getSource()))
        sourceStrings.add(set.getSource());
    return sourceStrings;
  }
}
