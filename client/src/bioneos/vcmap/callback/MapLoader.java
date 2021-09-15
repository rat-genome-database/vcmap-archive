package bioneos.vcmap.callback;

/**
 * <p>Interface to be implemented by any class that will load
 * backbone or off backbone maps using the {@link MainGUI}</p>
 * 
 * <p>Created on: July 22, 2010</p>
 * @author cgoodman@bioneos.com
 *
 */
public interface MapLoader
{
  /**
   * <p>Method called when loading a map is completed either successfully or
   * because of a failure</p>
   * @param successful
   *    true for successful completion, otherwise false
   * @param messageType
   *    the type of success or error message, constants in the MainGUI
   * @param message
   *    the message text
   */
  public void mapLoadCompleted(boolean successful, int messageType, String message);
}