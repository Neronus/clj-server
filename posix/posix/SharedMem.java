package posix;

/** A Posix shared memory segment.  Shared physical memory is not supported,
    since this requires superuser privilege and is very hardware dependent.

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
public class SharedMem extends IPC {
  private CPtr cptr;

  /** Attach an existing shared memory segment. */
  public SharedMem(int key,int flag) throws IPCException { this(key,0,flag); }

  /** Attach or create a shared memory segment.
    @param key	IPC key or IPC_PRIVATE
    @param size	size of shared memory 
    @param flag	options
   */
  public SharedMem(int key,int size,int flag) throws IPCException {
    id = shmget(key,size,flag);
    if (id < 0)
      throw new IPCException("shmget, key=0x" + Integer.toHexString(key));
    if (false) {
      // done by attach()
      size = getStatus().shm_segsz;
      long addr = shmat(id,0,0);
      if (addr == -1L)
	throw new IPCException("shmat");
      cptr = new CPtr(addr,size);
    }
    owner = (flag & IPC_CREAT) != 0 || key == IPC_PRIVATE;
  }

  public SharedMem(String path,int type) throws IPCException {
    this(ftok(path,type),0);
  }

  static final int SHM_SIZE = 6;	// change the size of a segment
  static final int SHM_RDONLY = 010000;	// attach read-only (else read-write)

  static final int SHM_RND = 020000;	// round attach address to SHMLBA
  /** The modulus for attaching shared memory by logical address.  This
      is system dependent and therefore computed by the class initializer.
      While attaching shared memory to a specific address is somewhat
      system dependent, it is easily handled by configuration or property
      settings.
   */
  public static final long SHMLBA = getLBA();

  public static class shmid_ds extends IPC.Perm {
    shmid_ds() { }	// only we can create
    public int	shm_segsz;	/* segment size */
    public int	shm_lpid;	/* pid of last shmop */
    public int	shm_cpid;	/* pid of creator */
    public int	shm_nattch;	/* current # attached */
    public int	shm_cnattch;	/* in memory # attached */
    public long	shm_atime;	/* last shmat time */
    public long	shm_dtime;	/* last shmdt time */
    public long	shm_ctime;	/* last change time */
  }

  private static native long getLBA(); 
  private static native int shmctl(int id,int cmd,shmid_ds buf);
  private static native long shmat(int id,long addr,int flag);
  private static native int shmdt(long addr);
  private static native int shmget(int key,int size,int flag);

  public shmid_ds getStatus() throws IPCException {
    int id = this.id;
    if (id < 0) return null;
    shmid_ds ds = new shmid_ds();
    int rc = shmctl(id,IPC_STAT,ds);
    if (rc != 0) {
      throw new IPCException(String.format("shmctl(%d,IPC_STAT)",id),rc);
    }
    return ds;
  }

  public Perm getPerm() throws IPCException { return getStatus(); }

  public synchronized CPtr attach() throws IPCException {
    if (cptr == null) {
      int size = getStatus().shm_segsz;
      long addr = shmat(id,0,0);
      if (addr == -1L)
	throw new IPCException("shmat");
      cptr = new CPtr(addr,size);
    }
    return cptr;
  }

  public void remove() {
    if (id >= 0) {
      shmctl(id,IPC_RMID,null);
      id = -1;
    }
  }

  public synchronized void dispose() {
    if (id >= 0) {
      if (cptr != null) {
	cptr.size = 0;	// prevent further access through cptr
	shmdt(cptr.addr);
	cptr.addr = CPtr.NULL;
	cptr = null;
      }
      super.dispose();
    }
  }

}
