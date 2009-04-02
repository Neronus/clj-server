package posix;

import java.io.*;

/** A simple unix style lock file.  The first process to append its
    process id owns the lock.  The lock is stale if the process id no
    longer exists.  There is a race condition when removing
    stale locks.
 */

public class LockFile {
  private java.io.File lockfile;

  private void checkPID(int mypid) throws IOException {
    BufferedReader lf = new BufferedReader(new FileReader(lockfile));
    String p = lf.readLine();
    if (p != null) {
      try {
	int pid = Integer.parseInt(p.trim());
	if (pid != mypid && IPC.isPidValid(pid))
	  throw new IOException("Valid lockfile exists: PID " + pid
	    + " - " + lockfile);
      }
      catch (NumberFormatException x) { }
    }
    lf.close();
  }

  public LockFile(String name) throws IOException {
    lockfile = new java.io.File(name);
    synchronized (LockFile.class) {
      boolean trunc = lockfile.exists();
      if (trunc)
	checkPID(0);
      // open for appending
      PrintWriter lf = new PrintWriter(new FileWriter(name,!trunc));
      int pid = IPC.pid;
      lf.println(pid);
      lf.close();
      checkPID(pid);
    }
  }

  public void finalize() {
    final java.io.File lockfile = this.lockfile;
    delete();
    if (lockfile != null)
      System.err.println("Released LockFile: " + lockfile);
  }

  public synchronized void delete() {
    if (lockfile != null) {
      lockfile.delete();
      lockfile = null;
    }
  }

  public static void main(String[] argv) throws IOException {
    LockFile[] locks = new LockFile[argv.length];
    try {
      for (int i = 0; i < argv.length; ++i) {
	locks[i] = new LockFile(argv[i]);
      }
      System.in.read();
    }
    catch (IOException x) {
      System.err.println(x);
      System.exit(1);
    }
    finally {
      for (int i = 0; i < locks.length; ++i)
	if (locks[i] != null)
	   locks[i].delete();
    }
  }
}
