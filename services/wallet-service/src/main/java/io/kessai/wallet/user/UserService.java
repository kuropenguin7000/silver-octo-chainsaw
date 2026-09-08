package io.kessai.wallet.user;

import io.kessai.wallet.shared.error.DomainException;
import io.kessai.wallet.shared.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User register(String displayName, String email) {
        String normalizedEmail = User.normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailTaken(normalizedEmail);
        }

        User user = User.register(displayName.trim(), normalizedEmail);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw emailTaken(normalizedEmail);
        }
    }

    private DomainException emailTaken(String email) {
        return new DomainException(
                ErrorCode.USER_EMAIL_TAKEN, "Email " + email + " is already registered");
    }
}
