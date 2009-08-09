package clojure_server;

public class ExitException extends Exception {
	private int status;
	public ExitException(int status) {
		this.status = status;
	}

	public int getStatus() {
		return status;
	}
}