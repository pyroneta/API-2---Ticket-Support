package Handler;

import Service.RoundRobin;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ApiForwardHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) {
        try {
            System.out.println("\n================ PROXY REQUEST ================");
            System.out.println("Metodo: " + exchange.getRequestMethod());
            System.out.println("URI: " + exchange.getRequestURI());
            System.out.println("Remote: " + exchange.getRemoteAddress());

            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin == null || origin.isEmpty()) {
                origin = "http://localhost:5173";
            }

            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                System.out.println("Request OPTIONS detectado, respondiendo 204");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            String backend = RoundRobin.next();
            System.out.println("Backend seleccionado: " + backend);

            if (backend == null) {
                byte[] msg = "No hay backends registrados".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(503, msg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg);
                }
                return;
            }

            URI reqUri = exchange.getRequestURI();
            String path = reqUri.getPath();
            String query = reqUri.getQuery();
            String fullPath = (query == null || query.isEmpty()) ? path : path + "?" + query;


            String targetUrl = backend.endsWith("/")
                    ? backend.substring(0, backend.length() - 1) + fullPath
                    : backend + fullPath;
            System.out.println("Forward URL: " + targetUrl);

            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(8000);

            Headers reqHeaders = exchange.getRequestHeaders();
            System.out.println("Headers entrantes:");
            for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }

            for (String key : reqHeaders.keySet()) {
                if (key == null) continue;
                if (key.equalsIgnoreCase("Host")) continue;
                if (key.equalsIgnoreCase("Content-Length")) continue;

                for (String v : reqHeaders.get(key)) {
                    conn.addRequestProperty(key, v);
                }
            }

            String method = exchange.getRequestMethod();
            boolean mayHaveBody = method.equalsIgnoreCase("POST")
                    || method.equalsIgnoreCase("PUT")
                    || method.equalsIgnoreCase("PATCH");

            if (mayHaveBody) {
                byte[] requestBody = readBytes(exchange.getRequestBody());

                System.out.println("Body recibido en proxy:");
                System.out.println(new String(requestBody, StandardCharsets.UTF_8));

                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(requestBody);
                    out.flush();
                }

                System.out.println("Body reenviado al backend correctamente");
            }

            int status = conn.getResponseCode();
            System.out.println("Status backend: " + status);

            System.out.println("Headers respuesta backend:");
            conn.getHeaderFields().forEach((k, vals) -> {
                System.out.println("  " + k + ": " + vals);
            });

            conn.getHeaderFields().forEach((k, vals) -> {
                if (k == null) return;
                if (k.equalsIgnoreCase("Transfer-Encoding")) return;
                if (k.toLowerCase().startsWith("access-control")) return;
                for (String v : vals) {
                    exchange.getResponseHeaders().add(k, v);
                }
            });

            InputStream backendStream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            byte[] respBytes = (backendStream == null) ? new byte[0] : readBytes(backendStream);

            System.out.println("Body respuesta backend:");
            System.out.println(new String(respBytes, StandardCharsets.UTF_8));

            exchange.sendResponseHeaders(status, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }

            System.out.println("Respuesta enviada al frontend");
            System.out.println("============== FIN PROXY REQUEST ==============\n");

        } catch (Exception e) {
            System.out.println("ERROR EN PROXY:");
            e.printStackTrace();

            try {
                byte[] msg = ("Proxy error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(502, msg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg);
                }
            } catch (Exception ignored) {
            }
        } finally {
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    private byte[] readBytes(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = in.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }
}