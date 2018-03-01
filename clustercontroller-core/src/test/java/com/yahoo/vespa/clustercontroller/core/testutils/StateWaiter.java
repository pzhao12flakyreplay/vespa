// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.testutils;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.FakeTimer;
import com.yahoo.vespa.clustercontroller.core.listeners.SystemStateListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Old class used for waiting for something..
 * Deprecated.. Use the Waiter class instead
 */
public class StateWaiter implements SystemStateListener {
    private final FakeTimer timer;
    protected ClusterState current;
    private int stateUpdates = -1;

    public StateWaiter(FakeTimer timer) {
        this.timer = timer;
    }

    public void handleNewSystemState(ClusterState state) {
        synchronized(timer) {
            current = state;

            ++stateUpdates;
            timer.notifyAll();
        }
    }

    public int getStateUpdates() { return Math.max(0, stateUpdates); }

    public ClusterState getCurrentSystemState() {
        synchronized(timer) {
            return current;
        }
    }

    public void waitForState(String stateRegex, long timeout) {
        waitForState(stateRegex, timeout, 0);
    }

    /**
     * WARNING: If timeIntervalToProvokeRetry is set != 0 that means time will can be set far into future
     * and thus hit various unintended timeout periods. Only auto-step time if this is a non-issue.
     */
    public void waitForState(String stateRegex, long timeout, long timeIntervalToProvokeRetry) {
        Pattern p = Pattern.compile(stateRegex);
        long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeout;
        int iteration = 0;
        while (true) {
            ClusterState currentClusterState;
            synchronized(timer) {
                currentClusterState = current;

                if (currentClusterState != null) {
                    Matcher m = p.matcher(currentClusterState.toString());

                    if (m.matches()) {
                        return;
                    }
                }
                try{
                    if (timeIntervalToProvokeRetry == 0) {
                        timer.wait(endTime - startTime);
                    } else {
                        if (++iteration % 10 == 0) {
                            timer.advanceTime(timeIntervalToProvokeRetry);
                        }
                        timer.wait(10);
                    }
                } catch (InterruptedException e) {
                }
            }
            startTime = System.currentTimeMillis();
            if (startTime >= endTime) {
                throw new IllegalStateException("Timeout. Did not find a state matching " + stateRegex + " within timeout of " + timeout + " milliseconds. Current state is " + currentClusterState);
            }
        }
    }
    public void clear() {
        synchronized(timer) {
            current = null;
        }
    }

    public void waitForInitProgressPassed(Node node, double minProgress, int timeoutMS) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMS;
        while (true) {
            ClusterState currentClusterState;
            synchronized(timer) {
                currentClusterState = current;
                if (currentClusterState != null) {
                    if (currentClusterState.getNodeState(node).getInitProgress() >= minProgress) {
                        return;
                    }
                }
                try{
                    timer.wait(endTime - startTime);
                } catch (InterruptedException e) {
                }
            }
            startTime = System.currentTimeMillis();
            if (startTime >= endTime) {
                throw new IllegalStateException("Timeout. Did not get to " + minProgress + " init progress on node " + node + " within timeout of " + timeoutMS + " ms. Current init progress is " + currentClusterState.getNodeState(node).getInitProgress());
            }
        }
    }
}
