package Handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegistrarHandler implements HttpHandler {
    public static final List<String> BACKENDS = new CopyOnWriteArrayList<>();
    private final Gson gson = new Gson();

    static class Registro {
        String ip;
        int puerto;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String body;
        try (Scanner s = new Scanner(exchange.getRequestBody(), "UTF-8").useDelimiter("\\A")) {
            body = s.hasNext() ? s.next() : "";
        }

        Registro r = gson.fromJson(body, Registro.class);
        if (r != null && r.ip != null && !r.ip.trim().isEmpty()) {
            String key = r.ip + ":" + r.puerto;
            if (!BACKENDS.contains(key)) {
                BACKENDS.add(key);
                System.out.println("Registrado: " + key);

                System.out.println(BACKENDS);
            }
            responder(exchange, 201, "{\"ok\":true}");
        } else {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        }
    }

    private void responder(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}