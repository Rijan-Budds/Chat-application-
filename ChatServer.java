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

    public static void main(String[] args) {
        initializeUsers();
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

    private static void initializeUsers() {
        userPasswords.put("Padma", "padma");
        userPasswords.put("Rijan", "rijan");
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

        private boolean authenticate(String credentials) {
            try {
                String[] parts = credentials.split(":");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    if (userPasswords.containsKey(username) && 
                        userPasswords.get(username).equals(password)) {
                        this.username = username;
                        return true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Authentication error: " + e.getMessage());
            }
            return false;
        }

        private void handleCommand(String message) {
            String[] parts = message.split("\\s+", 3);
            String command = parts[0].toLowerCase();

            switch (command) {
                case "/help":
                    sendHelpMessage();
                    break;
                case "/online":
                    listOnlineUsers();
                    break;
                case "/history":
                    sendMessageHistory();
                    break;
                case "/whisper":
                    if (parts.length >= 3) {
                        sendPrivateMessage(parts[1], parts[2]);
                    } else {
                        out.println("Usage: /whisper <username> <message>");
                    }
                    break;
                case "/createroom":
                    if (parts.length >= 2) {
                        createChatRoom(parts[1]);
                    } else {
                        out.println("Usage: /createroom <roomname>");
                    }
                    break;
                case "/join":
                    if (parts.length >= 2) {
                        joinChatRoom(parts[1]);
                    } else {
                        out.println("Usage: /join <roomname>");
                    }
                    break;
                default:
                    out.println("Unknown command. Type /help for available commands.");
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
                if (!found) {
                    out.println("User '" + recipient + "' is not online.");
                }
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

                out.println("Please enter username:password");
                String credentials = in.readLine();
                
                if (!authenticate(credentials)) {
                    out.println("Authentication failed!");
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