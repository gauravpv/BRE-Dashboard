package com.bredashboard.auth.service;

import com.bredashboard.auth.domain.AppUser;
import com.bredashboard.auth.domain.BreUserPrincipal;
import com.bredashboard.auth.repository.AppUserRepository;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BreUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public BreUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmailIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled");
        }
        if (!user.hasLocalPassword()) {
            throw new BadCredentialsException("Use Microsoft sign-in for this account");
        }

        return BreUserPrincipal.fromUser(user);
    }
}
