package Client;

import com.google.gson.Gson;
import Model.Ticket;

import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TicketClient {

    private final String API_URL = "http://localhost:1914/tickets";
    private final Gson gson = new Gson();

    public String obtenerTickets() throws Exception {

        URL url = new URL("http://localhost:1914/tickets");

        HttpURLConnection conn =
                (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        return response.toString();
    }


    public Ticket enviarTicket(Ticket ticket) throws Exception {

        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");

        String json = gson.toJson(ticket);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();

        if (responseCode != 200 && responseCode != 201) {
            throw new Exception("Error al enviar ticket. Código HTTP: " + responseCode);
        }

        BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            response.append(line);
        }

        br.close();
        conn.disconnect();

        // ✅ convertir JSON de API1 a Ticket
        return gson.fromJson(response.toString(), Ticket.class);
    }


}
