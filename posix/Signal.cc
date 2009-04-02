/*
 * $Log: Signal.cc,v $
 * Revision 1.7  2007/06/18 17:58:56  stuart
 * Support kill method for Java signals, provide map to native.
 *
 * Revision 1.6  2005/06/14 23:34:37  stuart
 * Improve Signal error handling.
 *
 * Revision 1.5  2002/07/03 22:39:29  stuart
 * More portable signal handling.
 *
 * Revision 1.3  2002/01/22 17:37:19  stuart
 * Make asynch signal safe using sem_post.
 * Sleep in trap.java since signalThread is now a daemon.
 *
 * Revision 1.2  2002/01/17 21:36:59  stuart
 * Update to Oct 2001 version.
 *
 * Revision 1.1  1999/08/06  18:05:42  stuart
 * Initial revision

Arrange to trap selected posix signals and wait for them to
occur.

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
#include <signal.h>
#include <stdlib.h>
#include <errno.h>
#include "posix_Signal.h"

#ifndef SIGMAX
#define SIGMAX SIGUNUSED
#endif
static sig_atomic_t flags[SIGMAX + 1];

enum { STATE_DFL, STATE_IGN, STATE_TRAP, JMAXSIG = 8 };

struct jsignal {
  int sig;			// which posix signal for this Java signal
  int state;			// current state
  struct sigaction action;	// save default JVM signal handling
};

static jsignal sigmap[JMAXSIG] = {
 {SIGHUP}, {SIGINT}, {SIGQUIT}, {SIGUSR1}, {SIGUSR2},
#ifdef SIGDANGER
 {SIGDANGER},
#else
 {SIGPWR},
#endif
 {SIGTERM},
 {SIGWINCH}
};

static bool signalThreadRunning = false;
static pthread_t signalThread;
static sigset_t signalSet;
static pthread_cond_t signalEvent = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t signalMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t handlerMutex = PTHREAD_MUTEX_INITIALIZER;

/* A dedicated thread to wait for signals.  A non-java thread is needed
   because we need to cancel the thread to change the set of signals that
   can be waited for.  A java thread cannot be cancelled via the pthread
   library without dire consequences.
 */
static void *handler(void *set) {
  sigset_t signalSet = *(sigset_t *)set;
  sigthreadmask(SIG_BLOCK, &signalSet, NULL);
  for (;;) {
    int sig;
    int old;
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE,&old);
    if (sigwait(&signalSet, &sig)) break;
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE,&old);
    if (pthread_mutex_lock(&signalMutex) == 0) {
      flags[0] = 1;
      ++flags[sig];
      pthread_cond_broadcast(&signalEvent);
      pthread_mutex_unlock(&signalMutex);
    }
  }
  pthread_exit(0);
}

static bool sigisequal(const sigset_t *a,const sigset_t *b) {
  for (int i = 1; i <= SIGMAX; ++i) {
    bool b1 = sigismember(a,i) != 0;
    bool b2 = sigismember(b,i) != 0;
    if (b1 != b2) return false;
  }
  return true;
}

/** Create a new signalThread to update the set of trapped signals.
   Unfortunately, this loses any pending signals.
 */
static void restartHandler() {
  static sigset_t set;
  sigemptyset(&set);
  int cnt = 0;
  for (int i = 0; i < JMAXSIG; ++i) {
    jsignal *s = sigmap + i;
    if (s->state == STATE_TRAP) {
      sigaddset(&set, s->sig);
      ++cnt;
    }
  }
  if (sigisequal(&set,&signalSet)) return;
  if (signalThreadRunning) {
    void *ret;
    pthread_cancel(signalThread);
    pthread_join(signalThread,&ret);
    signalThreadRunning = false;
  }
  if (cnt > 0) {
    signalSet = set;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_create(&signalThread,&attr,handler,&signalSet);
    signalThreadRunning = true;
    pthread_attr_destroy(&attr);
  }
}

JNIEXPORT jint JNICALL Java_posix_Signal_sigwait
  (JNIEnv *env, jclass) {
  int s = -1;
  if (!pthread_mutex_lock(&signalMutex)) {
    while (!flags[0])	// wait for signal
      pthread_cond_wait(&signalEvent,&signalMutex);
    flags[0] = 0;
    for (int i = 0; i < JMAXSIG; ++i) {
      int sig = sigmap[i].sig;
      if (flags[sig] > 0) {
	--flags[sig];
	s = i;
	break;
      }
    }
    pthread_mutex_unlock(&signalMutex);
  }
  return s;
}

static bool validSignal(JNIEnv *env,int jsig) {
  if (jsig >= 0 && jsig < JMAXSIG) return true;
  jclass exclass = env->FindClass("java/lang/IllegalArgumentException");
  if (exclass != 0) {
    char buf[80];
    sprintf(buf,"Invalid signal id: %d",jsig);
    env->ThrowNew(exclass,buf);
    // not much we can do if ThrowNew fails...
  }
  return false;
}

JNIEXPORT jint JNICALL Java_posix_Signal_sigmap
  (JNIEnv *env, jclass, jint jsig) {
  if (!validSignal(env,jsig)) return 0;
  jsignal *s = sigmap + jsig;
  return s->sig;
}

JNIEXPORT void JNICALL Java_posix_Signal_sigaction
  (JNIEnv *env, jclass, jint jsig, jint action) {
  if (!validSignal(env,jsig)) return;
  jsignal *s = sigmap + jsig;
  if (pthread_mutex_lock(&handlerMutex)) return;
  if (s->state != action && s->sig > 0) {
    struct sigaction a;
    if (s->state == 0)
      sigaction(s->sig,0,&s->action);
    if (s->action.sa_handler != SIG_IGN && s->action.sa_handler != SIG_DFL) {
      // the JVM is handling this signal!
      jclass exclass = env->FindClass("java/lang/IllegalStateException");
      if (exclass != 0) {
	char buf[80];
	sprintf(buf,"Signal trapped by JVM: %d",s->sig);
	env->ThrowNew(exclass,buf);
	// not much we can do if ThrowNew fails...
      }
      return;
    }
    switch (action) {
    case STATE_DFL:		// set JVM default action
      sigaction(s->sig,&s->action,0);
      s->state = action;
      restartHandler();	// cancel and restart handler with new signal set
      break;
    case STATE_IGN:
      a.sa_handler = SIG_IGN;
      sigemptyset(&a.sa_mask);
      a.sa_flags = SA_RESTART;
      sigaction(s->sig,&a,0);
      s->state = action;
      restartHandler();	// cancel and restart handler with new signal set
      break;
    case STATE_TRAP:
      s->state = action;
      restartHandler();
      a.sa_handler = SIG_DFL;
      sigemptyset(&a.sa_mask);
      a.sa_flags = SA_RESTART;
      sigaction(s->sig,&a,0);
      break;
    }
  }
  pthread_mutex_unlock(&handlerMutex);
}

JNIEXPORT jint JNICALL Java_posix_Signal_kill
  (JNIEnv *, jclass, jint pid, jint sig) {
    return kill(pid,sig) ? errno : 0;
  }
