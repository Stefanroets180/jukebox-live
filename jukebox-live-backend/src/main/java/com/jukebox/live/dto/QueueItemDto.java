package com.jukebox.live.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueItemDto {
    private String id;
    private TrackDto track;
    private RequesterDto requestedBy;
    private String status;
    private Integer position;
    private String createdAt;
}
