package org.evosuite.ga.operators.selection;

import org.evosuite.ga.Chromosome;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@inheritDoc}
 *
 * Select individual by highest fitness
 */
public class BestKSelection<T extends Chromosome> extends SelectionFunction<T> {

    /**
     * {@inheritDoc}
     *
     * Population has to be sorted!
     */
    @Override
    public List<T> select(List<T> population, int number) {
        return population.stream().limit(number).collect(Collectors.toList());
    }

    /**
     * Selects index of best offspring.
     *
     * Population has to be sorted!
     */
    @Override
    public int getIndex(List<T> population) {
        return 0;
    }
}
