package com.isd.wms.entity;

import com.isd.wms.enums.Zone;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

import java.util.Objects;

/**
 * Represents a physical location (shelf, bin, or area) in the warehouse.
 * <p>
 * Each location has a unique name and barcode, belongs to a specific {@link Zone}
 * (REPLENISHMENT, PICKING, DISPATCH), and can be marked as available or active.
 * Locations are used to store stock and serve as destinations for orders and
 * replenishments.
 * </p>
 *
 * @see Zone
 * @see Stock
 * @see Order
 * @see Replenishment
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class Location{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE , generator = "location_seq")
    @SequenceGenerator(name = "location_seq", sequenceName = "locations_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone")
    private Zone zone;

    @Column
    private String description;

    @Column(nullable = false)
    private Boolean available = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public Location(String name, String barcode, Zone zone, String description) {
        this.name = name;
        this.barcode = barcode;
        this.zone = zone;
        this.description = description;
    }

    public Location(String name, String barcode, Zone zone, String description, Boolean available) {
        this.name = name;
        this.barcode = barcode;
        this.zone = zone;
        this.description = description;
        this.available = available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Location location = (Location) o;
        return id != null && Objects.equals(id, location.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
