package com.poliku.polygoplus.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;

import java.util.List;

final class PickerOptionAdapter extends RecyclerView.Adapter<PickerOptionAdapter.OptionHolder> {

    interface OnClick {
        void onClick(@NonNull String label);
    }

    private final List<PickerOptionSheet.Option> options;
    private final String current;
    private final OnClick listener;

    PickerOptionAdapter(List<PickerOptionSheet.Option> options, @Nullable String current, OnClick listener) {
        this.options = options;
        this.current = current;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OptionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_picker_option, parent, false);
        return new OptionHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OptionHolder holder, int position) {
        PickerOptionSheet.Option option = options.get(position);
        holder.title.setText(option.label);
        if (option.description != null && !option.description.isEmpty()) {
            holder.desc.setText(option.description);
            holder.desc.setVisibility(View.VISIBLE);
        } else {
            holder.desc.setVisibility(View.GONE);
        }
        if (option.iconRes != 0) {
            holder.icon.setImageResource(option.iconRes);
            holder.icon.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setVisibility(View.GONE);
        }
        boolean selected = current != null && current.equalsIgnoreCase(option.label);
        holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onClick(option.label));
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static final class OptionHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView desc;
        final ImageView check;

        OptionHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivOptionIcon);
            title = itemView.findViewById(R.id.tvOptionTitle);
            desc = itemView.findViewById(R.id.tvOptionDesc);
            check = itemView.findViewById(R.id.ivOptionCheck);
        }
    }
}