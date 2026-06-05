package com.temporalkg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityDetailDTO {
    private Long id;
    private String name;
    private String type;
    private Map<String, Object> attributes;
    private Integer tripleCount;
    private String earliestEventTime;
    private String latestEventTime;
    private List<EventItem> recentEvents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventItem {
        private String relation;
        private String otherEntityName;
        private String time;
    }
}
