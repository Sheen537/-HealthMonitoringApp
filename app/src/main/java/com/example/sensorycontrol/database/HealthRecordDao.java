package com.example.sensorycontrol.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;

import java.util.List;

/**
 * Data Access Object (DAO) for health records.
 * Provides methods to interact with the local SQLite database.
 */
@Dao
public interface HealthRecordDao {
    
    /**
     * Insert a new health record.
     * @param record The health record to insert
     * @return The row ID of the inserted record
     */
    @Insert
    long insert(HealthRecordEntity record);
    
    /**
     * Get all health records for a specific user, ordered by timestamp (newest first).
     * @param userId The Firebase user ID
     * @return LiveData list of health records
     */
    @Query("SELECT * FROM health_records WHERE user_id = :userId ORDER BY timestamp DESC")
    LiveData<List<HealthRecordEntity>> getAllRecords(String userId);
    
    /**
     * Get the last N health records for a specific user.
     * @param userId The Firebase user ID
     * @param limit Number of records to retrieve
     * @return LiveData list of health records
     */
    @Query("SELECT * FROM health_records WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<HealthRecordEntity>> getLastNRecords(String userId, int limit);
    
    /**
     * Get health records within a specific time range.
     * @param userId The Firebase user ID
     * @param startTime Start timestamp (milliseconds)
     * @param endTime End timestamp (milliseconds)
     * @return LiveData list of health records
     */
    @Query("SELECT * FROM health_records WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    LiveData<List<HealthRecordEntity>> getRecordsByTimeRange(String userId, long startTime, long endTime);
    
    /**
     * Get records with critical health status.
     * @param userId The Firebase user ID
     * @return LiveData list of critical health records
     */
    @Query("SELECT * FROM health_records WHERE user_id = :userId AND health_status = 'CRITICAL' ORDER BY timestamp DESC")
    LiveData<List<HealthRecordEntity>> getCriticalRecords(String userId);
    
    /**
     * Get the most recent health record for a user.
     * @param userId The Firebase user ID
     * @return The most recent health record
     */
    @Query("SELECT * FROM health_records WHERE user_id = :userId ORDER BY timestamp DESC LIMIT 1")
    HealthRecordEntity getLatestRecord(String userId);
    
    /**
     * Get the total count of records for a user.
     * @param userId The Firebase user ID
     * @return Total number of records
     */
    @Query("SELECT COUNT(*) FROM health_records WHERE user_id = :userId")
    int getRecordCount(String userId);
    
    /**
     * Delete all records for a specific user.
     * @param userId The Firebase user ID
     */
    @Query("DELETE FROM health_records WHERE user_id = :userId")
    void deleteAllRecords(String userId);
    
    /**
     * Delete records older than a specific timestamp.
     * Useful for data retention policies.
     * @param userId The Firebase user ID
     * @param timestamp Cutoff timestamp (milliseconds)
     */
    @Query("DELETE FROM health_records WHERE user_id = :userId AND timestamp < :timestamp")
    void deleteOldRecords(String userId, long timestamp);
    
    /**
     * Delete a specific record.
     * @param record The record to delete
     */
    @Delete
    void delete(HealthRecordEntity record);
    
    /**
     * Get average heart rate over a time period.
     * @param userId The Firebase user ID
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return Average heart rate
     */
    @Query("SELECT AVG(heart_rate) FROM health_records WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime")
    double getAverageHeartRate(String userId, long startTime, long endTime);
    
    /**
     * Get average SpO2 over a time period.
     * @param userId The Firebase user ID
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return Average SpO2
     */
    @Query("SELECT AVG(spo2) FROM health_records WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime")
    double getAverageSpO2(String userId, long startTime, long endTime);
    
    /**
     * Get average temperature over a time period.
     * @param userId The Firebase user ID
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return Average temperature
     */
    @Query("SELECT AVG(temperature) FROM health_records WHERE user_id = :userId AND timestamp BETWEEN :startTime AND :endTime")
    double getAverageTemperature(String userId, long startTime, long endTime);
}
