package com.sdms.prototype.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJob {
    private final CaseService cases;

    public MaintenanceJob(CaseService cases) {
        this.cases = cases;
    }

    /** Hourly: confirmations older than the policy deadline become UNCONFIRMED; stale access requests expire. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 3_600_000)
    public void run() {
        cases.maintenance();
    }
}
