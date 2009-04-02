package posix;
import java.io.IOException;

/** Extend java.io.File with posix features.
  @author Stuart D. Gathman
  Copyright 2002 Business Management Systems, Inc
 */
public class File extends java.io.File {
  public File(String name) { super(name); }
  public File(String dir,String name) { super(dir,name); }
  public File(File dir,String name) { super(dir,name); }
  private final Stat s = new Stat();

  private Stat tryStat() {
    /* Fields remain 0 if stat fails. */
    s.stat(getAbsolutePath());
    return s;
  }

  /** Return the posix last accessed time (atime).
   @return the accessed time in milliseconds since 1970
   */
  public long lastAccessed() { return tryStat().atime; }
  /** Return the posix last changed time (ctime).
   @return the changed time in milliseconds since 1970
   */
  public long lastChanged() { return tryStat().ctime; }
  /** Return the posix file mode.
   @return a bitmask of posix file permissions and type
   */
  public int getMode() { return tryStat().mode; }

  /** Return the posix Stat record for the file.
   @return the posix Stat record or null 
   */
  public Stat getStat() throws IOException {
    return new Stat(getAbsolutePath());
  }
}
