package com.sdms.prototype.service;

import com.sdms.prototype.domain.PolicySetting;
import com.sdms.prototype.domain.Repos.PolicyRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Configurable AICTE policy rules, editable at runtime without code changes. */
@Service
public class PolicyService {
    public static final String INTAKE_TOLERANCE = "intakeTolerancePct";
    public static final String SPIKE_THRESHOLD = "spikeThresholdPct";
    public static final String CONFIRMATION_MODE = "studentConfirmation";
    public static final String CONFIRMATION_DAYS = "confirmationDeadlineDays";

    private final PolicyRepo repo;
    private final int configuredYear;

    public PolicyService(PolicyRepo repo, @Value("${app.current-admission-year:0}") int configuredYear) {
        this.repo = repo;
        this.configuredYear = configuredYear;
    }

    public String get(String key, String def) {
        return repo.findById(key).map(p -> p.settingValue).orElse(def);
    }

    public void set(String key, String value) {
        repo.save(new PolicySetting(key, value));
    }

    public double intakeTolerancePct() {
        return Double.parseDouble(get(INTAKE_TOLERANCE, "0"));
    }

    public double spikeThresholdPct() {
        return Double.parseDouble(get(SPIKE_THRESHOLD, "30"));
    }

    public String confirmationMode() {
        return get(CONFIRMATION_MODE, "OPTIONAL");
    }

    public int confirmationDays() {
        return Integer.parseInt(get(CONFIRMATION_DAYS, "30"));
    }

    /** Admissions year runs from June: October 2026 belongs to the 2026 intake. */
    public int currentYear() {
        if (configuredYear > 0) return configuredYear;
        LocalDate d = LocalDate.now();
        return d.getMonthValue() >= 6 ? d.getYear() : d.getYear() - 1;
    }

    public Map<String, Object> all() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(INTAKE_TOLERANCE, intakeTolerancePct());
        m.put(SPIKE_THRESHOLD, spikeThresholdPct());
        m.put(CONFIRMATION_MODE, confirmationMode());
        m.put(CONFIRMATION_DAYS, confirmationDays());
        m.put("currentAdmissionYear", currentYear());
        return m;
    }
}
