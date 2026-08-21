package com.tdpl.chat.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CharacterPersonality(
    val traits: List<String> = emptyList(),
    val relationships: String = "",
    val background: String = "",
    val speechStyle: String = ""
)

@Serializable
data class ExampleTurn(val role: Role, val content: String)

@Serializable
data class Character(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val avatarUrl: String? = null,
    val shortDescription: String = "",
    val personality: CharacterPersonality = CharacterPersonality(),
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogue: List<ExampleTurn> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Renders a Character to the same system-prompt template used at training
 * time (see character_app_spec.md §3) — keeping this identical to the
 * training-time template matters more than anything else here, since it's
 * what makes the model's learned roleplay behavior actually activate.
 */
fun Character.renderSystemPrompt(): String {
    val personalityParagraph = buildString {
        if (personality.traits.isNotEmpty()) {
            append("Sifat: ${personality.traits.joinToString(", ")}. ")
        }
        if (personality.background.isNotBlank()) append("${personality.background} ")
        if (personality.relationships.isNotBlank()) append(personality.relationships)
    }.trim()

    return buildString {
        appendLine("Kamu adalah $name dalam roleplay naratif.")
        appendLine()
        appendLine("Nama: $name")
        appendLine()
        appendLine("Deskripsi karakter:")
        appendLine(shortDescription)
        if (personalityParagraph.isNotBlank()) {
            appendLine()
            appendLine("Kepribadian:")
            appendLine(personalityParagraph)
        }
        if (personality.speechStyle.isNotBlank()) {
            appendLine()
            appendLine("Gaya bicara: ${personality.speechStyle}")
        }
        appendLine()
        appendLine("Situasi:")
        appendLine(scenario)
        appendLine()
        appendLine("Aturan roleplay:")
        appendLine("- Mulai dan lanjutkan adegan secara proaktif.")
        appendLine("- Gunakan *asterisk* untuk aksi, ekspresi, pikiran, dan deskripsi suasana.")
        appendLine("- Gunakan tanda kutip untuk ucapan karakter.")
        appendLine("- Jangan menggunakan format Assistant: atau User: dalam respons.")
        appendLine("- Jangan mengendalikan tindakan, pikiran, atau ucapan pengguna.")
        appendLine("- Jangan berbicara sebagai narator yang mengetahui semua hal.")
        appendLine("- Pertahankan kepribadian, hubungan, latar belakang, dan suasana cerita.")
        append("- Akhiri respons dengan situasi terbuka agar pengguna dapat melanjutkan kisah.")
    }
}
