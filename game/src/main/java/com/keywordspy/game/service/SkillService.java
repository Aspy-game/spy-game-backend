package com.keywordspy.game.service;

import com.keywordspy.game.model.User;
import com.keywordspy.game.model.Transaction;
import com.keywordspy.game.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SkillService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EconomyService economyService;

    // Skill IDs
    public static final String SKILL_ANONYMOUS_VOTE = "ANONYMOUS_VOTE";
    public static final String SKILL_SPECIAL_ROUND = "SPECIAL_ROUND";

    // Skill Prices
    public static final int PRICE_ANONYMOUS_VOTE = 200;
    public static final int PRICE_SPECIAL_ROUND = 500;

    @Transactional
    public void buySkill(String userId, String skillId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int price = getSkillPrice(skillId);
        if (user.getBalance() < price) {
            throw new RuntimeException("Insufficient balance");
        }

        // Deduct balance
        economyService.deductBalance(userId, price, Transaction.TransactionType.valueOf("BUY_SKILL"), "Mua kỹ năng: " + skillId);

        // Add to inventory
        Map<String, Integer> inventory = user.getInventory();
        inventory.put(skillId, inventory.getOrDefault(skillId, 0) + 1);
        userRepository.save(user);
    }

    private int getSkillPrice(String skillId) {
        return switch (skillId) {
            case SKILL_ANONYMOUS_VOTE -> PRICE_ANONYMOUS_VOTE;
            case SKILL_SPECIAL_ROUND -> PRICE_SPECIAL_ROUND;
            default -> throw new RuntimeException("Invalid skill ID");
        };
    }
    
    public Map<String, Integer> getInventory(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getInventory();
    }

    @Transactional
    public void useSkill(String userId, String skillId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Integer> inventory = user.getInventory();
        int quantity = inventory.getOrDefault(skillId, 0);
        if (quantity <= 0) {
            throw new RuntimeException("Skill not available in inventory");
        }

        // Consume skill
        inventory.put(skillId, quantity - 1);
        userRepository.save(user);
    }
}
