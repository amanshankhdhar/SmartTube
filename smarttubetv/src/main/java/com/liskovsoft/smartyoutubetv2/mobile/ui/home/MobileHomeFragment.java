package com.liskovsoft.smartyoutubetv2.mobile.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileMainActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

public class MobileHomeFragment extends Fragment {
    private RecyclerView mRowsView;
    private ProgressBar mProgressBar;
    private TextView mEmptyView;
    private HomeRowsAdapter mAdapter;
    private List<VideoGroup> mPendingRows;
    private Boolean mPendingLoading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRowsView = view.findViewById(R.id.mobile_home_rows);
        mProgressBar = view.findViewById(R.id.mobile_home_progress);
        mEmptyView = view.findViewById(R.id.mobile_home_empty);

        mAdapter = new HomeRowsAdapter(video -> {
            if (getActivity() instanceof MobileMainActivity) {
                ((MobileMainActivity) getActivity()).onVideoClicked(video);
            }
        });

        mRowsView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRowsView.setAdapter(mAdapter);
        mRowsView.setHasFixedSize(true);

        if (mPendingRows != null) {
            applyRows(mPendingRows);
        }

        if (mPendingLoading != null) {
            applyLoading(mPendingLoading);
        }
    }

    /** Called by MobileMainActivity whenever BrowsePresenter pushes new Home data. */
    public void setRows(List<VideoGroup> rows) {
        mPendingRows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);

        if (mAdapter != null) {
            applyRows(mPendingRows);
        }
    }

    public void setLoading(boolean loading) {
        mPendingLoading = loading;

        if (mProgressBar != null) {
            applyLoading(loading);
        }
    }

    private void applyRows(List<VideoGroup> rows) {
        mAdapter.setRows(rows);

        if (mEmptyView != null) {
            mEmptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void applyLoading(boolean loading) {
        mProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
