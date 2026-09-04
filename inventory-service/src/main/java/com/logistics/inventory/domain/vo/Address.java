package com.logistics.inventory.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Postal address of a warehouse. One table, but a value object in the model. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Address {

    @Column(name = "address_line1", nullable = false, length = 180)
    private String line1;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 16)
    private String postalCode;

    /**
     * ISO-3166-1 alpha-2, as VARCHAR(2) rather than CHAR(2). CHAR pads with spaces, so a value
     * read back as "MA " would not equal the "MA" that was written - a comparison bug waiting to
     * happen, and one Hibernate flags anyway because it maps String to VARCHAR.
     */
    @Column(name = "country", nullable = false, length = 2)
    private String country;
}
