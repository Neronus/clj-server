package posix;

/** Read and write memory reachable through a C ptr.  Memory access is
    bounds checked to keep it within the size of region addressed by
    the C ptr.  Only classes within the posix package can create CPtr objects,
    and the use of this class is completely safe at present.  This is
    because we know the size of, for instance, shared memory segments or
    memory allocated with C malloc. At some future date, we may need the
    ability to dereference arbitrary C pointers - those dereferences will
    be unsafe.
    <p>
    We have not yet implemented the floating and 64-bit types for get/set
    due to a lack of need.

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

public class CPtr {
  long addr;
  int size;

  /** A null CPtr value. */
  static final long NULL = getNULL();

  CPtr() { }

  CPtr(long addr,int size) {
    this.addr = addr;
    this.size = size;
  }

  private static final long getNULL() {
    System.loadLibrary("posix");
    return init();
  }

  private static native long init();

  /** Type codes for <code>alignOf()</code> and <code>sizeOf()</code>. */
  public static final int
    CBYTE_TYPE = 0,
    CSHORT_TYPE = 1,
    CINT_TYPE = 2,
    CLONG_TYPE = 3,
    CFLT_TYPE = 4,
    CDBL_TYPE = 5,
    CPTR_TYPE = 6;

  /** Get the alignment of a C type.  Can be used to compute C struct
      offsets in a mostly system independent manner.
   */
  public static native int alignOf(int type);

  /** Get the size of a C type.  Can be used to compute C struct
      offsets in a mostly system independent manner.
   */
  public static native int sizeOf(int type);

  /** Compute the offsets of a C struct one member at a time.  This
      is supposed to reflect what a C compiler would do.  I can't think
      of a better way to track C data structs with code that doesn't get
      recompiled.  A config file could do it, but would be even
      more work.  Some C compilers will do surprising things - like
      padding structs that contain only 'char' members.  They do this to
      avoid cache misses at the beginning of the struct - or to make struct
      pointers uniform on word addressable machines (e.g. PDP20). 
      You can work around this for now with the
      <code>addMember</code> method - provided you can figure out <u>when</u>
      to do so.  Please report any problems you encounter - we can add
      additional native methods, e.g. 'structAlign' to return minimum
      struct alignment.
   */
  public static class Struct {
    private int offset = 0;
    private int align = 0;	// maximum alignment in struct

    /** Initialize with the offset (within a CPtr) of the C struct.
     */
    public Struct(int offset) { this.offset = offset; }

    /** Return the offset of the next member. */
    public final int offsetOf(int type) {
      return offsetOf(type,1);
    }

    /** Return the offset of the next array member. */
    public final int offsetOf(int type,int len) {
      return addMember(len * sizeOf(type),alignOf(type) - 1);
    }

    /** Add a member by size and alignment mask.  Return the member
      offset.
     */
    public final int addMember(int size,int mask) {
      int pos = (offset + mask) & ~mask;	// align for this member
      offset = pos + size;
      if (mask > align) align = mask;
      return pos;
    }

    /** Return the offset of a nested struct. The members must have
	already been added to the nested struct so that it will have
	the proper size and alignment.
      */
    public final int offsetOf(Struct s,int cnt) {
      return addMember(cnt * s.size(),s.align);
    }

    /** Return total struct size including padding to reflect
	maximum alignment. */
    public final int size() {
      return (offset + align) & ~align;
    }
  }

  /** Copy bytes out of C memory into a Java byte array. */
  public native void copyOut(int off,byte[] ba,int pos,int cnt);
  /** Copy a Java byte array into C memory. */
  public native void copyIn(int off,byte[] ba,int pos,int cnt);
  public native byte getByte(int off);
  public native void setByte(int off,byte val);
  public native short getShort(int off);
  public native void setShort(int off,short val);
  public native int getInt(int off);
  public native void setInt(int off,int val);

  public native short getCShort(int off,int idx);
  public native void setCShort(int off,int idx,short val);
  public native int getCInt(int off,int idx);
  public native void setCInt(int off,int idx,int val);

  public short getCShort(int off) { return getCShort(off,0); }
  public void setCShort(int off,short val ) { setCShort(off,0,val); }
  public int getCInt(int off) { return getCInt(off,0); }
  public void setCInt(int off,int val) { setCInt(off,0,val); }
}
