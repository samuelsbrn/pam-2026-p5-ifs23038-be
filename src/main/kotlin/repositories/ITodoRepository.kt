package org.delcom.repositories

import org.delcom.entities.Todo

interface ITodoRepository {
    suspend fun getAll(
        userId: String,
        search: String,
        isDone: Boolean?,
        urgency: String?,
        page: Int,
        perPage: Int
    ): List<Todo>

    suspend fun getById(todoId: String): Todo?
    suspend fun create(todo: Todo): String
    suspend fun update(userId: String, todoId: String, newTodo: Todo): Boolean
    suspend fun delete(userId: String, todoId: String): Boolean
}