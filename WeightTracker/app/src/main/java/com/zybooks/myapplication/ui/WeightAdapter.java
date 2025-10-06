package com.zybooks.myapplication.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.zybooks.myapplication.R;
import com.zybooks.myapplication.models.WeightRecord;

/// RecyclerView adapter for displaying weight records with Edit/Delete actions.
public class WeightAdapter extends ListAdapter<WeightRecord, WeightAdapter.VH> {

    /// Callbacks for row-level actions (provided by the hosting Activity)
    public interface OnItemAction {
        void onEdit(WeightRecord record);
        void onDelete(WeightRecord record);
    }

    private final OnItemAction actions;

    /// ListAdapter handles diffing automatically using DIFF below.
    public WeightAdapter(OnItemAction actions) {
        super(DIFF);
        this.actions = actions;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate one item row
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_weight_record, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        // Get the current row’s data
        WeightRecord r = getItem(position);

        // Read values from your model (rename if your model differs)
        double value = r.getWeight();
        String date  = r.getDate();

        // Bind text
        h.weightText.setText(String.format("%.2f lb", value));
        h.dateText.setText(date);

        // Wire row actions to the provided callbacks
        h.editButton.setOnClickListener(v -> actions.onEdit(r));
        h.deleteButton.setOnClickListener(v -> actions.onDelete(r));
    }

    /// Stable diffing for smooth list updates
    static final DiffUtil.ItemCallback<WeightRecord> DIFF =
            new DiffUtil.ItemCallback<WeightRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull WeightRecord a, @NonNull WeightRecord b) {
                    // Same database row -> same item
                    return a.getId() == b.getId();
                }
                @Override
                public boolean areContentsTheSame(@NonNull WeightRecord a, @NonNull WeightRecord b) {
                    // If fields shown on screen haven’t changed, no rebind needed
                    return a.getWeight() == b.getWeight()
                            && safeEq(a.getDate(), b.getDate());
                }
                private boolean safeEq(String x, String y) {
                    return (x == null && y == null) || (x != null && x.equals(y));
                }
            };

    /// Simple ViewHolder that caches view references for one row
    static class VH extends RecyclerView.ViewHolder {
        final TextView weightText, dateText;
        final Button editButton, deleteButton;

        VH(@NonNull View itemView) {
            super(itemView);
            weightText   = itemView.findViewById(R.id.itemWeightText);
            dateText     = itemView.findViewById(R.id.itemDateText);
            editButton   = itemView.findViewById(R.id.itemEditButton);
            deleteButton = itemView.findViewById(R.id.itemDeleteButton);
        }
    }
}