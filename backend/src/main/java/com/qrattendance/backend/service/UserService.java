package com.qrattendance.backend.service;

import com.qrattendance.backend.model.User;
import com.qrattendance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.employee-email-domain}")
    private String employeeEmailDomain;

    public User getOrCreateUser(Jwt jwt) {
        String email = jwt.getClaimAsString("email");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            return newUser;
        });

        user.setFirstName(jwt.getClaimAsString("given_name"));
        user.setLastName(jwt.getClaimAsString("family_name"));
        user.setRole(determineRole(jwt));

        return userRepository.save(user);
    }

    private User.UserRole determineRole(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            var roles = (java.util.List<?>) realmAccess.get("roles");
            if (roles != null && roles.contains("ZAPOSLENI")) {
                return User.UserRole.ZAPOSLENI;
            }
        }
        String email = jwt.getClaimAsString("email");
        if (email != null && email.endsWith("@" + employeeEmailDomain)) {
            return User.UserRole.ZAPOSLENI;
        }
        return User.UserRole.STUDENT;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}