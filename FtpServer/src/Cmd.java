
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

public class Cmd {
	private File file;
	private String parameter;
	private Socket socket;
	private Socket dataSocket;
	private ServerSocket pasvServerSocket;
	private String encoding = "UTF-8";

	public Cmd(Socket socket) {
		this.socket = socket;
	}

	public Cmd() {
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}
	
	private void closeDataConnection(){
		if(dataSocket!= null){
			try {
				dataSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			dataSocket=null;
		}else {
			dataSocket = null;
		}
		if(pasvServerSocket!= null){
			try {
				pasvServerSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			pasvServerSocket=null;
		}else {
			pasvServerSocket = null;
		}
			
	}
	
	public void setDefaulDirectory(String dir) {
		if (!(file = new File(dir)).isDirectory()) {
			file = null;
			return;
		}
	}

	private void writeMessage(String cmd) {
		BufferedOutputStream out;
		try {
			out = new BufferedOutputStream(socket.getOutputStream());
			out.write((cmd + "\r\n").getBytes(encoding));
			out.flush();
			System.out.println("Server: send msg " + cmd);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// writeMessage(socket, "530 Not logged in.");
	}

	public void setParameter(String string) {
		parameter = string;
	}
	
	public void cmdSTOR(){
		if (parameter.isEmpty()) {writeMessage("501 Syntax error in parameters or arguments."); return;}
	
			try {
				writeMessage("150 Sending file");
				FileOutputStream fo = new FileOutputStream(file+"/"+parameter);
				InputStream in = dataSocket.getInputStream();
				
				int count;
				byte[] bytes = new byte[16 * 1024];
				while((count = in.read(bytes))>0){
				fo.write(bytes, 0, count);
				}
				fo.close();
				in.close();
				closeDataConnection();
				if(count==0) writeMessage("426 Couldn't receive data");
				else if(count==-2) writeMessage("425 Could not connect data socket");
				else 
				writeMessage("226 Data transmission OK");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			
					
	}
	
	public void cmdRETR(){
		if (parameter.isEmpty()) {writeMessage("501 Syntax error in parameters or arguments."); return;}
		File tmp = new File(file.getAbsolutePath()+"/"+parameter);
		if(!tmp.exists()){
			writeMessage("550 File does not exist.");
			closeDataConnection();
			return;
		}else if(tmp.isDirectory()){
			writeMessage("550 Can't RETR a directory.");
			closeDataConnection();
			return;
		}else if(!tmp.canRead()){
			writeMessage("550 No read permissions.");
			closeDataConnection();
			return;
		}else {
			try {
				writeMessage("150 Sending file");
				InputStream fi = new FileInputStream(tmp);
				OutputStream out = dataSocket.getOutputStream();
				int count;
				byte[] bytes = new byte[16 * 1024];
				while((count = fi.read(bytes))>0)
				out.write(bytes, 0, count);
				fi.close();
				out.close();
				closeDataConnection();
				writeMessage("226 Data transmission OK");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}				
		}
	}
	
	public void cmdSIZE() {
		File tmp = null;
		if (parameter.contains("/"))
			tmp = new File(parameter);
		else
			tmp = new File(file.toString() + "/" + parameter);
		if (tmp.exists())
			writeMessage("213 " + String.valueOf(tmp.length()));
		else
			writeMessage("550 Cannot get the SIZE of nonexistent object");
	}

	public void cmdMDTM() {
		if (file.exists()) {
			long lastModified = file.lastModified();
			SimpleDateFormat format = new SimpleDateFormat("YYYYMMDDhhmmss", Locale.US);
			writeMessage("213 " + format.format(lastModified).toString());
		} else {
			writeMessage("550 file does not exist\r\n");
		}
	}

	public void cmdPORT() {
		int pt;
		StringBuilder host = new StringBuilder();
		String[] str = parameter.split(",");
		if (str.length < 5) {
			writeMessage("501 Syntax error in parameters or arguments.");
			return;
		}
		for (int i = 0; i < str.length - 2; i++) {
			host.append(str[i]);
			if (i != str.length - 3)
				host.append('.');
		}
		System.out.println("Server Port host" + host.toString());
		pt = Integer.valueOf(str[4]) * 256 + Integer.valueOf(str[5]);
		System.out.println("Server Port port" + String.valueOf(pt));
		try {
			dataSocket = new Socket(host.toString(), pt);
			if (dataSocket.isConnected())
				writeMessage("200 PORT connect");
			else {
				writeMessage("530 Not logged in.");
				dataSocket = null;
			}

		} catch (IOException e) {
			dataSocket = null;
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void cmdLIST() {
		System.out.println("Server: start cmdList path " + file.getAbsolutePath());
		try {
			if (dataSocket == null) {
				System.out.println("Error dataSocket null");
				return;
			}
			writeMessage("150 Opening BINARY mode data connection for file list");
			BufferedOutputStream out = new BufferedOutputStream(dataSocket.getOutputStream());
			StringBuilder strout = new StringBuilder();
			if (file.isDirectory())
				for (File f : file.listFiles()) {
					Path p = f.toPath();

					if (f.isDirectory())
						strout.append('d');
					else if (Files.isSymbolicLink(p))
						strout.append('c');
					else
						strout.append('-');

					Set<PosixFilePermission> permp = Files.getPosixFilePermissions(p);
					strout.append(PosixFilePermissions.toString(permp));
					strout.append(" 1 ");
					strout.append(Files.getOwner(p));
					GroupPrincipal group = Files.readAttributes(p, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
							.group();
					strout.append(' ');
					strout.append(group.getName());
					long fileSize = f.length();
			        String sizeString = Long.toString(fileSize);
			        int padSpaces = 13 - sizeString.length();
			        while (padSpaces-- > 0) {
			        	strout.append(' ');
			        }
			        strout.append(sizeString);
					SimpleDateFormat format;
			        format = new SimpleDateFormat(" MMM dd  yyyy ", Locale.US);
					strout.append(format.format(new Date(f.lastModified())));
					strout.append(f.getName());
					strout.append("\r\n");

				}
			//System.out.println(strout.toString());
			out.write(strout.toString().getBytes(encoding));
			out.flush();
			writeMessage("226 Data transmission OK");
			closeDataConnection();

		} catch (IOException e) {
			dataSocket = null;
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Server: close cmdList");

	}

	public void cmdCWD() {
		try {
			File tmp = null;
			if (parameter.contains("/"))
				tmp = new File(parameter);
			else
				tmp = new File(file.toString() + "/" + parameter);
			System.out.println("Server CWD:" + tmp.toString());
			if (!tmp.isDirectory()) {
				writeMessage("550 Can't CWD to invalid directory.");
				return;
			} else if (!tmp.canRead()) {
				writeMessage("550 That path is inaccessible");
				return;
			} else {
				file = new File(tmp.getAbsolutePath());
				tmp.delete();
				writeMessage("250 CWD successful");
			}
		} catch (Exception e) {
			writeMessage("550 Invalid path");
			e.printStackTrace();
		}
		System.out.println("Server: CWD path: " + file.getAbsolutePath());

	}

	public void cmdPASV() {
		try {
			pasvServerSocket = new ServerSocket(0);
			String cantOpen = "502 Couldn't open a port\r\n";
			int port;
			if ((port = pasvServerSocket.getLocalPort()) == 0) {
				writeMessage(cantOpen);
				return;
			}
			// InetAddress addr = dataSocket.getLocalAddress();
			InetAddress addr = socket.getInetAddress();
			if (addr == null) {
				writeMessage(cantOpen);
				return;
			}

			if (port < 1) {
				writeMessage(cantOpen);
				return;
			}
			StringBuilder response = new StringBuilder("227 Entering Passive Mode (");
			// Output our IP address in the format xxx,xxx,xxx,xxx
			response.append(addr.getHostAddress().replace('.', ','));
			response.append(",");
			// Output our port in the format p1,p2 where port=p1*256+p2
			response.append(port / 256);
			response.append(",");
			response.append(port % 256);
			response.append(").");
			String responseString = response.toString();
			writeMessage(responseString);
			System.out.println("Server: port:" + String.valueOf(port));
			dataSocket = pasvServerSocket.accept();
		} catch (IOException e) {
			pasvServerSocket = null;
			dataSocket = null;
			e.printStackTrace();
		}

	}

	public void cmdTYPE() {
		if (parameter.equals("I") || parameter.equals("L 8")) {
			writeMessage("200 Binary type set");
		}
		// else if (string.equals("A") || string.equals("A N"))
		// writeMessage("200 ASCII type set");
		else
			writeMessage("503 Malformed TYPE command");

	}

	public void cmdPWD() {
		System.out.println("Server: cmdPWD " + file.toString());
		writeMessage("257 \"" + file.toString() + "\"");
	}

	public void cmdSYST() {
		writeMessage("215 UNIX Type: L8");
	}

	public void cmdUSER() {
		writeMessage("230 User logged in, proceed.");
	}

	public void cmdFEAT() {
		writeMessage("211-Features supported by FTP Server");
		writeMessage(" UTF8");
		writeMessage(" MDTM");
		writeMessage(" MFMT");
		writeMessage("211 End");
	}

}
