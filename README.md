# WaEnhancer X
<div align="center">
  <p><strong>WaEnhancer X is a powerful Xposed/LSPosed module that supercharges your WhatsApp experience with advanced privacy, customization, and utility features.</strong></p>

  > **Legal & DMCA Disclaimer:** WaEnhancer X is a community-driven, independent fork. It is not affiliated with, endorsed by, or in any way associated with WhatsApp Inc. or Meta Platforms, Inc. This project is provided "as is" for research and educational purposes. Use at your own risk.
</div>

[![Youtube Video](http://thumb.mubashar.dev/?id=BIrojFtTGJ8)](https://www.youtube.com/watch?v=BIrojFtTGJ8)

## Why WaEnhancer X?
- **Unified LSPosed scope**: Optimized hooks for seamless integration.
- **Strict Semantic Versioning**: Clear and predictable release cycles.
- **Embedded Native UI**: Seamlessly integrated settings directly within WhatsApp.

## Main Features

### ⚡ Pro Features (Exclusive to WaEnhancerX Pro)
- **Message Bomber**: Send multiple repeated messages to a contact or group instantly with customizable count and delays.
- **Enhanced Media Deletion (Delete Message File)**: Automatically delete downloaded media files from your local storage when a message is deleted for you/me or by the sender.
- **Sent Media Clean-Up (Delete Message File Sent)**: Automatically delete sent media files from your device's local storage when you delete a sent message.

### Privacy & Security
- `Anti-Revoke` (Prevents chats/messages from being deleted for you)
- `Anti-View Once` (Bypasses restriction to view view-once messages/media infinitely)
- `Hide Read Receipts` (Prevent others from knowing you read their messages)
- `Hide Delivery Reports` (Hide the second tick to show offline)
- `Hide Seen Status` (View statuses without appearing in the viewed list)
- `Typing Privacy` (Hide the 'typing...' indicator)
- `Recording Privacy` (Hide the 'recording...' indicator)
- `Call Privacy` (Customize who can call you and hide call metadata)
- `Freeze Last Seen` (Freeze your active time status)
- `Recover Delete For Me` (Instantly undo or recover a message you accidentally deleted for yourself)
- `Spy Mode` (Read messages and statuses with absolute stealth)
- `Custom Privacy Exceptions` (Setup custom exceptions per contact)
- `Locked Chats Enhancer` (Secure and extend standard locked chats)
- `Hide Chat` (Completely hide specific chats from your main chat screen)

### Visual Customization
- `Custom Themes (V2)` (Advanced custom dark/light color themes)
- `Bubble Colors` (Personalize incoming and outgoing message bubble backgrounds)
- `Custom Toolbar` (Modify or hide elements in the main conversation toolbar)
- `Hide Tabs` (Fully dynamic custom tab hiding for chats, groups, updates, calls)
- `Instagram-Style Status` (Display updates/statuses horizontally like Instagram stories)
- `Menu Home` (Inject custom options into the main action menu)
- `Custom Font` (Apply custom system or bundled typography)
- `Custom Time` (Display precise message and call timestamps)
- `Custom Views` (Inject customized background shapes and layouts)

### Media & Data
- `Auto Status Forward on Reply` (Auto-forward specific status updates instantly)
- `Force Cloud Restore` (Manually trigger local/cloud backup database restoration)
- `Status Download` (Download and save status videos and images)
- `Profile Picture Download` (Easily download and save profile photos)
- `Download View Once` (Directly save view-once media items to your device)
- `Video Note Attachment` (Directly send custom video note clips)
- `Media Quality` (Configure original quality status and media uploads)
- `Media Preview` (Instantly preview status media without loading full screens)
- `Share Limit Bypass` (Share files to unlimited contacts at once)
- `Status Length Bypass` (Upload high-duration status videos)

### Tools & Utilities
- `Call Recording` (Record incoming and outgoing calls with high audio quality)
- `Google Translate Integration` (Inline translate messages instantly)
- `Audio Transcript` (Transcribe incoming audio messages to text)
- `Tasker Integration` (Broadcast intents to control features automatically)
- `Toast Viewer` (Receive floating notifications for active updates/events)
- `Block Verification` (Check if you are blocked by a contact safely)
- `Message Scheduler` (Schedule messages to be sent at specific dates/times)
- `Auto Reply` (Auto-respond to incoming messages dynamically)
- `Backup & Restore` (Safely export/import WaEnhancer configurations)
- `DND Mode` (Do Not Disturb - silent notifications and pretend you are offline)
- `Lite Mode` (Performance booster for low-end Android devices)
- `Anti-WA Expiration` (Bypass hardcoded WhatsApp version expiration/reversion prompts)

### Chat Enhancements
- `Pinned Limit Bypass` (Pin unlimited chats to the top of your chat list)
- `Chat Filters` (Organize and filter conversations with custom tabs)
- `Separate Groups` (Separate personal chats from groups into distinct tabs)
- `Text Status Composer` (Customize text status composer features)
- `Stickers Management` (Save, manage, and duplicate sticker packs)
- `New Chat Shortcut` (Directly start a conversation with unsaved numbers)
- `Chat Limit Bypass` (Bypass chat message limit restrictions)
- `Tag Message` (Advanced custom tags for messages and contacts)
- `Delete Status` (Instantly delete your own status updates with zero delay)
- `Group Admin Indicator` (Custom badges or indicators for group admins)
- `Copy Status Text` (Copy text from picture or text status updates easily)
- `Channels Enhancements` (Lock or improve standard channels integration)


## Why choose WaEnhancerX

WaEnhancerX isn't just a fork; it's a **high-performance, native-first evolution** of the original project. While the original WaEnhancer shifted towards experimental Kotlin and detached UIs, WaEnhancerX was rebuilt from the ground up to prioritize absolute stability, zero-lag performance, and native WhatsApp integration.

### 🌟 Exclusive Features (Only in WaEnhancerX)
- **Deep Settings Search:** Stop digging through menus. Our custom `FeatureCatalog` maps every toggle to localized strings for instant, deep searching.
- **Embedded Native UI:** No jarring app transitions. WaEnhancerX settings are injected directly into WhatsApp’s native layout using `PreferenceFragmentCompat` for a seamless experience.
- **Deleted By Me Log:** Keeps a local history of messages you deleted, allowing you to review or restore them natively.
- **Aggressive Anti-Meta AI:** WhatsApp constantly tries to force Meta AI onto your screen via server-side resets. WaEnhancerX actively hunts down and destroys these UI elements (`View.GONE`) in real-time, beating server overrides.
- **Tasker Automation:** Total extensibility. Control your privacy toggles and actions via Android Intent broadcasts using Tasker.
- **Keyword-Based Status Forwarding:** Set up custom rules to auto-forward statuses based on specific keywords.
- **Custom Admin Indicators:** Spot group admins instantly with customizable text or emoji badges next to their names.
- **Dynamic Update Channels:** Switch between Stable and Beta release channels effortlessly from within the app to test bleeding-edge features.

### Architectural Supremacy
| Feature Category | Legacy / Standard Modules | ⚡ WaEnhancerX (MubasharDev) |
| :--- | :--- | :--- |
| **Performance Philosophy** | Heavy / Experimental Logic. | **Hyper-Optimized.** Consolidates features (e.g., merging advanced SQLite queries into `ChatScrollButtons`) to keep the module lightweight. |
| **Status Deletion** | Standard UI Dialog Delays. | **Zero-Lag Execution.** Directly targets the SQLite `message` database and File System (`MediaProvider`) to nuke statuses instantly. |
| **Code Architecture** | Fragmented between Java and Kotlin (`.kt`). | **100% Pure Java.** Maintains strict, predictable reflection hooking (`FeatureLoader`) ensuring absolute Xposed compatibility without crashes. |
| **UI Integration** | Moving towards detached, custom Compose UIs. | **Fully Embedded.** Looks and feels exactly like an official WhatsApp settings menu. |
| **Contact Selection** | Basic Kotlin intents. | **Optimized Java API** A robust foundation for future per-contact Privacy Exception Lists. |

## Installation
1. Ensure that your device is **Rooted**.
2. Install the **LSPosed** (Zygisk or Riru version) on your device.
3. Download and install the **WaEnhancer X** APK.
4. Open the **LSPosed Manager** app.
5. Enable the **WaEnhancer X** module.
6. Ensure **WhatsApp** is selected in the scope.
7. **Force Stop** WhatsApp.
8. Open WhatsApp and verify the module is active.



---

### ⚖️ Open Source Attribution & Architectural Divergence

WaEnhancerX originally started as a direct fork of **WaEnhancer** by **Dev4Mod**. We deeply respect the foundational work and legacy Xposed hooking concepts created by the original author.

**Why the Repository Was Detached (Architectural Independence):**
The repository was officially detached from the upstream network to pursue an independent architectural roadmap. At the time, the original project initiated a rapid migration to Kotlin. While Kotlin is an excellent modern standard, staying attached would have caused massive merge conflicts with our ongoing, deep-level Java optimizations.

Detaching allowed us to first stabilize, refactor, and achieve a zero-lag UI entirely on our own terms, without upstream changes breaking our core architecture. This gives WaEnhancerX the freedom to evolve at its own pace, whether that means perfecting the current foundation or transitioning frameworks in the future.

All legacy logic and core methods adapted from Dev4Mod are explicitly attributed here in full compliance with open-source ethics. Today, WaEnhancerX continues to evolve independently as a highly optimized, tracker-free, and stable ecosystem.

*Built for the community.*

*Managed and Optimized by [Mubashar Dev](https://mubashar.dev)*
