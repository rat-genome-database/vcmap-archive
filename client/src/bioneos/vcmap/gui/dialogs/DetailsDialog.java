package bioneos.vcmap.gui.dialogs;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SpringLayout;

import org.apache.log4j.Logger;

import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.gui.components.AnnotationDetailPanel;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.Factory;

/**
 * <p>{@link DetailsDialog} displays the information for all the selected
 * {@link Annotation} one at a time.</p>
 *
 * <p>Created on: February 17, 2009</p>
 * @author jaaseby@bioneos.com
 *
 */
public class DetailsDialog
  extends VCMDialog
  implements ActionListener
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  // Singleton design pattern
  private static HashMap<MainGUI, DetailsDialog> instances = new HashMap<MainGUI, DetailsDialog>();

  private MainGUI mainGUI;

  private JButton close;
  private JPanel main;
  private JTabbedPane annotationTabs;

  private Vector<Annotation> annotation;

  public final int MAX_TABS = 100;

  private static Logger logger = Logger.getLogger(bioneos.vcmap.VCMap.class.getName());

  /**
   * <p>Constructor for {@link DetailsDialog}. Creates {@link DetailsDialog}
   * from the information in the {@link MapNavigator} of the {@link MainGUI}.
   * The constructor is private so that only this class can create an instance
   * of {@link DetailsDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link DetailsDialog}
   */
  public DetailsDialog(MainGUI parent)
  {
    super(parent, false);

    mainGUI = parent;

    // Set up tabs
    annotationTabs = new JTabbedPane();
    annotationTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

    // Set up buttons
    close = new JButton("Close");
    close.addActionListener(this);

    annotationTabs.setPreferredSize(new Dimension(250, 400));

    // Set up layout of main dialog pane
    SpringLayout s = new SpringLayout();
    main = new JPanel(s);

    s.putConstraint(SpringLayout.WEST, annotationTabs, 5, SpringLayout.WEST, main);
    s.putConstraint(SpringLayout.NORTH, annotationTabs, 5, SpringLayout.NORTH, main);
    s.putConstraint(SpringLayout.NORTH, close, 5, SpringLayout.SOUTH, annotationTabs);
    s.putConstraint(SpringLayout.EAST, close, 0, SpringLayout.EAST, annotationTabs);
    s.putConstraint(SpringLayout.SOUTH, main, 5, SpringLayout.SOUTH, close);
    s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, close);

    main.add(annotationTabs);
    main.add(close);

    setContentPane(main);
    setTitle("Annotation Details");
    setResizable(true);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    pack();

  }

  /**
   * <p>Show the instance of {@link DetailsDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link DetailsDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link DetailsDialog}
   */
  public static void showDetailsDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new DetailsDialog(parent));
    DetailsDialog instance = instances.get(parent);
    instance.annotationTabs.removeAll();

    instance.setupComponents();
    // disable the parent
    parent.setEnabled(false);
    Point center = parent.getLocation();
    center.x += parent.getWidth() / 2;
    center.y += parent.getHeight() / 2;
    center.x -= instance.getWidth() / 2;
    center.y -= instance.getHeight() / 2;
    if (center.x < 0) center.x = 0;
    if (center.y < 0) center.y = 0;
    instance.setLocation(center);
    instance.setVisible(true);
//    instance.repaint();
  }

  /**
   * <p>Removes the instance of the {@link DetailsDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link DetailsDialog}
   */
  public static void closeDetailsDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /**
   * <p>Setup the different components of the {@link DetailsDialog} so it is
   * displayed properly upon opening.</p>
   *
   */
  public void setupComponents()
  {
    this.annotation = mainGUI.getMapNavigator().getSelectedAnnotation();
    Annotation lastSelectedAnnot = mainGUI.getMapNavigator().getSelection().getLastAnnotationAdded();
    Vector<Annotation> annotationList = new Vector<Annotation>();

    annotationTabs.removeAll();

    if (this.annotation.size() > 0)
    {
      HashSet<Annotation> annotAccountedFor = new HashSet<Annotation>();

      int selectedIndex = 0;
      int i = 0;
      for (Annotation annot : annotation)
      {
        if (!annotAccountedFor.contains(annot) && i < MAX_TABS)
        {
          boolean siblingIsLastSelected = false;

          // Add annotation siblings to accounted for
          for (Annotation sibling : annot.getSiblings())
          {
            annotAccountedFor.add(sibling);
            if (sibling == lastSelectedAnnot)
            {
              annotationList.addElement(lastSelectedAnnot);
              selectedIndex = i;
              siblingIsLastSelected = true;
            }
          }

          if (!siblingIsLastSelected)
          {
            if (annot == lastSelectedAnnot)
              selectedIndex = i;

            annotationList.addElement(annot);

          }

          // Add annotation to accounted for
          annotAccountedFor.add(annot);

          i++;
        }
      }

      // Don't get AVP information for custom annotation
      Vector<Annotation> nonAVPAnnotation = new Vector<Annotation>();
      for (Annotation a : annotationList)
        if (a.getId() != -1)
          nonAVPAnnotation.addElement(a);

      // Call to load additional annotation data
      try
      {
        Factory.getAnnotationAVPInformation(nonAVPAnnotation);
      }
      catch (SQLException e)
      {
        logger.warn("There was a problem gathering AVP information: " + e);
      }

      for (Annotation annot : annotationList)
        annotationTabs.add(annot.getName(), new AnnotationDetailPanel(annotationTabs, annot, mainGUI));

      annotationTabs.setSelectedIndex(selectedIndex);
    }
    else
    {
      // Set tabs
      annotationTabs.addTab("None selected", new AnnotationDetailPanel(annotationTabs, null, mainGUI));
    }

    annotationTabs.requestFocus();
  }

  /**
   *
   */
  public void clearData()
  {
    annotationTabs.removeAll();
    annotation.removeAllElements();
  }

  /*
   * Overridden to ensure that when the DetailsDialog is visible, the parent
   * is no longer enabled, but whenever the DetailsDialog is hidden, the parent
   * is enabled again.
   * NOTE: handling clearing of tabs is done here
   */
  public void setVisible(boolean b)
  {
    if (!b) clearData();
    super.setVisible(b);
  }

  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getSource() == close)
    {
      this.setVisible(false);
    }
  }
}
