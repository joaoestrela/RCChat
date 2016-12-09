import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

	// Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
	JFrame frame = new JFrame("Chat Client");
	private JTextField chatBox = new JTextField();
	private JTextArea chatArea = new JTextArea();
	// --- Fim das variáveis relacionadas coma interface gráfica

	// Se for necessário adicionar variáveis ao objecto ChatClient, devem
	// ser colocadas aqui

	Socket clientSocket;
	DataOutputStream outToServer;
	BufferedReader inFromServer;
	String prev_message = "";
	String nickname;
	String channel;

	// Método a usar para acrescentar uma string à caixa de texto (mensagem de servidor)
	// * NÃO MODIFICAR *
	public void printMessage(final String message) {
		String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
		chatArea.append(" [" + timeStamp + "] " + message+"\n");
	}

	//Método a usar para acrecentar uma string à caixa de texto (mensagem privada)
	public void printPrivateMessage(final String who,final String direction,final String message) {
		String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
		chatArea.append(" [" + timeStamp + "] "+direction+" <" + who + ">: " + message+"\n");
	}

	//Método a usar para acrecentar uma string à caixa de texto (mensagem de canal)
	public void printChannelMessage(final String who,final String message) {
		String timeStamp = new SimpleDateFormat("HH.mm.ss").format(new Date());
		chatArea.append(" [" + timeStamp + "] [#"+channel+"] " + "<" + who + ">: " + message+"\n");
	}

	// Construtor
	public ChatClient(String server, int port) throws IOException {
		// Inicialização da interface gráfica --- * NÃO MODIFICAR *
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.add(chatBox);
		frame.setLayout(new BorderLayout());
		frame.add(panel, BorderLayout.SOUTH);
		frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
		frame.setSize(500, 300);
		frame.setVisible(true);
		chatArea.setEditable(false);
		chatBox.setEditable(true);
		chatBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					newMessage(chatBox.getText());
				} catch (IOException ex) {
				} finally {
					chatBox.setText("");
				}
			}
		});
		// --- Fim da inicialização da interface gráfica
		// Se for necessário adicionar código de inicialização ao
		// construtor, deve ser colocado aqui
		boolean connected = false;
		try{
			clientSocket = new Socket(server,port);
			outToServer = new DataOutputStream(clientSocket.getOutputStream());
			inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),"UTF-8"));
			connected = true;
		}
		catch(IOException ie){
			connected = false;
			System.out.println("Error");
		}
		finally{
			if(connected){
				frame.setTitle("Chat Server @ " + server + ":" + port);
				printMessage("Connected to server @ " + server + ":" + port);
				printMessage("---------------------------------------------------------");
				printMessage("Available Commands:");
				printMessage("/nick $nickname to set/change your nickname.");
				printMessage("/join $room to join/change your conversation room.");
				printMessage("/leave to leave your current coversation room.");
				printMessage("/priv $nickname to send a private message to $nickname.");
				printMessage("/bye to close the connection to the current server.");
				printMessage("---------------------------------------------------------");
			}
			else printMessage("Unable to connect to the server.");
		}
	}

	// Método invocado sempre que o utilizador insere uma mensagem
	// na caixa de entrada
	public void newMessage(String message) throws IOException {
		// PREENCHER AQUI com código que envia a mensagem ao servidor
		prev_message = message;
		message+="\n";
		byte [] msg = message.getBytes("UTF-8");
		outToServer.write(msg);
	}

	public void handleFromServer(String cmd){
		String prev_message_split [] = prev_message.split(" ");
		switch(prev_message_split[0]){
		case "/leave":
			if(cmd.equals("OK")){
				printMessage("You left the room #" + channel + ".");
				channel = "";
			}
			if(cmd.equals("ERROR")){
				if(channel == null) printMessage("You can't leave if you aint't in a room");
				else printMessage("You can't leave room #" + channel + ".");
			}
			break;
		case "/join":
			channel = prev_message_split[1];
			if(cmd.equals("OK")){
				printMessage("You joined the room #" + channel + ".");
			}
			if(cmd.equals("ERROR")){
				if(nickname == null) printMessage("You can join a room before setting a nickname.");
				else printMessage("You can't join room #" + channel + ".");
			}
			break;
		case "/nick":
			nickname = prev_message_split[1];
			if(cmd.equals("OK")){
				printMessage("You changed your nick to <" + nickname + ">.");
			}
			if(cmd.equals("ERROR")){
				if(nickname == null) printMessage("You can't set your nickname to "+nickname+".");
				else
				printMessage("You can't change your nickname to "+nickname+".");
			}
			break;
		case "/priv":
			String receiver = prev_message_split[1];
			if(cmd.equals("OK")){
				String message ="";
				for(int k = 2; k<prev_message_split.length; k++) message += (prev_message_split[k] + " ") ;
				printPrivateMessage(receiver,"TO", message);
			}
			if(cmd.equals("ERROR"))printMessage("Unable to send private message to <"+ receiver + ">");
			break;
		default:
			if(cmd.equals("ERROR")){
				if(prev_message_split[0].charAt(0) == '/' && prev_message_split[0].charAt(1) != '/'){
					printMessage("Invalid command: " + prev_message_split[0]);
					break;
				}
				if(nickname == null) printMessage("Please set your nickname before doing that!");
				else if(channel == null) printMessage("Please join a room before sending any message!");
			}
			break;
		}
	}

	// Método principal do objecto
	public void run() throws IOException {
		// PREENCHER AQUI
		while(!clientSocket.isClosed()){
			String temp_line = inFromServer.readLine();
			System.out.println("Received " + temp_line);
			if(!temp_line.equals("")){
				String messageFromServer [] = temp_line.split(" ");
				String user_message = "";
				switch(messageFromServer[0]){
				case "OK":
					handleFromServer("OK");
					break;
				case "ERROR":
					handleFromServer("ERROR");
					break;
				case "MESSAGE":
					for(int i = 2; i < messageFromServer.length ; i++){
						user_message += " " + messageFromServer[i];
					}
					printChannelMessage(messageFromServer[1], user_message);
					break;
				case "PRIVATE":
					for(int i = 2; i < messageFromServer.length ; i++){
						user_message += (messageFromServer[i]+" ");
					}
					printPrivateMessage(messageFromServer[1],"FROM", user_message);
					break;
				case "NEWNICK":
					printMessage("User " + messageFromServer[1] + " changed nick to: " + messageFromServer[2]);
					break;
				case "JOINED":
					printMessage("User " + messageFromServer[1] + " has joined the room");
					break;
				case "LEFT":
					printMessage("User " + messageFromServer[1] + " has left the room");
					break;
				case "BYE":
					printMessage("Disconnected from the server!");
					clientSocket.close();
					frame.dispose();
					break;
				}
			}
		}
	}

	// Instancia o ChatClient e arranca-o invocando o seu método run()
	// * NÃO MODIFICAR *
	public static void main(String[] args) throws IOException {
		ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
		client.run();
	}

}
