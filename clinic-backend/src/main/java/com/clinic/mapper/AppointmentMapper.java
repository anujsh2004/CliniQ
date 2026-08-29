package com.clinic.mapper;

import com.clinic.dto.response.AppointmentCreatedResponse;
import com.clinic.dto.response.AppointmentDetailResponse;
import com.clinic.dto.response.AppointmentListItem;
import com.clinic.dto.response.AppointmentRescheduledResponse;
import com.clinic.dto.response.DoctorDayAppointment;
import com.clinic.dto.response.DoctorParty;
import com.clinic.dto.response.PatientParty;
import com.clinic.entity.Appointment;
import com.clinic.entity.Slot;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO mapping for appointments. Each of the contract's four
 * appointment payload shapes is built here so controllers never improvise one.
 */
@Component
public class AppointmentMapper {

    public AppointmentCreatedResponse toCreated(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return new AppointmentCreatedResponse(
                appointment.getId().toString(),
                appointment.getDoctor().getId().toString(),
                appointment.getPatient().getId().toString(),
                slot.getId().toString(),
                slot.getDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                appointment.getStatus(),
                appointment.getPaymentStatus());
    }

    public AppointmentDetailResponse toDetail(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return new AppointmentDetailResponse(
                appointment.getId().toString(),
                new DoctorParty(appointment.getDoctor().getId().toString(), appointment.getDoctor().getName()),
                PatientParty.withoutPhone(appointment.getPatient().getId().toString(),
                        appointment.getPatient().getUser().getName()),
                slot.getDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                appointment.getStatus(),
                appointment.getPaymentStatus());
    }

    public AppointmentListItem toListItem(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return new AppointmentListItem(
                appointment.getId().toString(),
                appointment.getDoctor().getName(),
                slot.getDate(),
                slot.getStartTime(),
                appointment.getStatus(),
                appointment.getPaymentStatus());
    }

    /** The doctor's own daily list carries the patient's phone (contract 13). */
    public DoctorDayAppointment toDayAppointment(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return new DoctorDayAppointment(
                appointment.getId().toString(),
                new PatientParty(appointment.getPatient().getId().toString(),
                        appointment.getPatient().getUser().getName(),
                        appointment.getPatient().getUser().getPhone()),
                slot.getStartTime(),
                slot.getEndTime(),
                appointment.getStatus());
    }

    public AppointmentRescheduledResponse toRescheduled(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return new AppointmentRescheduledResponse(
                appointment.getId().toString(),
                slot.getDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                appointment.getStatus());
    }
}
