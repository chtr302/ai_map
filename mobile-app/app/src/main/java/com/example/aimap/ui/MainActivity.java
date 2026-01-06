package com.example.aimap.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

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

    // SỬ DỤNG MODEL 8B THINKING
    private static final String MODEL_NAME = "qwen/qwen3-8b"; 

    private EditText editTextMessage;
    private RecyclerView recyclerViewChat;
    private RecyclerView recyclerViewDrawerSessions;
    private RecyclerView recyclerViewSuggestions;
    private View layoutEmptyState;

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
        buttonNewSession = findViewById(R.id.buttonNewSession);
        ImageButton buttonMenu = findViewById(R.id.buttonMenu);
        buttonUser = findViewById(R.id.buttonUser);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        
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
                showSessionOptionsDialogLocalized(session);
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

            if (currentUser.isGuest && currentUser.questionCount >= MAX_GUEST_QUESTIONS) {
                showGuestLimitDialog();
                return;
            }

            List<String> locationKeywords = Arrays.asList("gần đây", "ở đây", "xung quanh", "quanh đây");
            boolean requiresLocation = locationKeywords.stream().anyMatch(msg::contains);

            if (requiresLocation && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.location_permission_message), Toast.LENGTH_SHORT).show();
                requestLocationPermission();
                return;
            }

            if (currentUser.isGuest) {
                sessionManager.incrementQuestionCount();
                currentUser = sessionManager.getCurrentUser();
            }

            editTextMessage.setText("");
            isGenerating = true;
            toggleSendingState(true);
            updateEmptyStateUi(false);

            ChatMessage userMessage = new ChatMessage(UUID.randomUUID().toString(), currentSessionId, msg, null, ChatMessage.TYPE_USER, System.currentTimeMillis());
            addMessageToUI(userMessage);

            executor.execute(() -> {
                database.chatMessageDao().insertMessage(userMessage);
                syncMessageToFirestore(currentSessionId, userMessage);
            });

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
                dynamicSystemPrompt += String.format(Locale.US, "\n# VỊ TRÍ HIỆN TẠI: (latitude: %f, longitude: %f).", lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            }
            
            final String promptToSend = dynamicSystemPrompt;
            ChatMessage aiMessage = new ChatMessage(UUID.randomUUID().toString(), currentSessionId, "<think>", null, ChatMessage.TYPE_AI, System.currentTimeMillis());
            addMessageToUI(aiMessage);

            List<ChatMessage> historyToSend = new ArrayList<>();
            if (chatList.size() > 2) {
                int historyStartIndex = Math.max(0, chatList.size() - 8); 
                for (int i = historyStartIndex; i < chatList.size() - 2; i++) {
                    historyToSend.add(chatList.get(i));
                }
            }
            
            executor.execute(() -> {
                apiManager.sendMessage(MODEL_NAME, currentSessionId, msg, promptToSend, historyToSend, new ApiManager.StreamCallback() {
                    private final StringBuilder streamingResponse = new StringBuilder();
                    @Override
                    public void onPartialResult(String partialResult) {
                        if (!isGenerating) return;
                        streamingResponse.append(partialResult);
                        String currentFullText = streamingResponse.toString();
                        String textToDisplay = currentFullText.contains("|||") ? currentFullText.substring(0, currentFullText.indexOf("|||")).trim() : currentFullText;
                        
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpdateUiTime > 100) {
                            lastUpdateUiTime = currentTime;
                            mainHandler.post(() -> {
                                if (!isGenerating) return;
                                aiMessage.message = textToDisplay;
                                chatAdapter.updateStreamingMessage(chatList.size() - 1);
                            });
                        }
                    }

                    @Override
                    public void onComplete(String fullResult, String error) {
                        isGenerating = false;
                        mainHandler.post(() -> toggleSendingState(false));
                        if (error != null) {
                            mainHandler.post(() -> {
                                aiMessage.message = getString(R.string.error_message);
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        } else {
                            int splitIndex = fullResult != null ? fullResult.indexOf("|||") : -1;
                            String finalText = splitIndex != -1 ? fullResult.substring(0, splitIndex).trim() : (fullResult != null ? fullResult : "");
                            String finalJson = splitIndex != -1 ? fullResult.substring(splitIndex + 3).trim() : null;

                            mainHandler.post(() -> {
                                aiMessage.message = finalText;
                                aiMessage.metadata = finalJson;
                                chatAdapter.notifyItemChanged(chatList.size() - 1, "UPDATE_BUTTON");
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
                .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                handleGoogleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
            }
        });
    }

    private void startGoogleSignIn() {
        if (googleSignInClient != null) googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account == null) return;
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) return;
                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                if (firebaseUser == null) return;
                String uid = firebaseUser.getUid();
                saveUserToFirestore(uid, firebaseUser.getDisplayName(), firebaseUser.getEmail(), firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null);
                executor.execute(() -> {
                    User existingUser = database.userDao().getUserByGoogleId(uid);
                    if (existingUser == null) {
                        User newUser = new User("google-" + uid, uid, firebaseUser.getDisplayName(), firebaseUser.getEmail(), firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null, false, 0, System.currentTimeMillis(), System.currentTimeMillis());
                        database.userDao().insertUser(newUser);
                        database.sessionDao().updateSessionsUserId(currentUser.userId, newUser.userId);
                        existingUser = newUser;
                    }
                    User finalUser = existingUser;
                    mainHandler.post(() -> {
                        currentUser = new UserSession(finalUser.userId, finalUser.displayName, finalUser.email, finalUser.avatarUrl, false, UserSession.PROVIDER_GOOGLE, finalUser.questionCount);
                        sessionManager.setCurrentUser(currentUser);
                        updateUserButton();
                        loadUserSessions();
                        syncLocalSessionsToFirestore();
                        attachFirebaseListenersIfNeeded();
                    });
                });
            });
        } catch (ApiException e) { Log.e(TAG, "Login error", e); }
    }

    private void applyThemeOnStartup() {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settingsManager.isDarkThemeEnabled() ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void applyLanguageOnStartup() {
        Locale locale = new Locale(settingsManager.getLanguage());
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private void showUserMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.END, 0, R.style.PopupMenuStyle);
        popupMenu.getMenuInflater().inflate(R.menu.user_menu, popupMenu.getMenu());

        // Đổi màu chữ menu user
        int textColor = ContextCompat.getColor(this, R.color.userMenuText);
        for (int i = 0; i < popupMenu.getMenu().size(); i++) {
            android.view.MenuItem item = popupMenu.getMenu().getItem(i);
            CharSequence title = item.getTitle();
            if (title != null) {
                SpannableString span = new SpannableString(title);
                span.setSpan(new ForegroundColorSpan(textColor), 0, span.length(), 0);
                item.setTitle(span);
            }
        }

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_settings) showSettingsDialog();
            else if (item.getItemId() == R.id.menu_logout) handleLogout();
            return true;
        });
        popupMenu.show();
    }

    private void showSettingsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        androidx.appcompat.widget.SwitchCompat switchTheme = dialogView.findViewById(R.id.switchTheme);
        androidx.appcompat.widget.SwitchCompat switchLanguage = dialogView.findViewById(R.id.switchLanguage);
        switchTheme.setChecked(settingsManager.isDarkThemeEnabled());
        switchLanguage.setChecked(SettingsManager.LANG_EN.equals(settingsManager.getLanguage()));
        new AlertDialog.Builder(this).setTitle(getString(R.string.settings_title)).setView(dialogView).setPositiveButton(getString(R.string.settings_save), (d, which) -> {
            settingsManager.setDarkThemeEnabled(switchTheme.isChecked());
            settingsManager.setLanguage(switchLanguage.isChecked() ? SettingsManager.LANG_EN : SettingsManager.LANG_VI);
            recreate();
        }).setNegativeButton(getString(R.string.settings_cancel), null).show();
    }

    private void handleLogout() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.logout_title)).setMessage(getString(R.string.logout_message)).setPositiveButton(getString(R.string.logout_confirm), (d, which) -> {
            if (googleSignInClient != null) googleSignInClient.signOut().addOnCompleteListener(this, task -> {
                currentUser = sessionManager.createGuestSession();
                updateUserButton();
                loadUserSessions();
            });
        }).setNegativeButton(getString(R.string.delete_cancel), null).show();
    }

    private void updateUserButton() {
        if (currentUser.isGuest) buttonUser.setImageResource(R.drawable.ic_avatar);
        else Glide.with(this).load(currentUser.avatarUrl).circleCrop().placeholder(R.drawable.ic_avatar).into(buttonUser);
    }

    private void showGuestLimitDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_guest_limit, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(v).create();
        v.findViewById(R.id.buttonLoginNow).setOnClickListener(view -> { dialog.dismiss(); startGoogleSignIn(); });
        dialog.show();
    }

    private void loadUserSessions() {
        executor.execute(() -> {
            int count = database.sessionDao().getSessionCountByUserId(currentUser.userId);
            if (count == 0) {
                String newId = UUID.randomUUID().toString();
                Session s = new Session(newId, currentUser.userId, "Cuộc trò chuyện mới", "", System.currentTimeMillis(), false);
                database.sessionDao().insertSession(s);
                syncSessionToFirestore(s);
                currentSessionId = newId;
            } else {
                List<Session> sessions = database.sessionDao().getSessionsByUserId(currentUser.userId);
                currentSessionId = sessions.get(0).session_id;
            }
            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
            List<ChatMessage> msgs = database.chatMessageDao().getMessageByeSession(currentSessionId);
            mainHandler.post(() -> {
                sessionAdapter.setSessions(all);
                chatList.clear(); chatList.addAll(msgs);
                chatAdapter.notifyDataSetChanged();
                updateEmptyStateUi(chatList.isEmpty());
                generateRandomSuggestions();
            });
        });
    }

    private void createNewSession() {
        currentSessionId = UUID.randomUUID().toString();
        Session s = new Session(currentSessionId, currentUser.userId, "Cuộc trò chuyện mới", "", System.currentTimeMillis(), false);
        executor.execute(() -> {
            database.sessionDao().insertSession(s);
            syncSessionToFirestore(s);
            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
            mainHandler.post(() -> {
                chatList.clear(); chatAdapter.notifyDataSetChanged();
                sessionAdapter.setSessions(all);
                updateEmptyStateUi(true);
                generateRandomSuggestions();
            });
        });
    }

    private void loadSession(String id) {
        currentSessionId = id;
        executor.execute(() -> {
            List<ChatMessage> msgs = database.chatMessageDao().getMessageByeSession(id);
            mainHandler.post(() -> {
                chatList.clear(); chatList.addAll(msgs);
                chatAdapter.notifyDataSetChanged();
                if (!chatList.isEmpty()) recyclerViewChat.scrollToPosition(chatList.size()-1);
                updateEmptyStateUi(chatList.isEmpty());
            });
        });
    }

    // Menu chỉnh sửa session theo ngôn ngữ
    private void showSessionOptionsDialogLocalized(Session session) {
        String rename = getString(R.string.session_menu_rename);
        String pin = getString(session.isPinned ? R.string.session_menu_unpin : R.string.session_menu_pin);
        String deleteOption = getString(R.string.session_menu_delete);
        String[] options = {rename, pin, deleteOption};

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

    private void showSessionOptionsDialog(Session session) {
        String[] options = {"Đổi tên", "Ghim", "Xóa"};
        if (session.isPinned) options[1] = "Bỏ ghim";
        new AlertDialog.Builder(this).setTitle(session.title).setItems(options, (dialog, which) -> {
            if (which == 0) showRenameDialog(session);
            else if (which == 1) togglePinSession(session);
            else if (which == 2) confirmDeleteSession(session);
        }).show();
    }

    private void showRenameDialog(Session session) {
        final EditText input = new EditText(this);
        input.setText(session.title);
        new AlertDialog.Builder(this).setTitle(getString(R.string.rename_title)).setView(input)
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
            }).setNegativeButton(getString(R.string.rename_cancel), null).show();
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
                mainHandler.post(() -> Toast.makeText(this, getString(R.string.min_sessions_message), Toast.LENGTH_LONG).show());
                return;
            }
            mainHandler.post(() -> new AlertDialog.Builder(this).setTitle(getString(R.string.delete_title)).setMessage(getString(R.string.delete_message))
                .setPositiveButton(getString(R.string.delete_confirm), (dialog, which) -> {
                    executor.execute(() -> {
                        database.chatMessageDao().deleteMessagesBySession(session.session_id);
                        database.sessionDao().deleteSessionById(session.session_id);
                        List<Session> remaining = database.sessionDao().getSessionsByUserId(currentUser.userId);
                        if (remaining.isEmpty()) createNewSession();
                        else {
                            currentSessionId = remaining.get(0).session_id;
                            loadSession(currentSessionId);
                            mainHandler.post(() -> sessionAdapter.setSessions(remaining));
                        }
                    });
                }).setNegativeButton(getString(R.string.logout_cancel), null).show());
        });
    }

    private void updateEmptyStateUi(boolean isEmpty) {
        layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerViewChat.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        recyclerViewSuggestions.setVisibility(View.VISIBLE);
    }

    private void generateRandomSuggestions() {
        mainHandler.post(() -> {
            try {
                String[] all = getResources().getStringArray(R.array.suggestion_questions);
                List<String> list = new ArrayList<>(Arrays.asList(all));
                Collections.shuffle(list);
                suggestionAdapter.updateSuggestions(list.subList(0, Math.min(3, list.size())));
            } catch (Exception e) { Log.e(TAG, "Sug error", e); }
        });
    }

    private void addMessageToUI(ChatMessage m) {
        chatList.add(m);
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        recyclerViewChat.scrollToPosition(chatList.size() - 1);
        updateEmptyStateUi(false);
    }

    @Override public void onPlacesButtonClick(String json) {
        PlacesBottomSheetFragment.newInstance(json, lastKnownLocation).show(getSupportFragmentManager(), "places");
    }

    private void saveUserToFirestore(String uid, String name, String email, String url) {
        java.util.Map<String, Object> u = new java.util.HashMap<>();
        u.put("uid", uid); u.put("displayName", name); u.put("email", email); u.put("photoUrl", url); u.put("updatedAt", System.currentTimeMillis());
        firestore.collection("users").document(uid).set(u, SetOptions.merge());
    }

    private void syncSessionToFirestore(Session s) {
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("userId", u.getUid()); data.put("title", s.title); data.put("previewMessage", s.preview_message); data.put("updatedAt", s.last_updated);
        firestore.collection("users").document(u.getUid()).collection("sessions").document(s.session_id).set(data, SetOptions.merge());
    }

    private void syncMessageToFirestore(String sId, ChatMessage m) {
        FirebaseUser u = mAuth.getCurrentUser();
        if (u == null) return;
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("role", (m.type == ChatMessage.TYPE_USER) ? "user" : "model"); data.put("content", m.message); data.put("metadata", m.metadata); data.put("createdAt", m.timestamp);
        firestore.collection("users").document(u.getUid()).collection("sessions").document(sId).collection("messages").document(m.message_id).set(data, SetOptions.merge());
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocationForContext();
        else requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationForContext() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener(this, l -> { if (l != null) lastKnownLocation = l; });
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
                firestore.collection("users").document(uid).collection("sessions").document(session.session_id).set(sessionData, SetOptions.merge());
                List<ChatMessage> messages = database.chatMessageDao().getMessageByeSession(session.session_id);
                for (ChatMessage msg : messages) syncMessageToFirestore(session.session_id, msg);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachFirebaseListenersIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        attachFirebaseListenersIfNeeded();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    private void attachFirebaseListenersIfNeeded() {
        if (currentUser != null && !currentUser.isGuest && mAuth.getCurrentUser() != null) {
            Log.d(TAG, "Attaching Firebase listeners for user: " + mAuth.getCurrentUser().getUid());
        }
    }

    @Override protected void onDestroy() { super.onDestroy(); if (executor != null) executor.shutdown(); }
}
