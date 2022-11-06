package one.papachi.httpd.impl;

import java.io.ByteArrayOutputStream;

public class CustomDataBuffer extends ByteArrayOutputStream {

    public CustomDataBuffer() {
    }

    public CustomDataBuffer(int size) {
        super(size);
    }

    public byte[] getArray() {
        return buf;
    }

    public int getLength() {
        return count;
    }

}
