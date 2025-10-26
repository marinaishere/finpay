package com.finpay.authservice.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class representing a geographic location.
 * Stores location information including place name, description, and coordinates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "locations")
public class Location {
    /**
     * Unique identifier for the location.
     * Auto-generated using database identity strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /**
     * Name or address of the location.
     */
    private String place;

    /**
     * Description or additional details about the location.
     */
    private String description;

    /**
     * Longitude coordinate of the location.
     * Represents the east-west position on Earth's surface.
     */
    private double longitude;

    /**
     * Latitude coordinate of the location.
     * Represents the north-south position on Earth's surface.
     */
    private double latitude;
}

