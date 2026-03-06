package org.delcom.repositories

import org.delcom.dao.TodoDAO
import org.delcom.entities.Todo
import org.delcom.helpers.suspendTransaction
import org.delcom.helpers.todoDAOToModel
import org.delcom.tables.TodoTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.lowerCase
import java.util.*

class TodoRepository : ITodoRepository {
    // Tambahkan parameter urgency pada fungsi getAll
    override suspend fun getAll(
        userId: String,
        search: String,
        isDone: Boolean?,
        urgency: String?, // <-- Filter Urgency
        page: Int,
        perPage: Int
    ): List<Todo> = suspendTransaction {
        val limitCount = perPage
        val offsetCount = ((page - 1) * perPage).toLong()

        TodoDAO.find {
            var condition: Op<Boolean> = (TodoTable.userId eq UUID.fromString(userId))

            if (search.isNotBlank()) {
                val keyword = "%${search.lowercase()}%"
                condition = condition and (TodoTable.title.lowerCase() like keyword)
            }

            if (isDone != null) {
                condition = condition and (TodoTable.isDone eq isDone)
            }

            // Tambahkan kondisi filter urgency
            if (urgency != null) {
                condition = condition and (TodoTable.urgency eq urgency)
            }

            condition
        }
            // Mengurutkan berdasarkan urgency (Opsional: Jika Anda ingin Custom Sort bisa memanipulasi di sini, secara default SortOrder memproses alfabetikal H -> L -> M, Anda mungkin butuh case logic bila ingin urutan Low -> Med -> High)
            .orderBy(
                if (search.isNotBlank()) TodoTable.title to SortOrder.ASC
                else TodoTable.createdAt to SortOrder.DESC
            )
            .limit(limitCount)
            .offset(offsetCount)
            .map(::todoDAOToModel)
    }

    override suspend fun getById(todoId: String): Todo? = suspendTransaction {
        TodoDAO
            .find {
                (TodoTable.id eq UUID.fromString(todoId))
            }
            .limit(1)
            .map(::todoDAOToModel)
            .firstOrNull()
    }

    override suspend fun create(todo: Todo): String = suspendTransaction {
        val todoDAO = TodoDAO.new {
            userId = UUID.fromString(todo.userId)
            title = todo.title
            description = todo.description
            cover = todo.cover
            this.isDone = todo.isDone
            this.urgency = todo.urgency // <-- Simpan urgency
            createdAt = todo.createdAt
            updatedAt = todo.updatedAt
        }

        todoDAO.id.value.toString()
    }

    override suspend fun update(userId: String, todoId: String, newTodo: Todo): Boolean = suspendTransaction {
        val todoDAO = TodoDAO
            .find {
                (TodoTable.id eq UUID.fromString(todoId)) and
                        (TodoTable.userId eq UUID.fromString(userId))
            }
            .limit(1)
            .firstOrNull()

        if (todoDAO != null) {
            todoDAO.title = newTodo.title
            todoDAO.description = newTodo.description
            todoDAO.cover = newTodo.cover
            todoDAO.isDone = newTodo.isDone
            todoDAO.urgency = newTodo.urgency // <-- Update urgency
            todoDAO.updatedAt = newTodo.updatedAt
            true
        } else {
            false
        }
    }

    override suspend fun delete(userId: String, todoId: String): Boolean = suspendTransaction {
        val rowsDeleted = TodoTable.deleteWhere {
            (TodoTable.id eq UUID.fromString(todoId)) and
                    (TodoTable.userId eq UUID.fromString(userId))
        }
        rowsDeleted >= 1
    }
}