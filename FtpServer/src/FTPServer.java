import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Vector;

public class FTPServer {
	static ServerSocket serverSocket;
	Vector<Thread> vector;
	int maxClients;
	int timeout;

	public FTPServer(int port) {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Server start on port " + String.valueOf(port));
			maxClients = 10;
			timeout = 1000 * 60 * 30;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static void writeMessage(Socket socket, String message) {
		BufferedOutputStream out;
		try {
			out = new BufferedOutputStream(socket.getOutputStream());
			out.write((message + "\r\n").getBytes());
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void startServer() {
		vector = new Vector<Thread>();
		vector.setSize(maxClients);
		while (true) {
			try {
				final Socket socket = serverSocket.accept();
				socket.setSoTimeout(timeout);
				final Thread thread = new Thread(new Runnable() {

					public void run() {
						socketProcess(socket);
						vector.removeElement(Thread.currentThread());
					}
				});
				thread.start();
				vector.add(thread);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	static void socketProcess(Socket socket) {
		try {
			System.out.println("Client conect");
			InputStream inputStream = socket.getInputStream();
			BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
			out.write("220 FTP server ready.\r\n".getBytes());
			out.flush();
			BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
			Cmd cmd = new Cmd(socket);
			cmd.setDefaulDirectory("/home/sania/");
			while (!Thread.currentThread().isInterrupted()) {
				if (socket.isClosed())
					Thread.currentThread().interrupt();
				else {
					try {
						String s = null;
						if(socket.isBound())
							s = br.readLine();
						if (s == null || s.isEmpty())
							Thread.currentThread().interrupt();
						else {
							parseComand(socket, s, cmd);
						}
					} catch (SocketTimeoutException e) {
						socket.close();
						System.out.println("Client timeout ");
					}

				}
			}
			socket.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			socket = null;
			e.printStackTrace();

		}
		System.out.println("Client disconect.");
	}

	static void parseComand(Socket socket, String string, Cmd cm) {
		System.out.println("Client: " + string);
		String[] tmp = string.split(" ");
		if (tmp.length < 1) {
			writeMessage(socket, "501 Syntax error in parameters or arguments.");
			return;
		}
		String cmd = tmp[0].toUpperCase();
		String parameter = string.substring(tmp[0].length()).trim();
		try {
			Commands c = Commands.valueOf(cmd);
			cm.setParameter(parameter);
			switch (c) {
			case STOR:
				cm.cmdSTOR();
				break;
			case RETR:
				cm.cmdRETR();
				break;
			case SIZE:
				cm.cmdSIZE();
				break;
			case MDTM:
				cm.cmdMDTM();
				break;
			case PORT:
				cm.cmdPORT();
				break;
			case LIST:
				cm.cmdLIST();
				break;
			case CWD:
				cm.cmdCWD();
				break;
			case USER:
				cm.cmdUSER();
				break;
			case PASV:
				cm.cmdPASV();
				break;
			case SYST:
				cm.cmdSYST();
				break;
			case TYPE:
				cm.cmdTYPE();
				break;
			case PWD:
				cm.cmdPWD();
				break;
			case FEAT:
				cm.cmdFEAT();
				break;
			case QUIT:
				socket.close();
				break;
			}
		} catch (Exception e) {
			writeMessage(socket, "502 Command not implemented.");
			System.out.println("Server: Command not implemented: " + cmd);
		}
	}

}
