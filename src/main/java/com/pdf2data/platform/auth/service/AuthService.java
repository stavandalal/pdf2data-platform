package com.pdf2data.platform.auth.service;

import com.pdf2data.platform.auth.dto.AuthResponse;
import com.pdf2data.platform.auth.dto.LoginRequest;
import com.pdf2data.platform.auth.dto.RegistrationRequest;
import com.pdf2data.platform.auth.entity.User;
import com.pdf2data.platform.auth.repository.UserRepository;
import com.pdf2data.platform.auth.security.JwtUtil;
import com.pdf2data.platform.common.enums.UserRole;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    public String registerUser(RegistrationRequest registrationRequest) {
        if (userRepository.existsByUsername(registrationRequest.getUsername())) {
            throw new RuntimeException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(registrationRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already in use!");
        }

        final String MASTER_ADMIN_SECRET = "Super_Secret_Admin";
        UserRole assignedRole = UserRole.ROLE_USER;

        if (registrationRequest.getAdminSecretKey() != null && !registrationRequest.getAdminSecretKey().isEmpty()) {
            if (MASTER_ADMIN_SECRET.equals(registrationRequest.getAdminSecretKey())) {
                assignedRole = UserRole.ROLE_ADMIN;
            } else {
                throw new RuntimeException("Error: Invalid Admin Secret Key! Registration denied.");
            }
        }

        User user = User.builder()
                .username(registrationRequest.getUsername())
                .email(registrationRequest.getEmail())
                .password(passwordEncoder.encode(registrationRequest.getPassword()))
                .role(assignedRole)
                .build();

        userRepository.save(user);
        return "User registered successfully with role: " + assignedRole.name();
    }

    public AuthResponse authenticateUser(LoginRequest loginRequest) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        final String jwt = jwtUtil.generateToken(userDetails);
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User context tracking error."));


        return new AuthResponse(jwt, user.getUsername(), user.getRole().name());
    }

}