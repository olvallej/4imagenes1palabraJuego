package org.example;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class GameRoom {

    private final String id;
    private final int maxPlayers;
    private final List<String> players = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Set<String> answered = ConcurrentHashMap.newKeySet();

    private List<Round> rounds = new ArrayList<>();
    private int index = -1;
    private boolean started = false;

    private final Lock lock = new ReentrantLock();

    // Scheduler para terminar rondas automáticamente
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean roundFinished = true;
    private String host; // jugador que creó la sala

    public void setHost(String player) { this.host = player; }
    public boolean isHost(String player) { return host.equals(player); }


    public GameRoom(String id, int maxPlayers) {
        this.id = id;
        this.maxPlayers = maxPlayers;
    }

    public boolean isFull() { return players.size() >= maxPlayers; }
    public boolean hasPlayer(String p) { return players.contains(p); }

    public void addPlayer(String p) {
        lock.lock();
        try {
            players.add(p);
            scores.put(p, 0);
        } finally { lock.unlock(); }
    }

    public void removePlayer(String p) {
        players.remove(p);
        scores.remove(p);
        answered.remove(p);
    }

    public List<String> getPlayers() {
        return new ArrayList<>(players);
    }

    // ---------------------------------------------------------
    // INICIO DEL JUEGO
    // ---------------------------------------------------------

    public void startGame(List<Round> loaded) {
        this.rounds = loaded;
        this.index = 0;
        this.started = true;
        startCurrentRound();
    }

    private void startCurrentRound() {
        Round r = rounds.get(index);
        r.start();

        // Resetea el set de jugadores que ya acertaron
        answered.clear();
        roundFinished = false;

        System.out.println("Ronda " + (index + 1) + " iniciada: palabra = " + r.word);

        // Programa el fin automático de la ronda por tiempo
        scheduler.schedule(() -> {
            roundFinished = true;
            System.out.println("Ronda " + (index + 1) + " finalizada por tiempo.");
        }, r.timeLimit, TimeUnit.SECONDS);
    }


    public boolean nextRound() {
        if (!roundFinished) return false; // No se puede avanzar hasta que termine la ronda
        index++;
        if (index >= rounds.size()) return false;
        startCurrentRound();
        return true;
    }

    public Round getCurrentRound() {
        if (index < 0 || index >= rounds.size()) return null;
        return rounds.get(index);
    }

    // ---------------------------------------------------------
    // RESPUESTAS DE JUGADORES
    // ---------------------------------------------------------

    public SubmitResult submitAnswer(String player, String answer, XMLDatabase db) {
        Round r = getCurrentRound();
        if (r == null) return new SubmitResult(false, 0, "");

        long elapsed = (System.currentTimeMillis() - r.startTime) / 1000;
        boolean correct = answer.equalsIgnoreCase(r.word);

        int points = 0;
        if (correct && !answered.contains(player)) {
            // Solo gana puntos la primera vez que acierta
            points = Math.max(100, 1000 - (int) elapsed * 30);
            scores.put(player, scores.get(player) + points);
            db.saveScore(player, points, id);
        }

        // Marcar que el jugador ya acertó
        if (correct) answered.add(player);

        // Verificar si todos los jugadores han acertado
        if (answered.containsAll(players)) {
            roundFinished = true;
            System.out.println("Ronda " + (index + 1) + " finalizada: todos los jugadores respondieron correctamente.");
        }

        return new SubmitResult(correct, points, r.word);
    }


    // ---------------------------------------------------------
    // EXPORTACIONES XML
    // ---------------------------------------------------------

    public String getRoundXML() {
        Round r = getCurrentRound();
        if (r == null) return "<response><status>NO_ROUND</status></response>";

        StringBuilder sb = new StringBuilder();
        sb.append("<response><status>OK</status>");
        sb.append("<round>").append(index + 1).append("</round>");
        sb.append("<word>").append(r.word).append("</word>");
        sb.append("<images>");

        for (String img : r.images)
            sb.append("<img>").append(img).append("</img>");

        sb.append("</images>");
        sb.append("<timeLimit>").append(r.timeLimit).append("</timeLimit>");
        sb.append("</response>");

        return sb.toString();
    }

    public String getStatusXML() {
        Round r = getCurrentRound();
        StringBuilder sb = new StringBuilder();
        sb.append("<response><status>OK</status>");
        sb.append("<started>").append(started).append("</started>");
        if (r != null && !roundFinished) {
            sb.append("<round>").append(index + 1).append("</round>");
            sb.append("<word>").append(r.word).append("</word>");
            sb.append("<images>");
            for (String img : r.images)
                sb.append("<img>").append(img).append("</img>");
            sb.append("</images>");
            sb.append("<timeLimit>").append(r.timeLimit).append("</timeLimit>");
        }
        sb.append("<scores>");
        players.forEach(p ->
                sb.append("<player name=\"").append(p).append("\">")
                        .append(scores.get(p)).append("</player>")
        );
        sb.append("</scores>");
        sb.append("</response>");
        return sb.toString();
    }


    public String getFinalResultsXML() {
        StringBuilder sb = new StringBuilder("<response><status>GAME_OVER</status><scores>");

        players.forEach(p ->
                sb.append("<player name=\"").append(p).append("\">")
                        .append(scores.get(p)).append("</player>")
        );

        sb.append("</scores></response>");
        return sb.toString();
    }
}
