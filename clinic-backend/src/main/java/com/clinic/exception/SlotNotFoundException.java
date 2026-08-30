package com.clinic.exception;

/**
 * Slot id is invalid, expired or otherwise not bookable (API contract 7a).
 */
public class SlotNotFoundException extends ApiException {

    public SlotNotFoundException() {
        super(ErrorCode.SLOT_NOT_FOUND);
    }

    public SlotNotFoundException(String message) {
        super(ErrorCode.SLOT_NOT_FOUND, message);
    }
}
