package bioneos.vcmap.gui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import bioneos.vcmap.gui.dialogs.SearchDialog;
import bioneos.vcmap.model.AnnotationSet;

/**
 * <p>This class creates a {@link JPanel} with self contained components.
 * It contains it's own methods, {@link ActionListener}, and {@link ItemListener}
 * which change the {@link JPanel}s contents based on the user's selections.</p>
 *
 * <p>Created on: May 28, 2010</p>
 * @author cgoodman@bioneos.com
 */
public class FilterPanel
  extends JPanel
  implements ActionListener, ItemListener, KeyListener, FocusListener
{
  /* Not used */
  private static final long serialVersionUID = 1L;

  private SearchDialog sd;

  private JButton removeFilter;
  private JComboBox filterType;
  private JPanel leftPanel;
  private JPanel rightPanel;
  private JTextField keyword;
  private JLabel lKeyword;
  private JComboBox secondarySelection;
  private SpringLayout slLeft;
  private SpringLayout slRight;
  private String oldKeyword;

  // Listeners active
  boolean secondaryListenerActive;
  boolean filterTypeListenerActive;

  private static final int BUMPER = 30;

  /**
   * <p>Constructor for the {@link FilterPanel}. Creates a new {@link FilterPanel}
   * with the default setup, just a {@link JComboBox}.</p>
   *
   * @param searchD
   *   {@link SearchDialog} that is the parent of the {@link FilterPanel}
   */
  public FilterPanel(SearchDialog searchD)
  {
    sd = searchD;

    this.setLayout(new GridLayout(1, 2));

    slLeft = new SpringLayout();
    leftPanel = new JPanel(slLeft);
    slRight = new SpringLayout();
    rightPanel = new JPanel(slRight);

    this.add(leftPanel);
    this.add(rightPanel);

    // Try to get "-" icon otherwise use text
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/remove.png");
      if(imageUrl != null)
      {
        removeFilter = new JButton(new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl)));
        removeFilter.setPreferredSize(new Dimension(16,16));
        removeFilter.setBorderPainted(false);
        removeFilter.setOpaque(false);
        removeFilter.setContentAreaFilled(false);
      }
      else
      {
        removeFilter = new JButton("-");
        removeFilter.setPreferredSize(new Dimension(50, (int) removeFilter.getPreferredSize().getHeight()));
      }
    }
    catch (Exception e)
    {
      removeFilter = new JButton("-");
      removeFilter.setPreferredSize(new Dimension(50, (int) removeFilter.getPreferredSize().getHeight()));
    }

    removeFilter.setActionCommand("RemoveFilter");
    removeFilter.addActionListener(this);
    removeFilter.setToolTipText("Remove this Filter");
    filterType = new JComboBox();
    filterType.addItem("");
    for(String str : sd.FILTER_TYPES)
      filterType.addItem(str);

    filterType.setSelectedItem("");
    filterType.addItemListener(this);

    secondaryListenerActive = true;
    filterTypeListenerActive = true;

    setDefaultFilter();
  }

  /**
   * <p>Get the string of the filter</p>
   *
   * @return
   *   {@link String} from the filterType {@link JComboBox}
   */
  public String getFilterType()
  {
    return filterType.getSelectedItem().toString();
  }

  /**
   * <p>Get the {@link JComboBox} of filters</p>
   *
   * @return
   *  {@link JComboBox} of the filter types
   */
  public JComboBox getFilterTypeBox()
  {
    return filterType;
  }

  /**
   * <p>Set the selected item in the filter type {@link JComboBox}</p>
   *
   * @param s
   *  The string to set the selection of the filter type {@link JComboBox}
   */
  public void setFilterType(String s)
  {
    filterType.setSelectedItem(s);
  }

  /**
   * <p>Sets up a default {@link FilterPanel} with a {@link JComboBox} and
   * two {@link JButton}s.</p>
   */
  private void setDefaultFilter()
  {
    leftPanel.removeAll();
    rightPanel.removeAll();

    slLeft.putConstraint(SpringLayout.WEST, removeFilter, 5, SpringLayout.WEST, leftPanel);

    slLeft.putConstraint(SpringLayout.SOUTH, filterType, BUMPER, SpringLayout.NORTH, leftPanel);
    slLeft.putConstraint(SpringLayout.EAST, filterType, -5, SpringLayout.EAST, leftPanel);
    slLeft.putConstraint(SpringLayout.WEST, filterType, 5, SpringLayout.EAST, removeFilter);

    slLeft.putConstraint(SpringLayout.SOUTH, leftPanel, 5, SpringLayout.SOUTH, filterType);

    slLeft.putConstraint(SpringLayout.SOUTH, removeFilter, -12, SpringLayout.SOUTH, leftPanel);

    leftPanel.add(removeFilter);
    leftPanel.add(filterType);

    this.setMaximumSize(new Dimension(this.getMaximumSize().width, this.getPreferredSize().height));

  }

  /**
   * <p>Get the list of available species from the {@link SearchDialog}</p>
   */
  private void setSpeciesSearch()
  {
    updateSecondarySelectionBox(sd.getSpeciesList(), "");
  }

  /**
   * <p>Get the list of available map types from the {@link SearchDialog}</p>
   */
  private void setMapTypeSearch()
  {
    updateSecondarySelectionBox(sd.getMapTypeList(), "");
  }

  /**
   * <p>Get the list of available map versions from the {@link SearchDialog} and convert them
   * to the desired string form by usign the {@link Version}'s toString method</p>
   */
  private void setMapReleaseSearch()
  {
    updateSecondarySelectionBox(sd.getVersionList(), "");
  }

  /**
   * <p>Get the list of available map annotation sets from the {@link SearchDialog} and convert them
   * to the desired string form by using the {@link AnnotationSet}'s toString method</p>
   */
  private void setAnnotTypeSearch()
  {
    updateSecondarySelectionBox(sd.getAnnotSetList(), "");
  }

  /**
   * <p>Updates the items in the secondary selection {@link JComboBox}</p>
   *
   * @param vector
   */
  public void updateSecondarySelectionBox(Vector<? extends Object> vector, String first)
  {
    Object tmp = null;

    if (secondarySelection != null) // Has already been created remove listeners and items
    {
      secondaryListenerActive = false;
      tmp = secondarySelection.getSelectedItem();
      secondarySelection.removeAllItems();
    }
    else
    {
      secondarySelection = new JComboBox(); // Create a new JComboBox
      secondarySelection.addItemListener(this);
      secondaryListenerActive = false;
    }
    secondarySelection.addItem(first);

    // Add objects to the JComboBox
    for(Object o : vector)
      secondarySelection.addItem(o);

    // Set previously selected item back to original item
    secondarySelection.setSelectedItem(tmp);

    secondaryListenerActive = true;

    // if the box has already been added repaint it
    if (this.isAncestorOf(secondarySelection))
    {
      secondarySelection.repaint();
    }
    else // Set constraints and add
    {
      slRight.putConstraint(SpringLayout.SOUTH, secondarySelection, BUMPER, SpringLayout.NORTH, rightPanel);
      slRight.putConstraint(SpringLayout.EAST, secondarySelection, -5, SpringLayout.EAST, rightPanel);
      slRight.putConstraint(SpringLayout.WEST, secondarySelection, 5, SpringLayout.WEST, rightPanel);

      slRight.putConstraint(SpringLayout.SOUTH, rightPanel, 5, SpringLayout.SOUTH, secondarySelection);

      rightPanel.add(secondarySelection);
    }

    sd.repaintPanel();
    this.repaint();
    this.revalidate();

    sd.adjustView(this);
  }

  /**
   * <p>Get the secondary selection {@link JComboBox}s string value</p>
   *
   * @return
   *   {@link String} from the secondarySelection {@link JComboBox}
   */
  public Object getSecondarySelection()
  {
    if (secondarySelection == null)
      return null;
    else
      return secondarySelection.getSelectedItem();
  }

  /**
   * <p>Add or remove the {@link ItemListener} on the {@link JComboBox} for the secondary filter selection</p>
   *
   * @param b
   *  True or False to add or remove the {@link ItemListener} respectively
   */
  public void setSecondarySelectionItemListenerEnabled(boolean b)
  {
    secondaryListenerActive = b;
  }

  /**
   * <p>Add or remove the {@link ItemListener} on the {@link JComboBox} for the filter type selection</p>
   *
   * @param b
   *  True or False to add or remove the {@link ItemListener} respectively
   */
  public void setFilterTypeItemListenerEnabled(boolean b)
  {
    filterTypeListenerActive = b;
  }

  /**
   * <p>Sets up the secondary filter type to a name search with a {@link JTextField}</p>
   */
  private void setNameSearch()
  {
    keyword = new JTextField();
    slRight.putConstraint(SpringLayout.SOUTH, keyword, BUMPER, SpringLayout.NORTH, rightPanel);
    slRight.putConstraint(SpringLayout.EAST, keyword, -5, SpringLayout.EAST, rightPanel);
    slRight.putConstraint(SpringLayout.WEST, keyword, 5, SpringLayout.WEST, rightPanel);

    lKeyword = new JLabel("Any text entered will be treated as a wildcard");
    lKeyword.setFont(new Font(lKeyword.getFont().getName(), lKeyword.getFont().getStyle(), 10));

    slRight.putConstraint(SpringLayout.EAST, lKeyword, -5, SpringLayout.EAST, rightPanel);
    slRight.putConstraint(SpringLayout.NORTH, lKeyword, 0, SpringLayout.SOUTH, keyword);
    slRight.putConstraint(SpringLayout.WEST, lKeyword, 10, SpringLayout.WEST, rightPanel);

    slRight.putConstraint(SpringLayout.SOUTH, rightPanel, 5, SpringLayout.SOUTH, lKeyword);

    // Add listeners
    keyword.addKeyListener(this);
    keyword.setActionCommand("AdvancedSearch");
    keyword.addActionListener(sd);

    this.setMaximumSize(new Dimension(this.getMaximumSize().width, this.getPreferredSize().height));

    rightPanel.add(keyword);
    rightPanel.add(lKeyword);
  }

  /**
   * <p>Sets up the secondary filter type to a location search with a {@link JTextField}</p>
   */
  private void setLocationSearch()
  {
    setNameSearch();

    lKeyword.setText("<html>Enter searches in the form <i><b>chrXX:start-stop (units)</b></i></html>");
    keyword.addFocusListener(this);

    // Set initial text and font
    Font font = new Font(keyword.getFont().getName(), Font.ITALIC, keyword.getFont().getSize());
    keyword.setForeground(Color.LIGHT_GRAY);
    keyword.setFont(font);
    keyword.setText("Ex] chr1:1002800-23006278 (bp)");
  }

  /**
   * <p>Enables or disables the filterType {@link JComboBox}</p>
   *
   * @param b
   *  True or False which enables or disables the {@link JComboBox} respectively
   */
  public void setFilterTypeEnabled(boolean b)
  {
    filterType.setEnabled(b);
  }

  /**
   * <p>Enables or disables the filterType {@link JComboBox}</p>
   *
   * @param b
   *  True or False which enables or disables the {@link JButton} respectively
   */
  public void setRemoveButtonEnabled(boolean b)
  {
    removeFilter.setEnabled(b);
  }

  /**
   * <p>Get the keyword from the keyword {@link JTextField}</p>
   *
   * @return
   *  The {@link String} keyword
   */
  public String getKeyword()
  {
    return keyword.getText();
  }

  /**
   * <p>Get the keyword {@link JTextField}</p>
   *
   * @return
   *  The {@link String} keyword
   */
  public JTextField getTextField()
  {
    return keyword;
  }

  /**
   * <p>When a opening parenthesis is typed this method adds a closing
   * parenthesis and sets the cursor in the middle</p>
   */
  public void addClosingParenthesis()
  {
    // statement needed  before function call
    // if (getFilterType().equals("Location") && e.getKeyCode() == KeyEvent.VK_9 && e.getModifiers() == KeyEvent.SHIFT_MASK)

    int pos = keyword.getCaretPosition();
    keyword.setText(oldKeyword + ")");
    keyword.setCaretPosition(pos); // Reset cursor position
    oldKeyword = keyword.getText(); // Set keyword
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
   */
  public void itemStateChanged(ItemEvent ie)
  {
    // Do nothing for events other than selection events
    if (ie.getStateChange() != ItemEvent.SELECTED)
      return;

    sd.clearTable();

    if (ie.getSource() == filterType && filterTypeListenerActive)
    {
      boolean rf = removeFilter.isEnabled(); // Store the removeFilter status

      // Re-create the panel based on the selection
      if (filterType.getSelectedItem().equals(""))
      {
        setDefaultFilter();
      }
      else
      {
        setDefaultFilter();

        if (filterType.getSelectedItem().equals("Species"))
          setSpeciesSearch();
        else if (filterType.getSelectedItem().equals("Map Type"))
          setMapTypeSearch();
        else if (filterType.getSelectedItem().equals("Map Version"))
          setMapReleaseSearch();
        else if (filterType.getSelectedItem().equals("Feature Set"))
          setAnnotTypeSearch();
        else if (filterType.getSelectedItem().equals("Feature Name"))
          setNameSearch();
        else if (filterType.getSelectedItem().equals("Location"))
          setLocationSearch();

        sd.setAddButtonEnabled();
      }

      removeFilter.setEnabled(rf); // Restore status of removeFilter JButton

      // Check to see if user is selecting a category that has already been chosen
      if (sd.matchAllIsSelected())
      {
        if (!sd.noMatchFound())
        {
          JOptionPane.showMessageDialog(null,
              "You have already selected that filter.\n" +
              "Adding another filter of the same type will\n" +
              "result in the search returning no results.\n" +
              "Please choose a different filter type or\n" +
              "select the \"Match Any\" radio button.",
              "Error!",
              JOptionPane.ERROR_MESSAGE);

          filterType.setSelectedItem(""); // Reset choice to nothing
        }

        sd.updateFilterPanelLists();
      }

      if(secondarySelection != null)
        secondarySelection.repaint();
      sd.repaintPanel();
      this.repaint();
      this.revalidate();

      sd.adjustView(this);
    }
    if (ie.getSource() == secondarySelection && secondaryListenerActive)
    {
      if (sd.matchAllIsSelected())
        sd.updateFilterPanelLists(); // update list info if match all is seleted

      sd.setAddButtonEnabled();
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  public void actionPerformed(ActionEvent ae)
  {
    if (ae.getActionCommand() == "RemoveFilter")
    {
      sd.removeFilter(this); // delete this FilterPanel
    }
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
   */
  public void keyReleased(KeyEvent e)
  {
    if (oldKeyword == null || !keyword.getText().equals(oldKeyword))
    {
      oldKeyword = keyword.getText(); // Set keyword

      sd.clearTable();
      sd.setAddButtonEnabled(); // Check to see if the add button should be enabled
    }
  }

  public void keyPressed(KeyEvent e) {}
  public void keyTyped(KeyEvent e) {}

  public void focusGained(FocusEvent e)
  {
    // Change the text back to plain if the example text is visible
    if (keyword.getForeground() == Color.LIGHT_GRAY)
    {
      Font font = new Font(keyword.getFont().getName(), Font.PLAIN, keyword.getFont().getSize());
      keyword.setForeground(Color.BLACK);
      keyword.setFont(font);
      keyword.setText("");
    }

  }

  public void focusLost(FocusEvent e)
  {
    // If there is no text set change the font and display example text in the textfield
    if (getFilterType().equals("Location") && keyword.getText().equals(""))
    {
      Font font = new Font(keyword.getFont().getName(), Font.ITALIC, keyword.getFont().getSize());
      keyword.setForeground(Color.LIGHT_GRAY);
      keyword.setFont(font);
      keyword.setText("chrXX:start-stop (units)");
    }
  }
}
