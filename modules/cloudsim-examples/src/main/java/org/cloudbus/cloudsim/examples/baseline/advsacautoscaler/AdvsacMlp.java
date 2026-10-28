package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.Random;

/**
 * Dependency-free MLP for discrete SAC. Supports input gradients so the
 * ASTRA-inspired adversarial regularizer can penalize state-sensitive Q values.
 */
final class AdvsacMlp {
    private final int[] sizes;
    private final double[][][] weights;
    private final double[][] biases;
    private final Random random;

    AdvsacMlp(final int inputSize, final int outputSize, final long seed) {
        if (inputSize <= 0 || outputSize <= 0) {
            throw new IllegalArgumentException("Network dimensions must be positive.");
        }
        sizes = new int[] {inputSize, AdvsacConfig.ACTOR_HIDDEN_SIZE,
                AdvsacConfig.ACTOR_HIDDEN_SIZE, AdvsacConfig.ACTOR_HIDDEN_SIZE, outputSize};
        weights = new double[sizes.length - 1][][];
        biases = new double[sizes.length - 1][];
        random = new Random(seed);
        for (int layer = 0; layer < weights.length; layer++) {
            weights[layer] = new double[sizes[layer + 1]][sizes[layer]];
            biases[layer] = new double[sizes[layer + 1]];
            double limit = Math.sqrt(6.0 / (sizes[layer] + sizes[layer + 1]));
            for (int out = 0; out < sizes[layer + 1]; out++) {
                for (int in = 0; in < sizes[layer]; in++) {
                    weights[layer][out][in] = (random.nextDouble() * 2.0 - 1.0) * limit;
                }
            }
        }
    }

    private AdvsacMlp(final AdvsacMlp source) {
        sizes = source.sizes.clone();
        weights = new double[source.weights.length][][];
        biases = new double[source.biases.length][];
        random = new Random(AdvsacConfig.RANDOM_SEED);
        for (int layer = 0; layer < weights.length; layer++) {
            weights[layer] = new double[source.weights[layer].length][];
            for (int out = 0; out < weights[layer].length; out++) {
                weights[layer][out] = source.weights[layer][out].clone();
            }
            biases[layer] = source.biases[layer].clone();
        }
    }

    AdvsacMlp copy() {
        return new AdvsacMlp(this);
    }

    double[] predict(final double[] input) {
        return forward(input).activations[weights.length].clone();
    }

    /** Returns d(output[actionIndex])/d(input), without changing network weights. */
    double[] inputGradient(final double[] input, final int actionIndex) {
        if (actionIndex < 0 || actionIndex >= sizes[sizes.length - 1]) {
            throw new IllegalArgumentException("Invalid output index: " + actionIndex);
        }
        ForwardPass pass = forward(input);
        double[] delta = new double[sizes[sizes.length - 1]];
        delta[actionIndex] = 1.0;
        for (int layer = weights.length - 1; layer >= 0; layer--) {
            double[] previousDelta = new double[sizes[layer]];
            for (int out = 0; out < sizes[layer + 1]; out++) {
                double gradient = delta[out];
                if (layer < weights.length - 1 && pass.preactivations[layer][out] <= 0.0) {
                    gradient = 0.0;
                }
                for (int in = 0; in < sizes[layer]; in++) {
                    previousDelta[in] += weights[layer][out][in] * gradient;
                }
            }
            delta = previousDelta;
        }
        return delta;
    }

    /** Applies one SGD update for a supplied dLoss/dOutput vector. */
    void train(final double[] input, final double[] outputGradient, final double learningRate) {
        if (outputGradient == null || outputGradient.length != sizes[sizes.length - 1]) {
            throw new IllegalArgumentException("Output gradient dimension mismatch.");
        }
        ForwardPass pass = forward(input);
        double[][] deltas = new double[weights.length][];
        deltas[weights.length - 1] = outputGradient.clone();

        // Calculate every layer's delta using the same pre-update weights.
        for (int layer = weights.length - 2; layer >= 0; layer--) {
            double[] delta = new double[sizes[layer + 1]];
            for (int out = 0; out < sizes[layer + 1]; out++) {
                double downstream = 0.0;
                for (int next = 0; next < sizes[layer + 2]; next++) {
                    downstream += weights[layer + 1][next][out] * deltas[layer + 1][next];
                }
                delta[out] = pass.preactivations[layer][out] > 0.0 ? downstream : 0.0;
            }
            deltas[layer] = delta;
        }

        for (int layer = 0; layer < weights.length; layer++) {
            double[] previous = pass.activations[layer];
            for (int out = 0; out < sizes[layer + 1]; out++) {
                double gradient = deltas[layer][out];
                for (int in = 0; in < sizes[layer]; in++) {
                    weights[layer][out][in] -= learningRate * gradient * previous[in];
                }
                biases[layer][out] -= learningRate * gradient;
            }
        }
    }

    void softUpdateFrom(final AdvsacMlp source, final double tau) {
        if (source.weights.length != weights.length) {
            throw new IllegalArgumentException("Network architectures do not match.");
        }
        for (int layer = 0; layer < weights.length; layer++) {
            for (int out = 0; out < weights[layer].length; out++) {
                for (int in = 0; in < weights[layer][out].length; in++) {
                    weights[layer][out][in] = (1.0 - tau) * weights[layer][out][in]
                            + tau * source.weights[layer][out][in];
                }
                biases[layer][out] = (1.0 - tau) * biases[layer][out]
                        + tau * source.biases[layer][out];
            }
        }
    }

    private ForwardPass forward(final double[] input) {
        if (input == null || input.length != sizes[0]) {
            throw new IllegalArgumentException("Input dimension must be " + sizes[0] + ".");
        }
        double[][] activations = new double[weights.length + 1][];
        double[][] preactivations = new double[weights.length][];
        activations[0] = input.clone();
        for (int layer = 0; layer < weights.length; layer++) {
            double[] z = new double[sizes[layer + 1]];
            double[] a = new double[sizes[layer + 1]];
            for (int out = 0; out < z.length; out++) {
                double value = biases[layer][out];
                for (int in = 0; in < sizes[layer]; in++) {
                    value += weights[layer][out][in] * activations[layer][in];
                }
                z[out] = value;
                a[out] = layer == weights.length - 1 ? value : Math.max(0.0, value);
            }
            preactivations[layer] = z;
            activations[layer + 1] = a;
        }
        return new ForwardPass(activations, preactivations);
    }

    private static final class ForwardPass {
        private final double[][] activations;
        private final double[][] preactivations;

        private ForwardPass(final double[][] activations, final double[][] preactivations) {
            this.activations = activations;
            this.preactivations = preactivations;
        }
    }
}
