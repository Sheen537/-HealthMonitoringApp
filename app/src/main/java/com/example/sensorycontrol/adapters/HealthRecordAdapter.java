package com.example.sensorycontrol.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sensorycontrol.R;
import com.example.sensorycontrol.database.HealthRecordEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying health records
 */
public class HealthRecordAdapter extends RecyclerView.Adapter<HealthRecordAdapter.ViewHolder> {
    
    private List<HealthRecordEntity> records = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(HealthRecordEntity record);
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_health_record, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HealthRecordEntity record = records.get(position);
        holder.bind(record);
    }
    
    @Override
    public int getItemCount() {
        return records.size();
    }
    
    public void setRecords(List<HealthRecordEntity> records) {
        this.records = records != null ? records : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    public List<HealthRecordEntity> getRecords() {
        return records;
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDate;
        private final TextView tvTime;
        private final TextView tvHeartRate;
        private final TextView tvSpO2;
        private final TextView tvTemperature;
        private final TextView tvStatus;
        private final View statusIndicator;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvHeartRate = itemView.findViewById(R.id.tv_heart_rate);
            tvSpO2 = itemView.findViewById(R.id.tv_spo2);
            tvTemperature = itemView.findViewById(R.id.tv_temperature);
            tvStatus = itemView.findViewById(R.id.tv_status);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(records.get(position));
                }
            });
        }
        
        public void bind(HealthRecordEntity record) {
            Date date = new Date(record.getTimestamp());
            tvDate.setText(dateFormat.format(date));
            tvTime.setText(timeFormat.format(date));
            
            tvHeartRate.setText(String.format(Locale.US, "%d BPM", record.getHeartRate()));
            tvSpO2.setText(String.format(Locale.US, "%d%%", record.getSpO2()));
            tvTemperature.setText(String.format(Locale.US, "%.1f°C", record.getTemperature()));
            
            String status = record.getHealthStatus();
            tvStatus.setText(status);
            
            // Set status color
            int color;
            switch (status) {
                case "GOOD":
                    color = itemView.getContext().getColor(R.color.status_good);
                    break;
                case "WARNING":
                    color = itemView.getContext().getColor(R.color.status_warning);
                    break;
                case "CRITICAL":
                    color = itemView.getContext().getColor(R.color.status_critical);
                    break;
                default:
                    color = itemView.getContext().getColor(R.color.text_secondary);
            }
            
            tvStatus.setTextColor(color);
            statusIndicator.setBackgroundColor(color);
        }
    }
}
