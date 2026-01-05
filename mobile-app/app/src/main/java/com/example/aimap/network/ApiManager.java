package com.example.aimap.network;

import androidx.annotation.NonNull;

import com.example.aimap.data.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiManager {
    public static final String LM_STUDIO_BASE_URL = "http://10.0.2.2:1234"; // API của Emulator đổi thành IP của máy tính đang chạy chương trình
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private OkHttpClient client;
    private boolean inThinkBlock = false;
    private Call currentCall;

    public ApiManager() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void cancelCurrentRequest() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }

    public interface StreamCallback {
        void onPartialResult(String partialResult);
        void onComplete(String fullResult, String error);
    }

    public interface ApiCallback {
        void onComplete(String fullResult, String error);
    }

    private String trimLeadingWhitespace(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int i = 0;
        while (i < str.length() && Character.isWhitespace(str.charAt(i))) {
            i++;
        }
        return str.substring(i);
    }

    public void sendMessage(String modelName, String currentSessionId, String userInput, String systemPrompt, List<ChatMessage> history, StreamCallback callback) {
        this.inThinkBlock = false;
        new Thread(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", modelName);
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", 2048); 
                List<Map<String, String>> messages = new ArrayList<>();
                
                // 1. System Prompt
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt); 
                messages.add(systemMessage);

                // 2. Chat History
                if (history != null && !history.isEmpty()) {
                    for (ChatMessage msg : history) {
                        Map<String, String> histMsg = new HashMap<>();
                        String role = (msg.type == ChatMessage.TYPE_USER) ? "user" : "assistant";
                        histMsg.put("role", role);

                        String messageContent = msg.message;
                        if (messageContent != null && messageContent.contains("|||")) {
                            messageContent = messageContent.split("\\|\\|\\|")[0].trim();
                        }
                        histMsg.put("content", messageContent);
                        messages.add(histMsg);
                    }
                }

                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", userInput);
                messages.add(userMessage);

                requestBody.put("messages", messages);
                requestBody.put("stream", true);

                String jsonBody = convertMapToJson(requestBody);

                RequestBody body = RequestBody.create(jsonBody, JSON);
                Request request = new Request.Builder()
                        .url(LM_STUDIO_BASE_URL + "/v1/chat/completions")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                
                currentCall = client.newCall(request);
                currentCall.enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (call.isCanceled()) return;
                        callback.onComplete(null, "Lỗi kết nối: " + e.getMessage());
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try (ResponseBody responseBody = response.body()) {
                            if (!response.isSuccessful()) {
                                String errorBody = responseBody != null ? responseBody.string() : "null";
                                callback.onComplete(null, "Lỗi HTTP: " + response.code() + " - " + response.message() + ", body: " + errorBody);
                                return;
                            }
                            if (responseBody != null) {
                                BufferedReader reader = new BufferedReader(responseBody.charStream());
                                String line;
                                StringBuilder fullResponse = new StringBuilder();
                                boolean isFirstVisibleChunk = true;

                                while ((line = reader.readLine()) != null) {
                                    if (line.trim().equals("data: [DONE]")) {
                                        break;
                                    }
                                    if (line.startsWith("data: ")) {
                                        String jsonData = line.substring(6).trim();
                                        if (jsonData.isEmpty()) continue;

                                        String content = extractContentFromStreamData(jsonData);
                                        if (content == null) continue;

                                        String partToSend = content;
                                        
                                        if (partToSend != null && !partToSend.isEmpty()) {

                                            if (isFirstVisibleChunk) {
                                                partToSend = trimLeadingWhitespace(partToSend);
                                            }

                                            if (!partToSend.isEmpty()) {
                                                isFirstVisibleChunk = false;
                                                fullResponse.append(partToSend);
                                                callback.onPartialResult(partToSend);
                                            }
                                        }
                                    }
                                }
                                callback.onComplete(fullResponse.toString(), null);
                            } else {
                                callback.onComplete(null, "Phản hồi không có nội dung");
                            }
                        }
                    }
                });
            } catch (Exception e) {
                callback.onComplete(null, "Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private String extractResponseContent(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray choicesArray = jsonObject.getJSONArray("choices");
            if (choicesArray.length() > 0) {
                JSONObject choice = choicesArray.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String content = message.getString("content");
                if (content != null) {
                    content = content.replaceAll("\\*\\*", "");
                }
                return content;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String extractContentFromStreamData(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray choicesArray = jsonObject.getJSONArray("choices");
            if (choicesArray.length() > 0) {
                JSONObject choice = choicesArray.getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                if (delta.has("content")) {
                    return delta.getString("content");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String convertMapToJson(Map<String, Object> map) {
        try {
            return new JSONObject(map).toString();
        }
        catch (Exception e) {
            return "{}";
        }
    }
}
