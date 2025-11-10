package com.example.aimap.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.data.ChatMessage;
import com.example.aimap.R;
import com.example.aimap.ui.adapter.ChatAdapter;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private EditText editTextMessage;
    private RecyclerView recyclerViewChat;
    private ChatAdapter chatAdapter;
    private ArrayList<ChatMessage> chatList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        editTextMessage = findViewById(R.id.editTextMessage);
        ImageButton buttonSend = findViewById(R.id.buttonSend);
        recyclerViewChat = findViewById(R.id.recyclerViewChat);

        chatList = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatList);


        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        addAIMessage("Xin chào! Tôi là AI-MAP. Bạn muốn tìm kiếm địa điểm nào?");

        buttonSend.setOnClickListener(v -> {
            String msg = editTextMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                addUserMessage(msg);
                editTextMessage.setText("");
            }
        });
    }

    private void addAIMessage(String message) {
        chatList.add(new ChatMessage(message, ChatMessage.TYPE_AI));
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        recyclerViewChat.scrollToPosition(chatList.size() - 1);
    }

    private void addUserMessage(String message) {
        chatList.add(new ChatMessage(message, ChatMessage.TYPE_USER));
        chatAdapter.notifyItemInserted(chatList.size() - 1);
        recyclerViewChat.scrollToPosition(chatList.size() - 1);
    }
}
