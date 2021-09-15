package bioneos.vcmap.gui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpringLayout;
import javax.swing.table.DefaultTableModel;

import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.dialogs.DetailsDialog;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.OntologyTree;
import bioneos.vcmap.util.Util;

/**
 * <p>Extends the {@link JPanel} class so seperate {@link AnnotationDetailPanel}s
 * can be created for each of the selected {@link Annotation} displayed in the
 * {@link DetailsDialog}.</p>
 * <p>Created on: July 14, 2010</p>
 * @author cgoodman
 *
 */
public class AnnotationDetailPanel
  extends JPanel
  implements MouseListener, ItemListener
{
  private JTabbedPane tabbedPane;

  private Annotation selectedAnnotation;

  private MainGUI mainGUI;

  // GUI Components
  private JLabel name;
  private JLabel alias;
  private JLabel type;
  private JLabel start;
  private JLabel stop;
  private JLabel chromosome;
  private JLabel map;
  private JComboBox siblings;
  private JTable moreDetails;
  private JLabel lSource;
  private JLabel lHomologene;

  // Constants
  public final String[] COLUMN_TITLES = {"Subject", "Description"};

  /**
   * <p>Constructor to initialize variables and call GUI setup methods</p>
   * @param parent
   *    {@link JTabbedPane} that is the parent of the {@link AnnotationDetailPanel}
   * @param annotation
   *    {@link Annotation} that the {@link AnnotationDetailPanel} represents
   * @param main
   *    {@link MainGUI} that contains the loaded {@link DisplayMaps}
   */
  public AnnotationDetailPanel(JTabbedPane parent, Annotation annotation, MainGUI main)
  {
    tabbedPane = parent; // Reference to JTabbedPane for changing tab titles

    selectedAnnotation = annotation; // Reference to annotation

    mainGUI = main; // Reference to MainGUI to open Homologene and Source links

    setupComponents();

    setAnnotationDetails(annotation);
  }

  /**
   * <p>Creates GUI components and sets their constraints.</p>
   */
  public void setupComponents()
  {
    // Local labels
    JLabel lName = new JLabel("Name:");
    JLabel lAlias = new JLabel("Alias:");
    JLabel lStart = new JLabel("Start:");
    JLabel lStop = new JLabel("Stop:");
    JLabel lChromosome = new JLabel("Chromosome:");
    JLabel lMap = new JLabel("Map:");
    JLabel lSiblings = new JLabel("Homologous Genes:");
    JLabel lType = new JLabel("Type:");
    JLabel lMore = new JLabel("More Details:");
    JLabel lExternalLinks = new JLabel("External Links:");

    // Smaller Label Font
    Font lFont = new Font(lName.getFont().getName(), Font.PLAIN, 10);

    lName.setFont(lFont);
    lAlias.setFont(lFont);
    lType.setFont(lFont);
    lStart.setFont(lFont);
    lStop.setFont(lFont);
    lChromosome.setFont(lFont);
    lMap.setFont(lFont);
    lSiblings.setFont(lFont);
    lMore.setFont(lFont);
    lExternalLinks.setFont(lFont);

    Font font = new Font(lName.getFont().getName(), Font.BOLD, 12);

    // Non local labels
    name = new JLabel();
    name.setFont(font);
    type = new JLabel();
    type.setFont(font);
    alias = new JLabel();
    alias.setFont(font);
    start = new JLabel();
    start.setFont(font);
    stop = new JLabel();
    stop.setFont(font);
    chromosome = new JLabel();
    chromosome.setFont(font);
    map = new JLabel();
    map.setFont(font);

    // Set up sibling combo box
    siblings = new JComboBox();
    siblings.setToolTipText("View details of the selected annotation's homologous genes");
    siblings.addItemListener(this);

    // Set up links
    lHomologene = new JLabel("<html><u>Homologene Link</u><html>");
    lHomologene.setFont(font);

    lSource = new JLabel("<html><u>Source Link</u><html>");
    lSource.setFont(font);

    // Disable links for custom annotation
    if (selectedAnnotation.getId() != -1)
    {
      lHomologene.setForeground(Color.BLUE);
      lSource.setForeground(Color.BLUE);
      lHomologene.setToolTipText("Open the web page for current annotation");
      lSource.setToolTipText("Open the web page for current annotation");
      setHomologeneMouseListenerEnabled(true);
      lSource.addMouseListener(this);
    }
    else
    {
      lHomologene.setForeground(Color.GRAY);
      lSource.setForeground(Color.GRAY);
      lSource.setEnabled(false);
      lHomologene.setEnabled(false);
    }

    // Set up table
    moreDetails = new JTable(new DefaultTableModel())
    {
      public boolean isCellEditable(int row, int column)
      {
        return false;
      }

      public String getToolTipText(MouseEvent e)
      {
        // NOTE: It would be nice to be able to distinguish between a cell
        //   value that does not fit in the cell boundaries and one that
        //   does (no tooltip necessary), but that would be much more work.
        Point p = e.getPoint();

        String text = (String)getValueAt(rowAtPoint(p), columnAtPoint(p));

        return text.equals("") || columnAtPoint(p) == 0 ? null : text;
      }
    };

    moreDetails.setColumnSelectionAllowed(false);
    moreDetails.setRowSelectionAllowed(false);
    moreDetails.getTableHeader().setReorderingAllowed(false);
    moreDetails.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

    for (String title : COLUMN_TITLES)
      ((DefaultTableModel)moreDetails.getModel()).addColumn(title);

    // Create scroll pane for the table
    JScrollPane moreScroll = new JScrollPane(moreDetails);
    moreDetails.setPreferredScrollableViewportSize(new Dimension(400, 150));

    SpringLayout s = new SpringLayout();
    this.setLayout(s);

    s.putConstraint(SpringLayout.WEST, lName, 5, SpringLayout.WEST, this);
    s.putConstraint(SpringLayout.NORTH, name, 5, SpringLayout.NORTH, this);
    s.putConstraint(SpringLayout.SOUTH, lName, 0, SpringLayout.SOUTH, name);

    s.putConstraint(SpringLayout.WEST, lAlias, 0, SpringLayout.WEST, lName);
    s.putConstraint(SpringLayout.NORTH, alias, 5, SpringLayout.SOUTH, name);
    s.putConstraint(SpringLayout.SOUTH, lAlias, 0, SpringLayout.SOUTH, alias);

    s.putConstraint(SpringLayout.WEST, lType, 0, SpringLayout.WEST, lAlias);
    s.putConstraint(SpringLayout.NORTH, type, 5, SpringLayout.SOUTH, alias);
    s.putConstraint(SpringLayout.SOUTH, lType, 0, SpringLayout.SOUTH, type);

    s.putConstraint(SpringLayout.WEST, lStart, 0, SpringLayout.WEST, lType);
    s.putConstraint(SpringLayout.NORTH, start, 5, SpringLayout.SOUTH, type);
    s.putConstraint(SpringLayout.SOUTH, lStart, 0, SpringLayout.SOUTH, start);

    s.putConstraint(SpringLayout.WEST, lStop, 0, SpringLayout.WEST, lStart);
    s.putConstraint(SpringLayout.NORTH, stop, 5, SpringLayout.SOUTH, start);
    s.putConstraint(SpringLayout.SOUTH, lStop, 0, SpringLayout.SOUTH, stop);

    s.putConstraint(SpringLayout.WEST, lChromosome, 0, SpringLayout.WEST, lStop);
    s.putConstraint(SpringLayout.NORTH, chromosome, 5, SpringLayout.SOUTH, stop);
    s.putConstraint(SpringLayout.SOUTH, lChromosome, 0, SpringLayout.SOUTH, chromosome);

    s.putConstraint(SpringLayout.WEST, lMap, 0, SpringLayout.WEST, lChromosome);
    s.putConstraint(SpringLayout.NORTH, map, 5, SpringLayout.SOUTH, chromosome);
    s.putConstraint(SpringLayout.SOUTH, lMap, 0, SpringLayout.SOUTH, map);

    // Data labels aligned to same source
    s.putConstraint(SpringLayout.WEST, name, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.EAST, alias, -5, SpringLayout.EAST, this);
    s.putConstraint(SpringLayout.WEST, alias, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.WEST, type, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.WEST, start, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.WEST, stop, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.WEST, chromosome, 5, SpringLayout.EAST, lChromosome);
    s.putConstraint(SpringLayout.EAST, map, -5, SpringLayout.EAST, this);
    s.putConstraint(SpringLayout.WEST, map, 5, SpringLayout.EAST, lChromosome);

    s.putConstraint(SpringLayout.NORTH, lSiblings, 15, SpringLayout.SOUTH, map);
    s.putConstraint(SpringLayout.WEST, lSiblings, 0, SpringLayout.WEST, lMap);
    s.putConstraint(SpringLayout.NORTH, siblings, 5, SpringLayout.SOUTH, lSiblings);
    s.putConstraint(SpringLayout.EAST, siblings, 0, SpringLayout.EAST, moreScroll);
    s.putConstraint(SpringLayout.WEST, siblings, 0, SpringLayout.WEST, moreScroll);

    s.putConstraint(SpringLayout.NORTH, lMore, 5, SpringLayout.SOUTH, siblings);
    s.putConstraint(SpringLayout.WEST, lMore, 0, SpringLayout.WEST, lMap);
    s.putConstraint(SpringLayout.NORTH, moreScroll, 5, SpringLayout.SOUTH, lMore);
    s.putConstraint(SpringLayout.EAST, moreScroll, -5, SpringLayout.EAST, this);
    s.putConstraint(SpringLayout.WEST, moreScroll, 0, SpringLayout.WEST, lMore);
    s.putConstraint(SpringLayout.NORTH, lExternalLinks, 5, SpringLayout.SOUTH, moreScroll);
    s.putConstraint(SpringLayout.WEST, lExternalLinks, 0, SpringLayout.WEST, lMap);

    s.putConstraint(SpringLayout.NORTH, lSource, 5, SpringLayout.SOUTH, lExternalLinks);
    s.putConstraint(SpringLayout.WEST, lSource, 5, SpringLayout.WEST, lExternalLinks);
    s.putConstraint(SpringLayout.NORTH, lHomologene, 5, SpringLayout.SOUTH, lSource);
    s.putConstraint(SpringLayout.WEST, lHomologene, 0, SpringLayout.WEST, lSource);

    s.putConstraint(SpringLayout.SOUTH, this, 5, SpringLayout.SOUTH, lHomologene);

    this.add(lName);
    this.add(name);
    this.add(lAlias);
    this.add(alias);
    this.add(lType);
    this.add(type);
    this.add(lStart);
    this.add(start);
    this.add(lStop);
    this.add(stop);
    this.add(lChromosome);
    this.add(chromosome);
    this.add(lMap);
    this.add(map);
    this.add(lSiblings);
    this.add(lExternalLinks);
    this.add(lSource);
    this.add(lHomologene);
    this.add(lMore);
    this.add(moreScroll);
    this.add(siblings);

  }

  /**
   * <p>Sets GUI components data to match the selected annotation</p>
   */
  public void setAnnotationDetails()
  {
    this.setAnnotationDetails(selectedAnnotation);
  }

  /**
   * <p>Sets GUI components data to match the specified {@link Annotation}</p>
   * @param annotation
   *    {@link Annotation} whose data will be displayed in the {@link AnnotationDetailPanel}
   */
  public void setAnnotationDetails(Annotation annotation)
  {
    if (annotation == null) return;

    selectedAnnotation = annotation;

    // Remove old data
    ((DefaultTableModel)moreDetails.getModel()).getDataVector().removeAllElements();
    siblings.removeAllItems();

    // Set labels
    name.setText(annotation.getName());
    name.setToolTipText(annotation.getName());
    type.setText(annotation.getAnnotationSet().getType());
    // Build alias label
    String aliases = "";
    String[] names = annotation.getNames();
    for (int i = 1; i < names.length; i++)
    {
      if (i > 1)
        aliases += ", ";
      aliases += names[i];
    }

    // Panel incorrectly spaces empty label
    if (!aliases.equals(""))
    {
      alias.setText(aliases);
      alias.setToolTipText(aliases);
    }
    else
      alias.setText(" ");

    start.setText(Util.formatUnit(annotation.getStart(), annotation.getChromosome().getMap().getScale()) + " "
      + annotation.getChromosome().getMap().getUnitsString());
    stop.setText(Util.formatUnit(annotation.getStop(), annotation.getChromosome().getMap().getScale()) + " "
      + annotation.getChromosome().getMap().getUnitsString());
    chromosome.setText(annotation.getChromosome().getName().substring(3));
    map.setText(annotation.getChromosome().getMap().getName());
    map.setToolTipText(map.getText());

    if (!selectedAnnotation.hasHomoloGeneId())
    {
      lHomologene.setEnabled(false);
      setHomologeneMouseListenerEnabled(false);
      lHomologene.setForeground(Color.LIGHT_GRAY);
      lHomologene.setToolTipText(null);
    }
    else if (selectedAnnotation.getId() != -1)
    {
      lHomologene.setEnabled(true);
      setHomologeneMouseListenerEnabled(true);
      lHomologene.setForeground(Color.BLUE);
      lHomologene.setToolTipText("Open the HomoloGene web page for current annotation");
    }

    // Set More details
    String[] infoKeys = annotation.getInfoKeys();

    if (infoKeys != null)
    {
      for (String key : infoKeys)
      {
        // Get the list of what should not be displayed in the moreDetails table
        HashSet<String> blacklist = getBlacklist();
        if (blacklist.contains(key))
        {
          if (key.equals("ontology"))
          {
            Vector<String> ontology = annotation.getOntology();
            String[] position = {"Top ", "Second ", "Third ", "Fourth "};
            int flag = 0;
            for (int i = 0; i < ontology.size(); i++)
            {
              String[] detail = new String[2];
              if (i == 0 && ontology.size() > 1)
              {
                if (ontology.get(0).equals(OntologyTree.ROOT_NODE))
                {
                  flag++;
                  continue;
                }
              }

              if (i > 4)
              {
                detail[0] = new Integer(i).toString() + " ";
              }
              detail[0] = position[i-flag];
              detail[0] += "Category";
              detail[1] = ontology.get(i);

              ((DefaultTableModel)moreDetails.getModel()).addRow(detail);
            }
          }
          else
          {
            continue;
          }
        }
        else
        {
          String[] detail = new String[2];

          detail[0] = key;
          detail[1] = annotation.getInfo(key);

          // Capitalized each word and remove "_" in the key
          String[] words = detail[0].split("_");
          detail[0] = "";

          for (String word : words)
          {
            String firstLetter = word.substring(0, 1);
            String remainder = word.substring(1, word.length());
            firstLetter = firstLetter.toUpperCase();

            detail[0] += firstLetter + remainder;
            if (!word.equals(words[words.length-1]))
            {
              detail[0] += " ";
            }
          }

          ((DefaultTableModel)moreDetails.getModel()).addRow(detail);
        }
      }
    }

    // Set siblings
    if (annotation.getSiblings().size() > 0)
      siblings.addItem(" ");
    else
      siblings.addItem("No Loaded Siblings");

    for (Annotation sibling : annotation.getSiblings())
    {
      StringBuilder text = new StringBuilder();
      text.append(sibling.getName());
      text.append(" - ");
      text.append(sibling.getChromosome().getName());
      text.append(" - ");
      text.append(sibling.getChromosome().getMap().getSpecies());
      text.append(" - ");
      text.append(sibling.getChromosome().getMap().getTypeString());
      siblings.addItem(text.toString());
    }

    siblings.setEnabled(siblings.getItemCount() > 1);

  }

  /**
   * <p>Get the list of keys to not display to the user in the more details
   * portion of the {@link DetailsDialog}.
   *
   */
  private HashSet<String> getBlacklist()
  {
    HashSet<String> bl = new HashSet<String>();
    String obl = mainGUI.getOptions().getStringOption("blackList");
    if (obl == null) return bl;
    else
    {
      String[] list = obl.split(";");
      for (String item : list)
        bl.add(item);
    }

    return bl;
  }

  /**
   * <p>Adds or removes a {@link MouseListener} to the Homologene link.
   * Prevents multiple {@link MouseListener}s from being added to the Homologene
   * link by removing all {@link MouseListener}s before adding one.</p>
   * @param b
   *    Adds {@link MouseListener} if true otherwise nothing is added
   */
  public void setHomologeneMouseListenerEnabled(boolean b)
  {
    for (MouseListener listener : lHomologene.getMouseListeners())
      lHomologene.removeMouseListener(listener);

    if (b)
      lHomologene.addMouseListener(this);

  }

  public void itemStateChanged(ItemEvent ie)
  {
    if (ie.getSource() == siblings)
    {
      int index = siblings.getSelectedIndex() - 1;

      if (index >= 0 && selectedAnnotation != null)
      {
        if (index < selectedAnnotation.getSiblings().size())
        {
          setAnnotationDetails(selectedAnnotation.getSiblings().get(index));
          tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), selectedAnnotation.getName());
        }
      }
    }
  }

  public void mouseReleased(MouseEvent e)
  {
    // Open appropriate URL
    if (e.getSource() == lHomologene)
      mainGUI.getMapNavigator().openHomoloGeneURL(selectedAnnotation);
    else if (e.getSource() == lSource)
      mainGUI.getMapNavigator().openAnnotationURL(selectedAnnotation);

    // Set cursor back to default
    setCursor(Cursor.getDefaultCursor());
  }

  public void mouseEntered(MouseEvent e)
  {
    // Change cursor to indicate link
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  public void mouseExited(MouseEvent e)
  {
    // Reset default cursor
    setCursor(Cursor.getDefaultCursor());
  }

  public void mouseClicked(MouseEvent e) {}
  public void mousePressed(MouseEvent e) {}

}
