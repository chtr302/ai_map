package com.example.aimap.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.R;
import com.example.aimap.data.AppDatabase;
import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.SystemPrompts;
import com.example.aimap.data.repository.SessionManager;
import com.example.aimap.network.ApiManager;
import com.example.aimap.ui.adapter.ChatAdapter;
import com.example.aimap.ui.adapter.SessionAdapter;
import com.example.aimap.ui.adapter.SuggestionAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity"; // Tag for logging
    private EditText editTextMessage;
    private RecyclerView recyclerViewChat;
    private RecyclerView recyclerViewDrawerSessions;
    private RecyclerView recyclerViewSuggestions;
    private ChatAdapter chatAdapter;
    private SessionAdapter drawerSessionAdapter;
    private SuggestionAdapter suggestionAdapter;
    private ArrayList<ChatMessage> chatList;
    private String currentSessionId;
    private SessionManager sessionManager;
    private ApiManager apiManager;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private android.location.Location lastKnownLocation;

    // Drawer
    private DrawerLayout drawerLayout;
    private MaterialButton buttonNewSession;
    private ImageButton buttonMenu;
    private TextView textViewEmptyChat;

    // Executor cho background tasks
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor(); // Để chạy thread ngầm
        mainHandler = new Handler(Looper.getMainLooper());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this); // Lấy vị trí

        // Khởi tạo database với session manager
        AppDatabase database = AppDatabase.getDatabase(this);
        sessionManager = new SessionManager(
                database.sessionDao(),
                database.chatMessageDao()
        );
        apiManager = new ApiManager(sessionManager);

        drawerLayout = findViewById(R.id.drawerLayout);
        editTextMessage = findViewById(R.id.editTextMessage);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewDrawerSessions = findViewById(R.id.recyclerViewDrawerSessions);
        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        buttonNewSession = findViewById(R.id.buttonNewSession);
        buttonMenu = findViewById(R.id.buttonMenu);
        textViewEmptyChat = findViewById(R.id.textViewEmptyChat);

        buttonMenu.setOnClickListener(v -> drawerLayout.open());

        // adapter cho danh sách chat
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList);
        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(chatLayoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        drawerSessionAdapter = new SessionAdapter(
            session -> {
                switchToSession(session.session_id);
                drawerLayout.closeDrawers();
            },
            new SessionAdapter.OnSessionActionListener() {
                @Override
                public void onDeleteSession(com.example.aimap.data.Session session) {
                    executor.execute(() -> {
                        sessionManager.deleteSession(session.session_id);
                        mainHandler.post(() -> {
                            loadSessionsForDrawer();
                            if (session.session_id.equals(currentSessionId)) {
                                executor.execute(() -> {
                                    String newSessionId = sessionManager.createNewSession("Cuộc trò chuyện mới");
                                    mainHandler.post(() -> switchToSession(newSessionId));
                                });
                            }
                        });
                    });
                }

                @Override
                public void onRenameSession(com.example.aimap.data.Session session) {
                    showRenameSessionDialog(session);
                }

                @Override
                public void onDuplicateSession(com.example.aimap.data.Session session) {
                    executor.execute(() -> {
                        String newSessionId = sessionManager.createNewSession(session.title + " (Sao chép)");
                        List<com.example.aimap.data.ChatMessage> originalMessages = sessionManager.getMessagesBySession(session.session_id);
                        for (com.example.aimap.data.ChatMessage message : originalMessages) {
                            sessionManager.addMessageToSession(newSessionId, message.getMessage(), message.getType());
                        }
                        mainHandler.post(MainActivity.this::loadSessionsForDrawer);
                    });
                }
            }
        );
        recyclerViewDrawerSessions.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewDrawerSessions.setAdapter(drawerSessionAdapter);

        // adapter cho gợi ý
        suggestionAdapter = new SuggestionAdapter(suggestion -> {
            editTextMessage.setText(suggestion);
            buttonSend.performClick();
        });
        recyclerViewSuggestions.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewSuggestions.setAdapter(suggestionAdapter);

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                fetchLocationForContext();
            } else {
                Toast.makeText(this, "Không có quyền truy cập vị trí, một số câu hỏi sẽ không hoạt động.", Toast.LENGTH_LONG).show();
            }
        });

        loadInitialSession();
        requestLocationPermission();

        buttonNewSession.setOnClickListener(v -> {
            executor.execute(() -> {
                String newSessionId = sessionManager.createNewSession("Cuộc trò chuyện mới");
                
                mainHandler.post(() -> {
                    switchToSession(newSessionId);
                    drawerLayout.closeDrawers();
                });
            });
        });

        buttonSend.setOnClickListener(v -> {
            String msg = editTextMessage.getText().toString().trim();
            if (msg.isEmpty()) {
                return;
            }

            List<String> locationKeywords = Arrays.asList("gần đây", "ở đây", "xung quanh", "quanh đây");
            boolean requiresLocation = locationKeywords.stream().anyMatch(msg::contains);

            if (requiresLocation && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Câu hỏi này cần vị trí, vui lòng cấp quyền...", Toast.LENGTH_SHORT).show();
                requestLocationPermission();
                return;
            }

            editTextMessage.setText("");

            ChatMessage userMessage = new ChatMessage(
                    java.util.UUID.randomUUID().toString(),
                    currentSessionId,
                    msg,
                    ChatMessage.TYPE_USER,
                    System.currentTimeMillis()
            );
            addMessageToUI(userMessage);
            saveMessageToDB(userMessage);

            // Tạo system prompt động
            String dynamicSystemPrompt = SystemPrompts.DEFAULT_MAP_PROMPT; // Có thể tạo 1 file để tạo System Prompt
            if (lastKnownLocation != null) {
                dynamicSystemPrompt += String.format(
                    Locale.US,
                    "\n# BỐI CẢNH VỊ TRÍ\nVị trí hiện tại của người dùng là (latitude: %f, longitude: %f). Hãy sử dụng thông tin này nếu câu hỏi của họ có liên quan đến 'ở đây', 'gần đây', 'xung quanh đây'.",
                    lastKnownLocation.getLatitude(),
                    lastKnownLocation.getLongitude()
                );
            }

            ChatMessage aiMessage = new ChatMessage(
                    java.util.UUID.randomUUID().toString(),
                    currentSessionId,
                    "...",
                    ChatMessage.TYPE_AI,
                    System.currentTimeMillis()
            );
            addMessageToUI(aiMessage);

            final String finalSystemPrompt = dynamicSystemPrompt;
            executor.execute(() -> {
                apiManager.sendMessage(currentSessionId, msg, finalSystemPrompt, new ApiManager.StreamCallback() {
                    private final StringBuilder streamingResponse = new StringBuilder();
                    private boolean firstChunk = true;

                    @Override
                    public void onPartialResult(String partialResult) {
                        if (firstChunk) {
                            streamingResponse.setLength(0);
                            firstChunk = false;
                        }
                        streamingResponse.append(partialResult);
aiMessage.message = streamingResponse.toString();
                        mainHandler.post(() -> {
                            chatAdapter.notifyItemChanged(chatList.size() - 1);
                            recyclerViewChat.post(() -> {
                                if (chatAdapter.getItemCount() > 0) {
                                    recyclerViewChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                                }
                            });
                        });
                    }

                    @Override
                    public void onComplete(String fullResult, String error) {
                        if (error != null){
                            Log.e(TAG, "API Error in sendMessage: " + error);
                            mainHandler.post(() -> {
                                aiMessage.message = "Có lỗi khi kết nối với AI rồi. Liên hệ Hậu để được Hậu hỗ trợ nhé, nếu bạn là con gái thì liên hệ qua zalo cho Hậu nheee, còn con trai thì gửi mail đi";
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        }
                        saveMessageToDB(aiMessage);
                    }
                });
            });
        });
    }

    private void loadInitialSession() {
        String passedSessionId = getIntent().getStringExtra("SESSION_ID");
        executor.execute(() -> {
            String sessionIdToLoad;
            List<com.example.aimap.data.Session> activeSessions = sessionManager.getAllActiveSessions();

            if (passedSessionId != null && !passedSessionId.isEmpty()) {
                sessionIdToLoad = passedSessionId;
            } else if (!activeSessions.isEmpty()) {
                sessionIdToLoad = activeSessions.get(0).session_id;
            } else {
                sessionIdToLoad = sessionManager.createNewSession("Cuộc trò chuyện mới");
                mainHandler.post(() -> {
                    ChatMessage welcomeMessage = new ChatMessage(
                        java.util.UUID.randomUUID().toString(),
                        sessionIdToLoad,
                        "Xin chào! Tôi là Loco AI, một trợ lý ảo được Hậu xây dựng để chuyên trả lời các câu hỏi liên quan đến địa điểm. Hãy cho tôi một vài câu hỏi nhé.",
                        ChatMessage.TYPE_AI,
                        System.currentTimeMillis()
                    );
                    addMessageToUI(welcomeMessage);
                    saveMessageToDB(welcomeMessage);
                });
            }

            mainHandler.post(() -> {
                switchToSession(sessionIdToLoad);
                loadSessionsForDrawer();
            });
        });
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
                    lastKnownLocation = location; // Store the location
                    Log.d(TAG, "Location fetched: " + location.getLatitude() + ", " + location.getLongitude());
                } else {
                    Log.e(TAG, "Failed to get location, it was null.");
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
                Log.e(TAG, "Error generating random suggestions", e);
            }
        });
    }

    private void switchToSession(String sessionId) {
        currentSessionId = sessionId;
        
        executor.execute(() -> {
            List<com.example.aimap.data.ChatMessage> messages = sessionManager.getMessagesBySession(currentSessionId);
            
            mainHandler.post(() -> {
                chatList.clear();
                chatList.addAll(messages);
                chatAdapter.notifyDataSetChanged();
                
                if (!chatList.isEmpty()) {
                    recyclerViewChat.scrollToPosition(chatList.size() - 1);
                }
                generateRandomSuggestions();
                updateEmptyStateUi();
            });
        });
    }

    private void loadSessionsForDrawer() {
        executor.execute(() -> {
            List<com.example.aimap.data.Session> sessions = sessionManager.getAllActiveSessions();
            
            mainHandler.post(() -> {
                drawerSessionAdapter.updateSessions(sessions);
            });
        });
    }

    private void addMessageToUI(ChatMessage chatMessage) {
        chatList.add(chatMessage);
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        recyclerViewChat.smoothScrollToPosition(chatList.size() - 1);
        updateEmptyStateUi();
    }

    private void saveMessageToDB(ChatMessage chatMessage) {
        executor.execute(() -> {
            sessionManager.addMessageToSession(
                chatMessage.getSession_id(),
                chatMessage.getMessage(),
                chatMessage.getType()
            );
        });
    }

    private void showRenameSessionDialog(com.example.aimap.data.Session session) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Đổi tên cuộc trò chuyện");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(session.title);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                executor.execute(() -> {
                    sessionManager.updateSessionTitle(session.session_id, newName);
                    
                    mainHandler.post(this::loadSessionsForDrawer);
                });
            }
        });
        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());

        builder.show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}