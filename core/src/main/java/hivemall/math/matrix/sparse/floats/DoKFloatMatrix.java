/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package hivemall.math.matrix.sparse.floats;

import hivemall.annotations.Experimental;
import hivemall.math.matrix.AbstractMatrix;
import hivemall.math.matrix.FloatMatrix;
import hivemall.math.matrix.MatrixUtils;
import hivemall.math.matrix.builders.DoKMatrixBuilder;
import hivemall.math.vector.Vector;
import hivemall.math.vector.VectorProcedure;
import hivemall.utils.collections.maps.Long2FloatOpenHashTable;
import hivemall.utils.collections.maps.Long2FloatOpenHashTable.IMapIterator;
import hivemall.utils.lang.Preconditions;
import hivemall.utils.lang.Primitives;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

/**
 * Dictionary Of Keys based sparse matrix.
 *
 * This is an efficient structure for constructing a sparse matrix incrementally.
 */
@Experimental
public final class DoKFloatMatrix extends AbstractMatrix implements FloatMatrix {

    @Nonnull
    private final Long2FloatOpenHashTable elements;
    @Nonnegative
    private int numRows;
    @Nonnegative
    private int numColumns;
    @Nonnegative
    private int nnz;

    public DoKFloatMatrix() {
        this(0, 0);
    }

    public DoKFloatMatrix(@Nonnegative int numRows, @Nonnegative int numCols) {
        this(numRows, numCols, 0.05f);
    }

    public DoKFloatMatrix(@Nonnegative int numRows, @Nonnegative int numCols,
            @Nonnegative float sparsity) {
        super();
        Preconditions.checkArgument(sparsity >= 0.f && sparsity <= 1.f, "Invalid Sparsity value: "
                + sparsity);
        int initialCapacity = Math.max(16384, Math.round(numRows * numCols * sparsity));
        this.elements = new Long2FloatOpenHashTable(initialCapacity);
        elements.defaultReturnValue(0.f);
        this.numRows = numRows;
        this.numColumns = numCols;
        this.nnz = 0;
    }

    public DoKFloatMatrix(@Nonnegative int initSize) {
        super();
        int initialCapacity = Math.max(initSize, 16384);
        this.elements = new Long2FloatOpenHashTable(initialCapacity);
        elements.defaultReturnValue(0.f);
        this.numRows = 0;
        this.numColumns = 0;
        this.nnz = 0;
    }

    @Override
    public boolean isSparse() {
        return true;
    }

    @Override
    public boolean isRowMajorMatrix() {
        return false;
    }

    @Override
    public boolean isColumnMajorMatrix() {
        return false;
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public boolean swappable() {
        return true;
    }

    @Override
    public int nnz() {
        return nnz;
    }

    @Override
    public int numRows() {
        return numRows;
    }

    @Override
    public int numColumns() {
        return numColumns;
    }

    @Override
    public int numColumns(@Nonnegative final int row) {
        int count = 0;
        for (int j = 0; j < numColumns; j++) {
            long index = index(row, j);
            if (elements.containsKey(index)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public double[] getRow(@Nonnegative final int index) {
        double[] dst = row();
        return getRow(index, dst);
    }

    @Override
    public double[] getRow(@Nonnegative final int row, @Nonnull final double[] dst) {
        checkRowIndex(row, numRows);

        final int end = Math.min(dst.length, numColumns);
        for (int col = 0; col < end; col++) {
            long k = index(row, col);
            float v = elements.get(k);
            dst[col] = v;
        }

        return dst;
    }

    @Override
    public float[] getRow(@Nonnegative final int row, @Nonnull final float[] dst) {
        checkRowIndex(row, numRows);

        final int end = Math.min(dst.length, numColumns);
        for (int col = 0; col < end; col++) {
            long k = index(row, col);
            float v = elements.get(k);
            dst[col] = v;
        }

        return dst;
    }

    @Override
    public void getRow(@Nonnegative final int index, @Nonnull final Vector row) {
        checkRowIndex(index, numRows);
        row.clear();

        for (int col = 0; col < numColumns; col++) {
            long k = index(index, col);
            final float v = elements.get(k, 0.f);
            if (v != 0.f) {
                row.set(col, v);
            }
        }
    }

    @Override
    public float get(@Nonnegative final int row, @Nonnegative final int col,
            final float defaultValue) {
        long index = index(row, col);
        return elements.get(index, defaultValue);
    }

    @Override
    public void set(@Nonnegative final int row, @Nonnegative final int col, final float value) {
        checkIndex(row, col);

        final long index = index(row, col);
        if (value == 0.f && elements.containsKey(index) == false) {
            return;
        }

        if (elements.put(index, value, 0.f) == 0.f) {
            nnz++;
            this.numRows = Math.max(numRows, row + 1);
            this.numColumns = Math.max(numColumns, col + 1);
        }
    }

    @Override
    public float getAndSet(@Nonnegative final int row, @Nonnegative final int col, final float value) {
        checkIndex(row, col);

        final long index = index(row, col);
        if (value == 0.f && elements.containsKey(index) == false) {
            return 0.f;
        }

        final float old = elements.put(index, value, 0.f);
        if (old == 0.f) {
            nnz++;
            this.numRows = Math.max(numRows, row + 1);
            this.numColumns = Math.max(numColumns, col + 1);
        }
        return old;
    }

    @Override
    public void swap(@Nonnegative final int row1, @Nonnegative final int row2) {
        checkRowIndex(row1, numRows);
        checkRowIndex(row2, numRows);

        for (int j = 0; j < numColumns; j++) {
            final long i1 = index(row1, j);
            final long i2 = index(row2, j);

            final int k1 = elements._findKey(i1);
            final int k2 = elements._findKey(i2);

            if (k1 >= 0) {
                if (k2 >= 0) {
                    float v1 = elements._get(k1);
                    float v2 = elements._set(k2, v1);
                    elements._set(k1, v2);
                } else {// k1>=0 and k2<0
                    float v1 = elements._remove(k1);
                    elements.put(i2, v1);
                }
            } else if (k2 >= 0) {// k2>=0 and k1 < 0
                float v2 = elements._remove(k2);
                elements.put(i1, v2);
            } else {//k1<0 and k2<0
                continue;
            }
        }
    }

    @Override
    public void eachInRow(@Nonnegative final int row, @Nonnull final VectorProcedure procedure,
            final boolean nullOutput) {
        checkRowIndex(row, numRows);

        for (int col = 0; col < numColumns; col++) {
            long i = index(row, col);
            final int key = elements._findKey(i);
            if (key < 0) {
                if (nullOutput) {
                    procedure.apply(col, 0.f);
                }
            } else {
                float v = elements._get(key);
                procedure.apply(col, v);
            }
        }
    }

    @Override
    public void eachNonZeroInRow(@Nonnegative final int row,
            @Nonnull final VectorProcedure procedure) {
        checkRowIndex(row, numRows);

        for (int col = 0; col < numColumns; col++) {
            long i = index(row, col);
            final float v = elements.get(i, 0.f);
            if (v != 0.f) {
                procedure.apply(col, v);
            }
        }
    }

    @Override
    public void eachColumnIndexInRow(int row, VectorProcedure procedure) {
        checkRowIndex(row, numRows);

        for (int col = 0; col < numColumns; col++) {
            long i = index(row, col);
            final int key = elements._findKey(i);
            if (key != -1) {
                procedure.apply(col);
            }
        }
    }

    @Override
    public void eachInColumn(@Nonnegative final int col, @Nonnull final VectorProcedure procedure,
            final boolean nullOutput) {
        checkColIndex(col, numColumns);

        for (int row = 0; row < numRows; row++) {
            long i = index(row, col);
            final int key = elements._findKey(i);
            if (key < 0) {
                if (nullOutput) {
                    procedure.apply(row, 0.f);
                }
            } else {
                float v = elements._get(key);
                procedure.apply(row, v);
            }
        }
    }

    @Override
    public void eachNonZeroInColumn(@Nonnegative final int col,
            @Nonnull final VectorProcedure procedure) {
        checkColIndex(col, numColumns);

        for (int row = 0; row < numRows; row++) {
            long i = index(row, col);
            final float v = elements.get(i, 0.f);
            if (v != 0.f) {
                procedure.apply(row, v);
            }
        }
    }

    @Override
    public void eachNonZeroCell(@Nonnull final VectorProcedure procedure) {
        if (nnz == 0) {
            return;
        }
        final IMapIterator itor = elements.entries();
        while (itor.next() != -1) {
            long k = itor.getKey();
            int row = Primitives.getHigh(k);
            int col = Primitives.getLow(k);
            float value = itor.getValue();
            procedure.apply(row, col, value);
        }
    }

    @Override
    public CSRFloatMatrix toRowMajorMatrix() {
        final int nnz = elements.size();
        final int[] rows = new int[nnz];
        final int[] cols = new int[nnz];
        final float[] data = new float[nnz];

        final IMapIterator itor = elements.entries();
        for (int i = 0; i < nnz; i++) {
            if (itor.next() == -1) {
                throw new IllegalStateException("itor.next() returns -1 where i=" + i);
            }
            long k = itor.getKey();
            rows[i] = Primitives.getHigh(k);
            cols[i] = Primitives.getLow(k);
            data[i] = itor.getValue();
        }

        return MatrixUtils.coo2csr(rows, cols, data, numRows, numColumns, true);
    }

    @Override
    public CSCFloatMatrix toColumnMajorMatrix() {
        final int nnz = elements.size();
        final int[] rows = new int[nnz];
        final int[] cols = new int[nnz];
        final float[] data = new float[nnz];

        final IMapIterator itor = elements.entries();
        for (int i = 0; i < nnz; i++) {
            if (itor.next() == -1) {
                throw new IllegalStateException("itor.next() returns -1 where i=" + i);
            }
            long k = itor.getKey();
            rows[i] = Primitives.getHigh(k);
            cols[i] = Primitives.getLow(k);
            data[i] = itor.getValue();
        }

        return MatrixUtils.coo2csc(rows, cols, data, numRows, numColumns, true);
    }

    @Override
    public DoKMatrixBuilder builder() {
        return new DoKMatrixBuilder(elements.size());
    }

    @Nonnegative
    private static long index(@Nonnegative final int row, @Nonnegative final int col) {
        return Primitives.toLong(row, col);
    }

    @Override
    public double get(@Nonnegative final int row, @Nonnegative final int col,
            final double defaultValue) {
        return get(row, col, (float) defaultValue);
    }

    @Override
    public void set(@Nonnegative final int row, @Nonnegative final int col, final double value) {
        set(row, col, (float) value);
    }

    @Override
    public double getAndSet(@Nonnegative final int row, @Nonnegative final int col,
            final double value) {
        return getAndSet(row, col, (float) value);
    }

}
