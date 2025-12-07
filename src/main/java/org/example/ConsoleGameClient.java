package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConsoleGameClient {

    private static final Scanner scanner = new Scanner(System.in);
    private static String roomId;
    private static String playerName;

    public static void main(String[] args) throws Exception {
        System.out.println("== CLIENTE DE CONSOLA ==");
        System.out.print("Introduce tu nombre: ");
        playerName = scanner.nextLine();

        System.out.println("1) Crear sala");
        System.out.println("2) Unirse a sala");
        System.out.print("Opción: ");
        int option = Integer.parseInt(scanner.nextLine());

        if (option == 1) {
            roomId = createRoom(playerName);
        } else {
            System.out.print("Introduce ID de sala: ");
            roomId = scanner.nextLine();
            joinRoom(playerName, roomId);
        }

        System.out.println("Esperando a iniciar el juego...");
        System.out.println("Pulsa ENTER para iniciar (si hay suficientes jugadores)");
        scanner.nextLine();
        startGame(roomId);

        boolean gameRunning = true;
        while (gameRunning) {
            Map<String, Object> round = getCurrentRound(roomId);
            if (round == null) break;

            int roundNumber = (int) round.get("round");
            String word = (String) round.get("word");
            List<String> images = (List<String>) round.get("images");
            int time = (int) round.get("time");

            System.out.println("\nRonda " + roundNumber);
            System.out.println("Tienes " + time + " segundos");
            System.out.println("Imágenes: " + images);

            boolean correct = false;
            long start = System.currentTimeMillis();

            while (!correct && ((System.currentTimeMillis() - start) / 1000) < time) {
                System.out.print("Introduce respuesta: ");
                String answer = scanner.nextLine();

                Map<String, Object> result = submitAnswer(playerName, roomId, answer);
                correct = (boolean) result.get("correct");
                int points = (int) result.get("points");
                String correctWord = (String) result.get("correctWord");

                if (correct) {
                    System.out.println("¡Correcto! + " + points + " puntos");
                } else {
                    System.out.println("Incorrecto. La palabra correcta era: " + correctWord);
                }
            }

            // Pasar a la siguiente ronda
            Map<String, Object> next = nextRound(roomId);
            if (next.containsKey("GAME_OVER")) {
                System.out.println("\nJuego terminado. Resultados finales:");
                Map<String, Integer> scores = (Map<String, Integer>) next.get("scores");
                scores.forEach((player, pts) -> System.out.println(player + ": " + pts + " puntos"));
                gameRunning = false;
            }
        }

        leaveRoom(playerName, roomId);
        System.out.println("Has salido de la sala. ¡Gracias por jugar!");
    }

    // -----------------------------------------
    // Métodos HTTP para interactuar con el servidor
    // -----------------------------------------

    private static String createRoom(String name) throws Exception {
        String response = post("http://localhost:5555/create_room",
                "playerName=" + URLEncoder.encode(name, "UTF-8"));
        String id = parseXmlTag(response, "roomId");
        System.out.println("Sala creada: " + id);
        return id;
    }

    private static void joinRoom(String name, String roomId) throws Exception {
        post("http://localhost:5555/join_room",
                "playerName=" + URLEncoder.encode(name, "UTF-8") +
                        "&roomId=" + URLEncoder.encode(roomId, "UTF-8"));
        System.out.println("Te has unido a la sala " + roomId);
    }

    private static void startGame(String roomId) throws Exception {
        post("http://localhost:5555/start_game",
                "roomId=" + URLEncoder.encode(roomId, "UTF-8"));
        System.out.println("Juego iniciado");
    }

    private static Map<String, Object> getCurrentRound(String roomId) throws Exception {
        String response = post("http://localhost:5555/get_status",
                "roomId=" + URLEncoder.encode(roomId, "UTF-8"));

        // Simple parsing de ejemplo, en producción usar XML parser
        if (response.contains("<started>true</started>")) {
            response = post("http://localhost:5555/start_game",
                    "roomId=" + URLEncoder.encode(roomId, "UTF-8"));
            if (response.contains("<status>OK</status>")) {
                Map<String, Object> map = new HashMap<>();
                map.put("round", Integer.parseInt(parseXmlTag(response, "round")));
                map.put("word", parseXmlTag(response, "word"));
                map.put("time", Integer.parseInt(parseXmlTag(response, "time")));
                List<String> imgs = new ArrayList<>();
                String imgsXml = response.split("<images>")[1].split("</images>")[0];
                for (String img : imgsXml.split("</img>")) {
                    if (img.contains("<img>")) imgs.add(img.replace("<img>", "").trim());
                }
                map.put("images", imgs);
                return map;
            }
        }
        return null;
    }

    private static Map<String, Object> submitAnswer(String player, String roomId, String answer) throws Exception {
        String response = post("http://localhost:5555/submit_answer",
                "playerName=" + URLEncoder.encode(player, "UTF-8") +
                        "&roomId=" + URLEncoder.encode(roomId, "UTF-8") +
                        "&answer=" + URLEncoder.encode(answer, "UTF-8"));
        Map<String, Object> map = new HashMap<>();
        map.put("correct", Boolean.parseBoolean(parseXmlTag(response, "correct")));
        map.put("points", Integer.parseInt(parseXmlTag(response, "points")));
        map.put("correctWord", parseXmlTag(response, "correctWord"));
        return map;
    }

    private static Map<String, Object> nextRound(String roomId) throws Exception {
        String response = post("http://localhost:5555/next_round",
                "roomId=" + URLEncoder.encode(roomId, "UTF-8"));
        Map<String, Object> map = new HashMap<>();
        if (response.contains("GAME_OVER")) {
            Map<String, Integer> scores = new HashMap<>();
            String scoresXml = response.split("<scores>")[1].split("</scores>")[0];
            for (String s : scoresXml.split("</player>")) {
                if (!s.contains("<player")) continue;
                String name = s.split("name=\"")[1].split("\"")[0];
                int pts = Integer.parseInt(s.split(">")[1]);
                scores.put(name, pts);
            }
            map.put("GAME_OVER", true);
            map.put("scores", scores);
        }
        return map;
    }

    private static void leaveRoom(String player, String roomId) throws Exception {
        post("http://localhost:5555/leave_room",
                "playerName=" + URLEncoder.encode(player, "UTF-8") +
                        "&roomId=" + URLEncoder.encode(roomId, "UTF-8"));
    }

    private static String post(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        try (OutputStream os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream is = con.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String parseXmlTag(String xml, String tag) {
        if (!xml.contains("<" + tag + ">")) return "";
        return xml.split("<" + tag + ">")[1].split("</" + tag + ">")[0];
    }
}
