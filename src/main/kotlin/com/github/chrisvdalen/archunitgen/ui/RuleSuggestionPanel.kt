package com.github.chrisvdalen.archunitgen.ui

import com.github.chrisvdalen.archunitgen.config.ConfigLoader
import com.github.chrisvdalen.archunitgen.generator.ArchUnitTestGenerator
import com.github.chrisvdalen.archunitgen.generator.TestFileWriter
import com.github.chrisvdalen.archunitgen.generator.WriteResult
import com.github.chrisvdalen.archunitgen.model.RuleStatus
import com.github.chrisvdalen.archunitgen.service.AnalysisService
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer

/**
 * Main panel hosted inside the "ArchUnit Rules" tool window.
 *
 * Layout:
 *  ┌────────────────────────────────────────────────────┐
 *  │  [Analyse Project]   status label                  │  ← toolbar
 *  ├────────────────────────────────────────────────────┤
 *  │  Rule | Category | Confidence | Status             │  ← JBTable
 *  │  ...                                               │
 *  ├────────────────────────────────────────────────────┤
 *  │  [Accept All] [Accept Selected] [Ignore] [Generate]│  ← action bar
 *  └────────────────────────────────────────────────────┘
 */
class RuleSuggestionPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tableModel = RuleSuggestionTableModel()
    private val table = JBTable(tableModel)
    private val statusLabel = JLabel("Click 'Analyse Project' to start.")

    init {
        border = JBUI.Borders.empty(4)
        configureTable()
        add(buildToolbar(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(buildActionBar(), BorderLayout.SOUTH)
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private fun configureTable() {
        table.setShowGrid(true)
        table.rowHeight = 26
        table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        table.columnModel.apply {
            getColumn(0).preferredWidth = 340
            getColumn(1).preferredWidth = 160
            getColumn(2).preferredWidth = 90
            getColumn(3).preferredWidth = 90
        }

        // Colour status column to give a quick visual overview
        table.columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                text = value?.toString() ?: ""
                foreground = when (value?.toString()) {
                    "Accepted" -> java.awt.Color(0x2E7D32)
                    "Ignored" -> java.awt.Color(0x9E9E9E)
                    else -> null
                }
            }
        }
    }

    // ── Toolbar (top row) ─────────────────────────────────────────────────────

    private fun buildToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        val analyseButton = JButton("Analyse Project", AllIcons.Actions.Find)
        analyseButton.addActionListener { runAnalysis(analyseButton) }
        bar.add(analyseButton)
        bar.add(statusLabel)
        return bar
    }

    // ── Action bar (bottom row) ───────────────────────────────────────────────

    private fun buildActionBar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        bar.border = JBUI.Borders.emptyTop(4)

        val acceptAll = JButton("Accept All")
        acceptAll.addActionListener { applyStatusToRows((0 until tableModel.rowCount).toList(), accept = true) }

        val acceptSel = JButton("Accept Selected")
        acceptSel.addActionListener { applyStatusToRows(table.selectedRows.toList(), accept = true) }

        val ignoreSel = JButton("Ignore Selected")
        ignoreSel.addActionListener { applyStatusToRows(table.selectedRows.toList(), accept = false) }

        val generate = JButton("Generate Test Class", AllIcons.Actions.Execute)
        generate.addActionListener { generateTestClass() }

        bar.add(acceptAll)
        bar.add(acceptSel)
        bar.add(ignoreSel)
        bar.add(JSeparator(SwingConstants.VERTICAL).also { it.preferredSize = java.awt.Dimension(2, 24) })
        bar.add(generate)
        return bar
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun runAnalysis(trigger: JButton) {
        tableModel.clear()
        statusLabel.text = "Analysing…"
        trigger.isEnabled = false

        val service = project.getService(AnalysisService::class.java)
        service.analyzeAsync { suggestions ->
            ApplicationManager.getApplication().invokeLater {
                tableModel.setSuggestions(suggestions)
                statusLabel.text = "${suggestions.size} rule(s) suggested."
                trigger.isEnabled = true
            }
        }
    }

    private fun applyStatusToRows(rows: List<Int>, accept: Boolean) {
        rows.forEach { row ->
            val s = tableModel.getSuggestion(row) ?: return@forEach
            if (accept) s.accept() else s.ignore()
            tableModel.refreshRow(row)
        }
    }

    private fun generateTestClass() {
        val accepted = tableModel.allSuggestions().filter { it.status == RuleStatus.ACCEPTED }
        if (accepted.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "Accept at least one rule before generating a test class.",
                "No Rules Accepted",
            )
            return
        }

        val config = ConfigLoader(project).load()
        val basePackage = config.basePackage.ifBlank {
            Messages.showInputDialog(
                project,
                "Enter the root package of your application (e.g. com.example.myapp):",
                "Base Package",
                Messages.getQuestionIcon(),
            ) ?: return
        }

        val generated = ArchUnitTestGenerator().generate(accepted, config, basePackage)
        val writer = TestFileWriter(project)

        when (val result = writer.write(generated)) {
            is WriteResult.Success ->
                Messages.showInfoMessage(project, "Generated:\n${result.path}", "Test Class Created")

            is WriteResult.AlreadyExists -> {
                val overwrite = Messages.showYesNoDialog(
                    project,
                    "File already exists:\n${result.path}\n\nOverwrite?",
                    "File Already Exists",
                    Messages.getQuestionIcon(),
                )
                if (overwrite == Messages.YES) {
                    when (val r2 = writer.write(generated, overwrite = true)) {
                        is WriteResult.Success ->
                            Messages.showInfoMessage(project, "Overwritten:\n${r2.path}", "Done")
                        else -> showError((r2 as? WriteResult.Failure)?.reason ?: "Unknown error")
                    }
                }
            }

            is WriteResult.Failure -> showError(result.reason)
        }
    }

    private fun showError(msg: String) =
        Messages.showErrorDialog(project, msg, "ArchUnit Rule Generator Error")
}
