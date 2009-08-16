package clojure_server;

import java.io.*;

/**
 * This class's state is determined by a wrapped output stream
 * and an associated type number.
 * If data is written to an instance of this class, it prepends
 * a header to each "chunk" of data that is sent. This header
 * contains, in the order given, a 4 byte integer denoting the
 * message type which is part of the instance's state, and
 * the length of the remaining chunk. After that comes the
 * actual payload.
 */
public class ChunkOutputStream extends OutputStream {
	/**
	 * Stream used to send to
	 */
	final OutputStream stream;
	
	/*
	 * Part of the header
	 */
	final int type_nr;
	
	public ChunkOutputStream(OutputStream stream, int type_nr) {
		// Use the stream for locking, so that not two writers
		// write on it at the same time
		this.stream = stream;
		this.type_nr = type_nr;
	}

	public void write(byte[] buf, int offset, int len) throws IOException {
		// OK, first write the destination, then the length, then write
		// the data itself
		synchronized(stream) {
			stream.write(type_nr);
			for(int i = 3; i >= 0; i--) {
				stream.write((len >> (i*8)) & 255);
			}
			stream.write(buf, offset, len);
		}
	}

	public void write(int data) throws IOException {
		// OK, first write the destination, then the length, then write
		// the data itself
		synchronized(stream) {
			stream.write(type_nr);

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
		System.out.println("CLOSE");
		stream.close();
	}
}