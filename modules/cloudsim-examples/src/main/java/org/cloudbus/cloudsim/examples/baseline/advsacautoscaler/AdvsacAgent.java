package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.List;
import java.util.Random;

/**
 * Plain discrete Soft Actor-Critic baseline.
 *
 * The policy emits a categorical distribution over five scaling actions.
 * ASTRA-inspired discrete AdvSAC: twin critics, entropy-regularized targets,
 * bounded worst-case state perturbations and critic input-gradient regularization.
 * This is a project adaptation, not a claim of exact ASTRA reproduction.
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
            double softValue = 0.0;

            for (int action = 0; action < AdvsacConfig.ACTION_DIMENSION; action++) {
                double[] gradient = minCriticInputGradient(nextState, action, true);
                double gradientL1 = l1Norm(gradient);
                double[] adversarialNextState = perturbState(nextState, gradient);
                double q1 = targetCriticOne.predict(adversarialNextState)[action];
                double q2 = targetCriticTwo.predict(adversarialNextState)[action];
                double robustQ = Math.min(q1, q2)
                        - AdvsacConfig.ADVERSARIAL_EPSILON * gradientL1;
                double logProbability = Math.log(Math.max(
                        PROBABILITY_FLOOR, nextProbabilities[action]));
                softValue += nextProbabilities[action]
                        * (robustQ - ENTROPY_TEMPERATURE * logProbability);
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
            double gradientPenalty = AdvsacConfig.ADVERSARIAL_EPSILON
                    * l1Norm(minCriticInputGradient(state, action, false));
            perActionObjective[action] = ENTROPY_TEMPERATURE * Math.log(probability)
                    - Math.min(qOne[action], qTwo[action]) + gradientPenalty;
            expectedObjective += probabilities[action] * perActionObjective[action];
        }

        double[] logitsGradient = new double[AdvsacConfig.ACTION_DIMENSION];
        for (int action = 0; action < logitsGradient.length; action++) {
            logitsGradient[action] = probabilities[action]
                    * (perActionObjective[action] - expectedObjective);
        }
        actor.train(state, logitsGradient, AdvsacConfig.ACTOR_LEARNING_RATE);
    }

    /** Gradient of min(Q1,Q2) for an action, using the critic that defines the minimum. */
    private double[] minCriticInputGradient(
            final double[] state, final int action, final boolean target) {
        AdvsacMlp first = target ? targetCriticOne : criticOne;
        AdvsacMlp second = target ? targetCriticTwo : criticTwo;
        double q1 = first.predict(state)[action];
        double q2 = second.predict(state)[action];
        return (q1 <= q2 ? first : second).inputGradient(state, action);
    }

    /** Worst-case first-order perturbation: -epsilon * sign(gradient), clipped to [0,1]. */
    private double[] perturbState(final double[] state, final double[] gradient) {
        double[] perturbed = state.clone();
        for (int i = 0; i < perturbed.length; i++) {
            double sign = gradient[i] > 0.0 ? 1.0 : gradient[i] < 0.0 ? -1.0 : 0.0;
            perturbed[i] = Math.max(AdvsacConfig.NORMALIZATION_MIN,
                    Math.min(AdvsacConfig.NORMALIZATION_MAX,
                            perturbed[i] - AdvsacConfig.ADVERSARIAL_EPSILON * sign));
        }
        return perturbed;
    }

    private double l1Norm(final double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += Math.abs(value);
        }
        return sum;
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
