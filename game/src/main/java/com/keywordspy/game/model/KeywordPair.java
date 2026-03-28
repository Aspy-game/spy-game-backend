package com.keywordspy.game.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "keyword_pairs")
public class KeywordPair {
    @Id
    private String id;

    private String civilianKeyword;

    private String spyKeyword;
    
    // Mô tả dùng cho Vòng Đặc Biệt
    private String civilianDescription;
    private String spyDescription;

    private String category;
}