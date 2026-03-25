package com.keywordspy.game.service;

import com.keywordspy.game.model.Role;
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

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public String generateResetToken(User user) {
        // Tạo mã xác nhận ngẫu nhiên 6 chữ số
        String token = String.format("%06d", new Random().nextInt(999999));
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15)); // Hết hạn sau 15 phút
        userRepository.save(user);
        return token;
    }

    public boolean verifyResetToken(String email, String token) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return token.equals(user.getResetToken()) &&
                   user.getResetTokenExpiry() != null &&
                   user.getResetTokenExpiry().isAfter(LocalDateTime.now());
        }
        return false;
    }

    public void clearResetToken(User user) {
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail));
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername() != null ? user.getUsername() : user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                true,
                true,
                true,

                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }

    public User registerUser(String username, String email, String password, String displayName) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(username != null ? username : email.split("@")[0]);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_USER);
        User savedUser = userRepository.save(user);


        UserStats stats = new UserStats();
        stats.setUserId(savedUser.getId());
        userStatsRepository.save(stats);

        return savedUser;
    }

    public User registerUser(String username, String email, String password, String displayName, Role role) {
        User user = registerUser(username, email, password, displayName);
        if (role != null && role != user.getRole()) {
            user.setRole(role);
            user = userRepository.save(user);
        }
        return user;
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public long countUsers() {
        return userRepository.count();
    }

    public User updateActiveStatus(String id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(active);
        return userRepository.save(user);
    }

    public void resetPassword(String id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public boolean processPasswordReset(String email, String token, String newPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false; // User không tồn tại
        }

        User user = userOpt.get();

        // Kiểm tra token có hợp lệ không
        if (!token.equals(user.getResetToken()) || user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return false; // Token không hợp lệ hoặc hết hạn
        }

        // Cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // Xóa token sau khi sử dụng
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        // Lưu tất cả thay đổi vào DB
        userRepository.save(user);

        return true;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Kiểm tra mật khẩu cũ có khớp không
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return false; // Mật khẩu cũ không đúng
        }

        // Cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return true;
    }

    public User updateRole(String id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(role);
        return userRepository.save(user);
    }
}
