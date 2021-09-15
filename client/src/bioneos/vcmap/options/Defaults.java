package bioneos.vcmap.options;

import java.awt.Color;
import java.util.HashMap;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * <p>{@link Defaults} has all of the default settings for the {@link VCMap}.
 * This class is to mainly be used for restoring any changed settings
 * to a default state and to ensure the application preferences are correctly
 * set when the application if first run. Any specific default setting may
 * be individually retrieved from this class at any time. This class is based
 * on the Default class of GANT, created by Steven G Davis, SGD.</p>
 *
 * <p>Created on: August 18th, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class Defaults
{
  // Logging Reference (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // Default General Colors
  public static final HashMap<String, Object> DEFAULTS =
    new HashMap<String, Object>()
    {
      /* Not used  */
      private static final long serialVersionUID = 1L;

      // Anonymous initializer block
      {
        put("background", Color.WHITE);
        put("overlapbox", new Color(255, 255, 200));
        put("selected", Color.BLUE.brighter());
        put("connection", new Color(198, 198, 198));
        put("flank", new Color(236, 236, 236));
        put("hiddenmap", Color.LIGHT_GRAY);
        put("header", new Color(219, 218, 218));
        put("interval", new Color(113, 181, 43));
        put("intervalborder", new Color(61, 152, 43));
        put("syntenyborder", Color.BLACK);

        // Default Segment Colors
        put("chr0", new Color(204, 204, 153));
        put("chr1", new Color(153, 102, 0));
        put("chr2", new Color(102, 102, 0));
        put("chr3", new Color(153, 153, 30));
        put("chr4", new Color(204, 0,   0));
        put("chr5", new Color(255, 0,   0));
        put("chr6", new Color(255, 0,   204));
        put("chr7", new Color(255, 204, 204));
        put("chr8", new Color(255, 153, 0));
        put("chr9", new Color(255, 204, 0));
        put("chr10", new Color(255, 255, 0));
        put("chr11", new Color(204, 255, 0));
        put("chr12", new Color(0,   255, 0));
        put("chr13", new Color(53,  128, 0));
        put("chr14", new Color(0,   0,   204));
        put("chr15", new Color(102, 153, 255));
        put("chr16", new Color(153, 204, 255));
        put("chr17", new Color(0,   255, 255));
        put("chr18", new Color(204, 255, 255));
        put("chr19", new Color(153, 0,   204));
        put("chr20", new Color(204, 51,  255));
        put("chr21", new Color(204, 153, 255));
        put("chr22", new Color(102, 102, 102));
        put("chrx", new Color(153, 153, 153));
        put("chry", new Color(204, 204, 204));
        put("chrm", new Color(204, 204, 153));
        put("chrun", new Color(121, 204, 61));
        put("chrna", new Color(255, 255, 255));

        put("strokeChromosome", 10);

        // Default Widths
        put("segmentWidth", 24);
        put("betweenMapWidth", 125);
        put("leftMarginWidth", 50);
        put("footerHeight", 10);
        put("unitsWidth", 40);
        put("featureColumnWidth", 10);
        put("thresholdFeatureColumnWidth", 3);
        put("hiddenMapWidth", 10);
        put("featureLabelColumnWidth", 50);
        put("headerHeight", 10);
        put("overlapBoxGap", 35);
        put("annotDrawingWidth", 5);
        put("labelColumnSpacing", 10);
        put("annotColumnSpacing", 10);
        put("buttonHeight", 13);
        put("overlapBoxBorderSpacing", 10);
        put("overlapBoxBetweenAnnotSpacing", 2);

        // Default Lines and Selection related items
        put("featureLineStroke", 1);
        put("unitsLineStroke", 1);
        put("selectionBoxStroke", 1);
        put("selectionBoxSpacing", 2);

        // Default Marker Colors
        put("unknown", Color.BLACK);
        put("sts", Color.BLACK);
        put("gene", Color.BLACK);
        put("qtl", Color.BLACK);

        // Shown Marker Defaults
        put("shown_unknown", true);
        put("shown_sts", true);
        put("shown_gene", true);
        put("shown_qtl", true);

        // Misc
        put("show_units", true);
        put("featureDisplayType", 0);
        put("mainWindowWidth", 640);
        put("mainWindowHeight", 480);
        put("freqUnitLabels", 5);
        put("overlapThreshold", 7);
        put("overlapThresholdGroupSize", 3);
        put("multipleElements", " Features");
        put("DebugEnabled", false);
        put("numForcedGaps", 1);
        put("maxZoomBarValue", 15);
        put("blackList", "source_internal_id;ontology;name");
        put("incForChromLabel", 3);
        put("showTutorial", true);
        put("scrollSize", 5);

        // Connection Lines
        put("shown_adjconnections", true);
        put("shown_nonadjconnections", false);
        put("shown_showconnections", true);
      }
    };

  /**
   * <p>Clear any of the settings stored in the {@link GlobalOptions} and
   * then store the default settings into the {@link GlobalOptions}.</p>
   *
   * @param options
   *   {@link GlobalOptions} to clear current settings for and replace
   *   with default {@link VCMap} settings
   */
  public static void restoreDefaults(GlobalOptions options)
  {
    // Clear old data
    if (!options.clear())
      logger.warn("While attemping to restore defaults, Unable to clear global options");

    // Loop through defaults and assign them to the globalopts
    for(String key : DEFAULTS.keySet())
      options.setOption(key, DEFAULTS.get(key));
  }
}
