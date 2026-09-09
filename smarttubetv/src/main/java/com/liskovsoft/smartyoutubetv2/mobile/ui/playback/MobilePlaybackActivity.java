package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.video.VideoListener;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * PHASE 2a SCOPE: core fullscreen controls + gestures only.
 *  - Real: play/pause, seek (bar + double-tap), position/duration display, title/channel,
 *    brightness/volume swipe gestures, auto-hide controls.
 *  - Deferred to later sub-phases (stubbed here, safe no-ops): suggestions/related videos (2c),
 *    quality/speed/captions settings sheet (2b), comments (2d), subtitles rendering, SponsorBlock
 *    seekbar segments, storyboard preview, live chat.
 *  - Not implemented: PIP, background playback modes (matches nothing being lost vs. before -
 *    the old TV player screen didn't have working touch equivalents for these either).
 *
 * Reuses the exact same shared engine pieces as the TV player (ExoPlayerController,
 * ExoPlayerInitializer, RestoreTrackSelector, CustomOverridesRenderersFactory) - only the
 * rendering surface and controls are new. See PlaybackFragment.java (TV) for the reference
 * this was built from.
 */
public class MobilePlaybackActivity extends MotherActivity implements PlaybackView, com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView {
    private static final int SEEK_STEP_MS = 10_000;
    private static final int CONTROLS_AUTO_HIDE_MS = 3_000;
    private static final int PROGRESS_TICK_MS = 400;
    private static final float DRAG_THRESHOLD_PX = 12f;

    private PlaybackPresenter mPlaybackPresenter;
    private ExoPlayerController mExoPlayerController;
    private ExoPlayerInitializer mPlayerInitializer;
    private SimpleExoPlayer mPlayer;
    private AspectRatioFrameLayout mAspectFrame;
    private SurfaceView mSurfaceView;
    private View mControlsRoot;
    private View mTouchSurface;
    private TextView mTitleView;
    private TextView mChannelView;
    private TextView mPositionView;
    private TextView mDurationView;
    private TextView mGestureFeedback;
    private SeekBar mSeekBar;
    private ImageButton mPlayPauseButton;
    private ProgressBar mBufferingSpinner;
    private GestureDetector mGestureDetector;

    private Video mVideo;
    private boolean mControlsShown = true;
    private boolean mEngineInitialized;
    private boolean mEngineBlocked;
    private boolean mUserSeeking;
    private int mResizeMode = RESIZE_MODE_DEFAULT;
    private final android.util.SparseIntArray mButtonStates = new android.util.SparseIntArray();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mProgressTick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            mHandler.postDelayed(this, PROGRESS_TICK_MS);
        }
    };
    private final Runnable mAutoHideControls = () -> showControls(false);

    private float mDragStartY;
    private boolean mIsDragging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        applyImmersiveMode();
        setContentView(R.layout.mobile_activity_playback);

        bindViews();
        setupGestures();
        setupControlListeners();

        mPlaybackPresenter = PlaybackPresenter.instance(this);
        mPlaybackPresenter.setView(this);

        mExoPlayerController = new ExoPlayerController(this, mPlaybackPresenter);
        createPlayer();
        mPlaybackPresenter.onEngineInitialized(); // triggers VideoLoaderController to actually load the video

        mPlaybackPresenter.onViewInitialized();
    }

    private void bindViews() {
        mAspectFrame = findViewById(R.id.mobile_player_aspect_frame);
        mSurfaceView = findViewById(R.id.mobile_player_surface);
        mControlsRoot = findViewById(R.id.mobile_player_controls);
        mTouchSurface = findViewById(R.id.mobile_player_touch_surface);
        mTitleView = findViewById(R.id.mobile_player_title);
        mChannelView = findViewById(R.id.mobile_player_channel);
        mPositionView = findViewById(R.id.mobile_player_position);
        mDurationView = findViewById(R.id.mobile_player_duration);
        mGestureFeedback = findViewById(R.id.mobile_player_gesture_feedback);
        mSeekBar = findViewById(R.id.mobile_player_seekbar);
        mPlayPauseButton = findViewById(R.id.mobile_player_play_pause);
        mBufferingSpinner = findViewById(R.id.mobile_player_buffering);
    }

    private void setupControlListeners() {
        ImageButton back = findViewById(R.id.mobile_player_back);
        back.setOnClickListener(v -> onBackPressed());

        mPlayPauseButton.setOnClickListener(v -> {
            if (mExoPlayerController != null) {
                mExoPlayerController.setPlayWhenReady(!mExoPlayerController.getPlayWhenReady());
            }
            scheduleAutoHide();
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mExoPlayerController != null) {
                    long duration = mExoPlayerController.getDurationMs();
                    mPositionView.setText(formatTime(duration * progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUserSeeking = true;
                cancelAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mExoPlayerController != null) {
                    long duration = mExoPlayerController.getDurationMs();
                    mExoPlayerController.setPositionMs(duration * seekBar.getProgress() / 1000);
                }
                mUserSeeking = false;
                scheduleAutoHide();
            }
        });
    }

    private void setupGestures() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                showControls(!mControlsShown);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (mExoPlayerController == null) {
                    return true;
                }

                boolean isLeftSide = e.getX() < mTouchSurface.getWidth() / 2f;
                long newPos = mExoPlayerController.getPositionMs() + (isLeftSide ? -SEEK_STEP_MS : SEEK_STEP_MS);
                mExoPlayerController.setPositionMs(Math.max(0, newPos));
                showGestureFeedback(isLeftSide ? "-10s" : "+10s");
                return true;
            }
        });

        mTouchSurface.setOnTouchListener((v, event) -> {
            mGestureDetector.onTouchEvent(event);
            handleDrag(event, v);
            return true;
        });
    }

    private void handleDrag(MotionEvent event, View v) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragStartY = event.getY();
                mIsDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaY = mDragStartY - event.getY();

                if (!mIsDragging && Math.abs(deltaY) < DRAG_THRESHOLD_PX) {
                    return;
                }

                mIsDragging = true;
                boolean isLeftSide = event.getX() < v.getWidth() / 2f;
                float percentChange = deltaY / v.getHeight();

                if (isLeftSide) {
                    adjustBrightness(percentChange);
                } else {
                    adjustVolume(percentChange);
                }

                mDragStartY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                break;
        }
    }

    private void adjustBrightness(float delta) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        float current = attrs.screenBrightness;

        if (current < 0) {
            current = 0.5f;
        }

        current = Math.max(0.01f, Math.min(1f, current + delta));
        attrs.screenBrightness = current;
        getWindow().setAttributes(attrs);
        showGestureFeedback("Brightness " + Math.round(current * 100) + "%");
    }

    private void adjustVolume(float delta) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);

        if (am == null) {
            return;
        }

        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int change = Math.round(delta * max);
        int newVolume = Math.max(0, Math.min(max, current + change));
        am.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        showGestureFeedback("Volume " + Math.round(100f * newVolume / Math.max(1, max)) + "%");
    }

    private void showGestureFeedback(String text) {
        mGestureFeedback.setText(text);
        mGestureFeedback.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideGestureFeedback);
        mHandler.postDelayed(mHideGestureFeedback, 700);
    }

    private final Runnable mHideGestureFeedback = () -> mGestureFeedback.setVisibility(View.GONE);

    private void scheduleAutoHide() {
        mHandler.removeCallbacks(mAutoHideControls);
        mHandler.postDelayed(mAutoHideControls, CONTROLS_AUTO_HIDE_MS);
    }

    private void cancelAutoHide() {
        mHandler.removeCallbacks(mAutoHideControls);
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ---- Engine construction (mirrors PlaybackFragment#createPlayer on TV) ----

    private void createPlayer() {
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        mPlayerInitializer = new ExoPlayerInitializer(this);
        CustomOverridesRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(this);
        mPlayer = mPlayerInitializer.createPlayer(this, renderersFactory, trackSelector);

        mExoPlayerController.setPlayer(mPlayer);
        mExoPlayerController.setPlayerView(this);

        mPlayer.setVideoSurfaceView(mSurfaceView);

        // Keeps the video letterboxed to its real aspect ratio instead of stretching/cropping.
        mPlayer.addVideoListener(new VideoListener() {
            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                if (height != 0 && width != 0) {
                    mAspectFrame.setAspectRatio((width * pixelWidthHeightRatio) / height);
                }
            }
        });

        mEngineInitialized = true;
    }

    private void releasePlayer() {
        mHandler.removeCallbacksAndMessages(null);

        if (mPlaybackPresenter != null && mEngineInitialized) {
            mPlaybackPresenter.onEngineReleased();
        }

        if (mExoPlayerController != null) {
            mExoPlayerController.release();
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }

        if (mPlayerInitializer != null) {
            mPlayerInitializer.release();
        }

        mEngineInitialized = false;
    }

    private void updateProgress() {
        if (mExoPlayerController == null || mUserSeeking) {
            return;
        }

        long position = mExoPlayerController.getPositionMs();
        long duration = mExoPlayerController.getDurationMs();

        if (duration > 0) {
            mSeekBar.setProgress((int) (1000 * position / duration));
        }

        mPositionView.setText(formatTime(position));
        mDurationView.setText(formatTime(duration));
        mPlayPauseButton.setImageResource(mExoPlayerController.isPlaying() ?
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        mBufferingSpinner.setVisibility(mExoPlayerController.isLoading() ? View.VISIBLE : View.GONE);
    }

    private static String formatTime(long ms) {
        if (ms < 0) {
            ms = 0;
        }

        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    // ---- Activity lifecycle ----

    @Override
    protected void onResume() {
        super.onResume();

        if (mPlaybackPresenter != null) {
            mPlaybackPresenter.onViewResumed();
        }

        mHandler.post(mProgressTick);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mPlaybackPresenter != null) {
            mPlaybackPresenter.onViewPaused();
        }

        mHandler.removeCallbacks(mProgressTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPlaybackPresenter != null) {
            mPlaybackPresenter.onViewDestroyed();
        }

        releasePlayer();
    }

    // ---- PlayerUI: controls/overlay visibility ----

    @Override
    public void showControls(boolean show) {
        mControlsShown = show;
        mControlsRoot.animate().cancel();
        mControlsRoot.animate()
                .alpha(show ? 1f : 0f)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (show) {
                            mControlsRoot.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            mControlsRoot.setVisibility(View.INVISIBLE);
                        }
                    }
                });

        if (show) {
            scheduleAutoHide();
        } else {
            cancelAutoHide();
        }
    }

    @Override
    public boolean isControlsShown() {
        return mControlsShown;
    }

    @Override
    public void showOverlay(boolean show) {
        showControls(show);
    }

    @Override
    public boolean isOverlayShown() {
        return mControlsShown;
    }

    @Override
    public void showProgressBar(boolean show) {
        mBufferingSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---- PlayerUI: title/text ----

    @Override
    public void setTitle(String title) {
        mTitleView.setText(title);
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        // No channel avatar in the 2a layout yet - visual polish for a later pass.
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        // Scrub-preview title - deferred (needs the storyboard/thumbnail preview work too).
    }

    @Override
    public void setNextTitle(Video nextVideo) {
        // "Up next" preview - deferred to 2c alongside suggestions.
    }

    @Override
    public void updateEndingTime() {
        // Deferred - not essential for core playback.
    }

    // ---- PlayerUI: suggestions (deferred to sub-phase 2c) ----

    @Override
    public void updateSuggestions(VideoGroup group) {
        // Related videos panel - sub-phase 2c.
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int index) {
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) {
    }

    @Override
    public void focusSuggestedItem(Video video) {
    }

    @Override
    public void resetSuggestedPosition() {
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return true;
    }

    @Override
    public void clearSuggestions() {
    }

    @Override
    public void showSuggestions(boolean show) {
    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    // ---- PlayerUI: misc (deferred) ----

    @Override
    public int getButtonState(int buttonId) {
        return mButtonStates.get(buttonId, BUTTON_OFF);
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        mButtonStates.put(buttonId, buttonState);
    }

    @Override
    public void showDebugInfo(boolean show) {
    }

    @Override
    public void showSubtitles(boolean show) {
        // No subtitle view in the 2a layout yet.
    }

    @Override
    public void loadStoryboard() {
        // Seekbar thumbnail preview - deferred.
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        // SponsorBlock seekbar markers - visual polish for a later pass.
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        // Live chat isn't part of the mobile UI yet.
    }

    // ---- PlayerEngine: playback (delegates to the shared ExoPlayerController) ----

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        return mExoPlayerController.getDurationMs();
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem option) {
        mExoPlayerController.selectFormat(option);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    // ---- PlayerEngine: not backed by ExoPlayerController directly (2a: safe defaults) ----

    @Override
    public boolean isEngineInitialized() {
        return mEngineInitialized;
    }

    @Override
    public void restartEngine() {
        // Deferred via Handler: this can be called from inside the presenter's own controller
        // dispatch loop (e.g. ErrorFixerController's long-buffering recovery), so rebuilding
        // the engine synchronously here would re-enter that dispatch. Posting avoids that.
        mHandler.post(() -> {
            releasePlayer();
            mExoPlayerController = new ExoPlayerController(this, mPlaybackPresenter);
            createPlayer();
            mPlaybackPresenter.onEngineInitialized();
        });
    }

    @Override
    public void reloadPlayback() {
        restartEngine();
    }

    @Override
    public void blockEngine(boolean block) {
        mEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        return false; // No PIP support in 2a.
    }

    @Override
    public void setResizeMode(int mode) {
        mResizeMode = mode;
        mAspectFrame.setResizeMode(mode);
    }

    @Override
    public int getResizeMode() {
        return mResizeMode;
    }

    @Override
    public void setZoomPercents(int percents) {
        // Surface zoom - deferred (TV implements this via its own SurfaceView wrapper).
    }

    @Override
    public void setAspectRatio(float ratio) {
        if (ratio > 0) {
            mAspectFrame.setAspectRatio(ratio);
        }
    }

    @Override
    public void setRotationAngle(int angle) {
        // Deferred.
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
        // Deferred.
    }

    @Override
    public void setVideoGravity(int gravity) {
        // Deferred.
    }

    // ---- PlayerManager ----

    @Override
    public void setVideo(Video item) {
        mVideo = item;

        if (item != null) {
            mChannelView.setText(item.author);
        }
    }

    @Override
    public Video getVideo() {
        return mVideo;
    }

    @Override
    public void finish() {
        finishReally();
    }

    @Override
    public void finishReally() {
        releasePlayer();
        super.finish();
    }

    @Override
    public void showBackground(String url) {
        // Audio-only background art - deferred (no background playback mode in 2a).
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        // Deferred alongside showBackground().
    }

    @Override
    public void resetPlayerState() {
        // NOTE: mVideo is deliberately NOT cleared here. VideoLoaderController#loadVideo()
        // calls setVideo(item) immediately followed by resetPlayerState() - clearing mVideo
        // here was wiping out the video that was just set, breaking every getVideo() call
        // downstream (processFormatInfo()'s null guard, error handling, etc).
        mButtonStates.clear();
    }

    @Override
    public boolean isEmbed() {
        return false; // This is the fullscreen player, not the inline/embed one.
    }

    // ---- PlayerView (SmartTube's own tiny interface, not ExoPlayer's PlayerView widget) ----

    @Override
    public void setQualityInfo(String info) {
        // Shown in the settings sheet in sub-phase 2b - no UI for it yet.
    }
}
