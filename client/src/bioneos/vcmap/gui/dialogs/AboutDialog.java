package bioneos.vcmap.gui.dialogs;

import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.prefs.Preferences;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SpringLayout;

import org.apache.log4j.Logger;

import bioneos.vcmap.VCMap;
import bioneos.vcmap.gui.MainGUI;
import bioneos.vcmap.gui.MapNavigator;

/**
 * <p>This class displays a {@link JDialog} that shows the name of the
 * application. As well as the current version of the application, build
 * date and brief description of the application.</p>
 *
 * <p>Created on: June 20, 2008</p>
 * @author jaaseby@bioneos.com
 */

public class AboutDialog
  extends VCMDialog
  implements ActionListener
{
  // Singleton design pattern
  private static HashMap<MainGUI, AboutDialog> instances = new HashMap<MainGUI, AboutDialog>();

  // Logging and Preferences (static references)
  private static Logger logger = Logger.getLogger(VCMap.class.getName());

  // GUI Components
  private JButton close;

  /**
   * <p>Constructor for {@link AboutDialog}. Creates {@link AboutDialog}
   * from the information in the {@link MapNavigator} of the {@link MainGUI}.
   * The constructor is private so that only this class can create an instance
   * of {@link AboutDialog}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent {@link JFrame}
   */
  private AboutDialog(MainGUI parent)
  {
    super(parent, false);

    // Component setup
    String description = "<html>";
    description += "<font size=+1>";
    description += VCMap.NAME + " ";
    description += "v" + VCMap.RELEASE + ".";
    description += VCMap.FEATURE + ".";
    description += VCMap.BUGFIX + "<br>";
    description += "</font>";
    description += VCMap.FULL_NAME + "<br>";
    description += "Build: " + VCMap.BUILD + "<br>";
    description += "Build Date: " + VCMap.BUILD_DATE + "<br>";
    description += "<br>Created by Bio::Neos" + "<br>";
    description += "For the University of Iowa";
    description += "</html>";

    JLabel label = new JLabel( description);
    JLabel logo = null ;
    try
    {
      java.net.URL imageUrl = getClass().getResource("/images/vcmap-logo.png") ;
      if(imageUrl != null)
      {
        logo = new JLabel(new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageUrl))) ;
      }
    }
    catch (Exception e)
    {
      logger.warn("Error retrieving the VCMap Logo: " + e) ;
    }

    close = new JButton("Close");
    close.addActionListener(this);
    close.setEnabled(true);

    // Component Layout
    SpringLayout s = new SpringLayout();
    JPanel main = new JPanel(s);
    s.putConstraint(SpringLayout.EAST, close, -5, SpringLayout.EAST, main);
    s.putConstraint(SpringLayout.SOUTH, main, 5, SpringLayout.SOUTH, close);
    s.putConstraint(SpringLayout.NORTH, close, 10, SpringLayout.SOUTH, label);
    s.putConstraint(SpringLayout.WEST, label, 5, SpringLayout.WEST, main) ;

    if (logo == null)
    {
      s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, label);
      s.putConstraint(SpringLayout.NORTH, label, 5, SpringLayout.NORTH, main);
    }
    else
    {
      s.putConstraint(SpringLayout.EAST, main, 5, SpringLayout.EAST, logo);
      s.putConstraint(SpringLayout.NORTH, logo, 5, SpringLayout.NORTH, main) ;
      s.putConstraint(SpringLayout.NORTH, label, 5, SpringLayout.SOUTH, logo) ;
      main.add(logo) ;
    }

    main.add(label);
    main.add(close);
    setContentPane(main);

    // Final setup
    setTitle("About " + VCMap.NAME);
    setResizable(false);
    setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    pack();
    logger.debug("About Dialog assembled and displayed");
  }

  /**
   * <p>Show the instance of {@link AboutDialog} already created for a
   * specific {@link MainGUI} or creates a new instance of
   * {@link AboutDialog} if an instance does not exist for the
   * {@link MainGUI}.</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link AboutDialog}
   */
  public static void showAboutDialog(MainGUI parent)
  {
    if (instances.get(parent) == null)
      instances.put(parent, new AboutDialog(parent));
    AboutDialog instance = instances.get(parent);

    Point center = parent.getLocation();
    center.x += parent.getWidth() / 2;
    center.y += parent.getHeight() / 2;
    center.x -= instance.getWidth() / 2;
    center.y -= instance.getHeight() / 2;
    if (center.x < 0) center.x = 0;
    if (center.y < 0) center.y = 0;
    instance.setLocation(center);
    instance.setVisible(true);
  }

  /**
   * <p>Removes the instance of the {@link AboutDialog} for the
   * {@link MainGUI}</p>
   *
   * @param parent
   *   {@link MainGUI} that is the parent of the {@link AboutDialog}
   */
  public static void closeAboutDialog(MainGUI parent)
  {
    instances.remove(parent);
  }

  /*
   * (non-Javadoc)
   * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
   */
  public void actionPerformed(ActionEvent ae)
  {
    logger.debug("Setting AboutDialog to NOT visible");
    setVisible(false);
  }
}
