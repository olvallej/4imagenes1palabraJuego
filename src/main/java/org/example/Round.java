package org.example;

public class Round {

    public final String word;
    public final String[] images;
    public final int timeLimit;
    public long startTime;

    public Round(String word, String[] images, int timeLimit) {
        this.word = word;
        this.images = images;
        this.timeLimit = timeLimit;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }
}
