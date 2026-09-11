package com.poliku.polygoplus.data;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.R;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.databinding.ItemChatMessageBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.Holder> {
    private final List<JSONObject> messages = new ArrayList<>();
    private final String currentUser;

    public ChatMessageAdapter(String currentUser) {
        this.currentUser = currentUser == null ? "" : currentUser;
    }

    public void submit(JSONArray source) {
        messages.clear();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject message = source.optJSONObject(i);
                if (message != null) {
                    messages.add(message);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void submitMessages(List<PolyGoApi.Message> source) {
        messages.clear();
        if (source != null) {
            for (PolyGoApi.Message m : source) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("sender", m.sender);
                    o.put("text", m.text);
                    o.put("time", m.time);
                    o.put("mine", m.mine);
                    messages.add(o);
                } catch (Exception ignored) {
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        JSONObject message = messages.get(position);
        boolean mine = message.optBoolean("mine", currentUser.equals(message.optString("sender")));
        ItemChatMessageBinding binding = holder.binding;
        Context context = binding.getRoot().getContext();

        binding.messageBody.setText(message.optString("text"));
        binding.messageTime.setText(formatTime(message.optLong("time", 0)));

        if (mine) {
            binding.layoutMessageContainer.setGravity(Gravity.END);
            binding.messageBubble.setCardBackgroundColor(ContextCompat.getColor(context, R.color.pks_blue));
            binding.messageBody.setTextColor(ContextCompat.getColor(context, R.color.white));
            binding.messageTime.setTextColor(ContextCompat.getColor(context, R.color.white));
            
            // Adjust margins for gravity
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.messageBubble.getLayoutParams();
            params.gravity = Gravity.END;
            binding.messageBubble.setLayoutParams(params);
        } else {
            binding.layoutMessageContainer.setGravity(Gravity.START);
            binding.messageBubble.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white));
            binding.messageBody.setTextColor(ContextCompat.getColor(context, R.color.airbnb_ink));
            binding.messageTime.setTextColor(ContextCompat.getColor(context, R.color.airbnb_muted));

            // Adjust margins for gravity
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) binding.messageBubble.getLayoutParams();
            params.gravity = Gravity.START;
            binding.messageBubble.setLayoutParams(params);
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "";
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(timestamp));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemChatMessageBinding binding;

        Holder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
