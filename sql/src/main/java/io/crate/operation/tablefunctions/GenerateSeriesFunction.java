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

import io.crate.analyze.WhereClause;
import io.crate.data.Bucket;
import io.crate.data.Input;
import io.crate.data.Row;
import io.crate.data.RowN;
import io.crate.metadata.*;
import io.crate.metadata.table.StaticTableInfo;
import io.crate.metadata.table.TableInfo;
import io.crate.metadata.tablefunctions.TableFunctionImplementation;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.elasticsearch.cluster.service.ClusterService;

import javax.annotation.Nullable;
import java.util.*;

public class GenerateSeriesFunction {

    private static final String NAME = "generate_series";
    private static final TableIdent TABLE_IDENT = new TableIdent(null, NAME);

    static class GenerateSeriesFunctionImplementation implements TableFunctionImplementation {

        private final FunctionInfo info;

        private GenerateSeriesFunctionImplementation(FunctionInfo info) {
            this.info = info;
        }

        @Override
        public FunctionInfo info() {
            return info;
        }

        private static Long[] extractValues(Collection<? extends Input> arguments) {
            assert arguments.size() >= 2 && arguments.size() <= 3 : NAME + " only accepts 2-3 arguments";

            Long[] values = new Long[arguments.size()];
            int idx = 0;

            for (Input argument : arguments) {
                Number value = (Number) argument.value();
                values[idx++] = value.longValue();
            }

            return values;
        }

        @Override
        public Bucket execute(Collection<? extends Input> arguments) {
            final Long[] values = extractValues(arguments);

            final long start = values[0];
            final long stop = values[1];
            final long step = values.length==3 ? values[2] : 1;

            assert step!=0 : "step must not be 0";

            final int numCols = 1;
            final int numRows = (int) ((stop - start) / step);

            assert numRows>=0 : "invalid combination: cannot reach "+stop+" from "+start+" by stepping "+step;

            return new Bucket() {
                final Object[] cells = new Object[numCols];
                final RowN row = new RowN(cells);

                @Override
                public int size() {
                    return numRows+1;
                }

                @Override
                public Iterator<Row> iterator() {
                    return new Iterator<Row>() {

                        int currentRow = 0;

                        @Override
                        public boolean hasNext() {
                            return currentRow < size();
                        }

                        @Override
                        public Row next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException("No more rows");
                            }
                            cells[0] = start + currentRow*step;
                            currentRow++;
                            return row;
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException("remove is not supported for " +
                                GenerateSeriesFunction.class.getSimpleName() + "$iterator");
                        }
                    };
                }
            };
        }

        @Override
        public TableInfo createTableInfo(ClusterService clusterService) {
            int noElements = info.ident().argumentTypes().size();
            Map<ColumnIdent, Reference> columnMap = new LinkedHashMap<>(noElements);
            Collection<Reference> columns = new ArrayList<>(noElements);

            ColumnIdent columnIdent = new ColumnIdent("generate_series");
            DataType dataType = DataTypes.LONG;
            Reference reference = new Reference(
                new ReferenceIdent(TABLE_IDENT, columnIdent),
                RowGranularity.DOC, dataType);

            columns.add(reference);
            columnMap.put(columnIdent, reference);

            final String localNodeId = clusterService.localNode().getId();
            return new StaticTableInfo(TABLE_IDENT, columnMap, columns, Collections.emptyList()) {
                @Override
                public RowGranularity rowGranularity() {
                    return RowGranularity.DOC;
                }

                @Override
                public Routing getRouting(WhereClause whereClause, @Nullable String preference) {
                    return Routing.forTableOnSingleNode(TABLE_IDENT, localNodeId);
                }
            };
        }
    }

    public static void register(TableFunctionModule module){
        module.register(NAME, new BaseFunctionResolver(
            Signature.withLenientVarArgs(Signature.ArgMatcher.NUMERIC)) {

            @Override
            public FunctionImplementation getForTypes(List<DataType> dataTypes) throws IllegalArgumentException {
                return new GenerateSeriesFunction.GenerateSeriesFunctionImplementation(
                    new FunctionInfo(new FunctionIdent(NAME, dataTypes), DataTypes.OBJECT, FunctionInfo.Type.TABLE));
            }
        });
    }
}
