package com.liskovsoft.smartyoutubetv2.mobile.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.mobile.ui.common.VideoCardAdapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

class HomeRowsAdapter extends RecyclerView.Adapter<HomeRowsAdapter.RowHolder> {
    interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    private final List<VideoGroup> mRows = new ArrayList<>();
    private final OnVideoClickListener mListener;

    HomeRowsAdapter(OnVideoClickListener listener) {
        mListener = listener;
    }

    void setRows(List<VideoGroup> rows) {
        mRows.clear();
        if (rows != null) {
            mRows.addAll(rows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_item_row, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        holder.bind(mRows.get(position), mListener);
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final RecyclerView mCards;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.mobile_row_title);
            mCards = itemView.findViewById(R.id.mobile_row_cards);
            mCards.setLayoutManager(new LinearLayoutManager(itemView.getContext(), RecyclerView.HORIZONTAL, false));
            mCards.setHasFixedSize(true);
        }

        void bind(VideoGroup group, OnVideoClickListener listener) {
            String title = group.getTitle();
            mTitle.setText(title);
            mTitle.setVisibility(title == null || title.isEmpty() ? View.GONE : View.VISIBLE);
            mCards.setAdapter(new VideoCardAdapter(group.getVideos(), listener::onVideoClick));
        }
    }
}
