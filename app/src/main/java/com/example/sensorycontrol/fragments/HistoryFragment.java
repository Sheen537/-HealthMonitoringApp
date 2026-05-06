package com.example.sensorycontrol.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.adapters.HealthRecordAdapter;
import com.example.sensorycontrol.repository.FirestoreHealthRepository;
import com.example.sensorycontrol.utils.ChartHelper;
import com.example.sensorycontrol.utils.ExportHelper;
import com.example.sensorycontrol.utils.StatisticsHelper;
import com.example.sensorycontrol.viewmodels.HealthMonitorViewModelWifi;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.LineData;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;
import java.util.List;

/**
 * Fragment for displaying historical health data with charts and statistics
 */
public class HistoryFragment extends Fragment {
    
    private HealthMonitorViewModelWifi viewModel;
    private HealthRecordAdapter adapter;
    
    // UI Components
    private MaterialButton btn24h, btn7d, btn30d, btnExport;
    private LineChart chartHeartRate, chartSpO2, chartTemperature;
    private TextView tvHrStats, tvSpO2Stats, tvTempStats, tvStatusDist;
    private RecyclerView recyclerView;
    private MaterialCardView cardStats;
    private View progressBar, emptyView;
    
    // Current time range
    private TimeRange currentRange = TimeRange.HOURS_24;
    
    private enum TimeRange {
        HOURS_24(24 * 60 * 60 * 1000L),
        DAYS_7(7 * 24 * 60 * 60 * 1000L),
        DAYS_30(30 * 24 * 60 * 60 * 1000L);
        
        final long milliseconds;
        
        TimeRange(long milliseconds) {
            this.milliseconds = milliseconds;
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupViewModel();
        setupRecyclerView();
        setupCharts();
        setupButtons();
        
        // Load initial data (24 hours)
        loadData(TimeRange.HOURS_24);
    }
    
    private void initViews(View view) {
        // Time range buttons
        btn24h = view.findViewById(R.id.btn_24h);
        btn7d = view.findViewById(R.id.btn_7d);
        btn30d = view.findViewById(R.id.btn_30d);
        btnExport = view.findViewById(R.id.btn_export);
        
        // Charts
        chartHeartRate = view.findViewById(R.id.chart_heart_rate);
        chartSpO2 = view.findViewById(R.id.chart_spo2);
        chartTemperature = view.findViewById(R.id.chart_temperature);
        
        // Statistics
        tvHrStats = view.findViewById(R.id.tv_hr_stats);
        tvSpO2Stats = view.findViewById(R.id.tv_spo2_stats);
        tvTempStats = view.findViewById(R.id.tv_temp_stats);
        tvStatusDist = view.findViewById(R.id.tv_status_dist);
        cardStats = view.findViewById(R.id.card_stats);
        
        // RecyclerView
        recyclerView = view.findViewById(R.id.recycler_history);
        
        // Progress and empty views
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
    }
    
    private void setupViewModel() {
        viewModel = new ViewModelProvider(requireActivity()).get(HealthMonitorViewModelWifi.class);
    }
    
    private void setupRecyclerView() {
        adapter = new HealthRecordAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        adapter.setOnItemClickListener(record -> {
            // Show detailed view (optional)
            Toast.makeText(getContext(), 
                String.format("Record from %s", 
                    new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(new java.util.Date(record.getTimestamp()))),
                Toast.LENGTH_SHORT).show();
        });
    }
    
    private void setupCharts() {
        ChartHelper.configureChart(chartHeartRate);
        ChartHelper.configureChart(chartSpO2);
        ChartHelper.configureChart(chartTemperature);
    }
    
    private void setupButtons() {
        btn24h.setOnClickListener(v -> {
            loadData(TimeRange.HOURS_24);
            updateButtonStates(btn24h);
        });
        
        btn7d.setOnClickListener(v -> {
            loadData(TimeRange.DAYS_7);
            updateButtonStates(btn7d);
        });
        
        btn30d.setOnClickListener(v -> {
            loadData(TimeRange.DAYS_30);
            updateButtonStates(btn30d);
        });
        
        btnExport.setOnClickListener(v -> exportData());
        
        // Set initial button state
        updateButtonStates(btn24h);
    }
    
    private void updateButtonStates(MaterialButton selectedButton) {
        // Reset all buttons
        btn24h.setBackgroundColor(getResources().getColor(R.color.surface, null));
        btn7d.setBackgroundColor(getResources().getColor(R.color.surface, null));
        btn30d.setBackgroundColor(getResources().getColor(R.color.surface, null));
        
        // Highlight selected
        selectedButton.setBackgroundColor(getResources().getColor(R.color.primary_light, null));
    }
    
    private void loadData(TimeRange range) {
        currentRange = range;
        showLoading(true);
        
        long endTime = System.currentTimeMillis();
        long startTime = endTime - range.milliseconds;
        
        viewModel.getRecordsByTimeRange(startTime, endTime).observe(getViewLifecycleOwner(), records -> {
            showLoading(false);
            
            if (records == null || records.isEmpty()) {
                showEmptyView(true);
                return;
            }
            
            showEmptyView(false);
            updateUI(records);
        });
    }
    
    private void updateUI(List<FirestoreHealthRepository.HealthRecordFirestore> records) {
        // Update charts
        updateCharts(records);
        
        // Update statistics
        updateStatistics(records);
        
        // Update list
        adapter.setRecords(records);
    }
    
    private void updateCharts(List<FirestoreHealthRepository.HealthRecordFirestore> records) {
        boolean showDate = currentRange != TimeRange.HOURS_24;
        
        // Heart Rate Chart
        LineData hrData = ChartHelper.createHeartRateData(records);
        ChartHelper.updateChart(chartHeartRate, hrData, records, showDate);
        
        // SpO2 Chart
        LineData spo2Data = ChartHelper.createSpO2Data(records);
        ChartHelper.updateChart(chartSpO2, spo2Data, records, showDate);
        
        // Temperature Chart
        LineData tempData = ChartHelper.createTemperatureData(records);
        ChartHelper.updateChart(chartTemperature, tempData, records, showDate);
    }
    
    private void updateStatistics(List<FirestoreHealthRepository.HealthRecordFirestore> records) {
        // Calculate statistics
        StatisticsHelper.VitalStatistics hrStats = 
            StatisticsHelper.calculateHeartRateStats(records);
        StatisticsHelper.VitalStatistics spo2Stats = 
            StatisticsHelper.calculateSpO2Stats(records);
        StatisticsHelper.TemperatureStatistics tempStats = 
            StatisticsHelper.calculateTemperatureStats(records);
        StatisticsHelper.StatusDistribution statusDist = 
            StatisticsHelper.calculateStatusDistribution(records);
        
        // Update UI
        tvHrStats.setText(String.format(java.util.Locale.US,
            "Avg: %.1f BPM\nMin: %d | Max: %d",
            hrStats.average, hrStats.min, hrStats.max));
        
        tvSpO2Stats.setText(String.format(java.util.Locale.US,
            "Avg: %.1f%%\nMin: %d | Max: %d",
            spo2Stats.average, spo2Stats.min, spo2Stats.max));
        
        tvTempStats.setText(String.format(java.util.Locale.US,
            "Avg: %.1f°C\nMin: %.1f | Max: %.1f",
            tempStats.average, tempStats.min, tempStats.max));
        
        tvStatusDist.setText(String.format(java.util.Locale.US,
            "Good: %d (%.0f%%)\nWarning: %d (%.0f%%)\nCritical: %d (%.0f%%)",
            statusDist.goodCount, statusDist.goodPercentage(),
            statusDist.warningCount, statusDist.warningPercentage(),
            statusDist.criticalCount, statusDist.criticalPercentage()));
    }
    
    private void exportData() {
        List<FirestoreHealthRepository.HealthRecordFirestore> records = adapter.getRecords();
        if (records == null || records.isEmpty()) {
            Toast.makeText(getContext(), "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ExportHelper.exportAndShare(requireContext(), records);
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        cardStats.setVisibility(show ? View.GONE : View.VISIBLE);
        chartHeartRate.setVisibility(show ? View.GONE : View.VISIBLE);
        chartSpO2.setVisibility(show ? View.GONE : View.VISIBLE);
        chartTemperature.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyView(boolean show) {
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        cardStats.setVisibility(show ? View.GONE : View.VISIBLE);
        chartHeartRate.setVisibility(show ? View.GONE : View.VISIBLE);
        chartSpO2.setVisibility(show ? View.GONE : View.VISIBLE);
        chartTemperature.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}
