package com.example.sensorycontrol.firebase;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * Centralized Firestore Manager
 * Singleton pattern for clean architecture
 * 
 * Benefits:
 * - Single Firestore instance
 * - Consistent configuration
 * - Easy to mock for testing
 * - MVVM-friendly
 */
public class FirestoreManager {
    
    private static FirebaseFirestore db;
    
    /**
     * Get Firestore instance with optimized settings
     * Thread-safe singleton
     */
    public static FirebaseFirestore getInstance() {
        if (db == null) {
            synchronized (FirestoreManager.class) {
                if (db == null) {
                    db = FirebaseFirestore.getInstance();
                    
                    // Configure Firestore settings
                    FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true) // Offline support
                            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Unlimited cache
                            .build();
                    
                    db.setFirestoreSettings(settings);
                }
            }
        }
        return db;
    }
    
    /**
     * Enable debug logging (for development only)
     */
    public static void enableLogging() {
        FirebaseFirestore.setLoggingEnabled(true);
    }
    
    /**
     * Disable debug logging (for production)
     */
    public static void disableLogging() {
        FirebaseFirestore.setLoggingEnabled(false);
    }
}
