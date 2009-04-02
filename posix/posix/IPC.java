package posix;

/** The base class of the SysV derived posix IPC methods.

@author <a href="mailto:stuart@bmsi.com">Stuart D. Gathman</a>
Copyright (C) 1998 Business Management Systems, Inc.  <br>
<p>
This code is distributed under the
<a href="http://www.gnu.org/copyleft/lgpl.html">
GNU Library General Public License </a>
<p>
This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.
<p>
This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.
<p>
You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.

 */

public abstract class IPC {
  protected int id = -1;
  protected boolean owner;

  /* Common IPC Definitions. */                                                 

  static final int IPC_RMID = 0;
  static final int IPC_SET = 1;
  static final int IPC_STAT = 2;

  static {
    System.loadLibrary("posix");
  }

  /** The posix process id for the process of this JVM.  */
  public static final int pid = init();
  /** The effective uid of the JVM when this class was initialized.
    It is probably not a good idea to change this in Java,
    so it should be current. */
  public static final int euid = geteuid();
  /** The effective gid of the JVM when this class was initialized. */
  public static final int egid = getegid();

  public static boolean isPidValid(int pid) {
    int rc = Signal.kill(pid,0);
    return rc != Errno.ESRCH;
  }

  public int getId() { return id; }

  /** Mode bits for various IPC functions.
   */
  public static final int
    IPC_PRIVATE = 0,		// key value to create a private entry
    IPC_ALLOC = 0100000,	// entry currently allocated
    IPC_CREAT = 0001000,	// create entry if key doesn't exist
    IPC_EXCL = 0002000,		// fail if key exists
    IPC_NOWAIT = 0004000,	// error if request must wait
    IPC_CI = 0010000,		// cache inhibit on shared memory
    IPC_NOCLEAR = 0020000,	// don't clear segment on 1st attach
    IPC_PHYS = 0040000;		// shared segment is physical

  /** Return an IPC key from a pathname and an id.
   */
  public static native int ftok(String path,int id);

  /** Called when class is loaded and return process id.  */
  private static native int init();	

  private static native int geteuid();
  private static native int getegid();

  /** Permission structure for SysV IPC resources.
   */
  public static class Perm {
    public int uid;
    public int gid;
    public int cuid;
    public int cgid;
    public int mode;
    public int seq;
    public int key;
  }

  /** Return the permissions for this IPC data structure. */
  public abstract Perm getPerm() throws IPCException;

  /** Remove this IPC data structure from the system. */
  public abstract void remove();

  public synchronized void dispose() {
    if (owner) remove();
  }

  public void finalize() { dispose(); }
}
