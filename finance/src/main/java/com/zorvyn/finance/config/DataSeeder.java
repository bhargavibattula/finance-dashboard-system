package com.zorvyn.finance.config;

import com.zorvyn.finance.entity.User;
import com.zorvyn.finance.entity.enums.Role;
import com.zorvyn.finance.entity.enums.UserStatus;
import com.zorvyn.finance.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initAdmin(UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.count() == 0) {
                User admin = new User("Admin User", "admin@test.com", encoder.encode("Password@123"));
                admin.setRole(Role.ADMIN);
                admin.setStatus(UserStatus.ACTIVE);
                repo.save(admin);
                System.out.println(">>> Admin seeded: admin@test.com / Password@123");
            }
        };
    }
}