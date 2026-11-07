package com.banksim.audit;

public enum AuditEventType {
    REGISTER,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    TRANSFER_COMPLETED,
    TRANSFER_REJECTED,
    ACCOUNT_STATUS_CHANGED
}
