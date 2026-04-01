package com.securespring;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Optional;

@RestController
public class AuthController {
    
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    
    public AuthController(UserRepository userRepository, 
                         BCryptPasswordEncoder passwordEncoder,
                         AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }
    
    @PostMapping("/api/register")
    public String register(@RequestBody RegisterRequest request) {
        try {
            // Validar entrada
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return "{\"error\": \"Username cannot be empty\"}";
            }
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return "{\"error\": \"Password cannot be empty\"}";
            }
            
            // Verificar si el usuario ya existe
            Optional<User> existing = userRepository.findByUsername(request.getUsername().trim());
            if (existing.isPresent()) {
                return "{\"error\": \"User already exists\"}";
            }
            
            // Crear nuevo usuario
            User user = new User();
            user.setUsername(request.getUsername().trim());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            
            // Guardar en base de datos
            userRepository.save(user);
            
            return "{\"message\": \"User registered successfully\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Registration failed: " + e.getMessage() + "\"}";
        }
    }
    
    @PostMapping("/api/login")
    public String login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return "{\"error\": \"Username cannot be empty\"}";
            }
            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return "{\"error\": \"Password cannot be empty\"}";
            }
            
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getUsername().trim(),
                    request.getPassword()
                )
            );
            
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            
            return "{\"message\": \"Login successful\"}";
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Invalid username or password\"}";
        }
    }
    
    @GetMapping("/api/user")
    public String getCurrentUser(Principal principal) {
        if (principal == null) {
            return "{\"error\": \"Not authenticated\"}";
        }
        return "{\"username\": \"" + principal.getName() + "\"}";
    }
    
    @PostMapping("/api/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession().invalidate();
        }
        return "{\"message\": \"Logged out successfully\"}";
    }
    
    public static class LoginRequest {
        private String username;
        private String password;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class RegisterRequest {
        private String username;
        private String password;
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
