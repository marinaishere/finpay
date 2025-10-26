package com.finpay.authservice.dataseeders;

import com.finpay.authservice.models.Role;
import com.finpay.authservice.repositories.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for seeding initial role data into the database.
 * Ensures that default roles (USER and ADMIN) exist when the application starts.
 */
@Configuration
public class RoleSeeder {
    /**
     * Creates a CommandLineRunner that seeds the database with default roles.
     * Runs automatically at application startup and creates roles if they don't exist.
     *
     * @param roleRepository Repository for accessing role data
     * @return CommandLineRunner that executes the seeding logic
     */
    @Bean
    public CommandLineRunner seedRoles(RoleRepository roleRepository) {
        return args -> {
            // Create USER role if it doesn't exist
            if(roleRepository.findByRoleName("USER").isEmpty()){
                roleRepository.save(new Role(null, "USER"));
            }
            // Create ADMIN role if it doesn't exist
            if(roleRepository.findByRoleName("ADMIN").isEmpty()){
                roleRepository.save(new Role(null, "ADMIN"));
            }
        };
    }
}
