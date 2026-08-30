package com.clinic.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Patient profile (API contract 18).
 *
 * <p>Deliberately thin: the contract's patient payload is name, email and
 * phone, all of which live on {@link User}. This table exists so that
 * patient-specific data (date of birth, medical history) has somewhere to go
 * later without touching the identity table, and so appointments have a stable
 * patient id to reference.
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
