package posix;

/** Test out the signal handling classes.
 */

class trap implements SignalListener {
  trap() {
    Signal s = Signal.SIGINT;
    s.addSignalListener(this);
    s.setAction(Signal.SIG_EVT);
    s = Signal.SIGTERM;
    s.addSignalListener(this);
    s.setAction(Signal.SIG_EVT);
  }

  public void signalReceived(SignalEvent e) {
    System.err.println(e.getSource().toString() + " received");
  }

  public static void main(String[] argv) throws InterruptedException {
    new trap();
    Thread.sleep(10000);
  }
}
