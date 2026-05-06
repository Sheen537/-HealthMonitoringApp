package com.example.sensorycontrol.ble;

import java.util.UUID;

/**
 * BLE Constants for Child Health Wearable (Phase 3 Arduino Device)
 * 
 * This class defines the UUIDs and constants for communicating with
 * the ChildHealthWearable BLE device from Phase 3.
 */
public class HealthMonitorBleConstants {
    
    // Device identification
    public static final String DEVICE_NAME = "ChildHealthWearable";
    public static final String DEVICE_LOCAL_NAME = "Child Health Monitor";
    
    // Service UUID (from Phase 3 Arduino firmware)
    public static final UUID HEALTH_SERVICE_UUID = 
            UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214");
    
    // Sensor Data Characteristic UUID (from Phase 3 Arduino firmware)
    public static final UUID SENSOR_DATA_CHAR_UUID = 
            UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214");
    
    // Client Characteristic Configuration Descriptor (standard UUID)
    public static final UUID CCCD_UUID = 
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // Data packet format (6 bytes from Phase 3)
    public static final int PACKET_SIZE = 6;
    public static final int HR_LOW_BYTE = 0;
    public static final int HR_HIGH_BYTE = 1;
    public static final int SPO2_BYTE = 2;
    public static final int TEMP_LOW_BYTE = 3;
    public static final int TEMP_HIGH_BYTE = 4;
    public static final int FLAGS_BYTE = 5;
    
    // Validity flags (bit positions in FLAGS_BYTE)
    public static final int FLAG_HR_VALID = 0x01;   // Bit 0
    public static final int FLAG_TEMP_VALID = 0x02; // Bit 1
    
    // Connection parameters
    public static final int SCAN_PERIOD_MS = 10000;  // 10 seconds
    public static final int CONNECTION_TIMEOUT_MS = 5000;  // 5 seconds
    public static final int MAX_RECONNECT_ATTEMPTS = 3;
    public static final int RECONNECT_DELAY_MS = 2000;  // 2 seconds
    
    // Health thresholds (child-safe ranges)
    
    // Heart Rate (BPM)
    public static final int HR_MIN_CRITICAL = 40;
    public static final int HR_MIN_WARNING = 60;
    public static final int HR_MAX_GOOD = 120;
    public static final int HR_MAX_WARNING = 150;
    public static final int HR_MAX_CRITICAL = 200;
    
    // SpO2 (%)
    public static final int SPO2_MIN_CRITICAL = 90;
    public static final int SPO2_MIN_WARNING = 95;
    public static final int SPO2_MAX_GOOD = 100;
    
    // Temperature (°C)
    public static final float TEMP_MIN_GOOD = 36.0f;
    public static final float TEMP_MAX_GOOD = 37.5f;
    public static final float TEMP_MAX_WARNING = 38.4f;
    public static final float TEMP_MAX_CRITICAL = 42.0f;
    
    /**
     * Parse heart rate from BLE packet (little-endian uint16)
     */
    public static int parseHeartRate(byte[] data) {
        if (data == null || data.length < PACKET_SIZE) {
            return 0;
        }
        return ((data[HR_HIGH_BYTE] & 0xFF) << 8) | (data[HR_LOW_BYTE] & 0xFF);
    }
    
    /**
     * Parse SpO2 from BLE packet (uint8)
     */
    public static int parseSpO2(byte[] data) {
        if (data == null || data.length < PACKET_SIZE) {
            return 0;
        }
        return data[SPO2_BYTE] & 0xFF;
    }
    
    /**
     * Parse temperature from BLE packet (little-endian uint16, divide by 10)
     */
    public static float parseTemperature(byte[] data) {
        if (data == null || data.length < PACKET_SIZE) {
            return 0.0f;
        }
        int tempRaw = ((data[TEMP_HIGH_BYTE] & 0xFF) << 8) | (data[TEMP_LOW_BYTE] & 0xFF);
        return tempRaw / 10.0f;
    }
    
    /**
     * Check if heart rate is valid
     */
    public static boolean isHeartRateValid(byte[] data) {
        if (data == null || data.length < PACKET_SIZE) {
            return false;
        }
        return (data[FLAGS_BYTE] & FLAG_HR_VALID) != 0;
    }
    
    /**
     * Check if temperature is valid
     */
    public static boolean isTemperatureValid(byte[] data) {
        if (data == null || data.length < PACKET_SIZE) {
            return false;
        }
        return (data[FLAGS_BYTE] & FLAG_TEMP_VALID) != 0;
    }
}
