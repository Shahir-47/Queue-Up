package com.QueueUp.Backend.service;

import com.QueueUp.Backend.model.Message;
import com.fasterxml.jackson.core.type.TypeReference; // Import added
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Generate Name and Bio based on age and music
    public Map<String, String> generateBotProfile(int age, List<String> musicSample) {
        String prompt = String.format(
                "Generate a JSON object for a dating app user. " +
                        "Age: %d. Music interests: %s. " +
                        "Format: {\"name\": \"Firstname Lastname\", \"bio\": \"Short, witty bio (max 150 chars)\"}. " +
                        "Make them sound real, casual, and relatable.",
                age, String.join(", ", musicSample)
        );

        return callOpenAIForJson(prompt);
    }

    // Generate a chat reply
    public String generateChatReply(String botName, String botBio, List<Message> history) {
        // Build the conversation for the AI context
        List<Map<String, String>> messages = new ArrayList<>();

        // System prompt to set character
        messages.add(Map.of("role", "system", "content",
                "You are " + botName + ". Your bio is: " + botBio + ". " +
                        "You are chatting on a dating app. Keep messages short, casual, and friendly. " +
                        "Do B sound like a robot. Do not Use emojis."));

        // Add history (last 10 messages max)
        for (Message msg : history) {
            String role = msg.getSender().getName().equals(botName) ? "assistant" : "user";
            messages.add(Map.of("role", role, "content", msg.getContent()));
        }

        return callOpenAIForString(messages);
    }

    // Helper to call OpenAI expecting JSON (Profile generation)
    private Map<String, String> callOpenAIForJson(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "response_format", Map.of("type", "json_object")
            );

            String response = makeRequest(body);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // FIXED: Used TypeReference to enforce Map<String, String> type safety
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (Exception e) {
            // Fallback if AI fails
            return Map.of("name", "Alex Doe", "bio", "Music lover.");
        }
    }

    // Helper to call OpenAI expecting String (Chat)
    private String callOpenAIForString(List<Map<String, String>> messages) {
        try {
            Map<String, Object> body = Map.of(
                    "model", "gpt-3.5-turbo",
                    "messages", messages,
                    "max_tokens", 150
            );

            String response = makeRequest(body);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "Haha that's cool!";
        }
    }

    private String makeRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
        return restTemplate.postForObject(OPENAI_URL, entity, String.class);
    }
}