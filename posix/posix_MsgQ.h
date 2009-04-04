/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class posix_MsgQ */

#ifndef _Included_posix_MsgQ
#define _Included_posix_MsgQ
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     posix_MsgQ
 * Method:    msgget
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgget
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     posix_MsgQ
 * Method:    msgctl
 * Signature: (IILposix/MsgQ$msqid_ds;)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgctl
  (JNIEnv *, jclass, jint, jint, jobject);

/*
 * Class:     posix_MsgQ
 * Method:    msgsnd
 * Signature: (II[BI)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgsnd
  (JNIEnv *, jclass, jint, jint, jbyteArray, jint);

/*
 * Class:     posix_MsgQ
 * Method:    msgsnd0
 * Signature: (IJII)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgsnd0
  (JNIEnv *, jclass, jint, jlong, jint, jint);

/*
 * Class:     posix_MsgQ
 * Method:    msgrcv
 * Signature: (I[I[BI)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgrcv
  (JNIEnv *, jclass, jint, jintArray, jbyteArray, jint);

/*
 * Class:     posix_MsgQ
 * Method:    msgrcv0
 * Signature: (IJIII)I
 */
JNIEXPORT jint JNICALL Java_posix_MsgQ_msgrcv0
  (JNIEnv *, jclass, jint, jlong, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif