package posix;
import java.io.IOException;

/** File status record for posix systems.  The cross-platform features
  of java.io.File do not cover everything available on posix systems.
  @author Stuart D. Gathman
  Copyright 2002 Business Management Systems, Inc
 */
public class Stat {
  private static native void init();
  static {
    System.loadLibrary("posix");
    init();
  }

  /** Create a blank Stat record. */
  public Stat() { }

  /** Create a Stat record for the named file.
    @param path  a posix compliant path name for the file
   */
  public Stat(String path) throws IOException {
    int rc = stat(path);
    if (rc != 0)
      throw new IOException(path+": "+Errno.getErrdesc(rc));
  }
  /** ID of device containing a directory entry for this file. */
  public int dev;
  /** File serial number. */
  public int ino;
  /** File mode. */
  public int mode;
  /** Number of links. */
  public int nlink;
  /** User ID of the file's owner */
  public int uid;
  /** Group ID of the file's group */
  public int gid;
  /** ID of device if special file. */
  public int rdev;
  /** File size in bytes. */
  public long size;
  /** Time of last access */
  public long atime;
  /** Time of last data modification */
  public long mtime;
  /** Time of last file status change */
  public long ctime;
  /** Optimal blocksize for filesystem. */
  public int	blksize;
  /** Actual number of blocks allocated. */
  public long blocks;
  /** Fill in fields from a file path.
   @return 0 on sucess or errno on failure
   */
  public native int stat(String path);

  /** Set the process file creation mask.  Bits in this mask clear
    corresponding unix permissions when creating a new file.  This
    unix concept is not very Thread friendly, of course, so you'll need to 
    do something like:
<pre>
    synchronized (Stat.class) {
      int oldmask = Stat.umask(newmask);
      createFile();
      Stat.umask(oldmask);
    }
</pre>
   @return the previous value
  public static native int umask(int mask);
  /** Return the current process file creation mask. */
  public static native int umask();

}
