package clojure_server;

import java.io.*;

public class ChunkOutputStream extends OutputStream {
	final OutputStream stream;
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