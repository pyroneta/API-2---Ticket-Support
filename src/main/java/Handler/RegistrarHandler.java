package Handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RegistrarHandler implements HttpHandler {

    public static final List<String> BACKENDS = new CopyOnWriteArrayList<>();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("Entró a /register");
        System.out.println("Método: " + exchange.getRequestMethod());

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        BufferedReader br = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
        );

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        String body = sb.toString();
        System.out.println("Body recibido: " + body);

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (!json.has("url")) {
                responder(exchange, 400, "Falta el campo url");
                return;
            }

            String backendUrl = json.get("url").getAsString().trim();

            if (backendUrl.isEmpty()) {
                responder(exchange, 400, "La url está vacía");
                return;
            }

            if (!BACKENDS.contains(backendUrl)) {
                BACKENDS.add(backendUrl);
            }

            System.out.println("Backend agregado: " + backendUrl);
            System.out.println("Backends registrados: " + BACKENDS);

            responder(exchange, 200, "Registrado correctamente");

        } catch (Exception e) {
            e.printStackTrace();
            responder(exchange, 400, "JSON inválido");
        }
    }

    private void responder(HttpExchange exchange, int status, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}