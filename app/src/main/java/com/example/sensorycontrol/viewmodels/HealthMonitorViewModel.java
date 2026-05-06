package com.example.sensorycontrol.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.sensorycontrol.ble.HealthMonitorBleManager;
import com.example.sensorycontrol.database.HealthRecordEntity;
import com.example.sensorycontrol.models.HealthReading;
import com.example.sensorycontrol.models.HealthStatus;
import com.example.sensorycontrol.repository.HealthDataRepository;

import java.util.List;

import timber.log.Timber;

/**
 * ViewModel for Health Monitoring
 * Manages BLE connection and health data using MVVM architecture
 */
public class HealthMonitorViewModel extends AndroidViewModel {
    
    private final HealthMonitorBleManager bleManager;
    private final HealthDataRepository repository;
    
    // LiveData for UI observation
    private final MutableLiveData<HealthReading> currentReading = new MutableLiveData<>();
    private final MutableLiveData<HealthStatus> healthStatus = new MutableLiveData<>();
    private final MutableLiveData<HealthMonitorBleManager.ConnectionState> connectionState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);
    private final MutableLiveData<String> deviceAddress = new MutableLiveData<>();
    
    // Individual vital LiveData for easier UI binding
    private final MutableLiveData<Integer> heartRate = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> spO2 = new MutableLiveData<>(0);
    private final MutableLiveData<Float> temperature = new MutableLiveData<>(0.0f);
    private final MutableLiveData<Boolean> hrValid = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> tempValid = new MutableLiveData<>(false);
    
    // Data storage control
    private long lastStoredTimestamp = 0;
    private static final long STORAGE_INTERVAL_MS = 10000; // Store every 10 seconds
    private HealthStatus.Condition lastStoredStatus = null;
    
    public HealthMonitorViewModel(@NonNull Application application) {
        super(application);
        
        bleManager = new HealthMonitorBleManager(application);
        bleManager.setBleCallback(bleCallback);
        
        // Initialize repository
        repository = new HealthDataRepository(application);
        
        // Initialize connection state
        connectionState.setValue(HealthMonitorBleManager.ConnectionState.DISCONNECTED);
        
        // Initialize health status
        healthStatus.setValue(new HealthStatus());
    }
    
    /**
     * BLE callback implementation
     */
    private final HealthMonitorBleManager.BleCallback bleCallback = new HealthMonitorBleManager.BleCallback() {
        @Override
        public void onDeviceFound(android.bluetooth.BluetoothDevice device, int rssi) {
            Timber.d("Device found: %s, RSSI: %d", device.getAddress(), rssi);
            deviceAddress.postValue(device.getAddress());
        }
        
        @Override
        public void onScanComplete(boolean deviceFound) {
            isScanning.postValue(false);
            if (!deviceFound) {
                errorMessage.postValue("Device not found. Make sure the wearable is powered on.");
            }
        }
        
        @Override
        public void onConnectionStateChanged(HealthMonitorBleManager.ConnectionState state) {
            connectionState.postValue(state);
            
            if (state == HealthMonitorBleManager.ConnectionState.SCANNING) {
                isScanning.postValue(true);
            } else if (state == HealthMonitorBleManager.ConnectionState.DISCONNECTED) {
                isScanning.postValue(false);
                // Reset values on disconnect
                resetValues();
            }
        }
        
        @Override
        public void onHealthDataReceived(HealthReading reading) {
            // Update current reading
            currentReading.postValue(reading);
            
            // Update individual vitals
            if (reading.isHrValid()) {
                heartRate.postValue(reading.getHeartRate());
                spO2.postValue(reading.getSpO2());
                hrValid.postValue(true);
            } else {
                hrValid.postValue(false);
            }
            
            if (reading.isTempValid()) {
                temperature.postValue(reading.getTemperature());
                tempValid.postValue(true);
            } else {
                tempValid.postValue(false);
            }
            
            // Evaluate and update health status
            HealthStatus status = HealthStatus.evaluate(reading);
            healthStatus.postValue(status);
            
            // Store data to database based on strategy
            storeHealthDataIfNeeded(reading, status);
            
            Timber.d("Health data updated: %s", reading.toString());
            Timber.d("Health status: %s", status.toString());
        }
        
        @Override
        public void onError(String error) {
            errorMessage.postValue(error);
            Timber.e("BLE Error: %s", error);
        }
    };
    
    /**
     * Start scanning for device
     */
    public void startScan() {
        if (!bleManager.isBluetoothEnabled()) {
            errorMessage.setValue("Bluetooth is not enabled");
            return;
        }
        
        bleManager.startScan();
    }
    
    /**
     * Stop scanning
     */
    public void stopScan() {
        bleManager.stopScan();
    }
    
    /**
     * Disconnect from device
     */
    public void disconnect() {
        bleManager.disconnect();
    }
    
    /**
     * Reset all values to default
     */
    private void resetValues() {
        heartRate.postValue(0);
        spO2.postValue(0);
        temperature.postValue(0.0f);
        hrValid.postValue(false);
        tempValid.postValue(false);
        currentReading.postValue(null);
        healthStatus.postValue(new HealthStatus());
    }
    
    /**
     * Clear error message
     */
    public void clearError() {
        errorMessage.setValue(null);
    }
    
    /**
     * Store health data to database based on strategy:
     * 1. Every 10-30 seconds (configurable)
     * 2. When health status changes
     * 
     * This prevents storing every BLE packet while ensuring critical data is captured.
     */
    private void storeHealthDataIfNeeded(HealthReading reading, HealthStatus status) {
        long currentTime = System.currentTimeMillis();
        HealthStatus.Condition currentStatus = status.getOverallCondition();
        
        // Store if:
        // 1. Enough time has passed since last storage
        // 2. Health status has changed (especially to WARNING or CRITICAL)
        boolean shouldStore = false;
        
        if (currentTime - lastStoredTimestamp >= STORAGE_INTERVAL_MS) {
            shouldStore = true;
            Timber.d("Storing data: Time interval reached");
        } else if (lastStoredStatus != currentStatus) {
            shouldStore = true;
            Timber.d("Storing data: Status changed from %s to %s", lastStoredStatus, currentStatus);
        }
        
        if (shouldStore && reading.isHrValid() && reading.isTempValid()) {
            repository.insertHealthRecord(reading, status);
            lastStoredTimestamp = currentTime;
            lastStoredStatus = currentStatus;
        }
    }
    
    // Repository methods for accessing historical data
    
    /**
     * Get all health records for the current user.
     */
    public LiveData<List<HealthRecordEntity>> getAllRecords() {
        return repository.getAllRecords();
    }
    
    /**
     * Get the last N health records.
     */
    public LiveData<List<HealthRecordEntity>> getLastNRecords(int limit) {
        return repository.getLastNRecords(limit);
    }
    
    /**
     * Get health records within a time range.
     */
    public LiveData<List<HealthRecordEntity>> getRecordsByTimeRange(long startTime, long endTime) {
        return repository.getRecordsByTimeRange(startTime, endTime);
    }
    
    /**
     * Get records with critical health status.
     */
    public LiveData<List<HealthRecordEntity>> getCriticalRecords() {
        return repository.getCriticalRecords();
    }
    
    /**
     * Get the total count of records.
     */
    public void getRecordCount(HealthDataRepository.CountCallback callback) {
        repository.getRecordCount(callback);
    }
    
    /**
     * Delete all records for the current user.
     */
    public void deleteAllRecords() {
        repository.deleteAllRecords();
    }
    
    /**
     * Delete records older than specified days.
     */
    public void deleteOldRecords(int days) {
        repository.deleteOldRecords(days);
    }
    
    /**
     * Get average vital signs over a time period.
     */
    public void getAverageVitals(long startTime, long endTime, 
                                 HealthDataRepository.AverageVitalsCallback callback) {
        repository.getAverageVitals(startTime, endTime, callback);
    }
    
    // LiveData getters for UI observation
    
    public LiveData<HealthReading> getCurrentReading() {
        return currentReading;
    }
    
    public LiveData<HealthStatus> getHealthStatus() {
        return healthStatus;
    }
    
    public LiveData<HealthMonitorBleManager.ConnectionState> getConnectionState() {
        return connectionState;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Boolean> getIsScanning() {
        return isScanning;
    }
    
    public LiveData<String> getDeviceAddress() {
        return deviceAddress;
    }
    
    public LiveData<Integer> getHeartRate() {
        return heartRate;
    }
    
    public LiveData<Integer> getSpO2() {
        return spO2;
    }
    
    public LiveData<Float> getTemperature() {
        return temperature;
    }
    
    public LiveData<Boolean> getHrValid() {
        return hrValid;
    }
    
    public LiveData<Boolean> getTempValid() {
        return tempValid;
    }
    
    /**
     * Check if currently connected
     */
    public boolean isConnected() {
        return bleManager.isConnected();
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    public boolean isBluetoothEnabled() {
        return bleManager.isBluetoothEnabled();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        bleManager.close();
        repository.shutdown();
        Timber.d("ViewModel cleared, BLE manager and repository closed");
    }
}
