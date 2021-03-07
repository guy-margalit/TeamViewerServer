import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;


public class TCPConnectorServer {
    public int idLength; //the length of the id string
    private ServerSocket socket; //the main server socket
    private LinkedList<Integer> ports; //the total used ports list
    private HashMap<String, String> clients; //the id, ip HashMap
    private HashMap<String, Socket> calls; //the recipient id, call packet HashMap
    public static final int CALL_PORT = 7945; //the port where the client is waiting for a call
    public static final int MAIN_PORT = 9725; //the main port of the server
    public static final int PASSWORD_LENGTH = 10; //the length of the password string
    public static final byte[] ALERT_ONLINE_BYTES = new byte[]{8, 4, 8, 4, 8}; //the client request to alert online
    public static final byte[] CALL_BYTES = new byte[]{5, 7, 5, 7, 5}; //the client request to connect
    public static final byte[] CHECK_CALL_BYTES = new byte[]{9, 3, 9, 3, 9}; //the client request to check if he has a call
    public static final byte[] YES = new byte[]{1}; //represents YES in the protocol
    public static final byte[] NO = new byte[]{0}; //represents NO in the protocol


    /*
    constructor the takes id length and initiates the main server socket
     */
    private TCPConnectorServer(int idLength) {
        this.idLength = idLength;
        this.clients = new HashMap<>();
        this.calls = new HashMap<>();
        this.ports = new LinkedList<>();
        try {
            this.socket = new ServerSocket(MAIN_PORT);
        } catch (Exception e) {
            System.out.println("Error at creating server socket: " + e.toString());
        }
    }

    /*
    function that generates 2 port that aren't in use
     */
    private int[] generatePorts() {
        int[] ports = new int[2];
        for (int i = 0; i < 2; i++) {
            while (true) {
                ports[i] = (int) (Math.random() * 500) + 6000;
                if (!this.ports.contains(ports[i])) {
                    this.ports.add(ports[i]);
                    break;
                }
            }
        }
        return ports;
    }

    private String findIDByIP(String ip) {
        if (!this.clients.containsValue(ip))
            return null;
        for (String clientID : this.clients.keySet())
            if (this.clients.get(clientID).equals(ip))
                return clientID;
        return null;
    }

    /*
    function that generates id (it checks that it is available)
     */
    private byte[] generateId(String ip) {
        if (this.clients.containsValue(ip))
            return findIDByIP(ip).getBytes();
        int leftLimit = 48; // ascii number 0
        int rightLimit = 122; // ascii letter z
        StringBuilder id;
        int chr;
        while (true) {
            id = new StringBuilder();
            for (int i = 0; i < this.idLength;) {
                chr = (int) (Math.random() * (rightLimit - leftLimit + 1)) + leftLimit;
                if (chr <= 57 || (chr >= 65 && chr < 91) || chr > 96) {
                    id.append((char) chr);
                    i++;
                }
            }
            if (!this.clients.containsKey(id.toString())) {
                this.clients.put(id.toString(), ip);
                break;
            }
        }
        return id.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /*
    private void startConnection(Socket client1, Socket client2) {
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            byte[] idBytes = new byte[this.idLength];
            byte[] passwordBytes = new byte[PASSWORD_LENGTH];
            input.readFully(idBytes, 0, this.idLength);
            input.readFully(passwordBytes, 0, PASSWORD_LENGTH);
            String id = new String(idBytes, StandardCharsets.US_ASCII);
            System.out.println("given id: " + id);
            if (!this.clients.containsKey(id)) {
                output.write(new byte[5], 0, 5);
                output.write(new byte[5], 0, 5);
                return;
            }
            Socket client = new Socket(this.clients.get(id), CALL_PORT);
            System.out.println("Asking client");
            boolean answer = utils.askClient(client, passwordBytes);
            System.out.println("Answer: " + answer);
            if (answer) {
                int port = generatePort();
                System.out.println(port);
                utils.connect(socket, client, ports[0], ports[1]);
            } else {
                output.write(new byte[5], 0, 5);
                output.write(new byte[5], 0, 5);
            }
        } catch (Exception e) {
            System.out.println("Error starting connection: " + e.toString());
            try {
                socket.close();
            } catch (Exception err) {
                System.out.println("Error closing socket: " + err.toString());
            }
        }
    }
    */

    /*
    function that gets an id from a client socket
     */
    private String getID(Socket client) throws IOException {
        DataInputStream input = new DataInputStream(client.getInputStream());
        byte[] idBytes = new byte[this.idLength];
        input.readFully(idBytes, 0, this.idLength);
        return new String(idBytes, StandardCharsets.US_ASCII);
    }

    /*
    function that check if there is a call to the client
     */
    private void checkCall(Socket client) throws IOException {
        DataOutputStream output = new DataOutputStream(client.getOutputStream());
        String id = this.getID(client);
        if (!this.clients.containsKey(id)) {
            output.write(NO, 0, 1);
        } else {
            if (this.calls.containsKey(id)) {
                Socket caller = this.calls.get(id);
                this.calls.remove(id);
                DataOutputStream callerOutput = new DataOutputStream(caller.getOutputStream());
                DataInputStream callerInput = new DataInputStream(caller.getInputStream());
                callerOutput.write(YES, 0, 1);
                byte[] answer = new byte[1];
                callerInput.readFully(answer);
                output.write(answer, 0, 1);
                if (Arrays.equals(answer, YES)) {
                    System.out.println("Has call");
                    new utils.HandleConnection(caller, client, this.generatePorts()).start();
                }
            } else {
                output.write(NO, 0, 1);
            }
        }
    }

    /*
    function that gets the id from the client and registers a call
     */
    private void registerCall(Socket client) throws IOException {
        DataOutputStream output = new DataOutputStream(client.getOutputStream());
        String id = this.getID(client);
        System.out.println("given id: " + id);
        if (!this.clients.containsKey(id) || this.calls.containsKey(id)) {
            output.write(NO, 0, 1);
            return;
        }
        this.calls.put(id, client);
    }

    /*
     main function that receives clients and follows requests
     */
    public static void main(String... args) {
        Socket client;
        DataInputStream input;
        DataOutputStream output;
        byte[] request = new byte[5];
        TCPConnectorServer server = new TCPConnectorServer(10);
        while (true) {
            try {
                client = server.socket.accept();
                System.out.println("Received client");
                input = new DataInputStream(client.getInputStream());
                input.readFully(request, 0, 5);
                if (Arrays.equals(request, CALL_BYTES)) {
                    System.out.println("Registering call...");
                    server.registerCall(client);
                    System.out.println("Registered.");
                } else if (Arrays.equals(request, ALERT_ONLINE_BYTES)) {
                    output = new DataOutputStream(client.getOutputStream());
                    output.write(server.generateId(client.getInetAddress().getHostAddress()));
                    System.out.println(server.clients);
                } else if (Arrays.equals(request, CHECK_CALL_BYTES)) {
                    System.out.println("Checking calls...");
                    server.checkCall(client);
                }
            } catch (Exception e) {
                System.out.println("Error at main loop: " + e.toString());
            }
        }
    }
}
