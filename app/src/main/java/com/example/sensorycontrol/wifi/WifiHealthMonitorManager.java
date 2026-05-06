package com.example.sensorycontrol.wifi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.sensorycontrol.models.HealthReading;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import timber.log.Timber;

/**
 * WiFi Manager for Child Health Wearable Device
 * Handles WebSocket connection and real-time data reception from ESP32 device
 * 
 * Replaces: HealthMonitorBleManager (BLE communication)
 * Protocol: WebSocket over WiFi
 * Data Format: JSON
 */
public class WifiHealthMonitorManager {
    
    private final Context context;
    private final Handler handler;
    private WifiCallback wifiCallback;
    
    private OkHttpClient client;
    private WebSocket webSocket;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY_MS = 3000;
    private static final long HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds
    
    private String deviceUrl;
    private boolean isReconnecting = false;
    
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (connectionState == ConnectionState.CONNECTED && webSocket != null) {
                sendHeartbeat();
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };
    
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }
    
    /**
     * Callback interface for WiFi events
     */
    public interface WifiCallback {
        void onConnectionStateChanged(ConnectionState state);
        void onHealthDataReceived(HealthReading reading);
        void onError(String error);
    }
    
    public WifiHealthMonitorManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        
        // Initialize OkHttp client with timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Keep-alive ping
                .build();
    }
    
    public void setWifiCallback(WifiCallback callback) {
        this.wifiCallback = callback;
    }
    
    /**
     * Connect to ESP32 device via WebSocket
     * 
     * @param ipAddress ESP32 IP address (e.g., "192.168.1.100")
     * @param port WebSocket port (default: 8080)
     */
    public void connect(String ipAddress, int port) {
        if (connectionState == ConnectionState.CONNECTED || 
            connectionState == ConnectionState.CONNECTING) {
            Timber.d("Already connected or connecting");
            return;
        }
        
        this.deviceUrl = String.format("ws://%s:%d/health", ipAddress, port);
        
        updateConnectionState(ConnectionState.CONNECTING);
        Timber.d("Connecting to device: %s", deviceUrl);
        
        Request request = new Request.Builder()
                .url(deviceUrl)
                .build();
        
        webSocket = client.newWebSocket(request, webSocketListener);
    }
    
    /**
     * Connect with default port (8080)
     */
    public void connect(String ipAddress) {
        connect(ipAddress, 8080);
    }
    
    /**
     * Disconnect from device
     */
    public void disconnect() {
        if (webSocket != null) {
            updateConnectionState(ConnectionState.DISCONNECTING);
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        
        stopHeartbeat();
        isReconnecting = false;
        reconnectAttempts = 0;
        updateConnectionState(ConnectionState.DISCONNECTED);
    }
    
    /**
     * Send control command to device
     * 
     * @param command Command type ("start", "stop", "status")
     */
    public void sendCommand(String command) {
        if (webSocket == null || connectionState != ConnectionState.CONNECTED) {
            Timber.w("Cannot send command: Not connected");
            return;
        }
        
        try {
            JSONObject json = new JSONObject();
            json.put("command", command);
            json.put("timestamp", System.currentTimeMillis());
            
            boolean sent = webSocket.send(json.toString());
            if (sent) {
                Timber.d("Command sent: %s", command);
            } else {
                Timber.w("Failed to send command: %s", command);
            }
        } catch (JSONException e) {
            Timber.e(e, "Error creating command JSON");
        }
    }
    
    /**
     * Send heartbeat to keep connection alive
     */
    private void sendHeartbeat() {
        if (webSocket != null && connectionState == ConnectionState.CONNECTED) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "ping");
                json.put("timestamp", System.currentTimeMillis());
                webSocket.send(json.toString());
                Timber.d("Heartbeat sent");
            } catch (JSONException e) {
                Timber.e(e, "Error sending heartbeat");
            }
        }
    }
    
    /**
     * Start heartbeat mechanism
     */
    private void startHeartbeat() {
        stopHeartbeat();
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);
        Timber.d("Heartbeat started");
    }
    
    /**
     * Stop heartbeat mechanism
     */
    private void stopHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable);
        Timber.d("Heartbeat stopped");
    }
    
    /**
     * Attempt to reconnect to device
     */
    private void reconnect() {
        if (isReconnecting || deviceUrl == null) {
            return;
        }
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.d("Max reconnect attempts reached");
            notifyError("Connection lost. Max reconnect attempts reached.");
            updateConnectionState(ConnectionState.ERROR);
            return;
        }
        
        isReconnecting = true;
        reconnectAttempts++;
        
        Timber.d("Reconnecting (attempt %d/%d)...", reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
        
        handler.postDelayed(() -> {
            isReconnecting = false;
            
            Request request = new Request.Builder()
                    .url(deviceUrl)
                    .build();
            
            webSocket = client.newWebSocket(request, webSocketListener);
        }, RECONNECT_DELAY_MS * reconnectAttempts); // Exponential backoff
    }
    
    /**
     * WebSocket listener for connection and data events
     */
    private final WebSocketListener webSocketListener = new WebSocketListener() {
        
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Timber.d("WebSocket connected");
            updateConnectionState(ConnectionState.CONNECTED);
            reconnectAttempts = 0;
            isReconnecting = false;
            startHeartbeat();
        }
        
        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Timber.d("Message received: %s", text);
            
            try {
                JSONObject json = new JSONObject(text);
                
                // Check if it's a pong response
                if (json.has("type") && "pong".equals(json.getString("type"))) {
                    Timber.d("Pong received");
                    return;
                }
                
                // Check if it's an error message
                if (json.has("error")) {
                    String error = json.getString("message");
                    Timber.e("Device error: %s", error);
                    notifyError("Device error: " + error);
                    return;
                }
                
                // Parse health data
                HealthReading reading = parseHealthData(json);
                if (reading != null && wifiCallback != null) {
                    handler.post(() -> wifiCallback.onHealthDataReceived(reading));
                }
                
            } catch (JSONException e) {
                Timber.e(e, "Error parsing JSON message");
                notifyError("Invalid data format from device");
            }
        }
        
        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Timber.d("Binary message received (not supported)");
        }
        
        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Timber.d("WebSocket closing: %d %s", code, reason);
            webSocket.close(1000, null);
            stopHeartbeat();
        }
        
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Timber.d("WebSocket closed: %d %s", code, reason);
            updateConnectionState(ConnectionState.DISCONNECTED);
            stopHeartbeat();
            
            // Attempt reconnection if unexpected close
            if (code != 1000 && !isReconnecting) {
                reconnect();
            }
        }
        
        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Timber.e(t, "WebSocket failure");
            updateConnectionState(ConnectionState.ERROR);
            stopHeartbeat();
            
            String errorMsg = t.getMessage() != null ? t.getMessage() : "Connection failed";
            notifyError(errorMsg);
            
            // Attempt reconnection
            if (!isReconnecting) {
                reconnect();
            }
        }
    };
    
    /**
     * Parse health data from JSON
     * 
     * Expected format:
     * {
     *   "heartRate": 75,
     *   "spo2": 98,
     *   "temperature": 36.7,
     *   "timestamp": 1710000000,
     *   "valid": {
     *     "hr": true,
     *     "temp": true
     *   }
     * }
     */
    private HealthReading parseHealthData(JSONObject json) {
        try {
            int heartRate = json.optInt("heartRate", 0);
            int spo2 = json.optInt("spo2", 0);
            double temperature = json.optDouble("temperature", 0.0);
            
            // Parse validity flags
            boolean hrValid = true;
            boolean tempValid = true;
            
            if (json.has("valid")) {
                JSONObject valid = json.getJSONObject("valid");
                hrValid = valid.optBoolean("hr", true);
                tempValid = valid.optBoolean("temp", true);
            }
            
            HealthReading reading = new HealthReading(
                heartRate,
                spo2,
                (float) temperature,
                hrValid,
                tempValid
            );
            
            // Set timestamp from device if available
            if (json.has("timestamp")) {
                reading.setTimestamp(json.getLong("timestamp"));
            }
            
            Timber.d("Health data parsed: %s", reading.toString());
            return reading;
            
        } catch (JSONException e) {
            Timber.e(e, "Error parsing health data");
            return null;
        }
    }
    
    /**
     * Update connection state and notify callback
     */
    private void updateConnectionState(ConnectionState state) {
        this.connectionState = state;
        if (wifiCallback != null) {
            handler.post(() -> wifiCallback.onConnectionStateChanged(state));
        }
    }
    
    /**
     * Notify error via callback
     */
    private void notifyError(String error) {
        if (wifiCallback != null) {
            handler.post(() -> wifiCallback.onError(error));
        }
    }
    
    /**
     * Close and cleanup resources
     */
    public void close() {
        disconnect();
        
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
        
        Timber.d("WiFi manager closed");
    }
    
    // Getters
    
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
    
    public String getDeviceUrl() {
        return deviceUrl;
    }
}
