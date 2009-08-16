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

#include <locale.h>

#define PORT "9000" // the port client will be connecting to

#define MAXDATASIZE 65535

#define MSG_STDOUT 0
#define MSG_STDERR 1
#define MSG_EXIT   2
#define MSG_PATH   3
#define MSG_OK     4
#define MSG_ERR    5

/*
 * If the socket is done sending data
 */
int sock_done = 0;
/*
 * If client is done sending data
 */
int stdin_done = 0;

struct msg {
  uint8_t type;
  int32_t length;
  uint8_t *data;
};

int send_byte(int socket, uint8_t data) {
  return send(socket, &data, 1, 0);
}

int recv_byte(int socket, uint8_t *data) {
  return recv(socket, data, 1, 0);
}

int send_int(int socket, int32_t data) {
  data = htonl(data);
  return send(socket, &data, 4, 0);
}

int recv_int(int socket, int32_t *data) {
  int ret = recv(socket, data, 4, MSG_WAITALL);
  *data = ntohl(*data);
  return ret;
}

void recieve_msg(int socket, struct msg *msg) {
  if (recv_byte(socket, &msg->type) != 1) {
	perror("Reading message type: ");
	exit(1);
  }

  if (recv_int(socket, &msg->length) != 4) {
	perror("Reading message length: ");
	exit(1);
  }

  if (msg->length > 0) {
	msg->data = malloc(msg->length);
	if (msg->data == NULL) {
	  perror("Allocating message buffer: ");
	  exit(1);
	}

	if (recv(socket, msg->data, msg->length, MSG_WAITALL) != msg->length) {
	  perror("Recieving message data :");
	  exit(1);
	}
  } else {
	msg->data = NULL;
  }
}

void free_message_data(struct msg *msg) {
  if (msg->data != NULL) {
	free(msg->data);
	msg->data = NULL;
  }
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
  char *path;
  FILE *f;
  char buf[MAXDATASIZE];
  ssize_t read;
  int sent;
  struct msg msg;

  // recieve path
  recieve_msg(socket, &msg);

  if (msg.type != MSG_PATH) {
	fprintf(stderr, "Expected path message, got message type %d\n", msg.type);
	exit(1);
  }
  
  path = malloc(msg.length + 1);
  if (path == NULL) {
	perror("Error allocating memory for path: ");
	exit (1);
  }

  memcpy(path, msg.data, msg.length);
  path[msg.length] = '\0';
  // Now start sending the file
  f = fopen(path, "r");
  if(f == NULL) {
	perror("fopen");
	exit(1);
  }
  free(path);

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

  free_message_data(&msg);
  // OK, now get the answer

  recieve_msg(socket, &msg);
  switch (msg.type) {
  case MSG_OK:
	return;
  case MSG_ERR:
	fprintf(stderr, "Server did not accept authentication file\n");
	exit(1);
  default:
	fprintf(stderr, "Expected OK or ERR, got %d\n", msg.type);
	exit(1);
  }

  fclose(f);
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
 * Reads from socket.
 * When socket is at EOF, set sock_done to 1.
 */
void stdout_writer(int socket) {
  int status = 0; // Used for exit status
  struct msg msg;

  recieve_msg(socket, &msg);

  switch (msg.type) {
  case MSG_STDOUT:
	if (write(STDOUT_FILENO, msg.data, msg.length) != msg.length) {
	  perror ("An error occured while writing to stdout");
	  exit (1);
	}
	break;
  case MSG_STDERR:
	if (write(STDERR_FILENO, msg.data, msg.length) != msg.length) {
	  perror ("An error occured while writing to stderr");
	  exit (1);
	}
	break;
  case MSG_EXIT:
	status = ntohl((int)*(msg.data));
	exit (status);
  default:
	fprintf(stderr, "Got unexpected message type %d", msg.type);
	exit (1);
  }

  free_message_data(&msg);

  fflush(NULL);
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

	setlocale (LC_ALL, "");

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
	send_int(sockfd, strlen(cwd));
	send(sockfd, cwd, strlen(cwd), 0);
	free(cwd);

	// send argument count
	send_int(sockfd, argc-1);

	// send arguments
	for(i = 1; i < argc; i++) {
	  send_int(sockfd, strlen(argv[i]));
	  send(sockfd, argv[i], strlen(argv[i]), 0);
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
		exit(1);
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
