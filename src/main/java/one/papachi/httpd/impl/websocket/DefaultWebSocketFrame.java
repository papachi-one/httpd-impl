package one.papachi.httpd.impl.websocket;

import one.papachi.httpd.api.websocket.WebSocketFrame;
import one.papachi.httpd.impl.net.AsynchronousInputChannel;

import java.io.InputStream;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ReadableByteChannel;

public class DefaultWebSocketFrame extends AsynchronousInputChannel implements WebSocketFrame {

    public static class DefaultBuilder implements Builder {

        private boolean fin, rsv1, rsv2, rsv3, mask;
        private Type type;
        private byte[] maskingKey;
        private long length;
        private Object object;

        @Override
        public Builder fin(boolean value) {
            fin = value;
            return this;
        }

        @Override
        public Builder rsv1(boolean value) {
            rsv1 = value;
            return this;
        }

        @Override
        public Builder rsv2(boolean value) {
            rsv2 = value;
            return this;
        }

        @Override
        public Builder rsv3(boolean value) {
            rsv3 = value;
            return this;
        }

        @Override
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        @Override
        public Builder mask(boolean value) {
            mask = value;
            return this;
        }

        @Override
        public Builder length(long length) {
            this.length = length;
            return this;
        }

        @Override
        public Builder maskingKey(byte[] maskingKey) {
            mask();
            maskingKey = maskingKey;
            return this;
        }

        @Override
        public Builder payload() {
            object = null;
            length(0);
            return this;
        }

        @Override
        public Builder payload(AsynchronousByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(AsynchronousFileChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(ReadableByteChannel channel) {
            object = channel;
            return this;
        }

        @Override
        public Builder payload(InputStream inputStream) {
            object = inputStream;
            return this;
        }

        @Override
        public WebSocketFrame build() {
            return new DefaultWebSocketFrame(fin, rsv1, rsv2, rsv3, type, mask, length, maskingKey, object);
        }
    }

    private final boolean fin, rsv1, rsv2, rsv3, mask;
    private final Type type;
    private final byte[] maskingKey;

    DefaultWebSocketFrame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, Type type, boolean mask, long length, byte[] maskingKey, Object object) {
        super(object, length);
        this.fin = fin;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.type = type;
        this.mask = mask;
        this.maskingKey = maskingKey;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public boolean isFin() {
        return fin;
    }

    @Override
    public boolean isRsv1() {
        return rsv1;
    }

    @Override
    public boolean isRsv2() {
        return rsv2;
    }

    @Override
    public boolean isRsv3() {
        return rsv3;
    }

    @Override
    public boolean isMasked() {
        return mask;
    }

    @Override
    public long getLength() {
        return maxLength;
    }

    @Override
    public byte[] getMaskingKey() {
        return maskingKey;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WebSocketFrame(");
        sb.append("type = " + type);
        sb.append(", fin = " + fin);
//        sb.append("rsv1 = " + rsv1);
//        sb.append("rsv2 = " + rsv2);
//        sb.append("rsv3 = " + rsv3);
        sb.append(", mask = " + mask);
        sb.append(", length = " + maxLength);
        sb.append(")");
        return sb.toString();
    }
}
