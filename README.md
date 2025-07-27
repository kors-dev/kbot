# kbot

Prometheus 2nd week kbot

## Description

**kbot** is a Telegram bot created for the Prometheus course (week 2). The bot is implemented in Go using the [Cobra](https://github.com/spf13/cobra) library for CLI creation and [telebot](https://github.com/tucnak/telebot) for Telegram integration.

### Main Features

- Responds to text messages in Telegram.
- Displays the application version.
- Easily extendable for adding new commands and features.

### Installation

1. Clone the repository:
   ```sh
   git clone https://github.com/your-repository/kbot.git
   cd kbot
   ```
2. Install dependencies:
   ```sh
   go mod tidy
   ```
3. Add the TELE_TOKEN environment variable with your Telegram bot token.

### Usage

```sh
go run main.go kbot
```

### Project Structure

- `main.go` — entry point.
- `cmd/` — CLI commands (kbot, root, version).
- `README.md` — documentation.

### License

The project is distributed under the MIT license.

---

Feedback and questions.
