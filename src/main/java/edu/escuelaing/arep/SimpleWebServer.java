package edu.escuelaing.arep;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The SimpleWebServer class represents a basic multithreaded web server.
 * It listens on a specified port and handles incoming client requests,
 * either by serving static files or by executing predefined services.
 */
public class SimpleWebServer {
    static final int PORT = 8080;
    private static boolean running = true;

    /**
     * The main method serves as the entry point of the SimpleWebServer.
     * It initializes the server, sets up a thread pool, and starts listening
     * for incoming client connections.
     *
     * @param args Command-line arguments (not used).
     * @throws IOException if an I/O error occurs when opening the server socket.
     */
    public static void main(String[] args) throws IOException {
        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Ready to receive on port " + PORT + "...");

        initialConfig();

        while (running) {
            Socket clientSocket = serverSocket.accept();
            threadPool.submit(new ClientHandler(clientSocket));
        }
        serverSocket.close();
        threadPool.shutdown();
    }

    /**
     * Sets up the initial configuration for the server, including defining
     * the location of static files and registering services for specific URLs.
     */
    static void initialConfig() {
        WebServer.staticfiles("src/main/java/edu/escuelaing/arep/resources/");;

        WebServer.get("/hello", (req, resp) -> {
            String name = WebServer.queryParams(req, "name");
            
            if (!name.isEmpty()) {
                try {
                    String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
    
                    String plainTextResponse = "Hola, " + decodedName;
                    System.out.println("Respuesta texto plano: " + plainTextResponse);
                    
                    return plainTextResponse;
                } catch (Exception e) {
                    return "Error al decodificar el nombre.";
                }
            } else {
                return "Error: No se proporcionó ningún nombre.";
            }
        });

        WebServer.get("/echo", (req, resp) -> {
            String message = req;
            if (!message.isEmpty()) {
                Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"(.*?)\"");
                Matcher matcher = pattern.matcher(message);
    
                String text;
                if (matcher.find()) {
                    text = matcher.group(1);
                } else {
                    text = "Error: Campo 'text' no encontrado";
                }
                String plainTextResponse = "Echo: " + text;
                System.out.println("Respuesta texto plano: " + plainTextResponse);
                return plainTextResponse;
            } else {
                return "Error: No se proporcionó ningún mensaje.";
            }
        });
    }

    /**
     * Stops the server by setting the running flag to false.
     * This method is used to gracefully shut down the server.
     */
    public static void stop() {
        running = false;
    }
}

/**
 * The ClientHandler class implements Runnable and is responsible for
 * handling individual client connections. It processes HTTP requests,
 * determines the type of request, and invokes the appropriate handler
 * for either serving static files or processing application-specific logic.
 */
class ClientHandler implements Runnable {
    private Socket clientSocket;

    /**
     * Constructs a new ClientHandler for the given client socket.
     *
     * @param socket The client socket to handle.
     */
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    /**
     * The run method is invoked when the ClientHandler is executed by a thread.
     * It reads the client's HTTP request, processes it based on the request type,
     * and sends back the appropriate response.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedOutputStream dataOut = new BufferedOutputStream(clientSocket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null)
                return;
            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String fileRequested = tokens[1];

            printRequestLine(requestLine, in);
            if (fileRequested.startsWith("/app")) {
                handleAppRequest(fileRequested,in, out);
            } else {
                if (method.equals("GET")) {
                    handleGetRequest(fileRequested, out, dataOut);
                } else if (method.equals("POST")) {
                    handlePostRequest(fileRequested, out, dataOut);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close(); 
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Prints the request line and headers from the client's HTTP request to the console.
     *
     * @param requestLine the initial request line (e.g., "GET /index.html HTTP/1.1").
     * @param in the BufferedReader for reading the client's request headers.
     */    
    private void printRequestLine(String requestLine, BufferedReader in) {
        System.out.println("Request line: " + requestLine);
        String inputLine;
        try {
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Header: " + inputLine);
                if (in.ready()) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles a GET request by serving a static file from the server's root directory.
     * If the file is found, it is sent to the client along with appropriate HTTP headers.
     * If the file is not found, a 404 error message is returned.
     *
     * @param fileRequested the file requested by the client.
     * @param out the PrintWriter to send the HTTP headers to the client.
     * @param dataOut the BufferedOutputStream to send the file data to the client.
     * @throws IOException if an I/O error occurs while reading the file or sending the response.
     */    
    private void handleGetRequest(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        File file = new File(WebServer.staticFilesLocation, fileRequested);
        int fileLength = (int) file.length();
        String content = getContentType(fileRequested);

        if (file.exists()) {
            byte[] fileData = readFileData(file, fileLength);
            out.println("HTTP/1.1 200 OK");
            out.println("Content-type: " + content);
            out.println("Content-length: " + fileLength);
            out.println();
            out.flush();
            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        } else {
            out.println("HTTP/1.1 404 Not Found");
            out.println("Content-type: text/html");
            out.println();
            out.flush();
            out.println("<html><body><h1>File Not Found</h1></body></html>");
            out.flush();
        }
    }

    /**
     * Handles a POST request by reading the request payload and returning a simple HTML
     * response that includes the received data.
     *
     * @param fileRequested the file requested by the client (not used in this method).
     * @param out the PrintWriter to send the HTTP headers and response to the client.
     * @param dataOut the BufferedOutputStream to send the response body to the client.
     * @throws IOException if an I/O error occurs while reading the input or sending the response.
     */
    private void handlePostRequest(String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        StringBuilder payload = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                payload.append(line);
            }
        }

        out.println("HTTP/1.1 200 OK");
        out.println("Content-type: text/html");
        out.println();
        out.println("<html><body><h1>POST data received:</h1>");
        out.println("<p>" + payload.toString() + "</p>");
        out.println("</body></html>");
        out.flush();
    }

    /**
     * Handles an application-specific request, delegating the processing to a registered
     * RESTful service based on the request method (GET or POST).
     *
     * @param fileRequested the specific endpoint requested (e.g., "/app/hello").
     * @param in the BufferedReader for reading the request body (for POST requests).
     * @param out the PrintWriter to send the response to the client.
     */
    private void handleAppRequest(String fileRequested, BufferedReader in, PrintWriter out) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-type: text/plain");
        out.println();
        String modifiedUrl = fileRequested.replaceFirst("^/app", "");
        String response = "";
    
        if (modifiedUrl.startsWith("/hello")) {
            response = WebServer.services.get("/hello").getValue(fileRequested,"");
        } else if (modifiedUrl.startsWith("/echo")) {
            try {
                String line;
                int contentLength = 0;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.startsWith("Content-Length:")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    }
                }
                char[] charArray = new char[contentLength];
                in.read(charArray, 0, contentLength);
                String requestBody = new String(charArray);
                response = WebServer.services.get("/echo").getValue(requestBody,"");
            } catch (IOException e) {
                e.printStackTrace();
                response = "Error al procesar la solicitud";
            }
                
        } else {
            response = "Error: Método no soportado";
        }
        out.println(response);
        out.flush();
    }

    /**
     * Determines the MIME type of the requested file based on its extension.
     *
     * @param fileRequested the file requested by the client.
     * @return the MIME type of the file.
     */
    String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".html"))
            return "text/html";
        else if (fileRequested.endsWith(".css"))
            return "text/css";
        else if (fileRequested.endsWith(".js"))
            return "application/javascript";
        else if (fileRequested.endsWith(".png"))
            return "image/png";
        else if (fileRequested.endsWith(".jpg"))
            return "image/jpeg";
        return "text/plain";
    }

    /**
     * Reads the contents of a file into a byte array.
     *
     * @param file the file to be read.
     * @param fileLength the length of the file in bytes.
     * @return a byte array containing the file's data.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];
        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null)
                fileIn.close();
        }
        return fileData;
    }
}
