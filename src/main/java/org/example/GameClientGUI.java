package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;


public class GameClientGUI extends Application {

    private Stage primaryStage;
    private String roomId = null;
    private String playerName = null;
    private int timeRemaining = 0;
    private Timeline timer;
    private boolean hasAnswered = false;
    private boolean isHost = false;
    private boolean gameStarted = false;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("🎮 4 Imágenes 1 Palabra");
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> {
            if (roomId != null && playerName != null) {
                leaveRoom();
            }
            Platform.exit();
        });

        showWelcomeScreen();
        primaryStage.show();
    }

    // ============================================================
    //                  PANTALLA DE BIENVENIDA
    // ============================================================
    private void showWelcomeScreen() {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label title = new Label("🎮 4 IMÁGENES 1 PALABRA");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Juego Multijugador");
        subtitle.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        Label instruction = new Label("Adivina la palabra que conecta las 4 imágenes");
        instruction.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
        instruction.setTextFill(Color.web("#e0e0e0"));

        TextField nameField = new TextField();
        nameField.setPromptText("Ingresa tu nombre");
        nameField.setMaxWidth(300);
        nameField.setFont(Font.font(16));
        nameField.setStyle("-fx-background-radius: 20; -fx-padding: 10;");

        Button continueBtn = createStyledButton("Continuar", "#4CAF50");
        continueBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                playerName = name;
                showMainMenu();
            } else {
                showAlert("Error", "Por favor ingresa tu nombre", Alert.AlertType.ERROR);
            }
        });

        nameField.setOnAction(e -> continueBtn.fire());

        root.getChildren().addAll(title, subtitle, instruction, new Label(), nameField, continueBtn);

        Scene scene = new Scene(root, 600, 450);
        primaryStage.setScene(scene);
    }

    // ============================================================
    //                      MENÚ PRINCIPAL
    // ============================================================
    private void showMainMenu() {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label welcome = new Label("¡Hola, " + playerName + "! 👋");
        welcome.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        welcome.setTextFill(Color.WHITE);

        VBox infoBox = new VBox(5);
        infoBox.setAlignment(Pos.CENTER);

        if (roomId != null) {
            Label roomInfo = new Label("🏠 Sala: " + roomId);
            roomInfo.setFont(Font.font("Arial", FontWeight.BOLD, 16));
            roomInfo.setTextFill(Color.web("#ffeb3b"));

            Label statusInfo = new Label("Estado: En espera");
            statusInfo.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
            statusInfo.setTextFill(Color.web("#e0e0e0"));

            Label hostLabel = new Label(isHost ? "💡 Eres el host de la sala" : "💡 Esperando que el host inicie");
            hostLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 14));
            hostLabel.setTextFill(Color.web("#e0e0e0"));

            infoBox.getChildren().addAll(roomInfo, statusInfo, hostLabel);
        }

        Button createRoomBtn = createStyledButton("🏠 Crear Sala Nueva", "#4CAF50");
        createRoomBtn.setOnAction(e -> showCreateRoomDialog());

        Button joinRoomBtn = createStyledButton("🚪 Unirse a Sala", "#2196F3");
        joinRoomBtn.setOnAction(e -> showJoinRoomDialog());

        Button startGameBtn = createStyledButton("▶️ Iniciar Juego", "#FF9800");
        startGameBtn.setDisable(roomId == null || !isHost); // solo host puede iniciar
        startGameBtn.setOnAction(e -> {
            if (isHost) {
                startGame();
            } else {
                showAlert("Acceso Denegado", "Solo el host puede iniciar el juego", Alert.AlertType.WARNING);
            }
        });

        Button statusBtn = createStyledButton("📊 Ver Estado de Sala", "#9C27B0");
        statusBtn.setDisable(roomId == null);
        statusBtn.setOnAction(e -> showStatus());

        Button leaveBtn = createStyledButton("🚪 Salir de Sala", "#FF5722");
        leaveBtn.setDisable(roomId == null);
        leaveBtn.setOnAction(e -> {
            leaveRoom();
            roomId = null;
            showMainMenu();
        });

        Button exitBtn = createStyledButton("🚫 Cerrar Aplicación", "#f44336");
        exitBtn.setOnAction(e -> {
            if (roomId != null) leaveRoom();
            Platform.exit();
        });

        root.getChildren().addAll(welcome, infoBox, new Label(),
                createRoomBtn, joinRoomBtn, startGameBtn,
                statusBtn, leaveBtn, new Label(), exitBtn);

        Scene scene = new Scene(root, 600, 600);
        primaryStage.setScene(scene);
    }

    // ============================================================
    //                   DIÁLOGO CREAR SALA
    // ============================================================
    private void showCreateRoomDialog() {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Crear Sala");
        dialog.setHeaderText("Configura tu sala de juego");

        ButtonType createButtonType = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label infoLabel = new Label("Número de jugadores:");
        Spinner<Integer> maxPlayersSpinner = new Spinner<>(2, 6, 6);
        maxPlayersSpinner.setEditable(true);

        grid.add(infoLabel, 0, 0);
        grid.add(maxPlayersSpinner, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return maxPlayersSpinner.getValue();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(maxPlayers -> {
            String data = "playerName=" + playerName + "&maxPlayers=" + maxPlayers;
            String response = sendPost("/create_room", data);

            if (response.contains("<status>OK</status>")) {
                roomId = extractValue(response, "roomId");
                startPollingStatus();
                isHost = true;
                showAlert("¡Éxito! 🎉",
                        "Sala creada exitosamente\n\n" +
                                "🔑 Código de sala: " + roomId + "\n\n" +
                                "Comparte este código con tus amigos para que se unan",
                        Alert.AlertType.INFORMATION);
                showMainMenu();
            } else {
                showAlert("Error", "No se pudo crear la sala", Alert.AlertType.ERROR);
            }
        });
    }

    // ============================================================
    //                   DIÁLOGO UNIRSE A SALA
    // ============================================================
    private void showJoinRoomDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Unirse a Sala");
        dialog.setHeaderText("Ingresa el código de la sala");
        dialog.setContentText("Código:");

        dialog.showAndWait().ifPresent(code -> {
            String data = "roomId=" + code.toUpperCase() + "&playerName=" + playerName;
            String response = sendPost("/join_room", data);

            if (response.contains("<status>OK</status>")) {
                roomId = code.toUpperCase();
                startPollingStatus();
                isHost = false;
                showAlert("¡Éxito! 🎉", "Te has unido a la sala " + roomId, Alert.AlertType.INFORMATION);
                showMainMenu();
            } else {
                String error = extractValue(response, "message");
                showAlert("Error", error, Alert.AlertType.ERROR);
            }
        });
    }

    // ============================================================
    //                      INICIAR JUEGO
    // ============================================================
    // ============================================================
//                      INICIAR JUEGO (cliente)
// ============================================================
    private void startGame() {
        try {
            // Enviar roomId y playerName al servidor
            String data = "roomId=" + roomId + "&playerName=" + playerName;
            String response = sendPost("/start_game", data);

            // Comprobar errores
            if (response.contains("<status>ERROR</status>") || response.contains("<status>ERROR")) {
                String msg = extractValue(response, "msg");
                if (msg.isEmpty()) msg = extractValue(response, "message"); // por si usas otro tag
                if (msg.contains("Min 2 jugadores")) {
                    showAlert("No se puede iniciar", "La sala debe tener al menos 2 jugadores para iniciar", Alert.AlertType.WARNING);
                } else if (msg.contains("Solo el host")) {
                    showAlert("Acceso denegado", "Solo el host puede iniciar el juego", Alert.AlertType.WARNING);
                } else {
                    showAlert("Error", msg.isEmpty() ? "Error al iniciar el juego" : msg, Alert.AlertType.ERROR);
                }
                return;
            }

            // No hay ronda
            if (response.contains("<status>NO_ROUND</status>") || response.contains("<status>NO_ROUND")) {
                showAlert("Error", "No hay rondas disponibles", Alert.AlertType.ERROR);
                return;
            }

            // Esperamos un <status>OK</status> con los datos de la ronda
            if (!response.contains("<status>OK</status>")) {
                showAlert("Error", "Respuesta inesperada del servidor", Alert.AlertType.ERROR);
                return;
            }

            // Obtener número de ronda (si viene)
            String roundStr = extractValue(response, "round");
            int round = 1;
            if (!roundStr.isEmpty()) {
                try { round = Integer.parseInt(roundStr); } catch (NumberFormatException ignored) {}
            }

            // Palabra
            String word = extractValue(response, "word");
            if (word.isEmpty()) {
                // Si servidor devuelve <word> dentro de un bloque distinto, intentar buscar "correctWord" u otros
                word = extractValue(response, "correctWord");
            }

            // Tiempo: aceptar timeLimit o time (compatibilidad)
            // Tiempo
            String timeStr = extractValue(response, "timeLimit");
            if (timeStr.isEmpty()) timeStr = extractValue(response, "time");

            // Normalizar
            timeStr = timeStr.replace("\n", "").replace("\r", "").trim();

            int timeLimit = 0;
            try {
                timeLimit = Integer.parseInt(timeStr);
            } catch (Exception e) {
                System.out.println("ERROR AL PARSEAR TIEMPO: [" + timeStr + "]");
            }


            // Imágenes: usar tu helper extractImages que busca <images> y <img>...
            String[] images = extractImages(response);

            // Validaciones
            if (word.isEmpty() || images.length == 0 || timeLimit <= 0) {
                showAlert("Error", "Datos de ronda inválidos (palabra/imágenes/tiempo). Revisa el XML del servidor.", Alert.AlertType.ERROR);
                return;
            }

            // Mostrar la pantalla de juego
            hasAnswered = false;
            showGameScreen(round, word, images, timeLimit);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo iniciar la ronda: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }





    // ============================================================
    //                   PANTALLA DE JUEGO
    // ============================================================
    private void showGameScreen(int round, String word, String[] images, int timeLimit) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // ================= HEADER =================
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label roundLabel = new Label("🎯 Ronda " + round + " de 5");
        roundLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        roundLabel.setTextFill(Color.WHITE);

        ProgressBar progressBar = new ProgressBar(1.0);
        progressBar.setPrefWidth(400);
        progressBar.setStyle("-fx-accent: #4CAF50;");

        Label timerLabel = new Label("⏱️ " + timeLimit + "s");
        timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        timerLabel.setTextFill(Color.web("#ffeb3b"));

        header.getChildren().addAll(roundLabel, progressBar, timerLabel);

        // ================= GRID DE IMÁGENES =================
        GridPane imageGrid = new GridPane();
        imageGrid.setAlignment(Pos.CENTER);
        imageGrid.setHgap(20);
        imageGrid.setVgap(20);
        imageGrid.setPadding(new Insets(30));

        for (int i = 0; i < 4 && i < images.length; i++) {
            VBox imageBox = createImageBox(images[i], i + 1);
            imageGrid.add(imageBox, i % 2, i / 2);
        }

        // ================= ÁREA DE RESPUESTA =================
        VBox answerArea = new VBox(15);
        answerArea.setAlignment(Pos.CENTER);
        answerArea.setPadding(new Insets(20));

        Label questionLabel = new Label("❓ ¿Qué palabra conecta estas 4 imágenes?");
        questionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        Label hintLabel = new Label("💡 Pista: La palabra tiene " + word.length() + " letras");
        hintLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        hintLabel.setTextFill(Color.web("#666666"));

        TextField answerField = new TextField();
        answerField.setPromptText("Escribe tu respuesta aquí...");
        answerField.setMaxWidth(400);
        answerField.setFont(Font.font(18));
        answerField.setStyle("-fx-background-radius: 25; -fx-padding: 15;");

        Button submitBtn = createStyledButton("✓ Enviar Respuesta", "#4CAF50");
        submitBtn.setOnAction(e -> {
            if (!hasAnswered) {
                if (timer != null) timer.stop();
                submitAnswer(answerField.getText(), word);
            }
        });

        answerField.setOnAction(e -> submitBtn.fire());

        Label warningLabel = new Label("⚠️ Solo puedes responder una vez por ronda");
        warningLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        warningLabel.setTextFill(Color.web("#FF5722"));

        answerArea.getChildren().addAll(questionLabel, hintLabel, answerField, submitBtn, warningLabel);

        // ================= SET SCENE =================
        root.setTop(header);
        root.setCenter(imageGrid);
        root.setBottom(answerArea);

        Scene scene = new Scene(root, 850, 750);
        primaryStage.setScene(scene);

        // ================= INICIAR TIMER =================
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        timeRemaining = timeLimit;

        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeRemaining--;
            timerLabel.setText("⏱️ " + timeRemaining + "s");
            progressBar.setProgress((double) timeRemaining / timeLimit);

            // Cambiar color de la barra según el tiempo
            if (timeRemaining <= 5) {
                progressBar.setStyle("-fx-accent: #f44336;");
            } else if (timeRemaining <= 10) {
                progressBar.setStyle("-fx-accent: #FF9800;");
            }

            if (timeRemaining <= 0) {
                timer.stop();
                if (!hasAnswered) {
                    showAlert("⏰ Tiempo agotado",
                            "¡Se acabó el tiempo!\n\nLa respuesta correcta era: " + word.toUpperCase(),
                            Alert.AlertType.WARNING);
                    showNextRoundDialog();
                }
            }
        }));
        timer.setCycleCount(timeLimit);
        timer.play();
    }



    // ============================================================
    //                   ENVIAR RESPUESTA
    // ============================================================
    private void submitAnswer(String answer, String correctWord) {
        if (answer.trim().isEmpty()) {
            showAlert("Error", "Por favor ingresa una respuesta", Alert.AlertType.ERROR);
            timer.play();
            return;
        }

        hasAnswered = true;
        String data = "roomId=" + roomId + "&playerName=" + playerName + "&answer=" + answer;
        String response = sendPost("/submit_answer", data);

        String status = extractValue(response, "status");
        int points = Integer.parseInt(extractValue(response, "points"));

        switch (status) {
            case "CORRECT":
                showAlert("¡CORRECTO! 🎉",
                        "¡Excelente trabajo!\n\n" +
                                "🌟 Has ganado " + points + " puntos\n" +
                                "⚡ " + (points > 700 ? "¡Muy rápido!" : points > 500 ? "¡Buen tiempo!" : "¡Lo lograste!"),
                        Alert.AlertType.INFORMATION);
                break;

            case "INCORRECT":
                showAlert("❌ Incorrecto",
                        "Tu respuesta es incorrecta\n\n" +
                                "💡 La respuesta correcta era: " + correctWord.toUpperCase(),
                        Alert.AlertType.ERROR);
                break;

            case "ALREADY_ANSWERED":
                showAlert("⚠️ Ya respondiste", "Ya has enviado una respuesta para esta ronda", Alert.AlertType.WARNING);
                timer.play();
                break;

            case "TIMEOUT":
                showAlert("⏰ Tiempo agotado", "El tiempo se agotó antes de enviar tu respuesta", Alert.AlertType.WARNING);
                break;

            default:
                showAlert("Error", "Ocurrió un error al enviar la respuesta", Alert.AlertType.ERROR);
                timer.play();
                break;
        }


        // Mostrar diálogo para pasar a la siguiente ronda si corresponde
        if (status.equals("CORRECT") || status.equals("INCORRECT") || status.equals("TIMEOUT")) {
            showNextRoundDialog();
        }
    }


    // ============================================================
    //                   DIÁLOGO SIGUIENTE RONDA
    // ============================================================
    private void showNextRoundDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Ronda completada");
        alert.setHeaderText("¿Qué quieres hacer ahora?");
        alert.setContentText("Elige una opción:");

        ButtonType nextRoundBtn = new ButtonType("➡️ Siguiente Ronda");
        ButtonType scoresBtn = new ButtonType("📊 Ver Puntuaciones");
        ButtonType menuBtn = new ButtonType("🏠 Volver al Menú");

        alert.getButtonTypes().setAll(nextRoundBtn, scoresBtn, menuBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == nextRoundBtn) {
                nextRound();
            } else if (response == scoresBtn) {
                showStatus();
                // Dar opción de continuar después de ver puntuaciones
                Platform.runLater(() -> {
                    Alert continueAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    continueAlert.setTitle("Continuar");
                    continueAlert.setHeaderText("¿Listo para la siguiente ronda?");
                    continueAlert.setContentText("Presiona OK para continuar");
                    continueAlert.showAndWait().ifPresent(r -> {
                        if (r == ButtonType.OK) {
                            nextRound();
                        } else {
                            showMainMenu();
                        }
                    });
                });
            } else {
                showMainMenu();
            }
        });
    }

    // ============================================================
    //                   SIGUIENTE RONDA
    // ============================================================
    private void nextRound() {
        String data = "roomId=" + roomId;
        String response = sendPost("/next_round", data);

        if (response.contains("<status>GAME_OVER</status>")) {
            showGameOver(response);
        } else if (response.contains("<status>OK</status>")) {
            // Tomar siguiente ronda
            String nextRound = response.split("<round>")[1].split("</round>")[0];

            String word = extractValue(nextRound, "word");
            String[] images = extractImages(nextRound);
            int timeLimit = Integer.parseInt(extractValue(nextRound, "time"));

            if (word.isEmpty() || images.length == 0 || timeLimit <= 0) {
                showAlert("Error", "Datos de ronda inválidos", Alert.AlertType.ERROR);
                return;
            }

            hasAnswered = false;
            showGameScreen(1, word, images, timeLimit); // aquí round = 1, puedes mantener un contador global si quieres
        } else {
            String error = extractValue(response, "message");
            showAlert("Error", error, Alert.AlertType.ERROR);
        }
    }

    // ============================================================
    //                   PANTALLA GAME OVER
    // ============================================================
    private void showGameOver(String response) {
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%);");

        Label title = new Label("🏁 ¡JUEGO TERMINADO!");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("🏆 Clasificación Final");
        subtitle.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        subtitle.setTextFill(Color.web("#ffeb3b"));

        VBox scoresList = new VBox(12);
        scoresList.setAlignment(Pos.CENTER);
        scoresList.setPadding(new Insets(20));
        scoresList.setStyle("-fx-background-color: rgba(255, 255, 255, 0.1); " +
                "-fx-background-radius: 15; -fx-padding: 20;");

        String scoresSection = response.substring(
                response.indexOf("<finalScores>") + 13,
                response.indexOf("</finalScores>")
        );

        String[] playerBlocks = scoresSection.split("</player>");
        int position = 1;
        String winner = null;

        for (String block : playerBlocks) {
            if (block.contains("<player>")) {
                String name = extractValueFromBlock(block, "n");
                String score = extractValueFromBlock(block, "score");

                if (position == 1) winner = name;

                String medal = position == 1 ? "🥇" : position == 2 ? "🥈" : position == 3 ? "🥉" : "🏅";
                String positionText = position + (position == 1 ? "er" : position == 2 ? "do" : position == 3 ? "er" : "to");

                HBox playerBox = new HBox(15);
                playerBox.setAlignment(Pos.CENTER_LEFT);
                playerBox.setPadding(new Insets(10));
                playerBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.15); " +
                        "-fx-background-radius: 10;");
                playerBox.setPrefWidth(400);

                Label rankLabel = new Label(medal + " " + positionText);
                rankLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
                rankLabel.setTextFill(Color.WHITE);
                rankLabel.setPrefWidth(80);

                Label nameLabel = new Label(name);
                nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
                nameLabel.setTextFill(Color.WHITE);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                Label scoreLabel = new Label(score + " pts");
                scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
                scoreLabel.setTextFill(Color.web("#ffeb3b"));

                playerBox.getChildren().addAll(rankLabel, nameLabel, scoreLabel);
                scoresList.getChildren().add(playerBox);
                position++;
            }
        }

        Label winnerLabel = new Label("");
        if (winner != null) {
            if (winner.equals(playerName)) {
                winnerLabel.setText("🎊 ¡FELICITACIONES, GANASTE! 🎊");
                winnerLabel.setTextFill(Color.web("#ffeb3b"));
            } else {
                winnerLabel.setText("El ganador es: " + winner);
                winnerLabel.setTextFill(Color.WHITE);
            }
            winnerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        }

        Button newGameBtn = createStyledButton("🎮 Nueva Partida", "#4CAF50");
        newGameBtn.setOnAction(e -> {
            roomId = null;
            showMainMenu();
        });

        Button exitBtn = createStyledButton("🚪 Salir", "#f44336");
        exitBtn.setOnAction(e -> {
            leaveRoom();
            Platform.exit();
        });

        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(newGameBtn, exitBtn);

        root.getChildren().addAll(title, subtitle, scoresList, winnerLabel, new Label(), buttonBox);

        Scene scene = new Scene(root, 650, 700);
        primaryStage.setScene(scene);
    }

    // ============================================================
    //                   VER ESTADO
    // ============================================================
    private void showStatus() {
        String data = "roomId=" + roomId;
        String response = sendPost("/get_status", data);

        if (response.contains("<status>OK</status>")) {
            boolean gameStarted = extractValue(response, "gameStarted").equals("true");
            String currentRound = gameStarted ? extractValue(response, "currentRound") : "N/A";

            StringBuilder status = new StringBuilder();
            status.append("═══════════════════════════════════\n");
            status.append("🏠 SALA: ").append(roomId).append("\n");
            status.append("═══════════════════════════════════\n\n");
            status.append("🎮 Juego iniciado: ").append(gameStarted ? "SÍ" : "NO").append("\n");
            if (gameStarted) {
                status.append("🔢 Ronda actual: ").append(currentRound).append("/5\n");
            }
            status.append("\n👥 JUGADORES Y PUNTUACIONES:\n");
            status.append("═══════════════════════════════════\n");

            String playersSection = response.substring(
                    response.indexOf("<players>") + 9,
                    response.indexOf("</players>")
            );

            String[] playerBlocks = playersSection.split("</player>");
            int position = 1;
            for (String block : playerBlocks) {
                if (block.contains("<player>")) {
                    String name = extractValueFromBlock(block, "n");
                    String score = extractValueFromBlock(block, "score");
                    String medal = position == 1 ? "🥇" : position == 2 ? "🥈" : position == 3 ? "🥉" : "🏅";
                    status.append(medal).append(" ").append(name).append(": ").append(score).append(" pts\n");
                    position++;
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Estado de la Sala");
            alert.setHeaderText(null);
            alert.setContentText(status.toString());
            alert.showAndWait();
        } else {
            showAlert("Error", "No se pudo obtener el estado", Alert.AlertType.ERROR);
        }
    }

    // ============================================================
    //                   SALIR DE SALA
    // ============================================================
    private void leaveRoom() {
        if (roomId != null && playerName != null) {
            String data = "roomId=" + roomId + "&playerName=" + playerName;
            sendPost("/leave_room", data);
        }
    }

    // ============================================================
    //                   UTILIDADES
    // ============================================================
    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setMinWidth(250);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                "-fx-background-radius: 25; -fx-padding: 15 30; " +
                "-fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);");

        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + "-fx-scale-x: 1.05; -fx-scale-y: 1.05;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle()));

        return btn;
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ============================================================
//                   COMUNICACIÓN CON SERVIDOR
// ============================================================

    private String sendPost(String endpoint, String data) {
        try (Socket socket = new Socket("localhost", 5555)) {

            // Construir petición HTTP POST manualmente
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            writer.write("POST " + endpoint + " HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Content-Type: application/x-www-form-urlencoded\r\n");
            writer.write("Content-Length: " + data.length() + "\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.write(data);
            writer.flush();

            // Leer respuesta
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;
            boolean body = false;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    body = true;
                    continue;
                }
                if (body) response.append(line).append("\n");
            }

            return response.toString().trim();

        } catch (IOException e) {
            e.printStackTrace();
            return "<status>ERROR</status><message>No se pudo conectar con el servidor</message>";
        }
    }

// ============================================================
//                   EXTRAER VALORES DEL XML
// ============================================================

    private String extractValue(String xml, String key) {
        if (xml == null || key == null) return "";

        try {
            String open = "<" + key + ">";
            String close = "</" + key + ">";

            int start = xml.indexOf(open);
            if (start == -1) return "";

            start += open.length();
            int end = xml.indexOf(close, start);
            if (end == -1) return "";

            return xml.substring(start, end).trim();

        } catch (Exception e) {
            return "";
        }
    }


    private String extractValueFromBlock(String block, String key) {
        return extractValue(block, key);
    }

// ============================================================
//                   EXTRAER LISTA DE IMÁGENES
// ============================================================

    private VBox createImageBox(String imagePath, int number) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10));
        box.setPrefSize(220, 180); // REDUCIDO
        box.setMaxSize(220, 180);   // MÁXIMO
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");

        ImageView imgView = new ImageView();
        imgView.setFitWidth(200);   // REDUCIDO
        imgView.setFitHeight(140);  // REDUCIDO
        imgView.setPreserveRatio(true);
        imgView.setSmooth(true);

        Label numberLabel = new Label("📷 " + number);
        numberLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        numberLabel.setTextFill(Color.web("#666666"));

        Label loadingLabel = new Label("Cargando...");
        loadingLabel.setFont(Font.font("Arial", 10));
        loadingLabel.setTextFill(Color.GRAY);

        box.getChildren().addAll(imgView, numberLabel, loadingLabel);

        // Cargar imagen de forma asíncrona
        new Thread(() -> {
            try {
                String imageUrl = "http://localhost:5555/" + imagePath;
                System.out.println("🔍 Cargando imagen desde: " + imageUrl);

                Image img = new Image(imageUrl, true);

                img.progressProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() >= 1.0) {
                        Platform.runLater(() -> {
                            if (!img.isError()) {
                                imgView.setImage(img);
                                box.getChildren().remove(loadingLabel);
                                System.out.println("✓ Imagen cargada: " + imagePath);
                            } else {
                                loadingLabel.setText("❌ Error");
                                loadingLabel.setTextFill(Color.RED);
                                System.err.println("❌ Error de imagen: " + imagePath);
                                if (img.getException() != null) {
                                    System.err.println("   Causa: " + img.getException().getMessage());
                                }
                            }
                        });
                    }
                });

                if (img.isError()) {
                    Platform.runLater(() -> {
                        loadingLabel.setText("❌ No encontrada");
                        loadingLabel.setTextFill(Color.RED);
                        System.err.println("❌ Imagen no disponible: " + imagePath);
                    });
                }

            } catch (Exception e) {
                System.err.println("❌ Excepción cargando imagen: " + imagePath);
                e.printStackTrace();

                Platform.runLater(() -> {
                    loadingLabel.setText("❌ Error");
                    loadingLabel.setTextFill(Color.RED);
                });
            }
        }).start();

        return box;
    }


    private String[] extractImages(String xml) {
        List<String> imgs = new ArrayList<>();
        if (xml == null) return new String[0];

        int index = 0;

        while (true) {
            int start = xml.indexOf("<img>", index);
            if (start == -1) break;

            start += 5; // longitud "<img>"
            int end = xml.indexOf("</img>", start);
            if (end == -1) break;

            String value = xml.substring(start, end).trim();
            if (!value.isEmpty()) {
                imgs.add(value);
            }

            index = end + 6; // saltar "</img>"
        }

        return imgs.toArray(new String[0]);
    }


    private Image loadImageFromResources(String path) {
        try {
            // Normalizar ruta del XML: "data/imagenes/x.png"
            String fixed = path.startsWith("data/")
                    ? path
                    : "data/" + path;

            InputStream is = getClass().getClassLoader().getResourceAsStream(fixed);
            if (is == null) {
                System.out.println("❌ No se encontró la imagen: " + fixed);
                return null;
            }

            return new Image(is);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private void startPollingStatus() {
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();

        poller.scheduleAtFixedRate(() -> {
            try {
                String data = "roomId=" + roomId;
                String response = sendPost("/get_status", data);

                boolean started = extractValue(response, "started").equalsIgnoreCase("true");
                if (started && !gameStarted) {
                    gameStarted = true;

                    // Mostrar ronda actual
                    int round = 1;
                    String word = extractValue(response, "word");
                    List<String> imagesList = extractList(response, "img");
                    String[] images = imagesList.toArray(new String[0]);
                    int timeLimit = Integer.parseInt(extractValue(response, "time"));

                    Platform.runLater(() -> showGameScreen(round, word, images, timeLimit));
                }

                // Comprobar si el juego terminó
                boolean gameOver = extractValue(response, "gameOver").equalsIgnoreCase("true");
                if (gameOver) {
                    // Obtener el XML de puntuaciones
                    String scoresXml = extractValue(response, "scores");
                    Platform.runLater(() -> showGameOver(scoresXml));

                    // Detener el polling cuando termine la partida
                    poller.shutdownNow();
                }

            } catch (Exception ignored) {}
        }, 0, 1, TimeUnit.SECONDS);
    }


    private List<String> extractList(String xml, String tag) {
        List<String> list = new ArrayList<>();
        int index = 0;
        while (true) {
            int start = xml.indexOf("<" + tag + ">", index);
            if (start == -1) break;
            int end = xml.indexOf("</" + tag + ">", start);
            if (end == -1) break;
            String value = xml.substring(start + tag.length() + 2, end).trim();
            list.add(value);
            index = end + tag.length() + 3;
        }
        return list;
    }


    private Map<String, Integer> extractScores(String xml) {
        Map<String, Integer> scores = new HashMap<>();
        int index = 0;
        while (true) {
            int start = xml.indexOf("<player>", index);
            if (start == -1) break;
            int end = xml.indexOf("</player>", start);
            String block = xml.substring(start, end + 9);

            String name = extractValue(block, "n");
            int score = Integer.parseInt(extractValue(block, "score"));
            scores.put(name, score);

            index = end + 9;
        }
        return scores;
    }
    private void updateScores(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 Puntuaciones finales:\n\n");

        // Ordenar por puntuación descendente
        scores.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .forEach(entry -> sb.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" pts\n"));

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Puntuaciones Finales");
            alert.setHeaderText(null);
            alert.setContentText(sb.toString());
            alert.showAndWait();
        });
    }

}

