package posix;

/** Represent a posix semaphore set.  Work in progress.

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
public class SemSet extends IPC {

  private static final int GETNCNT = 3;	/* get semncnt */
  private static final int GETPID  = 4;	/* get sempid */
  private static final int GETVAL  = 5;	/* get semval */
  private static final int GETALL  = 6;	/* get all semval's */
  private static final int GETZCNT = 7;	/* get semzcnt */
  private static final int SETVAL  = 8;	/* set semval */
  private static final int SETALL  = 9;	/* set all semval's */
  public static final short SEM_UNDO = (short)init();
  private int nsems;			// number of semaphores in set

  public SemSet(int key,int nsem,int flag) throws IPCException {
    this.nsems = nsem;
    id = semget(key,nsem,flag);
    if (id < 0) throw new IPCException("semget",id & 0x7fffffff);
    owner = (flag & IPC_CREAT) != 0;
  }

  public void remove() {
    if (id >= 0) {
      semctl(id,0,IPC_RMID,0);
      id = -1;
    }
  }

  /** Attach to an existing semaphore set
   */
  public SemSet(int qid) throws IPCException {
    id = qid;
    semid_ds st = getStatus();
    nsems = st.sem_nsems;
  }

  /** Attach to an existing semaphore set
   */
  public SemSet(String path,int id) throws IPCException {
    this(ftok(path,id));
  }

  public static class semid_ds extends IPC.Perm {
    semid_ds() { }	// only we can create
    long sem_otime;	// last semop time
    long sem_ctime;	// last change time
    int sem_nsems;	// # of semaphores in set
  }

  public static native int semget(int key,int num,int flag);
  private static native int semctl(int id,int semnum,int cmd,semid_ds buf);
  private static native int semctl(int id,int semnum,int cmd,short[] buf);
  private static native int semctl(int id,int semnum,int cmd,int val);
  private static native int semop(int id,short[] sema);
  private static native int init();

  public semid_ds getStatus() throws IPCException {
    int id = this.id;
    if (id < 0) return null;
    semid_ds ds = new semid_ds();
    int rc = semctl(id,0,IPC_STAT,ds);
    if (rc < 0)
      throw new IPCException("semctl",rc & 0x7fffffff);
    return ds;
  }

  public Perm getPerm() throws IPCException { return getStatus(); }

  public void semop(short[] sema) throws IPCException {
    if (sema.length % 3 != 0)
      throw new IllegalArgumentException(
        "Wrong length for SemOp array: " + sema.length);
    int rc = semop(id,sema);
    if (rc != 0)
      throw new IPCException("semop",rc & 0x7fffffff);
  }

  public int getPid(char semnum) throws IPCException {
    int pid = semctl(id,semnum,GETPID,0);
    if (pid < 0)
      throw new IPCException("semGetPid",pid & 0x7fffffff);
    return pid;
  }

  public char getNCnt(char semnum) throws IPCException {
    int ncnt = semctl(id,semnum,GETNCNT,0);
    if (ncnt < 0)
      throw new IPCException("semGetNCnt",ncnt & 0x7fffffff);
    return (char)ncnt;
  }

  public char getZCnt(char semnum) throws IPCException {
    int ncnt = semctl(id,semnum,GETZCNT,0);
    if (ncnt < 0)
      throw new IPCException("semGetNCnt",ncnt & 0x7fffffff);
    return (char)ncnt;
  }

  public short getValue(char semnum) throws IPCException {
    int val = semctl(id,semnum,GETVAL,0);
    if (val < 0)
      throw new IPCException("semGetValue",val & 0x7fffffff);
    return (short)val;
  }

  public void setValue(char semnum,short val) throws IPCException {
    int rc = semctl(id,semnum,SETVAL,val);
    if (rc < 0)
      throw new IPCException("semSetValue",rc & 0x7fffffff);
  }

  public short[] getValues() throws IPCException {
    short[] v = new short[nsems];
    int rc = semctl(id,nsems,GETALL,v);
    if (rc < 0)
      throw new IPCException("semGetValues",rc & 0x7fffffff);
    return v;
  }

  public void setValues(short[] vals) throws IPCException {
    if (vals.length != nsems)
      throw new IllegalArgumentException("Wrong length for SemVal array");
    int rc = semctl(id,nsems,SETALL,vals);
    if (rc < 0)
      throw new IPCException("semSetValues",rc & 0x7fffffff);
  }

}
