package org.evosuite.ga;

import java.util.Objects;

/**
 * A wrapper class to treat fitness functions for chromosomes of type {@code T} as fitness functions
 * for chromosomes of type {@code U}. This is mainly useful to satisfy the type checker; other than
 * wrapping and retrieving a given fitness functions, nothing useful can actually be done with this
 * mocking class. One possible use case is to bridge the gap between {@code TestSuiteChromosome}s
 * and {@code TestChromosome}s, as is the case for {@code MOSA} in
 * {@link org.evosuite.strategy.MOSuiteStrategy}.
 *
 * @param <T> the chromosome type of the wrapped fitness function
 * @param <U> the chromosome type of the fitness function this mock should masquerade as
 * @author Sebastian Schweikl
 */
public class FitnessFunctionMock<T extends Chromosome<T>, U extends Chromosome<U>>
        extends FitnessFunction<U> {

    private static final long serialVersionUID = -2764090795456211662L;

    /**
     * The wrapped fitness function.
     */
    private final FitnessFunction<T> wrapped;

    /**
     * Creates a new mock of the given fitness function.
     *
     * @param wrapped the fitness function to mock; must not be {@code null}
     */
    public FitnessFunctionMock(final FitnessFunction<T> wrapped) {
        this.wrapped = Objects.requireNonNull(wrapped);
    }

    /**
     * Returns the wrapped fitness function.
     *
     * @return the wrapped fitness function
     */
    public FitnessFunction<T> getWrapped() {
        return wrapped;
    }

    /**
     * Throws an {@code UnsupportedOperationException} when called.
     *
     * @return never completes, always throws {@code UnsupportedOperationException}
     * @throws UnsupportedOperationException always fails, never succeeds
     */
    @Override
    public double getFitness(final U individual) {
        throw new UnsupportedOperationException("getFitness() called on mock");
    }

    /**
     * Tells whether the wrapped fitness function is a maximizing fitness function.
     *
     * @return {@code true} if maximizing, {@code false} otherwise
     */
    @Override
    public boolean isMaximizationFunction() {
        return wrapped.isMaximizationFunction();
    }
}
