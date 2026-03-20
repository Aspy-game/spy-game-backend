HỆ THỐNG KINH TẾ (XU) VÀ BẢNG XẾP HẠNG (RANKING) - KEYWORD SPY
1. KHÁI NIỆM CƠ BẢN (CORE CONCEPTS)
Hệ thống quản lý hai chỉ số độc lập trong bảng users:

Số dư Xu (Balance): Dùng để đặt cược vào cửa. Có tăng khi thắng và giảm khi thua/cược. Nếu hết Balance, người chơi phải đợi cứu trợ hoặc điểm danh ngày hôm sau (Phong cách WePlay).

Điểm Xếp Hạng (Ranking Points): Là tổng số xu "thực lực" kiếm được. Chỉ số này CHỈ TĂNG, KHÔNG GIẢM, dùng để phân cấp bậc và hiển thị trên Leaderboard.

2. CÁC CON SỐ CÀI ĐẶT (SYSTEM CONSTANTS)
Tặng người chơi mới: 500 Xu (0 Điểm).

Phí vào cửa (Entry Fee): 100 Xu/người.

Hũ (Pot): 6 người x 100 Xu = 600 Xu.

Thuế hệ thống: 10% (Hũ thực tế để chia là 540 Xu).

Điểm danh hàng ngày: 200 Xu (0 Điểm).

Cứu trợ (khi Balance < 10): 50 Xu (Tối đa 2 lần/ngày, 0 Điểm).

3. QUY TẮC BIẾN ĐỘNG XU VÀ ĐIỂM (LOGIC CHI TIẾT)
A. Khi bắt đầu ván đấu (Cược):

Balance: Trừ 100 Xu.

Ranking Points: Không thay đổi (0 điểm).

B. Khi Phe Gián Điệp (Spy) Thắng (Chia hũ 540 xu):

Gián điệp (Spy) nhận: +350 Xu vào Balance VÀ +350 Điểm vào Ranking Points.

Người bị tha hóa (Infected) nhận: +120 Xu vào Balance VÀ +120 Điểm vào Ranking Points.

Dân thường sống sót cuối cùng nhận: +70 Xu vào Balance VÀ +70 Điểm vào Ranking Points.

Dân thường đã bị loại sớm: Không nhận được xu và điểm (0).

C. Khi Phe Dân Thường (Civilians) Thắng:

Mỗi Dân thường (cả sống và chết) nhận: +135 Xu vào Balance VÀ +135 Điểm vào Ranking Points.

Phe Spy & Infected: Không nhận được xu và điểm (0).

D. Thưởng phụ từ hệ thống (Cộng trực tiếp):

Đoán đúng vai trò ở Vòng 2: +20 Xu vào Balance VÀ +20 Điểm vào Ranking Points.

Thưởng kỹ năng Spy (lừa thành công): +30 Xu vào Balance VÀ +30 Điểm vào Ranking Points.

E. Các khoản hỗ trợ (Không tính vào Rank):

Điểm danh hàng ngày: +200 Xu vào Balance (0 Điểm Ranking).

Quà cứu trợ khi hết tiền: +50 Xu vào Balance (0 Điểm Ranking).

4. CẤU TRÚC DATABASE (DATABASE SCHEMA)
Table: users
Thêm các cột sau vào bảng user hiện có:

balance: INT (Mặc định: 500) - Số tiền khả dụng để chơi.

ranking_points: INT (Mặc định: 0) - Điểm tích lũy trọn đời (Chỉ cộng thêm, không bao giờ trừ).

Table: transactions
Lưu vết mỗi khi có biến động Balance:

user_id: ID người chơi.

amount: Số xu (+ hoặc -).

type: Phân loại (BET, WIN_REWARD, DAILY_CHECKIN, GUESS_BONUS, RELIEF).

created_at: Thời gian.

5. PHÂN CẤP BẬC (RANK TIERS)
Dựa trên ranking_points, hiển thị danh hiệu tương ứng:

Từ 0 đến 1.000 điểm: Đồng (Bronze)

Từ 1.001 đến 3.000 điểm: Bạc (Silver)

Từ 3.001 đến 7.000 điểm: Vàng (Gold)

Từ 7.001 đến 15.000 điểm: Bạch Kim (Platinum)

Trên 15.000 điểm: Kim Cương (Diamond)

6. YÊU CẦU TRIỂN KHAI CHO TRAE AI
Cập nhật Model User để có 2 trường balance và ranking_points.

Viết logic trừ tiền cược khi bắt đầu ván.

Viết hàm finalizeMatch để tính toán phe thắng/thua, thực hiện cộng Xu và Điểm tương ứng theo quy tắc tại mục 3.

Xây dựng Leaderboard sắp xếp theo ranking_points giảm dần.

Ghi chú: "Điểm tích lũy (Ranking Points) là thước đo thực lực, do đó chỉ những khoản xu kiếm được từ việc thắng ván hoặc dùng kỹ năng mới được cộng vào điểm này. Tiền hệ thống tặng (điểm danh, người chơi mới) không được tính vào bảng xếp hạng."