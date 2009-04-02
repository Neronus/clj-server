/* $Log: ipc.cc,v $
/* Revision 1.10  2009/01/23 19:14:08  stuart
/* Add IPC.euid, IPC.guid, Stat.umask()
/*
/* Revision 1.9  2009/01/23 03:25:51  stuart
/* Fix error return on shmctl.  Support IPC_CREAT/IPC_PRIVATE in SharedMem.
/*
/* Revision 1.8  2006/10/31 18:53:11  stuart
/* Port to CentOS-4
/*
/* Revision 1.7  2005/12/10 02:36:27  stuart
/* Release 1.1.6
/*
/* Revision 1.6  2005/05/19 02:59:14  stuart
/* Move ipc finalizers to IPC.java.  Add javadocs, improve SemSet error handling.
/*
/* Revision 1.5  2005/05/18 04:41:45  stuart
/* Initialize sem ipc on class load.
/*
/* Revision 1.4  2005/05/18 04:35:08  stuart
/* Add Semaphore support.
/*
/* Revision 1.3  2002/09/23 16:10:20  stuart
/* Compile on RedHat 7.2
/*
/* Revision 1.2  2002/01/17 21:36:59  stuart
/* Update to Oct 2001 version.
/*

JNI implementation of Java classes derived from IPC.java.

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

#include <pthread.h>
#include <stdlib.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/shm.h>
#include <sys/sem.h>
#include <unistd.h>
#include <errno.h>
#include "posix_MsgQ.h"
#include "posix_IPC.h"
#include "posix_SharedMem.h"
#include "posix_SemSet.h"

static const int cmdmap[] = {
  IPC_RMID,
  IPC_SET,
  IPC_STAT,
  GETNCNT,	/* get semncnt */
  GETPID,	/* get sempid */
  GETVAL,	/* get semval */
  GETALL,	/* get all semval's */
  GETZCNT,	/* get semzcnt */
  SETVAL,	/* set semval */
  SETALL	/* set all semval's */
};

#ifndef HAVE_MSGBUF
/** This is no longer defined for posix compiles beginning with RH7. */
struct msgbuf {
  long mtype;
  char mtext[1];
};
  
#endif

class jIPC_perm {
  jfieldID uid,gid,cuid,cgid,mode; //,seq,key;
public:
  void init(JNIEnv *env) {
    jclass c = env->FindClass("posix/IPC$Perm");
    uid = env->GetFieldID(c,"uid","I");
    gid = env->GetFieldID(c,"gid","I");
    cuid = env->GetFieldID(c,"cuid","I");
    cgid = env->GetFieldID(c,"cgid","I");
    mode = env->GetFieldID(c,"mode","I");
    //seq = env->GetFieldID(c,"seq","I");
    //key = env->GetFieldID(c,"key","I");
  }
  /** Get ipc perm data from java space */
  void getperm(JNIEnv *env,ipc_perm *p,jobject msg_perm) {
    p->uid = env->GetIntField(msg_perm,uid);
    p->gid = env->GetIntField(msg_perm,gid);
    p->cuid = env->GetIntField(msg_perm,cuid);
    p->cgid = env->GetIntField(msg_perm,cgid);
    p->mode = env->GetIntField(msg_perm,mode);
    //p->seq = env->GetIntField(msg_perm,seq);
    //p->key = env->GetIntField(msg_perm,key);
  }
  /** Set Java ipc perm data */
  void setperm(JNIEnv *env,const ipc_perm *p,jobject msg_perm) {
    env->SetIntField(msg_perm,uid,p->uid);
    env->SetIntField(msg_perm,gid,p->gid);
    env->SetIntField(msg_perm,cuid,p->cuid);
    env->SetIntField(msg_perm,cgid,p->cgid);
    env->SetIntField(msg_perm,mode,p->mode);
    //env->SetIntField(msg_perm,seq,p->seq);
    //env->SetIntField(msg_perm,key,p->key);
  }
};

static jIPC_perm perm;

class jmsqid_ds {
  jfieldID qnum,cbytes,qbytes,lspid,lrpid,stime,rtime,ctime;
public:
  void init(JNIEnv *env) {
    jclass c = env->FindClass("posix/MsgQ$msqid_ds");
    qnum = env->GetFieldID(c,"qnum","I");
    cbytes = env->GetFieldID(c,"cbytes","I");
    qbytes = env->GetFieldID(c,"qbytes","I");
    lspid = env->GetFieldID(c,"lspid","I");
    lrpid = env->GetFieldID(c,"lrpid","I");
    stime = env->GetFieldID(c,"stime","J");
    rtime = env->GetFieldID(c,"rtime","J");
    ctime = env->GetFieldID(c,"ctime","J");
  }
  void getmsqid(JNIEnv *env,msqid_ds *mbuf,jobject obj) {
    ::perm.getperm(env,&mbuf->msg_perm,obj);
  }
  void setmsqid(JNIEnv *env,const msqid_ds *mbuf,jobject obj) {
    ::perm.setperm(env,&mbuf->msg_perm,obj);
    env->SetIntField(obj,qnum,mbuf->msg_qnum);
    env->SetIntField(obj,cbytes,mbuf->msg_cbytes);
    env->SetIntField(obj,qbytes,mbuf->msg_qbytes);
    env->SetIntField(obj,lspid,mbuf->msg_lspid);
    env->SetIntField(obj,lrpid,mbuf->msg_lrpid);
    env->SetLongField(obj,stime,(jlong)mbuf->msg_stime * 1000);
    env->SetLongField(obj,rtime,(jlong)mbuf->msg_rtime * 1000);
    env->SetLongField(obj,ctime,(jlong)mbuf->msg_ctime * 1000);
  }
};

static jmsqid_ds ds;


JNIEXPORT jint JNICALL Java_posix_MsgQ_msgget
  (JNIEnv *env, jclass, jint key, jint flg) {
    int rc = msgget(key,flg);
    return rc;
  }

JNIEXPORT jint JNICALL Java_posix_MsgQ_msgctl
  (JNIEnv *env, jclass c, jint qid, jint cmd, jobject buf) {
    struct msqid_ds mbuf;
    int rc;
    if (buf != 0) {
      if (cmd == 1) {
	ds.getmsqid(env,&mbuf,buf);
	rc = msgctl(qid,IPC_SET,&mbuf);
      }
      else if (cmd == 2) {
	rc = msgctl(qid,IPC_STAT,&mbuf);
	ds.setmsqid(env,&mbuf,buf);
      }
    }
    else
      rc = msgctl(qid,cmd,0);
    return rc;
  }

JNIEXPORT jint JNICALL Java_posix_MsgQ_msgsnd
  (JNIEnv *env, jclass, jint qid, jint mtype, jbyteArray ba, jint flag) {
    int msize = env->GetArrayLength(ba);
    struct msgbuf *mbuf = (struct msgbuf *)alloca(sizeof *mbuf + msize - 1);
    if (mbuf != 0) {
      env->GetByteArrayRegion(ba,0,msize,(jbyte *)mbuf->mtext);
      mbuf->mtype = mtype;
    }
    int rc = msgsnd(qid,mbuf,msize,flag);
    return rc;
  }

JNIEXPORT jint JNICALL Java_posix_MsgQ_msgsnd0
  (JNIEnv *, jclass, jint qid, jlong caddr, jint size, jint flag) {
    jint rc = msgsnd(qid,(void *)caddr,size,flag);
    return (rc < 0) ? (errno | 0x80000000) : rc;
  }

JNIEXPORT jint JNICALL Java_posix_MsgQ_msgrcv
  (JNIEnv *env, jclass, jint qid, jintArray ia, jbyteArray ba, jint flag) {
    int msize = env->GetArrayLength(ba);
    jint mtype;
    struct msgbuf *mbuf = (struct msgbuf *)alloca(sizeof *mbuf + msize - 1);
    env->GetIntArrayRegion(ia,0,1,&mtype);
    mbuf->mtype = mtype;
    int rc = msgrcv(qid,mbuf,msize,mbuf->mtype,flag);
    mtype = mbuf->mtype;
    env->SetIntArrayRegion(ia,0,1,&mtype);
    if (rc > 0)
      env->SetByteArrayRegion(ba,0,rc,(jbyte *)mbuf->mtext);
    return rc;
  }

JNIEXPORT jint JNICALL Java_posix_MsgQ_msgrcv0
  (JNIEnv *env, jclass, jint qid, jlong caddr, jint sz, jint typ, jint f) {
      jint rc = msgrcv(qid,(void *)caddr,sz,typ,f);
    return (rc < 0) ? (errno | 0x80000000) : rc;
  }

JNIEXPORT jint JNICALL Java_posix_IPC_ftok
  (JNIEnv *env, jclass, jstring s, jint id) {
    if (s == 0) return id;
    const char *path = env->GetStringUTFChars(s,0);
    long key = ftok(path,id);
    env->ReleaseStringUTFChars(s,path);
    return key;
  }

class jshmid_ds {
  jfieldID segsz,lpid,cpid,nattch,atime,dtime,ctime;
public:
  void init(JNIEnv *env) {
    jclass c = env->FindClass("posix/SharedMem$shmid_ds");
    segsz = env->GetFieldID(c,"shm_segsz","I");
    lpid = env->GetFieldID(c,"shm_lpid","I");
    cpid = env->GetFieldID(c,"shm_cpid","I");
    nattch = env->GetFieldID(c,"shm_nattch","I");
    //cnattch = env->GetFieldID(c,"shm_cnattch","I");
    atime = env->GetFieldID(c,"shm_atime","J");
    dtime = env->GetFieldID(c,"shm_dtime","J");
    ctime = env->GetFieldID(c,"shm_ctime","J");
  }
  void getshmid(JNIEnv *env,shmid_ds *mbuf,jobject obj) {
    ::perm.getperm(env,&mbuf->shm_perm,obj);
  }
  void setshmid(JNIEnv *env,const shmid_ds *mbuf,jobject obj) {
    ::perm.setperm(env,&mbuf->shm_perm,obj);
    env->SetIntField(obj,segsz,mbuf->shm_segsz);
    env->SetIntField(obj,lpid,mbuf->shm_lpid);
    env->SetIntField(obj,cpid,mbuf->shm_cpid);
    env->SetIntField(obj,nattch,mbuf->shm_nattch);
    //env->SetIntField(obj,cnattch,mbuf->shm_cnattch);
    env->SetLongField(obj,atime,(jlong)mbuf->shm_atime * 1000);
    env->SetLongField(obj,dtime,(jlong)mbuf->shm_dtime * 1000);
    env->SetLongField(obj,ctime,(jlong)mbuf->shm_ctime * 1000);
  }
};

static jshmid_ds shmds;

JNIEXPORT jlong JNICALL Java_posix_SharedMem_getLBA
  (JNIEnv *env, jclass) {
    shmds.init(env);
    return SHMLBA;
  }

JNIEXPORT jint JNICALL Java_posix_SharedMem_shmctl
  (JNIEnv *env, jclass, jint id, jint cmd, jobject obj) {
    shmid_ds buf;
    int rc = -1;
    if (obj != 0) {
      if (cmd == 1) {
	shmds.getshmid(env,&buf,obj);
	rc = shmctl(id,IPC_SET,&buf);
      }
      else if (cmd == 2) {
	rc = shmctl(id,IPC_STAT,&buf);
	shmds.setshmid(env,&buf,obj);
      }
    }
    else if (cmd == 0)
      rc = shmctl(id,IPC_RMID,0);
    return (rc < 0) ? errno : rc;
  }

JNIEXPORT jlong JNICALL Java_posix_SharedMem_shmat
  (JNIEnv *, jclass, jint id, jlong caddr, jint flag) {
    return (jlong)shmat(id,(void *)caddr,flag);
  }

JNIEXPORT jint JNICALL Java_posix_SharedMem_shmdt
  (JNIEnv *, jclass, jlong caddr) {
    return shmdt((void *)caddr);
  }

JNIEXPORT jint JNICALL Java_posix_SharedMem_shmget
  (JNIEnv *, jclass, jint key, jint size, jint flag) {
    return shmget(key,size,flag);
  }

JNIEXPORT jint JNICALL Java_posix_IPC_init
  (JNIEnv *env, jclass ) {
    perm.init(env);
    ds.init(env);
    return getpid();
  }

JNIEXPORT jint JNICALL Java_posix_IPC_geteuid
  (JNIEnv *, jclass ) { return geteuid(); }

JNIEXPORT jint JNICALL Java_posix_IPC_getegid
  (JNIEnv *, jclass) { return getegid(); }

class jsemid_ds {
  jfieldID otime,ctime,nsems;
public:
  void init(JNIEnv *env) {
    jclass c = env->FindClass("posix/SemSet$semid_ds");
    otime = env->GetFieldID(c,"sem_otime","J");
    ctime = env->GetFieldID(c,"sem_ctime","J");
    nsems = env->GetFieldID(c,"sem_nsems","I");
  }
  void getsemid(JNIEnv *env,semid_ds *mbuf,jobject obj) {
    ::perm.getperm(env,&mbuf->sem_perm,obj);
  }
  void setsemid(JNIEnv *env,const semid_ds *mbuf,jobject obj) {
    ::perm.setperm(env,&mbuf->sem_perm,obj);
    env->SetLongField(obj,otime,(jlong)mbuf->sem_otime * 1000);
    env->SetLongField(obj,ctime,(jlong)mbuf->sem_ctime * 1000);
    env->SetIntField(obj,nsems,mbuf->sem_nsems);
  }
};

static jsemid_ds semds;


JNIEXPORT jint JNICALL Java_posix_SemSet_init
  (JNIEnv *env, jclass) {
  semds.init(env);
  return SEM_UNDO;
}

JNIEXPORT jint JNICALL Java_posix_SemSet_semget
  (JNIEnv *, jclass, jint key, jint size, jint flag) {
  int rc = semget(key,size,flag);
  return (rc < 0) ? (errno | 0x80000000) : rc;
}

JNIEXPORT jint JNICALL Java_posix_SemSet_semctl__IIILposix_SemSet_00024semid_1ds_2
  (JNIEnv *env, jclass, jint id, jint sem, jint cmd, jobject obj) {
  semid_ds buf;
  int rc = -1;
  if (obj != 0) {
    if (cmd == 1) {	// IPC_SET
      semds.getsemid(env,&buf,obj);
      rc = semctl(id,sem,IPC_SET,&buf);
      if (rc < 0) rc = errno | 0x80000000;
    }
    else if (cmd == 2) { // IPC_STAT
      rc = semctl(id,sem,IPC_STAT,&buf);
      if (rc >= 0)
	semds.setsemid(env,&buf,obj);
      else rc = errno | 0x80000000;
    }
  }
  else if (cmd == 0)
    rc = semctl(id,IPC_RMID,0);
    if (rc < 0) rc = errno | 0x80000000;
  return rc;
}

/*
 * Class:     posix_SemSet
 * Method:    semctl
 * Signature: (III[S)I
 */
JNIEXPORT jint JNICALL Java_posix_SemSet_semctl__III_3S
  (JNIEnv *env, jclass, jint id, jint sem, jint cmd, jshortArray v) {
  int n = env->GetArrayLength(v);
  short *sa = (short *)alloca(n * sizeof *sa);
  int rc;
  switch (cmd) {
  case 6:	// GETALL
    rc = semctl(id,sem,GETALL,sa);
    if (rc == 0)
      env->SetShortArrayRegion(v,0,n,sa);
    return (rc < 0) ? (errno | 0x80000000) : rc;
  case 9:	// SETALL
    env->GetShortArrayRegion(v,0,n,sa);
    rc = semctl(id,sem,SETALL,sa);
    return (rc < 0) ? (errno | 0x80000000) : rc;
  }
  return -1;
}

/*
 * Class:     posix_SemSet
 * Method:    semctl
 * Signature: (IIII)I
 */
JNIEXPORT jint JNICALL Java_posix_SemSet_semctl__IIII
  (JNIEnv *, jclass, jint id, jint sem, jint cmd, jint val) {
  switch (cmd) {
  case 0:	// IPC_RMID
  case 3:	// GETNCNT
  case 4:	// GETPID
  case 5:	// GETVAL
  case 7:	// GETZCNT
  case 8:	// SETVAL
    int rc = semctl(id,sem,cmdmap[cmd],val);
    return (rc < 0) ? (errno | 0x80000000) : rc;
  }
  return -1;
}

/*
 * Class:     posix_SemSet
 * Method:    semop
 * Signature: (I[S)I
 */
JNIEXPORT jint JNICALL Java_posix_SemSet_semop
  (JNIEnv *env, jclass, jint id, jshortArray sema) {
  int n = env->GetArrayLength(sema);
  short *sa = (short *)alloca(n * sizeof *sa);
  env->GetShortArrayRegion(sema,0,n,sa);
  int nop = n / 3;
  struct sembuf *buf = (struct sembuf *)alloca(nop * sizeof *buf);
  for (int i = 0; i < nop; ++i) {
    struct sembuf *p = buf + i;
    p->sem_num = *sa++;
    p->sem_op = *sa++;
    p->sem_flg = *sa++;
  }
  int rc = semop(id,buf,nop);
  return (rc < 0) ? errno : rc;
}
