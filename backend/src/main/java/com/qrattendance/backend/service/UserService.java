package com.qrattendance.backend.service;

import com.qrattendance.backend.model.User;
import com.qrattendance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getOrCreateUser(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        // uloga na osnovu keycloak realm_access.roles
        User.UserRole role = determineRole(jwt);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);

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
        return User.UserRole.STUDENT;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}