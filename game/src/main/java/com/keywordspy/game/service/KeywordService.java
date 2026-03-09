package com.keywordspy.game.service;

import com.keywordspy.game.model.KeywordPair;
import com.keywordspy.game.repository.KeywordPairRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class KeywordService {

    @Autowired
    private KeywordPairRepository keywordPairRepository;

    // Seed 50+ keyword pairs khi khởi động nếu DB trống
    @PostConstruct
    public void seedKeywords() {
        if (keywordPairRepository.count() > 0) return;

        List<KeywordPair> pairs = new ArrayList<>();

        // Địa điểm
        pairs.add(pair("Bãi biển", "Hồ bơi", "địa điểm"));
        pairs.add(pair("Siêu thị", "Chợ", "địa điểm"));
        pairs.add(pair("Bệnh viện", "Phòng khám", "địa điểm"));
        pairs.add(pair("Trường học", "Trung tâm học", "địa điểm"));
        pairs.add(pair("Sân bay", "Bến tàu", "địa điểm"));
        pairs.add(pair("Rạp chiếu phim", "Nhà hát", "địa điểm"));
        pairs.add(pair("Công viên", "Vườn thực vật", "địa điểm"));
        pairs.add(pair("Khách sạn", "Nhà nghỉ", "địa điểm"));
        pairs.add(pair("Nhà hàng", "Quán ăn", "địa điểm"));
        pairs.add(pair("Thư viện", "Nhà sách", "địa điểm"));

        // Đồ vật
        pairs.add(pair("Điện thoại", "Máy tính bảng", "đồ vật"));
        pairs.add(pair("Xe đạp", "Xe máy", "đồ vật"));
        pairs.add(pair("Bàn phím", "Chuột máy tính", "đồ vật"));
        pairs.add(pair("Tivi", "Màn hình máy tính", "đồ vật"));
        pairs.add(pair("Máy giặt", "Máy sấy", "đồ vật"));
        pairs.add(pair("Nồi cơm điện", "Lò vi sóng", "đồ vật"));
        pairs.add(pair("Bàn chải đánh răng", "Bàn chải tóc", "đồ vật"));
        pairs.add(pair("Ví tiền", "Túi xách", "đồ vật"));
        pairs.add(pair("Kính mắt", "Kính áp tròng", "đồ vật"));
        pairs.add(pair("Máy ảnh", "Điện thoại chụp ảnh", "đồ vật"));

        // Động vật
        pairs.add(pair("Chó", "Mèo", "động vật"));
        pairs.add(pair("Sư tử", "Hổ", "động vật"));
        pairs.add(pair("Cá heo", "Cá mập", "động vật"));
        pairs.add(pair("Đại bàng", "Diều hâu", "động vật"));
        pairs.add(pair("Voi", "Tê giác", "động vật"));
        pairs.add(pair("Thỏ", "Sóc", "động vật"));
        pairs.add(pair("Gà", "Vịt", "động vật"));
        pairs.add(pair("Rắn", "Thằn lằn", "động vật"));
        pairs.add(pair("Cua", "Tôm", "động vật"));
        pairs.add(pair("Bướm", "Ong", "động vật"));

        // Đồ ăn
        pairs.add(pair("Phở", "Bún bò", "đồ ăn"));
        pairs.add(pair("Cơm tấm", "Cơm chiên", "đồ ăn"));
        pairs.add(pair("Bánh mì", "Bánh bao", "đồ ăn"));
        pairs.add(pair("Pizza", "Hamburger", "đồ ăn"));
        pairs.add(pair("Sushi", "Ramen", "đồ ăn"));
        pairs.add(pair("Kem", "Chè", "đồ ăn"));
        pairs.add(pair("Cà phê", "Trà sữa", "đồ ăn"));
        pairs.add(pair("Nước cam", "Nước chanh", "đồ ăn"));
        pairs.add(pair("Xoài", "Dứa", "đồ ăn"));
        pairs.add(pair("Chocolate", "Kẹo cao su", "đồ ăn"));

        // Nghề nghiệp
        pairs.add(pair("Bác sĩ", "Y tá", "nghề nghiệp"));
        pairs.add(pair("Giáo viên", "Giảng viên", "nghề nghiệp"));
        pairs.add(pair("Cảnh sát", "Bảo vệ", "nghề nghiệp"));
        pairs.add(pair("Đầu bếp", "Phụ bếp", "nghề nghiệp"));
        pairs.add(pair("Lập trình viên", "Kỹ sư phần mềm", "nghề nghiệp"));
        pairs.add(pair("Kiến trúc sư", "Kỹ sư xây dựng", "nghề nghiệp"));
        pairs.add(pair("Ca sĩ", "Nhạc sĩ", "nghề nghiệp"));
        pairs.add(pair("Diễn viên", "Đạo diễn", "nghề nghiệp"));
        pairs.add(pair("Phi công", "Tiếp viên hàng không", "nghề nghiệp"));
        pairs.add(pair("Nông dân", "Ngư dân", "nghề nghiệp"));

        // Cảm xúc
        pairs.add(pair("Vui vẻ", "Phấn khích", "cảm xúc"));
        pairs.add(pair("Buồn", "Thất vọng", "cảm xúc"));
        pairs.add(pair("Tức giận", "Bực bội", "cảm xúc"));
        pairs.add(pair("Sợ hãi", "Lo lắng", "cảm xúc"));
        pairs.add(pair("Ngạc nhiên", "Bất ngờ", "cảm xúc"));

        keywordPairRepository.saveAll(pairs);
    }

    private KeywordPair pair(String civilian, String spy, String category) {
        KeywordPair p = new KeywordPair();
        p.setCivilianKeyword(civilian);
        p.setSpyKeyword(spy);
        p.setCategory(category);
        return p;
    }

    // Random 1 keyword pair
    public KeywordPair getRandomKeyword() {
        List<KeywordPair> all = keywordPairRepository.findAll();
        if (all.isEmpty()) throw new RuntimeException("No keywords available");
        Collections.shuffle(all);
        return all.get(0);
    }

    // Random keyword, tránh trùng các id đã dùng gần đây
    public KeywordPair getRandomKeywordExcluding(List<String> excludeIds) {
        List<KeywordPair> all = keywordPairRepository.findAll();
        List<KeywordPair> available = all.stream()
                .filter(k -> !excludeIds.contains(k.getId()))
                .toList();

        if (available.isEmpty()) {
            // Nếu hết keyword thì random từ toàn bộ
            return getRandomKeyword();
        }

        Collections.shuffle(available);
        return available.get(0);
    }

    public List<KeywordPair> getAllKeywords() {
        return keywordPairRepository.findAll();
    }
}