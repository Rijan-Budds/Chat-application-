import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final Map<String, String> userPasswords = new HashMap<>();
    private static final List<String> messageHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 100;
    private static final Map<String, Set<String>> chatRooms = new HashMap<>();
    private static final String USER_DATA_FILE = "users.txt";

    public static void main(String[] args) {
        loadUsers();
        System.out.println("Chat server started on port " + PORT + "...");
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

    private static void loadUsers() {
        try {
            File file = new File(USER_DATA_FILE);
            if (!file.exists()) {
                file.createNewFile();
                return;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        userPasswords.put(parts[0], parts[1]);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading users: " + e.getMessage());
        }
    }

    private static synchronized void saveUsers() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_DATA_FILE))) {
            for (Map.Entry<String, String> entry : userPasswords.entrySet()) {
                writer.println(entry.getKey() + ":" + entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
        }
    }

    private static synchronized boolean registerUser(String username, String password) {
        if (userPasswords.containsKey(username)) {
            return false;
        }
        userPasswords.put(username, password);
        saveUsers();
        return true;
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String currentRoom = "main";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        private boolean handleInitialConnection() throws IOException {
            out.println("Welcome! Enter '1' to login or '2' to register:");
            String choice = in.readLine();

            if ("2".equals(choice)) {
                return handleRegistration();
            } else {
                return handleLogin();
            }
        }

        private boolean handleRegistration() throws IOException {
            out.println("Enter desired username:");
            String newUsername = in.readLine();

            if (newUsername == null || newUsername.trim().isEmpty() || userPasswords.containsKey(newUsername)) {
                out.println("Invalid username or already exists!");
                return false;
            }

            out.println("Enter password:");
            String newPassword = in.readLine();

            if (newPassword == null || newPassword.trim().isEmpty()) {
                out.println("Invalid password!");
                return false;
            }

            if (registerUser(newUsername, newPassword)) {
                this.username = newUsername;
                return true;
            }
            return false;
        }

        private boolean handleLogin() throws IOException {
            out.println("Enter username:");
            String username = in.readLine();
            out.println("Enter password:");
            String password = in.readLine();

            if (userPasswords.containsKey(username) && userPasswords.get(username).equals(password)) {
                this.username = username;
                return true;
            }
            out.println("Login failed!");
            return false;
        }

        private void handleCommand(String message) {
            String[] parts = message.split("\\s+", 3);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "/help": sendHelpMessage(); break;
                case "/online": listOnlineUsers(); break;
                case "/history": sendMessageHistory(); break;
                case "/whisper":
                    if (parts.length >= 3) sendPrivateMessage(parts[1], parts[2]);
                    else out.println("Usage: /whisper <username> <message>");
                    break;
                case "/createroom":
                    if (parts.length >= 2) createChatRoom(parts[1]);
                    else out.println("Usage: /createroom <roomname>");
                    break;
                case "/join":
                    if (parts.length >= 2) joinChatRoom(parts[1]);
                    else out.println("Usage: /join <roomname>");
                    break;
                default: out.println("Unknown command. Type /help for available commands.");
            }
        }

        private void sendHelpMessage() {
            out.println("\nAvailable commands:");
            out.println("/help - Show this help message");
            out.println("/online - List all online users");
            out.println("/history - Show message history");
            out.println("/whisper <username> <message> - Send private message");
            out.println("/createroom <roomname> - Create a new chat room");
            out.println("/join <roomname> - Join an existing chat room");
        }

        private void listOnlineUsers() {
            synchronized (clients) {
                StringBuilder sb = new StringBuilder("\nOnline users:\n");
                for (ClientHandler client : clients) {
                    sb.append("- ").append(client.username)
                      .append(" (in room: ").append(client.currentRoom).append(")\n");
                }
                out.println(sb.toString());
            }
        }

        private void sendMessageHistory() {
            synchronized (messageHistory) {
                out.println("\nLast " + Math.min(messageHistory.size(), MAX_HISTORY) + " messages:");
                for (String msg : messageHistory) {
                    out.println(msg);
                }
                out.println("End of history\n");
            }
        }

        private void sendPrivateMessage(String recipient, String message) {
            synchronized (clients) {
                boolean found = false;
                for (ClientHandler client : clients) {
                    if (client.username.equalsIgnoreCase(recipient)) {
                        client.out.println("\n[Private from " + username + "]: " + message);
                        out.println("\n[Private to " + recipient + "]: " + message);
                        found = true;
                        break;
                    }
                }
                if (!found) out.println("User '" + recipient + "' is not online.");
            }
        }

        private void createChatRoom(String roomName) {
            synchronized (chatRooms) {
                if (chatRooms.containsKey(roomName)) {
                    out.println("Room '" + roomName + "' already exists.");
                    return;
                }
                chatRooms.put(roomName, new HashSet<>());
                chatRooms.get(roomName).add(username);
                currentRoom = roomName;
                out.println("Created and joined room: " + roomName);
                broadcast(username + " created room: " + roomName);
            }
        }

        private void joinChatRoom(String roomName) {
            synchronized (chatRooms) {
                if (!chatRooms.containsKey(roomName)) {
                    out.println("Room '" + roomName + "' doesn't exist.");
                    return;
                }
                if (chatRooms.containsKey(currentRoom)) {
                    chatRooms.get(currentRoom).remove(username);
                }
                chatRooms.get(roomName).add(username);
                currentRoom = roomName;
                out.println("Joined room: " + roomName);
                broadcast(username + " joined room: " + roomName);
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                if (!handleInitialConnection()) {
                    socket.close();
                    return;
                }

                out.println("Welcome " + username + "! Type /help for available commands.");
                broadcast(username + " has joined the chat.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/")) {
                        handleCommand(message);
                    } else if (!message.trim().isEmpty()) {
                        String timestampedMessage = String.format("[%s] %s: %s",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            username, message);
                        broadcast(timestampedMessage);
                        
                        synchronized (messageHistory) {
                            messageHistory.add(timestampedMessage);
                            if (messageHistory.size() > MAX_HISTORY) {
                                messageHistory.remove(0);
                            }
                        }
                    }
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
                    if (client.currentRoom.equals(this.currentRoom)) {
                        client.out.println(message);
                    }
                }
            }
        }

        private void disconnect() {
            synchronized (clients) {
                clients.remove(this);
            }
            synchronized (chatRooms) {
                if (currentRoom != null && chatRooms.containsKey(currentRoom)) {
                    chatRooms.get(currentRoom).remove(username);
                }
            }
            if (username != null) {
                broadcast(username + " has left the chat.");
            }
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket for " + username);
            }
        }
    }
}