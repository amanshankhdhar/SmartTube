package com.liskovsoft.smartyoutubetv2.mobile.ui.main;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.home.MobileHomeFragment;
import com.liskovsoft.smartyoutubetv2.mobile.ui.placeholder.ComingSoonFragment;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * PHASE 1 SCOPE:
 *  - Home tab: real data via BrowsePresenter (same presenter/business logic the TV BrowseFragment uses).
 *  - Shorts / Subscriptions / Library tabs: placeholders (ComingSoonFragment). Wiring them to real
 *    data later is expected to be small, since it's the same BrowsePresenter#selectSection() call
 *    this class already makes for Home - see #selectSection below.
 *  - Side drawer: static items from the spec, tapping just closes the drawer for now.
 *  - Tapping a video reuses BrowsePresenter#onVideoItemClicked(), i.e. the exact same navigation
 *    the TV UI uses (opens the existing PlaybackActivity). No new player code in this phase.
 */
public class MobileMainActivity extends MotherActivity implements BrowseView {
    private DrawerLayout mDrawerLayout;
    private BrowsePresenter mBrowsePresenter;
    private MobileHomeFragment mHomeFragment;
    private boolean mProgressBarShowing;
    private final List<VideoGroup> mHomeRows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_activity_main);

        mDrawerLayout = findViewById(R.id.mobile_drawer_layout);
        TextView hamburger = findViewById(R.id.mobile_hamburger);
        NavigationView navigationView = findViewById(R.id.mobile_nav_view);
        BottomNavigationView bottomNav = findViewById(R.id.mobile_bottom_nav);

        hamburger.setOnClickListener(v -> mDrawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            mDrawerLayout.closeDrawers();
            // Real screens (Settings, Accounts, SponsorBlock, etc.) land in a later phase.
            Toast.makeText(this, item.getTitle() + " " + getString(R.string.mobile_coming_soon_suffix), Toast.LENGTH_SHORT).show();
            return true;
        });

        bottomNav.setOnItemSelectedListener(this::onBottomNavItemSelected);

        mHomeFragment = new MobileHomeFragment();
        showFragment(mHomeFragment);

        mBrowsePresenter = BrowsePresenter.instance(this);
        mBrowsePresenter.setView(this);
        mBrowsePresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBrowsePresenter != null) {
            mBrowsePresenter.onViewResumed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mBrowsePresenter != null) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBrowsePresenter != null) {
            mBrowsePresenter.onViewDestroyed();
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
            return;
        }

        super.onBackPressed();
    }

    /** Called by card adapters when a video is tapped. Delegates to the shared presenter, same as TV. */
    public void onVideoClicked(Video video) {
        if (mBrowsePresenter != null && video != null) {
            mBrowsePresenter.onVideoItemClicked(video);
        }
    }

    private boolean onBottomNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.mobile_nav_home) {
            showFragment(mHomeFragment);
            return true;
        } else if (id == R.id.mobile_nav_shorts) {
            showFragment(ComingSoonFragment.newInstance(getString(R.string.mobile_tab_shorts)));
            return true;
        } else if (id == R.id.mobile_nav_subscriptions) {
            showFragment(ComingSoonFragment.newInstance(getString(R.string.mobile_tab_subscriptions)));
            return true;
        } else if (id == R.id.mobile_nav_library) {
            showFragment(ComingSoonFragment.newInstance(getString(R.string.mobile_tab_library)));
            return true;
        }

        return false;
    }

    private void showFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.mobile_content, fragment);
        transaction.commitAllowingStateLoss();
    }

    // ---- BrowseView ----
    // Same interface the TV BrowseFragment implements. BrowsePresenter doesn't know or care
    // whether it's talking to leanback rows or a phone RecyclerView.

    @Override
    public void addSection(int index, BrowseSection section) {
        // Bookkeeping only for now - Phase 1 renders whichever section selectSection() marks active.
    }

    @Override
    public void removeSection(BrowseSection category) {
        // Not needed until more sections are wired up.
    }

    @Override
    public void removeAllSections() {
        mHomeRows.clear();
        if (mHomeFragment != null) {
            mHomeFragment.setRows(mHomeRows);
        }
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        // Phase 1 only has one real content tab (Home), so render whatever section
        // BrowsePresenter currently has active - this also covers its "boot into Music
        // instead of an empty Home" fallback for signed-out/fresh installs.
        mHomeRows.clear();
        if (mHomeFragment != null) {
            mHomeFragment.setRows(mHomeRows);
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) {
            return;
        }

        if (group.getAction() == VideoGroup.ACTION_REPLACE || group.getAction() == VideoGroup.ACTION_SYNC) {
            List<VideoGroup> stale = new ArrayList<>();
            for (VideoGroup existing : mHomeRows) {
                if (existing.getTitle() != null && existing.getTitle().equals(group.getTitle())) {
                    stale.add(existing);
                }
            }
            mHomeRows.removeAll(stale);
        }

        mHomeRows.add(group);

        if (mHomeFragment != null) {
            mHomeFragment.setRows(mHomeRows);
        }

        showProgressBar(false);
    }

    @Override
    public void updateSection(SettingsGroup group) {
        // Settings screen isn't part of the mobile UI yet.
    }

    @Override
    public void clearSection(BrowseSection section) {
        mHomeRows.clear();
        if (mHomeFragment != null) {
            mHomeFragment.setRows(mHomeRows);
        }
    }

    @Override
    public void selectSectionItem(int index) {
        // D-pad focus concept; not applicable on touch.
    }

    @Override
    public void selectSectionItem(Video item) {
        // D-pad focus concept; not applicable on touch.
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (data != null && data.getMessage() != null) {
            Toast.makeText(this, data.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressBarShowing = show;
        if (mHomeFragment != null) {
            mHomeFragment.setLoading(show);
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBarShowing;
    }

    @Override
    public void focusOnContent() {
        // D-pad focus concept; not applicable on touch.
    }

    @Override
    public boolean isEmpty() {
        return mHomeRows.isEmpty();
    }

    @Override
    public void updateBadge() {
        // Account/notification badge - later phase.
    }
}
