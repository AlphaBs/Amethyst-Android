package net.kdt.pojavlaunch.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PreparationCallbacksTest {
    @Test
    public void progressAndCompletionUseTheCallbackExecutorAndPreserveOrder() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        RecordingListener listener = new RecordingListener();
        PreparationCallbacks callbacks = new PreparationCallbacks(queue::add, listener);
        GamePreparationProgress progress = filesProgress();
        callbacks.progress(progress);
        callbacks.ready();
        assertTrue(listener.events.isEmpty());
        drain(queue);
        assertEquals(Arrays.asList("progress", "ready"), listener.events);
        assertSame(progress, listener.progress);
    }

    @Test
    public void lateProgressAndDuplicateTerminalSignalsAreIgnoredAfterFailure() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        RecordingListener listener = new RecordingListener();
        PreparationCallbacks callbacks = new PreparationCallbacks(queue::add, listener);
        Throwable failure = new IllegalStateException("download failed");
        callbacks.failure(failure);
        callbacks.progress(filesProgress());
        callbacks.ready();
        callbacks.failure(new IllegalStateException("late failure"));
        drain(queue);
        assertEquals(Arrays.asList("failure"), listener.events);
        assertSame(failure, listener.failure);
    }

    @Test
    public void finishingOneRequestDoesNotStopAnotherRequestsProgress() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        PreparationCallbacks firstRequest = new PreparationCallbacks(queue::add, first);
        PreparationCallbacks secondRequest = new PreparationCallbacks(queue::add, second);
        firstRequest.ready();
        firstRequest.progress(filesProgress());
        secondRequest.progress(filesProgress());
        drain(queue);
        assertEquals(Arrays.asList("ready"), first.events);
        assertEquals(Arrays.asList("progress"), second.events);
    }

    private static GamePreparationProgress filesProgress() {
        return new GamePreparationProgress(
                GamePreparationProgress.Stage.GAME_FILES,
                GamePreparationProgress.Unit.BYTES,
                4096,
                8192
        );
    }

    private static void drain(ArrayDeque<Runnable> queue) {
        while (!queue.isEmpty()) {
            queue.remove()
                    .run();
        }
    }

    private static final class RecordingListener implements AmethystRuntime.PreparationListener {
        final List<String> events = new ArrayList<>();
        GamePreparationProgress progress;
        Throwable failure;

        @Override
        public void onProgress(GamePreparationProgress value) {
            events.add("progress");
            progress = value;
        }

        @Override
        public void onReady() {
            events.add("ready");
        }

        @Override
        public void onFailure(Throwable value) {
            events.add("failure");
            failure = value;
        }
    }
}
