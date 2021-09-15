package bioneos.vcmap.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * <p>{@link CustomGlassPane} is a custom {@link JPanel} that redispatches
 * {@link MouseEvent}s to the appropriate source when the glass pane is
 * visible.</p>
 *
 * <p>Created on: June 1, 2009</p>
 * @author jaaseby@bioneos.com
 */

public class CustomGlassPane
  extends JPanel
  implements MouseListener, MouseMotionListener, MouseWheelListener
{
  private JScrollBar scrollBar;
  private MainGUI mainGUI;
  private StatusBar statusBar;
  private boolean redispatchEnabled;

  /**
   * <p>Default Constructor.  Initializes all the necessary variables.</p>
   *
   * @param parent
   *    {@link MainGUI} that the {@link StatusBar} is a part of
   */
  public CustomGlassPane(MainGUI parent)
  {
    super();

    mainGUI = parent;
    statusBar = mainGUI.getStatusBar();
    redispatchEnabled = true;

    setOpaque(false);

    addMouseListener(this);
    addMouseMotionListener(this);
    addMouseWheelListener(this);
  }

  /**
   * <p>Set whether the {@link MouseEvent}s will be redispatched or not.</p>
   *
   * @param b
   *   true - {@link MouseEvent}s will be redispatched
   *   false - {@link MouseEvent}s will NOT be redispatched
   */
  public void setRedispatchMouseEventsEnabled(boolean b)
  {
    redispatchEnabled = b;
  }

  public void mouseClicked(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  public void mouseEntered(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  public void mouseExited(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  public void mousePressed(MouseEvent me)
  {
    if (!redispatchEnabled) return;

    JScrollPane extendedScrollPane = statusBar.getExtendedScrollPane();
    JScrollPane qtlPopup = statusBar.getQTLPopup();

    // Either send mouse event to extended or to modify qtl popup
    if ((extendedScrollPane.isVisible() && extendedScrollPane.getBounds().contains(me.getPoint()))
      || (qtlPopup.isVisible() && qtlPopup.getBounds().contains(me.getPoint())))
    {
      redispatchMouseEvent(me);
    }
    else
    {
      qtlPopup.setVisible(false);
      extendedScrollPane.setVisible(false);
      statusBar.updateStatusBar();
      statusBar.setExtendedUpToDate(true);
      setVisible(false);
    }
  }

  public void mouseReleased(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  public void mouseMoved(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  public void mouseDragged(MouseEvent me)
  {
    redispatchMouseEvent(me);
  }

  /**
   * <p>Redispatch {@link MouseEvent}s to the appropriate components.</p>
   *
   * @param me
   *   {@link MouseEvent} that may need redispatched
   */
  private void redispatchMouseEvent(MouseEvent me)
  {
    if (!redispatchEnabled) return;

    JScrollPane extendedScrollPane = statusBar.getExtendedScrollPane();

    // Convert point
    Point spPoint = SwingUtilities.convertPoint(
      mainGUI.getGlassPane(),
      new Point(me.getX(), me.getY()),
      extendedScrollPane);

    // Check if mouse position is over a scroll bar
    if (extendedScrollPane.getVerticalScrollBar().getBounds().contains(spPoint))
      scrollBar = extendedScrollPane.getVerticalScrollBar();
    else if (extendedScrollPane.getHorizontalScrollBar().getBounds().contains(spPoint))
      scrollBar = extendedScrollPane.getHorizontalScrollBar();

    if (me.getID() == MouseEvent.MOUSE_MOVED) // Used to check if the mouse was dragged or moved
    {
      // Check if mouse position is over a scroll bar
      if (extendedScrollPane.getVerticalScrollBar().getBounds().contains(spPoint))
      {
        scrollBar = extendedScrollPane.getVerticalScrollBar();
      }
      else if (extendedScrollPane.getHorizontalScrollBar().getBounds().contains(spPoint))
      {
        scrollBar = extendedScrollPane.getHorizontalScrollBar();
      }
      else
        scrollBar = null;
    }

    if (scrollBar != null)
    {
      Point sbPoint = SwingUtilities.convertPoint(
        mainGUI.getGlassPane(),
        new Point(me.getX(), me.getY()),
        scrollBar);

      scrollBar.dispatchEvent(new MouseEvent(extendedScrollPane,
        me.getID(),
        me.getWhen(),
        me.getModifiers(),
        sbPoint.x,
        sbPoint.y,
        me.getClickCount(),
        me.isPopupTrigger()));
    }
  }

  public void mouseWheelMoved(MouseWheelEvent me)
  {
    if (!redispatchEnabled) return;

    JScrollPane extendedScrollPane = statusBar.getExtendedScrollPane();

    Point sbPoint = SwingUtilities.convertPoint(
      mainGUI.getGlassPane(),
      new Point(me.getX(), me.getY()),
      extendedScrollPane);

    extendedScrollPane.dispatchEvent(new MouseWheelEvent(extendedScrollPane,
      me.getID(),
      me.getWhen(),
      me.getModifiers(),
      sbPoint.x,
      sbPoint.y,
      me.getClickCount(),
      me.isPopupTrigger(),
      me.getScrollType(),
      me.getScrollAmount(),
      me.getWheelRotation()));
  }
}
