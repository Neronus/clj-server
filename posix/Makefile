PKG=posix-1.1.8
CVSTAG=posix-1_1_8

# preprocessor flags:
# POSIX_STRERROR_R=0	old glibc strerror_r
# POSIX_STRERROR_R=1	posix strerror_r
# HAVE_MSGBUF		struct msgbuf declared by system as per posix

# AIX 4.1.5 w/ GNUC and AIX linker
#CC=gcc
#CXXFLAGS = -O -I/usr/jdk_base/include -I/usr/jdk_base/include/aix -mthreads
#LIB=-mthreads -Wl,-blibpath:/lib:/usr/lib -lpthreads \
#	-L/usr/jdk_base/lib/aix/native_threads -ljava
#JAVALIB = /usr/jdk_base/lib/aix/native_threads/libposix.a

# RHEL 4


#ARCH=i386
ARCH=amd64
JAVATOP=/opt/java/

CXXFLAGS = -O -pthread -I$(JAVATOP)/include -I$(JAVATOP)/include/linux \
	-DHAS_SEM_POST -DHAVE_MSGBUF -Dsigthreadmask=pthread_sigmask -fPIC
LIB =	-pthread -L$(JAVATOP)/jre/lib/$(ARCH)/ -ljava

# Solaris 2.6
#CCFLAGS = -O -I/usr/java/include -I/usr/java/include/solaris -DSIGMAX=MAXSIG
#LIB = -L/usr/java/lib/sparc/native_threads -ljava -lpthread
#JAVALIB = /usr/java/lib/sparc/native_threads/libposix.so
#CCC = gcc

JAVA_CLASSES=$(wildcard posix/*.java)
JUNIT_LIB=/usr/lib/junit.jar

all:	libposix.so posix.jar

POSIX_OBJS = ipc.o Errno.o CPtr.o Signal.o Stat.o

ipc.o:	ipc.cc posix_MsgQ.h posix_IPC.h posix_SharedMem.h

Errno.o:	posix_Errno.h Errno.cc

CPtr.o:	CPtr.cc posix_CPtr.h posix_Malloc.h

Signal.o: Signal.cc posix_Signal.h

File.o:	File.cc posix_File.h

libposix.so: $(POSIX_OBJS)
	g++ -shared -o libposix.so $(POSIX_OBJS) $(LIB)

#posix.exp:	ipc.o Errno.o CPtr.o Signal.o
#	genexp ipc.o Errno.o CPtr.o Signal.o >posix.exp
#posix.so:	posix.exp
#	ld -s -o posix.so ipc.o Errno.o CPtr.o Signal.o	\
#	  -bnoentry -bM:SRE -bE:posix.exp -blibpath:/lib:/usr/lib \
#	  -lpthreads -lc_r -L/java/lib/aix/native_threads -ljava

tar:
	ln -s . $(PKG) || true
	tar -cvhf $(PKG).tar $(PKG)/posix/*.java $(PKG)/*.h \
		$(PKG)/*.cc $(PKG)/Makefile $(PKG)/*.html \
		$(PKG)/unfinished/*.java $(PKG)/*.spec \
		$(PKG)/TODO $(PKG)/CREDITS $(PKG)/COPYING
	gzip $(PKG).tar; rm $(PKG)

SRCTAR = $(PKG).tar.gz
$(SRCTAR):
	cvs export -r$(CVSTAG) -d $(PKG) java/posix
	tar cvf $(PKG).tar $(PKG)
	gzip -f $(PKG).tar
	rm -r $(PKG)

cvstar: $(SRCTAR)

posix.jar: classes
	jar c posix/*.class > posix.jar

classes:
	CLASSPATH=posix:"$(JUNIT_LIB)" javac -g posix/*.java

zip:
	zip posix posix/*.java *.h *.cc *.html Makefile

doc:	$(JAVA_CLASSES) package.html
	polardoc -author -version -package -notree -noindex \
		-d $(DOCDIR) posix/*.java

clean:
	rm -f *.so *.o posix/*.class posix.jar posix/*html

.PHONY : classes clean
