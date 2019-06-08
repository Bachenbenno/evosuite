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

public interface InsertionStrategy {

	/**
	 * In the given test case, insert a new statement at the end of the sequence as indicated by
	 * {@code lastPosition}.
	 *
	 * @param test the test case in which to insert
	 * @param lastPosition the position in the sequence after which to insert
	 * @return The position of the newly inserted statement. This might not necessarily be {@code
	 * lastPosition + 1}, e.g. when multiple statements had to be inserted before as dependencies.
	 */
	public int insertStatement(TestCase test, int lastPosition);
}
