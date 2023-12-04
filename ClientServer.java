import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ClientServer {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Ingrese su nombre de usuario: ");
        String username = scanner.nextLine();
        System.out.print("Ingrese su contraseña: ");
        String password = scanner.nextLine();

        // Autenticar con el servidor de manejo de usuarios
        if (authenticateUser(username, password)) {
        // Ruta donde se encuentran las películas
        String moviesDirectory = "C:\\Users\\wwwmp\\Desktop\\P4_SD2\\srtFiles";

        // Mostrar los nombres de las películas disponibles
        String[] movieNames = getMovieNames(moviesDirectory);
        displayMovieNames(movieNames);

        // Obtener la selección del usuario
        int selectedMovieIndex = getUserSelection(movieNames);

        if (selectedMovieIndex != -1) {
            String selectedMovieName = movieNames[selectedMovieIndex];

            // Enviar solicitud al servidor
            sendRequestToServer(selectedMovieName);
            sendLogActionToLoggingServer("Cliente realizó acción: Descargar subtítulos de " + selectedMovieName);
        }
        }
        else {
            // Autenticación fallida
            System.out.println("Autenticación fallida. Saliendo.");
        }
    }

    private static boolean authenticateUser(String username, String password) {
        try {
            String userHandlerUrl = "http://localhost:8082/";
            URL url = new URL(userHandlerUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Enviar las credenciales al servidor de manejo de usuarios
            String credentials = username + ":" + password;
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = credentials.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // Verificar el código de respuesta
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }
    private static String[] getMovieNames(String directoryPath) {
        File directory = new File(directoryPath);

        // Obtener la lista de archivos en el directorio
        File[] files = directory.listFiles();

        if (files == null) {
            System.out.println("Error al leer el directorio.");
            System.exit(1);
        }

        // Filtrar solo los archivos con extensiones reconocibles
        return java.util.Arrays.stream(files)
                .filter(file -> file.isFile() && isValidMovieFile(file.getName()))
                .map(file -> removeExtension(file.getName()))
                .toArray(String[]::new);
    }

    private static boolean isValidMovieFile(String fileName) {
        // Agrega extensiones de archivo de películas válidas según tus necesidades
        String[] validExtensions = {".srt"};
        for (String extension : validExtensions) {
            if (fileName.toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String removeExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    private static void displayMovieNames(String[] movieNames) {
        System.out.println("Películas disponibles:");
        for (int i = 0; i < movieNames.length; i++) {
            String modifiedMovieName = movieNames[i].replace("_", " ");
            System.out.println((i + 1) + ". " + modifiedMovieName);
        }
    }

    private static int getUserSelection(String[] movieNames) {
        try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
            System.out.print("Seleccione el número de la película deseada: ");
            int selectedMovieIndex = scanner.nextInt();

            // Validar la selección del usuario
            if (selectedMovieIndex < 1 || selectedMovieIndex > movieNames.length) {
                System.out.println("Selección no válida. Saliendo.");
                System.exit(1);
            }

            return selectedMovieIndex - 1;
        }
    }

    private static void sendRequestToServer(String selectedMovieName) {
        try {
            // Construir la URL para la solicitud al servidor de subtítulos
            String serverUrl = "http://localhost:8080/subtitle?movieName=" + selectedMovieName;
            URL url = new URL(serverUrl);

            // Abrir la conexión HTTP
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configurar la solicitud como GET
            connection.setRequestMethod("GET");

            // Obtener la respuesta del servidor
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Leer la respuesta del servidor
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;

                // Imprimir cada línea de subtítulo
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                reader.close();
            } else {
                System.out.println("Error al recibir los subtítulos. Código de respuesta: " + responseCode);
            }

            // Cerrar la conexión
            connection.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void sendLogActionToLoggingServer(String logAction) {
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
