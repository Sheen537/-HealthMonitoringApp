package com.example.sensorycontrol.utils;

import com.example.sensorycontrol.database.HealthRecordEntity;

import java.util.List;

/**
 * Helper class for calculating statistics from health records
 */
public class StatisticsHelper {
    
    /**
     * Statistics result container
     */
    public static class VitalStatistics {
        public double average;
        public int min;
        public int max;
        public int count;
        
        public VitalStatistics(double average, int min, int max, int count) {
            this.average = average;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }
    
    /**
     * Temperature statistics result container
     */
    public static class TemperatureStatistics {
        public double average;
        public double min;
        public double max;
        public int count;
        
        public TemperatureStatistics(double average, double min, double max, int count) {
            this.average = average;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }
    
    /**
     * Status distribution result
     */
    public static class StatusDistribution {
        public int goodCount;
        public int warningCount;
        public int criticalCount;
        public int totalCount;
        
        public double goodPercentage() {
            return totalCount > 0 ? (goodCount * 100.0 / totalCount) : 0;
        }
        
        public double warningPercentage() {
            return totalCount > 0 ? (warningCount * 100.0 / totalCount) : 0;
        }
        
        public double criticalPercentage() {
            return totalCount > 0 ? (criticalCount * 100.0 / totalCount) : 0;
        }
    }
    
    /**
     * Calculate heart rate statistics
     */
    public static VitalStatistics calculateHeartRateStats(List<HealthRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return new VitalStatistics(0, 0, 0, 0);
        }
        
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        
        for (HealthRecordEntity record : records) {
            int hr = record.getHeartRate();
            sum += hr;
            if (hr < min) min = hr;
            if (hr > max) max = hr;
        }
        
        double average = (double) sum / records.size();
        return new VitalStatistics(average, min, max, records.size());
    }
    
    /**
     * Calculate SpO2 statistics
     */
    public static VitalStatistics calculateSpO2Stats(List<HealthRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return new VitalStatistics(0, 0, 0, 0);
        }
        
        int sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        
        for (HealthRecordEntity record : records) {
            int spo2 = record.getSpO2();
            sum += spo2;
            if (spo2 < min) min = spo2;
            if (spo2 > max) max = spo2;
        }
        
        double average = (double) sum / records.size();
        return new VitalStatistics(average, min, max, records.size());
    }
    
    /**
     * Calculate temperature statistics
     */
    public static TemperatureStatistics calculateTemperatureStats(List<HealthRecordEntity> records) {
        if (records == null || records.isEmpty()) {
            return new TemperatureStatistics(0, 0, 0, 0);
        }
        
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        
        for (HealthRecordEntity record : records) {
            double temp = record.getTemperature();
            sum += temp;
            if (temp < min) min = temp;
            if (temp > max) max = temp;
        }
        
        double average = sum / records.size();
        return new TemperatureStatistics(average, min, max, records.size());
    }
    
    /**
     * Calculate status distribution
     */
    public static StatusDistribution calculateStatusDistribution(List<HealthRecordEntity> records) {
        StatusDistribution distribution = new StatusDistribution();
        
        if (records == null || records.isEmpty()) {
            return distribution;
        }
        
        for (HealthRecordEntity record : records) {
            String status = record.getHealthStatus();
            switch (status) {
                case "GOOD":
                    distribution.goodCount++;
                    break;
                case "WARNING":
                    distribution.warningCount++;
                    break;
                case "CRITICAL":
                    distribution.criticalCount++;
                    break;
            }
        }
        
        distribution.totalCount = records.size();
        return distribution;
    }
    
    /**
     * Format statistics for display
     */
    public static String formatVitalStats(VitalStatistics stats, String unit) {
        return String.format(
            "Avg: %.1f%s\nMin: %d%s\nMax: %d%s\nRecords: %d",
            stats.average, unit,
            stats.min, unit,
            stats.max, unit,
            stats.count
        );
    }
    
    /**
     * Format temperature statistics for display
     */
    public static String formatTemperatureStats(TemperatureStatistics stats) {
        return String.format(
            "Avg: %.1f°C\nMin: %.1f°C\nMax: %.1f°C\nRecords: %d",
            stats.average,
            stats.min,
            stats.max,
            stats.count
        );
    }
    
    /**
     * Format status distribution for display
     */
    public static String formatStatusDistribution(StatusDistribution dist) {
        return String.format(
            "Good: %d (%.1f%%)\nWarning: %d (%.1f%%)\nCritical: %d (%.1f%%)\nTotal: %d",
            dist.goodCount, dist.goodPercentage(),
            dist.warningCount, dist.warningPercentage(),
            dist.criticalCount, dist.criticalPercentage(),
            dist.totalCount
        );
    }
}
