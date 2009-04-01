/*
     Copyright (c) Christian von Essen. All rights reserved.
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     You must not remove this notice, or any other, from this software.
 */

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <netdb.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <arpa/inet.h>

#define PORT "9000" // the port client will be connecting to

#define MAXDATASIZE 4096

#define AUTH_FILE_PATH ".clj-server-auth"

/*
 * If the socket is done sending data
 */
int sock_done = 0;
/*
 * If client is done sending data
 */
int stdin_done = 0;


/*
 * Send one character
 */
int send_char(int socket, char c) {
  return send(socket, &c, 1, 0);
}

/*
 * Send one integer, converted to a string
 */
int send_int(int socket, int i) {
  char buf[255];
  int n = sprintf(buf, "%d", i);
  return send(socket, buf, n+1, 0);
}

// get sockaddr, IPv4 or IPv6:
void *get_in_addr(struct sockaddr *sa)
{
    if (sa->sa_family == AF_INET) {
        return &(((struct sockaddr_in*)sa)->sin_addr);
    }

    return &(((struct sockaddr_in6*)sa)->sin6_addr);
}

void send_auth_file(int socket) {
  char *home = getenv("HOME");
  int home_len, auth_file_path_len;
  char *path;
  int path_len = 0;
  char answer;
  FILE *f;
  char buf[MAXDATASIZE];
  int read;
  int sent;

  home_len = strlen(home);
  auth_file_path_len = strlen(AUTH_FILE_PATH);
  path_len = home_len + auth_file_path_len + 1;
  path = malloc(sizeof(char) * (path_len + 1));
  strcpy(path, home);
  path[home_len] = '/';
  strcpy(path + home_len + 1, AUTH_FILE_PATH);
  path[path_len] = '\0';
  
  send(socket, path, path_len + 1, 0);
  
  // The next byte determines, if the server accepted the file
  read = recv(socket, &answer, 1, 0);
  if(read != 1) {
	perror("recv");
	exit(1);
  }
  
  if(!answer) {
	fprintf(stderr, "Server did not accept authentication file\n");
	exit(1);
  }

  // Now start sending the file
  f = fopen(path, "r");
  if(f == NULL) {
	perror("fopen");
	exit(1);
  }

  while(1) {
	// read some bytes
	read = fread(buf, sizeof(char), MAXDATASIZE, f);
	if(read != MAXDATASIZE) {
	  if(feof(f)) {
		sent = send(socket, buf, read, 0);
		if(sent != read) {
		  perror("send");
		  exit(1);
		}
		// OK, file sent
		break;
	  }
	  // If we're here, some error has occured
	  perror("fread");
	  exit(1);
	}
	sent = send(socket, buf, read, 0);
	if(sent != read) {
	  perror("send");
	  exit(1);
	}
  }

  // OK, now get the answer
  read = recv(socket, &answer, 1, 0);
  if(read != 1) {
	perror("read");
	exit(1);
  }
  
  if(!answer) {
	fprintf(stderr, "Server did not accept authentication file\n");
	exit(1);
  }
}

/*
 * Only called, when STDIN is ready. Read from stdin and
 * write to socket.
 * When STDIN is at EOF, set stdin_done to 1.
 */
void stdin_reader(int socket) {
  char buf[MAXDATASIZE];
  int numbytes;
  
  numbytes = read(STDIN_FILENO, buf, MAXDATASIZE);
  switch(numbytes) {
  case -1:
	perror("read");
	exit(1);
  case 0:
	stdin_done = 1;
	shutdown(socket, SHUT_WR);
	break;
  default:
	send(socket, buf, numbytes, 0);
  }
}
  

/*
 * Only called, when socket is ready to read.
 * Reads from socket, and write output to stdout.
 * When socket is at EOF, set sock_done to 1.
 */
void stdout_writer(int socket) {
  char buf[MAXDATASIZE];
  int numbytes;
  
  numbytes = recv(socket, buf, MAXDATASIZE-1, 0);
  switch(numbytes) {
  case -1:
	perror("recv");
	exit(1);
  case 0:
	sock_done = 1;
	shutdown(socket, SHUT_RD);
	break;
  default:
	buf[numbytes] = '\0';
	fputs(buf, stdout);
	fflush(stdout);
  }

}


int main(int argc, char *argv[])
{
    int sockfd;
    struct addrinfo hints, *servinfo, *p;
    int rv;
    char s[INET6_ADDRSTRLEN];
	int i;
	char *cwd;
	char *port;
	
	fd_set fdset;
	int select_result;

    memset(&hints, 0, sizeof hints);
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

	port = getenv("CLJ_CLIENT_PORT");
	if(port == NULL) {
	  port = PORT;
	}

    if ((rv = getaddrinfo("127.0.0.1", port, &hints, &servinfo)) != 0) {
        fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(rv));
        return 1;
    }

    // loop through all the results and connect to the first we can
    for(p = servinfo; p != NULL; p = p->ai_next) {
        if ((sockfd = socket(p->ai_family, p->ai_socktype,
                p->ai_protocol)) == -1) {
            perror("client: socket");
            continue;
        }

        if (connect(sockfd, p->ai_addr, p->ai_addrlen) == -1) {
            close(sockfd);
            perror("client: connect");
            continue;
        }

        break;
    }


    if (p == NULL) {
        fprintf(stderr, "client: failed to connect\n");
        return 2;
    }

    inet_ntop(p->ai_family, get_in_addr((struct sockaddr *)p->ai_addr),
            s, sizeof s);

    freeaddrinfo(servinfo); // all done with this structure

	// attempt auth - if this doesn't work, we'll never return
	send_auth_file(sockfd);
	

	// Send PWD
	cwd = get_current_dir_name();
	send(sockfd, cwd, strlen(cwd), 0);
	send_char(sockfd, '\0');

	// send argument count
	send_int(sockfd, argc-1);

	free(cwd);

	// send arguments
	for(i = 1; i < argc; i++) {
	  send(sockfd, argv[i], strlen(argv[i]), 0);
	  send_char(sockfd, '\0');
	}


	// Let the communication begin!
	while(!sock_done) {
	  FD_ZERO(&fdset);
	  if(!stdin_done) {
		FD_SET(STDIN_FILENO, &fdset);
	  }
	  FD_SET(sockfd, &fdset);
	  
	  select_result = select(sockfd+1, &fdset, NULL, NULL, NULL);
	  if(select_result < 0) {
		perror("select");
	  }
	  
	  if(FD_ISSET(STDIN_FILENO, &fdset)) {
		stdin_reader(sockfd);
		FD_CLR(STDIN_FILENO, &fdset);
	  }
	  if(FD_ISSET(sockfd, &fdset)) {
		stdout_writer(sockfd);
		FD_CLR(sockfd, &fdset);
	  }
	}

    return 0;
}
