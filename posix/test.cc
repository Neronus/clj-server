#include <stdio.h>
#include <signal.h>

static sig_atomic_t flags[SIGMAX + 1];

static void handler(int sig) {
  flags[sig] = 1;
  flags[0] = 1;
}

int main(int ,char **) {
  struct sigaction a;
  a.sa_handler = handler;
  SIGINITSET(a.sa_mask);
  a.sa_flags = SA_RESTART;
  sigaction(15,&a,0);
  sigaction(2,&a,0);

  sigset_t b,sav;
  sigfillset(&b);
  sigprocmask(SIG_BLOCK,&b,&sav);
  if (flags[0])
    sigprocmask(SIG_SETMASK,&sav,0);
  else {
    // listen for any signals Java might trap
    sigdelset(&sav,15);
    sigdelset(&sav,2);
    sigsuspend(&sav);
  }
  for (int i = 1; i <= SIGMAX; ++i)
    if (flags[i])
      printf("Signal %d detected\n",i);
  return 0;
}
