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

import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

public class TicketProxy {

    private static final int CONNECT_TIMEOUT = 500;
    private static final int READ_TIMEOUT = 4000; // SOLO UNO

    // pausa entre intento 1 fallido y reintento
    private static final int RETRY_SLEEP_MS = 250;

    private static final Logger log = LoggerFactory.getLogger(TicketProxy.class);

    private final Gson gson = new Gson();

    private static final String ERR_BAD_GATEWAY_CONNECT = "BAD_GATEWAY_CONNECT_TIMEOUT";    // 502
    private static final String ERR_GATEWAY_TIMEOUT_READ = "GATEWAY_TIMEOUT_READ_TIMEOUT"; // 504
    private static final String ERR_NO_BACKENDS = "NO_BACKENDS_REGISTERED";                // 503 (en handler)

    // =========================
    // GET /tickets  (ROUND ROBIN)
    // =========================
    public String obtenerTickets() throws Exception {

        String rid = newRid();
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;

        String urlStr = pickTicketsUrlOrThrow(); // <-- round robin

        try {
            log.info("[{}] LB -> GET {} connectTimeout={}ms readTimeout={}ms",
                    rid, urlStr, CONNECT_TIMEOUT, READ_TIMEOUT);

            URL url = new URL(urlStr);
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

        } catch (SocketTimeoutException e) {
            long failTime = System.currentTimeMillis() - start;

            if (isConnectTimeout(e)) {
                log.error("[{}] LB -> GET CONNECT TIMEOUT after {}ms err={}", rid, failTime, e.toString());
                throw new Exception(ERR_BAD_GATEWAY_CONNECT, e); // 502
            } else {
                log.error("[{}] LB -> GET READ TIMEOUT after {}ms err={}", rid, failTime, e.toString());
                throw new Exception(ERR_GATEWAY_TIMEOUT_READ, e); // 504
            }

        } catch (ConnectException | UnknownHostException | NoRouteToHostException e) {
            long failTime = System.currentTimeMillis() - start;
            log.error("[{}] LB -> GET UPSTREAM CONNECT FAILED after {}ms err={}", rid, failTime, e.toString());
            throw new Exception(ERR_BAD_GATEWAY_CONNECT, e); // 502

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // =========================
    // POST /tickets  (ROUND ROBIN + RETRY TRANSPARENTE)
    // - intento 1: backend A (RR)
    // - intento 2: backend B (RR siguiente)
    // =========================
    public Ticket enviarTicket(Ticket ticket) throws Exception {

        String rid = newRid();
        long start = System.currentTimeMillis();

        // MISMO BODY para ambos intentos
        final String json = gson.toJson(ticket);

        final int maxAttempts = 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            HttpURLConnection conn = null;
            String urlStr = null;

            try {
                urlStr = pickTicketsUrlOrThrow();

                log.info("[{}] LB -> POST attempt {}/{} {} connectTimeout={}ms readTimeout={}ms bodySize={}",
                        rid, attempt, maxAttempts, urlStr, CONNECT_TIMEOUT, READ_TIMEOUT, json.length());

                if (attempt == 1) {
                    log.warn("[{}] Simulating READ TIMEOUT on attempt 1", rid);
                    throw new SocketTimeoutException("Simulated read timeout");
                }

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("X-Request-Id", rid);
                conn.setRequestProperty("X-Retry-Attempt", String.valueOf(attempt));

                conn.setDoOutput(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                String body = readResponseBody(conn, code);

                if (code != 200 && code != 201) {
                    log.error("[{}] LB -> POST failed http={} body={}", rid, code, safeShort(body, 500));
                    throw new Exception("API1 POST error HTTP " + code + " body=" + safeShort(body, 500));
                }

                log.info("[{}] LB -> POST success on attempt {} http={} timeMs={} respSize={}",
                        rid, attempt, code, (System.currentTimeMillis() - start), body.length());

                return gson.fromJson(body, Ticket.class);

            } catch (SocketTimeoutException e) {

                // connect timeout => 502 (sin retry)
                if (isConnectTimeout(e)) {
                    log.error("[{}] LB -> POST CONNECT TIMEOUT (no retry) err={}", rid, e.toString());
                    throw new Exception(ERR_BAD_GATEWAY_CONNECT, e);
                }

                // read timeout => retry interno
                if (attempt < maxAttempts) {
                    log.warn("[{}] LB -> POST READ TIMEOUT attempt {}/{}. Retrying internally after {}ms...",
                            rid, attempt, maxAttempts, RETRY_SLEEP_MS);
                    try { Thread.sleep(RETRY_SLEEP_MS); } catch (InterruptedException ignored) {}
                    continue;
                }

                log.error("[{}] LB -> POST READ TIMEOUT after {} attempts err={}", rid, attempt, e.toString());
                throw new Exception(ERR_GATEWAY_TIMEOUT_READ, e);

            } catch (ConnectException | UnknownHostException | NoRouteToHostException e) {
                log.error("[{}] LB -> POST UPSTREAM CONNECT FAILED err={}", rid, e.toString());
                throw new Exception(ERR_BAD_GATEWAY_CONNECT, e); // 502

            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        throw new Exception("Unexpected proxy error");
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

    private boolean isConnectTimeout(SocketTimeoutException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        msg = msg.toLowerCase();
        return msg.contains("connect timed out") || msg.contains("connect timeout");
    }

    private String pickTicketsUrlOrThrow() throws Exception {

        String backend = Service.RoundRobin.next();

        if (backend == null) {
            throw new Exception(ERR_NO_BACKENDS);
        }

        return "http://" + backend + "/tickets";
    }
}