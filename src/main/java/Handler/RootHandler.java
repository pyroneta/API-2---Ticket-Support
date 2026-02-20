package Handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RootHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(RootHandler.class);

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String response =
                "{\n" +
                        "  \"service\":\"Tickets API\",\n" +
                        "  \"status\":\"RUNNING\",\n" +
                        "  \"version\":\"1.0\"\n" +
                        "}";

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
        ex.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        ex.close();

        log.info("Root endpoint hit {}", ex.getRequestURI() != null ? ex.getRequestURI().getPath() : "/");
    }
}
