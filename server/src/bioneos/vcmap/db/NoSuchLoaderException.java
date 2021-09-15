package bioneos.vcmap.db;

public class NoSuchLoaderException extends Exception
{
  /** For serialization **/
  private static final long serialVersionUID = 1L;

  /* Default constructor */
  public NoSuchLoaderException()
  {
    super("No loader exists for this data source (no further information available)");
  }

  /* Standard constructor with message */
  public NoSuchLoaderException(String msg)
  {
    super(msg);
  }

  /* Standard constructor with message and cause*/
  public NoSuchLoaderException(String msg, Throwable cause)
  {
    super(msg, cause);
  }
}
