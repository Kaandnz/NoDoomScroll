package com.nodoomscroll.app.blocker

import java.util.Locale
import kotlin.math.abs

/**
 * Pure-Kotlin structural analyzer for Facebook (`com.facebook.katana` / `com.facebook.lite`)
 * accessibility trees to distinguish full-screen Reels viewers from Home Feed, Stories,
 * Profiles, and Photo Viewers.
 */
object FacebookAccessibilityAnalyzer {

  private enum class MainNavCategory {
    HOME_FEED,
    FRIENDS,
    MARKETPLACE,
    NOTIFICATIONS,
    MENU,
    REELS_OR_VIDEO,
  }

  private enum class ProfileSubTabCategory {
    POSTS,
    PHOTOS,
    ABOUT,
    MENTIONS,
    FOLLOWERS,
    EVENTS,
    REVIEWS,
  }

  private enum class ActionSlot {
    LIKE,
    COMMENT,
    SHARE,
    SAVE_OR_MORE,
    AUDIO,
  }

  private data class RailCandidate(
    val slot: ActionSlot,
    val centerX: Int,
    val centerY: Int,
  )

  private val whitespaceRegex = Regex("\\s+")
  private val reelWordRegex = Regex("\\breels?\\b")

  private val nonReelsSelectedMainTabs =
    setOf(
      "feed",
      "home",
      "news feed",
      "friends",
      "marketplace",
      "notifications",
      "menu",
      "groups",
      "gaming",
      "dating",
      "akis",
      "akış",
      "ana sayfa",
      "haber kaynagi",
      "haber kaynağı",
      "arkadaslar",
      "arkadaşlar",
      "bildirimler",
      "menü",
      "gruplar",
      "oyun",
    )

  private val mainNavCategoryKeywords: List<Pair<MainNavCategory, Set<String>>> =
    listOf(
      MainNavCategory.HOME_FEED to
        setOf("feed", "home", "news feed", "akış", "akis", "ana sayfa", "haber kaynağı", "haber kaynagi"),
      MainNavCategory.FRIENDS to setOf("friends", "arkadaşlar", "arkadaslar"),
      MainNavCategory.MARKETPLACE to setOf("marketplace"),
      MainNavCategory.NOTIFICATIONS to setOf("notifications", "bildirimler"),
      MainNavCategory.MENU to setOf("menu", "menü"),
      MainNavCategory.REELS_OR_VIDEO to setOf("reels", "video", "videolar", "watch"),
    )

  private val profileSubTabCategoryKeywords: List<Pair<ProfileSubTabCategory, Set<String>>> =
    listOf(
      ProfileSubTabCategory.POSTS to setOf("posts", "gönderiler", "gonderiler"),
      ProfileSubTabCategory.PHOTOS to setOf("photos", "fotoğraflar", "fotograflar"),
      ProfileSubTabCategory.ABOUT to setOf("about", "hakkında", "hakkinda"),
      ProfileSubTabCategory.MENTIONS to setOf("mentions", "bahsetmeler"),
      ProfileSubTabCategory.FOLLOWERS to setOf("followers", "takipçiler", "takipciler"),
      ProfileSubTabCategory.EVENTS to setOf("events", "etkinlikler"),
      ProfileSubTabCategory.REVIEWS to setOf("reviews", "değerlendirmeler", "degerlendirmeler"),
    )

  private val likePrefixes =
    setOf(
      "like",
      "unlike",
      "beğen",
      "begen",
      "beğenmekten vazgeç",
      "begenmekten vazgec",
      "react",
      "ifade bırak",
      "ifade birak",
      "like button",
      "beğen düğmesi",
      "begen dugmesi",
      "like reel",
      "reels videosunu beğen",
      "reels videosunu begen",
    )
  private val likeExclusions = listOf("more like this", "bunun gibi")

  private val commentPrefixes =
    setOf(
      "comment",
      "comments",
      "yorum",
      "yorum yap",
      "yorumlar",
      "comment button",
      "yorum düğmesi",
      "yorum dugmesi",
      "comment on reel",
      "reels videosuna yorum",
    )
  private val commentExclusions = listOf("write a comment", "yorum yaz")

  private val sharePrefixes =
    setOf(
      "share",
      "paylaş",
      "paylas",
      "send",
      "gönder",
      "gonder",
      "share button",
      "paylaş düğmesi",
      "paylas dugmesi",
      "share reel",
      "reels videosunu paylaş",
      "reels videosunu paylas",
      "send reel",
      "reels videosunu gönder",
      "reels videosunu gonder",
    )
  private val shareExclusions =
    listOf("send message", "mesaj gönder", "mesaj gonder", "hikayeye yanıt", "hikayeye yanit", "reply to story")

  private val saveOrMorePrefixes =
    setOf(
      "more options",
      "daha fazla seçenek",
      "daha fazla secenek",
      "more",
      "daha fazla",
      "save",
      "kaydet",
      "save reel",
      "reels videosunu kaydet",
      "options",
      "seçenekler",
      "secenekler",
    )

  private val audioPrefixes =
    setOf(
      "audio",
      "original audio",
      "orijinal ses",
      "ses",
      "ses sayfası",
      "ses sayfasi",
      "music",
      "müzik",
      "muzik",
      "remix",
      "remiksle",
    )

  private val playbackPrefixes =
    setOf(
      "pause",
      "play",
      "mute",
      "unmute",
      "duraklat",
      "oynat",
      "sesi aç",
      "sesi ac",
      "sesi kapat",
      "sessize al",
    )

  private val explicitReelViewerPhrases =
    listOf(
      "pause reel",
      "play reel",
      "mute reel",
      "unmute reel",
      "like reel",
      "unlike reel",
      "comment on reel",
      "share reel",
      "send reel",
      "save reel",
      "more options for this reel",
      "remix this reel",
      "remix reel",
      "reel player",
      "reels player",
      "next reel",
      "swipe up for next reel",
      "reels videosunu duraklat",
      "reels videosunu oynat",
      "reels videosunu beğen",
      "reels videosunu begen",
      "reels videosuna yorum",
      "reels videosunu paylaş",
      "reels videosunu paylas",
      "reels videosunu kaydet",
      "reels videosunu gönder",
      "reels videosunu gonder",
      "bu reels videosu için daha fazla",
      "bu reels videosu icin daha fazla",
      "bu reels videosunu remiksle",
      "reels remiksle",
      "sonraki reels",
    )

  private val originalAudioPhrases =
    listOf(
      "original audio",
      "orijinal ses",
      "audio track",
      "view audio",
      "use audio",
      "sesi kullan",
      "ses sayfası",
      "ses sayfasi",
      "sesi görüntüle",
      "sesi goruntule",
    )

  private val createReelPhrases =
    listOf(
      "create reel",
      "create a reel",
      "reel oluştur",
      "reel olustur",
      "reels oluştur",
      "reels olustur",
      "reels videosu oluştur",
      "reels videosu olustur",
    )

  private val topCameraLabels = setOf("camera", "kamera", "create", "oluştur", "olustur")

  private val feedClickDismissLabels =
    setOf(
      "hide",
      "gizle",
      "close",
      "kapat",
      "not interested",
      "ilgilenmiyorum",
      "see fewer",
      "daha az göster",
      "daha az goster",
      "dismiss",
      "more options",
      "daha fazla seçenek",
      "daha fazla secenek",
      "menu",
      "menü",
      "x",
    )

  private val coreEngagementSlots = setOf(ActionSlot.LIKE, ActionSlot.COMMENT, ActionSlot.SHARE)

  /**
   * Normalizes Turkish and English labels deterministically without system-locale pitfalls.
   */
  fun normalizeLabel(raw: String): String {
    if (raw.isEmpty()) return ""
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
      when (ch) {
        'İ', 'I', 'ı' -> sb.append('i')
        else -> sb.append(ch)
      }
    }
    return sb
      .toString()
      .lowercase(Locale.ROOT)
      .replace("\u0307", "")
      .trim()
      .replace(whitespaceRegex, " ")
  }

  /**
   * Analyzes visible Facebook [ObservedNode]s and geometry to detect full-screen Reels viewers.
   */
  fun analyze(
    nodes: List<ObservedNode>,
    screenBounds: NodeBounds,
    recentFeedReelClick: Boolean = false,
  ): FacebookViewerSignals {
    var hasSelectedNonReelsMainTab = false
    val topBarNavCategories = linkedSetOf<MainNavCategory>()
    val bottomBarNavCategories = linkedSetOf<MainNavCategory>()
    val profileSubTabCategories = linkedSetOf<ProfileSubTabCategory>()
    val railCandidatesBySlot = ActionSlot.entries.associateWith { mutableListOf<RailCandidate>() }
    val explicitLabels = linkedSetOf<String>()
    val supportingLabels = linkedSetOf<String>()
    var hasPlayerOrEngagementControl = false

    for (node in nodes) {
      if (node.className.contains("SeekBar", ignoreCase = true)) {
        hasPlayerOrEngagementControl = true
      }

      val normalizedLabels =
        listOf(node.contentDescription, node.text)
          .map(::normalizeLabel)
          .filter { it.isNotEmpty() }
          .distinct()

      for (label in normalizedLabels) {
        if (label.length <= 45) {
          if (node.isSelected && nonReelsSelectedMainTabs.any { matchesTokenPrefix(label, it) }) {
            hasSelectedNonReelsMainTab = true
          }
          if (screenBounds.isValid && node.bounds.isValid) {
            val isTopBar =
              node.bounds.centerY <= screenBounds.top + (screenBounds.height * 0.16).toInt()
            val isBottomBar =
              node.bounds.centerY >= screenBounds.bottom - (screenBounds.height * 0.14).toInt()
            if (isTopBar || isBottomBar) {
              for ((category, keywords) in mainNavCategoryKeywords) {
                if (keywords.any { matchesTokenPrefix(label, it) }) {
                  if (isTopBar) topBarNavCategories.add(category)
                  if (isBottomBar) bottomBarNavCategories.add(category)
                }
              }
            }
          }
        }

        if (label.length <= 28) {
          for ((category, keywords) in profileSubTabCategoryKeywords) {
            if (keywords.any { matchesTokenPrefix(label, it) }) {
              profileSubTabCategories.add(category)
            }
          }
        }

        if (label.length <= 80) {
          val explicitMatch = explicitReelViewerPhrases.firstOrNull { label.contains(it) }
          if (explicitMatch != null) {
            explicitLabels.add(explicitMatch)
            hasPlayerOrEngagementControl = true
          }

          if (originalAudioPhrases.any { label.contains(it) }) {
            supportingLabels.add("original_audio")
            hasPlayerOrEngagementControl = true
          }
          if (createReelPhrases.any { label.contains(it) }) {
            supportingLabels.add("create_reel")
          }
          if (label == "remix" || label == "remiksle" || label.contains("remix this") || label.contains("remiksle")) {
            supportingLabels.add("remix")
          }

          val isInTopBand =
            !screenBounds.isValid ||
              (node.bounds.isValid &&
                node.bounds.centerY <= screenBounds.top + (screenBounds.height * 0.22).toInt())
          if (isInTopBand) {
            if (label == "reels" || label == "reels videosu") {
              supportingLabels.add("top_reels_header")
            }
            if (label in topCameraLabels) {
              supportingLabels.add("top_camera")
            }
          }
        }

        if (label.length <= 60) {
          val slot = classifyActionSlot(label)
          if (slot != null) {
            if (slot != ActionSlot.SAVE_OR_MORE) {
              hasPlayerOrEngagementControl = true
            }
            if (isEligibleRightRailBounds(node.bounds, screenBounds)) {
              val list = railCandidatesBySlot.getValue(slot)
              if (list.size < MAX_RAIL_CANDIDATES_PER_SLOT) {
                list.add(RailCandidate(slot = slot, centerX = node.bounds.centerX, centerY = node.bounds.centerY))
              }
            }
          } else if (playbackPrefixes.any { matchesTokenPrefix(label, it) }) {
            hasPlayerOrEngagementControl = true
          }
        }
      }
    }

    val hasMainFeedTabBar =
      hasSelectedNonReelsMainTab ||
        topBarNavCategories.size >= 3 ||
        bottomBarNavCategories.size >= 3

    val hasProfileSubTabs = profileSubTabCategories.size >= 2

    val hasVerticalRightActionRail =
      screenBounds.isValid && detectVerticalRightActionRail(railCandidatesBySlot, screenBounds)

    return FacebookViewerSignals(
      hasMainFeedTabBar = hasMainFeedTabBar,
      hasProfileSubTabs = hasProfileSubTabs,
      hasVerticalRightActionRail = hasVerticalRightActionRail,
      explicitReelViewerLabels = explicitLabels,
      supportingReelLabels = supportingLabels,
      hasPlayerOrEngagementControl = hasPlayerOrEngagementControl,
      recentFeedReelClick = recentFeedReelClick,
    )
  }

  /**
   * Returns true if a click event's own or contextual labels indicate tapping a Reel in the feed,
   * while rejecting dismiss/hide/options controls.
   */
  fun isFeedReelClick(
    selfLabels: List<String>,
    contextLabels: List<String>,
  ): Boolean {
    val normalizedSelf = selfLabels.map(::normalizeLabel).filter { it.isNotEmpty() }
    for (selfLabel in normalizedSelf) {
      if (feedClickDismissLabels.any { matchesTokenPrefix(selfLabel, it) || selfLabel.contains(it) }) {
        return false
      }
    }

    val allLabels = normalizedSelf + contextLabels.map(::normalizeLabel).filter { it.isNotEmpty() }
    return allLabels.any { label ->
      label.length <= 140 && reelWordRegex.containsMatchIn(label)
    }
  }

  private fun classifyActionSlot(label: String): ActionSlot? {
    if (likeExclusions.none { label.contains(it) } && likePrefixes.any { matchesTokenPrefix(label, it) }) {
      return ActionSlot.LIKE
    }
    if (
      commentExclusions.none { label.contains(it) } &&
        commentPrefixes.any { matchesTokenPrefix(label, it) }
    ) {
      return ActionSlot.COMMENT
    }
    if (shareExclusions.none { label.contains(it) } && sharePrefixes.any { matchesTokenPrefix(label, it) }) {
      return ActionSlot.SHARE
    }
    if (saveOrMorePrefixes.any { matchesTokenPrefix(label, it) }) {
      return ActionSlot.SAVE_OR_MORE
    }
    if (audioPrefixes.any { matchesTokenPrefix(label, it) }) {
      return ActionSlot.AUDIO
    }
    return null
  }

  private fun isEligibleRightRailBounds(
    bounds: NodeBounds,
    screenBounds: NodeBounds,
  ): Boolean {
    if (!screenBounds.isValid || !bounds.isValid) return false
    val maxWidth = (screenBounds.width * 0.45).toInt()
    val minCenterX = screenBounds.left + (screenBounds.width * 0.68).toInt()
    val minCenterY = screenBounds.top + (screenBounds.height * 0.25).toInt()
    return bounds.width <= maxWidth && bounds.centerX >= minCenterX && bounds.centerY >= minCenterY
  }

  private fun detectVerticalRightActionRail(
    candidatesBySlot: Map<ActionSlot, List<RailCandidate>>,
    screenBounds: NodeBounds,
  ): Boolean {
    val populatedSlots = ActionSlot.entries.filter { candidatesBySlot.getValue(it).isNotEmpty() }
    if (populatedSlots.size < 3) return false

    val maxDeltaX = (screenBounds.width * 0.22).toInt()
    val minSpanY = (screenBounds.height * 0.08).toInt()
    val minPairStepY = (screenBounds.height * 0.03).toInt()

    for (i in 0 until populatedSlots.size - 2) {
      for (j in i + 1 until populatedSlots.size - 1) {
        for (k in j + 1 until populatedSlots.size) {
          val slotA = populatedSlots[i]
          val slotB = populatedSlots[j]
          val slotC = populatedSlots[k]
          val coreCount =
            (if (slotA in coreEngagementSlots) 1 else 0) +
              (if (slotB in coreEngagementSlots) 1 else 0) +
              (if (slotC in coreEngagementSlots) 1 else 0)
          if (coreCount < 2) continue

          for (a in candidatesBySlot.getValue(slotA)) {
            for (b in candidatesBySlot.getValue(slotB)) {
              if (abs(a.centerY - b.centerY) < minPairStepY) continue
              for (c in candidatesBySlot.getValue(slotC)) {
                if (abs(a.centerY - c.centerY) < minPairStepY) continue
                if (abs(b.centerY - c.centerY) < minPairStepY) continue

                val minX = minOf(a.centerX, b.centerX, c.centerX)
                val maxX = maxOf(a.centerX, b.centerX, c.centerX)
                if (maxX - minX > maxDeltaX) continue

                val minY = minOf(a.centerY, b.centerY, c.centerY)
                val maxY = maxOf(a.centerY, b.centerY, c.centerY)
                if (maxY - minY >= minSpanY) {
                  return true
                }
              }
            }
          }
        }
      }
    }
    return false
  }

  private fun matchesTokenPrefix(
    normalized: String,
    keyword: String,
  ): Boolean {
    if (normalized == keyword) return true
    if (!normalized.startsWith(keyword)) return false
    val nextChar = normalized[keyword.length]
    return nextChar == ' ' || nextChar == ',' || nextChar == '.' || nextChar == '(' || nextChar == ':'
  }

  private const val MAX_RAIL_CANDIDATES_PER_SLOT = 3
}
