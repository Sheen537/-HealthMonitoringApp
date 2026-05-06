package com.example.sensorycontrol.models;

/**
 * Model representing the current state of the sensory device
 */
public class DeviceState {
    
    private boolean powerOn;
    private int ledBrightness;      // 0-100
    private int ledColor;           // RGB color value
    private int soundVolume;        // 0-100
    private int soundTone;          // Frequency in Hz
    private int vibrationIntensity; // 0-100
    private String currentMode;     // Therapy mode name
    private int rssi;               // Signal strength
    private long lastUpdateTime;
    
    public DeviceState() {
        this.powerOn = false;
        this.ledBrightness = 50;
        this.ledColor = 0xFFFFFF; // White
        this.soundVolume = 30;
        this.soundTone = 440; // A4 note
        this.vibrationIntensity = 0;
        this.currentMode = "None";
        this.rssi = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    // Getters and Setters
    public boolean isPowerOn() {
        return powerOn;
    }
    
    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
    }
    
    public int getLedBrightness() {
        return ledBrightness;
    }
    
    public void setLedBrightness(int ledBrightness) {
        this.ledBrightness = Math.max(0, Math.min(100, ledBrightness));
    }
    
    public int getLedColor() {
        return ledColor;
    }
    
    public void setLedColor(int ledColor) {
        this.ledColor = ledColor;
    }
    
    public int getSoundVolume() {
        return soundVolume;
    }
    
    public void setSoundVolume(int soundVolume) {
        this.soundVolume = Math.max(0, Math.min(100, soundVolume));
    }
    
    public int getSoundTone() {
        return soundTone;
    }
    
    public void setSoundTone(int soundTone) {
        this.soundTone = soundTone;
    }
    
    public int getVibrationIntensity() {
        return vibrationIntensity;
    }
    
    public void setVibrationIntensity(int vibrationIntensity) {
        this.vibrationIntensity = Math.max(0, Math.min(100, vibrationIntensity));
    }
    
    public String getCurrentMode() {
        return currentMode;
    }
    
    public void setCurrentMode(String currentMode) {
        this.currentMode = currentMode;
    }
    
    public int getRssi() {
        return rssi;
    }
    
    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    /**
     * Check if device is within safe distance based on RSSI
     * @param minRssi Minimum acceptable RSSI (e.g., -80)
     * @return true if within safe distance
     */
    public boolean isWithinSafeDistance(int minRssi) {
        return rssi >= minRssi;
    }
    
    @Override
    public String toString() {
        return "DeviceState{" +
                "powerOn=" + powerOn +
                ", ledBrightness=" + ledBrightness +
                ", soundVolume=" + soundVolume +
                ", vibrationIntensity=" + vibrationIntensity +
                ", currentMode='" + currentMode + '\'' +
                ", rssi=" + rssi +
                '}';
    }
}
