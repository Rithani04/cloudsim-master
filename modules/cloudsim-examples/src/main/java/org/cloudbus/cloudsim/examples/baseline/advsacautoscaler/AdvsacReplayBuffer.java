package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fixed-capacity ring replay buffer for off-policy discrete SAC training.
 * Stored states are defensive copies via AdvsacState; action values are indices
 * in [0, 4], not VM deltas.
 */
public final class AdvsacReplayBuffer {
    private final AdvsacTransitionSample[] samples;
    private final Random random;
    private int nextIndex;
    private int size;

    public AdvsacReplayBuffer() {
        this(AdvsacConfig.REPLAY_BUFFER_CAPACITY, AdvsacConfig.RANDOM_SEED);
    }

    public AdvsacReplayBuffer(final int capacity, final long seed) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Replay buffer capacity must be positive.");
        }
        samples = new AdvsacTransitionSample[capacity];
        random = new Random(seed);
    }

    public void add(
            final AdvsacState state,
            final int actionIndex,
            final double reward,
            final AdvsacState nextState,
            final boolean done) {
        if (state == null || nextState == null) {
            throw new IllegalArgumentException("State and nextState are required.");
        }
        if (actionIndex < 0 || actionIndex >= AdvsacConfig.ACTION_DIMENSION) {
            throw new IllegalArgumentException("Action index must be in [0,4].");
        }
        if (!Double.isFinite(reward)) {
            throw new IllegalArgumentException("Reward must be finite.");
        }

        samples[nextIndex] = new AdvsacTransitionSample(
                state.toArray(), actionIndex, reward, nextState.toArray(), done);
        nextIndex = (nextIndex + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    public List<AdvsacTransitionSample> sample(final int batchSize) {
        if (batchSize <= 0 || batchSize > size) {
            throw new IllegalArgumentException(
                    "Batch size must be in [1," + size + "].");
        }

        List<AdvsacTransitionSample> batch = new ArrayList<>(batchSize);
        boolean[] selected = new boolean[size];
        while (batch.size() < batchSize) {
            int index = random.nextInt(size);
            if (!selected[index]) {
                selected[index] = true;
                batch.add(samples[index]);
            }
        }
        return batch;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return samples.length;
    }

    public boolean canSample(final int batchSize) {
        return batchSize > 0 && size >= batchSize;
    }

    /** Immutable replay entry. */
    public static final class AdvsacTransitionSample {
        private final double[] state;
        private final int actionIndex;
        private final double reward;
        private final double[] nextState;
        private final boolean done;

        private AdvsacTransitionSample(
                final double[] state,
                final int actionIndex,
                final double reward,
                final double[] nextState,
                final boolean done) {
            this.state = state.clone();
            this.actionIndex = actionIndex;
            this.reward = reward;
            this.nextState = nextState.clone();
            this.done = done;
        }

        public double[] getState() { return state.clone(); }
        public int getActionIndex() { return actionIndex; }
        public double getReward() { return reward; }
        public double[] getNextState() { return nextState.clone(); }
        public boolean isDone() { return done; }
    }
}
