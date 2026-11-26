import urllib.parse

contact_name = "Om"
msg = "❤️"

# In Android, sending a message directly via Intent without needing to navigate UI manually:
# 1. Direct send to text: Intent.ACTION_SEND with "image/*" or "text/plain" and package "com.whatsapp"
# 2. Or Uri.parse("https://api.whatsapp.com/send?text=" + URLEncoder.encode(msg, "UTF-8"))
print("WhatsApp direct text intent test")
