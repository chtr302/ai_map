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
import com.example.aimap.network.ApiManager;
import com.example.aimap.ui.adapter.ChatAdapter;
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
    private ImageButton buttonMenu;
    private TextView textViewEmptyChat;

    // background
    private ExecutorService executor;
    private Handler mainHandler;
    private long lastUpdateUiTime = 0;
    private boolean isGenerating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        AppDatabase database = AppDatabase.getDatabase(this);
        apiManager = new ApiManager();

        drawerLayout = findViewById(R.id.drawerLayout);
        editTextMessage = findViewById(R.id.editTextMessage);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        textViewEmptyChat = findViewById(R.id.textViewEmptyChat);

        currentSessionId = java.util.UUID.randomUUID().toString();

        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, chatList, this);
        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(chatLayoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        suggestionAdapter = new SuggestionAdapter(suggestion -> {
            if (isGenerating) return; // Chặn click khi đang generate
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
        requestLocationPermission();

        ChatMessage welcomeMessage = new ChatMessage(
                java.util.UUID.randomUUID().toString(),
                currentSessionId,
                "Xin chào! Tôi là Loco AI, một trợ lý ảo được Hậu xây dựng để chuyên trả lời các câu hỏi liên quan đến địa điểm. Hãy cho tôi một vài câu hỏi nhé.",
                null,
                ChatMessage.TYPE_AI,
                System.currentTimeMillis()
        );
        addMessageToUI(welcomeMessage);

        generateRandomSuggestions();
        updateEmptyStateUi();

        buttonSend.setOnClickListener(v -> {
            if (isGenerating) {
                // Xử lý dừng generate
                apiManager.cancelCurrentRequest();
                isGenerating = false;
                toggleSendingState(false);
                return;
            }

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
            executor.execute(() -> {
                apiManager.sendMessage(currentSessionId, msg, finalSystemPrompt, new ApiManager.StreamCallback() {
                    private final StringBuilder streamingResponse = new StringBuilder();
                    private boolean firstChunk = true;

                    @Override
                    public void onPartialResult(String partialResult) {
                        if (!isGenerating) return; // Dừng xử lý nếu đã bị cancel

                        if (firstChunk) {
                            streamingResponse.setLength(0);
                            firstChunk = false;
                        }
                        streamingResponse.append(partialResult);
                        
                        // Xử lý chuỗi trong background thread
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
                        // Reset throttling timer
                        lastUpdateUiTime = 0;
                        
                        if (!isGenerating) return; // Bỏ qua nếu đã cancel

                        mainHandler.post(() -> {
                            isGenerating = false;
                            toggleSendingState(false);
                        });
                        
                        if (error != null){
                            Log.e(TAG, "API Error in sendMessage: " + error);
                            mainHandler.post(() -> {
                                aiMessage.message = "Có lỗi khi kết nối với AI rồi. Liên hệ Hậu để được Hậu hỗ trợ nhé.";
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        } else {
                            Log.d(TAG, "Full AI Response: " + fullResult);
                            
                            String textPart = fullResult;
                            String jsonPart = null;

                            // Tách JSON
                            if (fullResult.contains("|||")) {
                                String[] parts = fullResult.split("\\|\\|\\|");
                                if (parts.length > 0) textPart = parts[0].trim();
                                if (parts.length > 1) jsonPart = parts[1].trim();
                            }

                            final String finalText = textPart;
                            final String finalJson = jsonPart;

                            mainHandler.post(() -> {
                                // Cập nhật kết quả cuối cùng
                                aiMessage.message = finalText;
                                aiMessage.metadata = finalJson;
                                
                                // Gửi payload UPDATE_BUTTON để chỉ hiện nút, không render lại text
                                chatAdapter.notifyItemChanged(chatList.size() - 1, "UPDATE_BUTTON");
                                recyclerViewChat.smoothScrollToPosition(chatList.size() - 1);
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
            recyclerViewSuggestions.setEnabled(false); // Chặn click vào list
            recyclerViewSuggestions.setAlpha(0.5f);
        } else {
            buttonSend.setImageResource(R.drawable.ic_send);
            editTextMessage.setEnabled(true);
            recyclerViewSuggestions.setEnabled(true);
            recyclerViewSuggestions.setAlpha(1.0f);
        }
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
        recyclerViewChat.smoothScrollToPosition(chatList.size() - 1);
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

    // Implement interface từ ChatAdapter
    @Override
    public void onPlacesButtonClick(String jsonMetadata) {
        PlacesBottomSheetFragment bottomSheet = PlacesBottomSheetFragment.newInstance(jsonMetadata, lastKnownLocation);
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