# Dự án AI Map (Android & AI Microservice)

Dự án này xây dựng một ứng dụng di động Android cho phép người dùng sử dụng ngôn ngữ tự nhiên để tương tác với AI đưa ra các câu hỏi liên quan đến Map

## Cấu trúc dự án (Monorepo)

Dự án được quản lý dưới dạng monorepo, chứa cả code của ứng dụng di động và dịch vụ AI trong cùng một repository.

```
/
├── mobile-app/
│   └── (Mã nguồn dự án Android Studio)
│
├── ai-service/
│   ├── app/
│   │   ├── api/
│   │   ├── core/
│   │   ├── models/
│   │   └── services/
│   ├── main.py
│   └── requirements.txt
│
├── .gitignore
└── README.md
```

## Các thành phần

### 1. Ứng dụng di động (`mobile-app`)

- **Nền tảng:** Android (Java/Kotlin)
- **Mô tả:** Là giao diện chính để người dùng tương tác, nhập câu hỏi tìm kiếm và nhận kết quả trả về từ AI.
- **Chi tiết:** Xem thêm trong `mobile-app/README.md` (sẽ được tạo sau).

### 2. Dịch vụ AI (`ai-service`)

- **Nền tảng:** Python
- **Mô tả:** Microservice backend có nhiệm vụ nhận yêu cầu từ ứng dụng di động, phân tích câu hỏi bằng Gemini, truy vấn Google Maps API, và trả về câu trả lời đã được tổng hợp thông minh.
- **Chi tiết:** Xem thêm trong `ai-service/README.md`.

## Bắt đầu

Để bắt đầu phát triển, bạn cần cài đặt môi trường cho cả hai thành phần. Vui lòng tham khảo hướng dẫn chi tiết trong file `README.md` của từng thư mục con.
