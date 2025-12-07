package org.example;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {

    public static final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    public static final XMLDatabase database = new XMLDatabase();

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(5555), 0);

            server.createContext("/create_room", GameServer::createRoom);
            server.createContext("/join_room", GameServer::joinRoom);
            server.createContext("/start_game", GameServer::startGame);
            server.createContext("/submit_answer", GameServer::submitAnswer);
            server.createContext("/next_round", GameServer::nextRound);
            server.createContext("/get_status", GameServer::getStatus);
            server.createContext("/leave_room", GameServer::leaveRoom);

            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("Servidor iniciado en http://localhost:8080/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------
    // UTILIDADES
    // -----------------------------------------------------------------

    private static Map<String, String> parseParams(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null) return params;

        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(
                        URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange ex, int code, String xml) {
        try {
            byte[] out = xml.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
            ex.sendResponseHeaders(code, out.length);
            ex.getResponseBody().write(out);
        } catch (Exception ignored) {
        } finally {
            ex.close();
        }
    }

    // -----------------------------------------------------------------
    // HANDLERS DEL SERVIDOR
    // -----------------------------------------------------------------

    private static void createRoom(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = "ROOM_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String playerName = params.getOrDefault("playerName", "Jugador");
        int max = Integer.parseInt(params.getOrDefault("maxPlayers", "6"));

        GameRoom room = new GameRoom(roomId, max);
        room.addPlayer(playerName);

        rooms.put(roomId, room);

        respond(ex, 200,
                "<response><status>OK</status><roomId>" + roomId + "</roomId></response>");
    }

    private static void joinRoom(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = params.get("roomId");
        String name = params.getOrDefault("playerName", "Jugador");

        GameRoom room = rooms.get(roomId);
        if (room == null) {
            respond(ex, 404, "<response><status>ERROR</status><msg>Sala no existe</msg></response>");
            return;
        }

        if (room.isFull()) {
            respond(ex, 400, "<response><status>ERROR</status><msg>Sala llena</msg></response>");
            return;
        }

        if (room.hasPlayer(name)) {
            respond(ex, 400, "<response><status>ERROR</status><msg>Nombre en uso</msg></response>");
            return;
        }

        room.addPlayer(name);

        respond(ex, 200, "<response><status>OK</status></response>");
    }

    private static void startGame(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = params.get("roomId");
        GameRoom room = rooms.get(roomId);

        if (room == null) {
            respond(ex, 404, "<response><status>ERROR</status><msg>Sala no existe</msg></response>");
            return;
        }
        if (room.getPlayers().size() < 2) {
            respond(ex, 400, "<response><status>ERROR</status><msg>Min 2 jugadores</msg></response>");
            return;
        }

        room.startGame(database.loadRounds());

        respond(ex, 200, room.getRoundXML());
    }

    private static void submitAnswer(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = params.get("roomId");
        String player = params.get("playerName");
        String answer = params.getOrDefault("answer", "");

        GameRoom room = rooms.get(roomId);

        SubmitResult result = room.submitAnswer(player, answer, database);

        respond(ex, 200, result.toXML());
    }

    private static void nextRound(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = params.get("roomId");
        GameRoom room = rooms.get(roomId);

        if (room.nextRound()) {
            respond(ex, 200, room.getRoundXML());
        } else {
            respond(ex, 200, room.getFinalResultsXML());
        }
    }

    private static void getStatus(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));
        String roomId = params.get("roomId");

        GameRoom room = rooms.get(roomId);

        respond(ex, 200, room.getStatusXML());
    }

    private static void leaveRoom(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(readBody(ex));

        String roomId = params.get("roomId");
        String name = params.get("playerName");

        GameRoom room = rooms.get(roomId);
        if (room != null) room.removePlayer(name);

        respond(ex, 200, "<response><status>OK</status></response>");
    }
}
