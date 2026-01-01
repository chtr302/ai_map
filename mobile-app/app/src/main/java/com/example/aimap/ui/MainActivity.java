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
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.aimap.R;
import com.example.aimap.data.AppDatabase;
import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.Session;
import com.example.aimap.data.SessionManager;
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

    // Google Sign-In
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        database = AppDatabase.getDatabase(this);
        apiManager = new ApiManager();

        // Khoi tao SessionManager va lay user hien tai (Guest hoac da login)
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

        // Cap nhat UI avatar/login button
        updateUserButton();

        // Mo drawer
        buttonMenu.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.START));

        // Click vao avatar/login button
        buttonUser.setOnClickListener(v -> {
            if (currentUser.isGuest) {
                startGoogleSignIn();
            } else {
                // Da login, co the show menu hoac profile
                Toast.makeText(this, "Xin chào " + currentUser.displayName, Toast.LENGTH_SHORT).show();
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
                                "Không có quyền truy cập vị trí, một số câu hỏi sẽ không hoạt động.",
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
                Toast.makeText(this, "Câu hỏi này cần vị trí, vui lòng cấp quyền...", Toast.LENGTH_SHORT).show();
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

            executor.execute(() -> database.chatMessageDao().insertMessage(userMessage));

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
                                aiMessage.message = finalText;
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
                                aiMessage.message = "Có lỗi khi kết nối với AI. Vui lòng thử lại.";
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
                                aiMessage.message = finalText;
                                aiMessage.metadata = finalJson;
                                chatAdapter.notifyItemChanged(chatList.size() - 1, "UPDATE_BUTTON");
                                int lastIndex = chatAdapter.getItemCount() - 1;
                                if (lastIndex >= 0) {
                                    recyclerViewChat.scrollToPosition(lastIndex);
                                }
                            });

                            executor.execute(() -> {
                                database.chatMessageDao().insertMessage(aiMessage);

                                Session s = database.sessionDao().getSessionById(currentSessionId);
                                if (s != null) {
                                    s.setPreview_message(finalText);
                                    s.setLast_updated(System.currentTimeMillis());
                                    database.sessionDao().updateSession(s);
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
                Toast.makeText(this, "Không lấy được thông tin tài khoản Google", Toast.LENGTH_SHORT).show();
                return;
            }

            String googleUserId = account.getId();
            String displayName = account.getDisplayName();
            String email = account.getEmail();
            String avatarUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null;

            // Luu vao DB
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

                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                    updateUserButton();

                    // Load lai sessions
                    loadUserSessions();
                });
            });

        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed", e);
            Toast.makeText(this, "Đăng nhập Google thất bại", Toast.LENGTH_SHORT).show();
        }
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
                .setTitle("Đổi tên cuộc trò chuyện")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        executor.execute(() -> {
                            session.setTitle(newTitle);
                            session.setLast_updated(System.currentTimeMillis());
                            database.sessionDao().updateSession(session);
                            List<Session> all = database.sessionDao().getSessionsByUserId(currentUser.userId);
                            mainHandler.post(() -> sessionAdapter.setSessions(all));
                        });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void togglePinSession(Session session) {
        executor.execute(() -> {
            session.setPinned(!session.isPinned());
            session.setLast_updated(System.currentTimeMillis());
            database.sessionDao().updateSession(session);
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
                                "Phải luôn có ít nhất 1 cuộc trò chuyện.",
                                    Toast.LENGTH_LONG).show()
                    );
                    return;
            }

            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Xóa cuộc trò chuyện")
                        .setMessage("Bạn có chắc muốn xóa cuộc trò chuyện này không?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
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
                        .setNegativeButton("Hủy", null)
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
                "Xin chào! Tôi là Loco AI, một trợ lý ảo chuyên trả lời các câu hỏi liên quan đến địa điểm. Hãy cho tôi biết bạn cần tìm gì nhé.",
                null,
                ChatMessage.TYPE_AI,
                System.currentTimeMillis()
        );
        addMessageToUI(welcomeMessage);

        executor.execute(() -> database.chatMessageDao().insertMessage(welcomeMessage));

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
