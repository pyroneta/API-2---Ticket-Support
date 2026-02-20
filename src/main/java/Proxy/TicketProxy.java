package Proxy;

import com.google.gson.Gson;
import Model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TicketProxy {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 200;

    private static final Logger log = LoggerFactory.getLogger(TicketProxy.class);

    private static final String API1_TICKETS_URL = "http://localhost:1914/tickets";
    private final Gson gson = new Gson();

    // =========================
    // GET /tickets
    // =========================
    public String obtenerTickets() throws Exception {

        String rid = newRid();
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;

        try {
            log.info("[{}] LB -> GET {} connectTimeout={}ms readTimeout={}ms",
                    rid, API1_TICKETS_URL, CONNECT_TIMEOUT, READ_TIMEOUT);

            URL url = new URL(API1_TICKETS_URL);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Request-Id", rid);

            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            int code = conn.getResponseCode();

            String body = readResponseBody(conn, code);

            long totalTime = System.currentTimeMillis() - start;
            log.info("[{}] LB -> GET done http={} timeMs={} respSize={}",
                    rid, code, totalTime, body.length());

            if (code < 200 || code >= 300) {
                log.error("[{}] LB -> GET failed http={} body={}", rid, code, safeShort(body, 400));
                throw new Exception("API1 GET error HTTP " + code + " body=" + safeShort(body, 400));
            }

            return body;

        } catch (Exception e) {
            long failTime = System.currentTimeMillis() - start;
            log.error("[{}] LB -> GET exception after {}ms err={}", rid, failTime, e.toString(), e);
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================
    // POST /tickets
    // =========================
    public Ticket enviarTicket(Ticket ticket) throws Exception {

        String rid = newRid();
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        int code = -1;

        try {
            String json = gson.toJson(ticket);

            log.info("[{}] LB -> POST {} connectTimeout={}ms readTimeout={}ms bodySize={} bodyPreview={}",
                    rid, API1_TICKETS_URL, CONNECT_TIMEOUT, READ_TIMEOUT, json.length(), safeShort(json, 250));

            URL url = new URL(API1_TICKETS_URL);
            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Request-Id", rid);
            conn.setDoOutput(true);

            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            long writeStart = System.currentTimeMillis();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            long writeTime = System.currentTimeMillis() - writeStart;

            // Aquí puede explotar por readTimeout (esperando headers/respuesta)
            code = conn.getResponseCode();

            String body = readResponseBody(conn, code);
            long totalTime = System.currentTimeMillis() - start;

            if (code != 200 && code != 201) {
                log.error("[{}] LB -> POST failed http={} timeMs={} writeMs={} body={}",
                        rid, code, totalTime, writeTime, safeShort(body, 500));
                throw new Exception("API1 POST error HTTP " + code + " body=" + safeShort(body, 500));
            }

            log.info("[{}] LB -> POST success http={} timeMs={} writeMs={} respSize={}",
                    rid, code, totalTime, writeTime, body.length());

            return gson.fromJson(body, Ticket.class);

        } catch (Exception e) {
            long failTime = System.currentTimeMillis() - start;
            log.error("[{}] LB -> POST exception http={} after {}ms err={}",
                    rid, code, failTime, e.toString(), e);
            throw e;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================
    // Helpers
    // =========================
    private String readResponseBody(HttpURLConnection conn, int code) {
        try {
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "";

            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            // Si falla leyendo body, no tiramos NPE ni escondemos el error original
            return "";
        }
    }

    private String newRid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String safeShort(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
