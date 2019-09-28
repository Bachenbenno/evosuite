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

import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.testcase.mutation.change.ModificationStrategy;
import org.evosuite.testcase.mutation.change.RandomModification;
import org.evosuite.testcase.mutation.deletion.DefaultDeletion;
import org.evosuite.testcase.mutation.deletion.DeletionStrategy;
import org.evosuite.testcase.mutation.insertion.AbstractInsertion;
import org.evosuite.testcase.mutation.insertion.InsertionStrategyFactory;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.utils.generic.GenericConstructor;
import org.evosuite.utils.generic.GenericField;
import org.evosuite.utils.generic.GenericMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author Gordon Fraser
 */
public class TestFactory {

	private static final Logger logger = LoggerFactory.getLogger(TestFactory.class);

	/**
	 * Singleton instance
	 */
	private static TestFactory instance = null;

    private AbstractInsertion insertionStrategy = InsertionStrategyFactory.getStrategy();
    private ModificationStrategy modificationStrategy = RandomModification.getInstance();
    private DeletionStrategy deletionStrategy = DefaultDeletion.getInstance();

	public static TestFactory getInstance() {
		if (instance == null)
			instance = new TestFactory();
		return instance;
	}

	public boolean changeRandomCall(TestCase test, Statement statement) {
		return modificationStrategy.changeRandomCall(test, statement);
	}

	/**
	 * Delete the statement at position from the test case and remove all
	 * references to it
	 *
	 * @param test
	 * @param position
	 * @return false if it was not possible to delete the statement
	 * @throws ConstructionFailedException
	 */
	public boolean deleteStatement(TestCase test, int position)
	        throws ConstructionFailedException {
		return deletionStrategy.deleteStatement(test, position);
	}

	/**
	 *
	 * @param test
	 * @param position
	 * @return true if statements was deleted or any dependency was modified
	 * @throws ConstructionFailedException
	 */
	public boolean deleteStatementGracefully(TestCase test, int position)
	        throws ConstructionFailedException {
		return deletionStrategy.deleteStatementGracefully(test, position);
	}

	/**
	 * Inserts one or perhaps multiple random statements into the given {@code test}. Callers
	 * have to specify the position of the last valid statement of {@code test} by supplying an
	 * appropriate index {@code lastPosition}. After a successful insertion, returns the updated
	 * position of the last valid statement (which is always non-negative), or if there was an error
	 * the constant {@link AbstractInsertion#INSERTION_ERROR
	 * INSERTION_ERROR}.
	 *
	 * @param test the test case in which to insert
	 * @param lastPosition the position of the last valid statement of {@code test} before insertion
	 * @return the position of the last valid statement after insertion, or {@code INSERTION_ERROR}
	 * (see above)
	 */
	public int insertRandomStatement(TestCase test, int lastPosition) {
		return insertionStrategy.insertStatement(test, lastPosition);
	}


	// -------------------------------------------------------------------
	// FIXME DELEGATES for backwards compatibility
	// The system tests have not been restructured yet.
	// In order to prevent them from failing, the methods below this
	// comment were added to the interface of TestFactory.
	// In an effort to refactor the TestFactory, they were moved to their
	// own class AbstractInsertion. However, the system tests still expect
	// them to be in the TestFactory class. In the future, the system
	// tests should be restructured as well so that we can remove the
	// delegates.
	// -------------------------------------------------------------------


	public final void appendStatement(TestCase test, Statement statement) throws ConstructionFailedException {
		insertionStrategy.appendStatement(test, statement);
	}

	public final List<VariableReference> satisfyParameters(TestCase test, VariableReference callee,
														   List<Type> parameterTypes,
														   List<Parameter> parameterList, int position, int recursionDepth, boolean allowNull,
														   boolean excludeCalleeGenerators, boolean canReuseExistingVariables) throws ConstructionFailedException {
		return insertionStrategy.satisfyParameters(test, callee, parameterTypes, parameterList,
				position, recursionDepth, allowNull, excludeCalleeGenerators, canReuseExistingVariables);
	}

	public final void attemptGeneration(TestCase newTest, Type returnType, int statement) throws ConstructionFailedException {
		insertionStrategy.attemptGeneration(newTest, returnType, statement);
	}

	public final void resetContext() {
		insertionStrategy.reset();
	}

	public final void insertRandomCallOnObjectAt(TestCase testCase, VariableReference var, int i) {
		insertionStrategy.insertRandomCallOnObjectAt(testCase, var, i);
	}

	public final VariableReference createObject(TestCase testCase, Type type, int statement, int i,
										  VariableReference o) throws ConstructionFailedException {
		return insertionStrategy.createObject(testCase, type, statement, i, o);
	}

	public final VariableReference addMethod(TestCase test, GenericMethod call, int size, int i) throws ConstructionFailedException {
		return insertionStrategy.addMethod(test, call, size, i);
	}

	public final VariableReference addConstructor(TestCase test, GenericConstructor call, int size, int i) throws ConstructionFailedException {
		return insertionStrategy.addConstructor(test, call, size, i);
	}

	public void addMethodFor(TestCase tc, VariableReference genericClass, GenericMethod gm, int i) throws ConstructionFailedException {
		insertionStrategy.addMethodFor(tc, genericClass, gm, i);
	}

	public VariableReference addField(TestCase test, GenericField field, int position,
									   int recursionDepth) throws ConstructionFailedException {
		return insertionStrategy.addField(test, field, position, recursionDepth);
	}

	public VariableReference addFieldFor(TestCase test, VariableReference callee,
										 GenericField field, int position) throws ConstructionFailedException {
		return insertionStrategy.addFieldFor(test, callee, field, position);
	}

	public VariableReference addFieldAssignment(TestCase test, GenericField field,
												 int position, int recursionDepth) throws ConstructionFailedException {
		return insertionStrategy.addFieldAssignment(test, field, position, recursionDepth);
	}
}
