package org.example;


import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;

public class XMLDatabase {

    private final File roundsFile =
            new File(getClass().getClassLoader().getResource("data/rounds.xml").getFile());

    private final File scoresFile =
            new File(getClass().getClassLoader().getResource("data/scores.xml").getFile());


    public XMLDatabase() {
        ensureFiles();
    }

    private void ensureFiles() {
        if (!roundsFile.exists()) {
            System.err.println("⚠️ Falta rounds.xml");
        }
        if (!scoresFile.exists()) {
            try {
                scoresFile.createNewFile();
                saveEmptyScores();
            } catch (Exception ignored) {}
        }
    }

    private void saveEmptyScores() throws Exception {
        DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = b.newDocument();
        Element root = doc.createElement("scores");
        doc.appendChild(root);
        saveXML(doc, scoresFile);
    }

    private void saveXML(Document doc, File f) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.transform(new DOMSource(doc), new StreamResult(f));
    }

    // ---------------------------------------------------------
    // CARGAR RONDAS
    // ---------------------------------------------------------

    public List<Round> loadRounds() {
        List<Round> result = new ArrayList<>();

        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(roundsFile);

            NodeList list = doc.getElementsByTagName("round");

            for (int i = 0; i < list.getLength(); i++) {
                Element e = (Element) list.item(i);

                String word = e.getElementsByTagName("word").item(0).getTextContent();

                NodeList imgs = e.getElementsByTagName("img");
                String[] arr = new String[imgs.getLength()];
                for (int j = 0; j < imgs.getLength(); j++)
                    arr[j] = imgs.item(j).getTextContent();

                int time = Integer.parseInt(
                        e.getElementsByTagName("time").item(0).getTextContent());

                result.add(new Round(word, arr, time));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return result;
    }

    // ---------------------------------------------------------
    // GUARDAR PUNTAJE
    // ---------------------------------------------------------

    public synchronized void saveScore(String player, int points, String room) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(scoresFile);

            Element root = doc.getDocumentElement();

            Element score = doc.createElement("entry");
            score.setAttribute("player", player);
            score.setAttribute("room", room);
            score.setAttribute("points", String.valueOf(points));

            root.appendChild(score);

            saveXML(doc, scoresFile);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
