package com.github.chrisvdalen.archunitgen.ui

import com.github.chrisvdalen.archunitgen.model.RuleSuggestion
import javax.swing.table.AbstractTableModel

/**
 * Table model that backs the rule-suggestion list in the tool window.
 * Columns: title | category | confidence | status
 */
class RuleSuggestionTableModel : AbstractTableModel() {

    private val suggestions = mutableListOf<RuleSuggestion>()

    companion object {
        private val COLUMNS = arrayOf("Rule", "Category", "Confidence", "Status")
    }

    override fun getRowCount(): Int = suggestions.size
    override fun getColumnCount(): Int = COLUMNS.size
    override fun getColumnName(col: Int): String = COLUMNS[col]
    override fun isCellEditable(row: Int, col: Int): Boolean = false

    override fun getValueAt(row: Int, col: Int): Any {
        val s = suggestions[row]
        return when (col) {
            0 -> s.title
            1 -> s.category.displayName
            2 -> s.confidence.displayName
            3 -> s.status.displayName
            else -> ""
        }
    }

    fun setSuggestions(newSuggestions: List<RuleSuggestion>) {
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        fireTableDataChanged()
    }

    fun getSuggestion(row: Int): RuleSuggestion? = suggestions.getOrNull(row)

    fun allSuggestions(): List<RuleSuggestion> = suggestions.toList()

    fun refreshRow(row: Int) = fireTableRowsUpdated(row, row)

    fun clear() {
        suggestions.clear()
        fireTableDataChanged()
    }
}
