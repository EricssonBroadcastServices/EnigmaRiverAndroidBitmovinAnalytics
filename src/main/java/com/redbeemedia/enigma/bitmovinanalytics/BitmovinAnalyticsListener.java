// SPDX-FileCopyrightText: 2026 Red Bee Media Ltd <https://www.redbeemedia.com/\>
//
// SPDX-License-Identifier: MIT

package com.redbeemedia.enigma.bitmovinanalytics;

import android.os.Handler;
import android.os.Looper;

import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.event.EventListener;
import com.bitmovin.player.api.event.PlayerEvent;
import com.bitmovin.player.api.event.SourceEvent;
import com.redbeemedia.enigma.core.analytics.AnalyticsPlayResponseData;
import com.redbeemedia.enigma.core.analytics.AnalyticsReporter;
import com.redbeemedia.enigma.core.analytics.IPlayerAnalyticsListener;

public class BitmovinAnalyticsListener implements IPlayerAnalyticsListener {
    private final Player player;
    private AnalyticsReporter reporter;
    private String playMode;
    private String mediaLocator;
    private boolean hasStartedAtLeastOnce = false;
    private AnalyticsPlayResponseData analyticsPlayResponseData;
    private Runnable onPlaybackFinishedEvent;
    private Runnable onPlaybackDestroyedEvent;
    private String assetId;

    private Handler timeChangedHandler;
    /* package-protected */ Runnable timeChangedRunnable;
    /* package-protected */ PlayerEvent.TimeChanged lastTimeChangedEvent;
    /* package-protected */ long intervalMillis;

    private final EventListener<PlayerEvent.Ready> readyListener = this::onReady;
    private final EventListener<PlayerEvent.Playing> playingListener = this::onPlaying;
    private final EventListener<PlayerEvent.Paused> pausedListener = this::onPaused;
    private final EventListener<PlayerEvent.PlaybackFinished> playbackFinishedListener = this::onPlaybackFinished;
    private final EventListener<PlayerEvent.Error> errorListener = this::onError;
    private final EventListener<PlayerEvent.Destroy> destroyListener = this::onDestroy;
    private final EventListener<PlayerEvent.StallStarted> stallStartedListener = this::onStallStarted;
    private final EventListener<PlayerEvent.StallEnded> stallEndedListener = this::onStallEnded;
    private final EventListener<PlayerEvent.Seek> seekListener = this::onSeek;
    private final EventListener<PlayerEvent.VideoPlaybackQualityChanged> videoPlaybackQualityChangedListener = this::onVideoPlaybackQualityChanged;
    private final EventListener<PlayerEvent.AdStarted> adStartedListener = this::onAdStarted;
    private final EventListener<PlayerEvent.AdFinished> adFinishedListener = this::onAdFinished;
    private final EventListener<PlayerEvent.AdError> adErrorListener = this::onAdError;
    private final EventListener<PlayerEvent.CastStarted> castStartedListener = this::onCastStarted;
    private final EventListener<PlayerEvent.CastStopped> castStoppedListener = this::onCastStopped;
    private final EventListener<PlayerEvent.SourceAdded> sourceAddedListener = this::onSourceAdded;
    private final EventListener<PlayerEvent.SourceRemoved> sourceRemovedListener = this::onSourceRemoved;
    private final EventListener<PlayerEvent.DownloadFinished> downloadFinishedListener = this::onDownloadFinished;
    private final EventListener<PlayerEvent.AdBreakFinished> adBreakFinishedListener = this::onAdBreakFinished;
    private final EventListener<PlayerEvent.AdBreakStarted> adBreakStartedListener = this::onAdBreakStarted;
    private final EventListener<PlayerEvent.AdSkipped> adSkippedListener = this::onAdSkipped;
    private final EventListener<PlayerEvent.AudioPlaybackQualityChanged> audioPlaybackQualityChangedListener = this::onAudioPlaybackQualityChanged;
    private final EventListener<PlayerEvent.CastStart> castStartListener = this::onCastStart;
    private final EventListener<PlayerEvent.DvrWindowExceeded> dvrWindowExceededListener = this::onDvrWindowExceeded;
    private final EventListener<PlayerEvent.Muted> mutedListener = this::onMuted;
    private final EventListener<PlayerEvent.Play> playListener = this::onPlay;
    private final EventListener<PlayerEvent.Seeked> seekedListener = this::onSeeked;
    private final EventListener<PlayerEvent.TimeChanged> timeChangedListener = this::onTimeChanged;
    private final EventListener<PlayerEvent.TimeShifted> timeShiftedListener = this::onTimeShifted;
    private final EventListener<PlayerEvent.Unmuted> unmutedListener = this::onUnmuted;
    private final EventListener<PlayerEvent.Warning> playerWarningListener = this::onPlayerWarning;

    private final EventListener<SourceEvent.AudioTrackChanged> audioTrackChangedListener = this::onAudioTrackChanged;
    private final EventListener<SourceEvent.DurationChanged> durationChangedListener = this::onDurationChanged;
    private final EventListener<SourceEvent.Loaded> sourceLoadedListener = this::onSourceLoaded;
    private final EventListener<SourceEvent.Warning> sourceWarningListener = this::onSourceWarning;
    private final EventListener<SourceEvent.DrmDataParsed> drmDataParsedListener = this::onDrmDataParsed;

    public BitmovinAnalyticsListener(Player player) {
        this.player = player;
    }

    @Override
    public void attach(
            AnalyticsReporter reporter,
            Runnable onPlaybackFinishedEvent,
            Runnable onPlaybackDestroyedEvent,
            AnalyticsPlayResponseData analyticsPlayResponseData,
            String playMode,
            String assetId
    ) {
        this.reporter = reporter;
        this.reporter.setPlayerTechnology(TECHNOLOGY);
        this.reporter.setSdkVersion(Player.getSdkVersion());
        this.playMode = playMode;
        this.analyticsPlayResponseData = analyticsPlayResponseData;
        this.onPlaybackFinishedEvent = onPlaybackFinishedEvent;
        this.onPlaybackDestroyedEvent = onPlaybackDestroyedEvent;
        this.assetId = assetId;

        hasStartedAtLeastOnce = false;
        lastTimeChangedEvent = null;

        intervalMillis = (analyticsPlayResponseData != null && analyticsPlayResponseData.postIntervalSeconds > 0)
                ? analyticsPlayResponseData.postIntervalSeconds * 1000L
                : DEFAULT_POST_INTERVAL;

        try {
            timeChangedHandler = new Handler(Looper.getMainLooper());
        } catch (Exception e) {
            timeChangedHandler = null; // For unit tests
        }

        timeChangedRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastTimeChangedEvent != null) {
                    reporter.playbackTimeChanged(getOffsetTime(lastTimeChangedEvent.getTime()), lastTimeChangedEvent.getTime());
                    lastTimeChangedEvent = null;
                }
                if (timeChangedHandler != null) {
                    timeChangedHandler.postDelayed(this, intervalMillis);
                }
            }
        };

        if (timeChangedHandler != null) {
            timeChangedHandler.postDelayed(timeChangedRunnable, intervalMillis);
        }

        player.on(PlayerEvent.Ready.class, readyListener);
        player.on(PlayerEvent.Playing.class, playingListener);
        player.on(PlayerEvent.Paused.class, pausedListener);
        player.on(PlayerEvent.PlaybackFinished.class, playbackFinishedListener);
        player.on(PlayerEvent.Error.class, errorListener);
        player.on(PlayerEvent.Destroy.class, destroyListener);
        player.on(PlayerEvent.StallStarted.class, stallStartedListener);
        player.on(PlayerEvent.StallEnded.class, stallEndedListener);
        player.on(PlayerEvent.Seek.class, seekListener);
        player.on(PlayerEvent.VideoPlaybackQualityChanged.class, videoPlaybackQualityChangedListener);
        player.on(PlayerEvent.AdStarted.class, adStartedListener);
        player.on(PlayerEvent.AdFinished.class, adFinishedListener);
        player.on(PlayerEvent.AdError.class, adErrorListener);
        player.on(PlayerEvent.CastStarted.class, castStartedListener);
        player.on(PlayerEvent.CastStopped.class, castStoppedListener);
        player.on(PlayerEvent.SourceAdded.class, sourceAddedListener);
        player.on(PlayerEvent.SourceRemoved.class, sourceRemovedListener);
        player.on(PlayerEvent.DownloadFinished.class, downloadFinishedListener);

        player.on(PlayerEvent.AdBreakFinished.class, adBreakFinishedListener);
        player.on(PlayerEvent.AdBreakStarted.class, adBreakStartedListener);
        player.on(PlayerEvent.AdSkipped.class, adSkippedListener);
        player.on(PlayerEvent.AudioPlaybackQualityChanged.class, audioPlaybackQualityChangedListener);
        player.on(PlayerEvent.CastStart.class, castStartListener);
        player.on(PlayerEvent.DvrWindowExceeded.class, dvrWindowExceededListener);
        player.on(PlayerEvent.Muted.class, mutedListener);
        player.on(PlayerEvent.Play.class, playListener);
        player.on(PlayerEvent.Seeked.class, seekedListener);
        player.on(PlayerEvent.TimeChanged.class, timeChangedListener);
        player.on(PlayerEvent.TimeShifted.class, timeShiftedListener);
        player.on(PlayerEvent.Unmuted.class, unmutedListener);
        player.on(PlayerEvent.Warning.class, playerWarningListener);

        player.on(SourceEvent.AudioTrackChanged.class, audioTrackChangedListener);
        player.on(SourceEvent.DurationChanged.class, durationChangedListener);
        player.on(SourceEvent.Loaded.class, sourceLoadedListener);
        player.on(SourceEvent.Warning.class, sourceWarningListener);
    }

    @Override
    public void detach() {
        if (timeChangedHandler != null) {
            timeChangedHandler.removeCallbacksAndMessages(null);
            timeChangedHandler = null;
        }
        timeChangedRunnable = null;
        lastTimeChangedEvent = null;

        player.off(PlayerEvent.Ready.class, readyListener);
        player.off(PlayerEvent.Playing.class, playingListener);
        player.off(PlayerEvent.Paused.class, pausedListener);
        player.off(PlayerEvent.PlaybackFinished.class, playbackFinishedListener);
        player.off(PlayerEvent.Error.class, errorListener);
        player.off(PlayerEvent.Destroy.class, destroyListener);
        player.off(PlayerEvent.StallStarted.class, stallStartedListener);
        player.off(PlayerEvent.StallEnded.class, stallEndedListener);
        player.off(PlayerEvent.Seek.class, seekListener);
        player.off(PlayerEvent.VideoPlaybackQualityChanged.class, videoPlaybackQualityChangedListener);
        player.off(PlayerEvent.AdStarted.class, adStartedListener);
        player.off(PlayerEvent.AdFinished.class, adFinishedListener);
        player.off(PlayerEvent.AdError.class, adErrorListener);
        player.off(PlayerEvent.CastStarted.class, castStartedListener);
        player.off(PlayerEvent.CastStopped.class, castStoppedListener);
        player.off(PlayerEvent.SourceAdded.class, sourceAddedListener);
        player.off(PlayerEvent.SourceRemoved.class, sourceRemovedListener);
        player.off(PlayerEvent.DownloadFinished.class, downloadFinishedListener);

        player.off(PlayerEvent.AdBreakFinished.class, adBreakFinishedListener);
        player.off(PlayerEvent.AdBreakStarted.class, adBreakStartedListener);
        player.off(PlayerEvent.AdSkipped.class, adSkippedListener);
        player.off(PlayerEvent.AudioPlaybackQualityChanged.class, audioPlaybackQualityChangedListener);
        player.off(PlayerEvent.CastStart.class, castStartListener);
        player.off(PlayerEvent.DvrWindowExceeded.class, dvrWindowExceededListener);
        player.off(PlayerEvent.Muted.class, mutedListener);
        player.off(PlayerEvent.Play.class, playListener);
        player.off(PlayerEvent.Seeked.class, seekedListener);
        player.off(PlayerEvent.TimeChanged.class, timeChangedListener);
        player.off(PlayerEvent.TimeShifted.class, timeShiftedListener);
        player.off(PlayerEvent.Unmuted.class, unmutedListener);
        player.off(PlayerEvent.Warning.class, playerWarningListener);

        player.off(SourceEvent.AudioTrackChanged.class, audioTrackChangedListener);
        player.off(SourceEvent.DurationChanged.class, durationChangedListener);
        player.off(SourceEvent.Loaded.class, sourceLoadedListener);
        player.off(SourceEvent.Warning.class, sourceWarningListener);

        reporter = null;
    }

    @Override
    public long getCurrentTimeInSeconds() {
        return getOffsetTime() / 1000;
    }

    @Override
    public String getTechnology() {
        return TECHNOLOGY;
    }

    private void onDownloadFinished(PlayerEvent.DownloadFinished downloadFinished) {
        switch (downloadFinished.getDownloadType()) {
            case ManifestDash:
                analyticsPlayResponseData.streamingTechnology = "DASH";
                mediaLocator = downloadFinished.getUrl();
                break;
            case ManifestHlsMaster:
            case ManifestHlsVariant:
                analyticsPlayResponseData.streamingTechnology = "HLS";
                mediaLocator = downloadFinished.getUrl();
                break;
        }
    }

    private long getOffsetTime() {
        return getOffsetTime(player.getCurrentTime());
    }

    private long getOffsetTime(double playbackTimeSeconds) {
        return (long) ((playbackTimeSeconds + player.getPlaybackTimeOffsetToRelativeTime()) * 1000);
    }

    public void onReady(PlayerEvent.Ready event) {
        reporter.playbackPlayerReady(getOffsetTime(), TECHNOLOGY,
                com.bitmovin.player.api.Player.getSdkVersion());
    }

    public void onPlaying(PlayerEvent.Playing event) {
        long offsetTime = getOffsetTime(event.getTime());
        if (hasStartedAtLeastOnce) {
            reporter.playbackResumed(offsetTime);
        } else {
            hasStartedAtLeastOnce = true;
            sendPlaybackStartedEvent(offsetTime);
        }
    }

    private void sendPlaybackStartedEvent(long offsetTime) {
        Integer bitrate = null;
        if (player.getSource() != null && player.getSource().getSelectedVideoQuality() != null) {
            bitrate = player.getSource().getSelectedVideoQuality().getBitrate();
        }

        Integer duration = (int) (player.getDuration() * 1000);

        reporter.playbackStarted(offsetTime, playMode, mediaLocator, null, bitrate, assetId != null ? assetId : "",
                duration, null, null, null, null,
                null);
    }

    public void onPaused(PlayerEvent.Paused event) {
        if (hasStartedAtLeastOnce) {
            reporter.playbackPaused(getOffsetTime(event.getTime()));
        }
    }

    public void onPlaybackFinished(PlayerEvent.PlaybackFinished event) {
        reporter.playbackCompleted(getOffsetTime());
        onPlaybackFinishedEvent.run();
    }

    public void onError(PlayerEvent.Error event) {
        reporter.playbackError(new BitmovinPlaybackError(event));
    }

    public void onDestroy(PlayerEvent.Destroy event) {
        reporter.playbackAborted(getOffsetTime());
        onPlaybackDestroyedEvent.run();
    }

    public void onStallStarted(PlayerEvent.StallStarted event) {
        reporter.playbackBufferingStarted(getOffsetTime());
    }

    public void onStallEnded(PlayerEvent.StallEnded event) {
        reporter.playbackBufferingStopped(getOffsetTime());
    }

    public void onSeek(PlayerEvent.Seek event) {
        reporter.playbackScrubbedTo(getOffsetTime(event.getTo().getTime()));
    }

    public void onVideoPlaybackQualityChanged(
            PlayerEvent.VideoPlaybackQualityChanged event) {
        if (event.getNewVideoQuality() != null) {
            reporter.playbackBitrateChanged(getOffsetTime(), event.getNewVideoQuality().getBitrate());
        }
    }

    public void onAdStarted(PlayerEvent.AdStarted event) {
        if (event.getAd() != null) {
            reporter.playbackAdStarted(getOffsetTime(), event.getAd().getId());
        }
    }

    public void onAdFinished(PlayerEvent.AdFinished event) {
        if (event.getAd() != null) {
            reporter.playbackAdCompleted(getOffsetTime(), event.getAd().getId());
        }
    }

    public void onAdError(PlayerEvent.AdError event) {
        reporter.playbackAdFailed(getOffsetTime());
    }

    public void onCastStarted(PlayerEvent.CastStarted event) {
        reporter.playbackStartCasting(getOffsetTime());
    }

    public void onCastStopped(PlayerEvent.CastStopped event) {
        reporter.playbackStopCasting(getOffsetTime());
    }

    private void onSourceAdded(PlayerEvent.SourceAdded sourceAdded) {
        sourceAdded.component1().on(SourceEvent.DrmDataParsed.class, drmDataParsedListener);
    }

    private void onSourceRemoved(PlayerEvent.SourceRemoved sourceRemoved) {
        sourceRemoved.component1().off(SourceEvent.DrmDataParsed.class, drmDataParsedListener);
    }

    private void onDrmDataParsed(SourceEvent.DrmDataParsed drmDataParsed) {
        reporter.playbackDrm(getOffsetTime());
    }

    public void onAdBreakFinished(PlayerEvent.AdBreakFinished event) {
        reporter.playbackAdBreakFinished(getOffsetTime(),
                event.getAdBreak() != null ? event.getAdBreak().getId() : null);
    }

    public void onAdBreakStarted(PlayerEvent.AdBreakStarted event) {
        reporter.playbackAdBreakStarted(getOffsetTime(),
                event.getAdBreak() != null ? event.getAdBreak().getId() : null);
    }

    public void onAdSkipped(PlayerEvent.AdSkipped event) {
        if (event.getAd() != null) {
            reporter.playbackAdSkipped(getOffsetTime(), event.getAd().getId());
        }
    }

    public void onAudioPlaybackQualityChanged(PlayerEvent.AudioPlaybackQualityChanged event) {
        reporter.playbackAudioPlaybackQualityChanged(getOffsetTime(),
                event.getNewAudioQuality() != null ? event.getNewAudioQuality().getBitrate() : null);
    }

    public void onCastStart(PlayerEvent.CastStart event) {
        reporter.playbackCastStart(getOffsetTime());
    }

    public void onDvrWindowExceeded(PlayerEvent.DvrWindowExceeded event) {
        reporter.playbackDVRWindowExceeded(getOffsetTime());
    }

    public void onMuted(PlayerEvent.Muted event) {
        reporter.playbackMuted(getOffsetTime());
    }

    public void onPlay(PlayerEvent.Play event) {
        reporter.playbackPlay(getOffsetTime(event.getTime()), event.getTime());
    }

    public void onSeeked(PlayerEvent.Seeked event) {
        reporter.playbackSeeked(getOffsetTime());
    }

    public void onTimeChanged(PlayerEvent.TimeChanged event) {
        lastTimeChangedEvent = event;
    }

    public void onTimeShifted(PlayerEvent.TimeShifted event) {
        reporter.playbackTimeShifted(getOffsetTime());
    }

    public void onUnmuted(PlayerEvent.Unmuted event) {
        reporter.playbackUnmuted(getOffsetTime());
    }

    public void onPlayerWarning(PlayerEvent.Warning event) {
        reporter.playbackWarning(getOffsetTime(), event.getCode() != null ? event.getCode().getValue() : null,
                event.getMessage());
    }

    public void onAudioTrackChanged(SourceEvent.AudioTrackChanged event) {
        reporter.playbackAudioChanged(getOffsetTime(),
                event.getNewAudioTrack() != null ? event.getNewAudioTrack().getId() : null);
    }

    public void onDurationChanged(SourceEvent.DurationChanged event) {
        reporter.playbackDurationChanged(getOffsetTime(), event.getTo());
    }

    public void onSourceLoaded(SourceEvent.Loaded event) {
        reporter.playbackSourceLoaded(getOffsetTime());
    }

    public void onSourceWarning(SourceEvent.Warning event) {
        reporter.playbackWarning(getOffsetTime(), event.getCode() != null ? event.getCode().getValue() : null,
                event.getMessage());
    }

    /* package-protected */ static final String TECHNOLOGY = "Bitmovin";
    /* package-protected */ static final Long DEFAULT_POST_INTERVAL = 60000L;
}
