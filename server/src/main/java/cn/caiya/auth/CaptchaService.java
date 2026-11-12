package cn.caiya.auth;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class CaptchaService {
    static final String ATTRIBUTE=CaptchaService.class.getName()+".challenge";
    record Challenge(byte[] digest,Instant expiresAt) {}
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random=new SecureRandom();
    private final Clock clock;
    public CaptchaService() {this(Clock.systemUTC());}
    CaptchaService(Clock clock) {this.clock=clock;}
    public Map<String,Object> issue(HttpSession session) throws IOException {
        var answer=new StringBuilder();for(int i=0;i<5;i++) answer.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        var image=new BufferedImage(190,64,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try {
            g.setColor(new Color(245,246,234));g.fillRect(0,0,190,64);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            for(int i=0;i<8;i++) {g.setColor(new Color(170+random.nextInt(45),185+random.nextInt(40),160+random.nextInt(50)));g.drawLine(random.nextInt(190),random.nextInt(64),random.nextInt(190),random.nextInt(64));}
            g.setFont(new Font(Font.MONOSPACED,Font.BOLD,34));
            for(int i=0;i<5;i++) {
                var transform=g.getTransform();g.rotate((random.nextDouble()-.5)*.35,24+i*34,34);
                g.setColor(new Color(45+random.nextInt(35),65+random.nextInt(40),35+random.nextInt(35)));
                g.drawString(answer.substring(i,i+1),12+i*34,44+random.nextInt(7)-3);g.setTransform(transform);
            }
        } finally {g.dispose();}
        var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        synchronized(session) {session.setAttribute(ATTRIBUTE,new Challenge(digest(answer.toString()),clock.instant().plusSeconds(120)));}
        return Map.of("image","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()),"expiresInSeconds",120);
    }
    boolean consume(HttpSession session,String answer) {
        if(session==null) return false;
        synchronized(session) {
            Object value=session.getAttribute(ATTRIBUTE);session.removeAttribute(ATTRIBUTE);
            if(!(value instanceof Challenge challenge)||!challenge.expiresAt().isAfter(clock.instant())||answer==null||answer.length()>10) return false;
            return MessageDigest.isEqual(challenge.digest(),digest(answer.trim().toUpperCase(Locale.ROOT)));
        }
    }
    void clear(HttpSession session) {if(session!=null) synchronized(session) {session.removeAttribute(ATTRIBUTE);}}
    static byte[] digest(String value) {
        try {return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
