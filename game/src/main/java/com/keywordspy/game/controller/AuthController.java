package com.keywordspy.game.controller;

import com.keywordspy.game.dto.*;
import com.keywordspy.game.model.User;
import com.keywordspy.game.service.EmailService;
import com.keywordspy.game.service.JwtService;
import com.keywordspy.game.service.TokenBlacklistService;
import com.keywordspy.game.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            // Kiểm tra xác nhận mật khẩu
            if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu xác nhận không khớp."));
            }

            User user = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getDisplayName(),
                    request.getRole()
            );

            UserDetails userDetails = userService.loadUserByUsername(user.getUsername());
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            AuthResponse response = AuthResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(user.getRole())
                    .balance(user.getBalance())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtService.getAccessTokenExpirationInSeconds())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String principal = (request.getUsername() != null && !request.getUsername().isBlank())
                    ? request.getUsername()
                    : request.getEmail();

            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(principal, request.getPassword()));

            User user = userService.findByUsernameOrEmail(principal)
                    .orElseThrow(() -> new RuntimeException("User not found after authentication"));

            UserDetails userDetails = userService.loadUserByUsername(principal);
            String accessToken = jwtService.generateAccessToken(userDetails);
            String refreshToken = jwtService.generateRefreshToken(userDetails);

            AuthResponse response = AuthResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(user.getRole())
                    .balance(user.getBalance())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtService.getAccessTokenExpirationInSeconds())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            String username = jwtService.extractUsername(refreshToken);

            if (username != null) {
                UserDetails userDetails = userService.loadUserByUsername(username);
                if (jwtService.isTokenValid(refreshToken, userDetails)) {
                    User user = userService.findByUsernameOrEmail(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    
                    String accessToken = jwtService.generateAccessToken(userDetails);
                    AuthResponse response = AuthResponse.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .displayName(user.getDisplayName())
                            .avatarUrl(user.getAvatarUrl())
                            .accessToken(accessToken)
                            .role(user.getRole())
                            .balance(user.getBalance())
                            .expiresIn(jwtService.getAccessTokenExpirationInSeconds())
                            .build();
                    return ResponseEntity.ok(response);
                }
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String username = jwtService.extractUsername(token);
            User user = userService.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            AuthResponse response = AuthResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .avatarUrl(user.getAvatarUrl())
                    .role(user.getRole())
                    .balance(user.getBalance())
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No token provided"));
        }
        try {
            String token = authHeader.substring(7);

            // Check token có hợp lệ không trước khi blacklist
            String username = jwtService.extractUsername(token);
            if (username == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid token"));
            }

            tokenBlacklistService.blacklistToken(token);
            return ResponseEntity.ok(Map.of("message", "Logged out"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid token"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            Optional<User> userOpt = userService.findByEmail(request.getEmail());
            
            // Kiểm tra xem email có tồn tại và khớp với username không
            if (userOpt.isEmpty() || !userOpt.get().getUsername().equals(request.getUsername())) {
                // Bảo mật: Không thông báo là thông tin không khớp để tránh rò rỉ dữ liệu
                return ResponseEntity.ok(Map.of("message", "Nếu thông tin khớp với hệ thống, mã xác nhận sẽ được gửi đi."));
            }

            User user = userOpt.get();
            String token = userService.generateResetToken(user);
            emailService.sendResetPasswordEmail(user.getEmail(), token);

            return ResponseEntity.ok(Map.of("message", "Mã xác nhận đã được gửi đến email của bạn."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-reset-token")
    public ResponseEntity<?> verifyResetToken(@RequestBody VerifyTokenRequest request) {
        try {
            Optional<User> userOpt = userService.findByEmail(request.getEmail());
            
            // Kiểm tra xem email có tồn tại và khớp với username không
            if (userOpt.isEmpty() || !userOpt.get().getUsername().equals(request.getUsername())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thông tin tài khoản không khớp."));
            }

            boolean isValid = userService.verifyResetToken(request.getEmail(), request.getToken());
            if (isValid) {
                return ResponseEntity.ok(Map.of("message", "Mã xác nhận hợp lệ."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Mã xác nhận không hợp lệ hoặc đã hết hạn."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            // Gọi hàm xử lý tập trung trong service
            boolean success = userService.processPasswordReset(request.getEmail(), request.getToken(), request.getNewPassword());

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Mật khẩu đã được đặt lại thành công."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Yêu cầu không hợp lệ. Vui lòng thử lại từ đầu."));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Principal principal, @RequestBody ChangePasswordRequest request) {
        try {
            String username = principal.getName();
            boolean success = userService.changePassword(username, request.getOldPassword(), request.getNewPassword());

            if (success) {
                return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công."));
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu cũ không chính xác."));
            }
        } catch (Exception e) {
            // Ghi log lỗi ở đây để debug
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Đã có lỗi xảy ra, vui lòng thử lại."));
        }
    }
}
