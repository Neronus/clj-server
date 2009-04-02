package posix;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.framework.Test;

import java.io.*;

/** Test Str behavior.
  @author Stuart D. Gathman
  Copyright (C) 2002 Business Management Systems, Inc.
 */
public class TestPasswd extends TestCase {

  public TestPasswd(String name) { super(name); }

  private static final String testdata = 
"root:x:0:0:root:/root:/bin/bash\n" +
"bms:x:500:101:Business Management Systems:/home/bms:/bin/ksh\n" +
"bms1:x:500:101::/home/bms:/bin/ksh\n";
  private final Passwd pwd = new Passwd() {
    public void setpwent() throws IOException {
      rdr = new BufferedReader(new StringReader(testdata));
    }
  };

  /** Test whether empty field is parsed correctly. */
  public void testEmpty() throws IOException {
    assertTrue(pwd.getpwnam("bms"));
    assertEquals("/home/bms",pwd.pw_dir);
    assertTrue(pwd.getpwnam("bms1"));
    assertEquals("/home/bms",pwd.pw_dir);
    assertEquals("",pwd.pw_gecos);
  }

  public static void main(String[] argv) {
    TestSuite suite = new TestSuite(TestPasswd.class);
    junit.textui.TestRunner.run(suite);
  }
}
