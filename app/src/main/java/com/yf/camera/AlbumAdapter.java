package com.yf.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.Holder> {
    public interface OnItemClickListener { void onClick(int position, MediaItem item); }

    private final Context context;
    private final List<MediaItem> items = new ArrayList<>();
    private final Set<Integer> selectedPositions = new HashSet<>();
    private final OnItemClickListener listener;
    private boolean multiSelectMode = false;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public AlbumAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<MediaItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        selectedPositions.clear();
        selectedPosition = items.isEmpty() ? RecyclerView.NO_POSITION : 0;
        notifyDataSetChanged();
    }

    public void setMultiSelectMode(boolean enabled) {
        multiSelectMode = enabled;
        selectedPositions.clear();
        if (enabled && selectedPosition >= 0 && selectedPosition < items.size()) {
            selectedPositions.add(selectedPosition);
        }
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() { return multiSelectMode; }

    public int getSelectedCount() { return selectedPositions.size(); }

    public List<MediaItem> getItemsSnapshot() { return new ArrayList<>(items); }

    public MediaItem getItem(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public MediaItem getSelectedItem() {
        if (selectedPosition < 0 || selectedPosition >= items.size()) return null;
        return items.get(selectedPosition);
    }

    public List<MediaItem> getSelectedItems() {
        List<MediaItem> result = new ArrayList<>();
        for (Integer pos : selectedPositions) {
            if (pos != null && pos >= 0 && pos < items.size()) result.add(items.get(pos));
        }
        return result;
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) return;
        if (selectedPositions.contains(position)) selectedPositions.remove(position);
        else selectedPositions.add(position);
        selectedPosition = position;
        notifyItemChanged(position);
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album_media, parent, false);
        return new Holder(view);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = items.get(position);
        holder.videoBadge.setVisibility(item.isVideo() ? View.VISIBLE : View.GONE);
        boolean selected = multiSelectMode && selectedPositions.contains(position);
        holder.selectionBorder.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.selectedBadge.setVisibility(selected ? View.VISIBLE : View.GONE);
        Bitmap thumb = ThumbnailUtil.loadThumbnail(context, item.uri, 360);
        if (thumb != null) holder.thumb.setImageBitmap(thumb);
        else holder.thumb.setImageResource(R.drawable.app_logo);
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (multiSelectMode) {
                toggleSelection(pos);
                if (listener != null && pos < items.size()) listener.onClick(pos, items.get(pos));
            } else {
                selectedPosition = pos;
                if (listener != null && pos < items.size()) listener.onClick(pos, items.get(pos));
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return true;
            if (!multiSelectMode) {
                multiSelectMode = true;
                selectedPositions.clear();
                selectedPositions.add(pos);
                selectedPosition = pos;
                notifyDataSetChanged();
            } else {
                toggleSelection(pos);
            }
            if (listener != null && pos < items.size()) listener.onClick(pos, items.get(pos));
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView thumb;
        TextView videoBadge;
        TextView selectedBadge;
        View selectionBorder;
        Holder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.ivThumb);
            videoBadge = itemView.findViewById(R.id.tvVideoBadge);
            selectedBadge = itemView.findViewById(R.id.tvSelectedBadge);
            selectionBorder = itemView.findViewById(R.id.selectionBorder);
        }
    }
}
