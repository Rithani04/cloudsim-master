package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.List;
import java.util.Random;

/**
 * Plain discrete Soft Actor-Critic baseline.
 *
 * The policy emits a categorical distribution over five scaling actions.
 * Twin critics estimate Q(s,a) for all discrete actions; target critics,
 * entropy-regularized Bellman targets and Polyak target updates are used.
 * No adversarial state perturbation, uncertainty estimation, distribution
 * shift detection or risk regularization is included in this baseline.
 */
public final class AdvsacAgent {
    private static final double ENTROPY_TEMPERATURE = 0.2;
    private static final double PROBABILITY_FLOOR = 1.0e-12;

    private final AdvsacMlp actor;
    private final AdvsacMlp criticOne;
    private final AdvsacMlp criticTwo;
    private final AdvsacMlp targetCriticOne;
    private final AdvsacMlp targetCriticTwo;
    private final AdvsacReplayBuffer replayBuffer;
    private final Random random;

    public AdvsacAgent() {
        long seed = AdvsacConfig.RANDOM_SEED;
        actor = new AdvsacMlp(AdvsacConfig.STATE_DIMENSION,
                AdvsacConfig.ACTION_DIMENSION, seed);
        criticOne = new AdvsacMlp(AdvsacConfig.STATE_DIMENSION,
                AdvsacConfig.ACTION_DIMENSION, seed + 1);
        criticTwo = new AdvsacMlp(AdvsacConfig.STATE_DIMENSION,
                AdvsacConfig.ACTION_DIMENSION, seed + 2);
        targetCriticOne = criticOne.copy();
        targetCriticTwo = criticTwo.copy();
        replayBuffer = new AdvsacReplayBuffer();
        random = new Random(seed + 3);
    }

    /** Samples from the actor during training; selects the most likely action in evaluation. */
    public int selectAction(final AdvsacState state, final boolean training) {
        double[] probabilities = policyProbabilities(state.toArray());
        if (!training) {
            int best = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[best]) {
                    best = i;
                }
            }
            return best;
        }

        double draw = random.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (draw <= cumulative || i == probabilities.length - 1) {
                return i;
            }
        }
        return probabilities.length - 1;
    }

    public void remember(
            final AdvsacState state,
            final int actionIndex,
            final double reward,
            final AdvsacState nextState,
            final boolean done) {
        replayBuffer.add(state, actionIndex, reward, nextState, done);
    }

    /** Performs one minibatch update once the replay buffer has enough samples. */
    public boolean trainStep() {
        if (!replayBuffer.canSample(AdvsacConfig.BATCH_SIZE)) {
            return false;
        }

        List<AdvsacReplayBuffer.AdvsacTransitionSample> batch =
                replayBuffer.sample(AdvsacConfig.BATCH_SIZE);
        for (AdvsacReplayBuffer.AdvsacTransitionSample sample : batch) {
            double[] state = sample.getState();
            double[] nextState = sample.getNextState();
            double[] nextProbabilities = policyProbabilities(nextState);
            double[] targetQ1 = targetCriticOne.predict(nextState);
            double[] targetQ2 = targetCriticTwo.predict(nextState);
            double softValue = 0.0;

            for (int action = 0; action < AdvsacConfig.ACTION_DIMENSION; action++) {
                double minQ = Math.min(targetQ1[action], targetQ2[action]);
                double logProbability = Math.log(Math.max(
                        PROBABILITY_FLOOR, nextProbabilities[action]));
                softValue += nextProbabilities[action]
                        * (minQ - ENTROPY_TEMPERATURE * logProbability);
            }

            double target = sample.getReward();
            if (!sample.isDone()) {
                target += AdvsacConfig.DISCOUNT_FACTOR * softValue;
            }

            updateCritic(criticOne, state, sample.getActionIndex(), target);
            updateCritic(criticTwo, state, sample.getActionIndex(), target);
            updateActor(state);
        }

        targetCriticOne.softUpdateFrom(criticOne, AdvsacConfig.TARGET_UPDATE_TAU);
        targetCriticTwo.softUpdateFrom(criticTwo, AdvsacConfig.TARGET_UPDATE_TAU);
        return true;
    }

    public int getReplayBufferSize() {
        return replayBuffer.size();
    }

    private void updateCritic(
            final AdvsacMlp critic,
            final double[] state,
            final int selectedAction,
            final double target) {
        double[] prediction = critic.predict(state);
        double[] gradient = new double[AdvsacConfig.ACTION_DIMENSION];
        gradient[selectedAction] = 2.0 * (prediction[selectedAction] - target);
        critic.train(state, gradient, AdvsacConfig.CRITIC_LEARNING_RATE);
    }

    private void updateActor(final double[] state) {
        double[] probabilities = policyProbabilities(state);
        double[] qOne = criticOne.predict(state);
        double[] qTwo = criticTwo.predict(state);
        double expectedObjective = 0.0;
        double[] perActionObjective = new double[AdvsacConfig.ACTION_DIMENSION];

        for (int action = 0; action < perActionObjective.length; action++) {
            double probability = Math.max(PROBABILITY_FLOOR, probabilities[action]);
            perActionObjective[action] = ENTROPY_TEMPERATURE * Math.log(probability)
                    - Math.min(qOne[action], qTwo[action]);
            expectedObjective += probabilities[action] * perActionObjective[action];
        }

        double[] logitsGradient = new double[AdvsacConfig.ACTION_DIMENSION];
        for (int action = 0; action < logitsGradient.length; action++) {
            logitsGradient[action] = probabilities[action]
                    * (perActionObjective[action] - expectedObjective);
        }
        actor.train(state, logitsGradient, AdvsacConfig.ACTOR_LEARNING_RATE);
    }

    private double[] policyProbabilities(final double[] state) {
        double[] logits = actor.predict(state);
        double max = logits[0];
        for (int i = 1; i < logits.length; i++) {
            max = Math.max(max, logits[i]);
        }

        double sum = 0.0;
        double[] probabilities = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probabilities[i] = Math.exp(Math.max(-60.0, logits[i] - max));
            sum += probabilities[i];
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            for (int i = 0; i < probabilities.length; i++) {
                probabilities[i] = 1.0 / probabilities.length;
            }
            return probabilities;
        }
        for (int i = 0; i < probabilities.length; i++) {
            probabilities[i] /= sum;
        }
        return probabilities;
    }
}
