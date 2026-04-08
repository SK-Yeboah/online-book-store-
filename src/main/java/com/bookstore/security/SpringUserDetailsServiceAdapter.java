package com.bookstore.security;

import com.bookstore.service.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class SpringUserDetailsServiceAdapter implements org.springframework.security.core.userdetails.UserDetailsService {
    private final UserDetailsService userDetailsService;

    public SpringUserDetailsServiceAdapter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDetailsService.loadUserByUsername(username);
    }

}
