import com.sun.net.httpserver.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;

public class SubtitleServer {
    private static final String SUBTITLE_ENDPOINT = "/subtitle";

    private final int port;
    private HttpServer server;

    public static void main(String[] args) {
        int serverPort = 8080;
        if (args.length == 1) {
            serverPort = Integer.parseInt(args[0]);
        }

        SubtitleServer subtitleServer = new SubtitleServer(serverPort);
        subtitleServer.startServer();

        System.out.println("Servidor de subtítulos escuchando en el puerto " + serverPort);
    }

    public SubtitleServer(int port) {
        this.port = port;
    }

    public void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        server.createContext(SUBTITLE_ENDPOINT, exchange -> {
            try {
                handleSubtitleRequest(exchange);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        // Agregar un shutdown hook para manejar la desactivación del servidor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Servidor de subtítulos desconectado.");
            sendLogActionToLoggingServer("Servidor de subtítulos desconectado.");
        }));
    }

    private void handleSubtitleRequest(HttpExchange exchange) throws IOException, InterruptedException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }

        // Obtener el nombre de la película desde la URL
        URI requestURI = exchange.getRequestURI();
        String query = requestURI.getRawQuery();
        String movieName = null;

        if (query != null) {
            String[] queryParams = query.split("&");
            for (String param : queryParams) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && keyValue[0].equalsIgnoreCase("movieName")) {
                    movieName = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.toString());
                    String modifiedMovieName = movieName.replace("_", " ");
                    System.out.println("Película pedida: " + modifiedMovieName);
                    break;
                }
            }
        }

        if (movieName == null || movieName.isEmpty()) {
            sendResponse("Nombre de película no proporcionado", exchange);
            return;
        }
        sendLogActionToLoggingServer("Servidor de subtítulos procesó la solicitud para la película: " + movieName);

        // Construir el nombre del archivo de subtítulos
        String subtitleFileName = movieName + ".srt";

        // Leer el contenido del archivo de subtítulos
        byte[] subtitleBytes;
        try {
            subtitleBytes = readSubtitleFile(subtitleFileName);
        } catch (IOException e) {
            sendResponse("Subtítulos no encontrados para la película: " + movieName, exchange);
            return;
        }

        // Enviar los subtítulos uno por uno con sincronización basada en el tiempo
        sendSubtitlesWithTiming(subtitleBytes, exchange);
    }

    private void sendSubtitlesWithTiming(byte[] subtitles, HttpExchange exchange) throws IOException, InterruptedException {
        String subtitleContent = new String(subtitles, StandardCharsets.UTF_8);

        // Utilizamos una expresión regular para separar bloques de subtítulos
        String[] subtitleBlocks = subtitleContent.split("\r\\r?\\n\\s*\\r+\\n+\n?\\s");

        for (String subtitleBlock : subtitleBlocks) {
            // Utilizamos expresiones regulares para extraer número, tiempo y texto del subtítulo
            String[] subtitleLines = subtitleBlock.split("\\r?\\n");

            String timeInfo = (subtitleLines.length > 1) ? subtitleLines[1] : "";
            long startTime = parseTime(timeInfo.split(" --> ")[0]);
            long endTime = parseTime(timeInfo.split(" --> ")[1]);

            // Calcular la duración del subtítulo actual
            long duration = endTime - startTime;

            String subtitleText = (subtitleLines.length > 2) ? String.join("\n", Arrays.copyOfRange(subtitleLines, 2, subtitleLines.length)) : "";

            String formattedSubtitle = String.format("%s\n%s", timeInfo, subtitleText);

            sendResponse(formattedSubtitle, exchange);

            System.out.println("Enviado subtítulo: " + formattedSubtitle);
            Thread.sleep(5000);
            try {
                // Retardo fijo de dos segundos entre cada subtítulo
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendLogActionToLoggingServer("Servidor de subtítulos realizó la conversión de tiempo a esta duracion: " + duration);

        }

        // Cerrar la conexión después de enviar todos los subtítulos
        exchange.close();
    }

    private long parseTime(String timeString) {
        // Parsear el tiempo en el formato HH:mm:ss,SSS
        String[] timeParts = timeString.split(":");
        long hours = Long.parseLong(timeParts[0]) * 60 * 60 * 1000;
        long minutes = Long.parseLong(timeParts[1]) * 60 * 1000;
        String[] secondsAndMillis = timeParts[2].split(",");
        long seconds = Long.parseLong(secondsAndMillis[0]) * 1000;
        long millis = Long.parseLong(secondsAndMillis[1]);
        return hours + minutes + seconds + millis;
    }

    private byte[] readSubtitleFile(String subtitleFileName) throws IOException {
        // Suponiendo que los archivos de subtítulos están en el directorio "subtitles"
        String subtitlesDirectory = "C:\\Users\\wwwmp\\Desktop\\P4_SD2\\srtFiles";
        String fullPath = subtitlesDirectory + File.separator + subtitleFileName;

        try (FileInputStream fileInputStream = new FileInputStream(fullPath);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            return byteArrayOutputStream.toByteArray();
        }
    }

    private void sendResponse(String responseString, HttpExchange exchange) throws IOException {
        byte[] responseBytes = responseString.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        sendLogActionToLoggingServer("Servidor de subtítulos mandó respuesta");
        outputStream.write(responseBytes);
        outputStream.flush();
    }

    // Método para enviar un log al servidor de logging
    private void sendLogActionToLoggingServer(String logAction) {
        try {
            String loggingServerUrl = "http://localhost:8081/";
            URL url = new URL(loggingServerUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = logAction.getBytes(StandardCharsets.UTF_8);
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
