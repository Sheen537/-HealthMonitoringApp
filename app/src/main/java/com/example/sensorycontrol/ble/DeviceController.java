package com.example.sensorycontrol.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.example.sensorycontrol.models.DeviceState;
import com.example.sensorycontrol.models.TherapyMode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.LinkedList;
import java.util.Queue;

import timber.log.Timber;

/**
 * Controller for sending commands to sensory devices via BLE
 */
public class DeviceController {
    
    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    
    private final Handler handler;
    private DeviceCallback deviceCallback;
    
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private int reconnectAttempts = 0;
    private String lastConnectedDeviceAddress;
    
    private final Queue<Runnable> commandQueue = new LinkedList<>();
    private boolean isCommandInProgress = false;
    
    private final DeviceState currentState = new DeviceState();
    
    // RSSI monitoring
    private final Handler rssiHandler = new Handler(Looper.getMainLooper());
    private final Runnable rssiCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (bluetoothGatt != null && connectionState == ConnectionState.CONNECTED) {
                readRssi();
                rssiHandler.postDelayed(this, BleConstants.RSSI_CHECK_INTERVAL);
            }
        }
    };
    
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    public interface DeviceCallback {
        void onDeviceFound(BluetoothDevice device, int rssi);
        void onScanComplete();
        void onConnectionStateChanged(ConnectionState state);
        void onDeviceStateUpdated(DeviceState state);
        void onDistanceWarning(int rssi);
        void onError(String error);
    }
    
    public DeviceController(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        
        if (bluetoothAdapter != null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }
    
    public void setDeviceCallback(DeviceCallback callback) {
        this.deviceCallback = callback;
    }
    
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    /**
     * Start scanning for devices
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
        
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        
        Timber.d("Starting device scan");
        
        try {
            bluetoothLeScanner.startScan(scanCallback);
            
            handler.postDelayed(() -> {
                stopScan();
                if (deviceCallback != null) {
                    deviceCallback.onScanComplete();
                }
            }, BleConstants.SCAN_PERIOD);
            
        } catch (SecurityException e) {
            Timber.e(e, "Security exception during scan");
            notifyError("Permission denied for Bluetooth scan");
        }
    }
    
    public void stopScan() {
        if (bluetoothLeScanner != null && hasPermissions()) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                Timber.d("Scan stopped");
            } catch (SecurityException e) {
                Timber.e(e, "Security exception stopping scan");
            }
        }
    }
    
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            
            if (deviceCallback != null) {
                deviceCallback.onDeviceFound(device, rssi);
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            Timber.e("Scan failed with error code: %d", errorCode);
            notifyError("Scan failed: " + errorCode);
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
    
    public void disconnect() {
        rssiHandler.removeCallbacks(rssiCheckRunnable);
        
        if (bluetoothGatt != null && hasPermissions()) {
            try {
                updateConnectionState(ConnectionState.DISCONNECTING);
                bluetoothGatt.disconnect();
            } catch (SecurityException e) {
                Timber.e(e, "Security exception during disconnect");
            }
        }
    }
    
    public void close() {
        rssiHandler.removeCallbacks(rssiCheckRunnable);
        
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
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (!hasPermissions()) return;
            
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.d("Connected to device");
                    updateConnectionState(ConnectionState.CONNECTED);
                    reconnectAttempts = 0;
                    
                    handler.postDelayed(() -> {
                        if (bluetoothGatt != null && hasPermissions()) {
                            try {
                                bluetoothGatt.discoverServices();
                            } catch (SecurityException e) {
                                Timber.e(e, "Security exception discovering services");
                            }
                        }
                    }, 600);
                    
                    // Start RSSI monitoring
                    rssiHandler.postDelayed(rssiCheckRunnable, BleConstants.RSSI_CHECK_INTERVAL);
                    
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.d("Disconnected from device");
                    updateConnectionState(ConnectionState.DISCONNECTED);
                    rssiHandler.removeCallbacks(rssiCheckRunnable);
                    
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        handler.postDelayed(() -> reconnect(), 1000);
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
            } else {
                Timber.w("Service discovery failed: %d", status);
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Command sent successfully");
            } else {
                Timber.w("Command failed: %d", status);
            }
            completeCommand();
        }
        
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentState.setRssi(rssi);
                
                // Check if within safe distance
                if (!currentState.isWithinSafeDistance(BleConstants.MIN_SAFE_RSSI)) {
                    Timber.w("Device too far! RSSI: %d", rssi);
                    if (deviceCallback != null) {
                        deviceCallback.onDistanceWarning(rssi);
                    }
                    // Auto power off for safety
                    sendPowerCommand(false);
                }
            }
        }
    };
    
    /**
     * Read RSSI (signal strength)
     */
    private void readRssi() {
        if (bluetoothGatt != null && hasPermissions()) {
            try {
                bluetoothGatt.readRemoteRssi();
            } catch (SecurityException e) {
                Timber.e(e, "Security exception reading RSSI");
            }
        }
    }
    
    // Command sending methods
    
    public void sendPowerCommand(boolean on) {
        String command = BleConstants.buildPowerCommand(on);
        sendCommand(BleConstants.CHARACTERISTIC_POWER_UUID, command);
        currentState.setPowerOn(on);
        notifyStateUpdate();
    }
    
    public void sendLedCommand(int brightness, int color) {
        String command = BleConstants.buildLedCommand(brightness, color);
        sendCommand(BleConstants.CHARACTERISTIC_LED_UUID, command);
        currentState.setLedBrightness(brightness);
        currentState.setLedColor(color);
        notifyStateUpdate();
    }
    
    public void sendSoundCommand(int volume, int tone) {
        String command = BleConstants.buildSoundCommand(volume, tone);
        sendCommand(BleConstants.CHARACTERISTIC_SOUND_UUID, command);
        currentState.setSoundVolume(volume);
        currentState.setSoundTone(tone);
        notifyStateUpdate();
    }
    
    public void sendVibrationCommand(int intensity) {
        String command = BleConstants.buildVibrationCommand(intensity);
        sendCommand(BleConstants.CHARACTERISTIC_VIBRATION_UUID, command);
        currentState.setVibrationIntensity(intensity);
        notifyStateUpdate();
    }
    
    public void applyTherapyMode(TherapyMode mode) {
        currentState.setCurrentMode(mode.getName());
        sendLedCommand(mode.getLedBrightness(), mode.getLedColor());
        sendSoundCommand(mode.getSoundVolume(), mode.getSoundTone());
        sendVibrationCommand(mode.getVibrationIntensity());
    }
    
    private void sendCommand(UUID characteristicUuid, String command) {
        queueCommand(() -> {
            if (bluetoothGatt == null || !hasPermissions()) {
                completeCommand();
                return;
            }
            
            try {
                BluetoothGattService service = bluetoothGatt.getService(BleConstants.SERVICE_UUID);
                if (service == null) {
                    Timber.w("Service not found");
                    completeCommand();
                    return;
                }
                
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
                if (characteristic == null) {
                    Timber.w("Characteristic not found");
                    completeCommand();
                    return;
                }
                
                byte[] data = command.getBytes(StandardCharsets.UTF_8);
                characteristic.setValue(data);
                
                boolean success = bluetoothGatt.writeCharacteristic(characteristic);
                if (!success) {
                    Timber.w("Failed to write characteristic");
                    completeCommand();
                }
                
                Timber.d("Sent command: %s", command);
                
            } catch (SecurityException e) {
                Timber.e(e, "Security exception sending command");
                completeCommand();
            }
        });
    }
    
    private void queueCommand(Runnable command) {
        commandQueue.add(command);
        if (!isCommandInProgress) {
            executeNextCommand();
        }
    }
    
    private void executeNextCommand() {
        if (!commandQueue.isEmpty()) {
            isCommandInProgress = true;
            Runnable command = commandQueue.poll();
            if (command != null) {
                command.run();
            }
        } else {
            isCommandInProgress = false;
        }
    }
    
    private void completeCommand() {
        isCommandInProgress = false;
        handler.postDelayed(this::executeNextCommand, 100);
    }
    
    public void reconnect() {
        if (lastConnectedDeviceAddress != null && reconnectAttempts < BleConstants.MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            int delay = Math.min(
                BleConstants.RECONNECT_DELAY_MS * (int) Math.pow(2, reconnectAttempts - 1),
                BleConstants.MAX_RECONNECT_DELAY_MS
            );
            
            Timber.d("Reconnecting in %d ms (attempt %d)", delay, reconnectAttempts);
            
            handler.postDelayed(() -> {
                if (hasPermissions()) {
                    try {
                        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastConnectedDeviceAddress);
                        connect(device);
                    } catch (SecurityException e) {
                        Timber.e(e, "Security exception during reconnect");
                    }
                }
            }, delay);
        }
    }
    
    private void updateConnectionState(ConnectionState state) {
        this.connectionState = state;
        if (deviceCallback != null) {
            handler.post(() -> deviceCallback.onConnectionStateChanged(state));
        }
    }
    
    private void notifyStateUpdate() {
        if (deviceCallback != null) {
            handler.post(() -> deviceCallback.onDeviceStateUpdated(currentState));
        }
    }
    
    private void notifyError(String error) {
        if (deviceCallback != null) {
            handler.post(() -> deviceCallback.onError(error));
        }
    }
    
    public ConnectionState getConnectionState() {
        return connectionState;
    }
    
    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED;
    }
    
    public DeviceState getCurrentState() {
        return currentState;
    }
    
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
}
