package com.finpay.authservice.services;

import com.finpay.authservice.models.Location;
import com.finpay.authservice.models.Role;
import com.finpay.authservice.models.UserEntity;
import com.finpay.authservice.repositories.LocationRepository;
import com.finpay.authservice.repositories.RoleRepository;
import com.finpay.authservice.repositories.UserRepository;
import com.finpay.common.dto.users.CreateUserRequest;
import com.finpay.common.dto.users.UserDTO;
import com.finpay.common.dto.users.UserLocationDTO;
import com.finpay.common.enums.RoleEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service class responsible for user management operations.
 * Implements UserDetailsService for Spring Security authentication.
 */
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private RoleRepository roleRepository;

    /**
     * Loads user details by username for Spring Security authentication.
     *
     * @param username the username identifying the user
     * @return UserDetails object containing user authentication information
     * @throws UsernameNotFoundException if the user cannot be found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));;

        // Wrap the UserEntity in CustomUserDetails for Spring Security
        return new CustomUserDetails(userEntity);
    }

    /**
     * Retrieves all users with their location information.
     *
     * @return List of UserLocationDTO containing user ID, email, and location details
     */
    public List<UserLocationDTO> getAllUsersLocation() {
        return userRepository.findAll()
                .stream()
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converts a UserEntity to UserLocationDTO containing location-specific information.
     *
     * @param user the UserEntity to convert
     * @return UserLocationDTO with user ID, email, and location coordinates
     */
    private UserLocationDTO convertEntityToDTO(UserEntity user) {
        UserLocationDTO userLocationDTO = new UserLocationDTO();
        userLocationDTO.setUserId(user.getId());
        userLocationDTO.setEmail(user.getEmail());
        userLocationDTO.setPlace(user.getLocation().getPlace());
        userLocationDTO.setLongitude(user.getLocation().getLongitude());
        userLocationDTO.setLatitude(user.getLocation().getLatitude());
        return userLocationDTO;
    }

    /**
     * Retrieves all users with their complete information.
     *
     * @return List of UserDTO containing all user details including role and location
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Converts a UserEntity to UserDTO containing comprehensive user information.
     *
     * @param user the UserEntity to convert
     * @return UserDTO with complete user details including personal info, role, and location
     */
    private UserDTO convertToDTO(UserEntity user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setUsername(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setRole(RoleEnum.valueOf(user.getRole().getRoleName()));
        userDTO.setLocation(user.getLocation().getPlace());
        return userDTO;
    }

    /**
     * Creates a new user with the provided information.
     * This method:
     * 1. Creates and saves a location entity
     * 2. Retrieves the role from the database
     * 3. Encrypts the password using BCrypt
     * 4. Creates and saves the user entity
     *
     * @param request CreateUserRequest containing user registration details
     * @return The created and persisted UserEntity
     * @throws RuntimeException if the specified role is not found
     */
    public UserEntity createUser(CreateUserRequest request) {
        // Create and save location information
        Location location = new Location();
        location.setPlace(request.getLocation());
        location.setDescription("Awesome");
        location.setLongitude(40.5);
        location.setLatitude(38.9);
        locationRepository.save(location);

        // Retrieve the role from database
        Role role = roleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        // Initialize password encoder for secure password hashing
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // Create new user entity with provided details
        UserEntity userEntity = new UserEntity();
        userEntity.setFirstName(request.getFirstName());
        userEntity.setLastName(request.getLastName());
        userEntity.setEmail(request.getEmail());
        userEntity.setUsername(request.getUsername());
        userEntity.setPassword(passwordEncoder.encode(request.getPassword())); // Hash password
        userEntity.setRole(role);
        userEntity.setLocation(location);

        // Save and return the new user
        return userRepository.save(userEntity);
    }
}
