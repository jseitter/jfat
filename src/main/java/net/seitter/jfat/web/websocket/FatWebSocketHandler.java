package net.seitter.jfat.web.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time communication with web clients
 */
public class FatWebSocketHandler {
    
    private final Map<Object, String> sessionSubscriptions = new ConcurrentHashMap<>();
    
    public void onConnect(Object session) {
        try {
            System.out.println("WebSocket connected: " + getSessionId(session));
            sendMessage(session, new WebSocketMessage("connected", "WebSocket connection established"));
        } catch (Exception e) {
            System.err.println("Error on WebSocket connect: " + e.getMessage());
        }
    }
    
    public void onMessage(Object session, String message) {
        try {
            System.out.println("WebSocket message received: " + message);
            
            // Parse simple JSON message
            WebSocketMessage wsMessage = parseMessage(message);
            
            switch (wsMessage.type) {
                case "subscribe_image":
                    handleSubscribeImage(session, String.valueOf(wsMessage.payload));
                    break;
                case "operation_request":
                    handleOperationRequest(session, String.valueOf(wsMessage.payload));
                    break;
                case "ping":
                    sendMessage(session, new WebSocketMessage("pong", "pong"));
                    break;
                default:
                    sendMessage(session, new WebSocketMessage("error", "Unknown message type: " + wsMessage.type));
                    break;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing WebSocket message: " + e.getMessage());
            sendMessage(session, new WebSocketMessage("error", "Failed to process message: " + e.getMessage()));
        }
    }
    
    public void onClose(Object session, int statusCode, String reason) {
        try {
            System.out.println("WebSocket disconnected: " + getSessionId(session) + " (code: " + statusCode + ", reason: " + reason + ")");
            sessionSubscriptions.remove(session);
        } catch (Exception e) {
            System.err.println("Error on WebSocket close: " + e.getMessage());
        }
    }
    
    public void onError(Object session, Throwable throwable) {
        System.err.println("WebSocket error for session " + getSessionId(session) + ": " + throwable.getMessage());
        throwable.printStackTrace();
    }
    
    private void handleSubscribeImage(Object session, String payload) {
        try {
            // Simple parsing to extract image name
            String imageName = payload.replace("\"", "").trim();
            sessionSubscriptions.put(session, imageName);
            
            sendMessage(session, new WebSocketMessage("subscribed", "Subscribed to image: " + imageName));
            
            // Send initial status
            sendMessage(session, new WebSocketMessage("image_status", 
                Map.of("imageName", imageName, "status", "ready")));
                
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("error", "Failed to subscribe to image: " + e.getMessage()));
        }
    }
    
    private void handleOperationRequest(Object session, String payload) {
        try {
            // Parse operation request
            // For now, just acknowledge
            sendMessage(session, new WebSocketMessage("operation_started", 
                Map.of("operation", "unknown", "status", "started")));
            
            // Simulate progress updates
            new Thread(() -> {
                try {
                    for (int i = 0; i <= 100; i += 20) {
                        Thread.sleep(500);
                        sendMessage(session, new WebSocketMessage("operation_progress", 
                            Map.of("progress", i, "message", "Processing... " + i + "%")));
                    }
                    sendMessage(session, new WebSocketMessage("operation_completed", 
                        Map.of("operation", "unknown", "status", "completed")));
                } catch (Exception e) {
                    System.err.println("Error in operation simulation: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            sendMessage(session, new WebSocketMessage("error", "Failed to process operation: " + e.getMessage()));
        }
    }
    
    public void broadcastFilesystemChange(String imageName, String path, String changeType) {
        WebSocketMessage message = new WebSocketMessage("filesystem_changed", 
            Map.of("imageName", imageName, "path", path, "change", changeType));
        
        sessionSubscriptions.entrySet().stream()
            .filter(entry -> imageName.equals(entry.getValue()))
            .forEach(entry -> sendMessage(entry.getKey(), message));
    }
    
    public void broadcastGraphUpdate(String imageName, String graphContent) {
        WebSocketMessage message = new WebSocketMessage("graph_updated", 
            Map.of("imageName", imageName, "content", graphContent));
        
        sessionSubscriptions.entrySet().stream()
            .filter(entry -> imageName.equals(entry.getValue()))
            .forEach(entry -> sendMessage(entry.getKey(), message));
    }
    
    private void sendMessage(Object session, WebSocketMessage message) {
        try {
            String json = serializeMessage(message);
            // Use reflection to send message since we can't import Javalin classes yet
            session.getClass().getMethod("send", String.class).invoke(session, json);
        } catch (Exception e) {
            System.err.println("Error sending WebSocket message: " + e.getMessage());
        }
    }
    
    private String getSessionId(Object session) {
        try {
            return (String) session.getClass().getMethod("sessionId").invoke(session);
        } catch (Exception e) {
            return session.toString();
        }
    }
    
    private WebSocketMessage parseMessage(String json) {
        // Simple JSON parsing for WebSocket messages
        WebSocketMessage message = new WebSocketMessage();
        
        // Remove brackets and quotes
        json = json.trim().replaceAll("[{}\"\\s]", "");
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length >= 2) {
                String key = keyValue[0];
                String value = String.join(":", java.util.Arrays.copyOfRange(keyValue, 1, keyValue.length));
                
                switch (key) {
                    case "type":
                        message.type = value;
                        break;
                    case "payload":
                        message.payload = value;
                        break;
                }
            }
        }
        
        return message;
    }
    
    private String serializeMessage(WebSocketMessage message) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"").append(message.type).append("\"");
        
        if (message.payload instanceof String) {
            json.append(",\"payload\":\"").append(message.payload).append("\"");
        } else if (message.payload instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) message.payload;
            json.append(",\"payload\":{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
        }
        
        json.append("}");
        return json.toString();
    }
    
    // Message classes
    public static class WebSocketMessage {
        public String type;
        public Object payload;
        
        public WebSocketMessage() {}
        
        public WebSocketMessage(String type, Object payload) {
            this.type = type;
            this.payload = payload;
        }
    }
} 