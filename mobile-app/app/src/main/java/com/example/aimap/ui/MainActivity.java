package com.example.aimap.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.aimap.R;
import com.example.aimap.data.AppDatabase;
import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.Session;
import com.example.aimap.data.SessionManager;
import com.example.aimap.data.SettingsManager;
import com.example.aimap.data.SystemPrompts;
import com.example.aimap.data.User;
import com.example.aimap.data.UserSession;
import com.example.aimap.network.ApiManager;
import com.example.aimap.ui.adapter.ChatAdapter;
import com.example.aimap.ui.adapter.SessionAdapter;
import com.example.aimap.ui.adapter.SuggestionAdapter;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnPlacesButtonClickListener {
    private static final String TAG = "MainActivity";
    private static final int MAX_GUEST_QUESTIONS = 5;

    private EditText editTextMessage;
    private RecyclerView recyclerViewChat;
    private RecyclerView recyclerViewDrawerSessions;
    private RecyclerView recyclerViewSuggestions;
    private ChatAdapter chatAdapter;
    private SuggestionAdapter suggestionAdapter;
    private SessionAdapter sessionAdapter;
    private ArrayList<ChatMessage> chatList;
    private String currentSessionId;
    private ApiManager apiManager;

    // User session
    private SessionManager sessionManager;
    private UserSession currentUser;

    // Settings
    private SettingsManager settingsManager;

    // Google Sign-In
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private android.location.Location lastKnownLocation;

    // UI components
    private DrawerLayout drawerLayout;
    private MaterialButton buttonNewSession;
    private TextView textViewEmptyChat;
    private ImageButton buttonUser;

    // Background
    private ExecutorService executor;
    private Handler mainHandler;
    private long lastUpdateUiTime = 0;
    private boolean isGenerating = false;

    // Database
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Khởi tạo SettingsManager trước setContentView
        settingsManager = SettingsManager.getInstance(this);

        // Áp dụng theme và ngôn ngữ
        applyThemeOnStartup();
        applyLanguageOnStartup();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        database = AppDatabase.getDatabase(this);
        apiManager = new ApiManager();

        // Khởi tạo SessionManager và lấy user hiện tại (Guest hoặc đã login)
        sessionManager = SessionManager.getInstance(this);
        currentUser = sessionManager.getCurrentUser();

        drawerLayout = findViewById(R.id.drawerLayout);
        editTextMessage = findViewById(R.id.editTextMessage);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        recyclerViewDrawerSessions = findViewById(R.id.recyclerViewDrawerSessions);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        textViewEmptyChat = findViewById(R.id.textViewEmptyChat);
        buttonNewSession = findViewById(R.id.buttonNewSession);
        ImageButton buttonMenu = findViewById(R.id.buttonMenu);
        buttonUser = findViewById(R.id.buttonUser);

        // Setup Google Sign-In
        setupGoogleSignIn();

        // Cập nhật UI avatar/login button
        updateUserButton();

        // Mo drawer
        buttonMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Click vao avatar/login button
        buttonUser.setOnClickListener(v -> {
            if (currentUser.isGuest) {
                startGoogleSignIn();
            } else {
                // Đã login, show menu dropdown
                showUserMenu(v);
            }
        });

        // Chat list
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, chatList, this);
        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(chatLayoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        // Suggestions
        suggestionAdapter = new SuggestionAdapter(suggestion -> {
            if (isGenerating) return;
            editTextMessage.setText(suggestion);
            buttonSend.performClick();
        });
        recyclerViewSuggestions.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewSuggestions.setAdapter(suggestionAdapter);

        // Sessions adapter
        sessionAdapter = new SessionAdapter(new SessionAdapter.OnSessionClickListener() {
            @Override
            public void onSessionClick(Session session) {
                loadSession(session.session_id);
                drawerLayout.closeDrawers();
            }

            @Override
            public void onSessionMenuClick(Session session) {
                showSessionOptionsDialog(session);
            }
        });
        recyclerViewDrawerSessions.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewDrawerSessions.setAdapter(sessionAdapter);

        buttonNewSession.setOnClickListener(v -> {
            createNewSession();
            drawerLayout.closeDrawers();
        });

        // Location permission
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        fetchLocationForContext();
                    } else {
                        Toast.makeText(this,
                                getString(R.string.location_denied_message),
                                Toast.LENGTH_LONG).show();
                    }
                });
        requestLocationPermission();

        // Load sessions theo user hien tai
        loadUserSessions();

        // Gui tin nhan
        buttonSend.setOnClickListener(v -> {
            if (isGenerating) {
                apiManager.cancelCurrentRequest();
                isGenerating = false;
                toggleSendingState(false);
                return;
            }

            String msg = editTextMessage.getText().toString().trim();
            if (msg.isEmpty()) return;

            // Check gioi han Guest
            if (currentUser.isGuest && currentUser.questionCount >= MAX_GUEST_QUESTIONS) {
                showGuestLimitDialog();
                return;
            }

            List<String> locationKeywords = Arrays.asList("gần đây", "ở đây", "xung quanh", "quanh đây");
            boolean requiresLocation = locationKeywords.stream().anyMatch(msg::contains);

            if (requiresLocation &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.location_permission_message), Toast.LENGTH_SHORT).show();
                requestLocationPermission();
                return;
            }

            // Tang so cau hoi cua Guest
            if (currentUser.isGuest) {
                sessionManager.incrementQuestionCount();
                currentUser = sessionManager.getCurrentUser(); // Refresh
            }

            editTextMessage.setText("");
            isGenerating = true;
            toggleSendingState(true);

            ChatMessage userMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    currentSessionId,
                    msg,
                    null,
                    ChatMessage.TYPE_USER,
                    System.currentTimeMillis()
            );
            addMessageToUI(userMessage);

            executor.execute(() -> {
                database.chatMessageDao().insertMessage(userMessage);
                syncMessageToFirestore(currentSessionId, userMessage);
            });

            // Cap nhat session title
            executor.execute(() -> {
                Session s = database.sessionDao().getSessionById(currentSessionId);
                if (s != null) {
                    if (s.title == null || s.title.equals("Cuộc trò chuyện mới")) {
                        String autoTitle = msg.length() > 40 ? msg.substring(0, 40) + "..." : msg;
                        s.setTitle(autoTitle);
                    }
                    s.setPreview_message(msg);
                    s.setLast_updated(System.currentTimeMillis());
                    database.sessionDao().updateSession(s);
                    syncSessionToFirestore(s);

                    List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
                    mainHandler.post(() -> sessionAdapter.setSessions(all));
                }
            });

            String dynamicSystemPrompt = SystemPrompts.DEFAULT_MAP_PROMPT;
            if (lastKnownLocation != null) {
                dynamicSystemPrompt += String.format(
                        Locale.US,
                        "\n# BỐI CẢNH VỊ TRÍ\nVị trí hiện tại của người dùng là (latitude: %f, longitude: %f). Hãy sử dụng thông tin này nếu câu hỏi của họ có liên quan đến 'ở đây', 'gần đây', 'xung quanh đây'.",
                        lastKnownLocation.getLatitude(),
                        lastKnownLocation.getLongitude()
                );
            }

            ChatMessage aiMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    currentSessionId,
                    "...",
                    null,
                    ChatMessage.TYPE_AI,
                    System.currentTimeMillis()
            );
            addMessageToUI(aiMessage);

            final String finalSystemPrompt = dynamicSystemPrompt;
            
            // Lay lich su chat de gui kem
            List<ChatMessage> historyToSend = new ArrayList<>();
            if (chatList.size() > 2) {
                int historyEndIndex = chatList.size() - 2;
                int historyStartIndex = Math.max(0, historyEndIndex - 20); 
                
                for (int i = historyStartIndex; i < historyEndIndex; i++) {
                    historyToSend.add(chatList.get(i));
                }
            }

            executor.execute(() -> {
                boolean thinkingEnabled = settingsManager.isThinkingEnabled();
                apiManager.sendMessage(currentSessionId, msg, finalSystemPrompt, historyToSend, new ApiManager.StreamCallback() {
                    private final StringBuilder streamingResponse = new StringBuilder();
                    private boolean firstChunk = true;

                    @Override
                    public void onPartialResult(String partialResult) {
                        if (!isGenerating) return;

                        if (firstChunk) {
                            streamingResponse.setLength(0);
                            firstChunk = false;
                        }
                        streamingResponse.append(partialResult);

                        String currentFullText = streamingResponse.toString();
                        String textToDisplay;

                        if (currentFullText.contains("|||")) {
                            int splitIndex = currentFullText.indexOf("|||");
                            textToDisplay = currentFullText.substring(0, splitIndex).trim();
                        } else {
                            textToDisplay = currentFullText;
                        }

                        final String finalText = textToDisplay;

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateUiTime > 100) {
                            lastUpdateUiTime = currentTime;
                            mainHandler.post(() -> {
                                if (!isGenerating) return;
                                aiMessage.content = finalText;
                                chatAdapter.updateStreamingMessage(chatList.size() - 1);
                            });
                        }
                    }


                    @Override
                    public void onComplete(String fullResult, String error) {
                        lastUpdateUiTime = 0;

                        if (!isGenerating) return;

                        mainHandler.post(() -> {
                            isGenerating = false;
                            toggleSendingState(false);
                        });

                        if (error != null) {
                            Log.e(TAG, "API Error: " + error);
                            mainHandler.post(() -> {
                                aiMessage.content = getString(R.string.error_message);
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        } else {
                            String textPart = fullResult;
                            String jsonPart = null;

                            if (fullResult.contains("|||")) {
                                String[] parts = fullResult.split("\\|\\|\\|");
                                if (parts.length > 0) textPart = parts[0].trim();
                                if (parts.length > 1) jsonPart = parts[1].trim();
                            }

                            final String finalText = textPart;
                            final String finalJson = jsonPart;

                            mainHandler.post(() -> {
                                aiMessage.content = finalText;
                                aiMessage.metadata = finalJson;
                                chatAdapter.notifyItemChanged(chatList.size() - 1, "UPDATE_BUTTON");
                                int lastIndex = chatAdapter.getItemCount() - 1;
                                if (lastIndex >= 0) {
                                    recyclerViewChat.scrollToPosition(lastIndex);
                                }
                            });

                            executor.execute(() -> {
                                database.chatMessageDao().insertMessage(aiMessage);
                                syncMessageToFirestore(currentSessionId, aiMessage);

                                Session s = database.sessionDao().getSessionById(currentSessionId);
                                if (s != null) {
                                    s.setPreview_message(finalText);
                                    s.setLast_updated(System.currentTimeMillis());
                                    database.sessionDao().updateSession(s);
                                    syncSessionToFirestore(s);
                                    List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
                                    mainHandler.post(() -> sessionAdapter.setSessions(all));
                                }
                            });
                        }
                    }
                });
            });
        });
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    }
                });
    }

    private void startGoogleSignIn() {
        if (googleSignInClient != null) {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account == null) {
                Toast.makeText(this, getString(R.string.google_account_error), Toast.LENGTH_SHORT).show();
                return;
            }

            // Sign in voi Firebase
            String idToken = account.getIdToken();
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

            mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(this, getString(R.string.firebase_login_error), Toast.LENGTH_SHORT).show();
                    return;
                }

                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser == null) return;

                String googleUserId = firebaseUser.getUid();
                String displayName = firebaseUser.getDisplayName();
                String email = firebaseUser.getEmail();
                String avatarUrl = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null;

                // Luu vao Firestore va Room
                saveUserToFirestore(googleUserId, displayName, email, avatarUrl);

                executor.execute(() -> {
                User existingUser = database.userDao().getUserByGoogleId(googleUserId);

                if (existingUser == null) {
                    // Tao user moi
                    String userId = "google-" + googleUserId;
                    long now = System.currentTimeMillis();
                    User newUser = new User(
                            userId,
                            googleUserId,
                            displayName,
                            email,
                            avatarUrl,
                            false,
                            0,
                            now,
                            now
                    );
                    database.userDao().insertUser(newUser);

                    // Merge session tu Guest sang User moi
                    String oldGuestUserId = currentUser.userId;
                    database.sessionDao().updateSessionsUserId(oldGuestUserId, userId);

                    existingUser = newUser;
                } else {
                    // Cap nhat thong tin
                    existingUser.setDisplayName(displayName);
                    existingUser.setEmail(email);
                    existingUser.setAvatarUrl(avatarUrl);
                    existingUser.setUpdatedAt(System.currentTimeMillis());
                    database.userDao().updateUser(existingUser);
                }

                // Cap nhat SessionManager
                User finalUser = existingUser;
                mainHandler.post(() -> {
                    currentUser = new UserSession(
                            finalUser.userId,
                            finalUser.displayName,
                            finalUser.email,
                            finalUser.avatarUrl,
                            false,
                            UserSession.PROVIDER_GOOGLE,
                            finalUser.questionCount
                    );
                    sessionManager.setCurrentUser(currentUser);

                    Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show();
                    updateUserButton();

                    // Load lai sessions
                    loadUserSessions();

                    // Sync sessions voi Firestore
                    syncLocalSessionsToFirestore();

                    // Attach realtime listeners
                    attachFirebaseListenersIfNeeded();
                });
            });
            });

        } catch (ApiException e) {
                Toast.makeText(this, getString(R.string.google_login_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyThemeOnStartup() {
        int nightMode = settingsManager.isDarkThemeEnabled() ?
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES :
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private void applyLanguageOnStartup() {
        String language = settingsManager.getLanguage();
        java.util.Locale locale = new java.util.Locale(language);
        java.util.Locale.setDefault(locale);

        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void showUserMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.END, 0, R.style.PopupMenuStyle);
        popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_settings) {
                showSettingsDialog();
                return true;
            } else if (item.getItemId() == R.id.menu_logout) {
                handleLogout();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        androidx.appcompat.widget.SwitchCompat switchTheme = dialogView.findViewById(R.id.switchTheme);
        androidx.appcompat.widget.SwitchCompat switchLanguage = dialogView.findViewById(R.id.switchLanguage);
        androidx.appcompat.widget.SwitchCompat switchThinking = dialogView.findViewById(R.id.switchThinking);

        // Set giá trị hiện tại
        switchTheme.setChecked(settingsManager.isDarkThemeEnabled());
        switchLanguage.setChecked(SettingsManager.LANG_EN.equals(settingsManager.getLanguage()));
        switchThinking.setChecked(settingsManager.isThinkingEnabled());

        Log.d(TAG, "Initial theme: " + settingsManager.isDarkThemeEnabled());
        Log.d(TAG, "Initial language: " + settingsManager.getLanguage());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.settings_save), (d, which) -> {
                    boolean themeChanged = settingsManager.isDarkThemeEnabled() != switchTheme.isChecked();
                    boolean languageChanged = !settingsManager.getLanguage().equals(
                            switchLanguage.isChecked() ? SettingsManager.LANG_EN : SettingsManager.LANG_VI);

                    Log.d(TAG, "Theme changed: " + themeChanged + ", current: " + settingsManager.isDarkThemeEnabled() + ", new: " + switchTheme.isChecked());
                    Log.d(TAG, "Language changed: " + languageChanged + ", current: " + settingsManager.getLanguage() + ", new: " + (switchLanguage.isChecked() ? SettingsManager.LANG_EN : SettingsManager.LANG_VI));

                    // Lưu cấu hình
                    settingsManager.setDarkThemeEnabled(switchTheme.isChecked());
                    settingsManager.setLanguage(switchLanguage.isChecked() ? SettingsManager.LANG_EN : SettingsManager.LANG_VI);
                    settingsManager.setThinkingEnabled(switchThinking.isChecked());

                    // Áp dụng theme nếu thay đổi
                    if (themeChanged) {
                        applyTheme();
                    }

                    // Áp dụng ngôn ngữ nếu thay đổi
                    if (languageChanged) {
                        applyLanguage();
                    }

                    Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.settings_cancel), null)
                .create();

        dialog.show();
    }

    private void handleLogout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_title))
                .setMessage(getString(R.string.logout_message))
                .setPositiveButton(getString(R.string.logout_confirm), (d, which) -> {
                    // Sign out Google
                    if (googleSignInClient != null) {
                        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                            // Reset ve Guest
                            currentUser = sessionManager.createGuestSession();
                            updateUserButton();
                            loadUserSessions();
                            Toast.makeText(this, getString(R.string.logout_success), Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .setNegativeButton(getString(R.string.delete_cancel), null)
                .show();
    }

    private void applyTheme() {
        // Áp dụng theme mới
        int nightMode = settingsManager.isDarkThemeEnabled() ?
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES :
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode);
        Log.d(TAG, "Applied theme: " + (nightMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES ? "dark" : "light"));

        // Recreate activity để áp dụng theme
        recreate();
    }

    private void applyLanguage() {
        // Áp dụng ngôn ngữ mới
        String language = settingsManager.getLanguage();
        java.util.Locale locale = new java.util.Locale(language);
        java.util.Locale.setDefault(locale);

        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        Log.d(TAG, "Applied language: " + language);

        // Recreate activity để áp dụng ngôn ngữ
        recreate();
    }

    private void updateUserButton() {
        if (currentUser.isGuest) {
            // Hien thi icon mac dinh cho Guest
            buttonUser.setImageResource(R.drawable.ic_avatar);
        } else {
            // Load avatar tu Google
            if (currentUser.avatarUrl != null && !currentUser.avatarUrl.isEmpty()) {
                Glide.with(this)
                        .load(currentUser.avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_avatar)
                        .into(buttonUser);
            } else {
                buttonUser.setImageResource(R.drawable.ic_avatar);
            }
        }
    }

    private void showGuestLimitDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_guest_limit, null);
        MaterialButton buttonLoginNow = dialogView.findViewById(R.id.buttonLoginNow);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Lam mo background va center dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.7f); // Lam mo 70%
        }

        buttonLoginNow.setOnClickListener(v -> {
            dialog.dismiss();
            startGoogleSignIn();
        });

        dialog.show();
    }

    private void loadUserSessions() {
        executor.execute(() -> {
            String userId = currentUser.userId;

            // Kiem tra xem user co session nao khong
            int count = database.sessionDao().getSessionCountByUserId(userId);
            if (count == 0) {
                // Tao session moi
                String newId = UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                Session session = new Session(
                        newId,
                        userId,
                        "Cuộc trò chuyện mới",
                        "",
                        now,
                        false
                );
                database.sessionDao().insertSession(session);
                syncSessionToFirestore(session);
                currentSessionId = newId;
        } else {
                // Lay session moi nhat
                List<Session> sessions = database.sessionDao().getSessionsByUserId(userId);
                if (!sessions.isEmpty()) {
                    currentSessionId = sessions.get(0).session_id;
                }
            }

            List<Session> allSessions = database.sessionDao().getSessionsByUserId(userId);
            List<ChatMessage> messages = database.chatMessageDao().getMessageByeSession(currentSessionId);

            mainHandler.post(() -> {
                sessionAdapter.setSessions(allSessions);
                chatList.clear();
                chatList.addAll(messages);
                chatAdapter.notifyDataSetChanged();
                if (chatList.isEmpty()) {
                    addWelcomeMessage();
                }
                generateRandomSuggestions();
                updateEmptyStateUi();
            });
        });
    }

    private void createNewSession() {
        currentSessionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Session session = new Session(
                currentSessionId,
                currentUser.userId,
                "Cuộc trò chuyện mới",
                "",
                now,
                false
        );

        executor.execute(() -> {
            database.sessionDao().insertSession(session);
            syncSessionToFirestore(session);
            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
            mainHandler.post(() -> {
                chatList.clear();
                chatAdapter.notifyDataSetChanged();
                sessionAdapter.setSessions(all);
                addWelcomeMessage();
                updateEmptyStateUi();
            });
        });
    }

    private void loadSession(String sessionId) {
        currentSessionId = sessionId;
        executor.execute(() -> {
            List<ChatMessage> messages = database.chatMessageDao().getMessageByeSession(sessionId);
            mainHandler.post(() -> {
                chatList.clear();
                chatList.addAll(messages);
                chatAdapter.notifyDataSetChanged();

                int lastIndex = chatAdapter.getItemCount() - 1;
                if (lastIndex >= 0) {
                    recyclerViewChat.scrollToPosition(lastIndex);
                }

                updateEmptyStateUi();
            });
        });
    }

    private void showSessionOptionsDialog(Session session) {
        String[] options = {"Đổi tên", "Ghim", "Xóa"};
        if (session.isPinned) {
            options[1] = "Bỏ ghim";
        }

        new AlertDialog.Builder(this)
                .setTitle(session.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(session);
                    } else if (which == 1) {
                        togglePinSession(session);
                    } else if (which == 2) {
                        confirmDeleteSession(session);
                    }
                })
                .show();
    }

    private void showRenameDialog(Session session) {
        final EditText input = new EditText(this);
        input.setText(session.title);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rename_title))
                .setView(input)
                .setPositiveButton(getString(R.string.rename_save), (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        executor.execute(() -> {
                            session.setTitle(newTitle);
                            session.setLast_updated(System.currentTimeMillis());
                            database.sessionDao().updateSession(session);
                            syncSessionToFirestore(session);
                            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
                            mainHandler.post(() -> sessionAdapter.setSessions(all));
                        });
                    }
                })
                .setNegativeButton(getString(R.string.rename_cancel), null)
                .show();
    }

    private void togglePinSession(Session session) {
        executor.execute(() -> {
            session.setPinned(!session.isPinned());
            session.setLast_updated(System.currentTimeMillis());
            database.sessionDao().updateSession(session);
            syncSessionToFirestore(session);
            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
            mainHandler.post(() -> sessionAdapter.setSessions(all));
        });
    }

    private void confirmDeleteSession(Session session) {
        executor.execute(() -> {
            int count = database.sessionDao().getSessionCountByUserId(currentUser.userId);

            if (count <= 1) {
                    mainHandler.post(() ->
                            Toast.makeText(this,
                                getString(R.string.min_sessions_message),
                                    Toast.LENGTH_LONG).show()
                    );
                    return;
            }

            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.delete_title))
                        .setMessage(getString(R.string.delete_message))
                        .setPositiveButton(getString(R.string.delete_confirm), (dialog, which) -> {
                            executor.execute(() -> {
                                database.chatMessageDao().deleteMessagesBySession(session.session_id);
                                database.sessionDao().deleteSessionById(session.session_id);

                                List<Session> remaining = database.sessionDao().getSessionsByUserId(currentUser.userId);
                                if (remaining.isEmpty()) {
                                    String newId = UUID.randomUUID().toString();
                                    long now = System.currentTimeMillis();
                                    Session newSession = new Session(
                                            newId,
                                            currentUser.userId,
                                            "Cuộc trò chuyện mới",
                                            "",
                                            now,
                                            false
                                    );
                                    database.sessionDao().insertSession(newSession);
                                    syncSessionToFirestore(newSession);
                                    remaining = database.sessionDao().getSessionsByUserId(currentUser.userId);
                                    currentSessionId = newId;
                                } else {
                                    currentSessionId = remaining.get(0).session_id;
                                }

                                List<ChatMessage> msgs = database.chatMessageDao().getMessageByeSession(currentSessionId);

                                List<Session> finalRemaining = remaining;
                                mainHandler.post(() -> {
                                    sessionAdapter.setSessions(finalRemaining);
                                    chatList.clear();
                                    chatList.addAll(msgs);
                                    chatAdapter.notifyDataSetChanged();
                                    updateEmptyStateUi();
                                });
                        });
                })
                .setNegativeButton(getString(R.string.logout_cancel), null)
                        .show();
            });
        });
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocationForContext();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationForContext() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                    }
                });
    }

    private void generateRandomSuggestions() {
        mainHandler.post(() -> {
            try {
                String[] allSuggestions = getResources().getStringArray(R.array.suggestion_questions);
                List<String> suggestionList = new ArrayList<>(Arrays.asList(allSuggestions));
                Collections.shuffle(suggestionList);
                int suggestionCount = Math.min(3, suggestionList.size());
                List<String> randomSuggestions = suggestionList.subList(0, suggestionCount);
                suggestionAdapter.updateSuggestions(randomSuggestions);
            } catch (Exception e) {
                Log.e(TAG, "Error generating suggestions", e);
            }
        });
    }

    private void addMessageToUI(ChatMessage chatMessage) {
        chatList.add(chatMessage);
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        int lastIndex = chatAdapter.getItemCount() - 1;
        if (lastIndex >= 0) {
            recyclerViewChat.scrollToPosition(lastIndex);
        }
        updateEmptyStateUi();
    }

    private void updateEmptyStateUi() {
        if (chatList.isEmpty()) {
            textViewEmptyChat.setVisibility(View.VISIBLE);
            recyclerViewChat.setVisibility(View.GONE);
        } else {
            textViewEmptyChat.setVisibility(View.GONE);
            recyclerViewChat.setVisibility(View.VISIBLE);
        }
    }

    private void addWelcomeMessage() {
        ChatMessage welcomeMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                currentSessionId,
                getString(R.string.welcome_message),
                null,
                ChatMessage.TYPE_AI,
                System.currentTimeMillis()
        );
        addMessageToUI(welcomeMessage);

        executor.execute(() -> {
            database.chatMessageDao().insertMessage(welcomeMessage);
            syncMessageToFirestore(currentSessionId, welcomeMessage);
        });

        generateRandomSuggestions();
    }

    private void toggleSendingState(boolean generating) {
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        if (generating) {
            buttonSend.setImageResource(android.R.drawable.ic_media_pause);
            editTextMessage.setEnabled(false);
            recyclerViewSuggestions.setEnabled(false);
            recyclerViewSuggestions.setAlpha(0.5f);
        } else {
            buttonSend.setImageResource(R.drawable.ic_send);
            editTextMessage.setEnabled(true);
            recyclerViewSuggestions.setEnabled(true);
            recyclerViewSuggestions.setAlpha(1.0f);
        }
    }

    @Override
    public void onPlacesButtonClick(String jsonMetadata) {
        PlacesBottomSheetFragment bottomSheet =
                PlacesBottomSheetFragment.newInstance(jsonMetadata, lastKnownLocation);
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    private void saveUserToFirestore(String uid, String displayName, String email, String photoUrl) {
        java.util.Map<String, Object> user = new java.util.HashMap<>();
        user.put("uid", uid);
        user.put("displayName", displayName);
        user.put("email", email);
        user.put("photoUrl", photoUrl);
        user.put("updatedAt", System.currentTimeMillis());

        firestore.collection("users").document(uid)
                .set(user, SetOptions.merge());
    }

    private void syncLocalSessionsToFirestore() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();

        executor.execute(() -> {
            List<Session> localSessions = database.sessionDao().getSessionsByUserId(currentUser.userId);

            for (Session session : localSessions) {
                java.util.Map<String, Object> sessionData = new java.util.HashMap<>();
                sessionData.put("sessionId", session.session_id);
                sessionData.put("userId", uid);
                sessionData.put("title", session.title);
                sessionData.put("previewMessage", session.preview_message);
                sessionData.put("lastUpdated", session.last_updated);
                sessionData.put("isPinned", session.isPinned);

                firestore.collection("sessions").document(session.session_id)
                        .set(sessionData, SetOptions.merge());

                List<ChatMessage> messages = database.chatMessageDao().getMessageByeSession(session.session_id);
                for (ChatMessage msg : messages) {
                    syncMessageToFirestore(session.session_id, msg);
                }
            }
        });
    }

    private void syncSessionToFirestore(Session session) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        java.util.Map<String, Object> sessionData = new java.util.HashMap<>();
        sessionData.put("userId", firebaseUser.getUid());
        sessionData.put("title", session.title);
        sessionData.put("previewMessage", session.preview_message);
        sessionData.put("updatedAt", session.last_updated);
        sessionData.put("isPinned", session.isPinned);

        firestore.collection("users").document(firebaseUser.getUid())
                .collection("sessions").document(session.session_id)
                .set(sessionData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Synced session to Firestore: " + session.session_id))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync session", e));
    }

    private void syncMessageToFirestore(String sessionId, ChatMessage message) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        java.util.Map<String, Object> messageData = new java.util.HashMap<>();
        messageData.put("role", message.role);
        messageData.put("content", message.content);
        messageData.put("metadata", message.metadata);
        messageData.put("createdAt", message.createdAt);
        messageData.put("updatedAt", message.updatedAt);
        messageData.put("status", message.status);
        messageData.put("deviceId", message.deviceId);

        firestore.collection("users").document(firebaseUser.getUid())
                .collection("sessions").document(sessionId)
                .collection("messages").document(message.message_id)
                .set(messageData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Synced message to Firestore: " + message.message_id))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to sync message", e));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Attach Firebase listeners when activity becomes visible
        // // quản lý lifecycle
        attachFirebaseListenersIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure listeners are attached when activity resumes
        attachFirebaseListenersIfNeeded();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach Firebase listeners when activity is not visible
        // // detach listeners ở onStop/onDestroy để tránh memory leak
    }

    /**
     * Attach Firebase listeners nếu user đã login và chưa attach
     */
    private void attachFirebaseListenersIfNeeded() {
        if (currentUser != null && !currentUser.isGuest && mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            Log.d(TAG, "Attaching Firebase listeners for user: " + uid);
            // syncHelper.attachListeners(uid); // Disabled for now
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
