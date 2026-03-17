package Proxy;

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

public class ApiForwardHandler implements HttpHandler {

    private final String backendBaseUrl; // ej: http://127.0.0.1:1914

    public ApiForwardHandler(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl.endsWith("/")
                ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                : backendBaseUrl;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            URI reqUri = exchange.getRequestURI();
            String path = reqUri.getPath();
            String query = reqUri.getQuery();

            String fullPath = (query == null || query.isEmpty()) ? path : path + "?" + query;

            URL url = new URL(backendBaseUrl + fullPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(exchange.getRequestMethod());
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(8000);

            Headers reqHeaders = exchange.getRequestHeaders();
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
                conn.setDoOutput(true);
                try (InputStream in = exchange.getRequestBody();
                     OutputStream out = conn.getOutputStream()) {
                    copy(in, out);
                    out.flush();
                }
            }

            int status = conn.getResponseCode();

            conn.getHeaderFields().forEach((k, vals) -> {
                if (k == null) return;
                if (k.equalsIgnoreCase("Transfer-Encoding")) return;
                for (String v : vals) {
                    exchange.getResponseHeaders().add(k, v);
                }
            });

            InputStream backendStream = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
            byte[] respBytes = (backendStream == null) ? new byte[0] : readBytes(backendStream);

            exchange.sendResponseHeaders(status, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }

        } catch (Exception e) {
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

    private void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
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