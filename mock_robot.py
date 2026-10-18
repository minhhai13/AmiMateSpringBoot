import requests
import uuid
import time

# Cấu hình API và Device
API_URL = "http://localhost:8080/api/v1/logs"
DEVICE_KEY = "amk_Pn2UjkkWNOGN_YyErk2jkDfpd2EW7RLQKXVu_rJuxBA"
SESSION_ID = f"sess-{uuid.uuid4().hex[:8]}"

headers = {
    "Content-Type": "application/json",
    "X-Device-Key": DEVICE_KEY
}

# Kịch bản trò chuyện
conversations = [
    {"user": "Xin chào", "bot": "Chào bạn, mình là AmiMate!"},
    {"user": "Bạn làm được gì?", "bot": "Mình có thể trò chuyện, phát âm tiếng Anh và quản lý lịch trình."},
    {"user": "Robot có thích ăn uống không?", "bot": "Mình chỉ thích 'ăn' điện và dữ liệu thôi nha."},
    {"user": "Tạm biệt", "bot": "Tạm biệt bạn, hẹn gặp lại nhé!"}
]

print(f"🤖 Đang giả lập robot AMI-8F7565 gửi dữ liệu lên server...\n")

for i, turn in enumerate(conversations):
    payload = {
        "clientMessageId": str(uuid.uuid4()), # Generate UUID ngẫu nhiên độ dài 36 ký tự (hợp lệ với max 64)
        "sessionId": SESSION_ID,
        "userMessage": turn["user"],
        "botResponse": turn["bot"]
    }
    
    try:
        response = requests.post(API_URL, json=payload, headers=headers)
        if response.status_code in [200, 201]:
            print(f"✅ [Log {i+1}] Đã đồng bộ: '{turn['user']}'")
        else:
            print(f"❌ [Log {i+1}] Lỗi {response.status_code}: {response.text}")
    except Exception as e:
        print(f"⚠️ Không thể kết nối tới server: {e}")
        
    time.sleep(0.5) # Dừng một chút giữa các request để thời gian tạo log tự nhiên hơn