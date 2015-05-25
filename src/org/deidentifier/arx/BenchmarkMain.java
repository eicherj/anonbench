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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.deidentifier.arx.BenchmarkSetup.BenchmarkAlgorithm;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkCriterion;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkDataset;
import org.deidentifier.arx.BenchmarkSetup.BenchmarkMetric;

import de.linearbits.subframe.Benchmark;
import de.linearbits.subframe.analyzer.ValueBuffer;

/**
 * Main benchmark class. Run with java -Xmx4G -XX:+UseConcMarkSweepGC -jar anonbench-0.1.jar
 * 
 * @author Fabian Prasser
 */
public class BenchmarkMain {

    /** The benchmark instance */
    private static final Benchmark BENCHMARK        = new Benchmark(new String[] { "Algorithm", "Dataset", "Criteria" });
    /** Label for information loss */
    public static final int        INFORMATION_LOSS = BENCHMARK.addMeasure("Information loss");

    static {
        BENCHMARK.addAnalyzer(INFORMATION_LOSS, new ValueBuffer());
    }

    /**
     * Main entry point
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {

        BenchmarkDriver driver = new BenchmarkDriver(BENCHMARK);

        // For each combination of criteria
        for (BenchmarkCriterion[] criteria : BenchmarkSetup.getPrivacyCriteria()) {

            // For each dataset
            for (BenchmarkDataset dataset : BenchmarkSetup.getDatasets()) {

                // For each metric
                for (BenchmarkMetric metric : BenchmarkSetup.getMetrics()) {

                    // For each suppression
                    for (double suppression : BenchmarkSetup.getSuppressionValues()) {

                        // For each algorithm
                        for (BenchmarkAlgorithm algorithm : BenchmarkSetup.getAlgorithms()) {

                            // Print status info
                            System.out.println("Running: " + algorithm.toString() + " / " + dataset.toString() + " / " +
                                               Arrays.toString(criteria));

                            // Benchmark
                            BENCHMARK.addRun(algorithm.toString(), dataset.toString(), Arrays.toString(criteria));

                            driver.anonymize(criteria, dataset, metric, suppression, algorithm);

                            // Write results incrementally
                            BENCHMARK.getResults().write(new File("results/results.csv"));
                        }
                    }
                }
            }
        }
    }
}
