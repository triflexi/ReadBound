# ReadBound Plugin SDK v1

A plugin is a ZIP archive with the `.readbound-plugin` extension. It contains `manifest.json` and one JavaScript ES module. Installation always shows the plugin identity, capabilities and allowed domains before any files are activated.

## Minimal package

```text
my-plugin.readbound-plugin
├── manifest.json
└── main.js
```

`manifest.json`:

```json
{
  "id": "dev.example.dictionary",
  "name": "Dictionary lookup",
  "version": "1.0.0",
  "apiVersion": 1,
  "entrypoint": "main.js",
  "permissions": ["network"],
  "allowedDomains": ["api.dictionaryapi.dev"],
  "actions": [
    { "id": "lookup", "title": "Dictionary", "context": "selection" }
  ]
}
```

`main.js` must export `handleSelection(context)`. It may be asynchronous. The context currently contains `quote` and `book: { id, title }`.

```js
export async function handleSelection(context) {
  return { type: "message", message: `Selected: ${context.quote}` };
}
```

## Result types

- `{ "type": "message", "message": "..." }` — show a short result.
- `{ "type": "anki", "quote": "front", "back": "back" }` — create one AnkiDroid card; requires `anki.write`.
- `{ "type": "http", "url": "https://...", "method": "GET|POST", "body": "..." }` — host-performed HTTPS request; requires `network`, and the exact host must be in `allowedDomains`.

Plugins have no direct Android, filesystem, database or socket API. Unknown permissions are rejected. Archives are limited to 5 MB, paths are checked against ZIP traversal, bundled plugins cannot be replaced, and execution is isolated and time-limited.

To package the sample from PowerShell:

```powershell
Compress-Archive -Path .\samples\dictionary-plugin\manifest.json,.\samples\dictionary-plugin\main.js -DestinationPath dictionary-plugin.zip
Rename-Item dictionary-plugin.zip dictionary-plugin.readbound-plugin
```
