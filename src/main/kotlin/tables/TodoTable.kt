package org.delcom.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object TodoTable : UUIDTable("todos") {
    val userId = uuid("user_id")
    val title = varchar("title", 100)
    val description = text("description")
    val cover = text("cover").nullable()
    val isDone = bool("is_done")
    // Tambahkan kolom urgency dengan default "Low"
    val urgency = varchar("urgency", 10).default("Low")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}