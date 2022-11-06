package one.papachi.httpd.impl.http.http2;

import java.nio.ByteBuffer;

public class Http2Frame {

    private final Http2FrameHeader header;

    private final byte[] payload;

    private boolean isEndStream;

    private boolean isEndHeaders;

    private boolean isPadded;

    private boolean isPriority;

    private boolean isExclusive;

    private boolean isAck;

    private int padLength;

    private int streamId;

    private int weight;

    private int errorCode;

    private int identifier;

    private int value;

    private int windowSizeIncrement;

    private ByteBuffer data;

    public Http2Frame(Http2FrameHeader header,  byte[] payload) {
        this.header = header;
        this.payload = payload;
        switch (header.getType()) {
            case DATA -> parseDataFrame();
            case HEADERS -> parseHeadersFrame();
            case PRIORITY -> parsePriorityFrame();
            case RST_STREAM -> parseRstStreamFrame();
            case SETTINGS -> parseSettingFrame();
            case PUSH_PROMISE -> parsePushPromiseFrame();
            case PING -> parsePingFrame();
            case GOAWAY -> parseGoAwayFrame();
            case WINDOW_UPDATE -> parseWindowUpdateFrame();
            case CONTINUATION -> parseContinuationFrame();
        }
    }

    public Http2FrameHeader getHeader() {
        return header;
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean isEndStream() {
        return isEndStream;
    }

    public boolean isEndHeaders() {
        return isEndHeaders;
    }

    public boolean isPadded() {
        return isPadded;
    }

    public boolean isPriority() {
        return isPriority;
    }

    public boolean isExclusive() {
        return isExclusive;
    }

    public boolean isAck() {
        return isAck;
    }

    public int getPadLength() {
        return padLength;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getWeight() {
        return weight;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getWindowSizeIncrement() {
        return windowSizeIncrement;
    }

    public ByteBuffer getData() {
        return data;
    }

    private void parseDataFrame() {
        isEndStream = (header.getFlags() & 0x01) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        int offset = 0;
        if (isPadded) {
            padLength = payload[offset++] & 0xFF;
        }
        data = ByteBuffer.wrap(payload, offset, payload.length - offset - padLength);
    }

    private void parseHeadersFrame() {
        isEndStream = (header.getFlags() & 0x01) != 0;
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        isPriority = (header.getFlags() & 0x20) != 0;
        int offset = 0;
        if (isPadded) {
            padLength = payload[offset++] & 0xFF;
        }
        if (isPriority) {
            for (int i = 0; i < 4; i++) {
                streamId = (streamId << 8) + (payload[offset++] & 0xFF);
            }
            if (streamId < 0) {
                isExclusive = true;
            }
            weight = payload[offset++] & 0xFF;
        }
        data = ByteBuffer.wrap(payload, offset, payload.length - offset - padLength);
    }

    private void parsePriorityFrame() {
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (payload[i] & 0xFF);
        }
        if (streamId < 0) {
            isExclusive = true;
        }
        weight = payload[4] & 0xFF;
    }

    private void parseRstStreamFrame() {
        for (int i = 0; i < 4; i++) {
            errorCode = (errorCode << 8) + (payload[i] & 0xFF);
        }
    }

    private void parseSettingFrame() {
        isAck = (header.getFlags() & 0x01) != 0;
    }

    private void parsePushPromiseFrame() {
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        int offset = 0;
        if (isPadded) {
            padLength = payload[offset++] & 0xFF;
        }
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (payload[offset++] & 0xFF);
        }
        data = ByteBuffer.wrap(payload, offset, payload.length - offset - padLength);
    }

    private void parsePingFrame() {
        isAck = (header.getFlags() & 0x01) != 0;
        data = ByteBuffer.wrap(payload);
    }

    private void parseGoAwayFrame() {
        int offset = 0;
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (payload[offset++] & 0xFF);
        }
        for (int i = 0; i < 4; i++) {
            errorCode = (errorCode << 8) + (payload[offset++] & 0xFF);
        }
        data = ByteBuffer.wrap(payload, 8, payload.length - offset);
    }

    private void parseWindowUpdateFrame() {
        for (int i = 0; i < 4; i++) {
            windowSizeIncrement = (windowSizeIncrement << 8) + (payload[i] & 0xFF);
        }
    }

    private void parseContinuationFrame() {
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        data = ByteBuffer.wrap(payload);
    }

}
