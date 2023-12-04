import com.sun.net.httpserver.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServidorLogging {
    private static final int LOGGING_PORT = 8081;
    private static final String LOG_FILE_PATH = "log.txt";

    public static void main(String[] args) {
        startLoggingServer();
    }

    private static void startLoggingServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(LOGGING_PORT), 0);
            server.createContext("/", new LoggingHandler());
            server.setExecutor(null); // Use the default executor
            server.start();

            System.out.println("Servidor de logging escuchando en el puerto " + LOGGING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class LoggingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Leer el mensaje enviado por otros servidores
            String logMessage = readMessageFromBody(exchange);

            // Agregar timestamp al mensaje
            String timestampedLogMessage = addTimestamp(logMessage);

            // Registro de la acción en el archivo de log
            writeLogToFile(timestampedLogMessage);

            // Respuesta simple al cliente
            String response = "Acción registrada en el servidor de logging.";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private String readMessageFromBody(HttpExchange exchange) throws IOException {
            // Leer el cuerpo del mensaje enviado por otros servidores
            InputStream is = exchange.getRequestBody();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            return body.toString();
        }

        private String addTimestamp(String message) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());
            return String.format("[%s] %s", timestamp, message);
        }

        private void writeLogToFile(String logMessage) {
            try (FileWriter fileWriter = new FileWriter(LOG_FILE_PATH, true);
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                 PrintWriter printWriter = new PrintWriter(bufferedWriter)) {
                printWriter.println(logMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Método estático para que otros servidores envíen mensajes de registro al servidor de logging
    public static void sendLogActionToLoggingServer(String logMessage) {
        try {
            String loggingServerUrl = "http://localhost:" + LOGGING_PORT + "/";
            URL url = new URL(loggingServerUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Enviar el mensaje al servidor de logging
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = logMessage.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // Verificar el código de respuesta
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("Error al enviar el mensaje al servidor de logging. Código de respuesta: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
