import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
             Scanner scanner = new Scanner(System.in)) {

            System.out.print("Enter username: ");
            String username = scanner.nextLine();
            System.out.print("Enter password: ");
            String password = scanner.nextLine();

            out.println(username + ":" + password);

            System.out.println("Connected to the chat server!");

            new Thread(() -> {
                String message;
                try {
                    while ((message = in.readLine()) != null) {
                        System.out.print("\r" + " ".repeat(80) + "\r");
                        System.out.println(message);
                        System.out.print("Message: ");
                    }
                } catch (IOException e) {
                    System.err.println("Connection to server lost.");
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