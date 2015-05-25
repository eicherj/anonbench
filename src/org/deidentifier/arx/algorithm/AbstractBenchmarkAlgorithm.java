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
import org.deidentifier.arx.framework.lattice.AbstractLattice;
import org.deidentifier.arx.framework.lattice.Node;
import org.deidentifier.arx.metric.InformationLoss;

/**
 * Abstract base class for algorithms used in the benchmark
 * @author Fabian Prasser
 */
public abstract class AbstractBenchmarkAlgorithm extends AbstractAlgorithm {

    /** The maximal size of the priority queue */
    private static final int MAX_QUEUE_SIZE          = 50000;
    /** The property indicating whether a node has been seen and checked already */
    public static final int  NODE_PROPERTY_COMPLETED = 1 << 20;
    /** The number of rollups that could have been performed */
    protected int            rollups;
    /** The number of checks */
    protected int            checks;
    /** The node checked previously */
    protected Node           previous;
    /** The hierarchy heights for each QI. */
    protected int[]          hierarchyHeights;
    /** The number indicating how often a dfs will be performed */
    protected int            stepping;

    /**
     * Constructor
     * @param lattice
     * @param checker
     */
    protected AbstractBenchmarkAlgorithm(AbstractLattice lattice, INodeChecker checker) {
        super(lattice, checker);
        this.hierarchyHeights = lattice.getTop().getTransformation().clone();
        for (int i = 0; i < hierarchyHeights.length; i++) {
            this.hierarchyHeights[i]++;
        }
    }

    public boolean isMaterializedLatticeRequired() {
        return false;
    };

    /**
     * Returns the number of checks
     * @return
     */
    public int getNumChecks() {
        return checks;
    }

    /**
     * Returns the number of potential rollups
     * @return
     */
    public int getNumRollups() {
        return rollups;
    }

    /**
     * Performs a check and keeps track of potential rollups
     * @param node
     */
    protected void check(Node node) {

        // Check
        lattice.setChecked(node, checker.check(node));
        trackOptimum(node);
        checks++;

        // Store
        if (previous == null) {
            previous = node;
            return;
        }

        // Check if successor
        boolean successor = true;
        for (int i = 0; i < node.getTransformation().length; i++) {
            if (node.getTransformation()[i] < previous.getTransformation()[i]) {
                successor = false;
            }
        }

        previous = node;

        // Count
        if (successor) {
            rollups++;
        }
    }

    /**
     * Returns whether the transformation represented by the node was
     * determined to be anonymous. Returns <code>null</code> if such information
     * is not available
     * @param node
     * @return
     */
    protected Boolean isAnonymous(Node node) {
        if (node.hasProperty(Node.PROPERTY_ANONYMOUS)) {
            return true;
        } else if (node.hasProperty(Node.PROPERTY_NOT_ANONYMOUS)) {
            return false;
        } else {
            return null;
        }
    }
    
    /**
     * Returns the information loss of the given node
     * @param node
     * @return
     */
    public InformationLoss<?> getInformationLoss(Node node) {
        node.setData(null);
        return checker.check(node, true).informationLoss;
    }

    /**
     * Returns whether the node has been tagged already
     * @param node
     * @return
     */
    protected boolean isTagged(Node node) {
        return node.hasProperty(Node.PROPERTY_ANONYMOUS) ||
               node.hasProperty(Node.PROPERTY_NOT_ANONYMOUS);
    }

    /**
     * Tags a transformation
     * @param node
     * @param lattice
     * @param anonymous
     */
    protected void setAnonymous(AbstractLattice lattice, Node node, boolean anonymous) {
        if (anonymous) {
            lattice.setProperty(node, Node.PROPERTY_ANONYMOUS);
        } else {
            lattice.setProperty(node, Node.PROPERTY_NOT_ANONYMOUS);
        }
    }

    /**
     * Tags a transformation
     * @param node
     * @param anonymous
     */
    protected void setAnonymous(Node node, boolean anonymous) {
        setAnonymous(lattice, node, anonymous);
    }

    /**
     * Predictively tags the search space with the node's anonymity property
     * @param node
     * @param lattice
     */
    protected void tag(AbstractLattice lattice, Node node) {
        if (node.hasProperty(Node.PROPERTY_ANONYMOUS)) {
            tagAnonymous(lattice, node);
        }
        else if (node.hasProperty(Node.PROPERTY_NOT_ANONYMOUS)) {
            tagNotAnonymous(lattice, node);
        }
    }

    /**
     * Predictively tags the search space with the node's anonymity property
     * @param node
     */
    protected void tag(Node node) {
        tag(lattice, node);
    }

    /**
     * Predictively tags the search space from an anonymous transformation
     * @param node
     * @param lattice
     */
    protected void tagAnonymous(AbstractLattice lattice, Node node) {
        lattice.setPropertyUpwards(node, true, Node.PROPERTY_ANONYMOUS |
                                               Node.PROPERTY_SUCCESSORS_PRUNED);
    }

    /**
     * Predictively tags the search space from an anonymous transformation
     * @param node
     */
    protected void tagAnonymous(Node node) {
        tagAnonymous(lattice, node);
    }

    /**
     * Predictively tags the search space from a non-anonymous transformation
     * @param node
     * @param lattice
     */
    protected void tagNotAnonymous(AbstractLattice lattice, Node node) {
        lattice.setPropertyDownwards(node, false, Node.PROPERTY_NOT_ANONYMOUS);
    }

    /**
     * Predictively tags the search space from a non-anonymous transformation
     * @param node
     */
    protected void tagNotAnonymous(Node node) {
        tagNotAnonymous(lattice, node);
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

        for (Node successor : node.getSuccessors(true)) {

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
