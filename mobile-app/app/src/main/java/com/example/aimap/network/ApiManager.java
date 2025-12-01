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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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

    public void sendMessage(String currentSessionId, String userInput, String systemPrompt, StreamCallback callback) {
        this.inThinkBlock = false;
        new Thread(() -> {
            try {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", "qwen3:8b");
                requestBody.put("temperature", 0.7);
                requestBody.put("max_tokens", 1500); // Tăng lên 1500
                List<Map<String, String>> messages = new ArrayList<>();
                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt + " /no_think"); // Đây là system prompt không public được, nên ai cần thì liên hệ nhé
                messages.add(systemMessage);

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

                                        StringBuilder chunkOutput = new StringBuilder();
                                        int currentPos = 0;
                                        while (currentPos < content.length()) {
                                            if (inThinkBlock) {
                                                int endTag = content.indexOf("</think>", currentPos);
                                                if (endTag != -1) {
                                                    inThinkBlock = false;
                                                    currentPos = endTag + "</think>".length();
                                                } else {
                                                    currentPos = content.length();
                                                }
                                            } else {
                                                int startTag = content.indexOf("<think>", currentPos);
                                                if (startTag != -1) {
                                                    chunkOutput.append(content.substring(currentPos, startTag));
                                                    inThinkBlock = true;
                                                    currentPos = startTag + "<think>".length();
                                                } else {
                                                    chunkOutput.append(content.substring(currentPos));
                                                    currentPos = content.length();
                                                }
                                            }
                                        }

                                        String partToSend = chunkOutput.toString();
                                        if (!partToSend.isEmpty()) {
                                            partToSend = partToSend.replaceAll("\\*\\*", "");

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
                    content = content.replaceAll("(?s)<think>.*?</think>\\s*", "");
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
