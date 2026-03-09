package com.keywordspy.game.service;

import com.keywordspy.game.model.User;
import com.keywordspy.game.model.UserStats;
import com.keywordspy.game.repository.UserRepository;
import com.keywordspy.game.repository.UserStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), 
                user.getPasswordHash(), 
                new ArrayList<>()
        );
    }

    public User registerUser(String username, String email, String password, String displayName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));

        User savedUser = userRepository.save(user);
        
        UserStats stats = new UserStats();
        stats.setUserId(savedUser.getId());
        userStatsRepository.save(stats);

        return savedUser;
    }
    public User saveUser(User user) {
    return userRepository.save(user);
}
}
