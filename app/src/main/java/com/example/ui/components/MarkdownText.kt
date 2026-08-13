package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native Jetpack Compose Markdown Renderer supporting headers, bold/italic, lists, privacy badges, and code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (markdown.isBlank()) {
        Text(
            text = "暂无文档内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.primary
    val privacyBg = MaterialTheme.colorScheme.tertiaryContainer
    val privacyFg = MaterialTheme.colorScheme.onTertiaryContainer

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val lines = markdown.lines()
        var inCodeBlock = false
        val codeBuffer = StringBuilder()

        lines.forEach { line ->
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Render accumulated code block
                    CodeBlockView(codeBuffer.toString())
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                return@forEach
            }

            if (inCodeBlock) {
                codeBuffer.append(line).append("\n")
                return@forEach
            }

            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.substring(2), codeBg, codeFg, privacyBg, privacyFg),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.substring(3), codeBg, codeFg, privacyBg, privacyFg),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.substring(4), codeBg, codeFg, privacyBg, privacyFg),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    BlockQuoteView(parseInlineMarkdown(trimmed.substring(2), codeBg, codeFg, privacyBg, privacyFg))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val content = trimmed.substring(2)
                    Row(
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = parseInlineMarkdown(content, codeBg, codeFg, privacyBg, privacyFg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line, codeBg, codeFg, privacyBg, privacyFg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (inCodeBlock && codeBuffer.isNotEmpty()) {
            CodeBlockView(codeBuffer.toString())
        }
    }
}

@Composable
private fun BlockQuoteView(text: AnnotatedString) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CodeBlockView(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = code.trimEnd(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Parses **bold**, *italic*, `code`, and privacy placeholders like [姓名], [电话]
 */
private fun parseInlineMarkdown(
    text: String,
    codeBg: Color,
    codeFg: Color,
    privacyBg: Color,
    privacyFg: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // Standard Markdown Link [title](url)
                text.startsWith("[", i) && text.indexOf("]", i) > i && 
                text.indexOf("]", i) + 1 < len && text[text.indexOf("]", i) + 1] == '(' -> {
                    val endBracket = text.indexOf("]", i)
                    val endParen = text.indexOf(")", endBracket + 2)
                    if (endParen != -1) {
                        val title = text.substring(i + 1, endBracket)
                        withStyle(
                            SpanStyle(
                                color = Color(0xFF1E88E5),
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append(title)
                        }
                        i = endParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Privacy Placeholder Highlight like [姓名], [电话], [电子邮箱]
                text.startsWith("[", i) && text.indexOf("]", i) > i -> {
                    val endBracket = text.indexOf("]", i)
                    val contentInside = text.substring(i + 1, endBracket)
                    val isPrivacyPlaceholder = contentInside in setOf(
                        "姓名", "电话", "手机号码", "电子邮箱", "邮箱", "居住城市", "城市",
                        "毕业院校", "学校", "公司名称", "公司", "职务", "项目名称", "Filtered Tag", "Safety Filtered"
                    ) || contentInside.contains("隐私") || contentInside.contains("占位")

                    if (isPrivacyPlaceholder) {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = privacyFg,
                                background = privacyBg
                            )
                        ) {
                            append(" [$contentInside] ")
                        }
                    } else {
                        append("[$contentInside]")
                    }
                    i = endBracket + 1
                }
                // Bold text **text**
                text.startsWith("**", i) -> {
                    val endBold = text.indexOf("**", i + 2)
                    if (endBold != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, endBold))
                        }
                        i = endBold + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Code inline `code`
                text.startsWith("`", i) -> {
                    val endCode = text.indexOf("`", i + 1)
                    if (endCode != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                color = codeFg
                            )
                        ) {
                            append(" ${text.substring(i + 1, endCode)} ")
                        }
                        i = endCode + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
