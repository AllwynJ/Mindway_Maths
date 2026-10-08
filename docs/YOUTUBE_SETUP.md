# Unlisted YouTube lessons

1. Create and configure your educational YouTube channel.
2. Upload content you have rights to publish. In YouTube Studio set each lesson to **Unlisted** and allow embedding.
3. Record the 11-character video ID and lesson metadata in `videos/{logicalId}`. Use the schema and inactive example in `content/media.example.json`. Check `visibility: "unlisted"` and set `isActive: true` only after verifying the video. The Android client has no YouTube API credential; an admin must verify the actual visibility in Studio.
4. Set `MINDWAY_YOUTUBE_ORIGIN` to an HTTPS origin you control, such as a free Cloudflare Pages site. Use only the origin (no path, query or trailing slash). A placeholder is not a working production origin.
5. The WebView loads a locally constructed, fixed HTML document with that base URL and strict-origin-when-cross-origin referrer policy. YouTube's official IFrame API constructs the standard privacy-enhanced embed with controls enabled and the origin supplied.
6. Test playback on your actual signed build, including fullscreen, background/foreground, network loss and an unavailable/deleted lesson. Error 153 commonly indicates missing/invalid client identification or referrer configuration; verify your owned origin and current YouTube embedding guidance rather than hiding or spoofing traffic.

**Unlisted is not equivalent to secret. Anyone who obtains the URL can reshare it.**

The implementation preserves standard controls, branding and player behavior. It does not download media, extract stream URLs, proxy video streams, block ads, replace YouTube UI, provide background playback or conceal network traffic. Pausing on lifecycle background is required. The only WebView event feedback is a fixed title marker for player completion/errors; there is no general-purpose JavaScript-to-native bridge.

Video IDs and metadata come from Firestore; no list of YouTube URLs is embedded in the APK. Related practice uses the topic ID. All external top-level navigation is restricted to approved HTTPS origins; file/content access, mixed content, popups and multiple windows are disabled. Never enable a permissive SSL error handler.
