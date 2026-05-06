package com.example.sensorycontrol.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room Entity representing a single health monitoring record.
 * Stores vital signs data locally for offline access and historical analysis.
 */
@Entity(tableName = "health_records")
public class HealthRecordEntity {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @ColumnInfo(name = "timestamp")
    private long timestamp;
    
    @ColumnInfo(name = "heart_rate")
    private int heartRate;
    
    @ColumnInfo(name = "spo2")
    private int spO2;
    
    @ColumnInfo(name = "temperature")
    private double temperature;
    
    @ColumnInfo(name = "health_status")
    private String healthStatus; // "GOOD", "WARNING", "CRITICAL"
    
    @ColumnInfo(name = "user_id")
    private String userId; // Firebase user ID for multi-user support
    
    // Constructor
    public HealthRecordEntity(long timestamp, int heartRate, int spO2, 
                             double temperature, String healthStatus, String userId) {
        this.timestamp = timestamp;
        this.heartRate = heartRate;
        this.spO2 = spO2;
        this.temperature = temperature;
        this.healthStatus = healthStatus;
        this.userId = userId;
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getHeartRate() {
        return heartRate;
    }
    
    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }
    
    public int getSpO2() {
        return spO2;
    }
    
    public void setSpO2(int spO2) {
        this.spO2 = spO2;
    }
    
    public double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
    
    public String getHealthStatus() {
        return healthStatus;
    }
    
    public void setHealthStatus(String healthStatus) {
        this.healthStatus = healthStatus;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    @Override
    public String toString() {
        return "HealthRecord{" +
                "id=" + id +
                ", timestamp=" + timestamp +
                ", heartRate=" + heartRate +
                ", spO2=" + spO2 +
                ", temperature=" + temperature +
                ", healthStatus='" + healthStatus + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }
}
