package Service;

import Handler.RegistrarHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HealthMonitor {

    public static final List<String> HEALTHY_BACKENDS = new CopyOnWriteArrayList<>();

    private static final Map<String, Integer> FAILED_ATTEMPTS = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = getMaxAttempts();

    public void start() {
        new Thread(() -> {
            while (true) {
                try {
                    checkAllBackends();
                    Thread.sleep(10000);
                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void checkAllBackends() {
        List<String> registrados = RegistrarHandler.BACKENDS;

        System.out.println("Backends registrados: " + registrados);

        for (String backend : registrados) {
            System.out.println("Probando backend: " + backend);

            boolean ok = checkHealth(backend);

            if (ok) {
                if (!HEALTHY_BACKENDS.contains(backend)) {
                    HEALTHY_BACKENDS.add(backend);
                }

                FAILED_ATTEMPTS.put(backend, 0);

            } else {
                // Suma un fallo más
                int intentosFallidos = FAILED_ATTEMPTS.getOrDefault(backend, 0) + 1;
                FAILED_ATTEMPTS.put(backend, intentosFallidos);

                System.out.println("Backend no saludable: " + backend +
                        " intento fallido " + intentosFallidos);

                if (HEALTHY_BACKENDS.contains(backend)) {
                    HEALTHY_BACKENDS.remove(backend);
                }

                if (intentosFallidos >= MAX_ATTEMPTS) {
                    RegistrarHandler.BACKENDS.remove(backend);
                    HEALTHY_BACKENDS.remove(backend);
                    FAILED_ATTEMPTS.remove(backend);

                    System.out.println("Backend eliminado de BACKENDS por " + MAX_ATTEMPTS + " " + backend);
                }
            }
        }

        System.out.println("Backends saludables actuales: " + HEALTHY_BACKENDS);

    }

    private boolean checkHealth(String backend) {
        HttpURLConnection conn = null;

        try {

            String urlStr = backend + "health";
            System.out.println("Llamando health a: " + urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            int status = conn.getResponseCode();
            System.out.println("HTTP status health de " + backend + ": " + status);

            if (status != 200) {
                return false;
            }

            InputStream is = conn.getInputStream();
            String body = readBody(is);

            System.out.println("Body health de " + backend + ": " + body);

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (!json.has("status")) {
                System.out.println("El JSON no tiene campo 'status'");
                return false;
            }

            String statusValue = json.get("status").getAsString();
            System.out.println("Valor de status en health: " + statusValue);

            return "UP".equalsIgnoreCase(statusValue);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readBody(InputStream is) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            sb.append(line);
        }

        return sb.toString();
    }

    private static int getMaxAttempts() {
        String envValue = System.getenv("MAX_HEALTH_CHECK_ATTEMPTS");

        if (envValue == null) {
            return 4;
        }

        try {
            int value = Integer.parseInt(envValue);
            if (value <= 0) {
                return 4;
            }
            return value;
        } catch (NumberFormatException e) {
            return 4;
        }
    }
}