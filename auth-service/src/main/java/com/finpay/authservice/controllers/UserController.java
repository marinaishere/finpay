package com.finpay.authservice.controllers;

import java.net.URI;
import java.util.List;

import com.finpay.authservice.models.UserEntity;
import com.finpay.authservice.services.UserService;
import com.finpay.common.dto.users.CreateUserRequest;
import com.finpay.common.dto.users.UserDTO;
import com.finpay.common.dto.users.UserLocationDTO;
import com.finpay.common.enums.RoleEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for user management operations.
 * Handles user registration, retrieval, and location queries.
 */
@RestController
@RequestMapping("/auth-services")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * Retrieves all users with their location information.
     * Returns simplified user data including ID, email, and geographic coordinates.
     *
     * @return List of UserLocationDTO containing user location details
     */
    @GetMapping("/users-location")
    public List<UserLocationDTO> getAlLUsersLocation() {
        return userService.getAllUsersLocation();
    }

    /**
     * Retrieves all users with complete information.
     * Returns full user details including personal info, role, and location.
     *
     * @return List of UserDTO containing complete user details
     */
    @GetMapping("/users")
    public List<UserDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    /**
     * Creates a new user account.
     * Registers a new user with the provided details and returns the created user.
     * Returns HTTP 201 Created with the Location header pointing to the new resource.
     *
     * @param request CreateUserRequest containing user registration details
     * @return ResponseEntity with UserDTO and Location header
     */
    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        UserEntity user = userService.createUser(request);

        // Build URI for the newly created user resource
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(user.getId())
                .toUri();

        // Convert UserEntity to UserDTO for response
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setUsername(user.getUsername());
        userDTO.setEmail(user.getEmail());
        userDTO.setRole(RoleEnum.valueOf(user.getRole().getRoleName()));
        userDTO.setLocation(user.getLocation().getPlace());

        return ResponseEntity.created(location).body(userDTO);
    }
}
