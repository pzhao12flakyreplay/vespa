// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TestIdentities;
import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.NToken;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Optional;

/**
 * This overrides methods in Authorizer which relies on properties set by jdisc HTTP filters.
 * This is necessary because filters are not currently executed when executing requests with Application.
 * 
 * @author bratseth
 * @author bjorncs
 */
@SuppressWarnings("unused") // injected
public class MockAuthorizer extends Authorizer {

    public MockAuthorizer(Controller controller, EntityService entityService, AthenzClientFactory athenzClientFactory) {
        super(controller, entityService, athenzClientFactory);
    }

    /** Returns a principal given by the request parameters 'domain' and 'user' */
    @Override
    public Optional<AthenzPrincipal> getPrincipalIfAny(HttpRequest request) {
        String domain = request.getHeader("Athenz-Identity-Domain");
        String name = request.getHeader("Athenz-Identity-Name");
        if (domain == null || name == null) return Optional.empty();
        return Optional.of(
                new AthenzPrincipal(
                        AthenzUtils.createAthenzIdentity(new AthenzDomain(domain), name),
                        new NToken("dummy")));
    }

    /** Returns the hardcoded NToken of {@link TestIdentities#userId} */
    @Override
    public Optional<NToken> getNToken(HttpRequest request) {
        return Optional.of(TestIdentities.userNToken);
    }


    @Override
    protected Optional<SecurityContext> securityContextOf(HttpRequest request) {
        return getPrincipalIfAny(request).map(MockSecurityContext::new);
    }
    
    private static final class MockSecurityContext implements SecurityContext {
        
        private final Principal principal;
        
        private MockSecurityContext(Principal principal) {
            this.principal = principal;
        }
        
        @Override
        public Principal getUserPrincipal() { return principal; }

        @Override
        public boolean isUserInRole(String role) { return false; }

        @Override
        public boolean isSecure() { return true; }

        @Override
        public String getAuthenticationScheme() { throw new UnsupportedOperationException(); }

    }

}
