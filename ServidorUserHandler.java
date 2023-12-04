import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ServidorUserHandler {
    private static final int USER_HANDLER_PORT = 8082;
    private static final String USERS_FILE_PATH = "Users.txt";

    public static void main(String[] args) {
        startUserHandlerServer();
    }

    private static void startUserHandlerServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(USER_HANDLER_PORT), 0);
            server.createContext("/", new UserHandler());
            server.setExecutor(null); // Use the default executor
            server.start();

            System.out.println("Servidor de manejo de usuarios escuchando en el puerto " + USER_HANDLER_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Obtener las credenciales del cliente
            String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)).readLine();
            String[] credentials = requestBody.split(":");
            String username = credentials[0];
            String password = credentials[1];

            // Validar las credenciales
            if (validateCredentials(username, password)) {
                // Credenciales válidas
                String response = "OK";
                sendLogActionToLoggingServer("Inicio de sesión válido de: "+username+" identificado con: "+password);
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                // Credenciales inválidas
                String response = "Invalid credentials";
                sendLogActionToLoggingServer("Inicio de sesión válido de: "+username+". Posible amenaza.\nLocalizar y exterminar.");
                exchange.sendResponseHeaders(401, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }

        private boolean validateCredentials(String username, String password) {
            try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE_PATH))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] userCredentials = line.split(":");
                    if (userCredentials.length == 2 && userCredentials[0].equals(username) && userCredentials[1].equals(password)) {
                        return true; // Credenciales válidas
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false; // Credenciales inválidas
        }

        private void sendLogActionToLoggingServer(String logAction) {
            try {
                String loggingServerUrl = "http://localhost:8081/log.txt";
                URL url = new URL(loggingServerUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
    
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = logAction.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
    
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.out.println("Error al enviar acción de log al servidor. Código de respuesta: " + responseCode);
                }
    
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
