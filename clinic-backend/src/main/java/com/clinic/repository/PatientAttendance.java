package com.clinic.repository;

import java.util.UUID;

/** A patient's earlier visits, attended or missed, for no-show risk. */
public interface PatientAttendance {

    UUID getPatientId();

    long getVisits();

    long getNoShows();
}
