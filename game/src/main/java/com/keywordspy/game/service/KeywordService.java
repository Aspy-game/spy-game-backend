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
        pairs.add(pair("Bãi biển", "Hồ bơi", "Nơi có nhiều cát, sóng biển và ánh nắng mặt trời.", "Nơi có làn nước trong xanh, thường nằm trong khuôn viên nhà hoặc khách sạn.", "địa điểm"));
        pairs.add(pair("Siêu thị", "Chợ", "Nơi mua sắm hiện đại với xe đẩy và máy tính tiền.", "Nơi mua sắm truyền thống, thường họp vào buổi sáng với tiếng trả giá.", "địa điểm"));
        pairs.add(pair("Bệnh viện", "Phòng khám", "Cơ sở y tế lớn với nhiều khoa và giường bệnh.", "Cơ sở y tế quy mô nhỏ, thường do bác sĩ tư nhân quản lý.", "địa điểm"));
        pairs.add(pair("Trường học", "Trung tâm học", "Nơi học sinh đến học tập chính quy mỗi ngày.", "Nơi học thêm các kỹ năng hoặc kiến thức bổ trợ ngoài giờ.", "địa điểm"));
        pairs.add(pair("Sân bay", "Bến tàu", "Nơi những con chim sắt khổng lồ cất cánh và hạ cánh.", "Nơi những con tàu cập bến để đón trả khách trên mặt nước.", "địa điểm"));
        pairs.add(pair("Rạp chiếu phim", "Nhà hát", "Nơi thưởng thức những bộ phim bom tấn trên màn hình lớn.", "Nơi diễn ra các buổi biểu diễn nghệ thuật trực tiếp trên sân khấu.", "địa điểm"));
        pairs.add(pair("Công viên", "Vườn thực vật", "Không gian xanh công cộng cho mọi người vui chơi, tập thể dục.", "Nơi bảo tồn và trưng bày nhiều loài cây quý hiếm.", "địa điểm"));
        pairs.add(pair("Khách sạn", "Nhà nghỉ", "Nơi lưu trú cao cấp với đầy đủ dịch vụ tiện nghi.", "Nơi dừng chân nghỉ ngơi đơn giản với giá cả bình dân.", "địa điểm"));
        pairs.add(pair("Nhà hàng", "Quán ăn", "Không gian ẩm thực sang trọng với thực đơn phong phú.", "Địa điểm ăn uống gần gũi, phục vụ các món ăn đơn giản.", "địa điểm"));
        pairs.add(pair("Thư viện", "Nhà sách", "Nơi mượn sách và học tập trong không gian yên tĩnh.", "Nơi trưng bày và bán các loại sách mới cho mọi người.", "địa điểm"));

        // Đồ vật
        pairs.add(pair("Điện thoại", "Máy tính bảng", "Thiết bị liên lạc nhỏ gọn luôn mang theo bên mình.", "Thiết bị điện tử màn hình lớn, hỗ trợ làm việc và giải trí.", "đồ vật"));
        pairs.add(pair("Xe đạp", "Xe máy", "Phương tiện di chuyển hai bánh chạy bằng sức người.", "Phương tiện di chuyển hai bánh chạy bằng động cơ xăng hoặc điện.", "đồ vật"));
        pairs.add(pair("Bàn phím", "Chuột máy tính", "Dụng cụ dùng để nhập dữ liệu bằng các phím bấm.", "Dụng cụ dùng để điều khiển con trỏ trên màn hình máy tính.", "đồ vật"));
        pairs.add(pair("Tivi", "Màn hình máy tính", "Thiết bị giải trí gia đình dùng để xem các chương trình truyền hình.", "Thiết bị hiển thị hình ảnh từ bộ xử lý trung tâm.", "đồ vật"));
        pairs.add(pair("Máy giặt", "Máy sấy", "Thiết bị giúp làm sạch quần áo bằng nước và xà phòng.", "Thiết bị giúp làm khô quần áo nhanh chóng sau khi giặt.", "đồ vật"));
        pairs.add(pair("Nồi cơm điện", "Lò vi sóng", "Vật dụng không thể thiếu để nấu chín hạt gạo.", "Thiết bị dùng sóng điện từ để hâm nóng thức ăn cực nhanh.", "đồ vật"));
        pairs.add(pair("Bàn chải đánh răng", "Bàn chải tóc", "Dụng cụ vệ sinh cá nhân giúp bảo vệ hàm răng trắng sáng.", "Dụng cụ giúp gỡ rối và làm mượt những sợi tóc.", "đồ vật"));
        pairs.add(pair("Ví tiền", "Túi xách", "Vật dụng nhỏ gọn dùng để đựng tiền và các loại thẻ.", "Phụ kiện thời trang dùng để đựng nhiều đồ dùng cá nhân khi ra ngoài.", "đồ vật"));
        pairs.add(pair("Kính mắt", "Kính áp tròng", "Phụ kiện đeo trên mặt để hỗ trợ thị lực hoặc thời trang.", "Miếng nhựa mỏng đặt trực tiếp lên con ngươi để nhìn rõ hơn.", "đồ vật"));
        pairs.add(pair("Máy ảnh", "Điện thoại chụp ảnh", "Thiết bị chuyên dụng dùng để lưu lại những khoảnh khắc đẹp.", "Tính năng phổ biến trên smartphone dùng để ghi hình.", "đồ vật"));

        // Động vật
        pairs.add(pair("Chó", "Mèo", "Loài vật trung thành, thường được coi là người bạn tốt của con người.", "Loài vật kiêu kỳ, thích bắt chuột và thích được vuốt ve.", "động vật"));
        pairs.add(pair("Sư tử", "Hổ", "Chúa tể sơn lâm với chiếc bờm oai vệ.", "Loài thú săn mồi dũng mãnh với bộ lông vằn đặc trưng.", "động vật"));
        pairs.add(pair("Cá heo", "Cá mập", "Loài động vật biển thông minh, thân thiện với con người.", "Sát thủ đại dương với hàm răng sắc nhọn và khứu giác nhạy bén.", "động vật"));
        pairs.add(pair("Đại bàng", "Diều hâu", "Chúa tể bầu trời với đôi mắt tinh anh và sải cánh rộng.", "Loài chim săn mồi cỡ trung, bay lượn rất linh hoạt.", "động vật"));
        pairs.add(pair("Voi", "Tê giác", "Động vật trên cạn lớn nhất với chiếc vòi dài.", "Loài thú lớn với lớp da dày và chiếc sừng trên mũi.", "động vật"));
        pairs.add(pair("Thỏ", "Sóc", "Loài vật gặm nhấm có đôi tai dài và thích ăn cà rốt.", "Loài vật nhỏ bé nhanh nhẹn, thích leo trèo và ăn hạt dẻ.", "động vật"));
        pairs.add(pair("Gà", "Vịt", "Loài gia cầm gáy báo thức vào mỗi buổi sáng.", "Loài gia cầm thích bơi lội và có tiếng kêu cạp cạp.", "động vật"));
        pairs.add(pair("Rắn", "Thằn lằn", "Loài bò sát không chân, di chuyển bằng cách trườn.", "Loài bò sát nhỏ có bốn chân, thường leo trèo trên tường.", "động vật"));
        pairs.add(pair("Cua", "Tôm", "Loài thủy sinh có lớp vỏ cứng và hai chiếc càng to.", "Loài thủy sinh thân mềm hơn, có nhiều chân và bơi lùi.", "động vật"));
        pairs.add(pair("Bướm", "Ong", "Loài côn trùng có đôi cánh rực rỡ sắc màu.", "Loài côn trùng chăm chỉ hút mật và có thể châm đốt.", "động vật"));

        // Đồ ăn
        pairs.add(pair("Phở", "Bún bò", "Món ăn quốc hồn quốc túy của Việt Nam với nước dùng thanh ngọt.", "Món bún đặc sản miền Trung với vị cay nồng và mùi mắm ruốc.", "đồ ăn"));
        pairs.add(pair("Cơm tấm", "Cơm chiên", "Món cơm bình dân ăn kèm với sườn nướng và bì chả.", "Món cơm được đảo trên chảo nóng cùng với trứng và các loại gia vị.", "đồ ăn"));
        pairs.add(pair("Bánh mì", "Bánh bao", "Món ăn đường phố nổi tiếng thế giới của người Việt.", "Món bánh hấp mềm mại với nhân thịt và trứng cút bên trong.", "đồ ăn"));
        pairs.add(pair("Pizza", "Hamburger", "Món bánh tròn của Ý với lớp phô mai tan chảy.", "Món bánh kẹp thịt kiểu Mỹ rất phổ biến toàn cầu.", "đồ ăn"));
        pairs.add(pair("Sushi", "Ramen", "Tinh hoa ẩm thực Nhật Bản với cá sống và cơm giấm.", "Món mì nước đậm đà trứ danh của xứ sở hoa anh đào.", "đồ ăn"));
        pairs.add(pair("Kem", "Chè", "Món tráng miệng mát lạnh tan chảy trong miệng.", "Món ăn ngọt truyền thống với nhiều loại đậu và nước cốt dừa.", "đồ ăn"));
        pairs.add(pair("Cà phê", "Trà sữa", "Thức uống giúp tỉnh táo vào mỗi buổi sáng.", "Thức uống giải khát yêu thích của giới trẻ với các loại topping.", "đồ ăn"));
        pairs.add(pair("Nước cam", "Nước chanh", "Thức uống giàu vitamin C, có màu vàng cam rực rỡ.", "Thức uống giải nhiệt có vị chua đặc trưng.", "đồ ăn"));
        pairs.add(pair("Xoài", "Dứa", "Loại trái cây nhiệt đới ngọt lịm khi chín.", "Loại trái cây có nhiều mắt và hương thơm rất mạnh.", "đồ ăn"));
        pairs.add(pair("Chocolate", "Kẹo cao su", "Món kẹo ngọt ngào làm từ hạt ca cao.", "Loại kẹo dùng để nhai nhưng không được nuốt.", "đồ ăn"));

        keywordPairRepository.saveAll(pairs);
    }

    private KeywordPair pair(String civilian, String spy, String civilianDesc, String spyDesc, String category) {
        KeywordPair p = new KeywordPair();
        p.setCivilianKeyword(civilian);
        p.setSpyKeyword(spy);
        p.setCivilianDescription(civilianDesc);
        p.setSpyDescription(spyDesc);
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