package posix;

/** Report error codes returned by posix calls.

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

public class Errno {
  public static native int getErrno();
  public static native String strerror(int errno);

  /** Return the C error code for a standardized java code. */
  private static native int errno(int eidx);

  static {
    System.loadLibrary("posix");
  }

  public final static int EPERM = errno(0);
  public final static int ENOENT = errno(1);
  public final static int ESRCH = errno(2);
  public final static int EINTR = errno(3);
  public final static int EIO = errno(4);
  public final static int EIDRM = errno(5);
  public final static int ENOMSG = errno(6);
  public final static int EFAULT = errno(7);
  public final static int EINVAL = errno(8);
  public final static int EACCES = errno(9);
  public final static int E2BIG = errno(10);
  public final static int EAGAIN = errno(11);
  public final static int ENOMEM = errno(12);

  public static String getErrdesc(int errno) {
    String msg = strerror(errno);
    if (msg == null)
      msg = "Unknown error: " + errno;
    return msg;
  }
  public static String getErrdesc() {
    return getErrdesc(getErrno());
  }

  public static void main(String[] argv) {
    for (int i = 0; i < argv.length; ++i) {
      int err = Integer.parseInt(argv[i]);
      System.out.println(Errno.getErrdesc(err));
    }
  }
}
