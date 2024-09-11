    package com.bfr.culture;
    
    import static android.os.SystemClock.sleep;

    import java.util.HashMap;
    import java.util.Map;
    import java.util.Random;
    
    import android.Manifest;
    import android.content.Intent;
    import android.content.pm.PackageManager;
    import android.graphics.drawable.Drawable;
    import android.media.MediaPlayer;
    import android.net.Uri;
    import android.os.Build;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.os.RemoteException;
    import android.util.Log;
    import android.view.View;
    import android.widget.AdapterView;
    import android.widget.ArrayAdapter;
    import android.widget.Button;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.ListView;
    import android.widget.TextView;
    import android.widget.Toast;
    import androidx.annotation.NonNull;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.app.ActivityCompat;
    import androidx.core.content.ContextCompat;
    import androidx.core.content.FileProvider;
    
    import com.bfr.buddy.ui.shared.FacialExpression;
    import com.bfr.buddy.ui.shared.LabialExpression;
    import com.bfr.buddy.speech.shared.ISTTCallback;
    import com.bfr.buddy.speech.shared.STTResult;
    import com.bfr.buddy.speech.shared.STTResultsData;
    import com.bfr.buddysdk.BuddyActivity;
    import com.bfr.buddysdk.BuddySDK;
    import com.bfr.buddysdk.services.speech.STTTask;
    import com.bfr.buddy.usb.shared.IUsbCommadRsp;
    import com.bumptech.glide.Glide;
    import com.bumptech.glide.annotation.GlideModule;
    import com.bumptech.glide.module.AppGlideModule;
    import com.microsoft.cognitiveservices.speech.*;
    
    import java.io.File;
    import java.util.ArrayList;
    import java.util.Arrays;
    import java.util.List;
    import java.util.Locale;
    import java.util.concurrent.Future;
    
    public class MainActivity extends BuddyActivity {
        private static final String SUBSCRIPTION_KEY = "522c4a2067f34864aaa6a35388cc4e1c";
        private static final String SERVICE_REGION = "westeurope";
        private static final int PERMISSIONS_REQUEST_CODE = 1;
    
        private static final String TAG = "MainActivity";
        private ListView listViewFiles;
        private Button buttonBrowse;
        private TextView recognizedText;
        private ImageView imageView;
        private LinearLayout mainButtonsContainer;
        private Button buttonBack;
        private TextView sttState;
        private Handler handler = new Handler(Looper.getMainLooper());
        private String folderPath = "/storage/emulated/0/Movies";
    
    
        private boolean awaitingCultureResponse = false;  // Added
        private boolean awaitingObjectResponse = false;   // Added
        private boolean awaitingTeachingResponse = false; // Added
        private boolean awaitingClothesGamesResponse = false;
    
        private STTTask sttTask;
        private boolean isSpeechServiceReady = false;
        private boolean awaitingGameResponse = false;
        private boolean isListening = false; // Flag to check if listening is active
        private String currentLanguage = "en-US"; // Default language
    
        private SpeechRecognizer recognizer;
        private boolean isProcessing = false; // To prevent duplicate processing of the same speech input
    
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
    
            initViews();
            configureListeners();
            checkPermissions();
        }
    
        private void initViews() {
            mainButtonsContainer = findViewById(R.id.mainButtonsContainer);
            // buttonBrowse = findViewById(R.id.button_browse);
            Button buttonListen = findViewById(R.id.button_listen);
            listViewFiles = findViewById(R.id.listView_files);
            recognizedText = findViewById(R.id.recognizedText);
            imageView = findViewById(R.id.imageView);
            buttonBack = findViewById(R.id.button_back);
            sttState = findViewById(R.id.sttState);
    
            buttonListen.setOnClickListener(v -> {
                mainButtonsContainer.setVisibility(View.GONE);
                startContinuousRecognition();
            });
    
            buttonBack.setOnClickListener(v -> {
                if (isListening) {
                    stopListening();
                }
                showMainButtons();
            });
        }
    
        private void configureListeners() {
            listViewFiles.setOnItemClickListener(this::onVideoSelected);
        }
    
        private void checkPermissionsAndLoadFiles() {
            Log.i(TAG, "checkPermissionsAndLoadFiles: Checking permissions for READ_EXTERNAL_STORAGE");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "checkPermissionsAndLoadFiles: Permission not granted. Requesting permission.");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
            } else {
                Log.i(TAG, "checkPermissionsAndLoadFiles: Permission granted. Loading video files.");
                loadVideoFiles();
            }
        }
    
        private void loadVideoFiles() {
            Log.i(TAG, "loadVideoFiles: Loading video files from directory.");
            File directory = new File(folderPath);
            ArrayList<String> videoNames = new ArrayList<>();
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".mp4")) {
                            videoNames.add(file.getName());
                            Log.d(TAG, "Loaded video file: " + file.getName());
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (!videoNames.isEmpty()) {
                        Log.i(TAG, "loadVideoFiles: Videos found. Updating ListView.");
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, videoNames);
                        listViewFiles.setAdapter(adapter);
                        listViewFiles.setVisibility(View.VISIBLE);
                        mainButtonsContainer.setVisibility(View.GONE);
                        showBackButton();
                    } else {
                        Log.i(TAG, "loadVideoFiles: No videos found.");
                        Toast.makeText(this, "No videos found.", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    Log.i(TAG, "loadVideoFiles: Directory not found.");
                    Toast.makeText(this, "Directory not found.", Toast.LENGTH_LONG).show();
                });
            }
        }
    
        private void onVideoSelected(AdapterView<?> parent, View view, int position, long id) {
            String filePath = folderPath + "/" + parent.getItemAtPosition(position).toString();
            Log.d(TAG, "onVideoSelected: FilePath: " + filePath);
            playVideo(filePath);
        }
    
        private void playVideo(String filePath) {
            try {
                File videoFile = new File(filePath);
                Uri videoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", videoFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(videoUri, "video/mp4");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "playVideo: Exception: " + e.getMessage(), e);
                Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show();
            }
        }
    
        private void moveBuddy(float speed, float distance, Runnable onSuccess) {
            Log.i(TAG, "Sending moveBuddy command: speed=" + speed + ", distance=" + distance);
    
            runOnUiThread(() -> {
                recognizedText.setText("Moving...");
                recognizedText.setVisibility(View.VISIBLE);
            });
    
            BuddySDK.USB.moveBuddy(speed, distance, new IUsbCommadRsp.Stub() {
                @Override
                public void onSuccess(String s) throws RemoteException {
                    Log.i(TAG, "moveBuddy success: " + s);
                    runOnUiThread(() -> {
                        recognizedText.setText("Move successful");
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                }
    
                @Override
                public void onFailed(String s) throws RemoteException {
                    Log.i(TAG, "moveBuddy failed: " + s);
                    runOnUiThread(() -> recognizedText.setText("Failed to move"));
                }
            });
        }
    
        private void rotateBuddy(float rotspeed, float angle, Runnable onSuccess) {
            Log.i(TAG, "Sending rotateBuddy command: rotspeed=" + rotspeed + ", angle=" + angle);
    
            runOnUiThread(() -> {
                recognizedText.setText("Rotating...");
                recognizedText.setVisibility(View.VISIBLE);
            });
    
            BuddySDK.USB.rotateBuddy(rotspeed, angle, new IUsbCommadRsp.Stub() {
                @Override
                public void onSuccess(String s) throws RemoteException {
                    Log.i(TAG, "rotateBuddy success: " + s);
                    runOnUiThread(() -> {
                        recognizedText.setText("Rotate successful");
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                }
    
                @Override
                public void onFailed(String s) throws RemoteException {
                    Log.i(TAG, "rotateBuddy failed: " + s);
                    runOnUiThread(() -> recognizedText.setText("Failed to rotate"));
                }
            });
        }
    
        private void initializeRecognizer() {
            SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
            List<String> languages = Arrays.asList("en-US", "fr-FR", "it-IT");
            AutoDetectSourceLanguageConfig autoDetectSourceLanguageConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);
    
            recognizer = new SpeechRecognizer(config, autoDetectSourceLanguageConfig);
    
            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    Log.i(TAG, "Recognized: " + e.getResult().getText());
    
                    // Update currentLanguage based on detected language
                    AutoDetectSourceLanguageResult languageResult = AutoDetectSourceLanguageResult.fromResult(e.getResult());
                    currentLanguage = languageResult.getLanguage();
                    Log.i(TAG, "Detected language: " + currentLanguage);
    
                    runOnUiThread(() -> {
                        recognizedText.setText("Recognized: " + e.getResult().getText());
                        handleSpeechInteraction(e.getResult().getText().toLowerCase());
                    });
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    Log.i(TAG, "No speech could be recognized.");
                    runOnUiThread(() -> recognizedText.setText("No speech could be recognized."));
                }
            });
    
            recognizer.canceled.addEventListener((s, e) -> {
                Log.i(TAG, "Canceled: Reason=" + e.getReason() + "\nErrorDetails: " + e.getErrorDetails());
                runOnUiThread(() -> recognizedText.setText("Recognition canceled."));
            });
    
            recognizer.sessionStarted.addEventListener((s, e) -> Log.i(TAG, "Session started."));
            recognizer.sessionStopped.addEventListener((s, e) -> Log.i(TAG, "Session stopped."));
        }
    
        private void startContinuousRecognition() {
            try {
                if (recognizer == null) {
                    initializeRecognizer();
                }
                recognizer.startContinuousRecognitionAsync();
                isListening = true;
                runOnUiThread(() -> {
                    sttState.setText("Listening...");
                    sttState.setVisibility(View.VISIBLE);
                    buttonBack.setVisibility(View.VISIBLE);
                    recognizedText.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error starting continuous recognition: " + e.getMessage());
            }
        }
    
        private synchronized void handleSpeechInteraction(String speechText) {
            if (isProcessing) {
                return; // Skip processing if we're already processing a speech input
            }
    
            isProcessing = true; // Set the flag to indicate we're processing
    
            // Movement commands
            if (speechText.contains("move forward")) {
                Log.i(TAG, "Command to move forward recognized.");
                runOnUiThread(() -> moveBuddy(0.3F, 0.2F, null));
            } else if (speechText.contains("turn left")) {
                Log.i(TAG, "Command to rotate left recognized.");
                runOnUiThread(() -> rotateBuddy(90, 110, null));
            } else if (speechText.contains("turn right")) {
                Log.i(TAG, "Command to rotate right recognized.");
                runOnUiThread(() -> rotateBuddy(90, -110, null));
            } else {
                // Food/drink interaction
                switch (currentLanguage) {
                    case "fr-FR":
                        handleGameRoomInteractionFR(speechText);
                        break;
                    case "it-IT":
                        handleGameRoomInteractionIT(speechText);
                        break;
                    default:
                        handleGameRoomInteractionEN(speechText);
                        break;
                }
            }
    
            isProcessing = false; // Reset the flag after processing is complete
        }
    
    
        // English game room interaction
        // English game room interaction

        private void handleGameRoomInteractionEN(String speechText) {
            if (speechText.contains("your culture") || speechText.contains("yourself")) {
                Log.i(TAG, "Command to talk about robot's culture recognized in English.");
                runOnUiThread(() -> {
                    sayText("I am Buddy, a robot from another planet. On my planet, we wear shiny silver clothes, and we love playing games where we fly in the sky. What about your culture?", "en-US-JennyNeural");
                });
                awaitingCultureResponse = true;  // Awaiting response about culture
            } else if (awaitingCultureResponse) {
                Log.i(TAG, "Responding to culture query in English");
                awaitingCultureResponse = false;  // Reset the flag after receiving the response

                runOnUiThread(() -> {
                    sayText("Can you tell me what kind of clothes you wear? What games do you play?", "en-US-JennyNeural");
                });
                awaitingClothesGamesResponse = true;  // Move to awaiting clothes and games response
            } else if (awaitingClothesGamesResponse && (speechText.contains("we wear") || speechText.contains("we play"))) {
                Log.i(TAG, "User responded to clothes and games query.");
                awaitingClothesGamesResponse = false;  // Reset the flag after response

                runOnUiThread(() -> {
                    sayText("Thanks for sharing! It’s always interesting to hear about different fashion choices.", "en-US-JennyNeural");
                });
            } else if (speechText.contains("hello buddy") || speechText.contains("hi buddy how are you")) {
                Log.i(TAG, "Greeting recognized in English.");
                runOnUiThread(() -> {
                    sayText("Hello! What are you playing with? Can you show me your toys?", "en-US-JennyNeural");
                });
                awaitingObjectResponse = true;  // Awaiting response about objects
            } else if (awaitingObjectResponse) {
                if (isNegativeResponse(speechText)) {
                    Log.i(TAG, "Negative response to object interaction in English.");
                    runOnUiThread(() -> sayText("Oh, ok.", "en-US-JennyNeural"));
                    awaitingObjectResponse = false;  // Reset after response
                } else if (isPositiveResponse(speechText)){
                    Log.i(TAG, "Positive or neutral response to object interaction in English.");
                    runOnUiThread(() -> {
                        sayText("Wow, that looks fun! Can you teach me what it's called? How do you say that in your language?", "en-US-JennyNeural");
                    });
                    awaitingObjectResponse = false;  // Reset after response
                    awaitingTeachingResponse = true;  // Awaiting teaching about the object
                }
            } else if (awaitingTeachingResponse && (speechText.contains("it is called") || speechText.contains("call")) ){
                Log.i(TAG, "Responding to teaching interaction in English.");
                runOnUiThread(() -> sayText("Thank you for teaching me!", "en-US-JennyNeural"));
                awaitingTeachingResponse = false;
            } else if (speechText.contains("play a game") || speechText.contains("guess the name")) {
                Log.i(TAG, "Invitation to play a game recognized in English.");
                runOnUiThread(() -> {
                    sayText("Let's play a fun game! I will show you pictures on the tablet, and you can guess what they are called. Are you ready?", "en-US-JennyNeural");
                    new Handler(Looper.getMainLooper()).postDelayed(this::showTabletGame, 8000);  // Delay in milliseconds
                });
            }
        }

        private boolean isNegativeResponse(String speechText) {
            String normalizedText = speechText.toLowerCase();
            // Check specifically for a negative response "no"
            return normalizedText.contains("no") || normalizedText.contains("yourself");
        }

        private boolean isPositiveResponse(String speechText) {
            String normalizedText = speechText.toLowerCase();
            // Check specifically for a negative response "no"
            return normalizedText.contains("yes") || normalizedText.contains("you can");
        }
        // French game room interaction
        // French game room interaction
        private void handleGameRoomInteractionFR(String speechText) {
            if (speechText.contains("culture") || speechText.contains("sur toi")) {
                Log.i(TAG, "Command to talk about robot's culture recognized in French.");
                runOnUiThread(() -> {
                    sayText("Je suis Buddy, un robot d'une autre planète. Sur ma planète, nous portons des vêtements argentés et nous aimons jouer à des jeux où nous volons dans le ciel. Et ta culture?", "fr-FR-HenriNeural");
                });
                awaitingCultureResponse = true;  // Awaiting response about culture
            } else if (awaitingCultureResponse) {
                Log.i(TAG, "Responding to culture query in French");
                awaitingCultureResponse = false;  // Reset the flag after receiving the response

                runOnUiThread(() -> {
                    sayText("Peux-tu me dire quels types de vêtements tu portes? Quels jeux joues-tu?", "fr-FR-HenriNeural");
                });
                awaitingClothesGamesResponse = true;  // Move to awaiting clothes and games response
            } else if (awaitingClothesGamesResponse && (speechText.contains("nous portons") || speechText.contains("nous jouons"))) {
                Log.i(TAG, "User responded to clothes and games query.");
                awaitingClothesGamesResponse = false;  // Reset the flag after response

                runOnUiThread(() -> {
                    sayText("Merci de partager! C'est toujours intéressant d'entendre parler de choix de mode différents.", "fr-FR-HenriNeural");
                });
            } else if (speechText.contains("bonjour") || speechText.contains("salut")) {
                Log.i(TAG, "Greeting recognized in French.");
                runOnUiThread(() -> {
                    sayText("Bonjour! Avec quoi joues-tu? Peux-tu me montrer tes poupées ou jouets?", "fr-FR-HenriNeural");
                });
                awaitingObjectResponse = true;  // Awaiting response about objects
            } else if (awaitingObjectResponse) {
                if (isNegativeResponseFR(speechText)) {
                    Log.i(TAG, "Negative response to object interaction in French.");
                    runOnUiThread(() -> sayText("Oh, d'accord.", "fr-FR-HenriNeural"));
                    awaitingObjectResponse = false;  // Reset after response
                } else if (isPositiveResponseFR(speechText)){
                    Log.i(TAG, "Positive or neutral response to object interaction in French.");
                    runOnUiThread(() -> {
                        sayText("Wow, ça a l'air amusant! Peux-tu m'apprendre comment ça s'appelle? Comment dis-tu ça dans ta langue?", "fr-FR-HenriNeural");
                    });
                    awaitingObjectResponse = false;  // Reset after response
                    awaitingTeachingResponse = true;  // Awaiting teaching about the object
                }
            } else if (awaitingTeachingResponse && (speechText.contains("ça s'appelle") || speechText.contains("appelle"))) {
                Log.i(TAG, "Responding to teaching interaction in French.");
                runOnUiThread(() -> sayText("Merci de m'avoir appris!", "fr-FR-HenriNeural"));
                awaitingTeachingResponse = false;
            } else if (speechText.contains("jouer à un jeu") || speechText.contains("devinez le nom")) {
                Log.i(TAG, "Invitation to play a game recognized in French.");
                runOnUiThread(() -> {
                    sayText("Jouons à un jeu amusant! Je vais te montrer des images sur la tablette, et tu peux deviner comment elles s'appellent. Prêt?", "fr-FR-HenriNeural");
                    new Handler(Looper.getMainLooper()).postDelayed(this::showTabletGame, 8000);  // Delay in milliseconds
                });
            }
        }

        private boolean isNegativeResponseFR(String speechText) {
            String normalizedText = speechText.toLowerCase();
            return normalizedText.contains("non") || normalizedText.contains("rien");
        }

        private boolean isPositiveResponseFR(String speechText) {
            String normalizedText = speechText.toLowerCase();
            return normalizedText.contains("oui") || normalizedText.contains("d'accord");
        }

        // Italian game room interaction
        // Italian game room interaction
        private void handleGameRoomInteractionIT(String speechText) {
            if (speechText.contains("cultura") || speechText.contains("di te")) {
                Log.i(TAG, "Command to talk about robot's culture recognized in Italian.");
                runOnUiThread(() -> {
                    sayText("Sono Buddy, un robot di un altro pianeta. Sul mio pianeta indossiamo abiti d'argento e amiamo giocare a giochi dove voliamo nel cielo. E la tua cultura?", "it-IT-IsabellaNeural");
                });
                awaitingCultureResponse = true;  // Awaiting response about culture
            } else if (awaitingCultureResponse) {
                Log.i(TAG, "Responding to culture query in Italian");
                awaitingCultureResponse = false;  // Reset the flag after receiving the response

                runOnUiThread(() -> {
                    sayText("Puoi dirmi che tipo di vestiti indossi? A quali giochi giochi?", "it-IT-IsabellaNeural");
                });
                awaitingClothesGamesResponse = true;  // Move to awaiting clothes and games response
            } else if (awaitingClothesGamesResponse && (speechText.contains("noi indossiamo") || speechText.contains("noi giochiamo"))) {
                Log.i(TAG, "User responded to clothes and games query.");
                awaitingClothesGamesResponse = false;  // Reset the flag after response

                runOnUiThread(() -> {
                    sayText("Grazie per aver condiviso! È sempre interessante sentire parlare delle diverse scelte di moda.", "it-IT-IsabellaNeural");
                });
            } else if (speechText.contains("ciao") || speechText.contains("salve")) {
                Log.i(TAG, "Greeting recognized in Italian.");
                runOnUiThread(() -> {
                    sayText("Ciao! Con cosa stai giocando? Puoi mostrarmi le tue bambole o giocattoli?", "it-IT-IsabellaNeural");
                });
                awaitingObjectResponse = true;  // Awaiting response about objects
            } else if (awaitingObjectResponse) {
                if (isNegativeResponseIT(speechText)) {
                    Log.i(TAG, "Negative response to object interaction in Italian.");
                    runOnUiThread(() -> sayText("Oh, va bene.", "it-IT-IsabellaNeural"));
                    awaitingObjectResponse = false;  // Reset after response
                } else if (isPositiveResponseIT(speechText)){
                    Log.i(TAG, "Positive or neutral response to object interaction in Italian.");
                    runOnUiThread(() -> {
                        sayText("Wow, sembra divertente! Puoi insegnarmi come si chiama? Come lo dici nella tua lingua?", "it-IT-IsabellaNeural");
                    });
                    awaitingObjectResponse = false;  // Reset after response
                    awaitingTeachingResponse = true;  // Awaiting teaching about the object
                }
            } else if (awaitingTeachingResponse && (speechText.contains("si chiama") || speechText.contains("chiama"))) {
                Log.i(TAG, "Responding to teaching interaction in Italian.");
                runOnUiThread(() -> sayText("Grazie per avermi insegnato!", "it-IT-IsabellaNeural"));
                awaitingTeachingResponse = false;
            } else if (speechText.contains("giocare a un gioco") || speechText.contains("indovina il nome")) {
                Log.i(TAG, "Invitation to play a game recognized in Italian.");
                runOnUiThread(() -> {
                    sayText("Giochiamo a un gioco divertente! Ti mostrerò delle immagini sul tablet, e puoi indovinare come si chiamano. Sei pronto?", "it-IT-IsabellaNeural");
                    new Handler(Looper.getMainLooper()).postDelayed(this::showTabletGame, 8000);  // Delay in milliseconds
                });
            }
        }

        private boolean isNegativeResponseIT(String speechText) {
            String normalizedText = speechText.toLowerCase();
            return normalizedText.contains("no") || normalizedText.contains("niente");
        }

        private boolean isPositiveResponseIT(String speechText) {
            String normalizedText = speechText.toLowerCase();
            return normalizedText.contains("sì") || normalizedText.contains("d'accordo");
        }


        private void sayText(String text, String voiceName) {
            handler.post(() -> {
                try {
                    SpeechConfig config = SpeechConfig.fromSubscription(SUBSCRIPTION_KEY, SERVICE_REGION);
                    config.setSpeechSynthesisVoiceName(voiceName);
                    SpeechSynthesizer synthesizer = new SpeechSynthesizer(config);
    
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            BuddySDK.UI.setLabialExpression(LabialExpression.SPEAK_HAPPY);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }).start();
    
                    SpeechSynthesisResult result = synthesizer.SpeakText(text);
                    BuddySDK.UI.setLabialExpression(LabialExpression.NO_EXPRESSION);
    
                    result.close();
                    synthesizer.close();
                } catch (Exception e) {
                    Log.i("info", "Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    
        private void playSound(int resourceId, Runnable onCompletion) {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, resourceId);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    handler.post(onCompletion);
                });
                mediaPlayer.start();
            } else {
                Log.e(TAG, "Failed to create MediaPlayer");
                handler.post(onCompletion);
            }
        }
    
    
    
        private void stopListening() {
            if (recognizer != null) {
                recognizer.stopContinuousRecognitionAsync();
                isListening = false;
                runOnUiThread(() -> {
                    sttState.setText("Stopped listening.");
                    sttState.setVisibility(View.GONE);
                    showMainButtons();
                });
            }
        }
    
        private void checkPermissions() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
    
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSIONS_REQUEST_CODE);
                } else {
                    initializeRecognizer();
                }
            } else {
                initializeRecognizer();
            }
        }
    
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == PERMISSIONS_REQUEST_CODE) {
                boolean allPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                if (allPermissionsGranted) {
                    Log.i(TAG, "onRequestPermissionsResult: All permissions granted. Initializing recognizer and loading videos.");
                    initializeRecognizer();
                    loadVideoFiles();
                } else {
                    Log.i(TAG, "onRequestPermissionsResult: Permissions not granted.");
                    Toast.makeText(this, "Permissions are required for this app to function", Toast.LENGTH_SHORT).show();
                }
            }
        }
        private static final int[] imageResources = new int[] {
                R.drawable.fr1, // Pisa Tower
                R.drawable.it1, // Eiffel Tower
                R.drawable.usa1 // Statue of Liberty
        };

        private Map<Integer, String> imageToNameMap = new HashMap<>();
        {
            imageToNameMap.put(R.drawable.fr1, "Eiffel Tower");
            imageToNameMap.put(R.drawable.it1, "Pisa Tower");
            imageToNameMap.put(R.drawable.usa1, "Statue of Liberty");
        }

        private int currentImageId; // Store the current image ID globally

        private void showTabletGame() {
            Random rand = new Random();
            currentImageId = imageResources[rand.nextInt(imageResources.length)];
            runOnUiThread(() -> {
                Drawable gameImage = ContextCompat.getDrawable(this, currentImageId);
                if (gameImage != null) {
                    imageView.setImageDrawable(gameImage);
                    imageView.setVisibility(View.VISIBLE);
                    // Simulate waiting for a response with a delay
                    new Handler(Looper.getMainLooper()).postDelayed(this::simulateUserGuess, 8000); // Delay set to 8 seconds
                } else {
                    Log.e(TAG, "Image resource not found for ID: " + currentImageId);
                    imageView.setVisibility(View.GONE);
                }
            });
        }

        private void simulateUserGuess() {
            // This is a mock function to simulate a user guessing the correct answer
            // In real scenario, this should be replaced by actual speech recognition handling
            String guessedName = imageToNameMap.get(currentImageId); // Assume the guess is always correct for simulation
            provideFeedback(guessedName, true); // Always true for simulation
        }

        private void provideFeedback(String guessedName, boolean isCorrect) {
            String correctAnswer = imageToNameMap.get(currentImageId);
            String response = "Interesting guess. It is actually a picture of " + correctAnswer + ". ";
            response += isCorrect ? "" : "";
            sayText(response, currentLanguage + "-JennyNeural");
            handler.postDelayed(() -> {
                runOnUiThread(() -> imageView.setVisibility(View.GONE));
            }, 8000);
        }

    
        private void showMainButtons() {
            mainButtonsContainer.setVisibility(View.VISIBLE);
            listViewFiles.setVisibility(View.GONE);
            recognizedText.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
            buttonBack.setVisibility(View.GONE);
            sttState.setVisibility(View.GONE);
        }
    
        private void showBackButton() {
            buttonBack.setVisibility(View.VISIBLE);
        }
    }
