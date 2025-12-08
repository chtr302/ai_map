package com.example.aimap.ui;

import android.Manifest;
import android.annotation.SuppressLint;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.R;
import com.example.aimap.data.AppDatabase;
import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.Session;
import com.example.aimap.data.SystemPrompts;
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

public class MainActivity extends AppCompatActivity implements ChatAdapter.OnPlacesButtonClickListener {
    private static final String TAG = "MainActivity";

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

    // location
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private android.location.Location lastKnownLocation;

    // Drawer + nút
    private DrawerLayout drawerLayout;
    private MaterialButton buttonNewSession;
    private TextView textViewEmptyChat;

    // background
    private ExecutorService executor;
    private Handler mainHandler;
    private long lastUpdateUiTime = 0;
    private boolean isGenerating = false;

    // database
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

        drawerLayout = findViewById(R.id.drawerLayout);
        editTextMessage = findViewById(R.id.editTextMessage);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        recyclerViewDrawerSessions = findViewById(R.id.recyclerViewDrawerSessions);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        textViewEmptyChat = findViewById(R.id.textViewEmptyChat);
        buttonNewSession = findViewById(R.id.buttonNewSession);
        ImageButton buttonMenu = findViewById(R.id.buttonMenu);

        // Mở drawer
        buttonMenu.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.START));

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

        // Sessions: click item để load, click nút ... để đổi tên / xóa
        sessionAdapter = new SessionAdapter(new SessionAdapter.OnSessionClickListener() {
            @Override
            public void onSessionClick(Session session) {
                Log.d(TAG, "Click session: " + session.session_id);
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

        // Khởi tạo session từ DB
        executor.execute(() -> {
            int count = database.sessionDao().getSessionCount();
            if (count == 0) {
                String newId = java.util.UUID.randomUUID().toString();
                long now = System.currentTimeMillis();
                Session session = new Session(
                        newId,
                        "Cuộc trò chuyện mới",
                        "",
                        now
                );
                database.sessionDao().insertSession(session);
                currentSessionId = newId;
            } else {
                Session latest = database.sessionDao().getAllSessions().get(0);
                currentSessionId = latest.session_id;
            }

            List<Session> all = database.sessionDao().getAllSessions();
            List<ChatMessage> messages =
                    database.chatMessageDao().getMessageByeSession(currentSessionId);

            mainHandler.post(() -> {
                sessionAdapter.setSessions(all);
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

        // Gửi tin nhắn
        buttonSend.setOnClickListener(v -> {
            if (isGenerating) {
                apiManager.cancelCurrentRequest();
                isGenerating = false;
                toggleSendingState(false);
                return;
            }

            String msg = editTextMessage.getText().toString().trim();
            if (msg.isEmpty()) return;

            List<String> locationKeywords = Arrays.asList("gần đây", "ở đây", "xung quanh", "quanh đây");
            boolean requiresLocation = locationKeywords.stream().anyMatch(msg::contains);

            if (requiresLocation &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Câu hỏi này cần vị trí, vui lòng cấp quyền...", Toast.LENGTH_SHORT).show();
                requestLocationPermission();
                return;
            }

            editTextMessage.setText("");
            isGenerating = true;
            toggleSendingState(true);

            ChatMessage userMessage = new ChatMessage(
                    java.util.UUID.randomUUID().toString(),
                    currentSessionId,
                    msg,
                    null,
                    ChatMessage.TYPE_USER,
                    System.currentTimeMillis()
            );
            addMessageToUI(userMessage);

            // Lưu user message vào DB
            executor.execute(() ->
                    database.chatMessageDao().insertMessage(userMessage)
            );

            // Cập nhật Session với câu hỏi
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

                    List<Session> all = database.sessionDao().getAllSessions();
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
                    java.util.UUID.randomUUID().toString(),
                    currentSessionId,
                    "...",
                    null,
                    ChatMessage.TYPE_AI,
                    System.currentTimeMillis()
            );
            addMessageToUI(aiMessage);

            final String finalSystemPrompt = dynamicSystemPrompt;
            
            // Lấy lịch sử chat để gửi kèm (Context Awareness)
            // chatList hiện tại đang chứa: [...History, UserMessage (vừa thêm), AiPlaceholder (vừa thêm)]
            // Chúng ta cần lấy History, tức là bỏ 2 phần tử cuối.
            List<ChatMessage> historyToSend = new ArrayList<>();
            if (chatList.size() > 2) {
                // Lấy tối đa 20 tin nhắn gần nhất để làm context, hoặc tất cả nếu ít hơn 20
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
                            Log.e(TAG, "API Error in sendMessage: " + error);
                            mainHandler.post(() -> {
                                aiMessage.message = "Có lỗi khi kết nối với AI rồi. Liên hệ Hậu để được Hậu hỗ trợ nhé.";
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        } else {
                            Log.d(TAG, "Full AI Response: " + fullResult);

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

                            // Lưu AI message + update preview session
                            executor.execute(() -> {
                                database.chatMessageDao().insertMessage(aiMessage);

                                Session s = database.sessionDao().getSessionById(currentSessionId);
                                if (s != null) {
                                    s.setPreview_message(finalText);
                                    s.setLast_updated(System.currentTimeMillis());
                                    database.sessionDao().updateSession(s);
                                    List<Session> all = database.sessionDao().getAllSessions();
                                    mainHandler.post(() -> sessionAdapter.setSessions(all));
                                }
                            });
                        }
                    }
                });
            });
        });
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

    private void createNewSession() {
        currentSessionId = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Session session = new Session(
                currentSessionId,
                "Cuộc trò chuyện mới",
                "",
                now
        );

        executor.execute(() -> {
            database.sessionDao().insertSession(session);
            List<Session> all = database.sessionDao().getAllSessions();
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
        Log.d(TAG, "loadSession: " + sessionId);
        currentSessionId = sessionId;
        executor.execute(() -> {
            List<ChatMessage> messages =
                    database.chatMessageDao().getMessageByeSession(sessionId);
            Log.d(TAG, "messages size = " + messages.size());
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

    // Dialog: Đổi tên / Xóa cuộc trò chuyện
    private void showSessionOptionsDialog(Session session) {
        String[] options = {"Đổi tên", "Xóa cuộc trò chuyện"};
        new AlertDialog.Builder(this)
                .setTitle(session.title == null || session.title.isEmpty()
                        ? "Cuộc trò chuyện"
                        : session.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(session);
                    } else if (which == 1) {
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
                            List<Session> all = database.sessionDao().getAllSessions();
                            mainHandler.post(() -> sessionAdapter.setSessions(all));
                        });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmDeleteSession(Session session) {
        executor.execute(() -> {
            int count = database.sessionDao().getSessionCount();

            // Nếu chỉ còn 1 session và nó là "Cuộc trò chuyện mới" -> không cho xóa
            if (count <= 1) {
                if (session.title == null || session.title.trim().isEmpty()
                        || "Cuộc trò chuyện mới".equals(session.title.trim())) {
                    mainHandler.post(() ->
                            Toast.makeText(this,
                                    "Phải luôn có ít nhất 1 cuộc trò chuyện mới, không thể xóa.",
                                    Toast.LENGTH_LONG).show()
                    );
                    return;
                }
            }

            // Cho phép xóa -> show dialog xác nhận
            mainHandler.post(() -> {
                new AlertDialog.Builder(this)
                        .setTitle("Xóa cuộc trò chuyện")
                        .setMessage("Bạn có chắc muốn xóa cuộc trò chuyện này không?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            executor.execute(() -> {
                                // Xóa toàn bộ message thuộc session
                                database.chatMessageDao().deleteMessagesBySession(session.session_id);
                                // Xóa session
                                database.sessionDao().deleteSessionById(session.session_id);

                                // Lấy danh sách còn lại
                                List<Session> remaining = database.sessionDao().getAllSessions();
                                if (remaining.isEmpty()) {
                                    String newId = java.util.UUID.randomUUID().toString();
                                    long now = System.currentTimeMillis();
                                    Session newSession = new Session(
                                            newId,
                                            "Cuộc trò chuyện mới",
                                            "",
                                            now
                                    );
                                    database.sessionDao().insertSession(newSession);
                                    remaining = database.sessionDao().getAllSessions();
                                    currentSessionId = newId;
                                } else {
                                    currentSessionId = remaining.get(0).session_id;
                                }

                                List<ChatMessage> msgs =
                                        database.chatMessageDao().getMessageByeSession(currentSessionId);

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
                java.util.UUID.randomUUID().toString(),
                currentSessionId,
                "Xin chào! Tôi là Loco AI, một trợ lý ảo được Hậu xây dựng để chuyên trả lời các câu hỏi liên quan đến địa điểm. Hãy cho tôi một vài câu hỏi nhé.",
                null,
                ChatMessage.TYPE_AI,
                System.currentTimeMillis()
        );
        addMessageToUI(welcomeMessage);

        // Lưu welcome vào DB
        executor.execute(() ->
                database.chatMessageDao().insertMessage(welcomeMessage)
        );

        generateRandomSuggestions();
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
