package com.example.tp_backend.service;

import com.example.tp_backend.dao.UserRepository;
import com.example.tp_backend.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;

import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;


@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private final JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    public void registerUser(String email, String password) throws MessagingException {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }
        String hashedPassword = passwordEncoder.encode(password);
        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setConfirmationToken(UUID.randomUUID().toString());

        userRepository.save(user);

        sendConfirmationEmail(user);
    }

    private void sendConfirmationEmail(User user) throws MessagingException {
        String link = "http://localhost:8082/api/auth/confirm?token=" + user.getConfirmationToken();
        String htmlContent = "<html>" +
                "<body>" +
                "<h2>Bienvenue sur notre site</h2>" +
                "<p>Pour confirmer votre inscription, cliquez sur le lien ci-dessous :</p>" +
                "<p><a href='" + link + "' style='color: #f29c11;'>Confirmer mon compte</a></p>" +
                "<p>Si vous n'avez pas demandé cette inscription, ignorez cet email.</p>" +
                "</body>" +
                "</html>";
        MimeMessage message = mailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setTo(user.getEmail());
        helper.setSubject("Confirmez votre inscription");
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    public void confirmUser(String token) {
        User user = userRepository.findByConfirmationToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        user.setEnabled(true);
        user.setConfirmationToken(null);
        userRepository.save(user);
    }


    public User loginUser(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Vérifie si le compte est activé
            if (!user.isEnabled()) {
                throw new RuntimeException("Compte non activé.");
            }

            // Vérifie si le mot de passe correspond
            if (passwordEncoder.matches(password, user.getPassword())) {
                return user; // Auth OK
            } else {
                throw new RuntimeException("Mot de passe incorrect.");
            }
        } else {
            throw new RuntimeException("Utilisateur non trouvé.");
        }
    }


    public void sendPasswordResetEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) throw new RuntimeException("Utilisateur non trouvé");

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        userRepository.save(user);

        String resetLink = "http://localhost:4200/reset-password?token=" + token;
        String html = "<p>Pour réinitialiser votre mot de passe, cliquez ici : "
                + "<a href='" + resetLink + "'>Réinitialiser</a></p>";

        sendEmail(user.getEmail(), "Réinitialisation de mot de passe", html);
    }

    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("noreply@elisee.com");

            mailSender.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.save(user);
    }


}
