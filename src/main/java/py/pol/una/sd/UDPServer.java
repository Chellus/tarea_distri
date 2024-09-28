package py.pol.una.sd;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
                try {
                    synchronized (this) {
                        this.wait();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void handleMessages(DatagramPacket packet) {
            try {

                String client_message = new String(packet.getData(), 0, packet.getLength());
                Object obj = new JSONParser().parse(client_message);
                JSONObject json_message = (JSONObject) obj;
                System.out.println("Recibido de " + client_address + ":" + client_port + " - " + client_message);

                // Responder al cliente
                JSONObject response = new JSONObject();
                byte[] send;
                DatagramPacket send_packet;

                if (json_message.get("tipo").equals("login")) {
                    user = (String) json_message.get("mensaje");
                    response.put("origen", "server");
                    if (users.containsKey(user)) {
                        response.put("login", false);
                        response.put("tipo", "error");
                        response.put("mensaje", "usuario ya existe");
                    }
                    else {
                        users.put(user, this);
                        response.put("login", true);
                        response.put("tipo", "mensaje");
                        response.put("mensaje", "Hola " + user);
                    }
                }
                // le vamos a enviar un mensaje a otro usuario
                else if (json_message.get("tipo").equals("mensaje")) {
                    String receptor = (String) json_message.get("destino");

                    if (!users.containsKey(receptor)) {
                        response.put("origen", "server");
                        response.put("tipo", "error");
                        response.put("mensaje", "El usuario no se encuentra conectado al servidor");
                    }
                    else {
                        users.get(receptor).sendMessage(user, (String) json_message.get("mensaje"));

                        response.put("origen", "server");
                        response.put("tipo", "info");
                        response.put("mensaje", "Mensaje enviado al usuario");
                    }

                }
                System.out.println(response);
                send = response.toString().getBytes();
                send_packet = new DatagramPacket(send, send.length, client_address, client_port);
                socket.send(send_packet);
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
            synchronized (this) {
                notify();
            }
        }

        public void sendMessage(String sender, String message) throws IOException {
            JSONObject message_json = new JSONObject();

            message_json.put("origen", sender);
            message_json.put("tipo", "mensaje");
            message_json.put("mensaje", message);

            byte[] send;
            DatagramPacket send_packet;
            System.out.println("Enviando mensaje a " + message);
            send = message_json.toString().getBytes();
            send_packet = new DatagramPacket(send, send.length, client_address, client_port);

            socket.send(send_packet);
            synchronized (this) {
                notify();
            }
        }
    }

}

