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
             Scanner scanner = new Scanner(System.in)) {

            String serverMessage = in.readLine();
            System.out.println(serverMessage); 

            while (true) {
                String input = scanner.nextLine();
                out.println(input);

                String response;
                while ((response = in.readLine()) != null) {
                    if (response.contains("Type /help for available commands")) {
                        handleChat(in, out, scanner);
                        return;
                    }
                    System.out.println(response);
                    if (response.contains("Enter") || response.contains("failed")) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Unable to connect to the chat server.");
        }
    }

    private static void handleChat(BufferedReader in, PrintWriter out, Scanner scanner) {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.print("\r" + " ".repeat(80) + "\r"); 
                    System.out.println(message);
                    System.out.print("Message: ");
                }
            } catch (IOException e) {
                System.err.println("Connection to server lost.");
            }
        }).start();

        while (true) {
            System.out.print("Message: ");
            String message = scanner.nextLine();
            if (message != null && !message.trim().isEmpty()) {
                out.println(message);
            }
        }
    }
}