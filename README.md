# CustomInvAudit

CustomInvAudit 是一款 Paper 26.x 管理插件，可以用箱子介面查看與修改玩家（含離線）的背包、裝備、終界箱，以及生物的裝備、背包與各種屬性；所有修改都會寫進每日紀錄檔，方便事後稽核。

## 功能

- **玩家背包**：
  - 背包、快捷欄、盔甲、副手一次看完，物品可直接拿放並即時同步回玩家
  - 數值選單：血量、最大血量、飢餓值、飽和度、消耗度、經驗等級、經驗進度、選取的快捷欄
  - 位置選單：查看座標、傳送到他、把他拉過來
  - 強制丟出模式、清空背包（需二次確認）
- **終界箱**：
  - 開自己或別人的終界箱，可與背包介面互相切換
- **離線玩家**：
  - 不使用 NMS，直接讀寫 26.1+ 的 `world/players/data/<uuid>.dat`（舊版 `playerdata/` 也支援）
  - 背包、裝備、終界箱、血量、經驗、飢餓、位置都能改
  - 寫入前自動備份 `<uuid>.dat.cia-bak`，採暫存檔 + 原子替換
  - 玩家登入時若還有沒寫入的修改，會先關閉介面並寫完才放行，不會被覆蓋
  - 資料版本較舊或有無法解析的物品時自動改為唯讀
- **生物**：
  - 裝備（頭、胸、腿、腳、主手、副手、獸鎧、鞍）、終界使者手持方塊
  - 村民、驢、騾、馬、羊駝、悅靈等生物的背包
  - 血量、最大血量、位置與傳送
  - 屬性：顯示名稱、發光、AI、無敵、靜音、重力、不會消失、幼年、坐下、馴服者、羊毛/項圈顏色、村民職業/種類/等級、貓/狼/狐狸/兔子/鮭魚/美西螈/鸚鵡/羊駝/馬/哞菇/牛/豬/雞/青蛙品種、熊貓基因、閃電苦力怕、山羊角、蜜蜂花粉等
- **修改紀錄 (`audit-log`)**：
  - 記錄誰、何時、改了誰的哪一格或哪個數值（含前後內容）
  - 寫在 `plugins/CustomInvAudit/logs/yyyy-MM-dd.log`，批次非同步寫入，超過保留天數自動刪除
- **保護**：
  - 有 `custominvaudit.exempt` 的玩家不能被查看或修改；離線玩家用 `exempt-offline-players` 名單
- **介面規範**：
  - 全部是箱子介面，功能按鈕只吃左鍵；介面 Holder 綁定本插件並在物品上寫入 PDC 簽署
- **更新檢查**：
  - 啟動時非同步檢查 GitHub Releases，管理員登入 3 秒後提醒，結果快取 10 分鐘

## 指令與權限

主指令是 `/custominvaudit`。

| 指令 | 說明 | 執行者 | 權限節點 | 預設 |
|------|------|--------|----------|------|
| `/ca inv <玩家>` | 查看/修改玩家背包（可離線） | 僅玩家 | `custominvaudit.inventory.view` | OP |
| `/ca ec [玩家]` | 開終界箱（不填 = 自己） | 僅玩家 | `custominvaudit.enderchest.self` / `.other` | OP |
| `/ca entity [UUID]` | 查看/修改生物（不填 = 準心指著的生物） | 僅玩家 | `custominvaudit.entity.view` | OP |
| `/ca version` | 檢查 GitHub 更新 | 玩家 / 主控台 | `custominvaudit.version` | OP |
| `/ca reload` | 重新載入並補齊 `config.yml`、`messages.yml` | 玩家 / 主控台 | `custominvaudit.reload` | OP |

> 指令別名：`/ca` 等同 `/custominvaudit`。
>
> 其他權限：
>
> - `custominvaudit.inventory.modify`：修改玩家背包、清空、強制丟出
> - `custominvaudit.enderchest.modify`：修改別人的終界箱
> - `custominvaudit.offline`：開啟離線玩家
> - `custominvaudit.stats.modify`：修改血量、經驗、飢餓等數值
> - `custominvaudit.teleport`：使用傳送按鈕
> - `custominvaudit.entity.modify`：修改生物的裝備、背包與屬性
> - `custominvaudit.exempt`：無法被別人查看或修改（預設無人擁有）
> - `custominvaudit.bypass.exempt`：無視 exempt（預設無人擁有）

## 使用範例

### 1. 檢查離線玩家的背包

1. 確認你有 `custominvaudit.inventory.view` 與 `custominvaudit.offline`
2. 輸入指令，插件會在背景讀檔後開啟介面
3. 拿走或放入物品，關閉介面後自動寫回玩家資料

```bash
/ca inv Steve
```

### 2. 修改村民職業

看著村民輸入指令，點「屬性」→「職業」→ 選擇新職業。

```bash
/ca entity
```

### 3. 常見狀況

- 玩家在你開著離線介面時登入：介面會自動關閉，修改先寫入再讓他進入
- 玩家資料來自舊版本：只能查看，請等他登入一次後再修改
- 目標有 exempt 權限：會提示受到保護

---

# CustomInvAudit (English)

CustomInvAudit is a Paper 26.x admin plugin to view and edit the inventories, equipment and ender chests of online and offline players, plus the equipment, inventories and attributes of mobs, through chest GUIs. Every change is written to a daily audit log.

## Features

- **Player inventory**:
  - Inventory, hotbar, armor and offhand in one GUI; items can be moved freely and sync back live
  - Stats menu: health, max health, food, saturation, exhaustion, XP level, XP progress, selected hotbar slot
  - Location menu: view coordinates, teleport to the player, pull the player to you
  - Force-drop mode and clear inventory (with confirmation)
- **Ender chest**:
  - Open your own or another player's ender chest; switch to the inventory GUI and back
- **Offline players**:
  - No NMS: reads and writes `world/players/data/<uuid>.dat` (26.1+) or legacy `playerdata/`
  - Inventory, equipment, ender chest, health, XP, food and location are editable
  - Backup `<uuid>.dat.cia-bak` before every write; temp file + atomic replace
  - If the player logs in while edits are pending, GUIs close and data is written before login continues
  - Data from an older version or with unreadable items is opened read-only
- **Mobs**:
  - Equipment (head, chest, legs, feet, hands, body armor, saddle) and enderman carried block
  - Inventories of villagers, donkeys, mules, horses, llamas, allays and more
  - Health, max health, location and teleport
  - Attributes: name visibility, glowing, AI, invulnerable, silent, gravity, persistence, baby, sitting, owner, wool/collar color, villager profession/type/level, cat/wolf/fox/rabbit/salmon/axolotl/parrot/llama/horse/mooshroom/cow/pig/chicken/frog variants, panda genes, charged creeper, goat horns, bee nectar and more
- **Audit log (`audit-log`)**:
  - Records who changed which slot or value of whom, with before/after
  - `plugins/CustomInvAudit/logs/yyyy-MM-dd.log`, batched async writes, old files removed after `keep-days`
- **Protection**:
  - Players with `custominvaudit.exempt` cannot be viewed or edited; offline players use the `exempt-offline-players` list
- **GUI rules**:
  - Chest GUIs only, buttons respond to left click only; holders are bound to this plugin and GUI items carry a PDC signature
- **Update checker**:
  - Async GitHub Releases check on startup, admin reminder 3 seconds after login, 10-minute cache

## Commands & Permissions

The primary command is `/custominvaudit`.

| Command | Description | Executor | Permission Node | Default |
|---------|-------------|----------|-----------------|---------|
| `/ca inv <player>` | View/edit a player's inventory (offline too) | Player only | `custominvaudit.inventory.view` | OP |
| `/ca ec [player]` | Open an ender chest (empty = your own) | Player only | `custominvaudit.enderchest.self` / `.other` | OP |
| `/ca entity [UUID]` | View/edit a mob (empty = the mob you look at) | Player only | `custominvaudit.entity.view` | OP |
| `/ca version` | Check GitHub for updates | Player / Console | `custominvaudit.version` | OP |
| `/ca reload` | Reload and fill in `config.yml` and `messages.yml` | Player / Console | `custominvaudit.reload` | OP |

> Command alias: `/ca` equals `/custominvaudit`.
>
> Other permissions:
>
> - `custominvaudit.inventory.modify`: edit, clear and force-drop player inventories
> - `custominvaudit.enderchest.modify`: edit other players' ender chests
> - `custominvaudit.offline`: open offline players
> - `custominvaudit.stats.modify`: edit health, XP, food and other stats
> - `custominvaudit.teleport`: use teleport buttons
> - `custominvaudit.entity.modify`: edit mob equipment, inventory and attributes
> - `custominvaudit.exempt`: cannot be viewed or edited by others (nobody by default)
> - `custominvaudit.bypass.exempt`: ignore exempt (nobody by default)

## Usage Examples

### 1. Check an offline player's inventory

1. Make sure you have `custominvaudit.inventory.view` and `custominvaudit.offline`
2. Run the command; the file is read in the background and the GUI opens
3. Take or add items; the data is written back after you close the GUI

```bash
/ca inv Steve
```

### 2. Change a villager's profession

Look at the villager, run the command, then click Attributes → Profession → pick one.

```bash
/ca entity
```

### 3. Common situations

- The player logs in while you have the offline GUI open: it closes, and your edits are saved before they join
- The player's data is from an older version: read-only until they log in once
- The target has the exempt permission: you are told they are protected