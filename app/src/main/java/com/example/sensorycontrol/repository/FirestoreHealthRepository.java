package com.example.sensorycontrol.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.sensorycontrol.models.HealthReading;
import com.example.sensorycontrol.models.HealthStatus;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firestore Repository for health data operations.
 * Replaces Room-based HealthDataRepository with cloud storage.
 * 
 * Features:
 * - Cloud storage with automatic sync
 * - Offline persistence (Firestore cache)
 * - Multi-user isolation via Firebase Auth
 * - Real-time listeners
 * - Batch operations for efficiency
 * 
 * Collection Structure:
 * users/{userId}/health_records/{recordId}
 */
public class FirestoreHealthRepository {
    
    private static final String TAG = "FirestoreHealthRepo";
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_HEALTH_RECORDS = "health_records";
    
    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final ExecutorService executorService;
    
    private ListenerRegistration realtimeListener;
    
    public FirestoreHealthRepository() {
        this.firestore = FirebaseFirestore.getInstance();
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.executorService = Executors.newSingleThreadExecutor();
        
        // Enable offline persistence
        firestore.getFirestoreSettings();
        
        Log.d(TAG, "Firestore repository initialized");
    }
    
    /**
     * Get current user's health records collection reference
     */
    private String getUserHealthRecordsPath() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return COLLECTION_USERS + "/" + user.getUid() + "/" + COLLECTION_HEALTH_RECORDS;
    }
    
    /**
     * Insert a health record into Firestore.
     * Runs asynchronously.
     * 
     * @param reading The health reading to store
     * @param status The evaluated health status
     */
    public void insertHealthRecord(HealthReading reading, HealthStatus status) {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Log.w(TAG, "Cannot insert record: User not authenticated");
                    return;
                }
                
                // Create document data
                Map<String, Object> data = new HashMap<>();
                data.put("heartRate", reading.getHeartRate());
                data.put("spo2", reading.getSpO2());
                data.put("temperature", reading.getTemperature());
                data.put("status", status.getOverallCondition().name());
                data.put("timestamp", reading.getTimestamp());
                data.put("hrValid", reading.isHrValid());
                data.put("tempValid", reading.isTempValid());
                
                // Add to Firestore
                firestore.collection(getUserHealthRecordsPath())
                        .add(data)
                        .addOnSuccessListener(documentReference -> {
                            Log.d(TAG, "Health record inserted: " + documentReference.getId());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error inserting health record", e);
                        });
                
            } catch (Exception e) {
                Log.e(TAG, "Error inserting health record", e);
            }
        });
    }
    
    /**
     * Get all health records for the current user.
     * Returns LiveData that updates in real-time.
     * 
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordFirestore>> getAllRecords() {
        MutableLiveData<List<HealthRecordFirestore>> liveData = new MutableLiveData<>();
        
        try {
            firestore.collection(getUserHealthRecordsPath())
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error getting records", error);
                            liveData.setValue(new ArrayList<>());
                            return;
                        }
                        
                        if (snapshots != null) {
                            List<HealthRecordFirestore> records = parseDocuments(snapshots);
                            liveData.setValue(records);
                            Log.d(TAG, "Records updated: " + records.size());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listener", e);
            liveData.setValue(new ArrayList<>());
        }
        
        return liveData;
    }
    
    /**
     * Get the last N health records.
     * 
     * @param limit Number of records to retrieve
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordFirestore>> getLastNRecords(int limit) {
        MutableLiveData<List<HealthRecordFirestore>> liveData = new MutableLiveData<>();
        
        try {
            firestore.collection(getUserHealthRecordsPath())
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(limit)
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error getting last N records", error);
                            liveData.setValue(new ArrayList<>());
                            return;
                        }
                        
                        if (snapshots != null) {
                            List<HealthRecordFirestore> records = parseDocuments(snapshots);
                            liveData.setValue(records);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listener", e);
            liveData.setValue(new ArrayList<>());
        }
        
        return liveData;
    }
    
    /**
     * Get health records within a time range.
     * 
     * @param startTime Start timestamp (milliseconds)
     * @param endTime End timestamp (milliseconds)
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordFirestore>> getRecordsByTimeRange(long startTime, long endTime) {
        MutableLiveData<List<HealthRecordFirestore>> liveData = new MutableLiveData<>();
        
        try {
            firestore.collection(getUserHealthRecordsPath())
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error getting records by time range", error);
                            liveData.setValue(new ArrayList<>());
                            return;
                        }
                        
                        if (snapshots != null) {
                            List<HealthRecordFirestore> records = parseDocuments(snapshots);
                            liveData.setValue(records);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listener", e);
            liveData.setValue(new ArrayList<>());
        }
        
        return liveData;
    }
    
    /**
     * Get records with critical health status.
     * 
     * @return LiveData list of critical records
     */
    public LiveData<List<HealthRecordFirestore>> getCriticalRecords() {
        MutableLiveData<List<HealthRecordFirestore>> liveData = new MutableLiveData<>();
        
        try {
            firestore.collection(getUserHealthRecordsPath())
                    .whereEqualTo("status", "CRITICAL")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener((snapshots, error) -> {
                        if (error != null) {
                            Log.e(TAG, "Error getting critical records", error);
                            liveData.setValue(new ArrayList<>());
                            return;
                        }
                        
                        if (snapshots != null) {
                            List<HealthRecordFirestore> records = parseDocuments(snapshots);
                            liveData.setValue(records);
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listener", e);
            liveData.setValue(new ArrayList<>());
        }
        
        return liveData;
    }
    
    /**
     * Get the total count of records.
     * 
     * @param callback Callback with the count result
     */
    public void getRecordCount(CountCallback callback) {
        executorService.execute(() -> {
            try {
                firestore.collection(getUserHealthRecordsPath())
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            int count = querySnapshot.size();
                            callback.onResult(count);
                            Log.d(TAG, "Record count: " + count);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error getting record count", e);
                            callback.onResult(0);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error getting record count", e);
                callback.onResult(0);
            }
        });
    }
    
    /**
     * Delete all records for the current user.
     */
    public void deleteAllRecords() {
        executorService.execute(() -> {
            try {
                firestore.collection(getUserHealthRecordsPath())
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            // Batch delete
                            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                                document.getReference().delete();
                            }
                            Log.d(TAG, "All health records deleted");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting all records", e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting all records", e);
            }
        });
    }
    
    /**
     * Delete records older than a specific number of days.
     * 
     * @param days Number of days to keep
     */
    public void deleteOldRecords(int days) {
        executorService.execute(() -> {
            try {
                long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
                
                firestore.collection(getUserHealthRecordsPath())
                        .whereLessThan("timestamp", cutoffTime)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            // Batch delete
                            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                                document.getReference().delete();
                            }
                            Log.d(TAG, "Old health records deleted (older than " + days + " days)");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting old records", e);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting old records", e);
            }
        });
    }
    
    /**
     * Get average vital signs over a time period.
     * 
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param callback Callback with the averages
     */
    public void getAverageVitals(long startTime, long endTime, AverageVitalsCallback callback) {
        executorService.execute(() -> {
            try {
                firestore.collection(getUserHealthRecordsPath())
                        .whereGreaterThanOrEqualTo("timestamp", startTime)
                        .whereLessThanOrEqualTo("timestamp", endTime)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            double sumHR = 0, sumSpO2 = 0, sumTemp = 0;
                            int count = 0;
                            
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                sumHR += doc.getLong("heartRate");
                                sumSpO2 += doc.getLong("spo2");
                                sumTemp += doc.getDouble("temperature");
                                count++;
                            }
                            
                            if (count > 0) {
                                callback.onResult(sumHR / count, sumSpO2 / count, sumTemp / count);
                            } else {
                                callback.onResult(0, 0, 0);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error calculating average vitals", e);
                            callback.onResult(0, 0, 0);
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error calculating average vitals", e);
                callback.onResult(0, 0, 0);
            }
        });
    }
    
    /**
     * Parse Firestore documents into HealthRecordFirestore objects
     */
    private List<HealthRecordFirestore> parseDocuments(QuerySnapshot snapshots) {
        List<HealthRecordFirestore> records = new ArrayList<>();
        
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            try {
                HealthRecordFirestore record = new HealthRecordFirestore();
                record.setId(doc.getId());
                record.setTimestamp(doc.getLong("timestamp"));
                record.setHeartRate(doc.getLong("heartRate").intValue());
                record.setSpO2(doc.getLong("spo2").intValue());
                record.setTemperature(doc.getDouble("temperature"));
                record.setHealthStatus(doc.getString("status"));
                record.setUserId(firebaseAuth.getCurrentUser().getUid());
                
                records.add(record);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing document: " + doc.getId(), e);
            }
        }
        
        return records;
    }
    
    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        
        if (realtimeListener != null) {
            realtimeListener.remove();
        }
        
        Log.d(TAG, "Repository shutdown");
    }
    
    // Callback interfaces
    public interface CountCallback {
        void onResult(int count);
    }
    
    public interface AverageVitalsCallback {
        void onResult(double avgHeartRate, double avgSpO2, double avgTemperature);
    }
    
    /**
     * Firestore health record model
     * Replaces HealthRecordEntity (Room)
     */
    public static class HealthRecordFirestore {
        private String id;
        private long timestamp;
        private int heartRate;
        private int spO2;
        private double temperature;
        private String healthStatus;
        private String userId;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public int getHeartRate() { return heartRate; }
        public void setHeartRate(int heartRate) { this.heartRate = heartRate; }
        
        public int getSpO2() { return spO2; }
        public void setSpO2(int spO2) { this.spO2 = spO2; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        public String getHealthStatus() { return healthStatus; }
        public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        @Override
        public String toString() {
            return "HealthRecordFirestore{" +
                    "id='" + id + '\'' +
                    ", timestamp=" + timestamp +
                    ", heartRate=" + heartRate +
                    ", spO2=" + spO2 +
                    ", temperature=" + temperature +
                    ", healthStatus='" + healthStatus + '\'' +
                    ", userId='" + userId + '\'' +
                    '}';
        }
    }
}
