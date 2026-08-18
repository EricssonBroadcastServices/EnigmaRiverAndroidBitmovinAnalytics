package com.redbeemedia.enigma.bitmovinanalytics;

import static org.junit.Assert.assertEquals;

import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.event.PlayerEvent;
import com.bitmovin.player.api.event.data.SeekPosition;
import com.bitmovin.player.api.source.Source;
import com.redbeemedia.enigma.core.analytics.AnalyticsReporter;
import com.redbeemedia.enigma.core.time.Duration;
import com.redbeemedia.enigma.core.time.ITimeProvider;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class BitmovinAnalyticsListenerTest {
    @Test
    public void timeChangedReportsEventTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.playbackTimeOffsetToRelativeTime = -100.0;

        harness.listener.onTimeChanged(new PlayerEvent.TimeChanged(125.5));
        
        // At this point, the event is only buffered.
        // Run the runnable to flush it manually since tests have no Looper thread by default.
        if (harness.listener.timeChangedRunnable != null) {
            harness.listener.timeChangedRunnable.run();
        }

        harness.reporter.assertSingleEvent("timeChanged", 25500L, 125.5);
    }

    @Test
    public void seekReportsTargetTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.playbackTimeOffsetToRelativeTime = -2.5;

        harness.listener.onSeek(new PlayerEvent.Seek(
                new SeekPosition(newSourceProxy(), 5.0),
                new SeekPosition(newSourceProxy(), 72.75)));

        harness.reporter.assertSingleEvent("scrubbedTo", 70250L);
    }

    @Test
    public void seekedReportsCurrentPlayerTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.currentTime = 14.5;
        harness.player.playbackTimeOffsetToRelativeTime = -2.0;

        harness.listener.onSeeked(new PlayerEvent.Seeked(new SeekPosition(newSourceProxy(), 0), new SeekPosition(newSourceProxy(), 0)));

        harness.reporter.assertSingleEvent("seeked", 12500L);
    }

    @Test
    public void playReportsEventTimeAsOffsetAndPreservesTime() {
        Harness harness = new Harness();
        harness.player.playbackTimeOffsetToRelativeTime = -20.0;

        harness.listener.onPlay(new PlayerEvent.Play(33.25));

        harness.reporter.assertSingleEvent("play", 13250L, 33.25);
    }

    @Test
    public void firstPlayingReportsStartedWithEventTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.currentTime = 99.0;
        harness.player.playbackTimeOffsetToRelativeTime = -10.0;

        harness.listener.onPlaying(new PlayerEvent.Playing(15.5));

        harness.reporter.assertSingleEvent("started", 5500L);
    }

    @Test
    public void laterPlayingReportsResumedWithEventTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.playbackTimeOffsetToRelativeTime = -10.0;
        harness.listener.onPlaying(new PlayerEvent.Playing(15.5));
        harness.reporter.clear();

        harness.listener.onPlaying(new PlayerEvent.Playing(20.25));

        harness.reporter.assertSingleEvent("resumed", 10250L);
    }

    @Test
    public void pausedReportsEventTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.playbackTimeOffsetToRelativeTime = -3.0;
        harness.listener.onPlaying(new PlayerEvent.Playing(10.0));
        harness.reporter.clear();

        harness.listener.onPaused(new PlayerEvent.Paused(12.5));

        harness.reporter.assertSingleEvent("paused", 9500L);
    }

    @Test
    public void playbackFinishedReportsCurrentPlayerTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.currentTime = 45.25;
        harness.player.playbackTimeOffsetToRelativeTime = -5.0;

        harness.listener.onPlaybackFinished(new PlayerEvent.PlaybackFinished());

        harness.reporter.assertSingleEvent("completed", 40250L);
        assertEquals(1, harness.finishedCalls);
    }

    @Test
    public void destroyReportsCurrentPlayerTimeAsOffset() {
        Harness harness = new Harness();
        harness.player.currentTime = 30.75;
        harness.player.playbackTimeOffsetToRelativeTime = -0.5;

        harness.listener.onDestroy(new PlayerEvent.Destroy());

        harness.reporter.assertSingleEvent("aborted", 30250L);
        assertEquals(1, harness.destroyedCalls);
    }

    private static Source newSourceProxy() {
        return newProxy(Source.class, new DefaultInvocationHandler());
    }

    private static <T> T newProxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static class Harness {
        private final PlayerInvocationHandler player = new PlayerInvocationHandler();
        private final RecordingAnalyticsReporter reporter = new RecordingAnalyticsReporter();
        private final BitmovinAnalyticsListener listener = new BitmovinAnalyticsListener(player.proxy());
        private int finishedCalls = 0;
        private int destroyedCalls = 0;

        private Harness() {
            listener.attach(
                    reporter,
                    () -> finishedCalls++,
                    () -> destroyedCalls++,
                    null,
                    "VOD",
                    "test-asset-id");
        }
    }

    private static class PlayerInvocationHandler extends DefaultInvocationHandler {
        private double currentTime = 0.0;
        private double playbackTimeOffsetToRelativeTime = 0.0;
        private double duration = 60.0;

        private Player proxy() {
            return newProxy(Player.class, this);
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "getCurrentTime":
                    return currentTime;
                case "getPlaybackTimeOffsetToRelativeTime":
                    return playbackTimeOffsetToRelativeTime;
                case "getDuration":
                    return duration;
                case "getSource":
                    return null;
                default:
                    return super.invoke(proxy, method, args);
            }
        }
    }

    private static class DefaultInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "toString":
                    return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == Void.TYPE) {
                return null;
            } else if (returnType == Boolean.TYPE) {
                return false;
            } else if (returnType == Byte.TYPE) {
                return (byte) 0;
            } else if (returnType == Short.TYPE) {
                return (short) 0;
            } else if (returnType == Integer.TYPE) {
                return 0;
            } else if (returnType == Long.TYPE) {
                return 0L;
            } else if (returnType == Float.TYPE) {
                return 0f;
            } else if (returnType == Double.TYPE) {
                return 0d;
            } else if (returnType == Character.TYPE) {
                return '\0';
            }
            return null;
        }
    }

    private static class RecordingAnalyticsReporter extends AnalyticsReporter {
        private final List<EventRecord> events = new ArrayList<>();

        private RecordingAnalyticsReporter() {
            super(new TestTimeProvider(), jsonObject -> {}, 0L);
        }

        @Override
        public void playbackStarted(long offsetTime, String playMode, String mediaLocator, Long referenceTime,
                Integer bitrate, String programId, Integer duration, String drmLicenseDurationRemainingTime,
                String drmRenewAllowed, String drmPlaybackDurationRemaining, String drmLicenseType,
                String drmPlayAllowed) {
            record("started", offsetTime);
        }

        @Override
        public void playbackPaused(long offsetTime) {
            record("paused", offsetTime);
        }

        @Override
        public void playbackResumed(long offsetTime) {
            record("resumed", offsetTime);
        }

        @Override
        public void playbackCompleted(long offsetTime) {
            record("completed", offsetTime);
        }

        @Override
        public void playbackAborted(long offsetTime) {
            record("aborted", offsetTime);
        }

        @Override
        public void playbackScrubbedTo(long offsetTime) {
            record("scrubbedTo", offsetTime);
        }

        @Override
        public void playbackPlay(long offsetTime, Double time) {
            record("play", offsetTime, time);
        }

        @Override
        public void playbackSeeked(long offsetTime) {
            record("seeked", offsetTime);
        }

        @Override
        public void playbackTimeChanged(long offsetTime, Double time) {
            record("timeChanged", offsetTime, time);
        }

        private void record(String name, long offsetTime) {
            events.add(new EventRecord(name, offsetTime, null));
        }

        private void record(String name, long offsetTime, Double time) {
            events.add(new EventRecord(name, offsetTime, time));
        }

        private void clear() {
            events.clear();
        }

        private void assertSingleEvent(String name, long offsetTime) {
            assertEquals(1, events.size());
            EventRecord event = events.get(0);
            assertEquals(name, event.name);
            assertEquals(offsetTime, event.offsetTime);
        }

        private void assertSingleEvent(String name, long offsetTime, Double time) {
            assertSingleEvent(name, offsetTime);
            assertEquals(time, events.get(0).time);
        }
    }

    private static class EventRecord {
        private final String name;
        private final long offsetTime;
        private final Double time;

        private EventRecord(String name, long offsetTime, Double time) {
            this.name = name;
            this.offsetTime = offsetTime;
            this.time = time;
        }
    }

    private static class TestTimeProvider implements ITimeProvider {
        @Override
        public long getTime() {
            return 0;
        }

        @Override
        public boolean isReady(Duration maxBlocktime) {
            return true;
        }
    }
}
