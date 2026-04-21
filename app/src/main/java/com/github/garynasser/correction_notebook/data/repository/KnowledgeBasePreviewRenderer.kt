package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

data class RenderedPreview(
    val htmlPath: String
)

private data class PptxRenderContext(
    val themeColors: Map<String, String>
)

@Singleton
class KnowledgeBasePreviewRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun render(file: KnowledgeBaseFileSummary): Result<RenderedPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(file.localPath)
            require(source.exists()) { "文件不存在" }

            val extension = source.extension.lowercase(Locale.getDefault())
            val outputDir = File(
                context.cacheDir,
                "kb_preview/${file.id}_${source.lastModified()}"
            ).apply {
                parentFile?.mkdirs()
                mkdirs()
            }

            val html = when (extension) {
                "docx" -> renderDocx(source, outputDir)
                "pptx" -> renderPptx(source, outputDir)
                else -> error("当前文件类型不支持高保真应用内预览")
            }

            val htmlFile = File(outputDir, "preview.html")
            htmlFile.writeText(html)
            RenderedPreview(htmlPath = htmlFile.absolutePath)
        }
    }

    private fun renderDocx(source: File, outputDir: File): String {
        ZipFile(source).use { zip ->
            val document = parseXml(zip, "word/document.xml")
            val relationships = parseRelationships(zip, "word/_rels/document.xml.rels", "word/")
            val body = document.documentElement
                .childElements()
                .firstOrNull { it.localName == "body" }
                ?: error("文档内容为空")

            val content = buildString {
                body.childElements().forEach { child ->
                    when (child.localName) {
                        "p" -> append(renderDocxParagraph(child, zip, relationships, outputDir))
                        "tbl" -> append(renderDocxTable(child, zip, relationships, outputDir))
                    }
                }
            }

            return wrapHtml(
                title = source.nameWithoutExtension,
                body = """<article class="docx-root">$content</article>""",
                extraCss = """
                    body { background: #f5f1e8; padding: 24px 12px 48px; }
                    .docx-root {
                        max-width: 860px;
                        margin: 0 auto;
                        background: white;
                        box-shadow: 0 12px 32px rgba(0,0,0,0.08);
                        border-radius: 20px;
                        padding: 40px 36px;
                    }
                    .docx-root p { margin: 0 0 1em; line-height: 1.7; }
                    .docx-root h1, .docx-root h2, .docx-root h3 {
                        margin: 1.2em 0 0.5em;
                        line-height: 1.3;
                    }
                    .docx-root table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 1rem 0 1.4rem;
                    }
                    .docx-root td {
                        border: 1px solid #d8d8d8;
                        padding: 10px 12px;
                        vertical-align: top;
                    }
                    .docx-root img {
                        max-width: 100%;
                        display: block;
                        margin: 12px auto;
                    }
                """.trimIndent()
            )
        }
    }

    private fun renderPptx(source: File, outputDir: File): String {
        ZipFile(source).use { zip ->
            val presentation = parseXml(zip, "ppt/presentation.xml")
            val sldSz = presentation.documentElement.childElements().firstOrNull { it.localName == "sldSz" }
            val slideWidth = sldSz?.attr("cx")?.toDoubleOrNull() ?: 9_144_000.0
            val slideHeight = sldSz?.attr("cy")?.toDoubleOrNull() ?: 5_143_500.0
            val renderContext = PptxRenderContext(
                themeColors = parseThemeColors(zip)
            )

            val slideEntries = zip.entries().asSequence()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { it.name.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                .toList()

            val slidesHtml = buildString {
                slideEntries.forEachIndexed { index, entry ->
                    val slideDoc = parseXml(zip, entry.name)
                    val relationships = parseRelationships(
                        zip,
                        entry.name.replace("ppt/slides/", "ppt/slides/_rels/") + ".rels",
                        "ppt/"
                    )
                    append(
                        renderPptxSlide(
                            slideDoc = slideDoc,
                            zip = zip,
                            relationships = relationships,
                            outputDir = outputDir,
                            slideWidth = slideWidth,
                            slideHeight = slideHeight,
                            renderContext = renderContext,
                            slideNumber = index + 1
                        )
                    )
                }
            }

            return wrapHtml(
                title = source.nameWithoutExtension,
                body = """<section class="deck-root">$slidesHtml</section>""",
                extraCss = """
                    body { background: #e9edf1; padding: 24px 10px 48px; }
                    .deck-root {
                        max-width: 1200px;
                        margin: 0 auto;
                        display: flex;
                        flex-direction: column;
                        gap: 28px;
                    }
                    .slide-shell {
                        background: rgba(255,255,255,0.72);
                        border-radius: 24px;
                        padding: 18px;
                        box-shadow: 0 18px 40px rgba(0,0,0,0.10);
                    }
                    .slide-label {
                        margin-bottom: 12px;
                        font-size: 13px;
                        color: #5f6b76;
                        letter-spacing: 0.06em;
                        text-transform: uppercase;
                    }
                    .slide {
                        width: 100%;
                        overflow: hidden;
                        border-radius: 16px;
                    }
                    .slide svg {
                        display: block;
                        width: 100%;
                        height: auto;
                    }
                """.trimIndent()
            )
        }
    }

    private fun renderDocxParagraph(
        paragraph: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File
    ): String {
        val alignment = paragraph.childElements()
            .firstOrNull { it.localName == "pPr" }
            ?.childElements()
            ?.firstOrNull { it.localName == "jc" }
            ?.attr("val")
            ?.let(::mapDocxAlignment)
            ?: "left"

        val styleName = paragraph.childElements()
            .firstOrNull { it.localName == "pPr" }
            ?.childElements()
            ?.firstOrNull { it.localName == "pStyle" }
            ?.attr("val")
            ?.lowercase(Locale.getDefault())

        val tag = when {
            styleName?.contains("heading1") == true -> "h1"
            styleName?.contains("heading2") == true -> "h2"
            styleName?.contains("heading3") == true -> "h3"
            else -> "p"
        }

        val content = buildString {
            paragraph.childElements().forEach { child ->
                when (child.localName) {
                    "r" -> append(renderDocxRun(child, zip, relationships, outputDir))
                    "hyperlink" -> append(renderDocxHyperlink(child, zip, relationships, outputDir))
                }
            }
        }.ifBlank { "&nbsp;" }

        return """<$tag style="text-align:$alignment">$content</$tag>"""
    }

    private fun renderDocxTable(
        table: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File
    ): String {
        val rows = table.childElements().filter { it.localName == "tr" }
        val body = rows.joinToString("") { row ->
            val cells = row.childElements().filter { it.localName == "tc" }
            val cellHtml = cells.joinToString("") { cell ->
                val cellContent = buildString {
                    cell.childElements().forEach { child ->
                        when (child.localName) {
                            "p" -> append(renderDocxParagraph(child, zip, relationships, outputDir))
                            "tbl" -> append(renderDocxTable(child, zip, relationships, outputDir))
                        }
                    }
                }
                "<td>$cellContent</td>"
            }
            "<tr>$cellHtml</tr>"
        }
        return "<table>$body</table>"
    }

    private fun renderDocxHyperlink(
        hyperlink: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File
    ): String {
        val target = hyperlink.attr("id").takeIf { it.isNotBlank() }?.let(relationships::get)
        val content = hyperlink.childElements()
            .filter { it.localName == "r" }
            .joinToString("") { renderDocxRun(it, zip, relationships, outputDir) }

        return if (target != null) {
            """<a href="${escapeHtml(target)}">$content</a>"""
        } else {
            content
        }
    }

    private fun renderDocxRun(
        run: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File
    ): String {
        val runProps = run.childElements().firstOrNull { it.localName == "rPr" }
        val styles = mutableListOf<String>()
        if (runProps?.childElements()?.any { it.localName == "b" } == true) styles += "font-weight:700"
        if (runProps?.childElements()?.any { it.localName == "i" } == true) styles += "font-style:italic"
        if (runProps?.childElements()?.any { it.localName == "u" } == true) styles += "text-decoration:underline"
        runProps?.childElements()
            ?.firstOrNull { it.localName == "sz" }
            ?.attr("val")
            ?.toDoubleOrNull()
            ?.let { styles += "font-size:${(it / 2.0).roundToInt()}pt" }

        val content = buildString {
            run.childElements().forEach { child ->
                when (child.localName) {
                    "t" -> append(escapeHtml(child.textContent))
                    "br" -> append("<br/>")
                    "drawing" -> append(renderDocxDrawing(child, zip, relationships, outputDir))
                }
            }
        }

        return if (content.isBlank()) "" else """<span style="${styles.joinToString(";")}">$content</span>"""
    }

    private fun renderDocxDrawing(
        drawing: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File
    ): String {
        val blip = drawing.findDescendants("blip").firstOrNull() ?: return ""
        val relId = blip.attr("embed")
        val target = relationships[relId] ?: return ""
        val imagePath = copyZipAsset(zip, target, outputDir) ?: return ""

        val extent = drawing.findDescendants("extent").firstOrNull()
        val widthPx = extent?.attr("cx")?.toDoubleOrNull()?.let(::emuToPx)
        val heightPx = extent?.attr("cy")?.toDoubleOrNull()?.let(::emuToPx)
        val style = buildList {
            widthPx?.let { add("width:${it}px") }
            heightPx?.let { add("height:${it}px") }
        }.joinToString(";")

        return """<img src="${escapeHtml(imagePath)}" style="$style" />"""
    }

    private fun renderPptxSlide(
        slideDoc: Document,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File,
        slideWidth: Double,
        slideHeight: Double,
        renderContext: PptxRenderContext,
        slideNumber: Int
    ): String {
        val spTree = slideDoc.documentElement.findDescendants("spTree").firstOrNull()
            ?: return ""
        val backgroundColor = slideDoc.documentElement
            .findDescendants("bg")
            .firstOrNull()
            ?.let { resolveFillColor(it, renderContext) }
            ?: "#ffffff"

        val layers = buildString {
            spTree.childElements().forEach { child ->
                when (child.localName) {
                    "sp" -> append(renderPptxShape(child, slideWidth, slideHeight, renderContext))
                    "pic" -> append(renderPptxPicture(child, zip, relationships, outputDir, slideWidth, slideHeight))
                    "grpSp" -> append(renderPptxGroupShape(child, zip, relationships, outputDir, slideWidth, slideHeight, renderContext))
                    "cxnSp" -> append(renderPptxConnectionShape(child, slideWidth, slideHeight, renderContext))
                }
            }
        }

        return """
            <section class="slide-shell">
                <div class="slide-label">Slide $slideNumber</div>
                <div class="slide">
                    <svg viewBox="0 0 ${slideWidth.toInt()} ${slideHeight.toInt()}" xmlns="http://www.w3.org/2000/svg">
                        <rect x="0" y="0" width="${slideWidth.toInt()}" height="${slideHeight.toInt()}" fill="$backgroundColor" />
                        $layers
                    </svg>
                </div>
            </section>
        """.trimIndent()
    }

    private fun renderPptxShape(
        shape: Element,
        slideWidth: Double,
        slideHeight: Double,
        renderContext: PptxRenderContext
    ): String {
        val xfrm = shape.findDescendants("xfrm").firstOrNull() ?: return ""
        val off = xfrm.childElements().firstOrNull { it.localName == "off" }
        val ext = xfrm.childElements().firstOrNull { it.localName == "ext" }
        val left = off?.attr("x")?.toDoubleOrNull() ?: 0.0
        val top = off?.attr("y")?.toDoubleOrNull() ?: 0.0
        val width = ext?.attr("cx")?.toDoubleOrNull() ?: slideWidth
        val height = ext?.attr("cy")?.toDoubleOrNull() ?: slideHeight

        val styleNode = shape.childElements().firstOrNull { it.localName == "spPr" }
        val fillColor = styleNode?.let { resolveFillColor(it, renderContext) }
        val lineColor = styleNode
            ?.childElements()
            ?.firstOrNull { it.localName == "ln" }
            ?.let { resolveFillColor(it, renderContext) }
        val borderWidth = styleNode
            ?.childElements()
            ?.firstOrNull { it.localName == "ln" }
            ?.attr("w")
            ?.toDoubleOrNull()
            ?.let { maxOf(1, (it / 12700.0).roundToInt()) }
            ?: 0
        val geometry = styleNode
            ?.childElements()
            ?.firstOrNull { it.localName == "prstGeom" }
            ?.attr("prst")
        val rx = if (geometry == "roundRect") minOf(width, height) * 0.04 else 0.0

        val paragraphs = shape.findDescendants("txBody")
            .firstOrNull()
            ?.childElements()
            ?.filter { it.localName == "p" }
            .orEmpty()
            .joinToString("") { paragraph ->
                val pAlign = paragraph.childElements()
                    .firstOrNull { it.localName == "pPr" }
                    ?.attr("algn")
                    ?.let(::mapPptAlignment)
                    ?: "left"
                val level = paragraph.childElements()
                    .firstOrNull { it.localName == "pPr" }
                    ?.attr("lvl")
                    ?.toIntOrNull()
                    ?: 0

                val runs = paragraph.childElements().joinToString("") { child ->
                    when (child.localName) {
                        "r" -> renderPptxRun(child, renderContext)
                        "br" -> "<br/>"
                        "endParaRPr" -> ""
                        else -> ""
                    }
                }
                """<p style="margin:0 0 0.38em;text-align:$pAlign;padding-left:${level * 1.1}em;">${runs.ifBlank { "&nbsp;" }}</p>"""
            }

        val shapeRect = when (geometry) {
            "ellipse" -> """<ellipse cx="${left + width / 2}" cy="${top + height / 2}" rx="${width / 2}" ry="${height / 2}" ${svgPaint(fillColor, lineColor, borderWidth)} />"""
            else -> """<rect x="$left" y="$top" width="$width" height="$height" rx="$rx" ry="$rx" ${svgPaint(fillColor, lineColor, borderWidth)} />"""
        }
        val textColor = resolveTextColor(shape, renderContext)
        val bodyPr = shape.findDescendants("bodyPr").firstOrNull()
        val insetLeft = bodyPr?.attr("lIns")?.toDoubleOrNull() ?: 91440.0
        val insetRight = bodyPr?.attr("rIns")?.toDoubleOrNull() ?: 91440.0
        val insetTop = bodyPr?.attr("tIns")?.toDoubleOrNull() ?: 45720.0
        val insetBottom = bodyPr?.attr("bIns")?.toDoubleOrNull() ?: 45720.0
        val foreignObject = if (paragraphs.isNotBlank()) {
            """
            <foreignObject x="${left + insetLeft}" y="${top + insetTop}" width="${maxOf(1.0, width - insetLeft - insetRight)}" height="${maxOf(1.0, height - insetTop - insetBottom)}">
                <div xmlns="http://www.w3.org/1999/xhtml" style="width:100%;height:100%;overflow:hidden;color:$textColor;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;line-height:1.2;">
                    $paragraphs
                </div>
            </foreignObject>
            """.trimIndent()
        } else ""

        return shapeRect + foreignObject
    }

    private fun renderPptxRun(run: Element, renderContext: PptxRenderContext): String {
        val runProps = run.childElements().firstOrNull { it.localName == "rPr" }
        val styles = mutableListOf<String>()
        runProps?.attr("sz")?.toDoubleOrNull()?.let {
            styles += "font-size:${((it / 100.0) * 96 / 72).roundToInt()}px"
        }
        if (runProps?.attr("b") == "1") styles += "font-weight:700"
        if (runProps?.attr("i") == "1") styles += "font-style:italic"
        if (runProps?.attr("u") == "sng") styles += "text-decoration:underline"
        runProps?.let { resolveFillColor(it, renderContext) }?.let { styles += "color:$it" }

        val text = run.childElements().firstOrNull { it.localName == "t" }?.textContent.orEmpty()
        return """<span style="${styles.joinToString(";")}">${escapeHtml(text)}</span>"""
    }

    private fun renderPptxGroupShape(
        groupShape: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File,
        slideWidth: Double,
        slideHeight: Double,
        renderContext: PptxRenderContext
    ): String {
        return buildString {
            groupShape.childElements().forEach { child ->
                when (child.localName) {
                    "sp" -> append(renderPptxShape(child, slideWidth, slideHeight, renderContext))
                    "pic" -> append(renderPptxPicture(child, zip, relationships, outputDir, slideWidth, slideHeight))
                    "grpSp" -> append(renderPptxGroupShape(child, zip, relationships, outputDir, slideWidth, slideHeight, renderContext))
                    "cxnSp" -> append(renderPptxConnectionShape(child, slideWidth, slideHeight, renderContext))
                }
            }
        }
    }

    private fun renderPptxConnectionShape(
        shape: Element,
        slideWidth: Double,
        slideHeight: Double,
        renderContext: PptxRenderContext
    ): String {
        val xfrm = shape.findDescendants("xfrm").firstOrNull() ?: return ""
        val off = xfrm.childElements().firstOrNull { it.localName == "off" }
        val ext = xfrm.childElements().firstOrNull { it.localName == "ext" }
        val left = off?.attr("x")?.toDoubleOrNull() ?: 0.0
        val top = off?.attr("y")?.toDoubleOrNull() ?: 0.0
        val width = ext?.attr("cx")?.toDoubleOrNull() ?: 0.0
        val height = ext?.attr("cy")?.toDoubleOrNull() ?: 0.0
        val lineColor = shape.childElements()
            .firstOrNull { it.localName == "spPr" }
            ?.childElements()
            ?.firstOrNull { it.localName == "ln" }
            ?.let { resolveFillColor(it, renderContext) }
            ?: "#6c7a89"

        return """
            <line x1="$left" y1="$top" x2="${left + width}" y2="${top + height}" stroke="$lineColor" stroke-width="2" />
        """.trimIndent()
    }

    private fun renderPptxPicture(
        picture: Element,
        zip: ZipFile,
        relationships: Map<String, String>,
        outputDir: File,
        slideWidth: Double,
        slideHeight: Double
    ): String {
        val xfrm = picture.findDescendants("xfrm").firstOrNull() ?: return ""
        val off = xfrm.childElements().firstOrNull { it.localName == "off" }
        val ext = xfrm.childElements().firstOrNull { it.localName == "ext" }
        val left = off?.attr("x")?.toDoubleOrNull() ?: 0.0
        val top = off?.attr("y")?.toDoubleOrNull() ?: 0.0
        val width = ext?.attr("cx")?.toDoubleOrNull() ?: 0.0
        val height = ext?.attr("cy")?.toDoubleOrNull() ?: 0.0

        val blip = picture.findDescendants("blip").firstOrNull() ?: return ""
        val relId = blip.attr("embed")
        val target = relationships[relId] ?: return ""
        val imagePath = copyZipAsset(zip, target, outputDir) ?: return ""

        return """
            <image href="${escapeHtml(imagePath)}"
                   x="$left" y="$top" width="$width" height="$height"
                   preserveAspectRatio="none" />
        """.trimIndent()
    }

    private fun parseRelationships(zip: ZipFile, path: String, basePrefix: String): Map<String, String> {
        val entry = zip.getEntry(path) ?: return emptyMap()
        val document = zip.getInputStream(entry).use { input ->
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(input)
        }

        return document.documentElement.childElements()
            .filter { it.localName == "Relationship" }
            .associate { rel ->
                val id = rel.getAttribute("Id")
                val target = rel.getAttribute("Target")
                id to if (target.startsWith("/")) target.removePrefix("/") else basePrefix + target.removePrefix("../")
            }
    }

    private fun parseXml(zip: ZipFile, path: String): Document {
        val entry = zip.getEntry(path) ?: error("缺少预览所需文件: $path")
        return zip.getInputStream(entry).use { input ->
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(input)
        }
    }

    private fun copyZipAsset(zip: ZipFile, target: String, outputDir: File): String? {
        val normalizedTarget = target.removePrefix("/")
        val entry = zip.getEntry(normalizedTarget) ?: return null
        val fileName = normalizedTarget.substringAfterLast('/')
        val outFile = File(outputDir, fileName)
        if (!outFile.exists()) {
            zip.getInputStream(entry).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.toURI().toString()
    }

    private fun parseThemeColors(zip: ZipFile): Map<String, String> {
        val entry = zip.getEntry("ppt/theme/theme1.xml") ?: return emptyMap()
        val document = zip.getInputStream(entry).use { input ->
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(input)
        }
        val clrScheme = document.documentElement.findDescendants("clrScheme").firstOrNull() ?: return emptyMap()
        return clrScheme.childElements().associate { colorNode ->
            val color = colorNode.findDescendants("srgbClr").firstOrNull()?.attr("val")
                ?: colorNode.findDescendants("sysClr").firstOrNull()?.attr("lastClr")
                ?: "000000"
            colorNode.localName to "#$color"
        }
    }

    private fun resolveFillColor(node: Element, renderContext: PptxRenderContext): String? {
        val solidFill = when (node.localName) {
            "solidFill" -> node
            else -> node.findDescendants("solidFill").firstOrNull()
        }
        solidFill?.findDescendants("srgbClr")?.firstOrNull()?.attr("val")
            ?.takeIf { it.isNotBlank() }
            ?.let { return "#$it" }

        solidFill?.findDescendants("schemeClr")?.firstOrNull()?.attr("val")
            ?.takeIf { it.isNotBlank() }
            ?.let { return renderContext.themeColors[it] ?: renderContext.themeColors[it.removePrefix("accent")] }

        solidFill?.findDescendants("sysClr")?.firstOrNull()?.attr("lastClr")
            ?.takeIf { it.isNotBlank() }
            ?.let { return "#$it" }

        if (node.findDescendants("noFill").isNotEmpty()) return "transparent"
        return null
    }

    private fun resolveTextColor(shape: Element, renderContext: PptxRenderContext): String {
        return shape.findDescendants("txBody").firstOrNull()
            ?.findDescendants("solidFill")
            ?.firstOrNull()
            ?.let { resolveFillColor(it, renderContext) }
            ?: "#1d2730"
    }

    private fun svgPaint(fillColor: String?, lineColor: String?, borderWidth: Int): String {
        val fill = fillColor?.takeUnless { it == "transparent" } ?: "none"
        val stroke = lineColor ?: "none"
        return """fill="$fill" stroke="$stroke" stroke-width="$borderWidth""""
    }

    private fun wrapHtml(
        title: String,
        body: String,
        extraCss: String
    ): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0" />
                <title>${escapeHtml(title)}</title>
                <style>
                    * { box-sizing: border-box; }
                    html, body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #1d2730; }
                    a { color: #0b65c2; text-decoration: none; }
                    $extraCss
                </style>
            </head>
            <body>
                $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result += node as Element
            }
        }
        return result
    }

    private fun Element.findDescendants(localName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(node: Element) {
            node.childElements().forEach { child ->
                if (child.localName == localName) {
                    result += child
                }
                walk(child)
            }
        }
        walk(this)
        return result
    }

    private fun Element.attr(name: String): String {
        return when {
            hasAttribute(name) -> getAttribute(name)
            hasAttributeNS("*", name) -> getAttributeNS("*", name)
            else -> attributes.asSequence()
                .firstOrNull { it.nodeName.endsWith(":$name") }
                ?.nodeValue
                .orEmpty()
        }
    }

    private fun org.w3c.dom.NamedNodeMap.asSequence(): Sequence<Node> = sequence {
        for (i in 0 until length) {
            yield(item(i))
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun emuToPx(value: Double): Int = (value / 9525.0).roundToInt()

    private fun mapDocxAlignment(value: String): String {
        return when (value.lowercase(Locale.getDefault())) {
            "center" -> "center"
            "right" -> "right"
            "both", "distribute" -> "justify"
            else -> "left"
        }
    }

    private fun mapPptAlignment(value: String): String {
        return when (value.lowercase(Locale.getDefault())) {
            "ctr" -> "center"
            "r" -> "right"
            "just" -> "justify"
            else -> "left"
        }
    }
}
