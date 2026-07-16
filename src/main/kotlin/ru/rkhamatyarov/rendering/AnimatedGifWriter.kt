package ru.rkhamatyarov.rendering

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

object AnimatedGifWriter {
    fun write(
        frames: List<BufferedImage>,
        output: Path,
        delayMs: Int,
    ) {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        Files.createDirectories(output.parent)

        val writer =
            ImageIO.getImageWritersBySuffix("gif").asSequence().firstOrNull()
                ?: error("No GIF ImageIO writer is available")
        val params = writer.defaultWriteParam
        val imageType = ImageTypeSpecifier.createFromBufferedImageType(frames.first().type)
        val metadata = writer.getDefaultImageMetadata(imageType, params)
        configureMetadata(metadata, delayMs)

        FileImageOutputStream(output.toFile()).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            frames.forEach { frame ->
                writer.writeToSequence(IIOImage(frame, null, metadata), params)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
    }

    private fun configureMetadata(
        metadata: IIOMetadata,
        delayMs: Int,
    ) {
        val formatName = metadata.nativeMetadataFormatName
        val root = metadata.getAsTree(formatName) as IIOMetadataNode

        val graphicsControl = root.child("GraphicControlExtension")
        graphicsControl.setAttribute("disposalMethod", "none")
        graphicsControl.setAttribute("userInputFlag", "FALSE")
        graphicsControl.setAttribute("transparentColorFlag", "FALSE")
        graphicsControl.setAttribute("delayTime", (delayMs / 10).coerceAtLeast(1).toString())
        graphicsControl.setAttribute("transparentColorIndex", "0")

        val appExtensions = root.child("ApplicationExtensions")
        val loop = IIOMetadataNode("ApplicationExtension")
        loop.setAttribute("applicationID", "NETSCAPE")
        loop.setAttribute("authenticationCode", "2.0")
        loop.userObject = byteArrayOf(1, 0, 0)
        appExtensions.appendChild(loop)

        metadata.setFromTree(formatName, root)
    }

    private fun IIOMetadataNode.child(name: String): IIOMetadataNode {
        for (index in 0 until length) {
            val node = item(index)
            if (node.nodeName == name) return node as IIOMetadataNode
        }
        val node = IIOMetadataNode(name)
        appendChild(node)
        return node
    }
}
