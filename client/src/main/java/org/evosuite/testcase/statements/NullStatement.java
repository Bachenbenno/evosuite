/**
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
/**
 *
 */
package org.evosuite.testcase.statements;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.evosuite.testcase.variable.NullReference;
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.variable.VariableReference;
import org.evosuite.testcase.execution.Scope;
import org.evosuite.utils.generic.GenericAccessibleObject;

/**
 * The concept of variableReferences is: that they are created by some
 * statement. This Statement serves the purpose of defining null references. We
 * need it, as various places in the code assume, that VariableReferences are
 * generated by statements.
 *
 * @author steenbuck
 */
public class NullStatement extends PrimitiveStatement<Void> {

	private static final long serialVersionUID = -7141670041216163032L;

	/**
	 * <p>
	 * Constructor for NullStatement.
	 * </p>
	 *
	 * @param tc
	 *            a {@link org.evosuite.testcase.TestCase} object.
	 * @param type
	 *            a {@link java.lang.reflect.Type} object.
	 */
	public NullStatement(TestCase tc, java.lang.reflect.Type type) {
		super(tc, new NullReference(tc, type), null);
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#clone(org.evosuite.testcase.TestCase)
	 */
	/** {@inheritDoc} */
	@Override
	public Statement copy(TestCase newTestCase, int offset) {
		return new NullStatement(newTestCase, retval.getType());
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#execute(org.evosuite.testcase.Scope, java.io.PrintStream)
	 */
	/** {@inheritDoc} */
	@Override
	public Throwable execute(Scope scope, PrintStream out)
	        throws InvocationTargetException, IllegalArgumentException,
	        IllegalAccessException, InstantiationException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#getUniqueVariableReferences()
	 */
	/** {@inheritDoc} */
	@Override
	public List<VariableReference> getUniqueVariableReferences() {
		return new ArrayList<VariableReference>(getVariableReferences());
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#getVariableReferences()
	 */
	/** {@inheritDoc} */
	@Override
	public Set<VariableReference> getVariableReferences() {
		Set<VariableReference> references = new LinkedHashSet<VariableReference>();
		references.add(retval);
		return references;
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#replace(org.evosuite.testcase.VariableReference, org.evosuite.testcase.VariableReference)
	 */
	/** {@inheritDoc} */
	@Override
	public void replace(VariableReference oldVar, VariableReference newVar) {
	}

	/* (non-Javadoc)
	 * @see org.evosuite.testcase.StatementInterface#same(org.evosuite.testcase.StatementInterface)
	 */
	/** {@inheritDoc} */
	@Override
	public boolean same(Statement s) {
		if (this == s)
			return true;
		if (s == null)
			return false;
		if (getClass() != s.getClass())
			return false;

		NullStatement ns = (NullStatement) s;
		return retval.same(ns.retval);
	}

	/** {@inheritDoc} */
	@Override
	public GenericAccessibleObject<?> getAccessibleObject() {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public void delta() {
		logger.info("Method delta not implemented: What is the delta for null?");
	}

	/** {@inheritDoc} */
	@Override
	public void zero() {
		logger.info("Method zero not implemented: How to zero null?");
	}

	/** {@inheritDoc} */
	@Override
	public void randomize() {
		logger.info("Method randomize not implemented: How to randomize null?");
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException,
	        IOException {
		ois.defaultReadObject();
		value = null;
	}
}
