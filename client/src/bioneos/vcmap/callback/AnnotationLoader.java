package bioneos.vcmap.callback;

/**
 * <p>Interface to be implemented by any class that will load
 * additional annotation to the {@link DisplayMap}s in the {@link MainGUI}</p>
 * 
 * <p>Created on: July 26, 2010</p>
 * @author cgoodman@bioneos.com
 *
 */
public interface AnnotationLoader
{
  /**
   * <p>Method called when loading additional annotation is completed either successfully or
   * because of a failure</p>
   * @param successful
   *    true for successful completion, otherwise false
   * @param messageType
   *    the type of success or error message, constants in the MainGUI
   * @param message
   *    the message text
   */
  public void annotationLoadCompleted(boolean successful, int messageType, String message);
}
