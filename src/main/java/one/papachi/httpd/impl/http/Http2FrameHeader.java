package one.papachi.httpd.impl.http;

public class Http2FrameHeader {

    private final int length;

    private final Http2FrameType type;

    private final int flags;

    private final int streamId;

    public Http2FrameHeader(byte[] data) {
        int length = 0;
        for (int i = 0; i < 3; i++)
            length = (length << 8) + (data[i] & 0xFF);
        this.length = length;
        int type = data[3] & 0xFF;
        this.type = Http2FrameType.values()[type];
        flags = data[4] & 0xFF;
        int streamId = 0;
        for (int i = 0; i < 4; i++)
            streamId = (streamId << 8) + (data[i + 5] & 0xFF);
        this.streamId = streamId & 0x7FFF;
    }

    public int getLength() {
        return length;
    }

    public Http2FrameType getType() {
        return type;
    }

    public int getFlags() {
        return flags;
    }

    public int getStreamId() {
        return streamId;
    }

}
