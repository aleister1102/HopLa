# HopLa - Burp Copilot (Enhanced UI Fork)

This is a fork of the original [HopLa](https://github.com/synacktiv/HopLa) extension, featuring an **enhanced User Interface**, **Anthropic support**, and additional improvements.

💥 All the power of PayloadsAllTheThings, without the overhead.

HopLa enhances Burp Suite with intelligent autocompletion, AI-powered chat/transformations, and a payload library.

**Key Features**:
* 🤖 **AI Integration**: Chat & Quick Actions with Ollama, OpenAI, Anthropic, and Gemini.
* ⚡ **Autocompletion**: "Copilot-style" suggestions for your requests.
* 🛠️ **Payload Library**: One-click insertion of XSS, SQLi, and custom payloads.
* 🔄 **Transformations**: Use AI to refactor requests (e.g., JSON to Multipart).
* 🔍 **Search & Replace**: Enhanced search in Repeater.
* ⌨️ **Productivity**: Custom shortcuts and keywords.

![Demo GIF](img/demo.gif)

## Installation

1. Download the latest jar from the [Releases](https://github.com/synacktiv/HopLa/releases) page.
2. In Burp Suite, go to **Extensions** -> **Add**.
3. Select the `HopLa.jar` file.

## Usage

### Shortcuts
* **`Ctrl+Q`** - Payload Library
* **`Ctrl+J`** - AI Chat
* **`Ctrl+Alt+O`** - AI Quick Actions
* **`Ctrl+L`** - Search & Replace
* **`Ctrl+M`** - Insert Burp Collaborator
* **`Ctrl+Alt+J`** - Add Custom Keyword

### Configuration
You can customize AI providers and Payloads by importing YAML configuration files via the HopLa menu.

* **AI Configuration**: [View Sample](src/main/resources/ai-configuration-sample.yaml)
* **Payloads**: [View Default Payloads](src/main/resources/default-payloads.yaml)

### Window Manager (i3)
If using i3, enable floating mode for the HopLa window:
```config
for_window [class=".*burp-StartBurp.*" title="^ $"] floating enable
```

## Build

To build from source:

```bash
gradle build
```
The output will be in `releases/HopLa.jar`.

## Thanks To

* **[Alexis Danizan](https://twitter.com/alexisdanizan/)** and **[Synacktiv](https://www.synacktiv.com/)** for the original [HopLa](https://github.com/synacktiv/HopLa).
* [Static-Flow/BurpSuiteAutoCompletion](https://github.com/Static-Flow/BurpSuiteAutoCompletion)
* [d3vilbug/HackBar](https://github.com/d3vilbug/HackBar)
* [swisskyrepo/PayloadsAllTheThings](https://github.com/swisskyrepo/PayloadsAllTheThings)

## License

BSD 3-Clause License. See [LICENSE](LICENSE) for details.
