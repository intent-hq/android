package dev.intent.app.agents

import dev.intent.app.markdown.parseMarkdown
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text

/** Keep visible text, including link labels, without destinations or markup. */
internal fun agentPreviewText(source: String): String {
    val text = StringBuilder()
    parseMarkdown(source).accept(object : AbstractVisitor() {
        override fun visit(node: Text) {
            text.append(node.literal)
        }
        override fun visit(node: Code) {
            text.append(node.literal)
        }
        override fun visit(node: SoftLineBreak) {
            text.append(' ')
        }
        override fun visit(node: HardLineBreak) {
            text.append(' ')
        }
        override fun visit(node: Heading) {
            visitChildren(node)
            text.append(' ')
        }
        override fun visit(node: Paragraph) {
            visitChildren(node)
            text.append(' ')
        }
        override fun visit(node: FencedCodeBlock) {
            text.append(node.literal).append(' ')
        }
        override fun visit(node: IndentedCodeBlock) {
            text.append(node.literal).append(' ')
        }
    })
    return text.toString().replace(Regex("\\s+"), " ").trim()
}
