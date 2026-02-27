package Server;

import Handler.RegistrarHandler;
import Handler.RootHandler;
import Handler.UpstreamHealthHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import Handler.TicketHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class TicketServer {

    private HttpServer server;

    public boolean start() {

        try {

            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 1916), 0);

            // Root opcional
            server.createContext("/", new RootHandler());

            // Registrar backends
            server.createContext("/registrar", new RegistrarHandler());

            // Tickets
            server.createContext("/tickets", exchange -> {

                Headers headers = exchange.getResponseHeaders();

                headers.add("Access-Control-Allow-Origin", "*");
                headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");

                if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                    exchange.sendResponseHeaders(200, -1);
                    return;
                }

                new TicketHandler().handle(exchange);
            });

            server.createContext("/health", new UpstreamHealthHandler());

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();

            System.out.println("✅ API2 corriendo en puerto 1916");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error al iniciar API2");
            e.printStackTrace();
            return false;
        }
    }


    public void stop() {

        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("🛑 Ticket API detenida");
        }
    }
}
