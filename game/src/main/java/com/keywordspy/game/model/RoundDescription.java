package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Document(collection = "round_descriptions")
public class RoundDescription {
    @Id
    private String id;

    private String roundId;

    private String userId;

    private String content;

    private boolean isFake = false;

    private LocalDateTime submittedAt = LocalDateTime.now();
}
