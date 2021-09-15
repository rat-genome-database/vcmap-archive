package bioneos.vcmap.gui.dialogs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.JOptionPane;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.callback.AnnotationLoader;
import bioneos.vcmap.callback.MapLoader;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.AnnotationSet;
import bioneos.vcmap.model.Chromosome;
import bioneos.vcmap.model.DisplayMap;
import bioneos.vcmap.model.DisplaySegment;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.model.OntologyNode;
import bioneos.vcmap.model.comparators.ChromosomeComparator;

/**
 * <p></p>
 *
 * <p>Created on: July 22, 2010</p>
 * @author cgoodman
 *
 */
public class SwapMapDialog
  extends JOptionPane
  implements MapLoader, AnnotationLoader
{
  private MainGUI mainGUI;

  private static HashMap<MainGUI, SwapMapDialog> instances = new HashMap<MainGUI, SwapMapDialog>();

  // Logging (static reference)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  private Vector<DisplayMap> maps;
  private MainGUI newMainGUI;
  private MapData newBB;

  private Vector<DisplayMap> loadedMaps;
  private DisplayMap lastLoadedMap;
  private Vector<AnnotationSet> loadedAnnotSets;
  private AnnotationSet lastLoadedAnnotSet;
  private Vector<OntologyNode> loadedOntology;
  private Vector<Object> mapErrorList;
  private Vector<Object> annotErrorList;

  /**
   * <p>Constructor sets parent</p>
   * @param parent
   */
  public SwapMapDialog(MainGUI parent)
  {
    mainGUI = parent;
  }

  /**
   * <p>Show the instance of {@link SwapMapDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link SwapMapDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link SwapMapDialog}
   */
  public static void showSwapMapDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new SwapMapDialog(parent));
    SwapMapDialog instance = instances.get(parent);

    instance.setupDialogs();
  }

  /**
   * <p>Removes the instance of the {@link SwapMapDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link SwapMapDialog}
   */
  public static void closeSwapMapDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Gets data from {@link MainGUI} and shows {@link JOptionPane}s for
   * user to select which {@link DisplayMap} and {@link Chromosome} to use
   * as the new backbone.</p>
   */
  public void setupDialogs()
  {
    maps = mainGUI.getMapNavigator().getDisplayMaps();

    selectNewBackboneMap();
  }

  /**
   * <p>Prompts the user to select a new backbone map if there are more
   * than two maps.  If there is only two maps the non-backbone map is
   * automatically selected</p>
   */
  public void selectNewBackboneMap()
  {
    // Get display maps
    DisplayMap selectedMap = mainGUI.getMapNavigator().getSelectedDisplayMap();

    if (mainGUI.getMapNavigator().getDisplayMaps().size() == 2)
    {
      logger.info("No selected map, but only two maps loaded. Assume to"
        + "to load off-backbone map as new backbone.");
      for (DisplayMap displayMap : mainGUI.getMapNavigator().getDisplayMaps())
      {
        if (displayMap.getMap() != mainGUI.getBackbone().getMap())
        {
          selectChromosome(displayMap);
          break;
        }
      }
    }
    else if (mainGUI.getMapNavigator().getDisplayMaps().size() > 2)
    {
      String mapName = null;
      Object[] mapNames = new Object[maps.size()-1];
      int i = 0;
      for (DisplayMap map : maps)
      {
        if (map.getMap() == mainGUI.getBackbone().getMap()) continue;
        else mapNames[i++] = map.getMap().getName();
      }

      // Display Dialog box for user to choose map
      String defaultMap = null;
      if (selectedMap != null)
        defaultMap = selectedMap.getMap().getName();

      mapName = (String)JOptionPane.showInputDialog(mainGUI,
        "Select off-backbone map",
        "Swap Backbones",
        JOptionPane.PLAIN_MESSAGE,
        null,
        mapNames,
        defaultMap);

      if (mapName != null)
      {
        for (DisplayMap map : maps)
        {
          if (map.getMap().getName().equals(mapName))
          {
            selectChromosome(map);
            break;
          }
        }
      }
    }
    else
    {
      logger.info("Unable to swap backbone because no off-backbone "
        + "map was selected");
      this.showMessageDialog(mainGUI,
        "Please select an off-backbone map to swap\nbefore selecting \"Swap Backbones...\" option.",
        "Error Swapping Backbones",
        this.ERROR_MESSAGE);
    }
  }

  /**
   * <p>Displays a {@link JOptionPane} with a list of {@link Chromosome}s to choose from</p>
   * @param newBB
   *    the {@link DisplayMap} object whose {@link Chromosome}s are being selected from
   */
  public void selectChromosome(DisplayMap newBB)
  {
    // Get chromosomes in current map for user to choose as backbone
    String chromName = null;
    Vector<Chromosome> chromosomes = new Vector<Chromosome>();
    for (DisplaySegment seg : newBB.getSegments())
      chromosomes.add(seg.getChromosome());
    Vector<String> chromNames = new Vector<String>();
    String initialChoice = null;

    for (Chromosome chromosome : chromosomes)
    {
      if (!chromNames.contains(chromosome.getName()))
      {
        chromNames.add(chromosome.getName());

        if (mainGUI.getMapNavigator().getSelection().getSegment() != null)
        {
          if (chromosome == mainGUI.getMapNavigator().getSelection().getSegment().getChromosome())
            initialChoice = chromosome.getName();
        }
      }
    }

    if (chromNames.size() == 1)
    {
      chromName = chromNames.firstElement();
    }
    else
    {
      Collections.sort(chromNames, new ChromosomeComparator());
      Object[] chromNameObjects = (Object[])chromNames.toArray(new Object[chromNames.size()]);

      // Display Dialog box for user to choose chromosome
      chromName = (String)this.showInputDialog(mainGUI,
          "Map Selected:\n"
          + newBB.getMap().getName()
          + "\n\nSelect Chromosome:",
          "Swap Backbones",
          this.PLAIN_MESSAGE,
          null,
          chromNameObjects,
          initialChoice);
    }

    swapBackbone(newBB, chromName);
  }

  /**
   * <p>Starts the swapping of backbone maps in</p>
   * @param map
   *    The {@link DisplayMap} object that is being loaded as the new backbone
   * @param chromosome
   *    The new backbone {@link Chromosome} name
   */
  public void swapBackbone(DisplayMap map, String chromosome)
  {
    if (chromosome != null)
    {
      loadedMaps = new Vector<DisplayMap>();
      mapErrorList = new Vector<Object>();
      annotErrorList = new Vector<Object>();

      newMainGUI = new MainGUI(mainGUI.getVCMap());
      newMainGUI.setExtendedState(mainGUI.getExtendedState());

      // Load new Backbone
      loadedMaps.addElement(map);
      lastLoadedMap = map;
      newMainGUI.loadMap(true, map.getMap(), chromosome, this);
    }
    // otherwise canceled - do nothing
  }

  /**
   * <p>Gets the {@link DisplayMap} in the new {@link MainGUI} that corresponds
   * to the {@link DisplayMap} parameter</p>
   * @param oldMap
   *    The {@link DisplayMap} from the old {@link MainGUI} that is being searched
   *    for in the new {@link MainGUI}.
   * @return
   *    A {@link DisplayMap} corresponding to the last loaded {@link DisplayMap}
   */
  public DisplayMap getNewMainGUIMap(DisplayMap oldMap)
  {
    for (DisplayMap map : newMainGUI.getMapNavigator().getDisplayMaps())
      if (map.getMap().getName().equals(oldMap.getMap().getName()))
        return map;

    return null;
  }

  /**
   * <p>Shows the errors dialog. Called if errors exist</p>
   */
  public void showErrorDialog()
  {
    if (mapErrorList.size() > 0)
    {
      String text = "No syntenic regions can be found for the following maps\n" +
      "given the currently loaded backbone map.  Synteny may exist,\n" +
      "but is not loaded into the VCMap database.\n\n";

      mapErrorList.insertElementAt(text, 0);
      mapErrorList.insertElementAt("Maps:", 1);
    }
    if (annotErrorList.size() > 0)
    {
      String text = "No annotation was loaded for the following types of annotation.\n\n";

      annotErrorList.insertElementAt(text, 0);
      annotErrorList.insertElementAt("Annotation:", 1);
    }
    Object[] optionText = new Object[mapErrorList.size() + annotErrorList.size()];

    // Store in array for JOptionPane
    int i = 0;
    for (int j = 0; j < mapErrorList.size(); j++)
    {
      optionText[i] = mapErrorList.get(j);
      i++;
    }

    for (int j = 0; j < annotErrorList.size(); j++)
    {
      optionText[i] = annotErrorList.get(j);
      i++;
    }


    JOptionPane.showMessageDialog(this, optionText, "Load Errors", JOptionPane.ERROR_MESSAGE, null);
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.callback.MapLoader#mapLoadCompleted(boolean, int, java.lang.String)
   */
  public void mapLoadCompleted(boolean successful, int messageType, String message)
  {
    if (messageType == MainGUI.SYNTENY_ERROR)
      mapErrorList.addElement("\t" + message);

    // Show JOptionPane if all maps are loaded and there are errors
    if (lastLoadedMap.getAllAnnotationSets().size() == 1 &&
        (mapErrorList.size() > 0 || annotErrorList.size() > 0) &&
        loadedMaps.size() == mainGUI.getMapNavigator().getDisplayMaps().size())
    {
      showErrorDialog();
    }

    // Load additional data only if backbone successfully loads
    if (messageType != MainGUI.BACKBONE_ERROR)
    {
      // Load All annotation for the lastLoadedMap
      if (lastLoadedMap.getAllAnnotationSets().size() > 1)
      {
        loadedAnnotSets = new Vector<AnnotationSet>();
        loadedOntology = null;

        // Add annotation that's already been added
        loadedAnnotSets.addElement(getNewMainGUIMap(lastLoadedMap).getAllAnnotationSets().firstElement());
        loadNextAnnotation();
      }
      else
      {
        loadNextMap();
      }
    }
  }

  /**
   * <p>Loads a map that hasn't been loaded already</p>
   */
  public void loadNextMap()
  {
    // Load next map
    for (DisplayMap nonBBmap : maps) // Load the rest of the maps
    {
      if (!loadedMaps.contains(nonBBmap))
      {
        loadedMaps.addElement(nonBBmap);
        lastLoadedMap = nonBBmap;
        newMainGUI.loadMap(false, nonBBmap.getMap(), "", this); // Chromosome is does not matter
        return;
      }
    }
  }

  /*
   * (non-Javadoc)
   * @see bioneos.vcmap.AnnotationLoader#annotationLoadCompleted(boolean, int, java.lang.String)
   */
  public void annotationLoadCompleted(boolean successful, int messageType, String message)
  {
    if (messageType == MainGUI.NO_DATA_LOADED_ERROR)
      annotErrorList.addElement(message);

    // If there are more load additional ontology filters
    if (loadedOntology != null && !loadedOntology.containsAll(lastLoadedMap.getOntologyFilters()))
      loadNextOntologyFilter(lastLoadedAnnotSet);
    // If all annotation has been loaded load next map
    else if (loadedAnnotSets.containsAll(lastLoadedMap.getAllAnnotationSets()))
      loadNextMap();
    // Otherwise continue loading annotation
    else
      loadNextAnnotation();
  }

  /**
   * <p>Helper method that loads {@link Annotation} for the last loaded {@link DisplayMap}
   *  that hasn't yet been loaded</p>
   */
  public void loadNextAnnotation()
  {
    for (AnnotationSet set : lastLoadedMap.getAllAnnotationSets())
    {
      // Load types that have not been loaded yet
      if (!loadedAnnotSets.contains(set))
      {
        loadedAnnotSets.addElement(set); // Add type to list of loaded types

        // Useful if Annotation other than QTL has ontology
        lastLoadedAnnotSet = set;

        if (set.getType().equals("QTL"))
        {
          // if there are no loaded ontology filters, load all ontology
          if (lastLoadedMap.getOntologyFilters().size() == 0)
          {
            newMainGUI.loadAnnotation(getNewMainGUIMap(lastLoadedMap), set, this);
          }
          else // load each individual filter
          {
            if (loadedOntology == null)
              loadedOntology = new Vector<OntologyNode>();

            loadNextOntologyFilter(set);
          }
        }
        else
        {
          newMainGUI.loadAnnotation(getNewMainGUIMap(lastLoadedMap), set, this);
        }
        return;
      }
    }
  }

  /**
   * <p>Helper method to load {@link OntologyNode} filters for a
   * {@link DisplayMap} that have not been loaded yet
   * @param set
   *    The {@link AnnotationSet} of the {@link Annotation} that is being loaded
   */
  public void loadNextOntologyFilter(AnnotationSet set)
  {
    for (OntologyNode filter : lastLoadedMap.getOntologyFilters())
    {
      if (!loadedOntology.contains(filter))
      {
        loadedOntology.addElement(filter);
        newMainGUI.loadAnnotation(getNewMainGUIMap(lastLoadedMap), set, filter, this);
        return;
      }
    }
  }
}
