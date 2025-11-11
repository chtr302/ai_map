# AI Map

AI Map là một dự án ứng dụng di động Android thử nghiệm, cho phép người dùng tìm kiếm các địa điểm xung quanh bằng cách sử dụng các câu hỏi bằng ngôn ngữ tự nhiên, thay vì các từ khóa cứng nhắc.

Ví dụ: *"Tìm giúp tôi những quán cơm tấm ngon, rẻ ở gần đây."* hoặc *"Có sân bóng nào gần đây có đánh giá tốt không?"*

## Tính năng chính

- **Tìm kiếm bằng ngôn ngữ tự nhiên:** Thay vì tìm kiếm theo từ khóa, người dùng có thể đặt câu hỏi như đang giao tiếp với một người trợ lý.
- **Phân tích và hiểu câu hỏi:** Một model AI chuyên dụng sẽ phân tích câu hỏi để xác định các yếu tố quan trọng như:
    - Loại địa điểm (quán ăn, quán cà phê, sân bóng...).
    - Các tiêu chí (ngon, rẻ, có đánh giá tốt, gần đây...).
    - Yêu cầu sắp xếp hoặc lọc.
- **Tích hợp Google Maps:** Sử dụng Google Maps Platform API làm nguồn dữ liệu chính để đảm bảo thông tin địa điểm chính xác và cập nhật.
- **Tổng hợp thông minh:** AI không chỉ trả về một danh sách thô, mà sẽ tổng hợp và định dạng lại thông tin thành một câu trả lời tự nhiên, dễ hiểu.

## Kiến trúc & Luồng hoạt động

Dự án được xây dựng theo kiến trúc client-server, bao gồm hai thành phần chính:

1.  **Mobile App (Client):** Giao diện người dùng trên nền tảng Android, nơi người dùng nhập câu hỏi và nhận kết quả trả về.
2.  **AI Service (Backend):** Dịch vụ backend viết bằng Python, chịu trách nhiệm xử lý logic AI.

**Luồng hoạt động cơ bản:**

1.  Người dùng nhập câu hỏi tìm kiếm trên ứng dụng Android.
2.  Ứng dụng gửi câu hỏi đến **AI Service**.
3.  Tại backend, **model AI** sẽ phân tích câu hỏi.
4.  Dựa trên phân tích, model AI sử dụng **Model Context Protocol (MCP)** để tạo và thực thi các truy vấn tới **Google Maps API**.
5.  Sau khi nhận dữ liệu từ Google Maps, **model AI** tiếp tục xử lý, tổng hợp và định dạng lại thành một câu trả lời tự nhiên.
6.  Kết quả cuối cùng được gửi trở lại ứng dụng Android và hiển thị cho người dùng.

## Công nghệ sử dụng

- **Backend:** Python
- **Mobile App:** Android (Java/Kotlin)
- **Xử lý ngôn ngữ:** Model AI chuyên dụng
- **Nguồn dữ liệu:** Google Maps Platform API
- **Giao thức tương tác:** Model Context Protocol (MCP)

## Cấu trúc thư mục

- `/mobile-app`: Chứa toàn bộ mã nguồn của ứng dụng Android.
- `/ai-service`: Chứa mã nguồn của dịch vụ AI backend.