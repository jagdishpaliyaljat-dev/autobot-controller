import re

prompt = "whatsapp open massage om ❤️"

def handle_whatsapp(prompt):
    # Detect recipient and message
    # Look for "massage" or "message" or "msg"
    # Example: "whatsapp open massage om ❤️" -> contact="om", msg="❤️"
    match = re.search(r'(?:message|massage|msg)\s+([a-zA-Z0-9_\u0900-\u097F]+)\s*(.*)', prompt, re.IGNORECASE)
    if match:
        contact = match.group(1).strip()
        msg = match.group(2).strip()
        print(f"Contact: '{contact}', Message: '{msg}'")
    else:
        print("No match")

handle_whatsapp("whatsapp open massage om ❤️")
handle_whatsapp("whatsapp par Om ko message bhejo hello")
