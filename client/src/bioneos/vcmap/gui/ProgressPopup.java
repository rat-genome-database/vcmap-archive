package bioneos.vcmap.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * This a simple class that encapsulates a {@link JProgressBar} inside a
 * {@link JDialog}.
 *
 * @author dquacken
 *
 */
public class ProgressPopup
{
  private JProgressBar progressBar;
  private JLabel progressLabel;
  private JPanel progressPanel;
  private JDialog progressDialog;
  private String mapName;
  private MainGUI parent;

  /**
   * Class constructor.
   *
   * @param parent
   *   the parent {@link MainGUI}.
   * @param mapName
   *   the name that will be displayed.
   */
  public ProgressPopup(MainGUI parent, String mapName)
  {
    this.parent = parent;
    this.mapName = mapName;

    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    progressBar.setPreferredSize(new Dimension(200,20));
    progressBar.setString("loading...");
    progressBar.setStringPainted(true);
    setupProgressPopup();
  }

  public ProgressPopup(MainGUI parent, String mapName, int start, int stop)
  {
    this.parent = parent;
    this.mapName = mapName;

    progressBar = new JProgressBar(start, stop);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);

    progressBar.setPreferredSize(new Dimension(200,20));
    setupProgressPopup();
  }

  public void setupProgressPopup()
  {
    progressLabel = new JLabel(mapName);

    progressPanel = new JPanel();
    progressPanel.setBorder(BorderFactory.createLineBorder(Color.black));
    progressPanel.add(progressLabel);
    progressPanel.add(progressBar);

    progressDialog = new JDialog(parent, "Load", false);
    progressDialog.setUndecorated(true);
    progressDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    progressDialog.getContentPane().add(progressPanel, BorderLayout.CENTER);

    progressDialog.pack();

    // Center progress bar
    Point center = parent.getLocation();
    center.x += parent.getWidth() / 2;
    center.y += parent.getHeight() / 2;
    center.x -= progressDialog.getWidth() / 2;
    center.y -= progressDialog.getHeight() / 2;
    if (center.x < 0) center.x = 0;
    if (center.y < 0) center.y = 0;
    progressDialog.setLocation(center);
  }

  /**
   * Hide or show the ProgressPopup.  When a progress popup is set as visible,
   * it's parent's menu bar will become disabled and all mouse events sent to
   * the parent will be caught and dropped by the glasspane.
   *
   * @param visible
   *   True to make the ProgressPopup visible, false to hide it and re-enable
   *   the menu and mouse events for the parent of this popup.
   */
  public void setVisible(boolean visible)
  {
    progressDialog.setVisible(visible);
    if (!visible) progressDialog.dispose();
    parent.getJMenuBar().setEnabled(!visible);
    for (int menu = 0; menu < parent.getJMenuBar().getMenuCount(); menu++)
      parent.getJMenuBar().getMenu(menu).setEnabled(!visible);
    ((CustomGlassPane) parent.getGlassPane()).setRedispatchMouseEventsEnabled(!visible);
    parent.getGlassPane().setVisible(visible);
  }

  public JProgressBar getProgressBar()
  {
    return progressBar;
  }

  public void setName(String name)
  {
    this.mapName = name;
    progressLabel.setText(mapName);
    progressDialog.pack();
  }
}
