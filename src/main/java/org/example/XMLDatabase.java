package org.example;


import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;
import java.io.InputStream;


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
        List<Round> rounds = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("data/rounds.xml")) {

            if (is == null) {
                throw new IllegalStateException("No se encontró el archivo rounds.xml en resources/data/");
            }

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);

            NodeList roundNodes = doc.getElementsByTagName("round");

            for (int i = 0; i < roundNodes.getLength(); i++) {
                Element roundElement = (Element) roundNodes.item(i);

                // Obtener palabra
                Node wordNode = roundElement.getElementsByTagName("word").item(0);
                if (wordNode == null || wordNode.getTextContent().trim().isEmpty()) {
                    System.err.println("⚠️ Ronda " + (i + 1) + " sin <word>, se omitirá");
                    continue;
                }
                String word = wordNode.getTextContent().trim();

                // Obtener imágenes
                NodeList imgNodes = roundElement.getElementsByTagName("img");
                List<String> images = new ArrayList<>();
                for (int j = 0; j < imgNodes.getLength(); j++) {
                    String imgPath = imgNodes.item(j).getTextContent().trim();
                    if (!imgPath.isEmpty()) {
                        images.add(imgPath);
                    }
                }
                if (images.isEmpty()) {
                    System.err.println("⚠️ Ronda '" + word + "' no tiene imágenes, se omitirá");
                    continue;
                }

                // Obtener tiempo
                Node timeNode = roundElement.getElementsByTagName("time").item(0);
                if (timeNode == null || timeNode.getTextContent().trim().isEmpty()) {
                    System.err.println("⚠️ Ronda '" + word + "' sin <time>, se omitirá");
                    continue;
                }
                int time;
                try {
                    time = Integer.parseInt(timeNode.getTextContent().trim());
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Ronda '" + word + "' tiene <time> inválido, se omitirá");
                    continue;
                }

                rounds.add(new Round(word, images.toArray(new String[0]), time));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (rounds.isEmpty()) {
            throw new IllegalStateException("No se cargaron rondas válidas desde rounds.xml");
        }

        return rounds;
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
