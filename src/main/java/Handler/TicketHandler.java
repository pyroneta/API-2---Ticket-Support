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

    private final TicketService ticketService;
    private final Gson gson;

    public TicketHandler() {
        this.ticketService = new TicketService();
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long start = System.currentTimeMillis();

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI() != null ? exchange.getRequestURI().getPath() : "";

        int status = 500;

        try {
            log.info("Incoming request {} {}", method, path);

            switch (method) {
                case "GET":
                    manejarGet(exchange);
                    status = 200;
                    break;

                case "POST":
                    manejarPost(exchange);
                    status = 201;
                    break;

                default:
                    status = 405;
                    responder(exchange, status, "{\"error\":\"Método no permitido\"}");
            }

        } catch (Exception e) {
            log.error("Request failed {} {} - {}", method, path, e.toString(), e);
            status = 500;
            responder(exchange, status, "{\"error\":\"Error interno\"}");
        } finally {
            long ms = System.currentTimeMillis() - start;
            log.info("Completed {} {} status={} timeMs={}", method, path, status, ms);
        }
    }

    private void manejarGet(HttpExchange exchange) throws Exception {
        String lista = ticketService.listarTickets(); // ya es JSON
        responder(exchange, 200, lista);
    }

    private void manejarPost(HttpExchange exchange) throws Exception {
        String body = leerBody(exchange);

        // OJO: no loguees el body completo si puede tener datos sensibles
        log.debug("POST /tickets bodySize={}", body != null ? body.length() : 0);

        Ticket ticket = gson.fromJson(body, Ticket.class);
        Ticket creado = ticketService.crearTicket(ticket);

        responder(exchange, 201, gson.toJson(creado));
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
