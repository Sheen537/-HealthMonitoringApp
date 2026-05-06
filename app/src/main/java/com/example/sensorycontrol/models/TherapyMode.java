package com.example.sensorycontrol.models;

/**
 * Preset therapy modes for different therapeutic needs
 */
public class TherapyMode {
    
    private String name;
    private String description;
    private int ledBrightness;
    private int ledColor;
    private int soundVolume;
    private int soundTone;
    private int vibrationIntensity;
    private int iconResource;
    
    public TherapyMode(String name, String description, int ledBrightness, int ledColor,
                      int soundVolume, int soundTone, int vibrationIntensity) {
        this.name = name;
        this.description = description;
        this.ledBrightness = ledBrightness;
        this.ledColor = ledColor;
        this.soundVolume = soundVolume;
        this.soundTone = soundTone;
        this.vibrationIntensity = vibrationIntensity;
    }
    
    // Predefined therapy modes
    public static TherapyMode getCalmMode() {
        return new TherapyMode(
            "Calm Mode",
            "Soft blue light with gentle sounds for relaxation",
            30,              // Low brightness
            0xFF6495ED,      // Cornflower blue
            20,              // Low volume
            220,             // Low frequency (A3)
            10               // Minimal vibration
        );
    }
    
    public static TherapyMode getFocusMode() {
        return new TherapyMode(
            "Focus Mode",
            "Bright white light with moderate stimulation",
            70,              // Medium-high brightness
            0xFFFFFFFF,      // White
            40,              // Medium volume
            440,             // A4 note
            30               // Moderate vibration
        );
    }
    
    public static TherapyMode getSensoryPlayMode() {
        return new TherapyMode(
            "Sensory Play",
            "Colorful lights and varied sounds for engagement",
            80,              // High brightness
            0xFFFF69B4,      // Hot pink
            60,              // Higher volume
            880,             // A5 note
            50               // Strong vibration
        );
    }
    
    public static TherapyMode getSleepAidMode() {
        return new TherapyMode(
            "Sleep Aid",
            "Dim warm light with soothing sounds",
            15,              // Very low brightness
            0xFFFFD700,      // Gold (warm)
            15,              // Very low volume
            110,             // Low frequency (A2)
            0                // No vibration
        );
    }
    
    // Getters
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getLedBrightness() {
        return ledBrightness;
    }
    
    public int getLedColor() {
        return ledColor;
    }
    
    public int getSoundVolume() {
        return soundVolume;
    }
    
    public int getSoundTone() {
        return soundTone;
    }
    
    public int getVibrationIntensity() {
        return vibrationIntensity;
    }
    
    public int getIconResource() {
        return iconResource;
    }
    
    public void setIconResource(int iconResource) {
        this.iconResource = iconResource;
    }
}
