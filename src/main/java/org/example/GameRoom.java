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
        roundFinished = false;
        answered.clear();

        // Programa el fin de la ronda automáticamente
        scheduler.schedule(() -> {
            roundFinished = true;
            System.out.println("Ronda " + (index + 1) + " finalizada automáticamente.");
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
        if (roundFinished) {
            // Ronda terminada, no aceptar más respuestas
            Round r = getCurrentRound();
            return new SubmitResult(false, 0, r != null ? r.word : "");
        }

        Round r = getCurrentRound();
        if (r == null) return new SubmitResult(false, 0, "");

        long elapsed = (System.currentTimeMillis() - r.startTime) / 1000;

        boolean correct = answer.equalsIgnoreCase(r.word);

        int points = correct ? Math.max(100, 1000 - (int) elapsed * 30) : 0;

        if (correct) {
            scores.put(player, scores.get(player) + points);
            db.saveScore(player, points, id);
            roundFinished = true; // termina la ronda si alguien acierta
        }

        answered.add(player);

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
        sb.append("<time>").append(r.timeLimit).append("</time>");
        sb.append("</response>");

        return sb.toString();
    }

    public String getStatusXML() {
        StringBuilder sb = new StringBuilder();
        sb.append("<response><status>OK</status>");
        sb.append("<started>").append(started).append("</started>");
        sb.append("<scores>");

        players.forEach(p ->
                sb.append("<player name=\"").append(p).append("\">")
                        .append(scores.get(p)).append("</player>")
        );

        sb.append("</scores></response>");

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
