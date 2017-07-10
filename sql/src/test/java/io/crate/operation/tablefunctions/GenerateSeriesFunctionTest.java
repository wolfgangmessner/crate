/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.operation.tablefunctions;

import org.junit.Test;

public class GenerateSeriesFunctionTest extends AbstractTableFunctionsTest {

    private void generateSeries(long start, long stop, long step) {
        StringBuilder sb = new StringBuilder();
        long amountRepetitions = (stop-start)/step;
        for(long i = 0; i <= amountRepetitions; i++) {
            long j = start + i * step;
            sb.append(j+"\n");
        }
        assertExecute("generate_series("+start+","+stop+","+step+")", sb.toString());
    }

    @Test
    public void testNormalCaseWithDefaultStep() throws Exception {
        generateSeries(0, 1000, 1);
    }

    @Test
    public void testNormalCaseWithCustomStep() throws Exception {
        generateSeries(0, 1000, 5);
    }

    @Test
    public void testBigNormalCase() throws Exception {
        generateSeries(0,1000000, 1);
    }

    @Test
    public void testNegativeCase() throws Exception {
        generateSeries(1000, 0, -1);
    }

    @Test
    public void testNegativeCaseWithBiggerStep() throws Exception {
        generateSeries(1000, 0, -10);
    }

    @Test
    public void testBigNegativeCase() throws Exception {
        generateSeries(1000000, 0, -1);
    }

    @Test
    public void testInvalidCombination() throws Exception {
        try {
            generateSeries(0, -1, 1);
            fail();
        } catch (AssertionError e) {
            assertEquals("invalid combination: cannot reach -1 from 0 by stepping 1", e.getMessage());
        }
    }

    @Test
    public void testBiggerStepThanStop() throws Exception {
        generateSeries(0, 10, 100);
    }

    @Test
    public void testFailures() throws Exception {
        generateSeries(1000000, 0, -1);
    }
}
