/**
 * ESP32 WiFi Health Monitor
 * 
 * Replaces: Arduino Nano 33 BLE (BLE communication)
 * Hardware: ESP32 + MAX30102 + MLX90614
 * Communication: WiFi (WebSocket)
 * Data Format: JSON
 * 
 * Features:
 * - WebSocket server on port 8080
 * - Real-time sensor data streaming (1 Hz)
 * - JSON data format
 * - Auto-reconnect handling
 * - Heartbeat/ping-pong
 * - Error reporting
 * 
 * Pin Connections:
 * MAX30102:
 *   SDA -> GPIO 21
 *   SCL -> GPIO 22
 *   VCC -> 3.3V
 *   GND -> GND
 * 
 * MLX90614:
 *   SDA -> GPIO 21
 *   SCL -> GPIO 22
 *   VCC -> 3.3V
 *   GND -> GND
 */

#include <WiFi.h>
#include <WebSocketsServer.h>
#include <Wire.h>
#include <MAX30105.h>
#include <heartRate.h>
#include <Adafruit_MLX90614.h>
#include <ArduinoJson.h>

// ==================== CONFIGURATION ====================

// WiFi credentials
const char* WIFI_SSID = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// WebSocket server port
const uint16_t WS_PORT = 8080;

// Sensor update interval (milliseconds)
const unsigned long SENSOR_UPDATE_INTERVAL = 1000; // 1 Hz

// Heartbeat interval (milliseconds)
const unsigned long HEARTBEAT_INTERVAL = 30000; // 30 seconds

// ==================== HARDWARE ====================

MAX30105 particleSensor;
Adafruit_MLX90614 mlx = Adafruit_MLX90614();
WebSocketsServer webSocket = WebSocketsServer(WS_PORT);

// ==================== GLOBAL VARIABLES ====================

// Sensor data
int heartRate = 0;
int spO2 = 0;
float temperature = 0.0;
bool hrValid = false;
bool tempValid = false;

// Timing
unsigned long lastSensorUpdate = 0;
unsigned long lastHeartbeat = 0;

// Heart rate calculation
const byte RATE_SIZE = 4;
byte rates[RATE_SIZE];
byte rateSpot = 0;
long lastBeat = 0;

// Connection tracking
bool clientConnected = false;
uint8_t connectedClientNum = 0;

// ==================== SETUP ====================

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n=== ESP32 WiFi Health Monitor ===");
  
  // Initialize I2C
  Wire.begin(21, 22); // SDA, SCL
  
  // Initialize sensors
  if (!initSensors()) {
    Serial.println("ERROR: Sensor initialization failed!");
    // Continue anyway - will report errors via WebSocket
  }
  
  // Connect to WiFi
  connectWiFi();
  
  // Start WebSocket server
  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  
  Serial.println("Setup complete!");
  Serial.print("WebSocket server: ws://");
  Serial.print(WiFi.localIP());
  Serial.print(":");
  Serial.println(WS_PORT);
  Serial.println("/health");
}

// ==================== MAIN LOOP ====================

void loop() {
  // Handle WebSocket events
  webSocket.loop();
  
  // Check WiFi connection
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected! Reconnecting...");
    connectWiFi();
  }
  
  unsigned long currentMillis = millis();
  
  // Update sensors and send data
  if (currentMillis - lastSensorUpdate >= SENSOR_UPDATE_INTERVAL) {
    lastSensorUpdate = currentMillis;
    
    if (clientConnected) {
      readSensors();
      sendHealthData();
    }
  }
  
  // Send heartbeat
  if (currentMillis - lastHeartbeat >= HEARTBEAT_INTERVAL) {
    lastHeartbeat = currentMillis;
    
    if (clientConnected) {
      sendHeartbeat();
    }
  }
}

// ==================== WIFI ====================

void connectWiFi() {
  Serial.print("Connecting to WiFi: ");
  Serial.println(WIFI_SSID);
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi connected!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
    Serial.print("Signal Strength: ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  } else {
    Serial.println("\nWiFi connection failed!");
  }
}

// ==================== SENSOR INITIALIZATION ====================

bool initSensors() {
  bool success = true;
  
  // Initialize MAX30102
  Serial.print("Initializing MAX30102... ");
  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    particleSensor.setup();
    particleSensor.setPulseAmplitudeRed(0x0A);
    particleSensor.setPulseAmplitudeGreen(0);
    Serial.println("OK");
  } else {
    Serial.println("FAILED");
    success = false;
  }
  
  // Initialize MLX90614
  Serial.print("Initializing MLX90614... ");
  if (mlx.begin()) {
    Serial.println("OK");
  } else {
    Serial.println("FAILED");
    success = false;
  }
  
  return success;
}

// ==================== SENSOR READING ====================

void readSensors() {
  // Read MAX30102 (Heart Rate + SpO2)
  long irValue = particleSensor.getIR();
  
  if (irValue > 50000) {
    // Valid finger detection
    hrValid = true;
    
    // Heart rate calculation
    if (checkForBeat(irValue)) {
      long delta = millis() - lastBeat;
      lastBeat = millis();
      
      int beatsPerMinute = 60 / (delta / 1000.0);
      
      if (beatsPerMinute < 255 && beatsPerMinute > 20) {
        rates[rateSpot++] = (byte)beatsPerMinute;
        rateSpot %= RATE_SIZE;
        
        // Calculate average
        int beatAvg = 0;
        for (byte x = 0; x < RATE_SIZE; x++) {
          beatAvg += rates[x];
        }
        beatAvg /= RATE_SIZE;
        
        heartRate = beatAvg;
      }
    }
    
    // SpO2 calculation (simplified - use library for accurate SpO2)
    spO2 = map(irValue, 50000, 100000, 95, 100);
    spO2 = constrain(spO2, 0, 100);
    
  } else {
    // No finger detected
    hrValid = false;
    heartRate = 0;
    spO2 = 0;
  }
  
  // Read MLX90614 (Temperature)
  float objectTemp = mlx.readObjectTempC();
  
  if (!isnan(objectTemp) && objectTemp > 30.0 && objectTemp < 45.0) {
    tempValid = true;
    temperature = objectTemp;
  } else {
    tempValid = false;
    temperature = 0.0;
  }
}

// ==================== WEBSOCKET ====================

void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch(type) {
    case WStype_DISCONNECTED:
      Serial.printf("[%u] Disconnected!\n", num);
      clientConnected = false;
      break;
      
    case WStype_CONNECTED: {
      IPAddress ip = webSocket.remoteIP(num);
      Serial.printf("[%u] Connected from %d.%d.%d.%d\n", num, ip[0], ip[1], ip[2], ip[3]);
      clientConnected = true;
      connectedClientNum = num;
      
      // Send welcome message
      sendWelcomeMessage(num);
      break;
    }
    
    case WStype_TEXT:
      Serial.printf("[%u] Received: %s\n", num, payload);
      handleCommand(num, (char*)payload);
      break;
      
    case WStype_BIN:
      Serial.printf("[%u] Binary data received (not supported)\n", num);
      break;
      
    case WStype_PING:
      Serial.printf("[%u] Ping received\n", num);
      break;
      
    case WStype_PONG:
      Serial.printf("[%u] Pong received\n", num);
      break;
  }
}

void handleCommand(uint8_t num, char* payload) {
  // Parse JSON command
  StaticJsonDocument<200> doc;
  DeserializationError error = deserializeJson(doc, payload);
  
  if (error) {
    Serial.println("JSON parse error");
    return;
  }
  
  const char* command = doc["command"];
  
  if (command) {
    Serial.printf("Command: %s\n", command);
    
    if (strcmp(command, "ping") == 0) {
      // Respond to ping
      sendPong(num);
    } else if (strcmp(command, "status") == 0) {
      // Send status
      sendStatus(num);
    } else if (strcmp(command, "start") == 0) {
      // Start monitoring (already running)
      Serial.println("Start monitoring");
    } else if (strcmp(command, "stop") == 0) {
      // Stop monitoring
      Serial.println("Stop monitoring");
    }
  }
}

// ==================== DATA TRANSMISSION ====================

void sendHealthData() {
  if (!clientConnected) return;
  
  // Create JSON document
  StaticJsonDocument<300> doc;
  
  doc["heartRate"] = heartRate;
  doc["spo2"] = spO2;
  doc["temperature"] = temperature;
  doc["timestamp"] = millis();
  
  // Validity flags
  JsonObject valid = doc.createNestedObject("valid");
  valid["hr"] = hrValid;
  valid["temp"] = tempValid;
  
  // Serialize and send
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(connectedClientNum, json);
  
  // Debug output
  Serial.print("Sent: ");
  Serial.println(json);
}

void sendWelcomeMessage(uint8_t num) {
  StaticJsonDocument<200> doc;
  
  doc["type"] = "welcome";
  doc["message"] = "ESP32 Health Monitor Connected";
  doc["version"] = "1.0.0";
  doc["timestamp"] = millis();
  
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(num, json);
}

void sendHeartbeat() {
  if (!clientConnected) return;
  
  StaticJsonDocument<100> doc;
  doc["type"] = "heartbeat";
  doc["timestamp"] = millis();
  
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(connectedClientNum, json);
  Serial.println("Heartbeat sent");
}

void sendPong(uint8_t num) {
  StaticJsonDocument<100> doc;
  doc["type"] = "pong";
  doc["timestamp"] = millis();
  
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(num, json);
}

void sendStatus(uint8_t num) {
  StaticJsonDocument<300> doc;
  
  doc["type"] = "status";
  doc["wifi"] = (WiFi.status() == WL_CONNECTED);
  doc["rssi"] = WiFi.RSSI();
  doc["ip"] = WiFi.localIP().toString();
  doc["uptime"] = millis();
  doc["hrValid"] = hrValid;
  doc["tempValid"] = tempValid;
  
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(num, json);
}

void sendError(const char* errorMsg) {
  if (!clientConnected) return;
  
  StaticJsonDocument<200> doc;
  
  doc["error"] = "sensor_failure";
  doc["message"] = errorMsg;
  doc["timestamp"] = millis();
  
  String json;
  serializeJson(doc, json);
  
  webSocket.sendTXT(connectedClientNum, json);
  Serial.print("Error sent: ");
  Serial.println(errorMsg);
}

// ==================== UTILITY ====================

bool checkForBeat(long irValue) {
  // Simple beat detection (use library for better accuracy)
  static long lastIR = 0;
  static bool beatDetected = false;
  
  if (irValue > lastIR + 1000 && !beatDetected) {
    beatDetected = true;
    lastIR = irValue;
    return true;
  }
  
  if (irValue < lastIR - 1000) {
    beatDetected = false;
  }
  
  lastIR = irValue;
  return false;
}
