import javax.xml.crypto.Data;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class utils {
    private static final byte[] ALERT_ONLINE_BYTES = new byte[]{8, 4, 8, 4, 8};
    private static final byte[] REQUEST_CONNECT_BYTES = new byte[]{5, 7, 5, 7, 5};

    /*
    function that gets an array and returns a new one 0 padded to len
     */
    static byte[] zFill(byte[] buffer, int len)
    {
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
                byte[] tcpPort = utils.zFill(("" +  this.ports[0]).getBytes(StandardCharsets.US_ASCII), 5);
                byte[] udpPort = utils.zFill(("" +  this.ports[1]).getBytes(StandardCharsets.US_ASCII), 5);

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
                    byte[] controllerPort = utils.zFill(("" + controllerUDP.getPort()).getBytes(StandardCharsets.US_ASCII), 5);
                    controlledOutput.write(controllerPort, 0, 5);
                    System.out.println("sent controlled the address");

                    //send controller the address of the controlled
                    controllerOutput.write(controlledUDP.getAddress().getAddress());
                    byte[] controlledPort = utils.zFill(("" + controlledUDP.getPort()).getBytes(StandardCharsets.US_ASCII), 5);
                    controllerOutput.write(controlledPort, 0, 5);
                    System.out.println("sent controller the address");

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
                new DataOutputStream(controlledTCP.getOutputStream()).write(new byte[] { 1 }, 0, 1);
                new DataOutputStream(controllerTCP.getOutputStream()).write(new byte[] { 1 }, 0, 1);
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
            DataInputStream input;
            DataOutputStream output;
            byte[] length = new byte[10];
            try {
                input = new DataInputStream(this.controlled.getInputStream());
                output = new DataOutputStream(this.controller.getOutputStream());
                output.write(new byte[] { 0 });
                new DataOutputStream(this.controlled.getOutputStream()).write(new byte[] { 0 });
            } catch (Exception e) {
                System.out.println("Error at handle stream connection: " + e.toString());
                return;
            }
            try {
                while (true) {
                    int index = 0, count = 1024;
                    input.readFully(length, 0, 10);
                    output.write(length, 0,10);
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
}
