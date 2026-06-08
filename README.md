# BreakAllBlocks

> **Author:** MRsuffix  
> **Version:** 1.0.0  
> **Target:** Paper 1.20.4 (compatible with Spigot 1.20+)  
> **Java:** 17+

---

## 🔥 Concept

When a player breaks **any block**, every block of that same type across the **entire world** is instantly removed. Each block type can only be destroyed **once — permanently and irreversibly** (unless an admin restores it via command).

---

## 📦 Project Structure

```
BreakAllBlocks/
├── pom.xml
└── src/
    └── main/
        ├── java/com/mrsuffix/breakallblocks/
        │   ├── BreakAllBlocks.java              ← Main plugin class
        │   ├── commands/
        │   │   └── BabCommandHandler.java       ← /bab command + tab-complete
        │   ├── listeners/
        │   │   ├── BlockBreakListener.java      ← Break event handler
        │   │   └── ChunkLoadListener.java       ← Lazy chunk-load cleaner
        │   ├── managers/
        │   │   ├── ConfigManager.java           ← config.yml wrapper
        │   │   ├── EliminationManager.java      ← Persistent eliminated list
        │   │   └── WorldScanner.java            ← Async world sweep engine
        │   └── util/
        │       └── MessageUtil.java             ← MiniMessage formatting
        └── resources/
            ├── plugin.yml
            └── config.yml
```

---

## 🛠️ Building

### Prerequisites
- Java 17 or newer
- Apache Maven 3.8+

### Compile & package

```bash
mvn clean package
```

The compiled JAR will be at:

```
target/BreakAllBlocks-1.0.0.jar
```

Copy it to your server's `plugins/` folder.

---

## ⚙️ Configuration (`plugins/BreakAllBlocks/config.yml`)

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Master toggle for the plugin |
| `batch_size` | `500` | Blocks removed per scheduler tick |
| `batch_delay_ticks` | `1` | Ticks between batch removals (1 tick = 50ms) |
| `broadcast_messages` | `true` | Broadcast elimination messages to all players |
| `count_indirect_breaks` | `false` | Explosions/pistons also trigger elimination |
| `count_creative_breaks` | `false` | Creative-mode players trigger elimination |
| `worlds` | `[]` | Active world names (empty = all worlds) |
| `excluded_materials` | `[BEDROCK, ...]` | Materials immune to elimination |

All messages use **MiniMessage** format. Supported tags: `<red>`, `<bold>`, `<gradient:#hex1:#hex2>`, etc.  
Full reference: https://docs.advntr.dev/minimessage/format.html

---

## 📋 Persistent Data (`plugins/BreakAllBlocks/eliminated_blocks.yml`)

Eliminated block types are automatically saved here. The file is updated on every elimination and on server shutdown.  
On server start, the plugin **re-scans all loaded chunks** and removes any eliminated blocks that may have regenerated.

---

## 🔑 Permissions

| Node | Default | Description |
|---|---|---|
| `breakallblocks.admin` | `op` | Access to all `/bab` commands |
| `breakallblocks.bypass` | `false` | Player can still break eliminated blocks |
| `breakallblocks.use` | `true` | Player triggers elimination on break |

---

## 💬 Commands

| Command | Description |
|---|---|
| `/bab reload` | Reload `config.yml` |
| `/bab list` | List all eliminated block types |
| `/bab restore <MATERIAL>` | Remove a material from the eliminated list |
| `/bab resetall` | Clear the entire eliminated list |

All subcommands require `breakallblocks.admin`.  
Tab-completion is supported for subcommands and material names.

---

## ⚡ Performance

The plugin is designed to stay lag-free even on large servers:

| Concern | Solution |
|---|---|
| Scanning loaded chunks | Done **asynchronously** |
| Removing blocks | **Batched on the main thread** (`batch_size` blocks per tick) |
| Unloaded chunks | Handled **lazily** via `ChunkLoadListener` — no force-loading |
| Startup re-scan | Delayed 3 seconds after enable to let the world settle |

You can tune `batch_size` and `batch_delay_ticks` in `config.yml` to match your server's hardware.

---

## 🧩 Compatibility

- **Paper 1.20.4** (primary — uses Adventure API)
- **Spigot 1.20.x** (compatible — Adventure is bundled via Paper API)
- Does **not** require additional dependency JARs

---

## 📝 Changelog

### 1.0.0
- Initial release
- Async world scanning with batched block removal
- Persistent eliminated block list (survives restarts)
- Lazy chunk-load cleaning
- MiniMessage-formatted, fully configurable messages
- Admin commands: reload, list, restore, resetall
- Permission nodes with bypass support
- Configurable excluded materials and world filters

---

## 🐛 Known Limitations / Future Work

- Blocks removed via `/bab restore` are **not** regenerated — they just become breakable again.
- The scanner only covers **loaded chunks** at elimination time. Unloaded chunks are cleaned when they next load.
- Very large worlds with many blocks of the eliminated type may take several minutes to fully clean.
