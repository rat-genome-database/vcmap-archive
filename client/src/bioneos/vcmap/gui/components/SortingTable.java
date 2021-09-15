package bioneos.vcmap.gui.components;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 * <p>Custom {@link JTable} that supports sorting columns by clicking on the
 * column headers.</p>
 *
 * <p>Created on: April 23, 2009</p>
 * @author jaaseby@bioneos.com
 * @author sgdavis
 *   Modified to handle the SearchResultsTableModel and to remove the
 *   inefficiencies of the copy-on-sorting algorithm previously used.
 */
public class SortingTable
  extends JTable
{
  private String previousColumn;
  private boolean ascending;
  private boolean rowSortingEnabled;
  private int prevColumnNum;
  private String prevColName;
  private static ImageIcon downArrow;
  private static ImageIcon upArrow;

  /**
   * Construct a SortingTable with a {@link DefaultTableModel} as the
   * {@link TableModel}.
   */
  public SortingTable()
  {
    this(new DefaultTableModel());
  }

  /**
   * <p>Constructor for {@link SortingTable}. Sets up the mouse event
   * handling for the table.</p>
   *
   * @param dm
   *   {@link TableModel} for the table
   */
  public SortingTable(TableModel dm)
  {
    super(dm);
    prevColumnNum = -1;
    prevColName = "";
    rowSortingEnabled = true;
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/icons/downArrow.png") ;
      if(imageUrl != null)
      {
        downArrow = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl));
      }
      imageUrl = getClass().getResource("/images/icons/upArrow.png") ;
      if(imageUrl != null)
      {
        upArrow = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl));
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    for (int i = 0 ; i < this.getColumnCount() ; i++)
    {
      this.getColumnModel().getColumn(i).setCellRenderer(new BioneosCellRenderer());
      this.getColumnModel().getColumn(i).setHeaderRenderer(new BioneosIconHeaderRenderer());
    }
    this.getTableHeader().addMouseListener(new MouseAdapter()
    {
      @SuppressWarnings("unchecked")
      public void mouseClicked(MouseEvent e)
      {
        if(rowSortingEnabled)
        {
          JTable table = ((JTableHeader) e.getSource()).getTable();

          // For ease of implementation, simply de-select the current selection
          // NOTE: in the future we may want to reverse this design decision
          // if the implementation isn't too difficult
          table.clearSelection();

          int col = table.columnAtPoint(e.getPoint());
          col = table.convertColumnIndexToModel(col);
          String colName = table.getModel().getColumnName(col);

          // Do nothing if empty column header space was selected
          if (col < 0) return;

          // Determine if ascending or descending
          if (previousColumn != colName)
            ascending = true;
          else
            ascending = !ascending;
          previousColumn = colName;

          // Support multiple table models
          if (table.getModel() instanceof DefaultTableModel)
          {
            // Handle standard table model with external Collections.sort()
            DefaultTableModel tableModel = (DefaultTableModel) table.getModel();
            Collections.sort(tableModel.getDataVector(), new SortColumn(col, ascending));
            tableModel.fireTableDataChanged();
          }
          else if (table.getModel() instanceof SearchResultsTableModel)
          {
            SearchResultsTableModel tableModel = (SearchResultsTableModel) table.getModel();
            tableModel.sort(col, ascending);
            setSortColumn(col, ascending);
          }
        }
      }
    });
  }

  /**
   * <p>Enable or disable row sorting</p>
   *
   * @param b
   *  True or false to enable or diable row sorting respectivly
   */
  public void setRowSortingEnabled(boolean b)
  {
    rowSortingEnabled = b;
  }

  /**
   * <p>Checks if row sorting is currently enabled</p>
   *
   * @return
   *  True or false, whether row sorting is enabled
   */
  public boolean isRowSortingEnabled()
  {
    return rowSortingEnabled;
  }

  /**
   * Sets the column with index to have an arrow in the direction of sort
   * @param col
   *    Integer for the column number
   * @param asc
   *    boolean for ascending value, (true for ascending, false for descending)
   */
  public void setSortColumn(int col, boolean asc)
  {
    JTable table = this.getTableHeader().getTable();
    if (prevColumnNum >= 0)
    {
      table.getColumnModel().getColumn(prevColumnNum).setHeaderValue(prevColName);
    }
    prevColumnNum = col;
    prevColName = table.getColumnName(col);
    TextAndIcon header = null;
    if (!asc)
      header = new TextAndIcon(prevColName, downArrow);
    else
      header = new TextAndIcon(prevColName, upArrow);
    table.getColumnModel().getColumn(prevColumnNum).setHeaderValue(header);
  }

  /**
   * <p>Simple {@link Comparator} that sorts a the data vector of a
   * {@link DefaultTableModel} by a specific column in ascending or descending
   * order.</p>
   *
   * @author jaaseby
   */
  public class SortColumn
    implements Comparator<Vector<Object>>
  {
    private int column;
    private int ascending;

    /**
     * <p>Constructor sets the column to sort by.</p>
     *
     * @param column
     *   int of the column to sort
     */
    public SortColumn(int column, boolean ascending)
    {
      this.column = column;
      if (ascending)
        this.ascending = 1;
      else
        this.ascending = -1;
    }

    @SuppressWarnings("unchecked")
    public int compare(Vector<Object> rowA, Vector<Object> rowB)
    {
      if (rowA == null || rowB == null || column < 0)
        return -1;
      if (column >= rowA.size() || column >= rowB.size())
        return -1;

      Object obj1 = rowA.get(column);
      Object obj2 = rowB.get(column);

      // Try as string
      if (obj1 instanceof String && obj2 instanceof String)
      {
        // Try as int first
        String str1 = ((String) obj1);
        String str2 = ((String) obj2);
        if (str1.matches("([-|\\+]?\\d+(\\.\\d+)?)"))
          if (str2.matches("([-|\\+]?\\d+(\\.\\d+)?)"))
          {
            double first = Double.parseDouble(str1);
            double second = Double.parseDouble(str2);
            return ascending * (int)(first - second);
          }

        // Compare as non-case sensitive Strings
        return ascending * str1.compareToIgnoreCase(str2);
      }

      // Try as comparable
      if (obj1 instanceof Comparable && obj2 instanceof Comparable)
        return ascending * ((Comparable)obj1).compareTo((Comparable)obj2);

      // Throw exception
      throw new ClassCastException();
    }
  }
  /**
   *
   * @author ddellspe
   *
   */
  public class BioneosIconHeaderRenderer extends JLabel implements TableCellRenderer
  {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
        boolean hasFocus, int row, int column)
    {        // Inherit the colors and font from the header component
      if (table != null)
      {
        JTableHeader header = table.getTableHeader();
        if (header != null)
        {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
        }
      }

      if (value instanceof TextAndIcon)
      {
        setIcon(((TextAndIcon)value).getIcon());
        setText(((TextAndIcon)value).getText());
        setToolTipText(((TextAndIcon) value).getText());
      }
      else
      {
        setText((value == null) ? "" : value.toString());
        setIcon(null);
        setToolTipText((value == null) ? "" : value.toString());
      }
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
      setHorizontalAlignment(JLabel.CENTER);
      setHorizontalTextPosition(JLabel.LEFT);
      return this;
    }
  }
  /**
  *
  * @author ddellspe
  *
  */
 public class BioneosCellRenderer extends JLabel implements TableCellRenderer
 {
   @Override
   public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
       boolean hasFocus, int row, int column)
   {        // Inherit the colors and font from the header component
     if (table != null)
     {
       JTableHeader header = table.getTableHeader();
       if (header != null)
       {
         setForeground(header.getForeground());
         setBackground(header.getBackground());
         setFont(header.getFont());
       }
     }

     if (value instanceof String)
     {
       setText((String)value);
       setToolTipText((String)value);
     }
     else
     {
       setText((value == null) ? "" : value.toString());
       setToolTipText((value == null) ? "" : value.toString());
     }
     setHorizontalAlignment(JLabel.LEFT);
     return this;
   }
 }
  public class TextAndIcon
  {
    private String text;
    private ImageIcon icon;
    public TextAndIcon(String text, ImageIcon icon)
    {
      this.text = text;
      this.icon = icon;
    }
    public String getText()
    {
      return text;
    }
    public ImageIcon getIcon()
    {
      return icon;
    }
  }
}
