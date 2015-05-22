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

package org.deidentifier.arx.algorithm;

import java.util.Comparator;

import org.deidentifier.arx.framework.check.INodeChecker;
import org.deidentifier.arx.framework.check.history.History;
import org.deidentifier.arx.framework.lattice.Lattice;
import org.deidentifier.arx.framework.lattice.Node;

/**
 * This class implements a simple depth-first-search with an outer loop.
 * 
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public class AlgorithmImprovedGreedy extends AbstractBenchmarkAlgorithm {

    private static final int MAX_QUEUE_SIZE          = 50000;
    public static final int  NODE_PROPERTY_COMPLETED = 1 << 20;
    private int              stepping;

    /**
     * Creates a new instance of the heurakles algorithm.
     * 
     * @param lattice The lattice
     * @param checker The checker
     * @param config The config
     * 
     */
    public AlgorithmImprovedGreedy(Lattice lattice, INodeChecker checker) {
        super(lattice, checker);
        checker.getHistory().setStorageTrigger(History.STORAGE_TRIGGER_ALL);
        this.stepping = lattice.getTop().getLevel();
        this.stepping = this.stepping > 0 ? this.stepping : 1;
        System.out.println("Stepping: " + this.stepping);
    }

    /**
     * Makes sure that the given node has been checked
     * @param node
     */
    private void assureChecked(final Node node) {
        if (!node.hasProperty(Node.PROPERTY_CHECKED)) {
            check(node);
        }
    }

    @Override
    public void traverse() {

        MinMaxPriorityQueue<Node> _queue = new MinMaxPriorityQueue<Node>(MAX_QUEUE_SIZE, new Comparator<Node>() {
            @Override
            public int compare(Node arg0, Node arg1) {
                return arg0.getInformationLoss().compareTo(arg1.getInformationLoss());
            }
        });

        Node bottom = lattice.getBottom();
        assureChecked(bottom);
        if (getGlobalOptimum() != null) {
            return;
        }
        _queue.add(bottom);

        Node next;
        int step = 0;
        while ((next = _queue.poll()) != null) {

            if (!prune(next)) {

                step++;
                if (step % stepping == 0) {
                    dfs(_queue, next);
                } else {
                    processNode(_queue, next);
                }

                if (getGlobalOptimum() != null) {
                    return;
                }
            }
        }

    }

    /**
     * Performs a dfs starting from the node
     * @param _queue
     * @param node
     */
    private void dfs(MinMaxPriorityQueue<Node> _queue, Node node) {

        Node nextNode = processNode(_queue, node);
        if (nextNode != null) {
            _queue.remove(nextNode);
            dfs(_queue, nextNode);
        }
    }

    /**
     * Returns the successor with minimal information loss, if any, null otherwise.
     * @param _queue
     * @param node
     * @return
     */
    private Node processNode(MinMaxPriorityQueue<Node> _queue, Node node) {

        Node result = null;

        for (Node successor : node.getSuccessors()) {

            if (getGlobalOptimum() != null) {
                return null;
            }

            if (!successor.hasProperty(NODE_PROPERTY_COMPLETED)) {
                assureChecked(successor);
                _queue.add(successor);
                if (result == null || successor.getInformationLoss().compareTo(result.getInformationLoss()) < 0) {
                    result = successor;
                }
            }

            while (_queue.size() > MAX_QUEUE_SIZE) {
                _queue.removeTail();
            }
        }

        lattice.setProperty(node, NODE_PROPERTY_COMPLETED);

        return result;
    }

    /**
     * Returns whether we can prune this node
     * @param node
     * @return
     */
    private boolean prune(Node node) {
        // A node (and it's direct and indirect successors, respectively) can be pruned if
        // the information loss is monotonic and the nodes's IL is greater or equal than the IL of the
        // global maximum (regardless of the anonymity criterion's monotonicity)
        boolean metricMonotonic = checker.getMetric().isMonotonic() || checker.getConfiguration().getAbsoluteMaxOutliers() == 0;

        // Depending on monotony of metric we choose to compare either IL or monotonic subset with the global optimum
        boolean prune = false;
        if (getGlobalOptimum() != null) {
            if (metricMonotonic) prune = node.getInformationLoss().compareTo(getGlobalOptimum().getInformationLoss()) >= 0;
        }

        return (prune || node.hasProperty(NODE_PROPERTY_COMPLETED));
    }
}
