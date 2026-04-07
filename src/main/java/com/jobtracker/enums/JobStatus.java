package com.jobtracker.enums;

public enum JobStatus {
    UNDETERMINED(true),
    NOT_A_FIT(true),
    APPLIED(false),
    SCREENING(false),
    INTERVIEWING(false),
    OFFER_RECEIVED(false),
    OFFER_ACCEPTED(false),
    OFFER_DECLINED(false),
    REJECTED(false),
    WITHDRAWN(false),
    GHOSTED(false);

    private final boolean preApplication;

    JobStatus(boolean preApplication) {
        this.preApplication = preApplication;
    }

    public boolean isPreApplication() {
        return preApplication;
    }
}
