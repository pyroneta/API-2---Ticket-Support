package Server;

import Proxy.ApiForwardHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

public class TicketServer {

    private HttpServer server;

    public boolean start() {
        try {
            Properties props = new Properties();

            try (InputStream is = TicketServer.class.getClassLoader().getResourceAsStream("proxy.properties")) {
                if (is == null) throw new RuntimeException("No se encontró proxy.properties en src/main/resources");
                props.load(is);
            }

            String host = props.getProperty("proxy.host", "127.0.0.1").trim();
            int port = Integer.parseInt(props.getProperty("proxy.port", "1916").trim());

            String backendBaseUrl = props.getProperty("backend.baseUrl").trim();
            String routesStr = props.getProperty("backend.routes").trim();

            List<String> routes = Arrays.stream(routesStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList(); // ✅ Java 21 ok

            server = HttpServer.create(new InetSocketAddress(host, port), 0);

            // ✅ Un solo handler para todas las rutas
            ApiForwardHandler forward = new ApiForwardHandler(backendBaseUrl);

            // ✅ FOR que crea contexts según properties (lo que tu profe quiere ver)
            for (String route : routes) {
                server.createContext(route, forward);
            }

            // Root opcional
            server.createContext("/", exchange -> {
                byte[] msg = ("API2 Proxy OK -> " + backendBaseUrl).getBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, msg.length);
                exchange.getResponseBody().write(msg);
                exchange.close();
            });

            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();

            System.out.println("✅ API2 Proxy corriendo en http://" + host + ":" + port);
            System.out.println("➡️ Forward a: " + backendBaseUrl);
            System.out.println("➡️ Rutas: " + routes);

            return true;

        } catch (Exception e) {
            System.err.println("❌ Error al iniciar API2 Proxy");
            e.printStackTrace();
            return false;
        }
    }
}