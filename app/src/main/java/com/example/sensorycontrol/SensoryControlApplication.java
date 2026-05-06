package com.example.sensorycontrol;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;

import timber.log.Timber;

/**
 * Application class for Sensory Control app
 * Multisensory therapy device controller for children with special needs
 */
public class SensoryControlApplication extends Application {
    
    public static final String CHANNEL_ID_SERVICE = "device_service_channel";
    public static final String CHANNEL_ID_SAFETY = "safety_alerts_channel";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        
        // Create notification channels
        createNotificationChannels();
        
        Timber.d("SensoryControl Application initialized");
    }
    
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            // Service notification channel
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Device Connection Service",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Maintains connection to sensory devices");
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);
            
            // Safety alert channel
            NotificationChannel safetyChannel = new NotificationChannel(
                CHANNEL_ID_SAFETY,
                "Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            safetyChannel.setDescription("Distance and safety warnings");
            safetyChannel.enableVibration(true);
            safetyChannel.setShowBadge(true);
            manager.createNotificationChannel(safetyChannel);
            
            Timber.d("Notification channels created");
        }
    }
}
