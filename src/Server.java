
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*;


public class Server implements Runnable {
    private final int port;
    private ServerSocketChannel ssc;
    private Selector selector;
    private ByteBuffer buf = ByteBuffer.allocate(256);
    private HashMap<SocketChannel, String> users;

    Server(int port) throws IOException {
        this.port = port;
        this.ssc = ServerSocketChannel.open();
        this.ssc.socket().bind(new InetSocketAddress(port));
        this.ssc.configureBlocking(false);
        this.selector = Selector.open();
        this.ssc.register(selector, SelectionKey.OP_ACCEPT);
        this.users = new HashMap<>();
    }

    @Override public void run() {
        try {
            System.out.println("Server starting on port " + this.port);
            Iterator<SelectionKey> iter;
            SelectionKey key;
            while(this.ssc.isOpen()) {
                selector.select();
                iter=this.selector.selectedKeys().iterator();
                while(iter.hasNext()) {
                    key = iter.next();
                    iter.remove();

                    if(key.isAcceptable()) this.handleAccept(key);
                    if(key.isReadable()) this.handleRead(key);
                }
            }
        } catch(IOException e) {
            System.out.println("IOException, server of port " + this.port + " terminating. Stack trace:");
            e.printStackTrace();
        }
    }

    private final ByteBuffer welcomeBuf = ByteBuffer.wrap("Welcome!\n".getBytes());

    private void handleAccept(SelectionKey key) throws IOException {


        SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
        String address = (new StringBuilder( sc.socket().getInetAddress().toString() )).append(":").append( sc.socket().getPort() ).toString();
        sc.configureBlocking(false);

        sc.register(selector, SelectionKey.OP_READ, address);
        sc.write(welcomeBuf);
        welcomeBuf.rewind();
        this.users.put(sc, address);

        connectedClients(sc);  // Send all the previous connected clients to the new connected client with the correct signals

        broadcast('\u0004'+ " " + this.users.get(sc) + "\n");  // Inform all clients for a new connection

        sc.write(ByteBuffer.wrap(('\u0001'+" \n").getBytes()));  // Send signals to the new client to get a Nickname

        System.out.println("accepted connection from: "+ this.users.get(sc));


    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        StringBuilder sb = new StringBuilder();
        buf.clear();
        int read = 0;
        while( (read = ch.read(buf)) > 0 ) {
            buf.flip();
            byte[] bytes = new byte[buf.limit()];
            buf.get(bytes);
            sb.append(new String(bytes));
            buf.clear();
        }
        String msg;
        if(read<0) {
            msg = this.users.get(ch)+" left the chat.\n";
            ch.close();
            broadcast('\u0005' + " " +this.users.get(ch)+"\n"); //broadcast to all clients that this user left
            this.users.remove(ch);
        }
        else if(sb.toString().charAt(0) == '\u0001'){    //change the nickname of the user in the Hashmap and inform everybody
            String oldNick = this.users.get(ch);
            handleNickChanges(ch, sb.toString().substring(1).replace("\n",""));
            msg = oldNick + " has changed nickname to "+ this.users.get(ch) +"\n";
        }
        else {
            msg = this.users.get(ch)+": "+ sb.toString().replace("\n","")+ "\n";
        }
        System.out.print(msg);
        broadcast('\u0003' + " " +msg);
    }

    private void handleNickChanges(SocketChannel sc, String nick) throws IOException{

        broadcast('\u0005' + " " + this.users.get(sc) + "\n");
//        broadcast(this.users.get(sc) + " has changed nickname to "+ nick +"\n");
        this.users.remove(sc);
        this.users.put(sc, nick);
        broadcast('\u0004'+ " " + this.users.get(sc) + "\n");

    }

    private void broadcast(String msg) throws IOException {
        ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
        for(SelectionKey key : selector.keys()) {
            if(key.isValid() && key.channel() instanceof SocketChannel) {
                SocketChannel sch=(SocketChannel) key.channel();
                sch.write(msgBuf);
                msgBuf.rewind();
            }
        }
    }

    private void connectedClients(SocketChannel sc) throws IOException {

        this.users.forEach((key, value) -> {
            if (value != this.users.get(sc)) {
                ByteBuffer msgBuf = ByteBuffer.wrap(('\u0004' + " " + value + "\n").getBytes());
                try {
                    sc.write(msgBuf);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("error in something");
                }
            }
        });

    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("===============================");
            System.err.println("Inicia el servidor així:");
            System.err.println("  > java Server <port>");
            System.err.println("===============================");
            System.exit(1);
        }

        Server server = new Server(Integer.parseInt(args[0]));
        (new Thread(server)).start();
    }
}