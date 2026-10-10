package com.universidad.espacios;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.universidad.espacios.controller.ApiHandler;
import com.universidad.espacios.service.DataStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class Main {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (Exception ignored) {}
        } else if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception ignored) {}
        }

        try {
            DataStore dataStore = new DataStore();
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Manejador de la API REST
            server.createContext("/api", new ApiHandler(dataStore));

            // Manejador de archivos estáticos (Frontend HTML, CSS, JS)
            server.createContext("/", new StaticFileHandler());

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("==================================================================");
            System.out.println("   APP ESPACIOS UNIVERSITARIOS - SERVIDOR BACKEND JAVA INICIADO   ");
            System.out.println("==================================================================");
            System.out.println(" * Servidor activo en: http://localhost:" + port);
            System.out.println(" * Frontend Web:       http://localhost:" + port + "/");
            System.out.println(" * Endpoints API:      http://localhost:" + port + "/api/espacios");
            System.out.println("                       http://localhost:" + port + "/api/reservas");
            System.out.println("                       http://localhost:" + port + "/api/usuarios");
            System.out.println("                       http://localhost:" + port + "/api/stats");
            System.out.println(" * Presione Ctrl + C para detener el servidor");
            System.out.println("==================================================================");

        } catch (IOException e) {
            System.err.println("Error fatal al iniciar el servidor en el puerto " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sirve los archivos estáticos de la carpeta frontend
     */
    static class StaticFileHandler implements HttpHandler {
        private final File frontendDir;

        public StaticFileHandler() {
            // Intentar buscar la carpeta frontend en varias ubicaciones posibles
            File f1 = new File("frontend");
            File f2 = new File("../frontend");
            File f3 = new File("./frontend");
            if (f1.exists()) {
                this.frontendDir = f1;
            } else if (f2.exists()) {
                this.frontendDir = f2;
            } else {
                this.frontendDir = f3;
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File requestedFile = new File(frontendDir, path.substring(1));
            if (!requestedFile.exists() || requestedFile.isDirectory()) {
                // Fallback a index.html para Single Page Application
                requestedFile = new File(frontendDir, "index.html");
            }

            if (!requestedFile.exists()) {
                String notFound = "<h1>404 - Archivo no encontrado</h1><p>Coloque el frontend en la carpeta 'frontend'.</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes());
                }
                return;
            }

            String contentType = determineContentType(requestedFile.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, requestedFile.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(requestedFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private String determineContentType(String name) {
            if (name.endsWith(".html")) return "text/html; charset=UTF-8";
            if (name.endsWith(".css")) return "text/css; charset=UTF-8";
            if (name.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (name.endsWith(".json")) return "application/json; charset=UTF-8";
            if (name.endsWith(".svg")) return "image/svg+xml";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=UTF-8";
        }
    }
}
