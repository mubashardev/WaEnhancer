# WaEnhancerX (WaEnhancer X)
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

### Privacy & Security
- `Anti-Revoke`
- `Anti-View Once`
- `Hide Read Receipts`
- `Hide Delivery Reports`
- `Hide Seen Status`
- `Typing Privacy`
- `Recording Privacy`
- `Call Privacy`
- `Locked Chats Enhancer`

### Visual Customization
- `Custom Themes (V2)`
- `Bubble Colors`
- `Custom Toolbar`
- `Hide Tabs`
- `Instagram-Style Status`
- `Menu Home`
- `Custom Font`

### Media & Data

- `Auto Status Forward on Reply`
- `Force Cloud Restore`
- `Status Download`
- `Profile Picture Download`
- `Download View Once`
- `Media Quality`
- `Share Limit Bypass`
- `Status Length Bypass`

### Tools & Utilities

- `Call Recording`
- `Google Translate Integration`
- `Audio Transcript`
- `Tasker Integration`
- `Toast Viewer`
- `Block Verification`
- `Message Scheduler`
- `Auto Reply`

### Chat Enhancements

- `Pinned Limit Bypass`
- `Chat Filters`
- `Separate Groups`
- `Text Status Composer`
- `Stickers Management`


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
| Feature Category | WaEnhancer (Dev4Mod) | ⚡ WaEnhancerX (MubasharDev) |
| :--- | :--- | :--- |
| **Performance Philosophy** | Bloated with experimental plugins. | **Hyper-Optimized.** Consolidates features (e.g., merging advanced SQLite queries into `ChatScrollButtons`) to keep the module lightweight. |
| **Status Deletion** | Slow, relying on WhatsApp's laggy UI dialogs. | **Zero-Lag Execution.** Directly targets the SQLite `message` database and File System (`MediaProvider`) to nuke statuses instantly. |
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

### ⚖️ Credits & Acknowledgements
WaEnhancerX originally started as a fork of **WaEnhancer** by **Dev4Mod**. I deeply respect and acknowledge the foundational open-source work provided by the original author.

*Built for the community.*

*Managed and Optimized by [Mubashar Dev](https://mubashar.dev)*
