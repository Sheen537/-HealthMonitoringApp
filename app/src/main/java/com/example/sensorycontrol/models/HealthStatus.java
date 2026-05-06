package com.example.sensorycontrol.models;

import com.example.sensorycontrol.ble.HealthMonitorBleConstants;

/**
 * Health status evaluation based on vital signs
 * Implements the three-dot health indicator system
 */
public class HealthStatus {
    
    /**
     * Overall health condition levels
     */
    public enum Condition {
        GOOD,       // Green dot (solid)
        WARNING,    // Yellow dot (solid)
        CRITICAL    // Red dot (blinking)
    }
    
    /**
     * Individual vital status
     */
    public enum VitalStatus {
        GOOD,       // Within normal range
        WARNING,    // Outside normal but not critical
        CRITICAL    // Requires immediate attention
    }
    
    private VitalStatus heartRateStatus;
    private VitalStatus spO2Status;
    private VitalStatus temperatureStatus;
    private Condition overallCondition;
    
    public HealthStatus() {
        this.heartRateStatus = VitalStatus.GOOD;
        this.spO2Status = VitalStatus.GOOD;
        this.temperatureStatus = VitalStatus.GOOD;
        this.overallCondition = Condition.GOOD;
    }
    
    /**
     * Evaluate health status from a reading
     */
    public static HealthStatus evaluate(HealthReading reading) {
        HealthStatus status = new HealthStatus();
        
        if (reading == null || !reading.hasValidData()) {
            return status;
        }
        
        // Evaluate heart rate
        if (reading.isHrValid()) {
            status.heartRateStatus = evaluateHeartRate(reading.getHeartRate());
        }
        
        // Evaluate SpO2 (only valid if HR is valid)
        if (reading.isHrValid()) {
            status.spO2Status = evaluateSpO2(reading.getSpO2());
        }
        
        // Evaluate temperature
        if (reading.isTempValid()) {
            status.temperatureStatus = evaluateTemperature(reading.getTemperature());
        }
        
        // Determine overall condition
        status.overallCondition = determineOverallCondition(
                status.heartRateStatus,
                status.spO2Status,
                status.temperatureStatus
        );
        
        return status;
    }
    
    /**
     * Evaluate heart rate status
     * Green: 60-120 BPM
     * Yellow: 40-59 or 121-150 BPM
     * Red: <40 or >150 BPM
     */
    private static VitalStatus evaluateHeartRate(int heartRate) {
        if (heartRate < HealthMonitorBleConstants.HR_MIN_CRITICAL || 
            heartRate > HealthMonitorBleConstants.HR_MAX_WARNING) {
            return VitalStatus.CRITICAL;
        } else if (heartRate < HealthMonitorBleConstants.HR_MIN_WARNING || 
                   heartRate > HealthMonitorBleConstants.HR_MAX_GOOD) {
            return VitalStatus.WARNING;
        } else {
            return VitalStatus.GOOD;
        }
    }
    
    /**
     * Evaluate SpO2 status
     * Green: ≥95%
     * Yellow: 90-94%
     * Red: <90%
     */
    private static VitalStatus evaluateSpO2(int spO2) {
        if (spO2 < HealthMonitorBleConstants.SPO2_MIN_CRITICAL) {
            return VitalStatus.CRITICAL;
        } else if (spO2 < HealthMonitorBleConstants.SPO2_MIN_WARNING) {
            return VitalStatus.WARNING;
        } else {
            return VitalStatus.GOOD;
        }
    }
    
    /**
     * Evaluate temperature status
     * Green: 36.0-37.5°C
     * Yellow: 37.6-38.4°C
     * Red: ≥38.5°C
     */
    private static VitalStatus evaluateTemperature(float temperature) {
        if (temperature >= HealthMonitorBleConstants.TEMP_MAX_WARNING) {
            return VitalStatus.CRITICAL;
        } else if (temperature > HealthMonitorBleConstants.TEMP_MAX_GOOD) {
            return VitalStatus.WARNING;
        } else if (temperature >= HealthMonitorBleConstants.TEMP_MIN_GOOD) {
            return VitalStatus.GOOD;
        } else {
            return VitalStatus.WARNING;  // Below normal
        }
    }
    
    /**
     * Determine overall condition based on individual vitals
     * Rule: If any vital = Red → CRITICAL
     *       Else if any vital = Yellow → WARNING
     *       Else → GOOD
     */
    private static Condition determineOverallCondition(VitalStatus hr, VitalStatus spo2, VitalStatus temp) {
        if (hr == VitalStatus.CRITICAL || spo2 == VitalStatus.CRITICAL || temp == VitalStatus.CRITICAL) {
            return Condition.CRITICAL;
        } else if (hr == VitalStatus.WARNING || spo2 == VitalStatus.WARNING || temp == VitalStatus.WARNING) {
            return Condition.WARNING;
        } else {
            return Condition.GOOD;
        }
    }
    
    // Getters
    public VitalStatus getHeartRateStatus() {
        return heartRateStatus;
    }
    
    public VitalStatus getSpO2Status() {
        return spO2Status;
    }
    
    public VitalStatus getTemperatureStatus() {
        return temperatureStatus;
    }
    
    public Condition getOverallCondition() {
        return overallCondition;
    }
    
    /**
     * Check if status requires blinking animation (critical condition)
     */
    public boolean shouldBlink() {
        return overallCondition == Condition.CRITICAL;
    }
    
    /**
     * Get color resource ID for overall condition
     */
    public int getConditionColor() {
        switch (overallCondition) {
            case GOOD:
                return android.R.color.holo_green_dark;
            case WARNING:
                return android.R.color.holo_orange_dark;
            case CRITICAL:
                return android.R.color.holo_red_dark;
            default:
                return android.R.color.darker_gray;
        }
    }
    
    /**
     * Get status message
     */
    public String getStatusMessage() {
        switch (overallCondition) {
            case GOOD:
                return "All vitals normal";
            case WARNING:
                return "Attention needed";
            case CRITICAL:
                return "Critical condition!";
            default:
                return "Unknown";
        }
    }
    
    @Override
    public String toString() {
        return String.format("Health Status: %s (HR: %s, SpO2: %s, Temp: %s)",
                overallCondition, heartRateStatus, spO2Status, temperatureStatus);
    }
}
