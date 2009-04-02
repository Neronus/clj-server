package posix;

/** Represent a posix message queue.

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
public class MsgQ extends IPC {
  private final static int CLONG_SIZE = CPtr.sizeOf(CPtr.CLONG_TYPE);

  public MsgQ(int key,int flag) throws IPCException {
    id = msgget(key,flag);
    if (id < 0) throw new IPCException();
    owner = (flag & IPC_CREAT) != 0;
  }

  public MsgQ(int qid) {
    id = qid;
  }

  /** Attach to an existing message queue.
   */
  public MsgQ(String path,int id) throws IPCException {
    this(ftok(path,id),0);
  }

  public static class msqid_ds extends IPC.Perm {
    msqid_ds() { }	// only we can create
    public int qnum;		// # of messages on q
    public int cbytes;		// # of bytes on q
    public int qbytes;		// max # of bytes on q
    public int lspid;		// pid of last msgsnd
    public int lrpid;		// pid of last msgrcv
    public long stime;		// last send
    public long rtime;		// last recv
    public long ctime;		// last change
  }

  public static final int MSG_NOERROR = 010000;

  // FIXME: should be private?
  public static native int msgget(int key,int flag);

  private static native int msgctl(int qid,int cmd,msqid_ds buf);
  private static native int msgsnd(int qid,int type,byte[] msg,int flag);
  /** Send a message in a C struct.
      @return 0 or errno|0x80000000
   */
  private static native int msgsnd0(int qid,long caddr,int size,int flag);
  private static native int msgrcv(int qid,int[] type,byte[] msg,int flag);
  /** Receive a message into a C struct.
      @return message text size or errno|0x80000000
   */
  private static native int msgrcv0(int qid,long cadr,int sz,int type,int flg);

  /** Send a message contained in a byte array.
      @return 0 for success, -1 for failure
   */
  public int send(int type,byte[] msg,int size,int flag) {
    if (size < msg.length) {	// FIXME: handle this in native code
      byte[] m = new byte[size];
      System.arraycopy(msg,0,m,0,size);
      msg = m;
    }
    // FIXME: loop on EINTR, throw IPCException on other errors
    return msgsnd(id,type,msg,flag);
  }

  /** Receive a message in a byte array. 
     @param mtype selects which messages to receive
     @param type returns the mtype of the message received
     @return -1 for failure, or the number of bytes stored in the byte array.
   */
  public int recv(int[] type,byte[] msg,int mtype,int flag) {
    type[0] = mtype;
    // FIXME: loop on EINTR, throw IPCException on other errors
    return msgrcv(id,type,msg,flag);
  }

  /** Send a message contained in a CPtr.  This can be convenient when
      it is necessary to build C data structures in the message to interface
      with C code.  Unlike the posix system call, the size is the actual
      size of the message - including the required mtype field as the first
      element.  

      @param msg	The message to send in native format.
      @param size	The message size including the type.
      @param flag	Options.
      @return 0 for success, Errno.EAGAIN if queue full and IPC_NOWAIT option
      		specified.
   */
  public int send(CPtr msg,int size,int flag) throws IPCException {
    synchronized (msg) {	// don't let other threads free/dispose msg!
      if (size < CLONG_SIZE || size > msg.size)
	throw new IllegalArgumentException("MsgQ: message too small");
    // FIXME: loop on EINTR, throw IPCException on other errors
      for (;;) {
	int rc = msgsnd0(id,msg.addr,size - CLONG_SIZE,flag);
	if (rc < 0) {
	  rc = rc & 0x7FFFFFFF;
	  if (rc == Errno.EINTR) continue;
	  if (rc != Errno.EAGAIN)
	    throw new IPCException("msgsnd",rc);
	}
	return rc;
      }
    }
  }

  /** Receive a message into a CPtr.  This can be convenient when the 
      message contains C data structures.  Unlike the posix system call,
      the size returned is the actual message size including the
      required mtype field as the first element.  If this call fails,
      the posix error code can be retrieved with the <code>Errno</code>
      class before any further I/O takes place in this Thread.

      @return -1 for failure, or the received message size
   */

  public int recv(CPtr msg,int type,int flag) throws IPCException {
    synchronized (msg) {	// don't let other threads free/dispose msg!
      if (msg.size < CLONG_SIZE)
	throw new IllegalArgumentException("MsgQ: message too small");
    // FIXME: loop on EINTR, throw IPCException on other errors
      for (;;) {
	int rc = msgrcv0(id,msg.addr,msg.size - CLONG_SIZE,type,flag);
	if (rc < 0) {
	  rc = rc & 0x7FFFFFFF;
	  if (rc == Errno.EINTR) continue;
	  throw new IPCException("msgrcv",rc);
	}
	return rc + CLONG_SIZE;
      }
    }
  }

  /** Remove the message queue from the system. */
  public void remove() {
    if (id >= 0)
      msgctl(id,IPC_RMID,null);
  }

  public msqid_ds getStatus() throws IPCException {
    int id = this.id;
    if (id < 0) return null;
    msqid_ds ds = new msqid_ds();
    int rc = msgctl(id,IPC_STAT,ds);
    if (rc < 0)
      throw new IPCException();
    return ds;
  }

  public Perm getPerm() throws IPCException { return getStatus(); }

}
