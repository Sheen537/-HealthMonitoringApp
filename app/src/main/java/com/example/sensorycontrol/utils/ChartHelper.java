package com.example.sensorycontrol.utils;

import android.content.Context;
import android.graphics.Color;

import com.example.sensorycontrol.database.HealthRecordEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Helper class for configuring and populating MPAndroidChart line charts
 */
public class ChartHelper {
    
    // Chart colors
    public static final int COLOR_HEART_RATE = Color.parseColor("#E91E63");
    public static final int COLOR_SPO2 = Color.parseColor("#2196F3");
    public static final int COLOR_TEMPERATURE = Color.parseColor("#FF9800");
    public static final int COLOR_GOOD = Color.parseColor("#4CAF50");
    public static final int COLOR_WARNING = Color.parseColor("#FF9800");
    public static final int COLOR_CRITICAL = Color.parseColor("#F44336");
    
    /**
     * Configure a line chart with default settings
     */
    public static void configureChart(LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        
        // Configure X-Axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.GRAY);
        xAxis.setGridColor(Color.LTGRAY);
        
        // Configure Y-Axis (Left)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setGridColor(Color.LTGRAY);
        
        // Disable right Y-Axis
        chart.getAxisRight().setEnabled(false);
        
        // Configure Legend
        Legend legend = chart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }
    
    /**
     * Create heart rate chart data from records
     */
    public static LineData createHeartRateData(List<HealthRecordEntity> records) {
        List<Entry> entries = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            HealthRecordEntity record = records.get(i);
            entries.add(new Entry(i, record.getHeartRate()));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "Heart Rate (BPM)");
        styleDataSet(dataSet, COLOR_HEART_RATE);
        
        return new LineData(dataSet);
    }
    
    /**
     * Create SpO2 chart data from records
     */
    public static LineData createSpO2Data(List<HealthRecordEntity> records) {
        List<Entry> entries = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            HealthRecordEntity record = records.get(i);
            entries.add(new Entry(i, record.getSpO2()));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "SpO2 (%)");
        styleDataSet(dataSet, COLOR_SPO2);
        
        return new LineData(dataSet);
    }
    
    /**
     * Create temperature chart data from records
     */
    public static LineData createTemperatureData(List<HealthRecordEntity> records) {
        List<Entry> entries = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            HealthRecordEntity record = records.get(i);
            entries.add(new Entry(i, (float) record.getTemperature()));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "Temperature (°C)");
        styleDataSet(dataSet, COLOR_TEMPERATURE);
        
        return new LineData(dataSet);
    }
    
    /**
     * Create combined chart with all vitals
     */
    public static LineData createCombinedData(List<HealthRecordEntity> records) {
        List<Entry> hrEntries = new ArrayList<>();
        List<Entry> spo2Entries = new ArrayList<>();
        List<Entry> tempEntries = new ArrayList<>();
        
        for (int i = 0; i < records.size(); i++) {
            HealthRecordEntity record = records.get(i);
            hrEntries.add(new Entry(i, record.getHeartRate()));
            spo2Entries.add(new Entry(i, record.getSpO2()));
            tempEntries.add(new Entry(i, (float) record.getTemperature() * 10)); // Scale temp for visibility
        }
        
        LineDataSet hrDataSet = new LineDataSet(hrEntries, "Heart Rate");
        styleDataSet(hrDataSet, COLOR_HEART_RATE);
        
        LineDataSet spo2DataSet = new LineDataSet(spo2Entries, "SpO2");
        styleDataSet(spo2DataSet, COLOR_SPO2);
        
        LineDataSet tempDataSet = new LineDataSet(tempEntries, "Temp (×10)");
        styleDataSet(tempDataSet, COLOR_TEMPERATURE);
        
        LineData lineData = new LineData(hrDataSet, spo2DataSet, tempDataSet);
        return lineData;
    }
    
    /**
     * Style a line data set
     */
    private static void styleDataSet(LineDataSet dataSet, int color) {
        dataSet.setColor(color);
        dataSet.setCircleColor(color);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextSize(9f);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(color);
        dataSet.setFillAlpha(30);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawValues(false);
    }
    
    /**
     * Create time-based X-axis formatter
     */
    public static ValueFormatter createTimeFormatter(List<HealthRecordEntity> records, boolean showDate) {
        return new ValueFormatter() {
            private final SimpleDateFormat timeFormat = new SimpleDateFormat(
                showDate ? "MM/dd HH:mm" : "HH:mm", Locale.getDefault()
            );
            
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < records.size()) {
                    long timestamp = records.get(index).getTimestamp();
                    return timeFormat.format(new Date(timestamp));
                }
                return "";
            }
        };
    }
    
    /**
     * Update chart with new data
     */
    public static void updateChart(LineChart chart, LineData data, List<HealthRecordEntity> records, boolean showDate) {
        chart.setData(data);
        chart.getXAxis().setValueFormatter(createTimeFormatter(records, showDate));
        chart.notifyDataSetChanged();
        chart.invalidate();
        
        // Auto-scale
        chart.fitScreen();
    }
    
    /**
     * Clear chart data
     */
    public static void clearChart(LineChart chart) {
        chart.clear();
        chart.invalidate();
    }
}
