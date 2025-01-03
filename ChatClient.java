import java.io.*;
import java.net.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to the chat server!");

            new Thread(() -> {
                String message;
                try {
                    while ((message = in.readLine()) != null) {
                        System.out.println("\n[Server]: " + message);
                        System.out.print("Message: ");
                    }
                } catch (IOException e) {
                    System.err.println("Error reading messages from the server.");
                }
            }).start();

            String userInput;
            while (true) {
                System.out.print("Message: ");
                userInput = consoleInput.readLine();
                if (userInput != null && !userInput.trim().isEmpty()) {
                    out.println(userInput);
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to connect to the chat server.");
        }
    }
}
