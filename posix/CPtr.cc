/* $Log: CPtr.cc,v $
/* Revision 1.2  2002/01/17 21:36:59  stuart
/* Update to Oct 2001 version.
/*

JNI implementation of CPtr.java.

Author Stuart D. Gathman <stuart@bmsi.com>

Copyright (C) 1998 Business Management Systems, Inc.  <br>

This code is distributed under the
<a href="http://www.gnu.org/copyleft/lgpl.html">
GNU Library General Public License </a>

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Library General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Library General Public License for more details.

You should have received a copy of the GNU Library General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*/

#include <stdlib.h>

#include "posix_CPtr.h"
#include "posix_Malloc.h"

static jfieldID addr;
static jfieldID size;

JNIEXPORT jlong JNICALL Java_posix_CPtr_init
  (JNIEnv *env, jclass c) {
    addr = env->GetFieldID(c,"addr","J");
    size = env->GetFieldID(c,"size","I");
    void *p = 0;
    return (jlong)p;
  }

static int throwBoundsCheck(JNIEnv *env,int i,int n) {
  char buf[80];
  sprintf(buf,"offset %d, length %d",i,n); 
  return
    env->ThrowNew(
      env->FindClass("java/lang/ArrayIndexOutOfBoundsException"),buf);
}

static void *chkRange(JNIEnv *env,jobject t,jint off,jint cnt) {
  char *ptr = (char *)env->GetLongField(t,addr);
  int sz = env->GetIntField(t,size);
  if (off + cnt > sz || cnt < 0 || off < 0) {
    throwBoundsCheck(env,off,cnt);
    return 0;
  }
  return ptr + off;
}

JNIEXPORT void JNICALL Java_posix_CPtr_copyOut
  (JNIEnv *env, jobject t, jint off, jbyteArray ba, jint pos, jint cnt) {
    jbyte *p = (jbyte *)chkRange(env,t,off,cnt);
    if (p) env->SetByteArrayRegion(ba,pos,cnt,p);
  }

JNIEXPORT void JNICALL Java_posix_CPtr_copyIn
  (JNIEnv *env, jobject t, jint off, jbyteArray ba, jint pos, jint cnt) {
    jbyte *p = (jbyte *)chkRange(env,t,off,cnt);
    if (p) env->GetByteArrayRegion(ba,pos,cnt,p);
  }

JNIEXPORT jbyte JNICALL Java_posix_CPtr_getByte
  (JNIEnv *env, jobject t, jint off) {
    jbyte *p = (jbyte *)chkRange(env,t,off,1);
    return p ? *p : 0;
  }

JNIEXPORT void JNICALL Java_posix_CPtr_setByte
  (JNIEnv *env, jobject t, jint off, jbyte b) {
    jbyte *p = (jbyte *)chkRange(env,t,off,1);
    if (p) *p = b;
  }

JNIEXPORT jshort JNICALL Java_posix_CPtr_getShort
  (JNIEnv *env, jobject t, jint off) {
    const char *cp = (const char *)chkRange(env,t,off,2);
    return cp ? (cp[0] << 8) + (cp[1] & 0xFF) : 0;
  }

JNIEXPORT void JNICALL Java_posix_CPtr_setShort
  (JNIEnv *env, jobject t, jint off, jshort val) {
    char *cp = (char *)chkRange(env,t,off,2);
    if (cp) {
      cp[0] = val >> 8;
      cp[1] = val;
    }
  }

JNIEXPORT jint JNICALL Java_posix_CPtr_getInt
  (JNIEnv *env, jobject t, jint off) {
    const char *cp = (const char *)chkRange(env,t,off,4);
    if (!cp) return 0;
    jint w0 = (cp[0] << 8) + (cp[1] & 0xFF);
    jint w1 = (cp[2] << 8) + (cp[3] & 0xFF);
    return (w0 << 16) + (w1 & 0xFFFF);
  }

JNIEXPORT void JNICALL Java_posix_CPtr_setInt
  (JNIEnv *env, jobject t, jint off, jint val) {
    char *cp = (char *)chkRange(env,t,off,4);
    if (cp) {
      cp[0] = val >> 24;
      cp[1] = val >> 16;
      cp[2] = val >> 8;
      cp[3] = val;
    }
  }

enum CType {
    CBYTE_TYPE = 0,
    CSHORT_TYPE = 1,
    CINT_TYPE = 2,
    CLONG_TYPE = 3,
    CFLT_TYPE = 4,
    CDBL_TYPE = 5,
    CPTR_TYPE = 6
  };

JNIEXPORT jint JNICALL Java_posix_CPtr_alignOf
  (JNIEnv *, jclass, jint type) {
    switch (type) {
    case CBYTE_TYPE:	return __alignof__(char);
    case CSHORT_TYPE: 	return __alignof__(short);
    case CINT_TYPE:	return __alignof__(int);
    case CLONG_TYPE:	return __alignof__(long);
    case CFLT_TYPE:	return __alignof__(float);
    case CDBL_TYPE:	return __alignof__(double);
    case CPTR_TYPE:	return __alignof__(void *);
    }
    return 0;
  }

JNIEXPORT jint JNICALL Java_posix_CPtr_sizeOf
  (JNIEnv *, jclass, jint type) {
    switch (type) {
    case CBYTE_TYPE:	return sizeof(char);
    case CSHORT_TYPE: 	return sizeof(short);
    case CINT_TYPE:	return sizeof(int);
    case CLONG_TYPE:	return sizeof(long);
    case CFLT_TYPE:	return sizeof(float);
    case CDBL_TYPE:	return sizeof(double);
    case CPTR_TYPE:	return sizeof(void *);
    }
    return 0;
  }

static void *checkPtr(JNIEnv *env,jobject t,jint off,jint i,int msk,int siz) {
  char *ptr = (char *)env->GetLongField(t,addr);
  int sz = env->GetIntField(t,size);
  if (off & --msk) {
    char buf[80];
    sprintf(buf,"offset %d, align %d",i,msk + 1); 
    env->ThrowNew(
      env->FindClass("posix/AlignmentException"),buf);
    return 0;
  }
  off += i * siz;
  if (off < 0 || off + siz > sz) {
    throwBoundsCheck(env,off,sizeof(short));
    return 0;
  }
  return ptr + off;
}

JNIEXPORT jshort JNICALL Java_posix_CPtr_getCShort
  (JNIEnv *env, jobject t, jint off, jint i) {
    short *p = (short *)checkPtr(env,t,off,i,__alignof__(short),sizeof(short));
    return p ? *p : 0;
  }

JNIEXPORT void JNICALL Java_posix_CPtr_setCShort
  (JNIEnv *env, jobject t, jint off, jint i,jshort val) {
    short *p = (short *)checkPtr(env,t,off,i,__alignof__(short),sizeof(short));
    if (p) *p = val;
  }

JNIEXPORT jint JNICALL Java_posix_CPtr_getCInt
  (JNIEnv *env, jobject t, jint off, jint i) {
    int *p = (int *)checkPtr(env,t,off,i,__alignof__(int),sizeof(int));
    return p ? *p : 0;
  }

JNIEXPORT void JNICALL Java_posix_CPtr_setCInt
  (JNIEnv *env, jobject t, jint off, jint i, jint val) {
    int *p = (int *)checkPtr(env,t,off,i,__alignof__(int),sizeof(int));
    if (p) *p = val;
  }

JNIEXPORT jlong JNICALL Java_posix_Malloc_malloc
  (JNIEnv *, jclass, jint size) {
    return (jlong)(char *)malloc(size);
  }

JNIEXPORT void JNICALL Java_posix_Malloc_free
  (JNIEnv *, jclass, jlong caddr) {
    free((void *)caddr);
  }
