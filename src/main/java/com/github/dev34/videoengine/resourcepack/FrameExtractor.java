package com.github.dev34.videoengine.resourcepack;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FrameExtractor {

    static final int FPS = 20;
    private static final long FRAME_MICROS = 1_000_000L / FPS;
    private static final int WIDTH = 128;
    private static final int MAX_SECONDS = 300;

    private static String texturePath(String fontName, int frame) {
        return "assets/minecraft/textures/font/" + fontName + "/frame_" + frame + ".png";
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static BufferedImage scale(BufferedImage source, int height) {
        BufferedImage scaled = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, WIDTH, height, null);
        g.dispose();
        return scaled;
    }

    private static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    protected static int extractFrames(String videoUrl, File outputZip, int packFormat) throws Exception {

        File parent = outputZip.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String fontName = outputZip.getName().replaceFirst("\\.zip$", "");
        File tmpZip = new File(parent, outputZip.getName() + ".tmp");
        Path tmpDir = Files.createTempDirectory("videoengine");

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);
        grabber.setOption("protocol_whitelist", "http,https,tcp,tls,crypto");
        grabber.setOption("rw_timeout", "15000000");

        Java2DFrameConverter converter = new Java2DFrameConverter();
        AudioChunker audio = null;
        boolean success = false;
        int frameCount = 0;

        try {
            grabber.start();

            int height = Math.max(1, (int) Math.round((double) WIDTH * grabber.getImageHeight() / grabber.getImageWidth()));
            int ascent = height / 2;
            int maxFrames = Math.min(JSONCreator.MAX_FRAMES, MAX_SECONDS * FPS);

            if (grabber.getAudioChannels() > 0) {
                audio = new AudioChunker(tmpDir.toFile(), fontName, grabber.getAudioChannels(), grabber.getSampleRate());
            }

            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tmpZip))) {

                put(zip, "pack.mcmeta", JSONCreator.toBytes(
                        McMetaCreator.create("VideoEngine " + fontName, packFormat)));

                long origin = Long.MIN_VALUE;
                long nextSlot = 0;
                BufferedImage pending = null;
                byte[] pendingPng = null;

                Frame frame;
                while ((frame = grabber.grab()) != null) {

                    if (origin == Long.MIN_VALUE) {
                        origin = frame.timestamp;
                    }
                    long relative = Math.max(0, frame.timestamp - origin);

                    if (frame.image != null) {
                        BufferedImage image = converter.convert(frame);
                        if (image == null) continue;

                        if (pending != null) {
                            while (nextSlot < relative && frameCount < maxFrames) {
                                if (pendingPng == null) pendingPng = toPng(pending);
                                put(zip, texturePath(fontName, frameCount), pendingPng);
                                frameCount++;
                                nextSlot += FRAME_MICROS;
                            }
                            if (frameCount >= maxFrames) break;
                        }

                        pending = scale(image, height);
                        pendingPng = null;

                    } else if (audio != null && frame.samples != null) {
                        audio.write(frame, relative);
                    }
                }

                if (pending != null && frameCount < maxFrames) {
                    put(zip, texturePath(fontName, frameCount), toPng(pending));
                    frameCount++;
                }

                if (frameCount == 0) {
                    throw new IllegalStateException("No video frames found");
                }

                put(zip, "assets/minecraft/font/" + fontName + ".json", JSONCreator.toBytes(
                        JSONCreator.createFontJson(fontName, frameCount, height, ascent)));

                if (audio != null) {
                    List<File> oggs = audio.finish();

                    if (!oggs.isEmpty()) {
                        List<String> soundNames = new ArrayList<>();

                        for (File ogg : oggs) {
                            soundNames.add(ogg.getName().replaceFirst("\\.ogg$", ""));
                            put(zip, "assets/minecraft/sounds/" + ogg.getName(), Files.readAllBytes(ogg.toPath()));
                        }

                        put(zip, "assets/minecraft/sounds.json", JSONCreator.toBytes(
                                JSONCreator.createSoundsJson(soundNames)));
                    }
                }
            }

            Files.move(tmpZip.toPath(), outputZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            success = true;
            return frameCount;

        } finally {
            try {
                if (audio != null) audio.close();
                grabber.stop();
            } finally {
                grabber.release();
                converter.close();
                if (audio != null) {
                    for (File ogg : audio.getFiles()) {
                        ogg.delete();
                    }
                }
                tmpDir.toFile().delete();
                if (!success) {
                    tmpZip.delete();
                }
            }
        }
    }
}