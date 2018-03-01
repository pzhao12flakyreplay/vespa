// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author freva
 */
public class PathResolver {
    static final Path ROOT = Paths.get("/");

    private static final Path RELATIVE_APPLICATION_STORAGE_PATH = Paths.get("home/docker/container-storage");
    private final Path applicationStoragePathForNodeAdmin;
    private final Path applicationStoragePathForHost;

    public PathResolver() {
        this(
                Paths.get("/host").resolve(RELATIVE_APPLICATION_STORAGE_PATH),
                ROOT.resolve(RELATIVE_APPLICATION_STORAGE_PATH));
    }

    public PathResolver(Path applicationStoragePathForNodeAdmin, Path applicationStoragePathForHost) {
        this.applicationStoragePathForNodeAdmin = applicationStoragePathForNodeAdmin;
        this.applicationStoragePathForHost = applicationStoragePathForHost;
    }

    public Path getApplicationStoragePathForNodeAdmin() {
        return applicationStoragePathForNodeAdmin;
    }

    public Path getApplicationStoragePathForHost() {
        return applicationStoragePathForHost;
    }
}
