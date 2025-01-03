import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final List<String> availableUsernames = new ArrayList<>(Arrays.asList("user1", "user2", "user3", "user4"));

    public static void main(String[] args) {
        System.out.println("Chat server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                synchronized (clients) {
                    clients.add(clientHandler);
                }
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                synchronized (availableUsernames) {
                    if (availableUsernames.isEmpty()) {
                        out.println("Server is full!");
                        socket.close();
                        return;
                    }
                    username = availableUsernames.remove(0);
                }
                broadcast(username + " has joined the chat.");

                String message;
                while ((message = in.readLine()) != null) {
                    broadcast(username + ": " + message);
                }
            } catch (IOException e) {
                System.err.println("Connection error with " + username);
            } finally {
                disconnect();
            }
        }

        private void broadcast(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.out.println(message);
                }
            }
        }

        private void disconnect() {
            synchronized (clients) {
                clients.remove(this);
            }
            synchronized (availableUsernames) {
                availableUsernames.add(username); 
            }
            broadcast(username + " has left the chat.");
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket for " + username);
            }
        }
    }
}
