package org.cloudbus.cloudsim.examples.baseline.advsacautoscaler;

import java.util.Random;

/**
 * Small dependency-free multilayer perceptron for the experiment's actor and
 * critics. It uses three 256-unit ReLU hidden layers and a linear output layer.
 * Gradients are supplied by the SAC objective and propagated by backpropagation.
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

    /**
     * Applies one online SGD update for a gradient dLoss/dOutput.
     * The supplied gradient is not modified.
     */
    void train(final double[] input, final double[] outputGradient, final double learningRate) {
        if (outputGradient.length != sizes[sizes.length - 1]) {
            throw new IllegalArgumentException("Output gradient dimension mismatch.");
        }
        ForwardPass pass = forward(input);
        double[] delta = outputGradient.clone();
        for (int layer = weights.length - 1; layer >= 0; layer--) {
            double[] previous = pass.activations[layer];
            double[] nextDelta = new double[sizes[layer]];
            for (int out = 0; out < sizes[layer + 1]; out++) {
                double gradient = delta[out];
                if (layer < weights.length - 1
                        && pass.preactivations[layer + 1][out] <= 0.0) {
                    gradient = 0.0;
                }
                for (int in = 0; in < sizes[layer]; in++) {
                    nextDelta[in] += weights[layer][out][in] * gradient;
                    weights[layer][out][in] -= learningRate * gradient * previous[in];
                }
                biases[layer][out] -= learningRate * gradient;
            }
            delta = nextDelta;
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
