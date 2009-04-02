package posix;

/** A posix signal.  A posix signal represents an external event of some
sort.  For instance, <code>SIGINTR</code> means that a user interrupt signal
was raised by the tty driver - usually because the user pressed the INTR
key (typically ^C).  
<p>
Some posix signals represent internal software errors.  For
example <code>SIGBUS</code> means that a load or store was attempted with
incorrect alignment.  We do not handle these internal
events for Java - only the JVM could do something sensible with them.
Furthermore, some posix signals are probably used by the JVM itself.  For
example, <code>SIGCHLD</code> means that a process started via 
<code>Runtime.exec()</code> has terminated.  For this reason, only
selected signals are exported via static constants.
<p>
Each Signal is in one of three states.  <code>SIG_DFL</code> means that
the default action is taken when the signal is received.  This usually
means stopping the JVM process.  Whether any cleanup takes place depends
on the JVM.  <code>SIG_IGN</code> means that the signal is ignored.
<code>SIG_EVT</code> means that the signal is converted to a JDK1.1 style
Java event.

<h2>WARNING</h2>
The Signal class will not crash.  However, whether or not a given 
signal can actually be trapped is highly dependent on both OS and JVM
implementation.  When multiple threads listen to the same signal, posix
does not specify which thread gets the signal.  So do not rely on
a signal being trapped on all platforms.  This may be rectified in the
future if JNI provides some kind of signal interface, or posix provides
some way of reliably distributing signals to multiple threads.

@author <a href="mailto:stuart@bmsi.com">Stuart D. Gathman</a>
Copyright (C) 1998 Business Management Systems, Inc.
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

public class Signal {
  private final int sig;
  private int action = SIG_DFL;
  private SignalListener listener = null;

  private static int MAXSIG = 8;
  private static Signal[] s = new Signal[MAXSIG];

  /** The terminal or X session has disconnected. */
  public static final Signal SIGHUP = new Signal(0);
  /** The user has sent an interrupt. */
  public static final Signal SIGINT = new Signal(1);
  /** The user has sent a diagnostic abort. */
  public static final Signal SIGQUIT = new Signal(2);
  /** Application dependent signal. */
  public static final Signal SIGUSR1 = new Signal(3);
  /** Application dependent signal. */
  public static final Signal SIGUSR2 = new Signal(4);
  /** Paging space is low, or power failure.  */
  public static final Signal SIGDANGER = new Signal(5);
  /** The process is being terminated. */
  public static final Signal SIGTERM = new Signal(6);
  /** The terminal window has changed size. */
  public static final Signal SIGWINCH = new Signal(7);

  public static final int SIG_DFL = 0;	// default action (usually stop)
  public static final int SIG_IGN = 1;	// ignore
  public static final int SIG_EVT = 2;	// trap signal

  /** Set the signal handling action type.
    @throws IllegalStateException if the JVM already traps this signal
    @param action one of SIG_DFL,SIG_IGN,SIG_EVT
   */
  public void setAction(int action) {
    switch (action) {
    default:
      throw new IllegalArgumentException("Invalid signal action: "+action);
    case SIG_EVT:
      start();	// start signal waiting Thread
    case SIG_IGN:
    case SIG_DFL:
    }
    sigaction(sig,action);
    this.action = action;
  }

  public final int getAction() { return action; }

  public void addSignalListener(SignalListener l) {
    listener = SignalMulticaster.add(listener,l);
  }

  public void removeSignalListener(SignalListener l) {
    listener = SignalMulticaster.del(listener,l);
  }

  /** Send a SignalEvent to all listeners. */

  public void processSignal() {
    SignalListener l = listener;
    if (l != null) {
      SignalEvent e = new SignalEvent(this);
      l.signalReceived(e);
    }
  }

  static void pollSignals() {
    int i = sigwait();
    if (i < 0)
      throw new InternalError("signal mutex error");
    if (s[i] != null) {
      try { s[i].processSignal(); }
      catch (Throwable t) { t.printStackTrace(); }
    }
  }

  private static Thread signalThread = null;

  private synchronized static void start() {
    if (signalThread == null) {
      signalThread = new Thread() {
	public void run() {
	  for (;;) { pollSignals(); }
	}
      };
      signalThread.setDaemon(true);
      signalThread.start();
    }
  }

  private Signal(int sig) {
    this.sig = sig;
    s[sig] = this;
  }

  /** Wait for signals.
    @return the Java signal index of a signal that has occurred
   */
  private static native int sigwait();

  /** Map Java signal index to native signal.
   @param sig  Java signal index
   @return native signal index
   */
  private static native int sigmap(int sig);

  /** Send signal to process using posix call.
   @param pid	process id to signal
   @param sig	native signal index
   @return 0 on success or errno
   */
  public static native int kill(int pid,int sig);

  /** Send this Signal to another process.
    @param the Posix process id
   */
  public void kill(int pid) throws IPCException {
    int rc = kill(pid,sigmap(sig));
    if (rc > 0)
      throw new IPCException("kill",rc);
  }

  private static native void sigaction(int sig,int action);

  {
    System.loadLibrary("posix");
  }

}
