package com.qrattendance.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.group-rules")
public class GroupRulesConfig {

    private List<GroupDef> auditorne;
    private List<GroupDef> laboratorijske;

    @Data
    public static class GroupDef {
        private String name;
        private List<Range> ranges;
    }

    @Data
    public static class Range {
        private int from;
        private int to;
    }

    public String resolveGroup(int indexNum, List<GroupDef> rules) {
        if (rules == null) return "Ostali";
        int numLength = String.valueOf(indexNum).length();
        for (GroupDef group : rules) {
            for (Range range : group.getRanges()) {
                int fromLength = String.valueOf(range.getFrom()).length();
                if (fromLength == numLength
                        && indexNum >= range.getFrom()
                        && indexNum <= range.getTo()) {
                    return group.getName();
                }
            }
        }
        return "Ostali";
    }
}