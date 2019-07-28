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
package org.evosuite.testcase.mutation;

import org.evosuite.testcase.TestCase;

/**
 * This interface must be implemented by all classes that modify test cases via insertion of
 * statements. Intended uses are for the implementation of mutation operators or for the generation
 * of initial populations.
 */
public interface InsertionStrategy {

	/**
	 * In the given test case, insert a new statement at the end of the sequence as indicated by
	 * {@code lastPosition}.
	 *
	 * @param test the test case in which to insert
	 * @param lastPosition the position of the last valid statement in the test case, defining the
	 *                     insertion point for new statements
	 * @return the updated position of the last valid statement after insertion, or a negative
	 * 		   number if insertion failed
	 */
	public int insertStatement(TestCase test, int lastPosition);
}
