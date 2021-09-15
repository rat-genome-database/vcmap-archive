package bioneos.vcmap.gui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import bioneos.vcmap.gui.dialogs.SearchDialog;

/**
 * <p>Custom renderer for the {@link SortingTable} in the {@link SearchDialog} class</p>
 *
 * <p>Created on: June 10, 2010</p>
 * @author cgoodman@bioneos.com
 *
 */
public class SearchResultsCellRenderer
  extends DefaultTableCellRenderer
{
  private Color fontColor;

  /**
   * Constructor for no arguments, default {@link Color} is gray
   */
  public SearchResultsCellRenderer()
  {
    fontColor = Color.GRAY;
  }

  /**
   * Constructor with defined {@link Color}
   *
   * @param color
   *   {@link Color} that rows of hidden annotation will be changed to
   */
  public SearchResultsCellRenderer(Color color)
  {
    fontColor = color;
  }

  /**
   * Overrides the getTableCellRendererComponent method in the {@link DefaultTableCellRenderer}
   * and changes the color of the hidden annotation search results in the {@link SearchDialog}
   */
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
    throws ArrayIndexOutOfBoundsException
  {
    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

    if ( table.getValueAt(row, 7).equals("Yes") )
      c.setFont(new Font(c.getFont().getName(), c.getFont().BOLD, c.getFont().getSize()));
    else
      c.setFont(new Font(c.getFont().getName(), c.getFont().PLAIN, c.getFont().getSize()));

    if ( table.getValueAt(row, 7).equals("Yes (Hidden)") )
      c.setForeground(fontColor);
    else
      c.setForeground(Color.BLACK);

    return c;
  }

}
