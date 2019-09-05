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
import org.evosuite.runtime.util.Inputs;
import org.evosuite.setup.TestCluster;
import org.evosuite.testcase.mutation.AbstractInsertionStrategy;
import org.evosuite.testcase.mutation.InsertionStrategyFactory;
import org.evosuite.testcase.statements.*;
import org.evosuite.testcase.variable.*;
import org.evosuite.utils.Randomness;
import org.evosuite.utils.generic.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Gordon Fraser
 */
public class TestFactory {

	private static final Logger logger = LoggerFactory.getLogger(TestFactory.class);

	/**
	 * Singleton instance
	 */
	private static TestFactory instance = null;

    private AbstractInsertionStrategy insertionStrategy = InsertionStrategyFactory.getStrategy();

	public static TestFactory getInstance() {
		if (instance == null)
			instance = new TestFactory();
		return instance;
	}

	/**
	 * Replace the statement with a new statement using given call
	 *
	 * @param test
	 * @param statement
	 * @param call
	 * @throws ConstructionFailedException
	 */
	public void changeCall(TestCase test, Statement statement,
	        GenericAccessibleObject<?> call) throws ConstructionFailedException {
		int position = statement.getReturnValue().getStPosition();

		logger.debug("Changing call {} with {}",test.getStatement(position), call);

		if (call.isMethod()) {
			GenericMethod method = (GenericMethod) call;
			if (method.hasTypeParameters())
				throw new ConstructionFailedException("Cannot handle generic methods properly");

			VariableReference retval = statement.getReturnValue();
			VariableReference callee = null;
			if (!method.isStatic()) {
				callee = getRandomNonNullNonPrimitiveObject(test, method.getOwnerType(), position);
			}

			List<VariableReference> parameters = new ArrayList<>();
			for (Type type : method.getParameterTypes()) {
				parameters.add(test.getRandomObject(type, position));
			}
			MethodStatement m = new MethodStatement(test, method, callee, parameters, retval);
			test.setStatement(m, position);
			logger.debug("Using method {}", m.getCode());

		} else if (call.isConstructor()) {

			GenericConstructor constructor = (GenericConstructor) call;
			VariableReference retval = statement.getReturnValue();
			List<VariableReference> parameters = new ArrayList<>();
			for (Type type : constructor.getParameterTypes()) {
				parameters.add(test.getRandomObject(type, position));
			}
			ConstructorStatement c = new ConstructorStatement(test, constructor, retval, parameters);

			test.setStatement(c, position);
			logger.debug("Using constructor {}", c.getCode());

		} else if (call.isField()) {
			GenericField field = (GenericField) call;
			VariableReference retval = statement.getReturnValue();
			VariableReference source = null;
			if (!field.isStatic())
				source = getRandomNonNullNonPrimitiveObject(test,field.getOwnerType(), position);

			try {
				FieldStatement f = new FieldStatement(test, field, source, retval);
				test.setStatement(f, position);
				logger.debug("Using field {}", f.getCode());
			} catch (Throwable e) {
				logger.error("Error: " + e + " , Field: " + field + " , Test: " + test);
				throw new Error(e);
			}
		}
	}

	private VariableReference getRandomNonNullNonPrimitiveObject(TestCase tc, Type type, int position)
			throws ConstructionFailedException {
		Inputs.checkNull(type);

		List<VariableReference> variables = tc.getObjects(type, position);
		variables.removeIf(var -> var instanceof NullReference
				|| tc.getStatement(var.getStPosition()) instanceof PrimitiveStatement
				|| var.isPrimitive()
				|| var.isWrapperType()
				|| tc.getStatement(var.getStPosition()) instanceof FunctionalMockStatement
				|| ConstraintHelper.getLastPositionOfBounded(var, tc) >= position);

		if (variables.isEmpty()) {
			throw new ConstructionFailedException("Found no variables of type " + type
					+ " at position " + position);
		}

		return Randomness.choice(variables);
	}

	public boolean changeRandomCall(TestCase test, Statement statement) {
		logger.debug("Changing statement {}", statement.getCode());

		List<VariableReference> objects = test.getObjects(statement.getReturnValue().getStPosition());
		objects.remove(statement.getReturnValue());

		Iterator<VariableReference> iter = objects.iterator();
		while(iter.hasNext()){
			VariableReference ref = iter.next();
			//do not use FM as possible callees
			if(test.getStatement(ref.getStPosition()) instanceof FunctionalMockStatement){
				iter.remove();
				continue;
			}

			int boundPosition = ConstraintHelper.getLastPositionOfBounded(ref, test);
			if(boundPosition >= 0 && boundPosition >= statement.getPosition()){
				// if bounded variable, cannot add methods before its initialization, and so cannot be
				// used as a callee
				iter.remove();
			}
		}

		// TODO: replacing void calls with other void calls might not be the best idea
		List<GenericAccessibleObject<?>> calls = getPossibleCalls(statement.getReturnType(), objects);

		GenericAccessibleObject<?> ao = statement.getAccessibleObject();
		if (ao != null && ao.getNumParameters() > 0) {
			calls.remove(ao);
		}

		if(ConstraintHelper.getLastPositionOfBounded(statement.getReturnValue(),test) >= 0){
			//if the return variable is bounded, we can only use a constructor on the right hand-side
			calls.removeIf(k -> !(k instanceof GenericConstructor));
		}

		logger.debug("Got {} possible calls for {} objects",calls.size(),objects.size());

		//calls.clear();
		if (calls.isEmpty()) {
			logger.debug("No replacement calls");
			return false;
		}

		GenericAccessibleObject<?> call = Randomness.choice(calls);
		try {
			changeCall(test, statement, call);
			return true;
		} catch (ConstructionFailedException e) {
			// Ignore
			logger.info("Change failed for statement " + statement.getCode() + " -> "
			        + call + ": " + e.getMessage() + " " + test.toCode());
		}
		return false;
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

		if(! ConstraintVerifier.canDelete(test, position)){
			return false;
		}

		logger.debug("Deleting target statement - {}", position);

		Set<Integer> toDelete = new LinkedHashSet<>();
		recursiveDeleteInclusion(test,toDelete,position);

		List<Integer> pos = new ArrayList<>(toDelete);
		pos.sort(Collections.reverseOrder());

		for (int i : pos) {
			logger.debug("Deleting statement: {}", i);
			test.remove(i);
		}

		return true;
	}

	private void recursiveDeleteInclusion(TestCase test, Set<Integer> toDelete, int position){

		if(toDelete.contains(position)){
			return; //end of recursion
		}

		toDelete.add(position);

		Set<Integer> references = getReferencePositions(test, position);

		/*
			it can happen that we can delete the target statements but, when we look at
			the other statements using it, then we could not delete them :(
			in those cases, we have to recursively look at all their dependencies.
		 */

		for (Integer i : references) {

			Set<Integer> constraintDependencies = ConstraintVerifier.dependentPositions(test, i);
			if(constraintDependencies!=null){
				for(Integer j : constraintDependencies){
					recursiveDeleteInclusion(test,toDelete,j);
				}
			}

			recursiveDeleteInclusion(test,toDelete,i);
		}
	}

	private Set<Integer> getReferencePositions(TestCase test, int position) {
		Set<VariableReference> references = new LinkedHashSet<>();
		Set<Integer> positions = new LinkedHashSet<>();
		references.add(test.getReturnValue(position));

		for (int i = position; i < test.size(); i++) {
			Set<VariableReference> temp = new LinkedHashSet<>();
			for (VariableReference v : references) {
				if (test.getStatement(i).references(v)) {
					temp.add(test.getStatement(i).getReturnValue());
					positions.add(i);
				}
			}
			references.addAll(temp);
		}
		return positions;
	}

	private static void filterVariablesByClass(Collection<VariableReference> variables, Class<?> clazz) {
		// Remove invalid classes if this is an Object.class reference
		variables.removeIf(r -> !r.getVariableClass().equals(clazz));
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
		VariableReference var = test.getReturnValue(position);

		if (var instanceof ArrayIndex) {
			return deleteStatement(test, position);
		}

		boolean changed = false;

		boolean replacingPrimitive = test.getStatement(position) instanceof PrimitiveStatement;

		// Get possible replacements
		List<VariableReference> alternatives = test.getObjects(var.getType(), position);

		int maxIndex = 0;
		if (var instanceof ArrayReference) {
			maxIndex = ((ArrayReference) var).getMaximumIndex();
		}

		// Remove invalid classes if this is an Object.class reference
		if (test.getStatement(position) instanceof MethodStatement) {
			MethodStatement ms = (MethodStatement) test.getStatement(position);
			if (ms.getReturnType().equals(Object.class)) {
				//				filterVariablesByClass(alternatives, var.getVariableClass());
				filterVariablesByClass(alternatives, Object.class);
			}
		} else if (test.getStatement(position) instanceof ConstructorStatement) {
			ConstructorStatement cs = (ConstructorStatement) test.getStatement(position);
			if (cs.getReturnType().equals(Object.class)) {
				filterVariablesByClass(alternatives, Object.class);
			}
		}

		// Remove self, and all field or array references to self
		alternatives.remove(var);
		Iterator<VariableReference> replacement = alternatives.iterator();
		while (replacement.hasNext()) {
			VariableReference r = replacement.next();
			if(test.getStatement(r.getStPosition()) instanceof FunctionalMockStatement){
				// we should ensure that a FM should never be a callee
				replacement.remove();
			} else if (var.equals(r.getAdditionalVariableReference())) {
				replacement.remove();
			} else if(var.isFieldReference()) {
				FieldReference fref = (FieldReference)var;
				if(fref.getField().isFinal()) {
					replacement.remove();
				}
			} else if (r instanceof ArrayReference) {
				if (maxIndex >= ((ArrayReference) r).getArrayLength())
					replacement.remove();
			} else if (!replacingPrimitive) {
				if (test.getStatement(r.getStPosition()) instanceof PrimitiveStatement) {
					replacement.remove();
				}
			}
		}

		if (!alternatives.isEmpty()) {
			// Change all references to return value at position to something else
			for (int i = position + 1; i < test.size(); i++) {
				Statement s = test.getStatement(i);
				if (s.references(var)) {
					if (s.isAssignmentStatement()) {
						AssignmentStatement assignment = (AssignmentStatement) s;
						if (assignment.getValue() == var) {
							VariableReference replacementVar = Randomness.choice(alternatives);
							if (assignment.getReturnValue().isAssignableFrom(replacementVar)) {
								s.replace(var, replacementVar);
								changed = true;
							}
						} else if (assignment.getReturnValue() == var) {
							VariableReference replacementVar = Randomness.choice(alternatives);
							if (replacementVar.isAssignableFrom(assignment.getValue())) {
								s.replace(var, replacementVar);
								changed = true;
							}
						}
					} else {
						/*
							if 'var' is a bounded variable used in 's', then it should not be
							replaced with another one. should be left as it is, as to make it
							deletable
						 */
						boolean bounded = false;
						if(s instanceof EntityWithParametersStatement){
							EntityWithParametersStatement es = (EntityWithParametersStatement) s;
							bounded = es.isBounded(var);
						}

						if(!bounded) {
							s.replace(var, Randomness.choice(alternatives));
							changed = true;
						}
					}
				}
			}
		}

		if (var instanceof ArrayReference) {
			alternatives = test.getObjects(var.getComponentType(), position);
			// Remove self, and all field or array references to self
			alternatives.remove(var);
			replacement = alternatives.iterator();
			while (replacement.hasNext()) {
				VariableReference r = replacement.next();
				if (var.equals(r.getAdditionalVariableReference()))
					replacement.remove();
				else if (r instanceof ArrayReference) {
					if (maxIndex >= ((ArrayReference) r).getArrayLength())
						replacement.remove();
				}
			}
			if (!alternatives.isEmpty()) {
				// Change all references to return value at position to something else
				for (int i = position; i < test.size(); i++) {
					Statement s = test.getStatement(i);
					for (VariableReference var2 : s.getVariableReferences()) {
						if (var2 instanceof ArrayIndex) {
							ArrayIndex ai = (ArrayIndex) var2;
							if (ai.getArray().equals(var)) {
								s.replace(var2, Randomness.choice(alternatives));
								changed = true;
							}
						}
					}
				}
			}
		}

		// Remove everything else
		boolean deleted = deleteStatement(test, position);
		return  deleted || changed;
	}

	/**
	 * Determine if the set of objects is sufficient to satisfy the set of
	 * dependencies
	 *
	 * @param dependencies
	 * @param objects
	 * @return
	 */
	private static boolean dependenciesSatisfied(Set<Type> dependencies,
	        List<VariableReference> objects) {
		for (Type type : dependencies) {
			boolean found = false;
			for (VariableReference var : objects) {
				if (var.getType().equals(type)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Retrieve the dependencies for a constructor
	 *
	 * @param constructor
	 * @return
	 */
	private static Set<Type> getDependencies(GenericConstructor constructor) {
		return new LinkedHashSet<>(Arrays.asList(constructor.getParameterTypes()));
	}

	/**
	 * Retrieve the dependencies for a field
	 *
	 * @param field
	 * @return
	 */
	private static Set<Type> getDependencies(GenericField field) {
		Set<Type> dependencies = new LinkedHashSet<>();
		if (!field.isStatic()) {
			dependencies.add(field.getOwnerType());
		}

		return dependencies;
	}

	/**
	 * Retrieve the dependencies for a method
	 *
	 * @param method
	 * @return
	 */
	private static Set<Type> getDependencies(GenericMethod method) {
		Set<Type> dependencies = new LinkedHashSet<>();
		if (!method.isStatic()) {
			dependencies.add(method.getOwnerType());
		}
		dependencies.addAll(Arrays.asList(method.getParameterTypes()));

		return dependencies;
	}

	/**
	 * Retrieve all the replacement calls that can be inserted at this position
	 * without changing the length
	 *
	 * @param returnType
	 * @param objects
	 * @return
	 */
	private List<GenericAccessibleObject<?>> getPossibleCalls(Type returnType,
															  List<VariableReference> objects) {
		List<GenericAccessibleObject<?>> calls = new ArrayList<>();
		Set<GenericAccessibleObject<?>> allCalls;

		try {
			allCalls = TestCluster.getInstance().getGenerators(new GenericClass(
			                                                           returnType));
		} catch (ConstructionFailedException e) {
			return calls;
		}

		for (GenericAccessibleObject<?> call : allCalls) {
			Set<Type> dependencies = null;
			if (call.isMethod()) {
				GenericMethod method = (GenericMethod) call;
				if (method.hasTypeParameters()) {
					try {
						call = method.getGenericInstantiation(new GenericClass(returnType));
					} catch (ConstructionFailedException e) {
						continue;
					}
				}
				if (!((GenericMethod) call).getReturnType().equals(returnType))
					continue;
				dependencies = getDependencies((GenericMethod) call);
			} else if (call.isConstructor()) {
				dependencies = getDependencies((GenericConstructor) call);
			} else if (call.isField()) {
				if (!((GenericField) call).getFieldType().equals(returnType))
					continue;
				dependencies = getDependencies((GenericField) call);
			} else {
				assert (false);
			}
			if (dependenciesSatisfied(dependencies, objects)) {
				calls.add(call);
			}
		}

		// TODO: What if primitive?

		return calls;
	}

	/**
	 * Inserts one or perhaps multiple random statements into the given {@code test}. Callers
	 * have to specify the position of the last valid statement of {@code test} by supplying an
	 * appropriate index {@code lastPosition}. After a successful insertion, returns the updated
	 * position of the last valid statement (which is always non-negative), or if there was an error
	 * the constant {@link AbstractInsertionStrategy#INSERTION_ERROR
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
	// DELEGATES
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

	public final void addMethod(TestCase test, GenericMethod call, int size, int i) throws ConstructionFailedException {
		insertionStrategy.addMethod(test, call, size, i);
	}

	public final void addConstructor(TestCase test, GenericConstructor call, int size, int i) throws ConstructionFailedException {
		insertionStrategy.addConstructor(test, call, size, i);
	}
}
