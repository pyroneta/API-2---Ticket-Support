package Server;

import Handler.ApiForwardHandler;
import Handler.RegistrarHandler;
import Service.HealthMonitor;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TicketServer {

    private HttpServer server;

    public boolean start() {
        try {
            Properties props = new Properties();

            try (InputStream is = TicketServer.class.getClassLoader().getResourceAsStream("proxy.properties")) {
                if (is == null) {
                    throw new RuntimeException("No se encontró proxy.properties en src/main/resources");
                }
                props.load(is);
            }

            String host = props.getProperty("proxy.host", "127.0.0.1").trim();
            int port = Integer.parseInt(props.getProperty("proxy.port", "1916").trim());

            String routesStr = props.getProperty("backend.routes", "/tickets").trim();

            List<String> routes = Arrays.stream(routesStr.split(","))
                    .map(String::trim)
                    .filter(new java.util.function.Predicate<String>() {
                        @Override
                        public boolean test(String s) {
                            return !s.isEmpty();
                        }
                    })
                    .collect(Collectors.toList());

            server = HttpServer.create(new InetSocketAddress(host, port), 0);

            server.createContext("/register", new RegistrarHandler());

            ApiForwardHandler forward = new ApiForwardHandler();

            for (String route : routes) {
                server.createContext(route, forward);
            }

            server.createContext("/", exchange -> {
                byte[] msg = ("API2 Proxy OK").getBytes("UTF-8");
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, msg.length);
                exchange.getResponseBody().write(msg);
                exchange.close();
            });

            HealthMonitor monitor = new HealthMonitor();
            monitor.start();

            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();

            System.out.println("API2 Proxy corriendo en http://" + host + ":" + port);
            System.out.println("Ruta de registro: /register");
            System.out.println("Rutas forward: " + routes);

            return true;

        } catch (Exception e) {
            System.err.println("Error al iniciar API2 Proxy");
            e.printStackTrace();
            return false;
        }
    }
}