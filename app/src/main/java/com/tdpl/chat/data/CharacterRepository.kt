package com.tdpl.chat.data

import android.content.Context
import com.tdpl.chat.model.Character
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Character library persistence — <filesDir>/characters.json. Local only. */
class CharacterRepository(context: Context) {

    private val file = File(context.filesDir, "characters.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<Character> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Character.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun saveAll(characters: List<Character>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(Character.serializer()), characters))
        }
    }
}
