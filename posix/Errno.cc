/* $Log: Errno.cc,v $
/* Revision 1.4  2007/03/20 01:15:42  stuart
/* Fix garbage errdesc on old glibc
/*
/* Revision 1.3  2002/01/21 18:52:28  stuart
/* Solaris port from  "Zabaneh, Ramzi" <ramzi.zabaneh@gs.com>
/*
/* Revision 1.2  2002/01/17 21:36:59  stuart
/* Update to Oct 2001 version.
/*

JNI implementation of Errno.java.

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

#include <string.h>
#include <errno.h>

#include "posix_Errno.h"

JNIEXPORT jint JNICALL Java_posix_Errno_getErrno
  (JNIEnv *, jclass) {
    return errno;
  }

JNIEXPORT jstring JNICALL Java_posix_Errno_strerror
  (JNIEnv *env, jclass, jint err) {
    char msg[512];
#if POSIX_STRERROR_R
    if (strerror_r(err,msg,sizeof msg) != 0)
      return env->NewStringUTF(msg);
#else
    // old glibc form of strerror_r
    char *s = strerror_r(err,msg,sizeof msg);
    if (s)
      return env->NewStringUTF(s);
#endif
    return NULL;
  }

JNIEXPORT jint JNICALL Java_posix_Errno_errno
  (JNIEnv *, jclass, jint eidx) {
    static int map[] = {
      EPERM, ENOENT, ESRCH, EINTR, EIO, EIDRM,
      ENOMSG, EFAULT, EINVAL, EACCES, E2BIG, EAGAIN, ENOMEM
    };
    if (eidx < 0 || eidx >= sizeof map / sizeof map[0])
      return -1;
    return map[eidx];
  }
