package cn.caiya.media;

import cn.caiya.common.ApiException;
import java.io.*;
import java.util.Set;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.w3c.dom.Node;

/** Decode every frame and regenerate the file; never serve the original upload bytes. */
final class SafeImageEncoder {
    record Encoded(byte[] bytes,int width,int height) {}
    static Encoded encode(byte[] input,String format) throws IOException {
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new ApiException(400,"无法解码图片");
            var reader=readers.next();
            try {
                reader.setInput(stream,false,false);
                if(!reader.getFormatName().equalsIgnoreCase(format)) throw new ApiException(400,"图片实际格式与扩展名不符");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                dimensions(width,height);
                var bytes=new ByteArrayOutputStream();
                if(!format.equals("gif")) {
                    var decoded=reader.read(0);
                    if(decoded==null || !ImageIO.write(decoded,format,bytes)) throw new ApiException(400,"图片重新编码失败");
                } else {
                    var sourceStream=reader.getStreamMetadata();
                    var streamTree=(IIOMetadataNode)sourceStream.getAsTree("javax_imageio_gif_stream_1.0");
                    var logical=(IIOMetadataNode)streamTree.getElementsByTagName("LogicalScreenDescriptor").item(0);
                    width=Integer.parseInt(logical.getAttribute("logicalScreenWidth"));
                    height=Integer.parseInt(logical.getAttribute("logicalScreenHeight"));
                    dimensions(width,height);
                    int frames=reader.getNumImages(true);
                    if(frames<1 || frames>200 || (long)width*height*frames>64_000_000L) throw new ApiException(400,"GIF 最多 200 帧，累计画面不超过 6400 万像素");
                    var writer=ImageIO.getImageWritersByFormatName("gif").next();
                    try(var output=new MemoryCacheImageOutputStream(bytes)) {
                        writer.setOutput(output);
                        var streamMeta=writer.getDefaultStreamMetadata(null);
                        streamMeta.setFromTree("javax_imageio_gif_stream_1.0",streamTree);
                        writer.prepareWriteSequence(streamMeta);
                        for(int i=0;i<frames;i++) {
                            dimensions(reader.getWidth(i),reader.getHeight(i));
                            var frame=reader.read(i);
                            var original=(IIOMetadataNode)reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                            var descriptor=(IIOMetadataNode)original.getElementsByTagName("ImageDescriptor").item(0);
                            int left=Integer.parseInt(descriptor.getAttribute("imageLeftPosition")),top=Integer.parseInt(descriptor.getAttribute("imageTopPosition"));
                            if(left+frame.getWidth()>width || top+frame.getHeight()>height) throw new ApiException(400,"GIF 帧超出画布范围");
                            var clean=new IIOMetadataNode("javax_imageio_gif_image_1.0");
                            for(Node child=original.getFirstChild();child!=null;child=child.getNextSibling()) {
                                if(Set.of("ImageDescriptor","LocalColorTable","GraphicControlExtension").contains(child.getNodeName())) clean.appendChild(copyMetadata(child));
                                if(i==0 && child.getNodeName().equals("ApplicationExtensions")) {
                                    for(Node app=child.getFirstChild();app!=null;app=app.getNextSibling()) {
                                        var node=(IIOMetadataNode)app;
                                        if("NETSCAPE".equals(node.getAttribute("applicationID")) && "2.0".equals(node.getAttribute("authenticationCode")) && node.getUserObject() instanceof byte[] loop && loop.length==3 && loop[0]==1) {
                                            var apps=new IIOMetadataNode("ApplicationExtensions");var safe=new IIOMetadataNode("ApplicationExtension");
                                            safe.setAttribute("applicationID","NETSCAPE");safe.setAttribute("authenticationCode","2.0");safe.setUserObject(loop.clone());apps.appendChild(safe);clean.appendChild(apps);
                                        }
                                    }
                                }
                            }
                            var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame),null);
                            metadata.setFromTree("javax_imageio_gif_image_1.0",clean);
                            writer.writeToSequence(new IIOImage(frame,null,metadata),null);
                            output.flush();limit(bytes.size());
                        }
                        writer.endWriteSequence();output.flush();
                    } finally {writer.dispose();}
                }
                limit(bytes.size());return new Encoded(bytes.toByteArray(),width,height);
            } finally {reader.dispose();}
        }
    }
    /**
     * Downscaled display variant for raster images wider than 960 px.
     * Returns null when no variant is useful (GIFs, small images) or the
     * bytes cannot be decoded; callers fall back to the original file.
     */
    static Encoded displayVariant(byte[] encoded,String format) {
        if(format.equals("gif")) return null;
        try {
            var image=ImageIO.read(new ByteArrayInputStream(encoded));
            if(image==null || image.getWidth()<=960) return null;
            int width=960, height=Math.max(1,(int) Math.round((double) image.getHeight()*width/image.getWidth()));
            var scaled=new java.awt.image.BufferedImage(width,height,
                format.equals("png")?java.awt.image.BufferedImage.TYPE_INT_ARGB:java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g=scaled.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image,0,0,width,height,null);
            } finally {g.dispose();}
            var bytes=new ByteArrayOutputStream();
            if(!ImageIO.write(scaled,format,bytes)) return null;
            return new Encoded(bytes.toByteArray(),width,height);
        } catch(IOException|RuntimeException e) {return null;}
    }
    // IIOMetadataNode.cloneNode does not copy attributes (frame offsets, timing, palette).
    private static IIOMetadataNode copyMetadata(Node source) {
        var copy=new IIOMetadataNode(source.getNodeName());
        var attributes=source.getAttributes();
        for(int i=0;i<attributes.getLength();i++) {var attribute=attributes.item(i);copy.setAttribute(attribute.getNodeName(),attribute.getNodeValue());}
        for(Node child=source.getFirstChild();child!=null;child=child.getNextSibling()) copy.appendChild(copyMetadata(child));
        return copy;
    }
    private static void dimensions(int width,int height) {
        if(width<1||height<1||width>8000||height>8000||(long)width*height>16_000_000L) throw new ApiException(400,"图片尺寸过大，最多 1600 万像素");
    }
    private static void limit(int size) {if(size>10*1024*1024) throw new ApiException(400,"处理后的图片超过 10 MB");}
}
