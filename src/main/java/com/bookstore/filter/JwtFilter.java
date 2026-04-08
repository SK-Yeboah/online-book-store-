package com.bookstore.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;

import com.bookstore.security.Jwtutil;
import com.bookstore.security.TokenBlacklist;
import com.bookstore.service.UserDetailsService;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final Jwtutil jwtutil;
    private final UserDetailsService  userdetailsService;
    private final TokenBlacklist tokenBlackList;
    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request, 
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain)  throws ServletException, IOException{
            String authHeader = request.getHeader("Authorization");

            if(authHeader == null || !authHeader.startsWith("Bearer ")){
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7).trim();
            if(token.isEmpty()){
                filterChain.doFilter(request, response);
                return;
            }

            String username;
            try{
                 username = jwtutil.extractUsername(token);
            }catch(Exception e){
                if(log.isDebugEnabled()){
                    log.debug("JWT parse failed: invalid or expired token");
                }
                filterChain.doFilter(request, response);
                return;
            }

            // check if token is blacklisted 
            String jti = jwtutil.extractJti(token);
            if(tokenBlackList.isBlackListed(jti)){
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"Token has been revoked\"}");
                return; //Stop - do not process the chain
            }

            if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
                UserDetails userDetails  = userdetailsService.loadUserByUsername(username);
                if(jwtutil.validateToken(token, userDetails)){
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            filterChain.doFilter(request, response);

        }
    



}