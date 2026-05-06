package com.example.sensorycontrol.ble;

import java.util.UUID;

/**
 * Constants for BLE communication with sensory devices
 */
public class BleConstants {
    
    // GATT Service UUID for Sensory Control
    public static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    
    // Characteristic UUIDs for different controls
    public static final UUID CHARACTERISTIC_POWER_UUID = 
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    
    public static final UUID CHARACTERISTIC_LED_UUID = 
            UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");
    
    public static final UUID CHARACTERISTIC_SOUND_UUID = 
            UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb");
    
    public static final UUID CHARACTERISTIC_VIBRATION_UUID = 
            UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb");
    
    // Client Characteristic Configuration Descriptor
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = 
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // Device name filter
    public static final String DEVICE_NAME_PREFIX = "SensoryDevice";
    
    // Connection parameters
    public static final int SCAN_PERIOD = 10000; // 10 seconds
    public static final int MAX_RECONNECT_ATTEMPTS = 5;
    public static final int RECONNECT_DELAY_MS = 2000;
    public static final int MAX_RECONNECT_DELAY_MS = 30000;
    
    // Safety parameters
    public static final int MIN_SAFE_RSSI = -80; // dBm
    public static final int RSSI_CHECK_INTERVAL = 2000; // 2 seconds
    
    // Command format
    // Power: "PWR:1" or "PWR:0"
    // LED: "LED:brightness,R,G,B" (e.g., "LED:80,255,100,50")
    // Sound: "SND:volume,tone" (e.g., "SND:50,440")
    // Vibration: "VIB:intensity" (e.g., "VIB:75")
    // Mode: "MODE:name" (e.g., "MODE:CALM")
    
    public static final String CMD_POWER = "PWR";
    public static final String CMD_LED = "LED";
    public static final String CMD_SOUND = "SND";
    public static final String CMD_VIBRATION = "VIB";
    public static final String CMD_MODE = "MODE";
    public static final String CMD_DELIMITER = ":";
    public static final String PARAM_DELIMITER = ",";
    
    /**
     * Build power command
     */
    public static String buildPowerCommand(boolean on) {
        return CMD_POWER + CMD_DELIMITER + (on ? "1" : "0");
    }
    
    /**
     * Build LED command
     */
    public static String buildLedCommand(int brightness, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return String.format("%s%s%d%s%d%s%d%s%d", 
            CMD_LED, CMD_DELIMITER, brightness, PARAM_DELIMITER, r, PARAM_DELIMITER, g, PARAM_DELIMITER, b);
    }
    
    /**
     * Build sound command
     */
    public static String buildSoundCommand(int volume, int tone) {
        return String.format("%s%s%d%s%d", 
            CMD_SOUND, CMD_DELIMITER, volume, PARAM_DELIMITER, tone);
    }
    
    /**
     * Build vibration command
     */
    public static String buildVibrationCommand(int intensity) {
        return CMD_VIBRATION + CMD_DELIMITER + intensity;
    }
    
    /**
     * Build mode command
     */
    public static String buildModeCommand(String modeName) {
        return CMD_MODE + CMD_DELIMITER + modeName.toUpperCase();
    }
}
