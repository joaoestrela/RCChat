import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;



class Client{
	private SocketChannel sc;
	private String nick;
	private String status;
	private String room;

	public Client(SocketChannel sc){
		this.sc = sc;
		this.nick="UNKNOWN";
		this.status = "init";
		this.room = "";
	}

	public SocketChannel getSc() {
		return sc;
	}

	public String getNick() {
		return nick;
	}

	public void setNick(String nick) {
		System.out.println(this.nick + " changed nick to " + nick);
		this.nick = nick;
		if(status == "init") status = "outside";
	}

	public String getRoom() {
		return room;
	}
	public void setRoom(String room) {
		this.room = room;
		if(room != "") status = "inside";
		else status = "outside";
	}

	public String getStatus() {
		return status;
	}

}

class Room{
	private String name;
	private LinkedList<Client> clients;

	public Room(String name){
		this.name = name;
		clients = new LinkedList<>();
	}

	public void add_client(Client client){
		client.setRoom(this.name);
		clients.add(client);
		System.out.println(client.getNick() + " joined " + this.name);
	}

	public void remove_client(Client client){
		client.setRoom("");
		clients.remove(client);
		System.out.println(client.getNick() + " left " + this.name);
		if(this.clients.size() == 0) ChatServer.remove_room(this);
	}

	public String getName() {
		return name;
	}

	public LinkedList<Client> getClients() {
		return clients;
	}

}

public class ChatServer
{
	// A pre-allocated buffer for the received data
	static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

	// Decoder for incoming text -- assume UTF-8
	static private final Charset charset = Charset.forName("UTF8");
	static private final CharsetDecoder decoder = charset.newDecoder();
	static private final CharsetEncoder encoder = charset.newEncoder();
	static private String message = "";
	static LinkedList<Client> clients = new LinkedList<>();
	static LinkedList<Room> rooms = new LinkedList<>();

	static public void main( String args[] ) throws Exception {
		// Parse port from command line
		int port = Integer.parseInt( args[0] );

		try {
			// Instead of creating a ServerSocket, create a ServerSocketChannel
			ServerSocketChannel ssc = ServerSocketChannel.open();

			// Set it to non-blocking, so we can use select
			ssc.configureBlocking( false );

			// Get the Socket connected to this channel, and bind it to the
			// listening port
			ServerSocket ss = ssc.socket();
			InetSocketAddress isa = new InetSocketAddress( port );
			ss.bind( isa );

			// Create a new Selector for selecting
			Selector selector = Selector.open();

			// Register the ServerSocketChannel, so we can listen for incoming
			// connections
			ssc.register( selector, SelectionKey.OP_ACCEPT );
			System.out.println( "Listening on port "+port );

			while (true) {
				// See if we've had any activity -- either an incoming connection,
				// or incoming data on an existing connection
				int num = selector.select();

				// If we don't have any activity, loop around and wait again
				if (num == 0) {
					continue;
				}

				// Get the keys corresponding to the activity that has been
				// detected, and process them one by one
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> it = keys.iterator();
				while (it.hasNext()) {
					// Get a key representing one of bits of I/O activity
					SelectionKey key = it.next();

					// What kind of activity is it?
					if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
							SelectionKey.OP_ACCEPT) {

						// It's an incoming connection.  Register this socket with
						// the Selector so we can listen for input on it
						Socket s = ss.accept();
						System.out.println( "Got connection from "+s );

						// Make sure to make it non-blocking, so we can use a selector
						// on it.
						SocketChannel sc = s.getChannel();
						sc.configureBlocking( false );

						// Register it with the selector, for reading
						sc.register( selector, SelectionKey.OP_READ );

					} else if ((key.readyOps() & SelectionKey.OP_READ) ==
							SelectionKey.OP_READ) {

						SocketChannel sc = null;

						try {

							// It's incoming data on a connection -- process it
							sc = (SocketChannel)key.channel();
							boolean ok = processInput( sc );

							// If the connection is dead, remove it from the selector
							// and close it
							if (!ok) {
								key.cancel();

								Socket s = null;
								try {
									s = sc.socket();
									System.out.println( "Closing connection to "+s );
									s.close();
								} catch( IOException ie ) {
									System.err.println( "Error closing socket "+s+": "+ie );
								}
							}

						} catch( IOException ie ) {

							// On exception, remove this channel from the selector
							key.cancel();

							try {
								sc.close();
							} catch( IOException ie2 ) { System.out.println( ie2 ); }

							System.out.println( "Closed "+sc );
						}
					}
				}

				// We remove the selected keys, because we've dealt with them.
				keys.clear();
			}
		} catch( IOException ie ) {
			System.err.println( ie );
		}
	}


	// Just read the message from the socket and send it to stdout
	static private boolean processInput( SocketChannel sc ) throws IOException {
		// Read the message to the buffer
		buffer.clear();
		sc.read( buffer );
		buffer.flip();
		// If no data, close the connection
		if (buffer.limit()==0) {
			return false;
		}

		Client temp_client = existSocket(sc);
		if(temp_client == null){
			temp_client = new Client(sc);
			clients.push(temp_client);
			System.out.println("Created client "+clients.size());
		}

		// Decode and print the message to stdout
		message += decoder.decode(buffer).toString();
		if(message.contains("\n")){
			System.out.println("User-"+temp_client.getNick()+" sent:" + message );
			process_msg(temp_client,message);
			message = "";
		}
		return true;
	}

	static private void process_msg(Client client,String msg) throws CharacterCodingException, IOException{
		String messagesFromClient [] = msg.split("\n");
		for(String i:messagesFromClient){
			String messageFromClient [] = i.split(" ");
			switch(messageFromClient[0]){
			case("/nick"):
				if(get_client(messageFromClient[1]) == null){
					String old_nick = client.getNick();
					client.setNick(messageFromClient[1]);
					client.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
					broadcast(client, "NEWNICK " + old_nick + " " + client.getNick());
				}
				else{
					client.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
				}
			break;
			case("/join"):
				if(client.getRoom().equals("") && client.getStatus().equals("outside")){
					Room temp_room = verify_room(messageFromClient[1]);
					if(temp_room == null){
						temp_room = new Room(messageFromClient[1]);
						create_room(temp_room);
					}
					temp_room.add_client(client);
					client.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
					broadcast(client, "JOINED " + client.getNick());
				}
				else{
					client.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
				}
			break;
			case("/leave"):
				if(!client.getRoom().equals("")){
					Room temp_room = verify_room(client.getRoom());
					if(temp_room == null){
						client.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
					}
					else{
						client.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
						broadcast(client, "LEFT " + client.getNick());
						temp_room.remove_client(client);
					}
				}
				else{
					client.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
				}
			break;
			case("/bye"):
				Room temp_room = verify_room(client.getRoom());
			if(temp_room != null){
				broadcast(client, "LEFT " + client.getNick());
				temp_room.remove_client(client);
			}
			client.getSc().write(encoder.encode(CharBuffer.wrap("BYE\n")));
			clients.remove(client);
			break;
			case("/priv"):
			Client receiver = get_client(messageFromClient[1]);
			if(receiver == null){
				client.getSc().write(encoder.encode(CharBuffer.wrap("ERROR\n")));
			}
			else{
				client.getSc().write(encoder.encode(CharBuffer.wrap("OK\n")));
				String message ="";
				for(int k = 2; k<messageFromClient.length; k++){
					message += (messageFromClient[k]+ " ");
				}
				receiver.getSc().write(encoder.encode(CharBuffer.wrap("PRIVATE " + client.getNick() + " " + message+"\n")));
			}
			break;
			default:
				broadcast(client, "MESSAGE " + client.getNick()+" " + i);
				break;
			}
		}
	}

	static private void broadcast(Client client, String message) throws CharacterCodingException, IOException{
		if(client.getRoom().equals("")) return;
		Room toCast = verify_room(client.getRoom());
		for(int i = 0; i < toCast.getClients().size(); i++){
			if((message.startsWith("MESSAGE") && toCast.getClients().get(i) == client) || (toCast.getClients().get(i) != client))
				toCast.getClients().get(i).getSc().write(encoder.encode(CharBuffer.wrap(message+"\n")));
		}
	}

	static public void create_room(Room room){
		rooms.add(room);
		System.out.println("Created room:" + room.getName());
	}

	static public void remove_room(Room room){
		System.out.println("Removed room:" + room.getName());
		rooms.remove(room);
	}

	static private Client existSocket(SocketChannel sc){
		if(clients.isEmpty()) return null;
		for(int i = 0; i<clients.size(); i++)
			if(clients.get(i).getSc() == sc) return clients.get(i);
		return null;
	}

	static private Room verify_room(String room_name){
		if(rooms.isEmpty()) return null;
		for(int i = 0; i < rooms.size(); i++){
			if(rooms.get(i).getName().equals(room_name))return rooms.get(i);
		}
		return null;
	}

	static private Client get_client(String nick){
		if(clients.isEmpty()) return null;
		for(int i=0; i<clients.size(); i++)
			if(clients.get(i).getNick().equals(nick)) return clients.get(i);
		return null;
	}
}
