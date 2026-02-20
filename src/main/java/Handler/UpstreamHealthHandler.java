package Handler;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpstreamHealthHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(UpstreamHealthHandler.class);

    private static final String API1_HEALTH = "http://localhost:1914/health";
    private static final int TIMEOUT = 2000;

    @Override
    public void handle(HttpExchange exchange) {
        long start = System.currentTimeMillis();
        int status = 500;

        try {
            JsonObject api1 = checkApi(API1_HEALTH);
            boolean ok = api1.get("ok").getAsBoolean();

            JsonObject response = new JsonObject();
            response.addProperty("status", ok ? "OK" : "DEGRADED");
            response.add("api1", api1);

            byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            status = ok ? 200 : 503;

            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();

            log.info("Upstream health api1_ok={} api1_http={} status={} timeMs={}",
                    ok,
                    api1.has("http") ? api1.get("http").getAsInt() : -1,
                    status,
                    System.currentTimeMillis() - start
            );

        } catch (Exception e) {
            log.error("Upstream health failed - {}", e.toString(), e);
            try {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } catch (Exception ignored) {}
        }
    }

    private JsonObject checkApi(String urlStr) {
        JsonObject obj = new JsonObject();

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);

            int code = conn.getResponseCode();

            obj.addProperty("ok", code == 200);
            obj.addProperty("http", code);

            conn.disconnect();

        } catch (Exception e) {
            obj.addProperty("ok", false);
            obj.addProperty("error", e.getMessage());
            log.warn("API1 health unreachable url={} error={}", urlStr, e.toString());
        }

        return obj;
    }
}
