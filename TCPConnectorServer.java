import java.io.*;
import java.net.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            for (int i = 0; i < this.idLength; ) {
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
            boolean answer = askClient(client, passwordBytes);
            System.out.println("Answer: " + answer);
            if (answer) {
                int port = generatePort();
                System.out.println(port);
                connect(socket, client, ports[0], ports[1]);
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
                    new HandleConnection(caller, client, this.generatePorts()).start();
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
    function that gets an array and returns a new one 0 padded to len
     */
    static byte[] zFill(byte[] buffer, int len) {
        if (buffer.length == len) return buffer;
        byte[] filled = new byte[len];
        for (int i = 0; i < len; i++)
            filled[i] = i < (len - buffer.length) ? (byte) 48 : buffer[i - len + buffer.length];
        return filled;
    }

    /*
    the thread that handles the connection between 2 clients
     */
    static class HandleConnection extends Thread {
        int[] ports;
        Socket controlled, controller;

        HandleConnection(Socket controller, Socket controlled, int[] ports) {
            this.controlled = controlled;
            this.controller = controller;
            this.ports = new int[ports.length];
            for (int i = 0; i < ports.length; i++) {
                this.ports[i] = ports[i];
            }
        }

        @Override
        public void run() {
            System.out.println("Entered handle connection...");
            try {
                //turn the ports to byte arrays
                byte[] tcpPort = zFill(("" + this.ports[0]).getBytes(StandardCharsets.US_ASCII), 5);
                byte[] udpPort = zFill(("" + this.ports[1]).getBytes(StandardCharsets.US_ASCII), 5);

                //instantiate input and output streams
                DataInputStream controlledInput, controllerInput;
                DataOutputStream controlledOutput, controllerOutput;
                controlledInput = new DataInputStream(this.controlled.getInputStream());
                controllerInput = new DataInputStream(this.controller.getInputStream());
                controlledOutput = new DataOutputStream(this.controlled.getOutputStream());
                controllerOutput = new DataOutputStream(this.controller.getOutputStream());

                //check the password
                byte[] password = new byte[TCPConnectorServer.PASSWORD_LENGTH];
                controllerInput.readFully(password, 0, TCPConnectorServer.PASSWORD_LENGTH);
                System.out.println("checking password: " + Arrays.toString(password));
                controlledOutput.write(password, 0, TCPConnectorServer.PASSWORD_LENGTH);

                //receive the response from the controlled and send to the controller
                byte[] answer = new byte[1];
                controlledInput.readFully(answer, 0, answer.length);
                controllerOutput.write(answer, 0, answer.length);
                System.out.println("answer: " + Arrays.toString(answer));
                if (Arrays.equals(answer, TCPConnectorServer.NO))
                    return;

                byte[] forceTCP = new byte[1];
                controllerInput.readFully(forceTCP, 0, forceTCP.length);
                controlledOutput.write(forceTCP, 0, forceTCP.length);

                if (forceTCP[0] == 0) {
                    //initiate udp server socket
                    DatagramPacket controllerUDP, controlledUDP;
                    DatagramSocket udpServer = new DatagramSocket(this.ports[1]);

                    //receive controlled address using udp packet
                    controlledOutput.write(udpPort, 0, 5);
                    controlledUDP = new DatagramPacket(new byte[1], 1);
                    udpServer.receive(controlledUDP);
                    System.out.println("connected controlled udp");

                    //receive controller address using udp packet
                    controllerOutput.write(udpPort, 0, 5);
                    controllerUDP = new DatagramPacket(new byte[1], 1);
                    udpServer.receive(controllerUDP);
                    System.out.println("connected controller udp");

                    //send controlled the address of the controller
                    controlledOutput.write(controllerUDP.getAddress().getAddress());
                    byte[] controllerPort = zFill(("" + controllerUDP.getPort()).getBytes(StandardCharsets.US_ASCII), 5);
                    controlledOutput.write(controllerPort, 0, 5);
                    System.out.println("sent controlled the address");

                    //send controller the address of the controlled
                    controllerOutput.write(controlledUDP.getAddress().getAddress());
                    byte[] controlledPort = zFill(("" + controlledUDP.getPort()).getBytes(StandardCharsets.US_ASCII), 5);
                    controllerOutput.write(controlledPort, 0, 5);
                    System.out.println("sent controller the address");
    
                    controllerInput.readFully(new byte[1], 0, 1);
                    controlledOutput.write(new byte[] { 1 }, 0, 1);
                    controllerOutput.write(new byte[] { 1 }, 0, 1);
                    
                    //receive status byte from both clients
                    byte[] controllerStatus = new byte[1];
                    byte[] controlledStatus = new byte[1];
                    controlledInput.readFully(controlledStatus, 0, controlledStatus.length);
                    controllerInput.readFully(controllerStatus, 0, controllerStatus.length);

                    if (!(controlledStatus[0] == 1 && controllerStatus[0] == 1)) {
                        //if the hole punching failed
                        controlledOutput.write(TCPConnectorServer.NO, 0, 1);
                        controllerOutput.write(TCPConnectorServer.NO, 0, 1);
                        udpServer.close();
                        byte[] identifier1 = new byte[1];
                        byte[] identifier2 = new byte[1];
                        Socket controllerStream = null, controlledStream = null, temp;
                        ServerSocket streamServer = new ServerSocket(this.ports[1]);
                        temp = streamServer.accept();
                        new DataInputStream(temp.getInputStream()).readFully(identifier1);
                        if (identifier1[0] == 1)
                            controllerStream = temp;
                        else
                            controlledStream = temp;
                        temp = streamServer.accept();
                        new DataInputStream(temp.getInputStream()).readFully(identifier2);
                        if (identifier2[0] == 1)
                            controllerStream = temp;
                        else
                            controlledStream = temp;
                        new HandleStream(controllerStream, controlledStream).start();
                    } else {
                        //if the hole punching was successful
                        controlledOutput.write(TCPConnectorServer.YES, 0, 1);
                        controllerOutput.write(TCPConnectorServer.YES, 0, 1);
                    }
                } else {
                    controllerOutput.write(udpPort, 0, 5);
                    controlledOutput.write(udpPort, 0, 5);

                    byte[] identifier1 = new byte[1];
                    byte[] identifier2 = new byte[1];
                    Socket controllerStream = null, controlledStream = null, temp;
                    ServerSocket streamServer = new ServerSocket(this.ports[1]);
                    temp = streamServer.accept();
                    new DataInputStream(temp.getInputStream()).readFully(identifier1);
                    if (identifier1[0] == 1)
                        controllerStream = temp;
                    else
                        controlledStream = temp;
                    temp = streamServer.accept();
                    new DataInputStream(temp.getInputStream()).readFully(identifier2);
                    if (identifier2[0] == 1)
                        controllerStream = temp;
                    else
                        controlledStream = temp;
                    new HandleStream(controllerStream, controlledStream).start();
                }
                //initiate commands tcp server socket
                Socket controllerTCP, controlledTCP;
                ServerSocket tcpServer = new ServerSocket(this.ports[0]);

                //connect the controlled commands
                controlledOutput.write(tcpPort, 0, 5);
                controlledTCP = tcpServer.accept();
                System.out.println("connected controlled commands");

                //connect the controller commands socket
                controllerOutput.write(tcpPort, 0, 5);
                controllerTCP = tcpServer.accept();
                System.out.println("connected controller commands");

                //send starting byte and start commands thread
                new DataOutputStream(controlledTCP.getOutputStream()).write(new byte[]{1}, 0, 1);
                new DataOutputStream(controllerTCP.getOutputStream()).write(new byte[]{1}, 0, 1);
                new HandleCommands(controllerTCP, controlledTCP).start();
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
    }

    static class HandleStream extends Thread {
        Socket controlled, controller;

        HandleStream(Socket controller, Socket controlled) {
            this.controlled = controlled;
            this.controller = controller;
        }

        @Override
        public void run() {
            System.out.println("Entered handle stream...");
            DataInputStream input, controllerInput;
            DataOutputStream output, controlledOutput;
            byte[] length = new byte[10];
            try {
                input = new DataInputStream(this.controlled.getInputStream());
                output = new DataOutputStream(this.controller.getOutputStream());
                controllerInput = new DataInputStream(this.controller.getInputStream()).readFully(new byte[1], 0, 1);
                output.write(new byte[]{0});
                controlledOutput = new DataOutputStream(this.controlled.getOutputStream()).write(new byte[]{0});
            } catch (Exception e) {
                System.out.println("Error at handle stream connection: " + e.toString());
                return;
            }
            try {
                while (true) {
                    int index = 0, count = 10240;
                    input.readFully(length, 0, 10);
                    output.write(length, 0, 10);
                    System.out.println(new String(length, StandardCharsets.US_ASCII));
                    int len = Integer.parseInt(new String(length, StandardCharsets.US_ASCII));
                    System.out.println("length: " + len);
                    byte[] data = new byte[len];
                    while (index < len) {
                        if ((index + count) > len)
                            count = len - index;
                        input.readFully(data, index, count);
                        output.write(data, index, count);
                        index += count;
                        System.out.println("index: " + index);
                    }
                    byte[] end = new byte[1];
                    controlledInput.readFully(end, 0, 1);
                    controllerOutput.write(end, 0, 1);
                }
            } catch (Exception e) {
                System.out.println("Error at handle stream: " + e.toString());
            }
        }
    }

    static class HandleCommands extends Thread {
        Socket controlled, controller;

        HandleCommands(Socket controller, Socket controlled) {
            this.controlled = controlled;
            this.controller = controller;
        }

        @Override
        public void run() {
            System.out.println("Entered handle commands...");
            DataInputStream input;
            DataOutputStream output;
            byte[] data = new byte[6];
            try {
                input = new DataInputStream(this.controller.getInputStream());
                output = new DataOutputStream(this.controlled.getOutputStream());
            } catch (Exception e) {
                System.out.println("Error at handle commands connection: " + e.toString());
                return;
            }
            try {
                while (true) {
                    input.readFully(data, 0, 6);
                    output.write(data, 0, 6);
                    System.out.println("commands: " + Arrays.toString(data));
                }
            } catch (Exception e) {
                System.out.println("Error at handle commands: " + e.toString());
            }
        }
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
