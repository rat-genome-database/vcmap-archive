package bioneos.vcmap.gui;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

import javax.help.HelpSet;
import javax.help.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;

/**
 * Class for the VCMap walk-through Tutorial.
 * TODO - describe the magic voodoo used to make the Map.jhm file work the way
 * that we want it to using the SeeAlso section.
 */
public class Tutorial extends JFrame
  implements ItemListener, ActionListener, HyperlinkListener, ListSelectionListener
{
  // Constants
  private static final int STARTUP_WIDTH = 600 ;
  private static final int STARTUP_HEIGHT = 550 ;

  // Objects
  private static Tutorial tutorial;
  private MainGUI mainGUI;

  // GUI components
  private JPanel main;
  private JCheckBox check;
  private JButton close;
  private JEditorPane htmlPane;
  private JScrollPane htmlScroll;
  private JList seeAlso;

  // Logging
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  public Tutorial(MainGUI mg)
  {
    logger.debug("Creating Tutorial object...");

    mainGUI = mg;

    // Create main JPanel
    SpringLayout s = new SpringLayout();
    main = new JPanel(s);

    // Setup check box
    check = new JCheckBox("Show on startup?");
    check.setSelected(mainGUI.getOptions().getBooleanOption("showTutorial", true));
    check.addItemListener(this);

    // Setup close button
    close = new JButton("Close");
    close.setEnabled(true);
    close.addActionListener(this);

    // Setup html content pane
    htmlPane = new JEditorPane();
    htmlPane.setEditable(false);
    htmlPane.addHyperlinkListener(this);
    htmlScroll = new JScrollPane(htmlPane);

    // Pane to display links
    seeAlso = new JList();
    seeAlso.setVisibleRowCount(5);
    seeAlso.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    JScrollPane seeAlsoScroll = new JScrollPane(seeAlso);
    JLabel seeAlsoL = new JLabel("Related topics:");

    // Set constraints
    s.putConstraint(SpringLayout.EAST, htmlScroll, -10, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.SOUTH, htmlScroll, -5, SpringLayout.NORTH, seeAlsoL);
    s.putConstraint(SpringLayout.WEST, htmlScroll, 10, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.NORTH, htmlScroll, 10, SpringLayout.NORTH, main);
    s.putConstraint(SpringLayout.SOUTH, seeAlsoL, -5, SpringLayout.NORTH, seeAlsoScroll);
    s.putConstraint(SpringLayout.WEST, seeAlsoL, 10, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.EAST, seeAlsoScroll, 275, SpringLayout.WEST, seeAlsoScroll);
    s.putConstraint(SpringLayout.SOUTH, seeAlsoScroll, -10, SpringLayout.SOUTH, main);
    s.putConstraint(SpringLayout.WEST, seeAlsoScroll, 10, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.EAST, check, -10, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.SOUTH, check, -5, SpringLayout.NORTH, close);
    s.putConstraint(SpringLayout.EAST, close, -10, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.SOUTH, close, -10, SpringLayout.SOUTH, main);

    // Add content to panel
    main.add(htmlScroll);
    main.add(seeAlsoL);
    main.add(seeAlsoScroll);
    main.add(check);
    main.add(close);
    setContentPane(main);

    // Set location when first created
    setSize(STARTUP_WIDTH, STARTUP_HEIGHT);
    Point location = mg.getLocation();
    location.x += mg.getWidth();
    location.x -= (getWidth() + 15);
    location.y += 15;
    setLocation(location);

    // Final setup
    setTitle("VCMap Tutorial");
    setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
    setName("Tutorial");

    // Lastly check with our preferences to determine if the Tutorial should
    // appear on startup or not
    if (mainGUI.getOptions().getBooleanOption("showTutorial", true))
    {
      logger.debug("Showing tutorial on startup");
      setVisible(true);
    }
  }

  public static void showTutorial(MainGUI mg)
  {
    logger.debug("Showing Tutorial...");
    init(mg);
    tutorial.setVisible(true);
  }

  public static void init(MainGUI mg)
  {
    // Create the tutorial if needed
    if (tutorial == null)
    {
      tutorial = new Tutorial(mg);
      updatePage("home");
    }
  }

  public static void closeTutorial()
  {
    if (tutorial != null) tutorial.dispose();
  }

  public static void updatePage(String page)
  {
    // Do nothing if not yet initialized
    if (tutorial == null) return;

    // Check if this is a valid page ID
    page = "tutorial." + page;
    HelpSet hs = tutorial.mainGUI.getVCMap().getHelpBroker().getHelpSet();
    if (!hs.getCombinedMap().isValidID(page, hs))
    {
      logger.warn("Invalid tutorial map id passed to updatePage: " + page);
      return;
    }

    // Update tutorial window with new page
    try
    {
      tutorial.htmlPane.setPage(hs.getCombinedMap().getURLFromID(Map.ID.create(page, hs)));
      tutorial.updateSeeAlso(page);
      // FIXME - this is not effective!!  We need to come up with something better
      if (tutorial.isVisible()) tutorial.toFront();
    }
    catch(MalformedURLException e)
    {
      logger.warn("Badly formed URL in HelpSet Map file: " + e);
    }
    catch(IOException ioe)
    {
      logger.warn("Unable to open " + page + ": " + ioe);
    }
  }

  /*
   * (non-javadoc)
   * Private helper method to update the See Also links for the active content.
   * These links are specified by using the page ID as a prefix to the ID,
   * therefore, grab all urls from IDs starting with the specified pageId.
   */
  private void updateSeeAlso(String pageId)
  {
    // Grab the HelpSet first
    HelpSet hs = tutorial.mainGUI.getVCMap().getHelpBroker().getHelpSet();

    // Clear the See Also window
    DefaultListModel model = new DefaultListModel();
    seeAlso.setModel(model);

    // Grab all IDs
    for (Enumeration e = hs.getCombinedMap().getAllIDs(); e.hasMoreElements(); )
    {
      Map.ID mapId = (Map.ID) e.nextElement();
      if (mapId.id.startsWith(pageId + "."))
      {
        model.addElement(new MapIdListItem(mapId));
      }
    }

    // Finally highlight our current page by inspecting the loaded url
    seeAlso.removeListSelectionListener(this);
    for (int i = 0; i < model.getSize(); i++)
      if (((MapIdListItem) model.getElementAt(i)).url.equals(htmlPane.getPage()))
        seeAlso.setSelectedIndex(i);

    // And add a link to the homepage, if not already there
    if (!pageId.equals("tutorial.home"))
      model.add(0, new MapIdListItem(Map.ID.create("tutorial.home.Tutorial_Home", hs)));
    seeAlso.addListSelectionListener(this);
  }

  /*
   * (non-javadoc)
   * Concrete implementation for ActionListener interface.
   */
  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand().equals("Close"))
    {
      setVisible(false);
      logger.debug("Tutorial close button pressed.");
    }
  }

  /*
   * (non-javadoc)
   * Concrete implementation for ItemListener interface.
   */
  public void itemStateChanged(ItemEvent ie)
  {
    if (ie.getSource().equals(check))
    {
      // set user preferences to show on startup
      mainGUI.getOptions().setOption("showTutorial", new Boolean(check.isSelected()));
      logger.debug("Tutorial startup settings changed to " + check.isSelected());
    }
  }

  /*
   * (non-javadoc)
   * Concrete implementation for HyperLinkListener interface.
   */
  public void hyperlinkUpdate(HyperlinkEvent hle)
  {
    if (hle.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
    {
      try
      {
        htmlPane.setPage(hle.getURL());
      }
      catch(IOException ioe)
      {
        logger.warn("Couldn't open hyperlink: " + ioe);
      }
    }
  }

  /*
   * (non-javadoc)
   * Concrete implementation for ListSelectionListener interface.
   */
  public void valueChanged(ListSelectionEvent lse)
  {
    // Do nothing if we are in the middle of a series of events
    if (lse.getValueIsAdjusting()) return;

    // Update the current tutorial page
    if (((JList) lse.getSource()).getSelectedValue() != null)
    {
      try
      {
        htmlPane.setPage(((MapIdListItem) ((JList) lse.getSource()).getSelectedValue()).url);
      }
      catch(IOException ioe)
      {
        logger.warn("Unable to open new Tutorial page (from SeeAlso JList): " + ioe);
      }
    }
  }

  /**
   * This inner class is used to store {@link Map.ID} information in a
   * {@link JList} while still displaying the list correctly.
   */
  public class MapIdListItem
  {
    public String id = "";
    public URL url = null;

    /** MapIdListItems are built from JavaHelp {@link Map.ID} objects. */
    public MapIdListItem(Map.ID orig)
    {
      id = orig.id;
      try
      {
        url = orig.getURL();
      }
      catch (MalformedURLException e)
      {
        logger.warn("There is a problem with the URL in the Helpset for Map.ID: " + orig);
      }
    }

    /*
     * Overridden to provide a normal looking string to the default JList
     * CellRenderer implementation.
     */
    public String toString()
    {
      // Assume that all id's follow the form tutorial.<refpage>.description_of_page
      // Chop everything before the last ".", then replace "_" with " " and
      // capitalize the first letter of the string (for keys like tutorial.home)
      String pretty = id.substring(id.lastIndexOf(".") + 1).replaceAll("_", " ");
      pretty = pretty.substring(0, 1).toUpperCase() + pretty.substring(1);
      return pretty;
    }
  }
}
