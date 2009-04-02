package posix;

/** Distribute a SignalEvent to multiple listeners.

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

class SignalMulticaster implements SignalListener {
  private SignalListener a, b;

  private SignalMulticaster(SignalListener a,SignalListener b) {
    this.a = a;
    this.b = b;
  }

  public void signalReceived(SignalEvent e) {
    a.signalReceived(e);
    b.signalReceived(e);
  }

  static SignalListener add(SignalListener l1,SignalListener l2) {
    if (l1 == null) return l2;
    if (l2 == null) return l1;
    return new SignalMulticaster(l1,l2);
  }

  static SignalListener del(SignalListener l1,SignalListener l2) {
    if (l1 == null || l2 == null) return l1;
    if (l1 == l2) return null;
    if (l1 instanceof SignalMulticaster) {
      SignalMulticaster l = (SignalMulticaster)l1;
      if (l.b == l2) return l.a;
      l.a = del(l.a,l2);
    }
    return l1;
  }
}
