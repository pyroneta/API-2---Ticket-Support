package Handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import Model.Ticket;
import Service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TicketHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(TicketHandler.class);

    private final TicketService ticketService = new TicketService();
    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.currentTimeMillis();

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI() != null ? exchange.getRequestURI().getPath() : "";

        int status = 500;

        try {
            log.info("Incoming request {} {}", method, path);

            if ("GET".equalsIgnoreCase(method)) {
                String listaJson = ticketService.listarTickets(); // ya JSON desde proxy
                responder(exchange, 200, listaJson);
                status = 200;
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = leerBody(exchange);

                Ticket ticket = gson.fromJson(body, Ticket.class);
                Ticket creado = ticketService.crearTicket(ticket);

                // ✅ Postman solo ve 201 si se guardó
                responder(exchange, 201, gson.toJson(creado));
                status = 201;
                return;
            }
            responder(exchange, 405, "{\"error\":\"Método no permitido\"}");
            status = 405;

        } catch (Exception e) {

            String msg = e.getMessage() != null ? e.getMessage() : "";

            if (msg.contains("BAD_GATEWAY_CONNECT_TIMEOUT")) {
                status = 502;
                responder(exchange, status, "{\"error\":\"502 - Bad Gateway\"}");
                log.error("Request failed {} {} - {}", method, path, e.toString(), e);
                return;
            }

            if (msg.contains("GATEWAY_TIMEOUT_READ_TIMEOUT")) {
                status = 504;
                responder(exchange, status, "{\"error\":\"504 - Gateway Timeout\"}");
                log.error("Request failed {} {} - {}", method, path, e.toString(), e);
                return;
            }

            status = 500;
            responder(exchange, status, "{\"error\":\"Error interno\"}");
            log.error("Request failed {} {} - {}", method, path, e.toString(), e);

        } finally {
            long ms = System.currentTimeMillis() - start;
            log.info("Completed {} {} status={} timeMs={}", method, path, status, ms);
        }
    }

    private String leerBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
        )) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private void responder(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }
}