package com.isd.wms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtRequestFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public JwtRequestFilter(UserDetailsService userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        final String requestURI = request.getRequestURI();

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
                log.debug("JWT Token detected. Extracted username: '{}' for URI: {}", username, requestURI);
            } catch (Exception e) {
                log.error("Failed to extract username from JWT token for URI: {}", requestURI, e);
            }
        } else {
            log.trace("No Bearer token found in Authorization header for URI: {}", requestURI);
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("User '{}' is not authenticated yet. Loading details...", username);

            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    log.debug("SecurityContext successfully updated for user '{}' with authorities: {}",
                        username, userDetails.getAuthorities());
                } else {
                    log.warn("JWT Token validation failed for user '{}' on URI: {}", username, requestURI);
                }
            } catch (UsernameNotFoundException e) {
                log.warn("Token validation failed: User '{}' not found in database. The token is likely obsolete due to a username change.", username);
            } catch (Exception e) {
                log.error("Error setting security context for user '{}'", username, e);
            }
        }

        chain.doFilter(request, response);
    }
}
