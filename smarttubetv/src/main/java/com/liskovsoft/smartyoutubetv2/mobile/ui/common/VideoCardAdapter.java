package com.liskovsoft.smartyoutubetv2.mobile.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

public class VideoCardAdapter extends RecyclerView.Adapter<VideoCardAdapter.CardHolder> {
    public interface OnClick {
        void onClick(Video video);
    }

    private final List<Video> mVideos;
    private final OnClick mOnClick;

    public VideoCardAdapter(List<Video> videos, OnClick onClick) {
        mVideos = videos != null ? videos : new ArrayList<>();
        mOnClick = onClick;
    }

    @NonNull
    @Override
    public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mobile_item_video_card, parent, false);
        return new CardHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardHolder holder, int position) {
        holder.bind(mVideos.get(position), mOnClick);
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    static class CardHolder extends RecyclerView.ViewHolder {
        private final ImageView mThumbnail;
        private final TextView mTitle;
        private final TextView mAuthor;
        private final TextView mBadge;

        CardHolder(@NonNull View itemView) {
            super(itemView);
            mThumbnail = itemView.findViewById(R.id.mobile_card_thumbnail);
            mTitle = itemView.findViewById(R.id.mobile_card_title);
            mAuthor = itemView.findViewById(R.id.mobile_card_author);
            mBadge = itemView.findViewById(R.id.mobile_card_badge);
        }

        void bind(Video video, OnClick onClick) {
            mTitle.setText(video.title);
            mAuthor.setText(video.author);

            if (video.badge != null && !video.badge.isEmpty()) {
                mBadge.setText(video.badge);
                mBadge.setVisibility(View.VISIBLE);
            } else if (video.isLive) {
                mBadge.setText(R.string.mobile_live_badge);
                mBadge.setVisibility(View.VISIBLE);
            } else {
                mBadge.setVisibility(View.GONE);
            }

            String imageUrl = video.cardImageUrl != null ? video.cardImageUrl : video.altCardImageUrl;
            Glide.with(mThumbnail.getContext())
                    .load(imageUrl)
                    .centerCrop()
                    .into(mThumbnail);

            itemView.setOnClickListener(v -> {
                if (onClick != null) {
                    onClick.onClick(video);
                }
            });
        }
    }
}
