package one.papachi.httpd.impl.http;

import java.nio.ByteBuffer;

public class Http2Frame {

    private final Http2FrameHeader header;

    private final ByteBuffer buffer;

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

    public Http2Frame(Http2FrameHeader header, ByteBuffer buffer) {
        this.header = header;
        this.buffer = buffer;
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

    public ByteBuffer getPayload() {
        return buffer;
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
        data = buffer.duplicate();
        isEndStream = (header.getFlags() & 0x01) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        if (isPadded) {
            padLength = data.get() & 0xFF;
        }
        data.limit(data.limit() - padLength);
    }

    private void parseHeadersFrame() {
        data = buffer.duplicate();
        isEndStream = (header.getFlags() & 0x01) != 0;
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        isPriority = (header.getFlags() & 0x20) != 0;
        if (isPadded) {
            padLength = data.get() & 0xFF;
        }
        if (isPriority) {
            for (int i = 0; i < 4; i++) {
                streamId = (streamId << 8) + (data.get() & 0xFF);
            }
            if (streamId < 0) {
                isExclusive = true;
            }
            weight = data.get() & 0xFF;
        }
        data.limit(data.limit() - padLength);
    }

    private void parsePriorityFrame() {
        ByteBuffer data = buffer.duplicate();
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (data.get() & 0xFF);
        }
        if (streamId < 0) {
            isExclusive = true;
        }
        weight = data.get() & 0xFF;
    }

    private void parseRstStreamFrame() {
        ByteBuffer data = buffer.duplicate();
        for (int i = 0; i < 4; i++) {
            errorCode = (errorCode << 8) + (data.get() & 0xFF);
        }
    }

    private void parseSettingFrame() {
        isAck = (header.getFlags() & 0x01) != 0;
    }

    private void parsePushPromiseFrame() {
        data = buffer.duplicate();
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        isPadded = (header.getFlags() & 0x08) != 0;
        if (isPadded) {
            padLength = data.get() & 0xFF;
        }
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (data.get() & 0xFF);
        }
        data.limit(data.limit() - padLength);
    }

    private void parsePingFrame() {
        isAck = (header.getFlags() & 0x01) != 0;
        data = buffer.duplicate();
    }

    private void parseGoAwayFrame() {
        data = buffer.duplicate();
        for (int i = 0; i < 4; i++) {
            streamId = (streamId << 8) + (data.get() & 0xFF);
        }
        for (int i = 0; i < 4; i++) {
            errorCode = (errorCode << 8) + (data.get() & 0xFF);
        }
    }

    private void parseWindowUpdateFrame() {
        windowSizeIncrement = buffer.duplicate().getInt();
    }

    private void parseContinuationFrame() {
        isEndHeaders = (header.getFlags() & 0x04) != 0;
        data = buffer.duplicate();
    }

}
