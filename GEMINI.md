# Bối cảnh dự án AI Map

Tài liệu này dùng để định hình bối cảnh và mục tiêu cho dự án AI Map, giúp Gemini có thể hỗ trợ một cách tốt nhất trong quá trình phát triển.

## 1. Mục tiêu dự án

Xây dựng một ứng dụng di động Android cho phép người dùng tìm kiếm các địa điểm xung quanh bằng cách sử dụng các câu hỏi bằng ngôn ngữ tự nhiên.

## 2. Tính năng cốt lõi & Vai trò của AI

- **Tìm kiếm bằng ngôn ngữ tự nhiên:** AI đóng vai trò trung tâm trong việc hiểu và phân tích các câu hỏi phức tạp của người dùng.
- **Ví dụ câu hỏi:**
    - "Có quán ăn hàn quốc nào ở đây ngon không?"
    - "Xung quanh đây những quán cơm tấm nào có đánh giá và review tốt nhất?"
    - "Ở gần đây có những sân bóng đá nào, sắp xếp theo số lượt đánh giá nhé."
- **Tổng hợp thông minh:** Sau khi lấy được dữ liệu, AI sẽ tự tổng hợp, lọc và sắp xếp thông tin để đưa ra câu trả lời phù hợp nhất cho người dùng, thay vì chỉ hiển thị một danh sách thô.

## 3. Luồng hoạt động chính

1.  Người dùng nhập câu hỏi tìm kiếm bằng ngôn ngữ tự nhiên vào ứng dụng.
2.  Ứng dụng gửi câu hỏi đến model AI (Gemini).
3.  Model AI phân tích câu hỏi để xác định các yếu tố quan trọng (loại địa điểm, tiêu chí đánh giá, yêu cầu sắp xếp...).
4.  Model AI sử dụng **Model Context Protocol (MCP)** để tạo truy vấn và tương tác với **Google Maps API** nhằm lấy dữ liệu địa điểm cần thiết.
5.  Model AI nhận dữ liệu từ API, sau đó xử lý, tổng hợp và định dạng lại thành một câu trả lời tự nhiên, dễ hiểu.
6.  Kết quả được trả về và hiển thị cho người dùng trên ứng dụng.

## 4. Công nghệ chính

- **Nền tảng:** Android (Java/Kotlin)
- **Nguồn dữ liệu:** Google Maps Platform API
- **Giao thức tương tác:** Model Context Protocol (MCP)
- **Xử lý ngôn ngữ:** Gemini

## 5. Giao diện người dùng (UI/UX)

- **Hiện tại:** Giao diện người dùng chưa được định hình cụ thể ở giai đoạn đầu.
- **Tập trung ban đầu:** Xây dựng và hoàn thiện luồng xử lý backend (từ câu hỏi của người dùng đến câu trả lời của AI).
