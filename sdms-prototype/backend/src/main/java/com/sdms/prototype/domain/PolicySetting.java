package com.sdms.prototype.domain;

import jakarta.persistence.*;

@Entity
public class PolicySetting {
    @Id
    public String settingKey;
    public String settingValue;

    public PolicySetting() {}

    public PolicySetting(String k, String v) {
        this.settingKey = k;
        this.settingValue = v;
    }
}
