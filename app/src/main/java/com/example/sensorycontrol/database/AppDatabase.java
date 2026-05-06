package com.example.sensorycontrol.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room Database for local health data storage.
 * Implements Singleton pattern to ensure single database instance.
 */
@Database(entities = {HealthRecordEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "child_health_monitor.db";
    private static volatile AppDatabase INSTANCE;
    
    /**
     * Get the DAO for health records.
     * @return HealthRecordDao instance
     */
    public abstract HealthRecordDao healthRecordDao();
    
    /**
     * Get singleton database instance.
     * Thread-safe implementation using double-checked locking.
     * 
     * @param context Application context
     * @return AppDatabase instance
     */
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    // Uncomment for debugging (allows main thread queries - NOT for production)
                    // .allowMainThreadQueries()
                    
                    // Add migration strategy if needed in future versions
                    .fallbackToDestructiveMigration() // For development only
                    
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Close the database instance.
     * Should be called when the app is destroyed.
     */
    public static void destroyInstance() {
        if (INSTANCE != null) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}
