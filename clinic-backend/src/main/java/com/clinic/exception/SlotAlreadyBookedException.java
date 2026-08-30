package com.clinic.exception;

/**
 * The slot was taken between the patient fetching the slot list and submitting
 * the booking (API contract 7a). Thrown by the transactional booking code and
 * mapped to SLOT_ALREADY_BOOKED by the global handler - the integration
 * boundary the contract describes.
 */
public class SlotAlreadyBookedException extends ApiException {

    public SlotAlreadyBookedException() {
        super(ErrorCode.SLOT_ALREADY_BOOKED);
    }
}
