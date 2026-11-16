package com.github.dev34.videoengine.resourcepack;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class AudioChunker implements AutoCloseable {

    private static final long CHUNK_MICROS = 10_000_000L;

    private final File directory;
    private final String name;
    private final int channels;
    private final int sampleRate;
    private final List<File> files = new ArrayList<>();

    private FFmpegFrameRecorder recorder;
    private int currentChunk = -1;

    AudioChunker(File directory, String name, int channels, int sampleRate) {
        this.directory = directory;
        this.name = name;
        this.channels = channels;
        this.sampleRate = sampleRate;
    }

    void write(Frame frame, long relativeMicros) throws Exception {
        int chunk = (int) (Math.max(0, relativeMicros) / CHUNK_MICROS);

        if (chunk != currentChunk) {
            closeRecorder();
            open(chunk);
        }

        recorder.recordSamples(sampleRate, channels, frame.samples);
    }

    private void open(int chunk) throws Exception {
        File ogg = new File(directory, name + "_" + chunk + ".ogg");

        recorder = new FFmpegFrameRecorder(ogg, channels);
        recorder.setFormat("ogg");
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_VORBIS);
        recorder.setSampleRate(sampleRate);
        recorder.setAudioBitrate(96_000);
        recorder.setAudioOption("strict", "-2");
        recorder.start();

        files.add(ogg);
        currentChunk = chunk;
    }

    private void closeRecorder() throws Exception {
        if (recorder == null) return;

        FFmpegFrameRecorder r = recorder;
        recorder = null;
        try {
            r.stop();
        } finally {
            r.release();
        }
    }

    List<File> finish() throws Exception {
        closeRecorder();
        return files;
    }

    List<File> getFiles() {
        return files;
    }

    @Override
    public void close() {
        try {
            closeRecorder();
        } catch (Exception ignored) {
        }
    }
}