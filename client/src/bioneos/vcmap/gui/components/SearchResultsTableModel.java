package bioneos.vcmap.gui.components;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

import bioneos.vcmap.gui.MapNavigator;
import bioneos.vcmap.model.Annotation;
import bioneos.vcmap.model.MapData;
import bioneos.vcmap.util.Util;

/**
 * This class defines a {@link TableModel} that stores the search results from
 * the VCMap search functionality.  In order to save memory, these search
 * results actually contain a mixture of database search results, and
 * references to local {@link Annotation} objects in the same {@link Vector}.
 * In order to support this, special care must be taken for the
 * {@link CellRenderer} and sorting.
 *
 * @author sgdavis
 */
public class SearchResultsTableModel
  extends AbstractTableModel
{
  // Reference back to MapNavigator of the MainGUI.  This is necessary so that
  // we can identify rows as "hidden" when they are not visible.
  private MapNavigator mapNavigator;

  // Constants
  private final String[] COLUMN_NAMES = {"Name", "Type", "Start", "Stop", "Units", "Chr", "Map", "Loaded"};

  // Data storage (mixed Vector)
  private Vector<Object> searchResults = new Vector<Object>();
  private Vector<int[]> localResultsCache = new Vector<int[]>();

  /**
   * Constructor needs to contain a reference back to
   */
  public SearchResultsTableModel(MapNavigator mapNav)
  {
    mapNavigator = mapNav;
  }

  /*
   * Overridden to indicate String.class (minor distinction)
   */
  public Class getColumnClass(int column)
  {
    return String.class;
  }

  /*
   * Overridden with our column count.
   */
  public int getColumnCount()
  {
    return COLUMN_NAMES.length;
  }

  /*
   * Overriden with our columns.
   */
  public String getColumnName(int c)
  {
    return COLUMN_NAMES[c];
  }

  /*
   * Overridden to properly grab from our data Vector.  This method (and the
   * opposing setValueAt() method) are the most heavily affected by the data
   * storage
   */
  public Object getValueAt(int r, int c)
  {
    if (r < 0 || c < 0) return null;
    if (r >= searchResults.size() || c >= COLUMN_NAMES.length) return null;

    Object row = searchResults.get(r);
    if (row instanceof Annotation)
    {
      Annotation a = (Annotation) row;
      switch(c)
      {
        case 0: return a.getName();
        case 1: return a.getAnnotationSet().getType();
        case 2: return Util.formatUnit(a.getStart(), a.getChromosome().getMap().getScale());
        case 3: return Util.formatUnit(a.getStop(), a.getChromosome().getMap().getScale());
        case 4: return a.getChromosome().getMap().getUnitsString();
        case 5: return a.getChromosome().getName().substring(3);
        case 6: return a.getChromosome().getMap().getName();
        // NOTE: The following has potential to cause too much of a performance
        // drain (the call to MapNavigator.isAnnotationVisible()) so we must
        // be careful to avoid this call as much as possible.  If performance
        // ever becomes an issue with this class, the hidden / visible data
        // could be cached in this class in someway at the expense of
        // additional memory usage.
        case 7: return (mapNavigator.isAnnotationVisible(a)) ? "Yes" : "Yes (Hidden)";
      }
    }
    else if (row instanceof String[])
    {
      return ((String[]) row)[c];
    }

    // Shouldn't ever happen
    return null;
  }

  /*
   * Overridden to support our data Vector.
   */
  public int getRowCount()
  {
    return searchResults.size();
  }

  /**
   * Convenience method for quickly dropping all of our data.
   */
  public void clear()
  {
    int stop = searchResults.size();
    searchResults = new Vector<Object>();
    localResultsCache = new Vector<int[]>();
    fireTableRowsDeleted(0, stop);
  }

  /**
   * Simple method to add a new row to our model.  The {@link Object} reference
   * must be either a {@link String}[] or a reference to a previously loaded
   * {@link Annotation} object.
   * @param o
   *   Either a {@link String}[8] or an {@link Annotation} object.
   */
  public void addRow(Object o)
  {
    if ((o instanceof String[] && ((String[]) o).length == COLUMN_NAMES.length))
    {
      searchResults.add(o);
    }
    else if (o instanceof Annotation)
    {
      Annotation a = (Annotation) o;
      localResultsCache.add(new int[] {a.getId(), a.getChromosome().getMap().getId()});
      searchResults.add(o);
    }
    else
    {
      throw new IllegalArgumentException("SearchResultsTableModel only supports Object[8] or Annotation objects");
    }

    fireTableRowsInserted(searchResults.size() - 1, searchResults.size() - 1);
  }

  /**
   * Helper method to access the data rows of this model.
   * @param r
   *   The index of the row to access.
   * @return
   *   The return value will be either 1) null if a bad index is specified,
   *   2) a {@link String}[] representing a result from the DB, or 3) an
   *   actual reference to an {@link Annotation} object.
   */
  public Object getRow(int r)
  {
    if (r < 0 || r >= searchResults.size()) return null;
    return searchResults.get(r);
  }

  /**
   * Convenience method to determine if this model already contains a reference
   * to an {@link Annotation} object with the same id from the same map
   * ({@link MapData}).  This is used to avoid duplicate results between the
   * local and database searches.
   * @param id
   *   The DB identifier for the annotation in question.
   * @param mapId
   *   The DB identifier for the map on which this annotation has been placed.
   * @return
   *   True iff a reference to an {@link Annotation} object with the exact same
   *   ID is already stored in this TableModel.
   */
  public boolean containsId(int id, int mapId)
  {
    for (int[] i : localResultsCache)
    {
      if (i[0] == id && i[1] == mapId) return true;
    }

    return false;
  }

  /**
   * Perform a sort based on the specified column and direction.
   * @param col
   *   The index of the column to sort upon.
   * @param asc
   *   True if an ascending sort is desired, false for descending.
   */
  public void sort(int col, boolean asc)
  {
    Collections.sort(searchResults, new SearchResultsComparator(col, asc));
    fireTableDataChanged();
  }

  /**
   * This specialized Comparator is used only to sort the data vector in the
   * {@link SearchResultsTableModel}.  It supports the special mixed data
   * types {@link Vector} that the model uses.
   * @author sgdavis
   */
  class SearchResultsComparator
    implements Comparator<Object>
  {
    int column = -1;
    int ascending = 1;
    public SearchResultsComparator(int col, boolean asc)
    {
      column = col;
      ascending = (asc) ? 1 : -1;
    }

    public boolean equals(Object o)
    {
      if (o instanceof SearchResultsComparator)
      {
        return (((SearchResultsComparator) o).column == column &&
          ((SearchResultsComparator) o).ascending == ascending);
      }
      return false;
    }

    public int compare(Object left, Object right)
    {
      // First translate Annotation objects into a string array for ease of use
      Object l = "", r = "";
      if (left instanceof String[]) l = ((String[]) left)[column];
      else
      {
        Annotation a = (Annotation) left;
        switch(column)
        {
          case 0: l = a.getName(); break;
          case 1: l = a.getAnnotationSet().toString(); break;
          case 2: l = Util.formatUnit(a.getStart(), a.getChromosome().getMap().getScale()); break;
          case 3: l = Util.formatUnit(a.getStop(), a.getChromosome().getMap().getScale()); break;
          case 4: l = a.getChromosome().getMap().getUnitsString(); break;
          case 5: l = a.getChromosome().getName().substring(3); break;
          case 6: l = a.getChromosome().getMap().getName(); break;
          case 7: l = (mapNavigator.isAnnotationVisible(a)) ? "Yes" : "Yes (Hidden)"; break;
        }
      }
      if (right instanceof String[]) r = ((String[]) right)[column];
      else
      {
        Annotation a = (Annotation) right;
        switch(column)
        {
          case 0: r = a.getName(); break;
          case 1: r = a.getAnnotationSet().toString(); break;
          case 2: r = Util.formatUnit(a.getStart(), a.getChromosome().getMap().getScale()); break;
          case 3: r = Util.formatUnit(a.getStop(), a.getChromosome().getMap().getScale()); break;
          case 4: r = a.getChromosome().getMap().getUnitsString(); break;
          case 5: r = a.getChromosome().getName().substring(3); break;
          case 6: r = a.getChromosome().getMap().getName(); break;
          case 7: r = (mapNavigator.isAnnotationVisible(a)) ? "Yes" : "Yes (Hidden)"; break;
        }
      }

      // Now perform the comparison
      if (column == 2 || column == 3 ||
          (column == 5 && ((String) l).matches("^-?(\\d+(\\.\\d+)?|\\.\\d+)") && ((String) r).matches("^-?(\\d+(\\.\\d+)?|\\.\\d+)")))
      {
        // Handle start & stop as doubles
        try
        {
          Double dL = Double.valueOf((String) l);
          Double dR = Double.valueOf((String) r);
          return ascending * dL.compareTo(dR);
        }
        catch(NumberFormatException e)
        {
          // Should never happen - just perform a String comparison
        }
      }

      // Handle everything else as a string
      return ascending * ((String) l).compareTo((String) r);
    }
  }
}
