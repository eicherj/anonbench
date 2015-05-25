/*
 * Source code of our CBMS 2014 paper "A benchmark of globally-optimal
 * methods for the de-identification of biomedical data"
 * 
 * Copyright (C) 2014 Florian Kohlmayer, Fabian Prasser
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.deidentifier.arx;

import java.io.IOException;

import org.deidentifier.arx.BenchmarkSetup.BenchmarkAlgorithm;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkCriterion;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkDataset;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkMetric;
import org.deidentifier.arx.algorithm.AbstractBenchmarkAlgorithm;
import org.deidentifier.arx.algorithm.AlgorithmDataFly;
import org.deidentifier.arx.algorithm.AlgorithmHeurakles;
import org.deidentifier.arx.algorithm.AlgorithmImprovedGreedy;
import org.deidentifier.arx.framework.check.INodeChecker;
import org.deidentifier.arx.framework.check.NodeChecker;
import org.deidentifier.arx.framework.data.DataManager;
import org.deidentifier.arx.framework.data.Dictionary;
import org.deidentifier.arx.framework.lattice.AbstractLattice;
import org.deidentifier.arx.framework.lattice.LatticeBuilder;

import de.linearbits.subframe.Benchmark;

/**
 * This class implements the main benchmark driver
 * @author Fabian Prasser
 */
public class BenchmarkDriver {

    /** Snapshot size. */
    private final double    snapshotSizeDataset  = 0.2d;

    /** Snapshot size snapshot */
    private final double    snapshotSizeSnapshot = 0.8d;

    /** History size. */
    private final int       historySize          = 200;

    /** The benchmark instance */
    private final Benchmark benchmark;

    /**
     * Creates a new benchmark driver
     * 
     * @param benchmark
     */
    public BenchmarkDriver(Benchmark benchmark) {
        this.benchmark = benchmark;
    }

    /**
     * Performs data anonymization
     * 
     * @param criteria
     * @param data
     * @param metric
     * @param suppression
     * @param algorithm
     * @throws IOException
     */
    public void anonymize(BenchmarkCriterion[] criteria,
                          BenchmarkDataset dataset,
                          BenchmarkMetric metric,
                          double suppression,
                          BenchmarkAlgorithm algorithm) throws IOException {

        // Build implementation
        AbstractBenchmarkAlgorithm implementation = getImplementation(criteria, dataset, metric, suppression, algorithm, true);

        // Execute
        implementation.traverse();
        // Store optimum
        benchmark.addValue(BenchmarkMain.INFORMATION_LOSS,
                           getInformationLoss(metric, algorithm, implementation, criteria, dataset, suppression));
    }

    private String getInformationLoss(BenchmarkMetric metric,
                                      BenchmarkAlgorithm algorithm,
                                      AbstractBenchmarkAlgorithm implementation,
                                      BenchmarkCriterion[] criteria,
                                      BenchmarkDataset dataset,
                                      double suppression) throws IOException {
        if (null == implementation.getGlobalOptimum()) {
            return "NoSolutionFound";
        }
        if (BenchmarkAlgorithm.HEURAKLES == algorithm) {
            return implementation.getGlobalOptimum().getInformationLoss().toString();
        }
        AbstractBenchmarkAlgorithm _algorithm = getImplementation(criteria, dataset, metric, suppression, algorithm, false);
        return _algorithm.getInformationLoss(implementation.getGlobalOptimum()).toString();

    }

    /**
     * @param dataset
     * @param criteria
     * @param algorithm
     * @return
     * @throws IOException
     */
    private AbstractBenchmarkAlgorithm getImplementation(BenchmarkCriterion[] criteria,
                                                         BenchmarkDataset dataset,
                                                         BenchmarkMetric metric,
                                                         double suppression,
                                                         BenchmarkAlgorithm algorithm, boolean useDecisionMetric) throws IOException {
        // Prepare
        Data data = BenchmarkSetup.getData(dataset, criteria);
        ARXConfiguration config = BenchmarkSetup.getConfiguration(criteria, dataset, metric, suppression, algorithm, useDecisionMetric);
        DataHandle handle = data.getHandle();

        // Encode
        final String[] header = ((DataHandleInput) handle).header;
        final int[][] dataArray = ((DataHandleInput) handle).data;
        final Dictionary dictionary = ((DataHandleInput) handle).dictionary;
        final DataManager manager = new DataManager(header,
                                                    dataArray,
                                                    dictionary,
                                                    data.getDefinition(),
                                                    config.getCriteria());

        // Initialize
        config.initialize(manager);

        // Build or clean the lattice
        AbstractLattice lattice = new LatticeBuilder(manager.getMaxLevels(),
                                                     manager.getMinLevels()).build();

        // Build a node checker
        INodeChecker checker = new NodeChecker(manager,
                                               config.getMetric(),
                                               config.getInternalConfiguration(),
                                               historySize,
                                               snapshotSizeDataset,
                                               snapshotSizeSnapshot);

        // Initialize the metric
        config.getMetric().initialize(handle.getDefinition(),
                                      manager.getDataQI(),
                                      manager.getHierarchies(),
                                      config);

        // Create an algorithm instance
        AbstractBenchmarkAlgorithm implementation;

        switch (algorithm) {
        case HEURAKLES:
            implementation = new AlgorithmHeurakles(lattice, checker);
            break;
        case DATAFLY:
            implementation = new AlgorithmDataFly(lattice, checker);
            break;
        case IMPROVED_GREEDY:
            implementation = new AlgorithmImprovedGreedy(lattice, checker);
            break;
        default:
            throw new RuntimeException("Invalid algorithm");
        }
        return implementation;
    }

}
