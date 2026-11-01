# AmiMate Backend (Spring Boot)

BE quản lý tài khoản phụ huynh, liên kết robot bằng PIN và đồng bộ lịch sử hội thoại từ Xiaozhi.
Robot dùng firmware `.bin` của xiaozhi.me và không gọi BE trực tiếp.

## Biến môi trường
| Biến | Ý nghĩa |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `JWT_SECRET`, `JWT_EXPIRATION_MINUTES` | JWT của app phụ huynh |
| `ADMIN_SECRET` | Header `X-Admin-Secret` cho `POST /api/v1/devices/provision` |
| `XIAOZHI_BEARER_TOKEN` | Token lấy từ xiaozhi.me, dùng để đọc chat của các agent |
| `XIAOZHI_SYNC_COOLDOWN_SECONDS` | (tùy chọn, mặc định 30) khoảng cách tối thiểu giữa hai lần gọi Xiaozhi cho cùng một robot |

## Quy trình thêm robot mới
1. Tạo agent trên xiaozhi.me, tải `.bin` và nạp vào robot; ghi lại `agentId`.
2. Đăng ký robot (PIN chỉ hiện một lần, in lên hộp cùng `agentId`):
   ```bash
   curl -X POST http://localhost:8080/api/v1/devices/provision \
     -H "X-Admin-Secret: $ADMIN_SECRET" -H "Content-Type: application/json" \
     -d '{"agentId":"2496934","name":"AmiMate"}'
   ```
3. Phụ huynh nhập `agentId` + PIN trong app (`POST /api/v1/devices/claim`). Sai PIN 5 lần bị khóa 15 phút.
4. `POST /api/v1/logs/sync-xiaozhi?deviceId=<agentId>` đồng bộ tăng dần: chỉ tải chat chưa đồng bộ và chat mới nhất nếu `msg_count` đổi.

## Nâng cấp từ bản cũ
```sql
ALTER TABLE device DROP COLUMN api_key_hash;
```
Đổi `FACTORY_SECRET` thành `ADMIN_SECRET`; bỏ `XIAOZHI_AGENT_ID`.

Xem API contract đầy đủ tại `amimateFE/AmiMate_Architecture_Spec_v3.md`.
