// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.yahoo.log.LogLevel;

/**
 * A class that stores stats about outstanding merge operations for
 * the current cluster state version, and exports metrics about these.
 *
 * Each distributor reports outstanding merge operations for the different
 * storage nodes. These reports arrive with getnodestate RPC calls,
 * and eventually ends up as calls to updateForDistributor().
 * No assumptions are made on the sequence of getnodestate calls.
 * For instance, it's perfectly fine for the calls to arrive in the
 * following order:
 *   distributor 0
 *   distributor 1
 *   distributor 1
 *   distributor 0
 *   distributor 2
 *   ... etc
 *
 * Whereas the metrics we want, is how many merge operations are outstanding
 * for a given storage nodes. So we need to keep track of the latest info
 * from each distributor.
 *
 * @author hakonhall
 */
public class ClusterStatsAggregator {

    private static Logger log = Logger.getLogger(ClusterStatsAggregator.class.getName());

    private final Set<Integer> distributors;
    private final MetricUpdater updater;

    // Maps the distributor node index to a map of storage node index to the
    // storage node's merge stats.
    private final Map<Integer, StorageMergeStats> distributorToStats = new HashMap<>();

    // This is only needed as an optimization. should just be the sum of distributorToStats' StorageMergeStats.
    // Maps the storage node index to the aggregate merge stats for that storage node.
    // This MUST be kept up-to-date with distributorToStats;
    private final StorageMergeStats aggregatedStats;

    private int hostToStatsMapHashCode = 0;

    ClusterStatsAggregator(Set<Integer> distributors, Set<Integer> storageNodes, MetricUpdater updater) {
        this.distributors = distributors;
        aggregatedStats = new StorageMergeStats(storageNodes);
        this.updater = updater;
    }

    /**
     * Update the aggregator with the newest available stats from a distributor.
     * Will update metrics if necessary.
     */
    void updateForDistributor(Map<Integer, String> hostnames, int distributorIndex, StorageMergeStats storageStats) {
        if (!distributors.contains(distributorIndex)) {
            return;
        }

        addStatsFromDistributor(distributorIndex, storageStats);

        if (distributorToStats.size() < distributors.size()) {
            // Not all distributors have reported their merge stats through getnodestate yet.
            return;
        }

        Map<String, NodeMergeStats> hostToStatsMap = getHostToStatsMap(hostnames);
        if (hostToStatsMap == null) {
            return;
        }

        if (hostToStatsMapHashCode == 0 || hostToStatsMapHashCode != hostToStatsMap.hashCode()) {
            updater.updateMergeOpMetrics(hostToStatsMap);
            hostToStatsMapHashCode = hostToStatsMap.hashCode();
        }
    }

    private Map<String, NodeMergeStats> getHostToStatsMap(Map<Integer, String> hostnames) {
        Map<String, NodeMergeStats> hostToStatsMap = new HashMap<>(aggregatedStats.size());
        for (NodeMergeStats nodeStats : aggregatedStats) {
            // The hosts names are kept up-to-date from Slobrok, and MAY therefore be arbitrarily
            // different from the node set used by aggregatedStats (and typically tied to a cluster state).
            // If so, we will not pretend the returned map is complete, and will return null.
            String host = hostnames.get(nodeStats.getNodeIndex());
            if (host == null) {
                log.log(LogLevel.DEBUG, "Failed to find the host name of storage node " + nodeStats.getNodeIndex() +
                        ". Skipping the report from " + ClusterStatsAggregator.class.getName());
                return null;
            }

            hostToStatsMap.put(host, nodeStats);
        }

        return hostToStatsMap;
    }

    private void addStatsFromDistributor(int distributorIndex, StorageMergeStats storageStatsFromDistributor) {
        StorageMergeStats previousStorageStats = distributorToStats.put(distributorIndex, storageStatsFromDistributor);

        for (NodeMergeStats storageNode : aggregatedStats) {
            Integer storageNodeIndex = storageNode.getNodeIndex();

            NodeMergeStats statsToAdd = storageStatsFromDistributor.getStorageNode(storageNodeIndex);
            if (statsToAdd != null) {
                storageNode.add(statsToAdd);
            }

            if (previousStorageStats != null) {
                NodeMergeStats statsToSubtract = storageStatsFromDistributor.getStorageNode(storageNodeIndex);
                if (statsToSubtract != null) {
                    storageNode.subtract(statsToSubtract);
                }
            }
        }
    }

}
