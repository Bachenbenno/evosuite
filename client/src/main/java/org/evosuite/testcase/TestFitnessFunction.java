/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.testcase;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.graphs.GraphPool;
import org.evosuite.graphs.ccg.ClassCallGraph;
import org.evosuite.graphs.ccg.ClassCallNode;
import org.evosuite.graphs.cfg.RawControlFlowGraph;
import org.evosuite.instrumentation.InstrumentingClassLoader;
import org.evosuite.symbolic.instrument.ClassLoaderUtils;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.utils.generic.GenericConstructor;
import org.evosuite.utils.generic.GenericExecutable;
import org.evosuite.utils.generic.GenericMethod;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract base class for fitness functions for test case chromosomes.
 *
 * @author Gordon Fraser
 */
public abstract class TestFitnessFunction extends FitnessFunction<TestChromosome>
		implements Comparable<TestFitnessFunction> {

	private static final long serialVersionUID = 5602125855207061901L;

	// TODO: should/must they be synchronized?
	private static final Map<String, Class<?>> classCache = new HashMap<>();
	private static final Map<String, GenericExecutable<?, ?>> executableCache = new HashMap<>();

	protected final String className;
	protected final String methodName;
	private final boolean publicExecutable;
	private final boolean constructor;
	private final boolean staticExecutable;
	private final Class<?> clazz;
	private final GenericExecutable<?, ?> executable;
	private final int cyclomaticComplexity;
	private int failurePenalty;

	protected TestFitnessFunction(final String className,
								  final String methodNameDesc) {
		this.className = Objects.requireNonNull(className, "class name cannot be null");
		this.methodName = Objects.requireNonNull(methodNameDesc, "method name + descriptor cannot be null");
		this.clazz = Objects.requireNonNull(getClazz(className));
		this.executable = Objects.requireNonNull(getExecutable(methodNameDesc, clazz));
		this.publicExecutable = executable.isPublic();
		this.staticExecutable = executable.isStatic();
		this.constructor = executable.isConstructor();
		this.cyclomaticComplexity = computeCyclomaticComplexity(className, methodName);
		this.failurePenalty = -cyclomaticComplexity;
	}

	// TODO: should we put this into ReflectionFactory?
	/**
	 * Returns the {@code Class} instance for the class with the specified fully qualified name.
	 * If no matching {@code Class} definition can be found for the given name {@code null} is
	 * returned.
	 *
	 * @param className the name of the class to reflect
	 * @return the corresponding {@code Class} instance for the given name or {@code null} if no
	 * definition is found
	 */
	public static Class<?> getClazz(final String className) {
		if (classCache.containsKey(className)) {
			return classCache.get(className);
		} else {
			final ClassLoader classLoader =
					TestGenerationContext.getInstance().getClassLoaderForSUT();
			final Class<?> clazz;
			try {
				clazz = Class.forName(className, false, classLoader);
			} catch (ClassNotFoundException e) {
				logger.error("Unable to reflect unknown class {}", className);
				return null;
			}
			classCache.put(className, clazz);
			return clazz;
		}
	}

	// TODO: should we put this into ReflectionFactory?
	/**
	 * Tries to reflect the method or constructor specified by the given owner class and method
	 * name + descriptor, and creates a corresponding {@code GenericMethod} or
	 * {@code GenericConstructor} object as appropriate. Callers may safely downcast the returned
	 * {@code GenericExecutableMember} to a {@code GenericMethod} or {@code GenericConstructor} by
	 * checking the concrete subtype via the methods {@link GenericExecutable#isMethod() isMethod()}
	 * and {@link GenericExecutable#isConstructor() isConstructor()}. The method returns
	 * {@code null} if no matching executable could be found. Throws an {@code
     * IllegalArgumentException} if the method name + descriptor is malformed.
     *
     * @param methodNameDesc method name and descriptor of the executable to reflect. Must not be
     *                       {@code null}
     * @param clazz          the {@code Class} instance representing the owner class of the
     *                       executable. Must not be {@code null}.
     * @return the {@code GenericExecutableMember} object that represents the reflected method
     * or constructor, or {@code null} if no such method or constructor can be found
	 */
	public static GenericExecutable<?, ?> getExecutable(final String methodNameDesc,
														final Class<?> clazz) {
		Objects.requireNonNull(methodNameDesc);
		Objects.requireNonNull(clazz);

		if (executableCache.containsKey(methodNameDesc)) {
			return executableCache.get(methodNameDesc);
		} else {
			// methodNameDesc = name + descriptor
			// We have to split it into two parts to work with it. The opening parenthesis
			// indicates the start of the method descriptor. Every legal method name in
			// Java must be at least one character long. Every legal descriptor starts
			// with the opening parenthesis.
			final int descriptorStartIndex = methodNameDesc.indexOf('(');
			if (descriptorStartIndex < 1) {
				throw new IllegalArgumentException("malformed method name or descriptor");
			}

			final String name = methodNameDesc.substring(0, descriptorStartIndex);
			final String descriptor = methodNameDesc.substring(descriptorStartIndex);

			final ClassLoader classLoader =
					TestGenerationContext.getInstance().getClassLoaderForSUT();

			// Tries to reflect the argument types.
			final Class<?>[] argumentTypes;
			try {
				argumentTypes = ClassLoaderUtils.getArgumentClasses(classLoader, descriptor);
			} catch (Throwable t) {
				logger.error("Unable to reflect argument types of method {}", methodNameDesc);
				logger.error("\tCause: {}", t.getMessage());
				return null;
			}

			// Tries to reflect the executable (must be a method or constructor).
			final boolean isConstructor = name.equals("<init>");
			final GenericExecutable<?, ?> executable;
			try {
				executable = isConstructor ?
						new GenericConstructor(clazz.getConstructor(argumentTypes), clazz)
						: new GenericMethod(clazz.getDeclaredMethod(name, argumentTypes), clazz);
			} catch (NoSuchMethodException e) {
				logger.error("No executable with name {} and arguments {} in {}", name,
						argumentTypes, clazz);
				return null;
			}
			executableCache.put(methodNameDesc, executable);
			return executable;
		}
	}

	/**
	 * <p>
	 * getFitness
	 * </p>
	 *
	 * @param individual a {@link org.evosuite.testcase.TestChromosome} object.
	 * @param result     a {@link org.evosuite.testcase.execution.ExecutionResult} object.
	 * @return a double.
	 */
	public abstract double getFitness(TestChromosome individual, ExecutionResult result);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double getFitness(TestChromosome individual) {
		logger.trace("Executing test case on original");
		ExecutionResult lastResult = individual.getLastExecutionResult();
		if (lastResult == null || individual.isChanged()) {
			lastResult = runTest(individual.test);
			individual.setLastExecutionResult(lastResult);
			individual.setChanged(false);
		}

		double fitness = getFitness(individual, lastResult);
		updateIndividual(individual, fitness);

		return fitness;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Used to preorder goals by difficulty
	 */
	@Override
	public abstract int compareTo(TestFitnessFunction other);

	protected final int compareClassName(TestFitnessFunction other) {
		return this.getClass().getName().compareTo(other.getClass().getName());
	}

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object other);

	/**
	 * {@inheritDoc}
	 */
	public ExecutionResult runTest(TestCase test) {
		return TestCaseExecutor.runTest(test);
	}

	/**
	 * Determine if there is an existing test case covering this goal
	 *
	 * @param tests a {@link java.util.List} object.
	 * @return a boolean.
	 */
	public boolean isCovered(List<TestCase> tests) {
		return tests.stream().anyMatch(this::isCovered);
	}

	/**
	 * Determine if there is an existing test case covering this goal
	 *
	 * @param tests a {@link java.util.List} object.
	 * @return a boolean.
	 */
	public boolean isCoveredByResults(List<ExecutionResult> tests) {
		return tests.stream().anyMatch(this::isCovered);
	}

	public boolean isCoveredBy(TestSuiteChromosome testSuite) {
		int num = 1;
		for (TestChromosome test : testSuite.getTestChromosomes()) {
			logger.debug("Checking goal against test " + num + "/" + testSuite.size());
			num++;
			if (isCovered(test))
				return true;
		}
		return false;
		// return testSuite.getTestChromosomes().stream().anyMatch(this::isCovered);
	}

	/**
	 * <p>
	 * isCovered
	 * </p>
	 *
	 * @param test a {@link org.evosuite.testcase.TestCase} object.
	 * @return a boolean.
	 */
	public boolean isCovered(TestCase test) {
		TestChromosome c = new TestChromosome();
		c.test = test;
		return isCovered(c);
	}

	/**
	 * <p>
	 * isCovered
	 * </p>
	 *
	 * @param tc a {@link org.evosuite.testcase.TestChromosome} object.
	 * @return a boolean.
	 */
	public boolean isCovered(TestChromosome tc) {
		if (tc.getTestCase().isGoalCovered(this)) {
			return true;
		}

		ExecutionResult result = tc.getLastExecutionResult();
		if (result == null || tc.isChanged()) {
			result = runTest(tc.test);
			tc.setLastExecutionResult(result);
			tc.setChanged(false);
		}

		return isCovered(tc, result);
	}

	/**
	 * <p>
	 * isCovered
	 * </p>
	 *
	 * @param individual a {@link org.evosuite.testcase.TestChromosome} object.
	 * @param result     a {@link org.evosuite.testcase.execution.ExecutionResult} object.
	 * @return a boolean.
	 */
	public boolean isCovered(TestChromosome individual, ExecutionResult result) {
		boolean covered = getFitness(individual, result) == 0.0;
		if (covered) {
			individual.test.addCoveredGoal(this);
		}
		return covered;
	}

	/**
	 * Helper function if this is used without a chromosome
	 *
	 * @param result
	 * @return
	 */
	public boolean isCovered(ExecutionResult result) {
		TestChromosome chromosome = new TestChromosome();
		chromosome.setTestCase(result.test);
		chromosome.setLastExecutionResult(result);
		chromosome.setChanged(false);
		return isCovered(chromosome, result);
	}

	/* (non-Javadoc)
	 * @see org.evosuite.ga.FitnessFunction#isMaximizationFunction()
	 */

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isMaximizationFunction() {
		return false;
	}

	/**
	 * Returns the fully qualified name of the target class. For example, a class named {@code Bar}
	 * in a package {@code com.example.foo} has the fully qualified name
	 * {@code com.example.foo.Bar}. For more thorough information about this topic please refer to
	 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.7">The Java® Language Specification</a>.
	 *
	 * @return the fully qualified name of the target class
	 */
	public final String getTargetClassName() {
		return className;
	}

	/**
	 * <p>
	 * Returns the method name and method descriptor of the target method concatenated as string.
	 * For instance, consider the method
	 * <blockquote><pre>
	 * Object someMethod(int i, double d, Thread t) {...}
	 * </pre></blockquote>
	 * The method name is <code>someMethod</code> and the method descriptor is
	 * <blockquote><pre>
	 * (IDLjava/lang/Thread;)Ljava/lang/Object;
	 * </pre></blockquote>
	 * The concatenation of method name and descriptor therefore is
	 * <blockquote><pre>
	 * someMethod(IDLjava/lang/Thread;)Ljava/lang/Object;
	 * </pre></blockquote>
	 * Any constructor of a given class has the special name <code>&lt;init&gt;</code>.
	 * </p>
	 * <p>
	 * For more thorough information about method descriptors refer to the
	 * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">The Java® Virtual Machine Specification</a>.
	 * </p>
	 *
	 * @return the method name and descriptor of the target method
	 */
	public final String getTargetMethodName() {
		return methodName;
	}

    /**
     * Computes the cyclomatic complexity of the executable specified by the given fully
     * qualified name of the owner class and the executable's name + descriptor.
     *
     * @param className name of the owner class
     * @param methodName name + descriptor of the executable
     * @return the cyclomatic complexity
     */
	private static int computeCyclomaticComplexity(final String className, final String methodName) {
		final InstrumentingClassLoader cl =
                TestGenerationContext.getInstance().getClassLoaderForSUT();
		final GraphPool gp = GraphPool.getInstance(cl);
		final RawControlFlowGraph cfg = gp.getRawCFG(className, methodName);

        // The graph has already been constructed as part of the static analysis of the CUT.
        // Computing the cyclomatic complexity is just a matter of counting the number of nodes and
        // edges in the graph, which is a very cheap operation. For this reason, the impact on the
        // search budget is negligible.
		final int cyclomaticComplexity = cfg.getCyclomaticComplexity();

		assert cyclomaticComplexity > 0 : "cyclomatic complexity must be positive number";

		return cyclomaticComplexity;
	}

	private static int computeCyclomaticComplexityInclCallees(final String className,
															  final String methodName) {
		final InstrumentingClassLoader cl =
                TestGenerationContext.getInstance().getClassLoaderForSUT();
		final GraphPool gp = GraphPool.getInstance(cl);

		final RawControlFlowGraph cfg = gp.getRawCFG(className, methodName);
		final int ownComplexity = cfg.getCyclomaticComplexity();

		// Constructs the class call graph for the target class.
		final ClassCallGraph ccg = gp.getCCFG(className).getCcg();

		// Node in the class call graph representing the method containing the current target.
		final ClassCallNode method = ccg.getNodeByMethodName(methodName);

		// Entry nodes of the methods called by the current target method.
		// Only considers methods that are declared in the same class as the target method.
		ccg.outgoingEdgesOf(method);
		final Set<ClassCallNode> callees = ccg.getChildren(method);
//			final Set<ClassCallNode> callees = ccg.getChildrenRecursively(method);
		callees.remove(method); // don't consider recursive invocations of the target method

		// Computes the sum of the cyclomatic complexities of the callee methods, as well as
		// the total number of callee methods. A method, even if being called multiple times,
		// is accounted for only once.
		final IntSummaryStatistics calleeComplexities = callees.stream()
				.map(callee -> gp.getRawCFG(methodName, callee.getMethod()))
				.collect(Collectors.summarizingInt(RawControlFlowGraph::getCyclomaticComplexity));
		final int totalCalleeComplexity = (int) calleeComplexities.getSum();
		final int numberOfCallees = (int) calleeComplexities.getCount(); // Individual callees!

		// Using the formula explained in the JavaDoc.
		final int cyclomaticComplexity = ownComplexity + totalCalleeComplexity - numberOfCallees;

		// sanity check that field was properly initialized and no impossible value was computed
		assert cyclomaticComplexity > 0 : "cyclomatic complexity must be positive number";

		return cyclomaticComplexity;
	}

//	/**
//	 * Returns the cyclomatic complexity of the target method (as given by
//	 * {@link TestFitnessFunction#getTargetMethodName()}).
//	 *
//	 * @return the cyclomatic complexity of the target method
//	 * @see RawControlFlowGraph#getCyclomaticComplexity()
//	 */
//	public int getCyclomaticComplexity() {
//		// This method is thread-safe: the cyclomaticComplexity field is effectively final as long
//		// as no setter exists. Then, race conditions cannot occur. The worst thing that can happen
//		// is that two threads initialize cyclomaticComplexity to the same value at the same time.
//
//		if (cyclomaticComplexity < 1) { // Lazy initialization of the cyclomaticComplexity field
//			cyclomaticComplexity = computeCyclomaticComplexity(className, methodName);
//		}
//
//		return cyclomaticComplexity;
//	}

//	/**
//	 * Returns the cyclomatic complexity of the target method, including the cyclomatic complexities
//	 * of all methods <i>directly</i> called by the target method.
//	 * <p>
//	 * The rationale is to handle pathetic cases where very complicated methods are called by very
//	 * simple ones, such as this one:
//	 * <pre><code>
//	 * void foo() {
//	 *     veryComplicatedMethod(); // cyclomatic complexity = 42
//	 * }
//	 * </code></pre>
//	 * Using the traditional definition of the cyclomatic complexity as implemented in
//	 * {@link TestFitnessFunction#getCyclomaticComplexity()}, <code>foo()</code> would have a
//	 * cyclomatic complexity of just 1, despite the fact that it's calling a method
//	 * with a much higher complexity. In the case of test generation, this would make covering
//	 * <code>foo()</code> much more appealing, when in fact it's just as appealing as covering the
//	 * <code>veryComplicatedMethod()</code>. For this reason, this method treats <code>foo</code>
//	 * and <code>veryComplicatedMethod()</code> the same way by assigning them the same cyclomatic
//	 * complexity.
//	 * <p>
//	 * Conceptually, if we want to compute the cyclomatic complexity of a method while also
//	 * considering the cyclomatic complexities of its callee methods, we have to embed the entire
//	 * CFG of every callee method into the CFG of the target method. This is done by replacing the
//	 * vertex that calls another method with the corresponding CFG of that method. The incoming
//	 * edge of the vertex we just replaced is now connected to the method entry node of the called
//	 * method. In analogue, the outgoing edge of the vertex we replaced is now connected to the
//	 * method exit point of the called method.
//	 * <p>
//	 * This notion only works if the callee method is called only once. Otherwise, the exit node
//	 * of the embedded CFG would have an out-degree of more than 1, despite not being a decision
//	 * node. It also means that the results computed by this method will be slightly flawed in
//	 * case the callee gets called more than once. However, this method is not meant to produce
//	 * exact results, it's rather only intended to serve the purpose of returning a rough estimate
//	 * of the complexity of a method.
//	 * <p>
//	 * The computation uses raw CFGs, i.e., it does not summarize sequentially composed statements
//	 * to basic blocks. Therefore, we can compute the "recursive" cyclomatic complexity by
//	 * computing the cyclomatic complexities of all involved methods individually, then summing
//	 * it all up, and finally subtracting the number of individual callees to account for the
//	 * fact that we replaced some nodes with entire CFGs as explained earlier. Note that the
//	 * cyclomatic complexities of the callee methods are not computed recursively using this same
//	 * method. That is, callees of callees are not accounted for. Instead, for the sake of
//	 * efficiency and simplicity, the cyclomatic complexity of calles is computed using the
//	 * "traditional way" as implemented in {@code getCyclomaticComplexity()} and
//	 * {@link RawControlFlowGraph#getCyclomaticComplexity()}.
//	 *
//	 * @return the cyclomatic complexity
//	 */
//	public int getCyclomaticComplexityInclCallees() {
//		// This method is thread-safe: the cyclomaticComplexity field is effectively final as long
//		// as no setter exists. Then, race conditions cannot occur. The worst thing that can happen
//		// is that two threads initialize cyclomaticComplexity to the same value at the same time.
//
//		if (cyclomaticComplexity < 1) { // Lazy initialization of the cyclomaticComplexity field
//			computeCyclomaticComplexityInclCallees(className, methodName);
//		}
//
//		return cyclomaticComplexity;
//	}

	/**
	 * Tells whether the executable (i.e., method or constructor) containing the coverage target is
	 * public.
	 *
	 * @return {@code true} if the executable is public, {@code false} otherwise
	 */
	public boolean isPublic() {
		return publicExecutable;
	}

	/**
	 * Tells whether the method containing the coverage target is static.
	 *
	 * @return {@code true} if the method is static, {@code false} otherwise
	 */
	public boolean isStatic() {
		return staticExecutable;
	}

	/**
	 * Tells whether the executable containing the coverage target is a constructor.
	 *
	 * @return {@code true} if the target is inside a constructor, {@code false} otherwise
	 */
	public boolean isConstructor() {
		return constructor;
	}

	/**
	 * Returns the {@code Class} object that represents the class the target is located in.
	 *
	 * @return the reflected class that contains the target
	 */
	public Class<?> getClazz() {
		return clazz;
	}

	/**
	 * Returns the {@code GenericExecutable} that represents the executable (i.e., method or
	 * constructor) the target is located in.
	 *
	 * @return the reflected executable containing the target
	 */
	public GenericExecutable<?, ?> getExecutable() {
		return executable;
	}

	/**
	 * Increases the failure penalty of the coverage target. This operation is a NO-OP if failure
	 * penalties are disabled.
	 *
	 * @see Properties#ENABLE_FAILURE_PENALTIES
	 */
	public void increaseFailurePenalty() {
		if (Properties.ENABLE_FAILURE_PENALTIES) {
			failurePenalty++;
		}
	}

	/**
	 * Resets the failure penalty for the coverage target. This operation is a NO-OP if failure
	 * penalties are disabled.
	 *
	 * @see Properties#ENABLE_FAILURE_PENALTIES
	 */
	public void resetFailurePenalty() {
		if (Properties.ENABLE_FAILURE_PENALTIES) {
			failurePenalty = -cyclomaticComplexity;
		}
	}

	/**
	 * Returns the current failure penalty for the coverage target.
	 *
	 * @return the current failure penalty
	 */
	public int getFailurePenalty() {
		return failurePenalty;
	}

	/**
	 * Tells whether the failure penalty has been reached for the current target.
	 *
	 * @return {@code true} if the failure penalty has been reached, {@code false} otherwise
	 * @see Properties#FAILURE_PENALTY
	 */
	public boolean isFailurePenaltyReached() {
		return failurePenalty > Properties.FAILURE_PENALTY;
	}

	/**
	 * Returns the cyclomatic complexity of the executable containing the coverage target.
	 *
	 * @return the cyclomatic complexity
	 */
	public int getCyclomaticComplexity() {
		return cyclomaticComplexity;
	}
}
