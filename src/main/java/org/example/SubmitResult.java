package org.example;


public class SubmitResult {

    public final boolean correct;
    public final int points;
    public final String correctWord;

    public SubmitResult(boolean correct, int points, String correctWord) {
        this.correct = correct;
        this.points = points;
        this.correctWord = correctWord;
    }

    public String toXML() {
        return "<response>" +
                "<correct>" + correct + "</correct>" +
                "<points>" + points + "</points>" +
                "<correctWord>" + correctWord + "</correctWord>" +
                "</response>";
    }
}
