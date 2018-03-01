// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperationsImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

/**
 * @author freva
 */
public class DockerFailTest {

    @Test
    public void dockerFailTest() throws Exception {
        try (DockerTester dockerTester = new DockerTester()) {
            ContainerNodeSpec containerNodeSpec = new ContainerNodeSpec.Builder()
                    .hostname("host1.test.yahoo.com")
                    .wantedDockerImage(new DockerImage("dockerImage"))
                    .nodeState(Node.State.active)
                    .nodeType("tenant")
                    .nodeFlavor("docker")
                    .wantedRestartGeneration(1L)
                    .currentRestartGeneration(1L)
                    .minCpuCores(1)
                    .minMainMemoryAvailableGb(1)
                    .minDiskAvailableGb(1)
                    .build();
            dockerTester.addContainerNodeSpec(containerNodeSpec);

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.nodeAdmin.getListOfHosts().size() == 0) {
                Thread.sleep(10);
            }

            dockerTester.callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");

            dockerTester.dockerMock.deleteContainer(new ContainerName("host1"));

            dockerTester.callOrderVerifier.assertInOrder(
                    "deleteContainer with ContainerName { name=host1 }",
                    "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "executeInContainerAsRoot with ContainerName { name=host1 }, args: [" + DockerOperationsImpl.NODE_PROGRAM + ", resume]");
        }
    }
}
