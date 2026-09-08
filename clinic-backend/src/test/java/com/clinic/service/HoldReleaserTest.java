package com.clinic.service;

import com.clinic.entity.Appointment;
import com.clinic.entity.AppointmentStatus;
import com.clinic.entity.Slot;
import com.clinic.entity.SlotStatus;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.SlotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Returning abandoned slots to the market (API contract 12, v1.4).
 *
 * <p>Booking holds a slot rather than selling it. Without this sweep, a patient
 * who closed the tab mid-payment would keep a slot off the market forever.
 */
class HoldReleaserTest {

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final SlotRepository slotRepository = mock(SlotRepository.class);

    private final HoldReleaser service = new HoldReleaser(appointmentRepository, slotRepository);

    /** The sweep that finds expired holds and hands each to the releaser. */
    private final HoldExpiryService sweep = new HoldExpiryService(appointmentRepository, service);

    private Slot slot(LocalDate date, LocalTime start, SlotStatus status) {
        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setDate(date);
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(30));
        slot.setStatus(status);
        return slot;
    }

    private Appointment appointment(Slot slot, AppointmentStatus status, OffsetDateTime holdExpiry) {
        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setSlot(slot);
        appointment.setStatus(status);
        appointment.setHoldExpiresAt(holdExpiry);
        return appointment;
    }

    private void stubLookup(Appointment appointment, Slot slot) {
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));
        when(slotRepository.findByIdForUpdate(slot.getId())).thenReturn(Optional.of(slot));
    }

    @Test
    void anExpiredHoldFreesTheSlotAndCancelsTheAppointment() {
        Slot slot = slot(LocalDate.now().plusDays(3), LocalTime.of(10, 0), SlotStatus.HELD);
        Appointment appointment = appointment(slot, AppointmentStatus.PENDING_PAYMENT,
                OffsetDateTime.now().minusMinutes(1));
        stubLookup(appointment, slot);

        assertThat(service.release(appointment.getId())).isTrue();

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancellationReason()).contains("not completed in time");
        assertThat(appointment.getHoldExpiresAt()).isNull();
    }

    @Test
    void aSlotWhoseTimeHasPassedExpiresRatherThanReturningToSale() {
        // Nobody can book 9am at 10am, so putting it back on the market would
        // only produce an unbookable slot patients can see.
        Slot slot = slot(LocalDate.now().minusDays(1), LocalTime.of(10, 0), SlotStatus.HELD);
        Appointment appointment = appointment(slot, AppointmentStatus.PENDING_PAYMENT,
                OffsetDateTime.now().minusMinutes(1));
        stubLookup(appointment, slot);

        service.release(appointment.getId());

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.EXPIRED);
    }

    @Test
    void aHoldThatHasNotRunOutIsLeftAlone() {
        Slot slot = slot(LocalDate.now().plusDays(3), LocalTime.of(10, 0), SlotStatus.HELD);
        Appointment appointment = appointment(slot, AppointmentStatus.PENDING_PAYMENT,
                OffsetDateTime.now().plusMinutes(4));
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        assertThat(service.release(appointment.getId())).isFalse();

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.HELD);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING_PAYMENT);
    }

    @Test
    void anAppointmentPaidBetweenTheQueryAndTheLockIsNotCancelled() {
        // The race that matters: the sweep reads a list, the patient pays, and
        // the sweep then reaches that appointment. Cancelling a paid
        // appointment would be the worst possible outcome of this feature.
        Slot slot = slot(LocalDate.now().plusDays(3), LocalTime.of(10, 0), SlotStatus.BOOKED);
        Appointment appointment = appointment(slot, AppointmentStatus.CONFIRMED, null);
        when(appointmentRepository.findWithDetailsById(appointment.getId()))
                .thenReturn(Optional.of(appointment));

        assertThat(service.release(appointment.getId())).isFalse();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        verify(slotRepository, never()).save(any());
    }

    @Test
    void aSlotSomeoneElseAlreadyOwnsIsNotStolenBack() {
        // If the slot is no longer HELD, something else owns it now. The
        // appointment is still cancelled, but the slot is left as it is.
        Slot slot = slot(LocalDate.now().plusDays(3), LocalTime.of(10, 0), SlotStatus.BOOKED);
        Appointment appointment = appointment(slot, AppointmentStatus.PENDING_PAYMENT,
                OffsetDateTime.now().minusMinutes(1));
        stubLookup(appointment, slot);

        service.release(appointment.getId());

        assertThat(slot.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void theSweepCarriesOnAfterOneAppointmentFails() {
        Slot good = slot(LocalDate.now().plusDays(3), LocalTime.of(11, 0), SlotStatus.HELD);
        Appointment broken = appointment(slot(LocalDate.now().plusDays(3), LocalTime.of(10, 0),
                SlotStatus.HELD), AppointmentStatus.PENDING_PAYMENT, OffsetDateTime.now().minusMinutes(1));
        Appointment healthy = appointment(good, AppointmentStatus.PENDING_PAYMENT,
                OffsetDateTime.now().minusMinutes(1));

        when(appointmentRepository.findByStatusAndHoldExpiresAtBefore(any(), any()))
                .thenReturn(List.of(broken, healthy));
        when(appointmentRepository.findWithDetailsById(broken.getId()))
                .thenThrow(new RuntimeException("database hiccup"));
        when(appointmentRepository.findWithDetailsById(healthy.getId()))
                .thenReturn(Optional.of(healthy));
        when(slotRepository.findByIdForUpdate(good.getId())).thenReturn(Optional.of(good));

        sweep.releaseExpiredHolds();

        assertThat(good.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(healthy.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void anEmptySweepTouchesNothing() {
        when(appointmentRepository.findByStatusAndHoldExpiresAtBefore(any(), any()))
                .thenReturn(List.of());

        sweep.releaseExpiredHolds();

        Mockito.verifyNoInteractions(slotRepository);
    }
}
