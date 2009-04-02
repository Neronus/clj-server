/*
 * $Log: Passwd.java,v $
 * Revision 1.4  2009/01/23 03:25:51  stuart
 * Fix error return on shmctl.  Support IPC_CREAT/IPC_PRIVATE in SharedMem.
 *
 * Revision 1.3  2005/12/10 02:16:08  stuart
 * Fix parsing empty field thanks to John Helewa
 *
 */
package posix;

import java.io.*;
import java.util.StringTokenizer;

/** POSIX style access to the unix passwd file.  There is
    no caching.  This gets you the most up to date data,
    but if you are looking up lots of user ids, you
    probably want to load them into a Hashtable.
    <p>
    This class is implemented in Java for the standard text passwd
    files used by unix systems.  Some unix systems replace the text format
    with an indexed
    database of some description.  This improves performance when there
    are thousands of users, but we won't be able to take advantage of
    that.  Such systems should still keep a text version in the usual
    place.

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

public class Passwd {
  private File file;
  BufferedReader rdr;	// package private for test access

  public String pw_name;
  public String pw_passwd;
  public int pw_uid;
  public int pw_gid;
  public String pw_gecos;
  public String pw_dir;
  public String pw_shell;

  /** Read the next record in the passwd file.
    @return true if we found another record
   */
  public boolean getpwent() throws IOException {
    if (rdr == null)
      setpwent();
    String line = rdr.readLine();
    if (line == null) return false;
    String[] s = new String[7];
    int pos = 0;
    for (int i = 0; i < s.length; ++i) {
      int sep = line.indexOf(':',pos);
      if (sep < 0) break;
      s[i] = line.substring(pos,sep);
      pos = sep + 1;
    }
    pw_name = s[0];
    pw_passwd = s[1];
    pw_uid = parseNum(s[2]);
    pw_gid = parseNum(s[3]);
    pw_gecos = s[4];
    pw_dir = s[5];
    pw_shell = s[6];
    return true;
  }

  private static int parseNum(String s) {
    if (s == null) return -1;
    try {
      return Integer.parseInt(s.trim());
    }
    catch (NumberFormatException n) {
      return -1;
    }
  }

  /** Access an arbitrary passwd format file. */
  public Passwd(String path) {
    file = new File(path);
  }

  /** Access the standard passwd file.  For unix, this is
      <code>/etc/passwd</code>.
   */
  public Passwd() {
    this("/etc/passwd");
  }

  public String getPath() { return file.getPath(); }

  /** Open and position the passwd file just before the first record. */
  public void setpwent() throws IOException {
    if (rdr != null)
      rdr.close();
    rdr = new BufferedReader(new FileReader(file));
  }


  /** Read the passwd record that matches a user id.
    @param uid	The user id
    @return true if the record is found
   */
  public boolean getpwuid(int uid) throws IOException {
    setpwent();
    while (getpwent())
      if (pw_uid == uid) return true;
    return false;
  }

  /** Read the first passwd record that matches a user name.
    @param nam	The user name
    @return true if the record is found
   */
  public boolean getpwnam(String nam) throws IOException {
    setpwent();
    while (getpwent())
      if (pw_name != null && pw_name.equals(nam)) return true;
    return false;
  }

  /** Update the passwd file with the current values.  This is not
    yet implemented.  A proper implementation requires the traditional
    unix text file locking mechanism (which uses a temporary output file with
    a well known name as the lock).  Traditional locking will soon be
    another feature of the posix package.
   */
  public boolean putpwent() throws IOException {
    throw new RuntimeException("Not Implemented");
  }

  /** Close the passwd file. */
  public void endpwent() {
    if (rdr != null)
      try {
	rdr.close();
      }
      catch (IOException e) { }
  }
}
