package com.example.sensorycontrol.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.example.sensorycontrol.models.HealthReading;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * BLE Manager for Child Health Wearable Device
 * Handles scanning, connection, and data reception from Phase 3 Arduino device
 */
public class HealthMonitorBleManager {
    
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    
    private final Handler handler;
    private BleCallback bleCallback;
    
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private int reconnectAttempts = 0;
    private String lastConnectedDeviceAddress;
    private boolean isScanning = false;
    
    public enum ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    /**
     * Callback interface for BLE events
     */
    public interface BleCallback {
        void onDeviceFound(BluetoothDevice device, int rssi);
        void onScanComplete(boolean deviceFound);
        void onConnectionStateChanged(ConnectionState state);
        void onHealthDataReceived(HealthReading reading);
        void onError(String error);
    }
    
    public HealthMonitorBleManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }
    
    public void setBleCallback(BleCallback callback) {
        this.bleCallback = callback;
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    /**
     * Check if required permissions are granted
     */
    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED &&
                   ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    /**
     * Start scanning for ChildHealthWearable device
     */
    public void startScan() {
        if (!isBluetoothEnabled()) {
            notifyError("Bluetooth is not enabled");
            return;
        }
        
        if (!hasPermissions()) {
            notifyError("Missing Bluetooth permissions");
            return;
        }
        
        if (isScanning) {
            Timber.d("Scan already in progress");
            return;
        }
        
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        
        updateConnectionState(ConnectionState.SCANNING);
        isScanning = true;
        
        Timber.d("Starting scan for %s", HealthMonitorBleConstants.DEVICE_NAME);
        
        try {
            // Create scan filter for our device
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder()
                    .setDeviceName(HealthMonitorBleConstants.DEVICE_NAME)
                    .build();
            filters.add(filter);
            
            // Scan settings
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            
            // Stop scan after timeout
            handler.postDelayed(() -> {
                stopScan();
                if (bleCallback != null) {
                    bleCallback.onScanComplete(false);
                }
            }, HealthMonitorBleConstants.SCAN_PERIOD_MS);
            
        } catch (SecurityException e) {
            Timber.e(e, "Security exception during scan");
            notifyError("Permission denied for Bluetooth scan");
            isScanning = false;
            updateConnectionState(ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * Stop scanning
     */
    public void stopScan() {
        if (!isScanning) {
            return;
        }
        
        if (bluetoothLeScanner != null && hasPermissions()) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                Timber.d("Scan stopped");
            } catch (SecurityException e) {
                Timber.e(e, "Security exception stopping scan");
            }
        }
        
        isScanning = false;
        if (connectionState == ConnectionState.SCANNING) {
            updateConnectionState(ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * Scan callback
     */
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            
            Timber.d("Device found: %s, RSSI: %d", device.getAddress(), rssi);
            
            if (bleCallback != null) {
                bleCallback.onDeviceFound(device, rssi);
            }
            
            // Auto-connect to first device found
            stopScan();
            connect(device);
            
            if (bleCallback != null) {
                bleCallback.onScanComplete(true);
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            Timber.e("Scan failed with error code: %d", errorCode);
            notifyError("Scan failed: " + errorCode);
            isScanning = false;
            updateConnectionState(ConnectionState.DISCONNECTED);
        }
    };
    
    /**
     * Connect to device
     */
    public void connect(BluetoothDevice device) {
        if (device == null || !hasPermissions()) {
            notifyError("Cannot connect to device");
            return;
        }
        
        // Disconnect from any existing connection
        disconnect();
        
        lastConnectedDeviceAddress = device.getAddress();
        updateConnectionState(ConnectionState.CONNECTING);
        
        Timber.d("Connecting to device: %s", device.getAddress());
        
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        } catch (SecurityException e) {
            Timber.e(e, "Security exception during connection");
            notifyError("Permission denied for connection");
            updateConnectionState(ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * Disconnect from device
     */
    public void disconnect() {
        if (bluetoothGatt != null && hasPermissions()) {
            try {
                updateConnectionState(ConnectionState.DISCONNECTING);
                bluetoothGatt.disconnect();
            } catch (SecurityException e) {
                Timber.e(e, "Security exception during disconnect");
            }
        }
    }
    
    /**
     * Close GATT connection
     */
    public void close() {
        if (bluetoothGatt != null && hasPermissions()) {
            try {
                bluetoothGatt.close();
                bluetoothGatt = null;
                updateConnectionState(ConnectionState.DISCONNECTED);
            } catch (SecurityException e) {
                Timber.e(e, "Security exception during close");
            }
        }
    }
    
    /**
     * GATT callback for connection and data events
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (!hasPermissions()) return;
            
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.d("Connected to device");
                    updateConnectionState(ConnectionState.CONNECTED);
                    reconnectAttempts = 0;
                    
                    // Discover services after short delay
                    handler.postDelayed(() -> {
                        if (bluetoothGatt != null && hasPermissions()) {
                            try {
                                Timber.d("Discovering services...");
                                bluetoothGatt.discoverServices();
                            } catch (SecurityException e) {
                                Timber.e(e, "Security exception discovering services");
                            }
                        }
                    }, 600);
                    
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.d("Disconnected from device");
                    updateConnectionState(ConnectionState.DISCONNECTED);
                    
                    // Attempt reconnection if unexpected disconnect
                    if (status != BluetoothGatt.GATT_SUCCESS && 
                        reconnectAttempts < HealthMonitorBleConstants.MAX_RECONNECT_ATTEMPTS) {
                        handler.postDelayed(() -> reconnect(), 
                                HealthMonitorBleConstants.RECONNECT_DELAY_MS);
                    }
                }
            } catch (SecurityException e) {
                Timber.e(e, "Security exception in connection state change");
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Services discovered");
                
                // Find health service and enable notifications
                BluetoothGattService service = gatt.getService(HealthMonitorBleConstants.HEALTH_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = 
                            service.getCharacteristic(HealthMonitorBleConstants.SENSOR_DATA_CHAR_UUID);
                    if (characteristic != null) {
                        enableNotifications(characteristic);
                    } else {
                        Timber.w("Sensor data characteristic not found");
                        notifyError("Characteristic not found");
                    }
                } else {
                    Timber.w("Health service not found");
                    notifyError("Service not found");
                }
            } else {
                Timber.w("Service discovery failed: %d", status);
                notifyError("Service discovery failed");
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Received notification with sensor data
            byte[] data = characteristic.getValue();
            
            if (data != null && data.length == HealthMonitorBleConstants.PACKET_SIZE) {
                HealthReading reading = HealthReading.fromBytes(data);
                
                if (reading != null) {
                    Timber.d("Health data received: %s", reading.toString());
                    
                    if (bleCallback != null) {
                        handler.post(() -> bleCallback.onHealthDataReceived(reading));
                    }
                }
            } else {
                Timber.w("Invalid data packet received");
            }
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Notifications enabled successfully");
            } else {
                Timber.w("Failed to enable notifications: %d", status);
                notifyError("Failed to enable notifications");
            }
        }
    };
    
    /**
     * Enable notifications for sensor data characteristic
     */
    private void enableNotifications(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || !hasPermissions()) {
            return;
        }
        
        try {
            // Enable local notifications
            boolean success = bluetoothGatt.setCharacteristicNotification(characteristic, true);
            if (!success) {
                Timber.w("Failed to set characteristic notification");
                notifyError("Failed to enable notifications");
                return;
            }
            
            // Enable remote notifications via CCCD
            BluetoothGattDescriptor descriptor = 
                    characteristic.getDescriptor(HealthMonitorBleConstants.CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                bluetoothGatt.writeDescriptor(descriptor);
                Timber.d("Enabling notifications...");
            } else {
                Timber.w("CCCD descriptor not found");
                notifyError("Descriptor not found");
            }
            
        } catch (SecurityException e) {
            Timber.e(e, "Security exception enabling notifications");
            notifyError("Permission denied");
        }
    }
    
    /**
     * Attempt to reconnect to last device
     */
    private void reconnect() {
        if (lastConnectedDeviceAddress != null && 
            reconnectAttempts < HealthMonitorBleConstants.MAX_RECONNECT_ATTEMPTS) {
            
            reconnectAttempts++;
            Timber.d("Reconnecting (attempt %d)...", reconnectAttempts);
            
            if (hasPermissions()) {
                try {
                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastConnectedDeviceAddress);
                    connect(device);
                } catch (SecurityException e) {
                    Timber.e(e, "Security exception during reconnect");
                }
            }
        } else {
            Timber.d("Max reconnect attempts reached");
            notifyError("Connection lost");
        }
    }
    
    /**
     * Update connection state and notify callback
     */
    private void updateConnectionState(ConnectionState state) {
        this.connectionState = state;
        if (bleCallback != null) {
            handler.post(() -> bleCallback.onConnectionStateChanged(state));
        }
    }
    
    /**
     * Notify error via callback
     */
    private void notifyError(String error) {
        if (bleCallback != null) {
            handler.post(() -> bleCallback.onError(error));
        }
    }
    
    // Getters
    
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
    
    public boolean isScanning() {
        return isScanning;
    }
}
