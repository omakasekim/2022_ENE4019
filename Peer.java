import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Peer {
    private static final String TERMINATE = "#EXIT";
    static String name = "TEST";
    static volatile boolean finished = false;
    public static String inputAddress;
    public static void main(String[] args) {
        if (args.length != 1)
            System.out.println("arguments required: <port-number>");
        else {
            while (true) {//to prevent actually exiting the program
                outerloop: while (true) {
                    Scanner sc = new Scanner(System.in);
                    //every EXIT means another possible JOIN
                    while (true) {
                        String tmp = sc.nextLine();
                        if (tmp.startsWith("#JOIN")) {
                            String[] buffer = tmp.split(" ");
                            inputAddress = buffer[1];
                            name = buffer[2];
                            break;
                        }
                    }

                    try {
                        //encryption process
                        SHA256 sha256 = new SHA256();
                        int port = Integer.parseInt(args[0]);
                        inputAddress = sha256.encrypt(inputAddress);
                        inputAddress = inputAddress.substring(inputAddress.length() - 6);
                        int x = Integer.parseInt(inputAddress.substring(0, 2), 16);
                        int y = Integer.parseInt(inputAddress.substring(2, 4), 16);
                        int z = Integer.parseInt(inputAddress.substring(4), 16);
                        String hashAddress = "225." + x + "." + y + "." + z;
                        MulticastSocket socket = new MulticastSocket(port);
                        InetAddress mcastaddr = InetAddress.getByName(hashAddress);
                        InetSocketAddress group = new InetSocketAddress(mcastaddr, port);
                        NetworkInterface Nif = socket.getNetworkInterface();
                        socket.joinGroup(group, Nif);

                        //Thread for concurrent usage.
                        Thread t = new Thread(new ReadThread(socket, group, port));

                        t.start();
                        while (true) {
                            String message;
                            message = sc.nextLine();
                            //EXIT
                            if (message.equalsIgnoreCase(Peer.TERMINATE)) {
                                finished = true;
                                socket.leaveGroup(group, Nif);
                                socket.close();
                                break outerloop;
                            }

                            message = name + ":" + message;
                            int count = 0;

                            byte[] buffer = null;
                            buffer = message.getBytes();
                            boolean allsent = false;

                            count =((buffer.length)/512)+1;

                            if(count > 2) {
                                for (int i = 0; i <= count; i++) {
                                    if(i==count){
                                        byte[] packet = Arrays.copyOfRange(buffer, i * 512, buffer.length);
                                        DatagramPacket datagram = new
                                                DatagramPacket(packet, 512, group.getAddress(), port);
                                        socket.send(datagram);
                                        break;
                                    }
                                    byte[] packet = Arrays.copyOfRange(buffer, i * 512, i * 512 + 512);
                                    DatagramPacket datagram = new
                                            DatagramPacket(packet, 512, group.getAddress(), port);
                                    socket.send(datagram);
                                }
                            }

                            DatagramPacket datagram = new
                                    DatagramPacket(buffer, buffer.length, group.getAddress(), port);
                            socket.send(datagram);
                            allsent = true;
                        }
                    } catch (SocketException se) {
                        System.out.println("Error creating socket");
                        se.printStackTrace();
                    } catch (IOException ie) {
                        System.out.println("Error reading/writing from/to socket");
                        ie.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}

class ReadThread implements Runnable {
    private MulticastSocket socket;
    private InetSocketAddress group;
    private int port;
    private static final int MAX_LEN = 512;
    //reads maximum 512 bytes
    ReadThread(MulticastSocket socket, InetSocketAddress group, int port) {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }

    @Override
    public void run() {
        while (!Peer.finished) {
            byte[] buffer = new byte[ReadThread.MAX_LEN];
            DatagramPacket datagram = new
                    DatagramPacket(buffer, buffer.length, group.getAddress(), port);
            String message;
            try {
                socket.receive(datagram);
                message = new
                        String(buffer, 0, datagram.getLength(), "UTF-8");
                if (!message.startsWith(Peer.name))
                    System.out.println(message);
            } catch (IOException e) {
                System.out.println("Socket closed!");
            }
        }
    }
}