package py.pol.una.sd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class UDPServer {
    protected static final int PORT = 1234;
    protected static ConcurrentHashMap<String, ClientHandler> connections = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<String, ClientHandler> users = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try {
            DatagramSocket socket = new DatagramSocket(PORT);

            while (true) {
                byte[] receive = new byte[1024];
                DatagramPacket receive_packet = new DatagramPacket(receive, receive.length);

                // espera una solicitud
                socket.receive(receive_packet);

                InetAddress client_address = receive_packet.getAddress();
                int client_port = receive_packet.getPort();
                String client_key = client_address.toString() + ":" + client_port;

                // verificamos si el cliente ya tiene un handler
                if (!connections.containsKey(client_key)) {
                    // si no tiene, creamos un handler para este cliente
                    ClientHandler handler = new ClientHandler(socket, client_address, client_port);
                    connections.put(client_key, handler);
                    new Thread(handler).start();
                }

                // enviamos el mensaje al handler asignado al cliente
                connections.get(client_key).handleMessages(receive_packet);

            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static class ClientHandler implements Runnable {
        private final DatagramSocket socket;
        private final InetAddress client_address;
        private final int client_port;
        private String user;
        private volatile boolean running = true;

        public ClientHandler(DatagramSocket socket, InetAddress client_address, int client_port) {
            this.socket = socket;
            this.client_address = client_address;
            this.client_port = client_port;
            this.user = "";
        }

        @Override
        public void run() {
            while (running) {
                // espera a recibir mensajes
                // TODO: cerrar conexion cuando el cliente escriba chau, o con un timeout

            }
        }

        public void handleMessages(DatagramPacket packet) {
            try {
                String client_message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Recibido de " + client_address + ":" + client_port + " - " + client_message);

                // Responder al cliente
                String response;
                byte[] send;
                DatagramPacket send_packet;

                if (user.isEmpty()) {
                    user = client_message.trim();

                    if (users.containsKey(user)) {
                        response = "Ese nombre de usuario ya existe";
                    }
                    else {
                        users.put(user, this);
                        response = "server: Hola " + user;
                    }
                }
                // le vamos a enviar un mensaje a otro usuario
                else {
                    try {
                        String receptor = client_message.substring(0, client_message.indexOf(":"));

                        if (!users.containsKey(receptor)) {
                            response = "El usuario no se encuentra conectado al servidor";
                        }
                        else {
                            users.get(receptor).sendMessage(user + ":" + client_message.substring(client_message.indexOf(":") + 1));
                            response = "Mensaje enviado al usuario";
                        }
                    } catch (IndexOutOfBoundsException e) {
                        response = "Formato de mensaje incorrecto";
                    }
                }
                send = response.getBytes();
                send_packet = new DatagramPacket(send, send.length, client_address, client_port);
                socket.send(send_packet);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void sendMessage(String message) throws IOException {
            byte[] send;
            DatagramPacket send_packet;
            System.out.println("Enviando mensaje a " + message);
            send = message.getBytes();
            send_packet = new DatagramPacket(send, send.length, client_address, client_port);

            socket.send(send_packet);

        }
    }

}

