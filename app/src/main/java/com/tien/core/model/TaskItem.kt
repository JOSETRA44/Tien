package com.tien.core.model

data class TaskItem(
    val id: Long,
    val title: String,
    val details: String,
    val dueAt: Long,
    val createdAt: Long,
    val priority: Int,
    val isDone: Boolean
)
