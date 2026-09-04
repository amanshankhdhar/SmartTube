package com.liskovsoft.smartyoutubetv2.mobile.ui.placeholder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.tv.R;

public class ComingSoonFragment extends Fragment {
    private static final String ARG_LABEL = "arg_label";

    public static ComingSoonFragment newInstance(String label) {
        ComingSoonFragment fragment = new ComingSoonFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LABEL, label);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mobile_fragment_coming_soon, container, false);

        TextView label = view.findViewById(R.id.mobile_coming_soon_label);
        String tabName = getArguments() != null ? getArguments().getString(ARG_LABEL) : null;

        if (tabName != null) {
            label.setText(getString(R.string.mobile_coming_soon_format, tabName));
        }

        return view;
    }
}
