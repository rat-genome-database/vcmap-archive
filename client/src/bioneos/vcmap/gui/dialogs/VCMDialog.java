package bioneos.vcmap.gui.dialogs;

import javax.swing.JDialog;
import javax.swing.JFrame;

import bioneos.vcmap.gui.MainGUI;

/**
 * <p>{@link VCMDialog} should be extended by all the {@link MainGUI} dialogs.
 * This class tries to ensure that window focus is transferred as the user
 * would expect when a dialog is closed.</p>
 *
 * <p>Created on: June 1, 2009</p>
 * @author jaaseby
 */

public class VCMDialog
  extends JDialog
{
  /* Not used */
  private static final long serialVersionUID = 1L;
  private MainGUI mainGUI;

  /**
   * <p>Constructor for a non-modal {@link VCMDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  public VCMDialog(MainGUI parent)
  {
    this(parent, false);
  }

  /**
   * <p>Constructor for a modal or non-modal {@link VCMDialog}.</p>
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   * @param modal
   *   true - modal
   *   false - NOT modal
   */
  public VCMDialog(MainGUI parent, boolean modal)
  {
    super(parent, modal);

    mainGUI = parent;
  }

  /*
   * Overridden to ensure that the {@link MainGUI} is called to the front
   * to try to avoid the problem where the {@link MainGUI} gets moved back
   * when hiding a dialog.
   */
  public void setVisible(boolean b)
  {
    mainGUI.setEnabled(!b);

    // Only bring mainGUI to front if window is being hidden
    if(!b) mainGUI.toFront();

    super.setVisible(b);
  }
}
