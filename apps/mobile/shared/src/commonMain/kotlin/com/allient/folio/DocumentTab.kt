package com.allient.folio

/** Presentation metadata shared by book tabs and recently opened note tabs. */
data class DocumentTab(val id: String, val title: String, val subtitle: String, val openedAt: Long = 0, val deletedAt: Long = 0)

fun recentDocumentTabs(items: List<DocumentTab>, currentId: String, closedIds: Set<String>, limit: Int = 12): List<DocumentTab> =
    items.filter { it.deletedAt == 0L && (it.id == currentId || (it.openedAt > 0 && it.id !in closedIds)) }
        .sortedWith(compareByDescending<DocumentTab> { it.id == currentId }.thenByDescending { it.openedAt })
        .take(limit.coerceAtLeast(1))

/**
 * Keeps tab positions stable: tabs stay where they are when selected, new tabs go to the end,
 * and the oldest tabs drop off once [limit] is exceeded. An empty [previous] order is seeded
 * from [recentDocumentTabs].
 */
fun stableDocumentTabOrder(previous: List<String>, items: List<DocumentTab>, currentId: String, closedIds: Set<String>, limit: Int = 12): List<String> {
    val max = limit.coerceAtLeast(1)
    if (previous.isEmpty()) return recentDocumentTabs(items, currentId, closedIds, max).map { it.id }
    val available = items.filter { it.deletedAt == 0L && (it.id == currentId || it.id !in closedIds) }.map { it.id }.toSet()
    val order = previous.distinct().filter { it in available }.toMutableList()
    if (currentId in available && currentId !in order) order += currentId
    while (order.size > max) order.remove(order.first { it != currentId })
    return order
}
