package com.twilio.video;

public class LocalAudioTrackStats extends LocalTrackStats {
    public final int audioInputLevel;
    /**
     * Packet jitter measured in milliseconds
     */
    public final int jitterReceived;
    /**
     * Jitter buffer measured in milliseconds
     */
    public final int jitterBufferMs;

    public LocalAudioTrackStats(String trackId,
                                int packetsLost,
                                String codecName,
                                String ssrc,
                                double unixTimestamp,
                                long bytesSent,
                                int packetsSent,
                                long roundTripTime,
                                int audioInputLevel,
                                int jitterReceived,
                                int jitterBufferMs) {
        super(trackId, packetsLost, codecName, ssrc,
                unixTimestamp, bytesSent, packetsSent, roundTripTime);
        this.audioInputLevel = audioInputLevel;
        this.jitterReceived = jitterReceived;
        this.jitterBufferMs = jitterBufferMs;
    }
}
