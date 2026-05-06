package com.example.sensorycontrol.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.sensorycontrol.database.HealthRecordEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Helper class for exporting health data to CSV
 */
public class ExportHelper {
    
    private static final String CSV_HEADER = "Timestamp,Date,Time,Heart Rate (BPM),SpO2 (%),Temperature (°C),Health Status\n";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    /**
     * Export health records to CSV file
     * 
     * @param context Application context
     * @param records List of health records to export
     * @return File object if successful, null otherwise
     */
    public static File exportToCSV(Context context, List<HealthRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show();
            return null;
        }
        
        try {
            // Create file in cache directory
            String fileName = "health_data_" + System.currentTimeMillis() + ".csv";
            File file = new File(context.getCacheDir(), fileName);
            
            FileWriter writer = new FileWriter(file);
            
            // Write header
            writer.append(CSV_HEADER);
            
            // Write data rows
            for (HealthRecordEntity record : records) {
                Date date = new Date(record.getTimestamp());
                
                writer.append(String.valueOf(record.getTimestamp())).append(",");
                writer.append(dateFormat.format(date)).append(",");
                writer.append(timeFormat.format(date)).append(",");
                writer.append(String.valueOf(record.getHeartRate())).append(",");
                writer.append(String.valueOf(record.getSpO2())).append(",");
                writer.append(String.format(Locale.US, "%.1f", record.getTemperature())).append(",");
                writer.append(record.getHealthStatus()).append("\n");
            }
            
            writer.flush();
            writer.close();
            
            return file;
            
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }
    
    /**
     * Share CSV file via intent
     * 
     * @param context Application context
     * @param file CSV file to share
     */
    public static void shareCSV(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Uri fileUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
            );
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Health Monitoring Data");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Health monitoring data exported from Child Health Monitor app.");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            context.startActivity(Intent.createChooser(shareIntent, "Share Health Data"));
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Export and share in one step
     * 
     * @param context Application context
     * @param records List of health records to export
     */
    public static void exportAndShare(Context context, List<HealthRecordEntity> records) {
        File file = exportToCSV(context, records);
        if (file != null) {
            shareCSV(context, file);
        }
    }
    
    /**
     * Generate summary text for sharing
     * 
     * @param records List of health records
     * @return Summary text
     */
    public static String generateSummary(List<HealthRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return "No data available";
        }
        
        StatisticsHelper.VitalStatistics hrStats = StatisticsHelper.calculateHeartRateStats(records);
        StatisticsHelper.VitalStatistics spo2Stats = StatisticsHelper.calculateSpO2Stats(records);
        StatisticsHelper.TemperatureStatistics tempStats = StatisticsHelper.calculateTemperatureStats(records);
        StatisticsHelper.StatusDistribution statusDist = StatisticsHelper.calculateStatusDistribution(records);
        
        long startTime = records.get(records.size() - 1).getTimestamp();
        long endTime = records.get(0).getTimestamp();
        
        return String.format(Locale.US,
            "Health Monitoring Summary\n" +
            "========================\n\n" +
            "Period: %s to %s\n" +
            "Total Records: %d\n\n" +
            "Heart Rate:\n" +
            "  Average: %.1f BPM\n" +
            "  Range: %d - %d BPM\n\n" +
            "Blood Oxygen (SpO2):\n" +
            "  Average: %.1f%%\n" +
            "  Range: %d - %d%%\n\n" +
            "Temperature:\n" +
            "  Average: %.1f°C\n" +
            "  Range: %.1f - %.1f°C\n\n" +
            "Health Status:\n" +
            "  Good: %d (%.1f%%)\n" +
            "  Warning: %d (%.1f%%)\n" +
            "  Critical: %d (%.1f%%)\n",
            dateFormat.format(new Date(startTime)),
            dateFormat.format(new Date(endTime)),
            records.size(),
            hrStats.average, hrStats.min, hrStats.max,
            spo2Stats.average, spo2Stats.min, spo2Stats.max,
            tempStats.average, tempStats.min, tempStats.max,
            statusDist.goodCount, statusDist.goodPercentage(),
            statusDist.warningCount, statusDist.warningPercentage(),
            statusDist.criticalCount, statusDist.criticalPercentage()
        );
    }
}
