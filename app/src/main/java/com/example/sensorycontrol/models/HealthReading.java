package com.example.sensorycontrol.models;

/**
 * Model class representing a single health reading from the BLE device
 */
public class HealthReading {
    
    private int heartRate;        // BPM (0-200)
    private int spO2;             // Percentage (0-100)
    private float temperature;    // Celsius (30.0-42.0)
    private boolean hrValid;      // Heart rate validity flag
    private boolean tempValid;    // Temperature validity flag
    private long timestamp;       // System time when received
    
    public HealthReading() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public HealthReading(int heartRate, int spO2, float temperature, 
                         boolean hrValid, boolean tempValid) {
        this.heartRate = heartRate;
        this.spO2 = spO2;
        this.temperature = temperature;
        this.hrValid = hrValid;
        this.tempValid = tempValid;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public int getHeartRate() {
        return heartRate;
    }
    
    public int getSpO2() {
        return spO2;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public boolean isHrValid() {
        return hrValid;
    }
    
    public boolean isTempValid() {
        return tempValid;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    // Setters
    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }
    
    public void setSpO2(int spO2) {
        this.spO2 = spO2;
    }
    
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    public void setHrValid(boolean hrValid) {
        this.hrValid = hrValid;
    }
    
    public void setTempValid(boolean tempValid) {
        this.tempValid = tempValid;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Check if any data is valid
     */
    public boolean hasValidData() {
        return hrValid || tempValid;
    }
    
    /**
     * Get formatted string representation
     */
    @Override
    public String toString() {
        return String.format("HR: %d BPM [%s], SpO2: %d%% [%s], Temp: %.1f°C [%s]",
                heartRate, hrValid ? "✓" : "✗",
                spO2, hrValid ? "✓" : "✗",
                temperature, tempValid ? "✓" : "✗");
    }
    
    /**
     * Create from BLE packet data
     */
    public static HealthReading fromBytes(byte[] data) {
        if (data == null || data.length != 6) {
            return null;
        }
        
        // Parse heart rate (little-endian uint16)
        int heartRate = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
        
        // Parse SpO2 (uint8)
        int spO2 = data[2] & 0xFF;
        
        // Parse temperature (little-endian uint16, divide by 10)
        int tempRaw = ((data[4] & 0xFF) << 8) | (data[3] & 0xFF);
        float temperature = tempRaw / 10.0f;
        
        // Parse validity flags
        boolean hrValid = (data[5] & 0x01) != 0;
        boolean tempValid = (data[5] & 0x02) != 0;
        
        return new HealthReading(heartRate, spO2, temperature, hrValid, tempValid);
    }
}
