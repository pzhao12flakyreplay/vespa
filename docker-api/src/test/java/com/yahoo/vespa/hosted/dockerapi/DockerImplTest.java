// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author tonytv
 */
public class DockerImplTest {
    @Test
    public void testDockerConfigWithUnixPath() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "unix:///var/run/docker.sock";
        DockerConfig config = createConfig(dockerUri, null, null, null);
        DefaultDockerClientConfig clientConfig = DockerImpl.buildDockerClientConfig(config).build();

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was set when using socket", clientConfig.getSSLConfig() == null);
    }

    @Test
    public void testDockerConfigWithTcpPathWithoutSSL() {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, null, null, null);
        DefaultDockerClientConfig clientConfig = DockerImpl.buildDockerClientConfig(config).build();

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was set", clientConfig.getSSLConfig() == null);
    }

    @Test
    public void testDockerConfigWithTcpPathWithSslConfig() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, "/some/path/ca", "/some/path/cert", "/some/path/key");
        DefaultDockerClientConfig clientConfig = DockerImpl.buildDockerClientConfig(config).build();

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was not set", clientConfig.getSSLConfig() != null);
    }

    @Test(expected=RuntimeException.class)
    public void testDockerConfigWithTcpPathWithInvalidSslConfig() throws IOException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String dockerUri = "tcp://127.0.0.1:2376";
        DockerConfig config = createConfig(dockerUri, "/some/path/ca", "/some/path/cert", "/some/path/key");
        DefaultDockerClientConfig clientConfig = DockerImpl.buildDockerClientConfig(config).build();

        assertTrue("Docker uri incorrectly set", clientConfig.getDockerHost().toString().equals(dockerUri));
        assertTrue("SSL config was not set", clientConfig.getSSLConfig() != null);

        // SSL certificates are read during the getSSLContext(), the invalid paths should cause a RuntimeException
        clientConfig.getSSLConfig().getSSLContext();
    }

    private static DockerConfig createConfig(String uri, String caCertPath, String clientCertPath, String clientKeyPath) {
        DockerConfig.Builder configBuilder = new DockerConfig.Builder();

        if (uri             != null) configBuilder.uri(uri);
        if (caCertPath      != null) configBuilder.caCertPath(caCertPath);
        if (clientCertPath  != null) configBuilder.clientCertPath(clientCertPath);
        if (clientKeyPath   != null) configBuilder.clientKeyPath(clientKeyPath);

        return new DockerConfig(configBuilder);
    }


    @Test
    public void testExecuteCompletes() throws Exception {
        final String containerId = "container-id";
        final String[] command = new String[] {"/bin/ls", "-l"};
        final String execId = "exec-id";
        final int exitCode = 3;

        final DockerClient dockerClient = mock(DockerClient.class);

        final ExecCreateCmdResponse response = mock(ExecCreateCmdResponse.class);
        when(response.getId()).thenReturn(execId);

        final ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        when(dockerClient.execCreateCmd(any(String.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(Matchers.<String>anyVararg())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(any(Boolean.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(any(String.class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(response);

        final ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(any(String.class))).thenReturn(execStartCmd);
        when(execStartCmd.exec(any(ExecStartResultCallback.class))).thenReturn(mock(ExecStartResultCallback.class));

        final InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        final InspectExecResponse state = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(any(String.class))).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(state);
        when(state.isRunning()).thenReturn(false);
        when(state.getExitCode()).thenReturn(exitCode);

        final Docker docker = new DockerImpl(dockerClient);
        final ProcessResult result = docker.executeInContainer(new ContainerName(containerId), command);
        assertThat(result.getExitStatus(), is(exitCode));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pullImageAsyncIfNeededSuccessfully() {
        final DockerImage image = new DockerImage("test:1.2.3");

        InspectImageResponse inspectImageResponse = mock(InspectImageResponse.class);
        when(inspectImageResponse.getId()).thenReturn(image.asString());

        InspectImageCmd imageInspectCmd = mock(InspectImageCmd.class);
        when(imageInspectCmd.exec())
                .thenThrow(new NotFoundException("Image not found"))
                .thenReturn(inspectImageResponse);

        // Array to make it final
        ArgumentCaptor<ResultCallback> resultCallback = ArgumentCaptor.forClass(ResultCallback.class);
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(pullImageCmd.exec(resultCallback.capture())).thenReturn(null);

        final DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.inspectImageCmd(image.asString())).thenReturn(imageInspectCmd);
        when(dockerClient.pullImageCmd(eq(image.asString()))).thenReturn(pullImageCmd);

        final DockerImpl docker = new DockerImpl(dockerClient);
        docker.setMetrics(new MetricReceiverWrapper(MetricReceiver.nullImplementation));
        assertTrue("Should return true, we just scheduled the pull", docker.pullImageAsyncIfNeeded(image));
        assertTrue("Should return true, the pull i still ongoing", docker.pullImageAsyncIfNeeded(image));

        assertTrue(docker.imageIsDownloaded(image));
        resultCallback.getValue().onComplete();
        assertFalse(docker.pullImageAsyncIfNeeded(image));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void pullImageAsyncIfNeededWithError() {
        final DockerImage image = new DockerImage("test:1.2.3");

        InspectImageCmd imageInspectCmd = mock(InspectImageCmd.class);
        when(imageInspectCmd.exec()).thenThrow(new NotFoundException("Image not found"));

        // Array to make it final
        ArgumentCaptor<ResultCallback> resultCallback = ArgumentCaptor.forClass(ResultCallback.class);
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(pullImageCmd.exec(resultCallback.capture())).thenReturn(null);

        final DockerClient dockerClient = mock(DockerClient.class);
        when(dockerClient.inspectImageCmd(image.asString())).thenReturn(imageInspectCmd);
        when(dockerClient.pullImageCmd(eq(image.asString()))).thenReturn(pullImageCmd);

        final DockerImpl docker = new DockerImpl(dockerClient);
        docker.setMetrics(new MetricReceiverWrapper(MetricReceiver.nullImplementation));
        assertTrue("Should return true, we just scheduled the pull", docker.pullImageAsyncIfNeeded(image));
        assertTrue("Should return true, the pull i still ongoing", docker.pullImageAsyncIfNeeded(image));

        try {
            resultCallback.getValue().onComplete();
        } catch (Exception ignored) { }

        assertFalse(docker.imageIsDownloaded(image));
        assertTrue("Should return true, new pull scheduled", docker.pullImageAsyncIfNeeded(image));
    }
}
