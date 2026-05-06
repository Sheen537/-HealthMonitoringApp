package com.example.sensorycontrol.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.sensorycontrol.database.AppDatabase;
import com.example.sensorycontrol.database.HealthRecordDao;
import com.example.sensorycontrol.database.HealthRecordEntity;
import com.example.sensorycontrol.firebase.FirestoreManager;
import com.example.sensorycontrol.models.HealthReading;
import com.example.sensorycontrol.models.HealthStatus;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for health data operations.
 * HYBRID APPROACH: Supports both Room (local) and Firestore (cloud)
 * 
 * Migration Strategy:
 * - Room: Legacy support (can be removed later)
 * - Firestore: New cloud-based storage with real-time sync
 * 
 * Provides a clean API for the ViewModel to interact with the database.
 * Handles background threading for database operations.
 */
public class HealthDataRepository {
    
    private static final String TAG = "HealthDataRepository";
    
    // Room (local database)
    private final HealthRecordDao healthRecordDao;
    
    // Firestore (cloud database)
    private final FirebaseFirestore firestore;
    
    private final ExecutorService executorService;
    private final FirebaseAuth firebaseAuth;
    
    // Feature flag: Enable/disable Firestore
    private boolean useFirestore = true; // Set to true to use Firestore
    
    public HealthDataRepository(Application application) {
        // Initialize Room
        AppDatabase database = AppDatabase.getInstance(application);
        healthRecordDao = database.healthRecordDao();
        
        // Initialize Firestore
        firestore = FirestoreManager.getInstance();
        
        executorService = Executors.newSingleThreadExecutor();
        firebaseAuth = FirebaseAuth.getInstance();
    }
    
    /**
     * Insert a health record into the database.
     * Runs on background thread.
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
                
                HealthRecordEntity record = new HealthRecordEntity(
                    System.currentTimeMillis(),
                    reading.getHeartRate(),
                    reading.getSpO2(),
                    reading.getTemperature(),
                    status.getOverallCondition().name(),
                    user.getUid()
                );
                
                long id = healthRecordDao.insert(record);
                Log.d(TAG, "Health record inserted with ID: " + id);
                
            } catch (Exception e) {
                Log.e(TAG, "Error inserting health record", e);
            }
        });
    }
    
    /**
     * Get all health records for the current user.
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordEntity>> getAllRecords() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get records: User not authenticated");
            return null;
        }
        return healthRecordDao.getAllRecords(user.getUid());
    }
    
    /**
     * Get the last N health records.
     * @param limit Number of records to retrieve
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordEntity>> getLastNRecords(int limit) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get records: User not authenticated");
            return null;
        }
        return healthRecordDao.getLastNRecords(user.getUid(), limit);
    }
    
    /**
     * Get health records within a time range.
     * @param startTime Start timestamp (milliseconds)
     * @param endTime End timestamp (milliseconds)
     * @return LiveData list of health records
     */
    public LiveData<List<HealthRecordEntity>> getRecordsByTimeRange(long startTime, long endTime) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get records: User not authenticated");
            return null;
        }
        return healthRecordDao.getRecordsByTimeRange(user.getUid(), startTime, endTime);
    }
    
    /**
     * Get records with critical health status.
     * @return LiveData list of critical records
     */
    public LiveData<List<HealthRecordEntity>> getCriticalRecords() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get records: User not authenticated");
            return null;
        }
        return healthRecordDao.getCriticalRecords(user.getUid());
    }
    
    /**
     * Get the total count of records.
     * Runs on background thread.
     * @param callback Callback with the count result
     */
    public void getRecordCount(CountCallback callback) {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    callback.onResult(0);
                    return;
                }
                int count = healthRecordDao.getRecordCount(user.getUid());
                callback.onResult(count);
            } catch (Exception e) {
                Log.e(TAG, "Error getting record count", e);
                callback.onResult(0);
            }
        });
    }
    
    /**
     * Delete all records for the current user.
     * Runs on background thread.
     */
    public void deleteAllRecords() {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Log.w(TAG, "Cannot delete records: User not authenticated");
                    return;
                }
                healthRecordDao.deleteAllRecords(user.getUid());
                Log.d(TAG, "All health records deleted");
            } catch (Exception e) {
                Log.e(TAG, "Error deleting health records", e);
            }
        });
    }
    
    /**
     * Delete records older than a specific number of days.
     * @param days Number of days to keep
     */
    public void deleteOldRecords(int days) {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Log.w(TAG, "Cannot delete old records: User not authenticated");
                    return;
                }
                long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
                healthRecordDao.deleteOldRecords(user.getUid(), cutoffTime);
                Log.d(TAG, "Old health records deleted (older than " + days + " days)");
            } catch (Exception e) {
                Log.e(TAG, "Error deleting old health records", e);
            }
        });
    }
    
    /**
     * Get average vital signs over a time period.
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @param callback Callback with the averages
     */
    public void getAverageVitals(long startTime, long endTime, AverageVitalsCallback callback) {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    callback.onResult(0, 0, 0);
                    return;
                }
                
                double avgHR = healthRecordDao.getAverageHeartRate(user.getUid(), startTime, endTime);
                double avgSpO2 = healthRecordDao.getAverageSpO2(user.getUid(), startTime, endTime);
                double avgTemp = healthRecordDao.getAverageTemperature(user.getUid(), startTime, endTime);
                
                callback.onResult(avgHR, avgSpO2, avgTemp);
            } catch (Exception e) {
                Log.e(TAG, "Error calculating average vitals", e);
                callback.onResult(0, 0, 0);
            }
        });
    }
    
    /**
     * Shutdown the executor service.
     * Should be called when the repository is no longer needed.
     */
    public void shutdown() {
        executorService.shutdown();
    }
    
    // ============================================================
    // FIRESTORE METHODS (Cloud Database with Real-time Sync)
    // ============================================================
    
    /**
     * Insert health record to Firestore (Cloud)
     * 
     * Benefits:
     * - Real-time sync across devices
     * - Automatic backup
     * - Offline support (queued writes)
     * - Multi-user isolation
     * 
     * Collection structure:
     * users/{userId}/health_records/{autoId}
     */
    public void insertHealthRecordToFirestore(HealthReading reading, HealthStatus status) {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Log.w(TAG, "Cannot insert to Firestore: User not authenticated");
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
                
                // Write to Firestore
                firestore.collection("users")
                        .document(user.getUid())
                        .collection("health_records")
                        .add(data)
                        .addOnSuccessListener(documentReference -> {
                            Log.d(TAG, "Firestore record inserted: " + documentReference.getId());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error inserting to Firestore", e);
                        });
                
            } catch (Exception e) {
                Log.e(TAG, "Error inserting health record to Firestore", e);
            }
        });
    }
    
    /**
     * Get health records from Firestore with real-time updates
     * 
     * This replaces Room queries for charts and history
     * 
     * @param callback Callback with list of records (fires on every change)
     */
    public void getHealthRecordsFromFirestore(FirestoreRecordsCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get Firestore records: User not authenticated");
            callback.onError("User not authenticated");
            return;
        }
        
        // Real-time listener (updates automatically)
        firestore.collection("users")
                .document(user.getUid())
                .collection("health_records")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to Firestore", error);
                        callback.onError(error.getMessage());
                        return;
                    }
                    
                    if (snapshots != null) {
                        // Convert to HealthRecordEntity for compatibility
                        List<HealthRecordEntity> records = new java.util.ArrayList<>();
                        snapshots.forEach(doc -> {
                            try {
                                HealthRecordEntity record = new HealthRecordEntity(
                                    doc.getLong("timestamp"),
                                    doc.getLong("heartRate").intValue(),
                                    doc.getLong("spo2").intValue(),
                                    doc.getDouble("temperature"),
                                    doc.getString("status"),
                                    user.getUid()
                                );
                                record.setId(doc.getId().hashCode()); // Use hash for ID
                                records.add(record);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing Firestore document", e);
                            }
                        });
                        
                        callback.onSuccess(records);
                        Log.d(TAG, "Firestore records received: " + records.size());
                    }
                });
    }
    
    /**
     * Get health records by time range from Firestore
     * 
     * @param startTime Start timestamp (milliseconds)
     * @param endTime End timestamp (milliseconds)
     * @param callback Callback with filtered records
     */
    public void getFirestoreRecordsByTimeRange(long startTime, long endTime, 
                                                FirestoreRecordsCallback callback) {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "Cannot get Firestore records: User not authenticated");
            callback.onError("User not authenticated");
            return;
        }
        
        firestore.collection("users")
                .document(user.getUid())
                .collection("health_records")
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error querying Firestore", error);
                        callback.onError(error.getMessage());
                        return;
                    }
                    
                    if (snapshots != null) {
                        List<HealthRecordEntity> records = new java.util.ArrayList<>();
                        snapshots.forEach(doc -> {
                            try {
                                HealthRecordEntity record = new HealthRecordEntity(
                                    doc.getLong("timestamp"),
                                    doc.getLong("heartRate").intValue(),
                                    doc.getLong("spo2").intValue(),
                                    doc.getDouble("temperature"),
                                    doc.getString("status"),
                                    user.getUid()
                                );
                                records.add(record);
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing document", e);
                            }
                        });
                        
                        callback.onSuccess(records);
                    }
                });
    }
    
    /**
     * Delete all Firestore records for current user
     */
    public void deleteAllFirestoreRecords() {
        executorService.execute(() -> {
            try {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user == null) {
                    Log.w(TAG, "Cannot delete Firestore records: User not authenticated");
                    return;
                }
                
                firestore.collection("users")
                        .document(user.getUid())
                        .collection("health_records")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            // Batch delete
                            querySnapshot.forEach(doc -> doc.getReference().delete());
                            Log.d(TAG, "All Firestore records deleted");
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting Firestore records", e);
                        });
                
            } catch (Exception e) {
                Log.e(TAG, "Error deleting Firestore records", e);
            }
        });
    }
    
    /**
     * Smart insert: Writes to both Room and Firestore
     * Use this during migration period
     */
    public void insertHealthRecordDual(HealthReading reading, HealthStatus status) {
        // Write to Room (local)
        insertHealthRecord(reading, status);
        
        // Write to Firestore (cloud)
        if (useFirestore) {
            insertHealthRecordToFirestore(reading, status);
        }
    }
    
    /**
     * Enable or disable Firestore
     * Useful for gradual migration
     */
    public void setUseFirestore(boolean enabled) {
        this.useFirestore = enabled;
        Log.d(TAG, "Firestore " + (enabled ? "enabled" : "disabled"));
    }
    
    // Callback interfaces
    public interface CountCallback {
        void onResult(int count);
    }
    
    public interface AverageVitalsCallback {
        void onResult(double avgHeartRate, double avgSpO2, double avgTemperature);
    }
    
    public interface FirestoreRecordsCallback {
        void onSuccess(List<HealthRecordEntity> records);
        void onError(String error);
    }
}
