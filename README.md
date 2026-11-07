# Net2SMS – Android SMS gateway (SendMode / ClickSend style)

Your phone becomes an SMS gateway: apps call a server API, the phone picks up the
queued messages and sends them as normal SMS from its own SIM.

```
Your app --POST /api/send--> Server <--polls-- Android phone --> SMS
```

## 1. Build the APK on GitHub
1. Create a new GitHub repo and push this folder to it (branch `main`).
2. Open the **Actions** tab -> *Build APK* runs automatically (or click *Run workflow*).
3. When it finishes, open the run and download **Net2SMS-apk** -> `Net2SMS.apk`.
4. Tip: push a tag (`git tag v1.0 && git push --tags`) to also get it under **Releases**.

## 2. Run the server (needs a public URL)
```
cd server && npm install
API_KEY=my-secret-key npm start
```
Host it on Render, Railway, Fly.io or a VPS, and use HTTPS.

## 3. Set up the phone
Install the APK (allow "install unknown apps"), enter the server URL + API key, tap
**Start gateway**, grant SMS permission. Disable battery optimization for the app.

## 4. Send an SMS
```
curl -X POST https://YOUR-SERVER/api/send \
  -H "X-API-Key: my-secret-key" -H "Content-Type: application/json" \
  -d '{"to":"+27821234567","body":"Hello from the internet"}'
```

## Notes
- Debug-signed APK is fine for sideloading. Google Play restricts SEND_SMS apps.
- Android and carriers throttle bulk SMS; the app waits 1.5 s between messages.
- Only message people who have agreed to receive them.
- Status is reported as "sent" once handed to the SMS system (not delivery receipts).
