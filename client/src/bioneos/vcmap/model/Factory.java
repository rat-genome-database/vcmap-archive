package bioneos.vcmap.model;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import org.apache.log4j.Logger;

import bioneos.common.errors.ErrorReporter;
import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.dialogs.AnnotationDialog;

/**
 * <p>This class is used to create {@link MapData} objects.  This class will
 * perform all of the necessary heavy lifting to create data model objects:
 * contacting the VCMap backend system, transferring the necessary data,
 * creating the data model in memory.  This class will serve as the pluggable
 * abstraction layer that will separate the rest of the code from the details
 * about how to communicate with the backend of the system.</p>
 * <p>This class will serve as the mechanism to load not only the
 * {@link MapData} objects, but any object from the data model.</p>
 *
 * <p>Created on: July 17th, 2008</p>
 * @author sgdavis@bioneos.com
 */
public class Factory
{
  // Logging
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // DB Connection string
  private static final String DB_STRING = "jdbc:mysql://animalgenome.org/vcmap";
  private static final String DB_USER = "sgdavis";
  private static final String DB_PASS = "goVC*_*";

  // Database connection
  private Connection dbConn = null;

  // Singleton Design Pattern
  private static Factory instance = null;

  // For storing ontology information
  // NOTE - May need the ability to store multiple OntologyTrees later on
  private OntologyTree ontologyTree = null;

  // For development mode
  public static boolean devel = false;

  // For background construction of the Ontology Tree
  private static TreeBuilder ontologyBuilder;

  // Available DisplayMaps
  private Vector<MapData> availableMaps;
  // Available AnnotationSets
  private Vector<AnnotationSet> availableAnnotationSets;

  /*
   * This static block of code is run at the time this class is loaded.  We
   * want to load the driver for this class at that time, and only at that time
   * so we have this static block to perform this.
   */
  static
  {
    try
    {
      Class.forName("com.mysql.jdbc.Driver");
    }
    catch (ClassNotFoundException e)
    {
      String msg = "The MySQL Driver could not be found";
      ErrorReporter.handleMajorError(null, msg);
    }
  }

  public Factory()
  {
    instance = this;
    availableMaps = new Vector<MapData>();
    availableAnnotationSets = new Vector<AnnotationSet>();
    initializeData();
  }

  /*
   * (non-javadoc)
   * Helper method to test the database connection (if any exists).
   * @return True for a good connection, false otherwise.
   */
  private boolean isConnected()
  {
    if (dbConn == null) return false;

    try
    {
      // Ping query string
      String query = "SELECT 1";
      Statement statement = dbConn.createStatement();
      statement.executeQuery(query);
      statement.close();
      return true;
    }
    catch (SQLException e)
    {
      dbConn = null;
      return false;
    }
  }

  /*
   * (non-javadoc)
   * Helper method to bring up a database connection.
   * NOTE: There is no way to "reconnect" so we are forced to recreate the
   * connection even if the previous connection simply timed out.  In order
   * to ensure there are no stale references to old Connection objects, we
   * will simply require the Factory to hold all references to the Connection.
   * When external classes need to make SQL queries for whatever reason, they
   * will simply call a generic "query()" method on the Factory instance that
   * is controlled with the singleton design pattern.
   */
  private void connect()
    throws SQLException
  {
    // First test the connection
    if (isConnected()) return;

    // Make our connection
    dbConn = DriverManager.getConnection(DB_STRING + (devel ? "_devel" : ""), DB_USER, DB_PASS);
    // FIXME - Handle notification to the user when access is denied HERE (use error reporter)
  }

  /**
   * A advanced method for allowing classes access to a {@link Statement}
   * object created by the {@link Factory} instance in order to allow for more
   * advanced SQL techniques like fetching block results, canceling long
   * queries, etc.  In turn, the classes using this method must make absolutely
   * certain that they manage their own memory and remember to close the
   * {@link Statement} by calling its .close() method when appropriate.
   *
   * @return
   *   A {@Link Statement} object created by the {@link Factory}.
   * @throws SQLException
   *   Thrown when there is a problem creating the Statement.
   */
  public static Statement getStatement()
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    // Check our connection
    if (!instance.isConnected()) instance.connect();
    return instance.dbConn.createStatement();
  }

  /**
   * <p>Initializes all data from the server.  This needs to be called at the start of the initial
   * load of the program.</p>
   */
  private static void initializeData()
  {
    if (instance == null) instance = new Factory();
    try
    {
      if (!instance.isConnected()) instance.connect();
      // Initialize the available maps from the DB
      // NOTE: This process will also create all of the available AnnotationSet
      // object and identify the default for each map from this list.
      initializeAllMaps();
    }
    catch (SQLException e)
    {
      logger.error("Error in Initializing all available map data: " + e);
      String msg = "There was a problem initializing the application:\n\n" +
        "General database error: Could not load available map data";
      ErrorReporter.handleMajorError(null, msg);
      return;
    }
  }

  /**
   * <p>Initializes all of the maps (assemblies) from the sql server</p>
   * @throws SQLException
   */
  private synchronized static void initializeAllMaps()
    throws SQLException
  {
    instance.availableMaps = new Vector<MapData>();
    String query = "SELECT id FROM maps WHERE disabled=0";
    Statement st = instance.dbConn.createStatement();
    ResultSet rs = st.executeQuery(query);
    while (rs.next())
      instance.availableMaps.add(initializeMap(rs.getInt(1)));
    st.close();
  }


  /**
   * <p>Returns the Vector of Strings that have the available chromosome names for the given
   * map.</p>
   * @param map
   *    {@link MapData} to get the available chromosome names of.
   * @return
   *   A {@link Vector} containing the Strings of the names of all available
   *   {@link Chromosome}s for the specified MapData object, or an empty Vector
   *   if no Chromosomes are available.  This method does not load all of the
   *   data associated with the Chromosome objects, it simply queries for the
   *   names of those objects that would be created.
   */
  public static Vector<String> getAvailableChromosomes(MapData map)
    throws SQLException
  {
    // Sanity check on our parameters
    if (map == null) return new Vector<String>();
    if (instance == null) instance = new Factory();
    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();
      st = instance.dbConn.createStatement();

      Vector<String> names = new Vector<String>();
      String query = "SELECT c.name FROM chromosomes c, maps m ";
      query += "WHERE m.disabled=0 AND m.id=c.map_id AND c.map_id=" + map.getId();
      ResultSet rs = st.executeQuery(query);
      while (rs.next())
        names.addElement(rs.getString(1));

      return names;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem querying for available chromosomes " +
          "for map_id: " + map.getId() + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Returns the Vector of available {@link MapData} from the server, if it has already been
   * obtained, it returns the Vector that is already loaded.</p>
   * @return
   *   A {@link Vector} with all of the available {@link MapData} objects that
   *   can be loaded from the backend, or an empty {@link Vector} if no maps
   *   are available from the database.
   */
  public static Vector<MapData> getAvailableMaps()
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    // If Vector has already been created return it directly
    if (instance.availableMaps != null)
      return instance.availableMaps;

    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      instance.availableMaps = new Vector<MapData>();
      String query = "SELECT id FROM maps WHERE disabled=0";
      st = instance.dbConn.createStatement();
      ResultSet rs = st.executeQuery(query);
      while (rs.next())
        instance.availableMaps.add(initializeMap(rs.getInt(1)));

      return instance.availableMaps;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem querying for available maps (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * Initializes the {@link MapData} object for the map with the given id
   * @param mapId
   *    the ID of the map in the mySQL server
   */
  public static MapData initializeMap(int mapId)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      // Build our query
      String query = "SELECT m.id, s.id, s.name, m.type, m.species, m.taxID, "
        + "v.human_name, m.units, m.scale, m.default_annotation ";
      query += "FROM maps m, sources s, versions v ";
      query += "WHERE m.disabled=0 AND v.source_id=s.id AND m.id=" + mapId + " AND m.version_id=v.id";
      st = instance.dbConn.createStatement();
      ResultSet rs = st.executeQuery(query);

      // Build our MapData object
      MapData map = null;
      int defaultAset = -1;
      if (rs.next())
      {
        map = new MapData(rs.getInt(1));
        map.setSource(rs.getInt(2), rs.getString(3));
        if (rs.getString(4).equals("Genomic"))
          map.setType(MapData.GENOMIC);
        else if (rs.getString(4).equals("Genetic"))
          map.setType(MapData.GENETIC);
        else if (rs.getString(4).equals("RH"))
          map.setType(MapData.RADIATION_HYBRID);
        map.setSpecies(rs.getString(5));
        map.setTaxId(rs.getInt(6));
        map.setVersion(rs.getString(7));
        if (rs.getString(8) != null)
        {
          if (rs.getString(8).equals("bp"))
            map.setUnits(MapData.BP_UNITS);
          else if (rs.getString(8).equals("cM"))
            map.setUnits(MapData.CM_UNITS);
          else if (rs.getString(8).equals("cR"))
            map.setUnits(MapData.CR_UNITS);
        }
        map.setScale(rs.getInt(9));
        defaultAset = rs.getInt(10);
      }
      
      // Now build all AnnotationSets for this map
      query = "SELECT a.id, a.map_id, a.type, v.source_id, s.name, v.human_name, v.release_date " +
        "FROM annotation_sets a, versions v, sources s WHERE " +
        "a.version_id = v.id AND v.source_id = s.id AND a.map_id = " + map.getId();
      rs = st.executeQuery(query);
      Vector<AnnotationSet> asets = new Vector<AnnotationSet>();
      AnnotationSet defaultSet = null;
      while (rs.next())
      {
        AnnotationSet aset = new AnnotationSet(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(6), rs.getString(7));
        aset.setSourceId(rs.getInt(4));
        aset.setSource(rs.getString(5));
        aset.setMap(map);
        asets.add(aset);
        if (aset.getId() == defaultAset)
          defaultSet = aset;
      }
      map.setAvailableAnnotationSets(asets);
      map.setDefaultAnnotationSet(defaultSet);
     
      return map;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem loading map data for mapId: " + mapId + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Returns the {@link AnnotationSet} with the given id and {@link MapData}
   * associated with it</p>
   * @param setID
   *    The integer value for the set_id in the mySQL server
   * @param map
   *    The {@link MapData} object that contains the information for the map
   * @return
   * @throws SQLException
   */
  public static AnnotationSet getAnnotationSet(int asetId, MapData map)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();
      
      String query = "SELECT a.id, a.map_id, a.type, v.source_id, s.name, v.human_name, v.release_date " +
        "FROM annotation_sets a, versions v, sources s WHERE " +
        "a.version_id = v.id AND v.source_id = s.id AND a.id = " + asetId;
      st = instance.dbConn.createStatement();
      ResultSet rs = st.executeQuery(query);
      AnnotationSet aset = null;
      if (rs.next())
      {
        aset = new AnnotationSet(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(6), rs.getString(7));
        aset.setSourceId(rs.getInt(4));
        aset.setSource(rs.getString(5));
        aset.setMap(map);
      }
      
      return aset;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem loading annotation_set data for asetId: " + asetId + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>returns the {@link AnnotationSet} for a custom set of {@link Annotation}s</p>
   *
   * @param type
   *    String containing the type of annotation (STS, GENE, VCF, BED, etc.)
   * @param map
   *    {@link MapData} for the map that corresponds with this set of {@link Annotation}s
   * @param sourceFile
   *    The File object that this custom annotation set is being loaded from
   * @return
   * @throws SQLException
   */
  public static AnnotationSet getCustomAnnotationSet(String type, MapData map, File sourceFile, String fileType)
    throws SQLException
  {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    AnnotationSet newSet = new AnnotationSet(-1, map.getId(), type, sourceFile.getName(), sdf.format(new Date()));
    newSet.setSource(fileType);
    newSet.setCustom(true, sourceFile);
    newSet.setMap(map);
    instance.availableAnnotationSets.add(newSet);
    return newSet;
  }

  /**
   * <p>Returns the {@link MapData} object for the given mapID</p>
   *
   * @param mapID
   *    mapID of the {@link MapData} object to return
   * @return
   */
  public static MapData getMap(int mapID)
  {
    Vector<MapData> availMaps = instance.availableMaps;
    if (availMaps == null)
    {
      try
      {
        availMaps = getAvailableMaps();
      }
      catch (SQLException e)
      {
        logger.error("Factory: Unable to get any maps from the server or program: " + e);
        return null;
      }
    }
    for (MapData map : availMaps)
      if(map.getId() == mapID)
        return map;
    return null;
  }
  
  /**
   * Query the database for the newest map based on their 'release_date' values
   * in the database.  This method makes the assumption that all maps passed to
   * this method will have proper and valid ids.
   * @param maps
   *   The list of the maps to query the database with.
   * @return
   *   An object reference to the newest map of the group.
   */
  public static MapData findNewestMap(Vector<MapData> maps)
    throws SQLException
  {
    if (instance == null) instance = new Factory();
    
    StringBuilder ids = new StringBuilder();
    for (MapData m : maps)
      ids.append(ids.length() > 0 ? ", " : "").append(m.getId());
    if (ids.length() == 0) return null; 

    // Now query the database
    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();
      
      String sql = "SELECT m.id FROM maps m LEFT JOIN versions v ON m.version_id=v.id " +
        "WHERE m.id IN (" + ids + ") ORDER BY release_date DESC";
      st = instance.dbConn.createStatement();
      ResultSet rs = st.executeQuery(sql);
      MapData newest = maps.get(0);
      if (rs.next())
        for (MapData m : maps)
          if (m.getId() == rs.getInt(1))
            newest = m;
      
      return newest;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem querying for map release_date (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Returns the {@link Chromosome} given the {@link MapData} and name of the chromosome.</p>
   *
   * @param map
   *    {@link MapData} object that the chromosome will correspond to
   * @param chromosomeName
   *    The Name of the Chromosome to be loaded
   */
  public static Chromosome getChromosome(MapData map, String chromosomeName)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    // Now query the database
    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      logger.debug("Starting load of chromosome " + chromosomeName + " for mapId: " + map.getId());
      long timer = System.currentTimeMillis();
      // Build our query
      StringBuilder query = new StringBuilder("SELECT c.id, c.length, c.name ");
      query.append("FROM maps m, chromosomes c ");
      query.append("WHERE m.id=" + map.getId() + " AND m.disabled=0 AND m.id=c.map_id ");
      query.append("AND c.name='" + chromosomeName + "'");
      st = instance.dbConn.createStatement();
      ResultSet rs = st.executeQuery(query.toString());

      // Build our Chromosome object
      Chromosome chr = null;
      if (rs.next())
      {
        chr = new Chromosome(map, rs.getInt(1));
        chr.setLength(rs.getInt(2));
        chr.setName(rs.getString(3));
      }
      else
      {
        return null;
      }

      // Determine annotation sets that need to be loaded as markers
      if (map.getDefaultAnnotationSet() != null)
        chr.loadAnnotation(map.getDefaultAnnotationSet());

      // All done
      logger.debug("Finished loading chromosome in " +
          (System.currentTimeMillis() - timer) + "ms");
      return chr;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem loading Chromosome data, mapId:" + map.getId() +
          ", name:" + chromosomeName  + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * This is a convenience method, and is equivalent to calling
   * <code>getAnnotation(chr, type, start, stop, null)</code>.
   */
  public static Vector<Annotation> getAnnotation(Chromosome chr, AnnotationSet annotationSet,
      int start, int stop)
    throws SQLException
  {
    return getAnnotation(chr, annotationSet, start, stop, null);
  }

  /**
   * Returns a <code>Vector</code> containing all the {@link Annotation} for a
   * given chromosome and type.  If <code>ontologyFilter</code> is not null, the
   * given {@link OntologyNode}'s category will be used to filter the set of
   * {@link Annotation} returned.
   *
   * @param chr
   *  The {@link Chromosome} for which to load {@link Annotation}.
   *
   * @param type
   *  A <code>String</code> representing the type of {@link Annotation} to load.
   *
   * @param start
   *  <code>Integer</code> value of the start point (in bp or cM).
   *
   * @param stop
   *  <code>Integer</code> value of the stop point (in bp or cM).
   *
   * @param ontologyFilter
   *  An {@link OntologyNode} representing the ontology category with which to
   *  filter the loaded {@link Annotation}.
   *
   * @return
   *  A <code>Vector</code> containing all of the loaded {@link Annotation}.
   *
   * @throws SQLException
   */
  public static Vector<Annotation> getAnnotation(Chromosome chr, AnnotationSet annotationSet,
      int start, int stop, OntologyNode ontologyFilter)
    throws SQLException
  {
    StringBuilder query = new StringBuilder();
    ArrayList<Integer> ontologyIDs = new ArrayList<Integer>();
    Vector<Annotation> annots = new Vector<Annotation>();

    // The ontology filter by default when loading QTL annotation and when
    // no ontology filter is chosen by user is the ROOT_NODE ontology node,
    // but in order to avoid some extra SQL, we can discard this node and
    // load all annotation of that type (even though we know it is not
    // always the case that this annotation is assigned to any of the
    // nodes in the ontology tree.
    if (ontologyFilter != null && ontologyFilter.getCategory().equals(OntologyTree.ROOT_NODE))
      ontologyFilter = null;
    if (instance.ontologyTree == null && ontologyFilter != null)
      instance.ontologyTree = ontologyBuilder.getOntologyTree();

    // Ensure order of start / stop
    if (start > stop)
    {
      int swap = start;
      start = stop;
      stop = swap;
    }
    logger.debug("Loading annotation set (" + annotationSet.getId() + ") for chrId: " +
        chr.getId() + " from [" + start + ", " + stop + "]" +
        ((ontologyFilter == null) ? "" : " with ontologyFilter: " + ontologyFilter.getCategory()));

    if (instance == null) instance = new Factory();

    Statement st = null;
    Statement st2 = null;
    ResultSet rs = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();
      st = instance.dbConn.createStatement();

      // determine if we were passed ontology filtering information
      // if so, query for the relevant annotation
      if (ontologyFilter != null)
      {
        logger.debug("Querying for specific ontology term: " + ontologyFilter.getCategory());
        ArrayList<OntologyNode> filters = new ArrayList<OntologyNode>();

        if (ontologyFilter.getFilterChildren() == null)
        {
          filters.add(ontologyFilter);
        }
        else
        {
          filters = instance.ontologyTree.traverseTree(ontologyFilter);
        }

        for (OntologyNode n : filters)
          ontologyIDs.add(n.getID());

        // Build our query for the annotation objects
        query.append("SELECT a.id, a.name, a.start, a.stop, a.source_ref_id, v.value ");
        query.append("FROM annotation a, annotation_avps avp, attributes at, vals v ");
        query.append("WHERE a.id=avp.annotation_id AND avp.attribute_id=at.id AND ");
        query.append("annotation_set_id = " + annotationSet.getId() + " AND ");
        query.append("((a.start>=" + start + " AND a.start<=" + stop + ") OR ");
        query.append("(a.stop>=" + start + " AND a.stop<=" + stop + ") OR ");
        query.append("(a.start<=" + start + " AND a.stop>=" + stop + ")) AND ");
        query.append("a.chromosome_id=" + chr.getId() + " AND avp.value_id=v.id ");
        query.append("AND at.type='ontology' AND v.value IN (");
        StringBuilder idStr = new StringBuilder();
        for (Integer id : ontologyIDs)
          idStr.append(idStr.length() == 0 ? "" : ",").append("" + id);
        idStr.append(")");
        query.append(idStr.toString());
      }
      else
      {
        logger.debug("Loading annotation without an ontology filter");
        // Build our query for the annotation objects.
        // Executed when loading STS and PSEUDO annotations as the ontology filter is null.
        query.append("SELECT a.id, a.name, a.start, a.stop, a.source_ref_id ");
        query.append("FROM annotation a WHERE ");
        query.append("((a.start>=" + start + " AND a.start<=" + stop + ") OR ");
        query.append("(a.stop>=" + start + " AND a.stop<=" + stop + ") OR ");
        query.append("(a.start<=" + start + " AND a.stop>=" + stop + ")) AND ");
        query.append("a.chromosome_id=" + chr.getId() + " AND a.annotation_set_id=" + annotationSet.getId());
      }
      rs = st.executeQuery(query.toString());
      HashMap<Integer, Annotation> annotMap = new HashMap<Integer, Annotation>();
      int sourceId = annotationSet.getSourceId();
      while (rs.next())
      {
        // Basic information
        Annotation a;
        if (ontologyFilter != null )
        {
          a = new Annotation(chr, rs.getInt(1), instance.ontologyTree.getNodeByID(rs.getInt(6)), sourceId, rs.getString(5));
        }
        else
        {
          a = new Annotation(chr, rs.getInt(1), null, sourceId, rs.getString(5));
        }
        a.addName(rs.getString(2));
        a.setStart(rs.getInt(3));
        a.setStop(rs.getInt(4));
        a.setAnnotationSet(annotationSet);
        annots.add(a);
        annotMap.put(rs.getInt(1), a);
      }
      
      // Now we must populate the links so siblings can be identified
      getLinkIds(annots);
      
      // And return our annots
      return annots;
    }
    catch (SQLException e)
    {
      // We don't need to report this error since it is only a warning
      logger.warn("Factory: Problem loading Annotation for chromosomeId: " + chr.getId()
          + " from " + start + " to " + stop + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
        if (st2 != null) st2.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Gets the link ID's of the given annotation only if a single annotation, will call the
   * {@link getLinkIDs} method.</p>
   * @param annot
   *    Annotation to get the linkID's of
   * @throws SQLException
   */
  public static void getLinkIds(Annotation annot)
    throws SQLException
  {
    Vector<Annotation> annots = new Vector<Annotation>();
    annots.add(annot);
    getLinkIds(annots);
  }

  /**
   * <p>Retrieve the link IDs from the database for the given {@link Vector} of {@link Annotation}s
   * from the links table</p>
   * @param annots
   *    The {@link Vector} of {@link Annotation}s to get the link for
   * @throws SQLException
   */
  public static void getLinkIds(Vector<Annotation> annots)
    throws SQLException
  {
    if (annots.size() == 0) return;
    
    long start = System.currentTimeMillis();

    // For performance reasons, build a temporary Hash for id->annot
    HashMap<Integer, Annotation> map = new HashMap<Integer, Annotation>();
    StringBuilder ids = new StringBuilder();
    for (Annotation a : annots)
    {
      ids.append(ids.length() > 0 ? ", " : "").append(a.getId());
      map.put(a.getId(), a);
    }

    // And query for all matching link ids associated with these annot ids
    String query = "SELECT annotation_id, id, source_id FROM links " +
      "WHERE annotation_id IN (" + ids + ")";
    Statement st = instance.dbConn.createStatement();
    ResultSet rs = st.executeQuery(query);
    while(rs.next())
    {
      if (map.get(rs.getInt(1)) != null)
        map.get(rs.getInt(1)).addLinkId(rs.getInt(2));
    }
    st.close();
    
    logger.debug("Getting links took: " + (System.currentTimeMillis() - start) + "ms");
  }

  /**
   * <p>Load AVP information from the database for a piece of {@link Annotation}.</p>
   *
   * @param annotation
   *   {@link Annotation} to load AVP information for
   */
  public static void getAnnotationAVPInformation(Annotation annotation)
    throws SQLException
  {
    Vector<Annotation> annots = new Vector<Annotation>();
    annots.add(annotation);
    getAnnotationAVPInformation(annots);
  }

  /**
   * <p>Load AVP information from the database for a piece of {@link Annotation}.</p>
   *
   * @param annots
   *   A {@link Vector} of {@link Annotation} to load AVP information for
   */
  public static void getAnnotationAVPInformation(Vector<Annotation> annots)
    throws SQLException
  {
    if (annots == null || annots.size() == 0) return;

    // Create our ids && initialize the info object for our annotation
    StringBuilder ids = new StringBuilder();
    for(Annotation a : annots)
    {
      a.info = new HashMap<String, String>();
      ids.append((ids.length() == 0) ? "" : ", ").append(a.getId());
    }
    logger.debug("Get AVP Information for annotation with ids: " + ids.toString());

    Statement st = null;
    try
    {
      if (!instance.isConnected()) instance.connect();
      st = instance.dbConn.createStatement();

      // Now get all AVP information
      StringBuilder query = new StringBuilder();
      query.append("SELECT avp.annotation_id, a.type, v.value FROM ");
      query.append("annotation_avps avp, attributes a, vals v WHERE ");
      query.append("avp.annotation_id in (" + ids.toString() + ") AND avp.attribute_id=a.id AND ");
      query.append("avp.value_id=v.id");
      //query.append("AND (avp.chromosome_ID=" + annotation.getChromosome().getId() + " OR avp.chromosome_id IS NULL)");
      ResultSet rs = st.executeQuery(query.toString());
      while (rs.next())
      {
        // Identify which annotation we have gotten information for
        Annotation annotation = null;
        for(Annotation a : annots)
        {
          if (a.getId() == rs.getInt(1))
          {
            annotation = a;
            break;
          }
        }

        if (annotation == null)
        {
          logger.warn("getAnnotationAVPInformation: Cannot find annotation in our list of annots?!?");
          continue;
        }

        // Warning on dups (overridden value is the result)
        if(annotation.getInfo(rs.getString(2)) != null)
          logger.warn("Duplicate AVP entry for annotation " + annotation.getId() +
              " attribute: " + rs.getString(2));

        // Now add this key treating names specially
        if (rs.getString(2).equals("name"))
          annotation.addName(rs.getString(3));
        else
          annotation.addInfo(rs.getString(2), rs.getString(3));
      }
    }
    catch (SQLException e)
    {
      // We don't need to report this error since it is only a warning
      logger.warn("Factory: Problem loading AVP information for annotation with ids: " + ids);
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * Get a list of {@link SyntenyBlock}s for the query maps.
   * @param origin
   *   A {@link Chromosome} representing the backbone from which the blocks
   *   should originate.
   * @param target
   *   The {@link MapData} to be queried for matching SyntenyBlocks.
   * @return
   *   A {@link Vector} of the {@link SyntenyBlock}s for the backbone (origin)
   *   Chromosome and target Map.  It is important to note that errors will
   *   occassionally occur in this process where we do not want to throw an
   *   {@link Exception}.  Instead, the error is reported to the logger and a
   *   <code>null</code> will be placed in the Vector in place of a
   *   SyntenyBlock Object.  Thus it is important for callers of this method to
   *   examine the returned list for nulls, and handle an error if necessary.
   *   Additionally, this method is allowed to return an empty list if no
   *   SyntenyBlocks are found, but should never return null.
   */
  public static boolean getSyntenyTest(Chromosome origin, MapData target)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    Statement st = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      logger.debug("Starting synteny load of map: " + target.getId() + " for chromosome: " + origin.getId());
      st = instance.dbConn.createStatement();

      // Load all syntenic chromosomes for the target map
      StringBuilder query = new StringBuilder("SELECT COUNT(DISTINCT c.name) FROM chromosomes c, synteny s WHERE ");
      query.append("c.id=s.right_id and s.left_id=" + origin.getId() + " AND ");
      query.append("c.map_id=" + target.getId());
      ResultSet rs = st.executeQuery(query.toString());
      rs.next();
      if(!rs.getString(1).equals("0"))
        return true;
      else
        return false;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem testing Synteny for mapId: " + target.getId()
        + " backbone chromosomeId: " + origin.getId() + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * Get a list of {@link SyntenyBlock}s for the query maps.
   * @param origin
   *   A {@link Chromosome} representing the backbone from which the blocks
   *   should originate.
   * @param target
   *   The {@link MapData} to be queried for matching SyntenyBlocks.
   * @return
   *   A {@link Vector} of the {@link SyntenyBlock}s for the backbone (origin)
   *   Chromosome and target Map.  It is important to note that errors will
   *   occassionally occur in this process where we do not want to throw an
   *   {@link Exception}.  Instead, the error is reported to the logger and a
   *   <code>null</code> will be placed in the Vector in place of a
   *   SyntenyBlock Object.  Thus it is important for callers of this method to
   *   examine the returned list for nulls, and handle an error if necessary.
   *   Additionally, this method is allowed to return an empty list if no
   *   SyntenyBlocks are found, but should never return null.
   */
  public static void getSynteny(Vector<SyntenyBlock> synteny, Chromosome origin, MapData target)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    Statement st = null, st2 = null;
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      logger.debug("Starting synteny load of map: " + target.getId()
          + " for chromosome: " + origin.getId());
      st = instance.dbConn.createStatement();

      // Load all syntenic chromosomes for the target map
      long begin = System.currentTimeMillis();
      if (synteny == null) synteny = new Vector<SyntenyBlock>();
      Vector<Chromosome> matches = new Vector<Chromosome>();
      StringBuilder query = new StringBuilder("SELECT DISTINCT c.name FROM chromosomes c, synteny s ");
      query.append("WHERE c.id=s.right_id and s.left_id=" + origin.getId() + " AND ");
      query.append("c.map_id=" + target.getId());
      ResultSet rs = st.executeQuery(query.toString());
      while (rs.next())
        matches.add(target.getChromosome(rs.getString(1)));
      logger.debug("Loading syntenic chromosomes took: " + + (System.currentTimeMillis() - begin) + "ms");

      // Now load all synteny blocks where left_id = origin
      for (Chromosome chr : matches)
      {
        query = new StringBuilder();
        query.append("SELECT right_start_id, right_stop_id, left_start_id, left_stop_id, id ");
        query.append("FROM synteny WHERE ");
        query.append("left_id=" + origin.getId() + " AND right_id=" + chr.getId());
        rs = st.executeQuery(query.toString());
        while (rs.next())
        {
          // First load the target region of the syntenic chromosome
          int start = -1, stop = -1;
          query = new StringBuilder();
          query.append("SELECT id, start, stop FROM annotation ");
          query.append("WHERE id IN (" + rs.getInt(1) + "," + rs.getInt(2) + ") AND ");
          query.append("chromosome_id=" + chr.getId());
          st2 = instance.dbConn.createStatement();
          ResultSet rs2 = st2.executeQuery(query.toString());
          while (rs2.next())
          {
            if (rs2.getInt(1) == rs.getInt(1)) start = rs2.getInt(2);
            if (rs2.getInt(1) == rs.getInt(2)) stop = rs2.getInt(3);
          }
          chr.loadRegion(start, stop);

          // Now create the synteny block
          // NOTE: if the annotation needed to support the block has not yet
          // been loaded into memory, this will create an error situation for
          // which we will insert a null into the Vector.
          SyntenyBlock syn = new SyntenyBlock();
          if (origin.getAnnotationById(rs.getInt(3)) == null ||
              origin.getAnnotationById(rs.getInt(4)) == null ||
              chr.getAnnotationById(rs.getInt(1)) == null ||
              chr.getAnnotationById(rs.getInt(2)) == null)
          {
            logger.error("Factory: Problem loading synteny block with id: " + rs.getInt(5));
            // This indicates an error situation to the caller.
            // This was implemented so that we can still return a list of the
            // block that all correctly load, but the caller has some method of
            // determining that an error occurred.
            synteny.add(null);
          }
          else
          {
            syn.setLeftMarkers(origin, origin.getAnnotationById(rs.getInt(3)).getStart(), 
                origin.getAnnotationById(rs.getInt(4)).getStop());
            syn.setRightMarkers(chr, chr.getAnnotationById(rs.getInt(1)).getStart(), 
                chr.getAnnotationById(rs.getInt(2)).getStop());
            synteny.add(syn);
          }
        }
      }
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem loading Synteny for mapId: " + target.getId()
        + " backbone chromosomeId: " + origin.getId() + " (" + e + ")");
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
        if (st2 != null) st2.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * Method to grab source ids from the database.
   * @param source
   *   The name of the source for which to query.
   * @return
   *   The internal database id for the given source (or -1 if not found).
   */
  public static int getSourceId(String source)
  {
    if (instance == null) instance = new Factory();

    PreparedStatement st = null;
    ResultSet rs = null;
    try
    {
      String query = "SELECT id FROM sources WHERE name=?";
      st = instance.dbConn.prepareStatement(query);
      st.setString(1, source);
      rs = st.executeQuery();
      if (rs.next()) return rs.getInt(1);

      // No source found
      return -1;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem grabbing source id for '" + source + "': " + e);
      return -1;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * Returns the String of the given source id
   * @param id
   *    integer of the ID to get the name of the source for
   * @return
   */

  public static String getSourceString(int id)
  {
    if (instance == null) instance = new Factory();

    Statement st = null;
    ResultSet rs = null;
    try
    {
      String query = "SELECT name FROM sources WHERE id=" + id;
      st = instance.dbConn.createStatement();
      rs = st.executeQuery(query);
      if (rs.next()) return rs.getString(1);

      // No source found
      return "";
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem grabbing source name for id:" + id + ": " + e);
      return "";
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Generates a <code>String</code> containing the URL for clicked
   * {@link Annotation}</p>
   *
   * @param sourceID
   *   <code>Integer</code> representing the source id from which the
   *   {@link Annotation} was grabbed.
   *
   * @param target
   *   <code>String</code> representing what kind of URL you will retrieve.
   *
   * @param params
   *   <code>HashMap</code> containing all parameters necessary for loading the URL.
   *
   * @return
   *   the URL.
   *
   * @author dquacken@bioneos.com
   * @author sgdavis
   */

  public static String getURL(int sourceID, String target, HashMap<String, String> params)
    throws SQLException
  {
    return getURL(sourceID, target, null, params);
  }

  /**
   * <p>Generates a <code>String</code> containing the URL for clicked
   * {@link Annotation}</p>
   *
   * @param sourceID
   *   <code>Integer</code> representing the source id from which the
   *   {@link Annotation} was grabbed.
   *
   * @param target
   *   <code>String</code> representing what kind of URL you will retrieve.
   *
   * @param type
   *   <code>String</code> that, when necessary, provides further information about
   *   the kind of URL you are trying to retrieve.
   *
   * @param params
   *   <code>HashMap</code> containing all parameters necessary for loading the URL.
   *
   * @return
   *   the URL.
   *
   * @author dquacken@bioneos.com
   * @author sgdavis
   */
  public static String getURL(int sourceID, String target, String type, HashMap<String, String> params)
    throws SQLException
  {
    if (instance == null) instance = new Factory();

    logger.debug("Loading URL data");

    if (sourceID < 1)
    {
      logger.error("Error getting URL: invalid sourceID");
      return null;
    }

    PreparedStatement st = null;
    ResultSet rs = null;
    String urlString = new String();
    try
    {
      // Check our connection
      if (!instance.isConnected()) instance.connect();

      // Build our query
      String query = "SELECT url FROM source_urls WHERE target = ? AND source_id = ?";
      if (type != null) query += " AND type = ?";
      st = instance.dbConn.prepareStatement(query);
      st.setString(1, target);
      st.setInt(2, sourceID);
      if (type != null) st.setString(3, type);
      rs = st.executeQuery();
      if (rs.next())
      {
        urlString = rs.getString(1);
      }
      else
      {
        logger.warn("Factory: Missing URL data for source id " + sourceID + " and target " + target +
            " and url type " + type);
        return null;
      }
      // Perform the replacements
      for (String key : params.keySet())
      {
        if(params.get(key).indexOf(':') == -1)
          urlString = urlString.replaceAll("\\{" + key + "\\}", params.get(key));
        else
          urlString = urlString.replaceAll("\\{" + key + "\\}", params.get(key).substring(params.get(key).indexOf(':') + 1));
      }
      // Check for missing replacement strings
      if (urlString.indexOf("{") != -1 && urlString.indexOf("}") != -1)
      {
        logger.warn("Could not replace all strings in the source_url (missing params??)  Final url: " + urlString);
        return null;
      }

      return urlString;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem loading URL data" + e);
      throw e;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

  /**
   * <p>Instantiates and starts the threaded {@link GetOntology} object.
   *
   * @param parent
   *   {@link OntologyNode} representing the parent in the hierarchy.
   *
   * @param chrs
   * An {@link ArrayList} containing all currently-loaded {@link Chromosome}s.
   *
   * @return
   *   {@link ArrayList} of {@link OntologyNode}s representing all found ontology
   *   categories.
   *
   * @author dquacken@bioneos.com
   */
  // FIXME!!!!! (No threading here.  Grab from tree already in memory)
  public static ArrayList<OntologyNode> getOntology(OntologyNode parent, ArrayList<Chromosome> chrs)
  {
    ArrayList<OntologyNode> nodes = new ArrayList<OntologyNode>();
    GetOntology getter = new GetOntology(parent, chrs, nodes);
    getter.start();
    while (getter.isAlive());

    return nodes;
  }

  /**
   * <p>This inner class, which extends {@link Thread} is used to determine all
   * relevant {@link OntologyNode}s for all currently loaded {@link Chromosome}s.
   * This class can be instantiated in one of two ways: if <code>parent</code> is
   * null, all top-level ontology categories for the loaded chromosomes will be
   * returned.  If <code>parent</code>} is not null, all of <code>parent<code>'s
   * immediate children will be returned.
   *
   * @param parent
   *   {@link OntologyNode} representing the parent in the hierarchy.
   *
   * @param chrs
   * An {@link ArrayList} containing all currently-loaded {@link Chromosome}s.
   *
   * @param nodes
   * A reference to an {@link ArrayList} in which all found {@link OntologyNode}s
   * will be returned in.
   *
   * @author dquacken@bioneos.com
   */
  static class GetOntology extends Thread
  {
    private OntologyNode parent = null;
    private ArrayList<Chromosome> loadedChrs = null;
    private ArrayList<OntologyNode> nodes = null;

    public GetOntology(OntologyNode parent, ArrayList<Chromosome> loadedChrs, ArrayList<OntologyNode> nodes)
    {
      this.parent = parent;
      this.loadedChrs = loadedChrs;
      this.nodes = nodes;
    }

    public void run()
    {
      if (instance == null || instance.ontologyTree == null) return;

      // wait until the background tree construction is finished
      while (!isTreeBuilt());

      if (instance.ontologyTree == null)
        instance.ontologyTree = ontologyBuilder.getOntologyTree();

      // if the parent is null, get all relevant top level entries
      if (parent == null)
      {
        // given the chromosomes passed in, determine the loaded species
        ArrayList<String> loadedSpecies = new ArrayList<String>();
        for (Chromosome chr : loadedChrs)
        {
          String species = chr.getMap().getSpecies();
          if (!loadedSpecies.contains(species))
            loadedSpecies.add(species);
        }

        // we need to start at the bottom and work up, so first determine
        // which ontologyNodes this chromosome references
        ArrayList<OntologyNode> referencedNodes = new ArrayList<OntologyNode>();
        for (OntologyNode n : instance.ontologyTree.getNodes())
        {
          // first reset the current node's list of descendants
          n.setAnnotationDescendantCount(0);

          // determine if this node is relevant to the loaded species
          boolean valid = false;
          for (String species : n.getSpecies())
          {
            if (loadedSpecies.contains(species))
            {
              valid = true;
              break;
            }
          }
          if (!valid)
            continue;

          // determine if this node's referenced chrs match any of the
          // loaded chrs
          for (int chrID : n.getReferencedChrIDs())
          {
            for (Chromosome chr : loadedChrs)
            {
              if (chrID == chr.getId())
                n.setFilter(true);
              else
              {
                // make sure we don't contradict a previously-set filter
                if (!n.isFilter())
                  n.setFilter(false);
              }
              if (!referencedNodes.contains(n))
                referencedNodes.add(n);
            }
          }
        }

        // we now have a list of all referenced nodes, but to present this to
        // the user in a top-down hiearchy, what we want is a list of
        // top-level nodes; so for each referenced node traverse the tree
        // upwards and add the last ancestor we find to the list
        for (OntologyNode n : referencedNodes)
        {
          int descendants = 0;
          String category = "";
          OntologyNode currentNode = n;
          if (currentNode.isFilter())
          {
            for (Chromosome chr : loadedChrs)
            {
              descendants += currentNode.getReferencedAnnotationCount(chr.getId());
            }
            currentNode.setAnnotationDescendantCount(descendants);
          }
          else
            currentNode.setAnnotationDescendantCount(-1);

          OntologyNode parent = currentNode.getParent();
          if (parent != null)
            category = parent.getCategory();
          else
            continue;

          while (!category.equals(OntologyTree.ROOT_NODE))
          {
            if (currentNode.isFilter())
            {
              parent.setFilter(true, currentNode);
              parent.incrementAnnotationDescendantCount(descendants);
              currentNode = parent;
              parent = currentNode.getParent();
            }
            else
            {
              currentNode = parent;
              parent = currentNode.getParent();
            }
            if (parent == null)
              break;
            category = parent.getCategory();
          }

          if (!nodes.contains(currentNode))
            nodes.add(currentNode);
        }
      }
      // we've been passed a node reference; get all of its immediate children
      else
      {
        if (parent.getFilterChildren() != null)
        {
          for (OntologyNode n : parent.getFilterChildren())
            nodes.add(n);
        }
      }
    }
  }

  /**
   * <p>Builds the {@link OntologyTree} in the background. This ensures that by
   * the time the user invokes the {@link AnnotationDialog}, the
   * {@link OntologyTree} will be loaded in memory.</p>
   *
   * @param mainGUI
   *   the parent {@link MainGUI} object.
   *
   * @author dquacken@bioneos.com
   */
  public static void buildOntologyTree(MainGUI mainGUI)
  {
    Statement st = null;

    // make sure we're properly connected
    try
    {
      if (!instance.isConnected()) instance.connect();
      st = instance.dbConn.createStatement();
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      logger.error("There was a problem with the connection while building the ontology tree: " + e);
    }

    ontologyBuilder = new TreeBuilder(logger, st, mainGUI);
    //ontologyBuilder.setPriority(Thread.MIN_PRIORITY);
    ontologyBuilder.start();
  }

  /**
   * <p>Tests if the tree-building thread is finished.</p>
   *
   * @return
   *   <code>true</code> if the <code>Thread</code> has finished, false otherwise.
   *
   * @author dquacken@bioneos.com
   */
  public static boolean isTreeBuilt()
  {
    if (ontologyBuilder.isAlive())
      return false;
    else
      return true;
  }

  public static OntologyTree getOntologyTree()
  {
    return ontologyBuilder.getOntologyTree();
  }

  public static HashMap<Integer, String> getAllSources()
  {
    if (instance == null) instance = new Factory();

    HashMap<Integer, String> sources = new HashMap<Integer, String>();
    Statement st = null;
    ResultSet rs = null;
    try
    {
      String query = "SELECT id, name FROM sources";
      st = instance.dbConn.createStatement();
      rs = st.executeQuery(query);
      while (rs.next())
        sources.put(rs.getInt(1), rs.getString(2));
      return sources;
    }
    catch (SQLException e)
    {
      logger.warn("Factory: Problem grabbing all sources: " + e);
      return null;
    }
    finally
    {
      // Close the Statement if it was opened
      try
      {
        if (st != null) st.close();
      }
      catch (SQLException e)
      {
        // Do Nothing
      }
    }
  }

}
