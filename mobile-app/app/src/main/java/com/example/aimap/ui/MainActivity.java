package com.example.aimap.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.aimap.R;
import com.example.aimap.data.AppDatabase;
import com.example.aimap.data.ChatLocalRepository;
import com.example.aimap.data.ChatSession;
import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.SessionManager;
import com.example.aimap.data.SystemPrompts;
import com.example.aimap.data.UserSession;
import com.example.aimap.network.ApiManager;
import com.example.aimap.ui.adapter.ChatAdapter;
import com.example.aimap.ui.adapter.ChatSessionAdapter;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity"; // Tag cho log

    private EditText editTextMessage;
    private RecyclerView recyclerViewChat;
    private RecyclerView recyclerViewDrawerSessions;
    private RecyclerView recyclerViewSuggestions;
    private ChatAdapter chatAdapter;
    private ChatSessionAdapter sessionAdapter;
    private SuggestionAdapter suggestionAdapter;
    private ArrayList<ChatMessage> chatList;
    private String currentSessionId;
    private ApiManager apiManager;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private android.location.Location lastKnownLocation;

    // Drawer
    private DrawerLayout drawerLayout;
    private MaterialButton buttonNewSession;
    private ImageButton buttonMenu;
    private ImageButton buttonUser;
    private TextView textViewEmptyChat;

    // Executor cho background
    private ExecutorService executor;
    private Handler mainHandler;

    // Session user
    private SessionManager sessionManager;
    private UserSession currentUser;
    private ChatLocalRepository localRepository;
    private ChatLocalRepository cloudRepository; // alias tam thoi cho code cu

    // Google Sign-In
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    // Gioi han tin nhan che do khach
    private static final int MAX_GUEST_MESSAGES = 5;
    private int guestMessageCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor(); // Thread nen cho cac task IO
        mainHandler = new Handler(Looper.getMainLooper());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this); // Lay vi tri

        // Khoi tao database (Room)
        AppDatabase database = AppDatabase.getDatabase(this);
        apiManager = new ApiManager(); // Goi API LLM
        localRepository = new ChatLocalRepository(database.chatMessageDao(), database.chatSessionDao());
        cloudRepository = localRepository; // su dung chung 1 repo local

        // View trong layout chinh
        drawerLayout = findViewById(R.id.drawerLayout);
        editTextMessage = findViewById(R.id.editTextMessage);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        recyclerViewSuggestions = findViewById(R.id.recyclerViewSuggestions);
        recyclerViewDrawerSessions = findViewById(R.id.recyclerViewDrawerSessions);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        textViewEmptyChat = findViewById(R.id.textViewEmptyChat);
        buttonMenu = findViewById(R.id.buttonMenu);
        buttonUser = findViewById(R.id.buttonUser);
        buttonNewSession = findViewById(R.id.buttonNewSession);

        // Khoi tao session user (guest / google)
        sessionManager = SessionManager.getInstance(this);
        currentUser = sessionManager.getCurrentUser(); // Neu chua co thi se tu tao che do khach

        // Quy uoc: user Google co 1 session mac dinh "default", guest thi session random
        currentSessionId = UUID.randomUUID().toString();

        // Cau hinh Google Sign-In
        setupGoogleSignIn();

        // Cap nhat avatar tren toolbar theo trang thai user
        updateUserUi();

        // Mo / dong drawer khi bam nut 3 gach
        buttonMenu.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        buttonUser.setOnClickListener(v -> {
            if (currentUser != null && !currentUser.isGuest) {
                showAccountMenu(v); // Da login -> hien menu tai khoan
            } else {
                showLoginDialog();  // Khach -> popup dang nhap
            }
        });

        // Tao chat moi tu drawer
        buttonNewSession.setOnClickListener(v -> {
            if (currentUser == null || currentUser.isGuest) {
                // Che do khach: tao session moi nhung van giu lich su cu
                currentSessionId = UUID.randomUUID().toString();
                chatList.clear();
                chatAdapter.notifyDataSetChanged();
                updateEmptyStateUi();
                // Cap nhat lai danh sach lich su chat trong drawer
                loadSessionsForDrawerOnly();
                return;
            }

            // User da dang nhap: tao session moi va luu vao SQLite
            String newSessionId = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            ChatSession newSession = new ChatSession(
                    newSessionId,
                    currentUser.userId,
                    "New chat",
                    now,
                    now,
                    false,
                    ""
            );

            currentSessionId = newSessionId;
            localRepository.createOrUpdateSession(currentUser, newSession);

            chatList.clear();
            chatAdapter.notifyDataSetChanged();
            updateEmptyStateUi();

            // Dong bo lai danh sach session trong drawer (bao gom ca session cu)
            loadSessionsForDrawerOnly();

            drawerLayout.closeDrawers();
        });

        // Adapter cho danh sach chat
        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList);
        LinearLayoutManager chatLayoutManager = new LinearLayoutManager(this);
        chatLayoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(chatLayoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        // Adapter cho danh sach session trong drawer
        sessionAdapter = new ChatSessionAdapter(new ChatSessionAdapter.SessionClickListener() {
            @Override
            public void onSessionSelected(ChatSession session) {
                onSessionClicked(session);
            }

            @Override
            public void onSessionMenuClicked(View anchor, ChatSession session) {
                showSessionMenu(anchor, session);
            }
        });
        recyclerViewDrawerSessions.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewDrawerSessions.setAdapter(sessionAdapter);

        // Adapter cho goi y cau hoi
        suggestionAdapter = new SuggestionAdapter(suggestion -> {
            editTextMessage.setText(suggestion);
            buttonSend.performClick();
        });
        recyclerViewSuggestions.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        );
        recyclerViewSuggestions.setAdapter(suggestionAdapter);

        // Xin quyen vi tri
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        fetchLocationForContext();
                    } else {
                        Toast.makeText(
                                this,
                                "Không có quyền vị trí, một số câu hỏi 'gần đây' sẽ không chính xác.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
        requestLocationPermission();

        // Neu la khach: them tin nhan chao + goi y va tai lich su chat (neu co)
        // Neu user da login: tai danh sach session + lich su tu SQLite
        if (currentUser == null || currentUser.isGuest) {
            ChatMessage welcomeMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    currentSessionId,
                    "Xin chào! Tớ là Loco AI, trợ lý nhỏ của Hậu chuyên trả lời các câu hỏi về địa điểm. Hỏi thử tớ vài câu nhé.",
                    ChatMessage.TYPE_AI,
                    System.currentTimeMillis()
            );
            addMessageToUI(welcomeMessage);

            generateRandomSuggestions();
            updateEmptyStateUi();
            // Lich su chat cua che do khach (neu co) van duoc hien trong drawer
            loadSessionsForDrawerOnly();
        } else {
            // Da login Google -> tai danh sach session va lich su tu SQLite
            loadSessionsFromLocal();
        }

        // Xu ly nut send
        buttonSend.setOnClickListener(v -> {
            String msg = editTextMessage.getText().toString().trim();
            if (msg.isEmpty()) {
                return;
            }

            // Lay lai user tu SessionManager neu can
            if (currentUser == null) {
                currentUser = sessionManager.getCurrentUser();
            }

            // Gioi han so tin nhan neu dang o che do khach
        if (currentUser != null && currentUser.isGuest && guestMessageCount >= MAX_GUEST_MESSAGES) {
                Toast.makeText(
                        this,
                        "Bạn đã dùng 5 tin nhắn ở chế độ khách. Đăng nhập Google để tiếp tục dùng Loco AI nhé.",
                        Toast.LENGTH_LONG
                ).show();
                showLoginDialog();
                return;
            }

            // Kiem tra cau hoi co can vi tri hay khong
            List<String> locationKeywords = Arrays.asList("gần đây", "ở đây", "xung quanh", "quanh đây");
            boolean requiresLocation = false;
            for (String key : locationKeywords) {
                if (msg.contains(key)) {
                    requiresLocation = true;
                    break;
                }
            }

            if (requiresLocation && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "Câu hỏi này cần vị trí, vui lòng cấp quyền trước nhé.",
                        Toast.LENGTH_SHORT
                ).show();
                requestLocationPermission();
                return;
            }

            editTextMessage.setText("");

            ChatMessage userMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
                    currentSessionId,
                    msg,
                    ChatMessage.TYPE_USER,
                    System.currentTimeMillis()
            );
            addMessageToUI(userMessage);

            if (currentUser != null && currentUser.isGuest) {
                // Dem tin nhan che do khach
                guestMessageCount++;
            }

            // Luu message user vao SQLite theo session
            if (localRepository != null) {
                localRepository.saveMessage(currentUser, userMessage);
            }
            // Cap nhat danh sach lich su chat trong drawer
            loadSessionsForDrawerOnly();

            // Tao system prompt dong theo vi tri
            String dynamicSystemPrompt = SystemPrompts.DEFAULT_MAP_PROMPT;
            if (lastKnownLocation != null) {
                dynamicSystemPrompt += String.format(
                        Locale.US,
                        "\n# BỐI CẢNH VỊ TRÍ\nVị trí hiện tại của người dùng là (latitude: %f, longitude: %f). Hãy dùng thông tin này nếu câu hỏi có 'gần đây', 'ở đây', 'xung quanh đây'.",
                        lastKnownLocation.getLatitude(),
                        lastKnownLocation.getLongitude()
                );
            }

            ChatMessage aiMessage = new ChatMessage(
                    UUID.randomUUID().toString(),
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
                        if (error != null) {
                            Log.e(TAG, "API Error in sendMessage: " + error);
                            mainHandler.post(() -> {
                                aiMessage.message = "Có lỗi khi kết nối với AI, thử lại sau nhé.";
                                chatAdapter.notifyItemChanged(chatList.size() - 1);
                            });
                        } else {
                            // Luu cau tra loi AI vao SQLite
                            if (localRepository != null) {
                                localRepository.saveMessage(currentUser, aiMessage);
                            }
                            // Dong bo lai danh sach session sau khi co cau tra loi moi
                            loadSessionsForDrawerOnly();
                        }
                    }
                });
            });
        });
    }

    // Hien dialog dang nhap de len man chat
    private void showLoginDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        MaterialButton buttonGoogleLogin = dialogView.findViewById(R.id.buttonGoogleLogin);

        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        buttonGoogleLogin.setOnClickListener(v -> {
            dialog.dismiss();
            startGoogleSignIn();
        });

        dialog.show();
    }

    // Cau hinh Google Sign-In va launcher
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail() // Chi can email, chua can idToken vi chua ket backend rieng
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        Log.w(TAG, "Google sign in canceled or no data");
                        Toast.makeText(this, "Đăng nhập Google bị huỷ hoặc không có dữ liệu", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // Goi tu dialog / nut avatar
    public void startGoogleSignIn() {
        if (googleSignInClient == null) {
            setupGoogleSignIn();
        }
        try {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            Toast.makeText(this, "Đang mở đăng nhập Google...", Toast.LENGTH_SHORT).show();
            googleSignInLauncher.launch(signInIntent);
        } catch (Exception e) {
            Log.e(TAG, "startGoogleSignIn error", e);
            Toast.makeText(this, "Không thể mở màn hình đăng nhập Google", Toast.LENGTH_LONG).show();
        }
    }

    // Xu ly ket qua Google Sign-In
    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account == null) {
                Toast.makeText(this, "Không lấy được thông tin tài khoản Google", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = account.getId();
            String name = account.getDisplayName();
            String email = account.getEmail();
            String photoUrl = account.getPhotoUrl() != null
                    ? account.getPhotoUrl().toString()
                    : null;

            currentUser = new UserSession(
                    userId,
                    name != null ? name : "Google user",
                    email,
                    photoUrl,
                    false,
                    UserSession.PROVIDER_GOOGLE
            );
            sessionManager.setCurrentUser(currentUser);

            // Session mac dinh cho user Google
            currentSessionId = "default";
            guestMessageCount = 0;

            updateUserUi();
            Toast.makeText(this, "Đăng nhập Google thành công", Toast.LENGTH_SHORT).show();

            // Sau khi login, tai danh sach session + lich su tu SQLite
            loadSessionsFromLocal();

        } catch (ApiException e) {
            Log.w(TAG, "Google sign in failed", e);
            Toast.makeText(this, "Đăng nhập Google thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    // Cap nhat icon avatar tren toolbar theo trang thai user
    private void updateUserUi() {
        if (buttonUser == null) return;

        if (currentUser != null && !currentUser.isGuest) {
            if (currentUser.avatarUrl != null && !currentUser.avatarUrl.isEmpty()) {
                Glide.with(this)
                        .load(currentUser.avatarUrl)
                        .placeholder(R.drawable.ic_avatar)
                        .into(buttonUser);
            } else {
                buttonUser.setImageResource(R.drawable.ic_avatar);
            }
        } else {
            buttonUser.setImageResource(R.drawable.ic_avatar);
        }
    }

    // Hien menu tai khoan khi user da login
    private void showAccountMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_account, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_manage_account) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://myaccount.google.com"));
                startActivity(intent);
                return true;
            } else if (id == R.id.action_sign_out) {
                signOut();
                return true;
            }
            return false;
        });

        popupMenu.show();
    }

    // Dang xuat, quay ve che do khach
    private void signOut() {
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        currentUser = sessionManager.createGuestSession();
        guestMessageCount = 0;
        updateUserUi();
        Toast.makeText(this, "Đã đăng xuất, chuyển về chế độ khách", Toast.LENGTH_SHORT).show();
    }

    // Tai danh sach session tu SQLite va dong bo voi danh sach trong drawer
    private void loadSessionsFromLocal() {
        if (localRepository == null || currentUser == null) {
            return;
        }
        localRepository.loadSessions(currentUser, new ChatLocalRepository.LoadSessionsCallback() {
            @Override
            public void onSuccess(List<ChatSession> sessions) {
                mainHandler.post(() -> {
                    sessionAdapter.setSessions(sessions);
                    // Sau khi co danh sach session, tai messages cho session hien tai
                    loadMessagesFromLocal();
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "loadSessionsFromLocal error", e);
                loadMessagesFromLocal();
            }
        });
    }

    // Tai danh sach session chi de hien thi lich su chat trong drawer
    // khong load lai noi dung khung chat hien tai
    private void loadSessionsForDrawerOnly() {
        if (localRepository == null || currentUser == null) {
            return;
        }
        localRepository.loadSessions(currentUser, new ChatLocalRepository.LoadSessionsCallback() {
            @Override
            public void onSuccess(List<ChatSession> sessions) {
                mainHandler.post(() -> sessionAdapter.setSessions(sessions));
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "loadSessionsForDrawerOnly error", e);
            }
        });
    }

    // Dialog doi ten session
    private void showRenameSessionDialog(ChatSession session) {
        final EditText input = new EditText(this);
        input.setText(session.title != null ? session.title : "");
        input.setSelection(input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle("Đổi tên đoạn chat")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newTitle = input.getText().toString().trim();
                    session.title = newTitle;
                    localRepository.updateSessionTitle(currentUser, session.sessionId, newTitle);
                    loadSessionsFromLocal();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Xoa session
    private void deleteSession(ChatSession session) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Xóa đoạn chat")
                .setMessage("Bạn có chắc muốn xóa đoạn chat này không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    cloudRepository.deleteSession(currentUser, session.sessionId, () -> {
                        mainHandler.post(() -> {
                            if (sessionAdapter != null) {
                                sessionAdapter.removeSession(session.sessionId);
                            }
                            // Neu dang mo session nay thi clear UI
                            if (session.sessionId.equals(currentSessionId)) {
                                chatList.clear();
                                chatAdapter.notifyDataSetChanged();
                                updateEmptyStateUi();
                            }
                        });
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Tai lich su chat tu SQLite theo session hien tai
    private void loadMessagesFromLocal() {
        if (localRepository == null || currentUser == null) {
            return;
        }
        if (currentSessionId == null || currentSessionId.isEmpty()) {
            currentSessionId = "default";
        }

        localRepository.loadMessages(currentUser, currentSessionId, new ChatLocalRepository.LoadMessagesCallback() {
            @Override
            public void onSuccess(List<ChatMessage> messages) {
                mainHandler.post(() -> {
                    chatList.clear();
                    if (messages != null && !messages.isEmpty()) {
                        chatList.addAll(messages);
                    }
                    chatAdapter.notifyDataSetChanged();
                    updateEmptyStateUi();
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "loadMessagesFromLocal error", e);
            }
        });
    }

    // Xu ly khi nguoi dung chon 1 session trong drawer
    private void onSessionClicked(ChatSession session) {
        if (session == null) return;
        currentSessionId = session.sessionId;
        drawerLayout.closeDrawers();
        loadMessagesFromCloud();
    }

    // Hien menu 3 cham cho tung session (pin / rename / delete)
    private void showSessionMenu(View anchor, ChatSession session) {
        if (session == null || currentUser == null || currentUser.isGuest) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_session_item, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_session_pin) {
                boolean newPinned = !session.pinned;
                session.pinned = newPinned;
                cloudRepository.updateSessionPinned(currentUser, session.sessionId, newPinned);
                // Reload danh sach session de sap xep lai
                loadSessionsFromCloud();
                return true;
            } else if (id == R.id.action_session_rename) {
                showRenameSessionDialog(session);
                return true;
            } else if (id == R.id.action_session_delete) {
                deleteSession(session);
                return true;
            }
            return false;
        });

        popupMenu.show();
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
                        lastKnownLocation = location; // Luu vi tri
                        Log.d(TAG, "Location fetched: " + location.getLatitude() + ", " + location.getLongitude());
                    } else {
                        Log.e(TAG, "Failed to get location, it was null.");
                    }
                });
    }

    // GiA¯? giA¯? ham cu, chuyA\"n sang xA¿ li local
    private void loadMessagesFromCloud() {
        loadMessagesFromLocal();
    }

    private void loadSessionsFromCloud() {
        loadSessionsFromLocal();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
