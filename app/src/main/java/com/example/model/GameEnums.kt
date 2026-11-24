package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

/**
 * All 16 DenizCraft Ranks (also default across independent networks).
 * Ordered from highest authority to base player rank.
 */
enum class Rank(
    val code: String,
    val displayName: String,
    val tagLabel: String,
    val tagBg: Color,
    val tagTextColor: Color,
    val nameColor: Color,
    val plusText: String = "",
    val plusColor: Color = Color.Transparent,
    val isRgb: Boolean = false,
    val isBicolorWhiteBlack: Boolean = false,
    val isYouTubeRedWhite: Boolean = false,
    val isTikTokPinkYellow: Boolean = false,
    val hierarchyOrder: Int
) {
    KURUCU(
        code = "KURUCU",
        displayName = "Kurucu",
        tagLabel = "KURUCU",
        tagBg = RankDarkRed,
        tagTextColor = Color.White,
        nameColor = RankLightRed,
        hierarchyOrder = 1
    ),
    YONETICI(
        code = "YONETICI",
        displayName = "Yönetici",
        tagLabel = "YÖNETİCİ",
        tagBg = RankDarkRed,
        tagTextColor = Color.White,
        nameColor = RankLightRed,
        hierarchyOrder = 2
    ),
    MODERATOR(
        code = "MODERATOR",
        displayName = "Moderatör",
        tagLabel = "MOD",
        tagBg = RankDarkPurple,
        tagTextColor = Color.White,
        nameColor = RankLightPurple,
        hierarchyOrder = 3
    ),
    YARDIMCI(
        code = "YARDIMCI",
        displayName = "Yardımcı",
        tagLabel = "YARDIMCI",
        tagBg = RankDarkGreen,
        tagTextColor = Color.White,
        nameColor = RankLightGreen,
        hierarchyOrder = 4
    ),
    MIMAR(
        code = "MIMAR",
        displayName = "Mimar",
        tagLabel = "MİMAR",
        tagBg = RankTurquoise,
        tagTextColor = Color.White,
        nameColor = RankAqua,
        hierarchyOrder = 5
    ),
    AKADEMI(
        code = "AKADEMI",
        displayName = "Akademi",
        tagLabel = "AKADEMİ",
        tagBg = RankOrange,
        tagTextColor = Color.White,
        nameColor = RankYellow,
        hierarchyOrder = 6
    ),
    MEDYA(
        code = "MEDYA",
        displayName = "Medya",
        tagLabel = "MEDYA",
        tagBg = RankPink,
        tagTextColor = Color.White,
        nameColor = Color.White,
        hierarchyOrder = 7
    ),
    YAYINCI(
        code = "YAYINCI",
        displayName = "Yayıncı",
        tagLabel = "YAYINCI",
        tagBg = Color(0xFF111111),
        tagTextColor = Color.White,
        nameColor = Color.White,
        isBicolorWhiteBlack = true,
        hierarchyOrder = 8
    ),
    YOUTUBE(
        code = "YOUTUBE",
        displayName = "YouTube",
        tagLabel = "YOUTUBE",
        tagBg = Color(0xFFD32F2F),
        tagTextColor = Color.White,
        nameColor = Color.White,
        isYouTubeRedWhite = true,
        hierarchyOrder = 9
    ),
    TIKTOK(
        code = "TIKTOK",
        displayName = "TikTok",
        tagLabel = "TIKTOK",
        tagBg = Color(0xFFFF2A7A),
        tagTextColor = Color(0xFFFFEB3B),
        nameColor = Color.White,
        isTikTokPinkYellow = true,
        hierarchyOrder = 10
    ),
    DVIP_PP(
        code = "DVIP_PP",
        displayName = "DVIP++",
        tagLabel = "DVIP++",
        tagBg = Color(0xFF7B1FA2),
        tagTextColor = Color.White,
        nameColor = Color.White,
        isRgb = true,
        hierarchyOrder = 11
    ),
    MVIP_P(
        code = "MVIP_P",
        displayName = "MVIP+",
        tagLabel = "MVIP",
        tagBg = RankBrightGreen,
        tagTextColor = Color.White,
        nameColor = Color.White,
        plusText = "+",
        plusColor = Color(0xFFFF1744),
        hierarchyOrder = 12
    ),
    MVIP(
        code = "MVIP",
        displayName = "MVIP",
        tagLabel = "MVIP",
        tagBg = RankBrightGreen,
        tagTextColor = Color.White,
        nameColor = Color.White,
        hierarchyOrder = 13
    ),
    VIP_P(
        code = "VIP_P",
        displayName = "VIP+",
        tagLabel = "VIP",
        tagBg = RankGold,
        tagTextColor = Color(0xFF1A1A1A),
        nameColor = Color.White,
        plusText = "+",
        plusColor = Color(0xFFFFEB3B),
        hierarchyOrder = 14
    ),
    VIP(
        code = "VIP",
        displayName = "VIP",
        tagLabel = "VIP",
        tagBg = RankGold,
        tagTextColor = Color(0xFF1A1A1A),
        nameColor = Color.White,
        hierarchyOrder = 15
    ),
    OYUNCU(
        code = "OYUNCU",
        displayName = "Oyuncu",
        tagLabel = "",
        tagBg = Color.Transparent,
        tagTextColor = RankPlayerGray,
        nameColor = RankPlayerGray,
        hierarchyOrder = 16
    );

    // --- EXACT STAFF PANEL RBAC PERMISSIONS ---
    val canAccessStaffPanel: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR, YARDIMCI)

    val canAccessManagementTab: Boolean
        get() = this in setOf(KURUCU, YONETICI)

    val canHandleTickets: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR, YARDIMCI)

    val canHandleReports: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR)

    val canBan: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR)

    val canMute: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR, YARDIMCI)

    val canWarn: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR)

    val canSpectateMatch: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR)

    val canBuildAndDesign: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR, YARDIMCI, MIMAR, AKADEMI)

    val hasFullDesignTools: Boolean
        get() = this in setOf(KURUCU, YONETICI, MODERATOR, YARDIMCI, MIMAR)

    val isProtectedFromPunishment: Boolean
        get() = this == KURUCU

    val canManageMaintenance: Boolean
        get() = this == KURUCU || this == YONETICI

    val canDeleteNetwork: Boolean
        get() = this == KURUCU

    fun canAssignRank(targetRank: Rank, currentTargetRank: Rank, isSelf: Boolean = false): Boolean {
        if (isSelf && currentTargetRank == KURUCU && targetRank != KURUCU) return false
        return when (this) {
            KURUCU -> true
            YONETICI -> targetRank != KURUCU && targetRank != YONETICI &&
                currentTargetRank != KURUCU && currentTargetRank != YONETICI
            else -> false
        }
    }

    fun canPunish(targetRank: Rank, isSelf: Boolean = false): Boolean {
        if (isSelf && targetRank == KURUCU) return false
        return when (this) {
            KURUCU -> true
            YONETICI -> targetRank != KURUCU && targetRank != YONETICI
            MODERATOR, YARDIMCI -> targetRank != KURUCU
            else -> false
        }
    }

    companion object {
        fun fromCode(code: String?): Rank =
            entries.firstOrNull { it.code == code } ?: OYUNCU
    }
}

/**
 * 40 Levels: Kömür 1..5 -> Taş 1..5 -> Demir 1..5 -> Altın 1..5 -> Elmas 1..5 -> Netherite 1..5 -> Usta 1..5 -> Pro 1..5
 */
object LevelSystem {
    data class TierInfo(
        val name: String,
        val subLevel: Int,
        val fullName: String,
        val color: Color,
        val levelNumber: Int
    )

    private val tierNames = listOf(
        "Kömür" to Color(0xFF616161),
        "Taş" to Color(0xFF9E9E9E),
        "Demir" to Color(0xFFCFD8DC),
        "Altın" to Color(0xFFFFCA28),
        "Elmas" to Color(0xFF00E5FF),
        "Netherite" to Color(0xFF8D6E63),
        "Usta" to Color(0xFFAB47BC),
        "Pro" to Color(0xFFFF5252)
    )

    fun getTierInfo(level: Int): TierInfo {
        val clamped = level.coerceIn(1, 40)
        val tierIdx = (clamped - 1) / 5
        val sub = ((clamped - 1) % 5) + 1
        val (name, color) = tierNames[tierIdx]
        return TierInfo(
            name = name,
            subLevel = sub,
            fullName = "$name $sub",
            color = color,
            levelNumber = clamped
        )
    }

    fun xpForNextLevel(level: Int): Long = (level.coerceIn(1, 40) * 150L)
}

enum class ServerType(
    val code: String,
    val displayName: String,
    val subtitle: String,
    val waitingLobbyMap: String,
    val matchMaps: List<String>,
    val accentColor: Color,
    val defaultMaxPlayers: Int
) {
    LOBI(
        code = "LOBI",
        displayName = "Lobi",
        subtitle = "Network'ün bekleme ve buluşma alanı",
        waitingLobbyMap = "DenizLimanı İskelesi",
        matchMaps = listOf("DenizLimanı Ana Meydan", "Mercan Adası Hub"),
        accentColor = Color(0xFF00BCD4),
        defaultMaxPlayers = 24
    ),
    BEDWARS(
        code = "BEDWARS",
        displayName = "Bedwars",
        subtitle = "Yatakları kır, takımını koru ve maçı kazan!",
        waitingLobbyMap = "Gökyüzü İskelesi Bekleme",
        matchMaps = listOf("Zümrüt Adalar", "Mercan Kalesi"),
        accentColor = Color(0xFFE74C3C),
        defaultMaxPlayers = 8
    ),
    SKYWARS(
        code = "SKYWARS",
        displayName = "Skywars",
        subtitle = "Sandıkları yağmala, herkesi yen ve maçı kazan!",
        waitingLobbyMap = "Bulut Tapınağı Bekleme",
        matchMaps = listOf("Korsan Koyu Adaları", "Volkanik Zirveler"),
        accentColor = Color(0xFF3498DB),
        defaultMaxPlayers = 12
    ),
    SKYBLOCK(
        code = "SKYBLOCK",
        displayName = "Skyblock",
        subtitle = "Gökyüzü adanı büyüt, çiftlik ve maden kur!",
        waitingLobbyMap = "Ada Limanı Bekleme",
        matchMaps = listOf("Başlangıç Adası", "Zengin Maden Adası"),
        accentColor = Color(0xFF2ECC71),
        defaultMaxPlayers = 16
    ),
    SURVIVAL(
        code = "SURVIVAL",
        displayName = "Survival",
        subtitle = "Kaynak topla, yaratıklarla savaş ve hayatta kal!",
        waitingLobbyMap = "Vahşi Kamp Bekleme",
        matchMaps = listOf("Orman Diyarı (SMP)", "Mağara Vadisi"),
        accentColor = Color(0xFFF39C12),
        defaultMaxPlayers = 20
    ),
    BUILD_BATTLE(
        code = "BUILD_BATTLE",
        displayName = "BuildBattle",
        subtitle = "Belirli sürede temaya en güzel yapıyı inşa et!",
        waitingLobbyMap = "Mimarlar Meydanı Bekleme",
        matchMaps = listOf("Yaratıcı Arenalar", "Büyük Heykel Alanı"),
        accentColor = Color(0xFF9B59B6),
        defaultMaxPlayers = 8
    ),
    CREATIVE(
        code = "CREATIVE",
        displayName = "Creative",
        subtitle = "Hesabına kayıtlı arsana istediğin evi yap!",
        waitingLobbyMap = "Arsa Merkezi Bekleme",
        matchMaps = listOf("Kişisel Arsa (Kayıtlı)", "Dev Sanat Parseli"),
        accentColor = Color(0xFF1ABC9C),
        defaultMaxPlayers = 12
    );

    companion object {
        fun fromCode(code: String?): ServerType =
            entries.firstOrNull { it.code == code } ?: LOBI
    }
}

enum class GameMode(val code: String, val displayName: String) {
    SURVIVAL("SURVIVAL", "Hayatta Kalma"),
    CREATIVE("CREATIVE", "Yaratıcı"),
    ADVENTURE("ADVENTURE", "Macera"),
    SPECTATOR("SPECTATOR", "İzleyici");

    companion object {
        fun fromCode(code: String?): GameMode =
            entries.firstOrNull { it.code == code } ?: SURVIVAL
    }
}

enum class BlockType(
    val code: String,
    val displayName: String,
    val topColor: Color,
    val sideColor: Color,
    val frontColor: Color,
    val solid: Boolean = true,
    val breakTimeSec: Float = 0.35f
) {
    AIR("AIR", "Hava", Color.Transparent, Color.Transparent, Color.Transparent, solid = false),
    GRASS("GRASS", "Çimen", Color(0xFF55B948), Color(0xFF795548), Color(0xFF6D4C41)),
    DIRT("DIRT", "Toprak", Color(0xFF8D6E63), Color(0xFF6D4C41), Color(0xFF5D4037)),
    STONE("STONE", "Taş", Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF616161)),
    WOOD("WOOD", "Tahta", Color(0xFFC18E56), Color(0xFFA06F3B), Color(0xFF8C5D2D)),
    WOOL_CYAN("WOOL_CYAN", "Camgöbeği Yün", Color(0xFF26C6DA), Color(0xFF00ACC1), Color(0xFF00838F)),
    WOOL_RED("WOOL_RED", "Kırmızı Yün", Color(0xFFEF5350), Color(0xFFE53935), Color(0xFFC62828)),
    CLAY("CLAY", "Sert Kil", Color(0xFF0097A7), Color(0xFF00838F), Color(0xFF006064)),
    OBSIDIAN("OBSIDIAN", "Obsidyen", Color(0xFF311B92), Color(0xFF1A0E4D), Color(0xFF120836), breakTimeSec = 1.1f),
    DIAMOND("DIAMOND", "Elmas Blok", Color(0xFF4DD0E1), Color(0xFF00BCD4), Color(0xFF0097A7)),
    GOLD("GOLD", "Altın Blok", Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFF8F00)),
    IRON_BLOCK("IRON_BLOCK", "Demir Blok", Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF90A4AE)),
    BED("BED", "Yatak", Color(0xFFE53935), Color(0xFFFFFFFF), Color(0xFFC62828)),
    CHEST("CHEST", "Sandık", Color(0xFFD49B38), Color(0xFFA87221), Color(0xFF8C5D18)),
    LEAVES("LEAVES", "Yaprak", Color(0xFF388E3C), Color(0xFF2E7D32), Color(0xFF1B5E20)),
    WATER("WATER", "Su", Color(0xCC29B6F6), Color(0xCC039BE5), Color(0xCC0288D1), solid = false),
    SAND("SAND", "Kum", Color(0xFFF6E096), Color(0xFFE0C673), Color(0xFFC9AE5B)),
    BRICK("BRICK", "Tuğla", Color(0xFFC85A42), Color(0xFFA6422D), Color(0xFF8A3321)),
    GLASS("GLASS", "Cam", Color(0x99B2EBF2), Color(0x8880DEEA), Color(0x884DD0E1)),
    TNT("TNT", "TNT", Color(0xFFE53935), Color(0xFFF5F5F5), Color(0xFFD32F2F));

    companion object {
        fun fromCode(code: String?): BlockType =
            entries.firstOrNull { it.code == code } ?: STONE
    }
}

enum class MobType(
    val code: String,
    val displayName: String,
    val maxHp: Int,
    val hostile: Boolean,
    val bodyColor: Color,
    val headColor: Color
) {
    ZOMBI("ZOMBI", "Zombi", 20, true, Color(0xFF2E7D32), Color(0xFF4CAF50)),
    ISKELET("ISKELET", "İskelet", 16, true, Color(0xFFE0E0E0), Color(0xFFF5F5F5)),
    CREEPER("CREEPER", "Creeper", 18, true, Color(0xFF43A047), Color(0xFF66BB6A)),
    DOMUZ("DOMUZ", "Domuz", 10, false, Color(0xFFF48FB1), Color(0xFFF8BBD0)),
    KOYUN("KOYUN", "Koyun", 10, false, Color(0xFFFAFAFA), Color(0xFFD7CCC8)),
    DEMIR_GOLEM("DEMIR_GOLEM", "Demir Golem", 40, false, Color(0xFFCFD8DC), Color(0xFFB0BEC5));

    companion object {
        fun fromCode(code: String?): MobType =
            entries.firstOrNull { it.code == code } ?: ZOMBI
    }
}

enum class ItemCategory(val displayName: String) {
    BLOK("Bloklar"),
    SILAH("Silahlar"),
    ZIRH("Zırhlar"),
    ALET("Aletler"),
    YIYECEK("Yiyecekler"),
    OZEL("Özel Eşyalar")
}

data class GameItem(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val blockType: BlockType? = null,
    val attackDamage: Int = 1,
    val color: Color,
    val priceCoins: Int = 50,
    val priceIron: Int = 4,
    val priceGold: Int = 0,
    val foodRestore: Int = 0
)

object GameCatalog {
    val items: List<GameItem> = listOf(
        GameItem("wool_cyan", "Camgöbeği Yün", ItemCategory.BLOK, BlockType.WOOL_CYAN, 1, BlockType.WOOL_CYAN.topColor, 20, 4, 0),
        GameItem("wood", "Tahta Blok", ItemCategory.BLOK, BlockType.WOOD, 1, BlockType.WOOD.topColor, 25, 4, 0),
        GameItem("clay", "Sert Kil", ItemCategory.BLOK, BlockType.CLAY, 1, BlockType.CLAY.topColor, 35, 12, 0),
        GameItem("stone", "Taş Blok", ItemCategory.BLOK, BlockType.STONE, 1, BlockType.STONE.topColor, 30, 8, 0),
        GameItem("grass", "Çimen Blok", ItemCategory.BLOK, BlockType.GRASS, 1, BlockType.GRASS.topColor, 15, 2, 0),
        GameItem("dirt", "Toprak Blok", ItemCategory.BLOK, BlockType.DIRT, 1, BlockType.DIRT.topColor, 10, 2, 0),
        GameItem("sand", "Kum Blok", ItemCategory.BLOK, BlockType.SAND, 1, BlockType.SAND.topColor, 15, 2, 0),
        GameItem("leaves", "Yaprak Blok", ItemCategory.BLOK, BlockType.LEAVES, 1, BlockType.LEAVES.topColor, 15, 2, 0),
        GameItem("obsidian", "Obsidyen", ItemCategory.BLOK, BlockType.OBSIDIAN, 1, BlockType.OBSIDIAN.topColor, 120, 0, 8),
        GameItem("brick", "Tuğla", ItemCategory.BLOK, BlockType.BRICK, 1, BlockType.BRICK.topColor, 40, 8, 0),
        GameItem("glass", "Cam", ItemCategory.BLOK, BlockType.GLASS, 1, BlockType.GLASS.topColor, 30, 6, 0),
        GameItem("diamond_block", "Elmas Blok", ItemCategory.BLOK, BlockType.DIAMOND, 1, BlockType.DIAMOND.topColor, 200, 0, 10),
        GameItem("gold_block", "Altın Blok", ItemCategory.BLOK, BlockType.GOLD, 1, BlockType.GOLD.topColor, 150, 0, 6),
        GameItem("iron_block", "Demir Blok", ItemCategory.BLOK, BlockType.IRON_BLOCK, 1, BlockType.IRON_BLOCK.topColor, 110, 12, 0),
        GameItem("chest", "Sandık", ItemCategory.BLOK, BlockType.CHEST, 1, BlockType.CHEST.topColor, 60, 6, 0),
        GameItem("tnt", "TNT Patlayıcı", ItemCategory.OZEL, BlockType.TNT, 8, BlockType.TNT.topColor, 100, 0, 4),
        GameItem("wood_sword", "Tahta Kılıç", ItemCategory.SILAH, null, 4, Color(0xFFA06F3B), 30, 4, 0),
        GameItem("stone_sword", "Taş Kılıç", ItemCategory.SILAH, null, 6, Color(0xFF9E9E9E), 60, 10, 0),
        GameItem("iron_sword", "Demir Kılıç", ItemCategory.SILAH, null, 8, Color(0xFFECEFF1), 100, 0, 7),
        GameItem("diamond_sword", "Elmas Kılıç", ItemCategory.SILAH, null, 11, Color(0xFF00E5FF), 240, 0, 12),
        GameItem("pickaxe", "Elmas Kazma", ItemCategory.ALET, null, 3, Color(0xFF26C6DA), 90, 10, 0),
        GameItem("iron_armor", "Demir Zırh", ItemCategory.ZIRH, null, 1, Color(0xFFB0BEC5), 140, 0, 8),
        GameItem("apple", "Kırmızı Elma", ItemCategory.YIYECEK, null, 1, Color(0xFFE53935), 15, 2, 0, foodRestore = 4),
        GameItem("bread", "Taze Ekmek", ItemCategory.YIYECEK, null, 1, Color(0xFFD7A86E), 25, 3, 0, foodRestore = 6),
        GameItem("cooked_beef", "Pişmiş Biftek", ItemCategory.YIYECEK, null, 1, Color(0xFF8D4E32), 45, 0, 2, foodRestore = 8)
    )

    fun findById(id: String): GameItem? = items.firstOrNull { it.id == id }
}
