package com.keywordspy.game.controller;

import com.keywordspy.game.model.KeywordPair;
import com.keywordspy.game.service.KeywordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/keywords")
public class KeywordController {

    @Autowired
    private KeywordService keywordService;

    // Lấy toàn bộ keyword pairs
    @GetMapping
    public ResponseEntity<List<KeywordPair>> getAllKeywords() {
        return ResponseEntity.ok(keywordService.getAllKeywords());
    }

    // Random 1 keyword pair
    @GetMapping("/random")
    public ResponseEntity<KeywordPair> getRandomKeyword() {
        return ResponseEntity.ok(keywordService.getRandomKeyword());
    }
}