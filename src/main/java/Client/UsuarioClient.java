package Client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UsuarioClient {

    private static final String API_URL = "http://localhost:1914/usuarios";

    private final Gson gson = new Gson();

    public boolean existeUsuario(int idUsuario) {

        try {

            URL url = new URL(API_URL + "?id=" + idUsuario);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            int status = conn.getResponseCode();

            if (status != 200) {
                return false;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            reader.close();

            JsonObject json = gson.fromJson(sb.toString(), JsonObject.class);

            return json.has("id");

        } catch (Exception e) {

            System.out.println("Error conectando API Usuarios");
            e.printStackTrace();

            return false;
        }
    }
}
