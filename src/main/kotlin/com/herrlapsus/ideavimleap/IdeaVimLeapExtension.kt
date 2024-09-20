/*
    IdeaVim-Leap plugin for IdeaVim mimicking leap.nvim plugin
    Copyright (C) 2020 Mikhail Levchenko
    Copyright (C) IdeaVim Authors
    Copyright (C) 2024 HerrLapsus

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.herrlapsus.ideavimleap

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.*
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.extension.VimExtensionFacade
import com.maddyhome.idea.vim.extension.VimExtensionHandler
import java.awt.Font
import java.awt.event.KeyEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.KeyStroke

class IdeaVimLeapExtension : VimExtension {
    override fun getName(): String = "leap"

    override fun init() {
        VimPlugin.getKey().putKeyMapping(
            MappingMode.NXO,
            listOf(KeyStroke.getKeyStroke('s')),
            owner,
            LeapHandler(Direction.FORWARD),
            false
        )

        VimPlugin.getKey().putKeyMapping(
            MappingMode.NXO,
            listOf(KeyStroke.getKeyStroke('S')),
            owner,
            LeapHandler(Direction.BACKWARD),
            false
        )
    }
}

private class LeapHandler(
    private val direction: Direction,
    private val highlightHandler: HighlightHandler = HighlightHandler()
) : VimExtensionHandler {
    private val insertedLabels = mutableListOf<Pair<Int, Char>>()

    override fun execute(editor: Editor, context: DataContext) {
        val charOne = getChar(editor) ?: return
        val charTwo = getChar(editor) ?: return

        val possibleJumpTargets = getJumpTargets(editor, charOne, charTwo, direction)
        when {
            possibleJumpTargets.isEmpty() -> return
            possibleJumpTargets.size == 1 -> {
                jumpTo(editor, possibleJumpTargets.first())
                return
            }

            else -> {
                // draw labels if necessary or jump if single match
                for ((index, possibleJumpTarget) in possibleJumpTargets.take(26).withIndex()) {
                    val labelPosition = if (direction == Direction.BACKWARD) possibleJumpTarget else possibleJumpTarget + index
                    insertLabel(editor, labelPosition, 'a' + index)
                    highlightHandler.highlightRange(editor, TextRange(labelPosition - 2, labelPosition))
                }
                val labelChar = getChar(editor) ?: return
                val labelPosition = insertedLabels.find { it.second == labelChar }?.first ?: return
                jumpTo(editor, labelPosition)
                clearLabels(editor)
                highlightHandler.clearAllHighlighters()
            }
        }
    }

    private fun getJumpTargets(editor: Editor, charOne: Char, charTwo: Char, direction: Direction): List<Int> {
        val currentPosition = editor.caretModel.primaryCaret.offset
        val chars = editor.document.charsSequence
        return direction.findBiChar(chars, currentPosition, charOne, charTwo)
    }

    private fun getChar(editor: Editor): Char? {
        val key = VimExtensionFacade.inputKeyStroke(editor)
        return when {
            key.keyChar == KeyEvent.CHAR_UNDEFINED || key.keyCode == KeyEvent.VK_ESCAPE -> null
            else -> key.keyChar
        }
    }

    private fun insertLabel(editor: Editor, position: Int, char: Char) {
        val labelPosition = position + 2
        ApplicationManager.getApplication().runWriteAction() {
            editor.document.insertString(labelPosition, char.toString())
        }
        insertedLabels.add(Pair(labelPosition, char))
    }

    private fun clearLabels(editor: Editor) {
        val labelsToRemove = insertedLabels.reversed()
        ApplicationManager.getApplication().runWriteAction() {
            for ((position, _) in labelsToRemove) {
                editor.document.deleteString(position, position + 1)
            }
        }
        insertedLabels.clear()
    }

    private fun jumpTo(editor: Editor, position: Int) {
        position.let(editor.caretModel::moveToOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        val highlighter = HighlightHandler()
        highlighter.highlightRange(editor, TextRange(position, position + 2))
        highlighter.setClearHighlightsTimer()
    }
}

private enum class Direction(val offset: Int) {
    FORWARD(1) {
        override fun findBiChar(
            charSequence: CharSequence,
            position: Int,
            charone: Char,
            chartwo: Char
        ): List<Int> {
            val occurrences = mutableListOf<Int>()
            for (i in (position + offset) until charSequence.length - 1) {
                if (matches(charSequence, i, charone, chartwo)) {
                    occurrences.add(i)
                }
            }
            return occurrences
        }
    },
    BACKWARD(-1) {
        override fun findBiChar(
            charSequence: CharSequence,
            position: Int,
            charone: Char,
            chartwo: Char
        ): List<Int> {
            val occurrences = mutableListOf<Int>()
            for (i in (position + offset) downTo 0) {
                if (matches(charSequence, i, charone, chartwo)) {
                    occurrences.add(i)
                }
            }
            return occurrences
        }

    };

    abstract fun findBiChar(charSequence: CharSequence, position: Int, charone: Char, chartwo: Char): List<Int>

    fun matches(charSequence: CharSequence, charPosition: Int, charOne: Char, charTwo: Char): Boolean {
        var match = charSequence[charPosition].equals(charOne, ignoreCase = true) &&
                charSequence[charPosition + 1].equals(charTwo, ignoreCase = true)

        if (charOne.isUpperCase() || charTwo.isUpperCase()) {
            match = charSequence[charPosition].equals(charOne, ignoreCase = false) &&
                    charSequence[charPosition + 1].equals(charTwo, ignoreCase = false)
        }
        return match
    }
}

private class HighlightHandler {
    private var editor: Editor? = null
    private val highlighters: MutableSet<RangeHighlighter> = mutableSetOf()

    fun highlightRange(editor: Editor, range: TextRange) {
        clearAllHighlighters()

        this.editor = editor

        if (range.isMultiple) {
            for (i in 0 until range.size()) {
                highlightSingleRange(editor, range.startOffsets[i]..range.endOffsets[i])
            }
        } else {
            highlightSingleRange(editor, range.startOffset..range.endOffset)
        }
    }

    fun clearAllHighlighters() {
        highlighters.forEach { highlighter ->
            editor?.markupModel?.removeHighlighter(highlighter)
        }

        highlighters.clear()
    }

    private fun highlightSingleRange(editor: Editor, range: ClosedRange<Int>) {
        val highlighter = editor.markupModel.addRangeHighlighter(
            range.start,
            range.endInclusive,
            HighlighterLayer.SELECTION,
            getHighlightTextAttributes(),
            HighlighterTargetArea.EXACT_RANGE
        )

        highlighters.add(highlighter)
    }

    fun setClearHighlightsTimer(delay: Long = 500) {
        Executors.newSingleThreadScheduledExecutor().schedule({
            ApplicationManager.getApplication().invokeLater {
                clearAllHighlighters()
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun getHighlightTextAttributes() = TextAttributes(
        null,
        EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES.defaultAttributes.backgroundColor,
        editor?.colorsScheme?.getColor(EditorColors.CARET_COLOR),
        EffectType.SEARCH_MATCH,
        Font.PLAIN
    )
}
