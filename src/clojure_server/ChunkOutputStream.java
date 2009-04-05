package clojure_server;

import java.io.*;

/**
 * This class's state is determined by a wrapped output stream
 * and an associated "file descriptor", which is just a  number.
 * If data is wrote to an instance of this class, it prepends
 * a header to each "chunk" of data, that is sent. This header
 * contains, in the order given, a 4 byte integer denoting the
 * file descriptor which is part of the instance's state, and
 * the length of the chunk.
 * The Main idea behind this class is to be able to addess
 * more than one filedescriptor transparently on the other end
 * of a socket stream.
 * By using this class, it is possible, to "split" a socket stream
 * into stdout and stderr. This can be used to emulate standard
 * terminal behaviour over a network.
 */
public class ChunkOutputStream extends OutputStream {
	
	/**
	 * Stream used to send the
	 */
	final OutputStream stream;
	
	/*
	 * Part of the header
	 */
	final int fd;
	
	public ChunkOutputStream(OutputStream stream, int fd) {
		// Use the stream for locking, so that not two writers
		// write on it at the same time
		this.stream = stream;
		this.fd = fd;
	}

	public void write(byte[] buf, int offset, int len) throws IOException {
		// OK, first write the destination, then the length, then write
		// the data itself
		//byte header[8];
		synchronized(stream) {
			for(int i = 3; i >= 0; i--) {
				stream.write((fd >> (i*8)) & 255);
			}
			for(int i = 3; i >= 0; i--) {
				stream.write(255 & (len >> (i*8)));
			}
			stream.write(buf, offset, len);
		}
	}

	public void write(int data) throws IOException {
		// OK, first write the destination, then the length, then write
		// the data itself
		synchronized(stream) {
			for(int i = 3; i >= 0; i--) {
				stream.write(fd >> (i*8));
			}
			for(int i = 2; i >= 0; i--) {
				stream.write(0);
			}
			stream.write(1);
			stream.write(data);
		}
	}

	public void flush() throws IOException {
		stream.flush();
	}

	public void close() throws IOException {
		stream.close();
	}
}