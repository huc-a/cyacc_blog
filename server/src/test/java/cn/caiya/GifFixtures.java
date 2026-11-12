package cn.caiya;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;

final class GifFixtures {
  static byte[] animation(int frames) throws IOException {
    var bytes = new ByteArrayOutputStream();
    var writer = ImageIO.getImageWritersByFormatName("gif").next();
    try (var output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      writer.prepareWriteSequence(null);
      for (int i = 0; i < frames; i++) {
        var frame = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 8; x++)
          for (int y = 0; y < 8; y++) frame.setRGB(x, y, i % 2 == 0 ? 0xff0000 : 0x0000ff);
        var meta =
            writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), null);
        var tree = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
        var control =
            (IIOMetadataNode) tree.getElementsByTagName("GraphicControlExtension").item(0);
        control.setAttribute("delayTime", i % 2 == 0 ? "7" : "13");
        control.setAttribute("disposalMethod", "restoreToBackgroundColor");
        if (i == 0) {
          var comments = new IIOMetadataNode("CommentExtensions");
          var comment = new IIOMetadataNode("CommentExtension");
          comment.setAttribute("value", "untrusted metadata");
          comments.appendChild(comment);
          tree.appendChild(comments);
          var apps = new IIOMetadataNode("ApplicationExtensions");
          var app = new IIOMetadataNode("ApplicationExtension");
          app.setAttribute("applicationID", "NETSCAPE");
          app.setAttribute("authenticationCode", "2.0");
          app.setUserObject(new byte[] {1, 0, 0});
          apps.appendChild(app);
          tree.appendChild(apps);
        }
        meta.setFromTree("javax_imageio_gif_image_1.0", tree);
        writer.writeToSequence(new IIOImage(frame, null, meta), null);
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
